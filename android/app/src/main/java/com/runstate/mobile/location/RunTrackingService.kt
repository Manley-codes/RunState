package com.runstate.mobile.location

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.runstate.mobile.MainActivity
import com.runstate.mobile.RunStateApplication
import com.runstate.mobile.data.local.PreRunEnergy
import com.runstate.mobile.data.local.RunLocationObservation
import com.runstate.mobile.data.local.RunTrackingMode
import com.runstate.mobile.data.local.StoredRunState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Process-local service progress. A stored run row remains the source of truth. */
internal sealed interface RunTrackingServiceState {
    data object Idle : RunTrackingServiceState
    data object Starting : RunTrackingServiceState
    data class Tracking(val runId: String) : RunTrackingServiceState
    data class Paused(val runId: String) : RunTrackingServiceState
    data class Failed(val reason: String) : RunTrackingServiceState
}

/**
 * Holds the one GPS run in a location foreground service. The Activity starts this only after
 * its visible countdown; the service alone requests the official GPS start. It follows Room's
 * durable state for pause/resume/completion instead of trusting a screen callback. A killed
 * process is not restarted in the background: the next visible Activity must explicitly resume
 * the recovered run, with a fresh GPS anchor and partial coverage.
 */
internal class RunTrackingService : Service() {
    private val app: RunStateApplication
        get() = application as RunStateApplication

    private val locationManager: LocationManager
        get() = getSystemService(LocationManager::class.java)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val observationLock = Mutex()
    private val policy = GpsDistancePolicy()
    private var work: Job? = null
    private var listener: LocationListener? = null
    private var runId: String? = null
    private var storedState: StoredRunState? = null
    private var gpsEnabled = false
    private var finishedNormally = false
    private var failed = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (work?.isActive == true) return START_NOT_STICKY

        val action = intent?.action
        val existingId = intent?.getStringExtra(EXTRA_RUN_ID)
        if (action != ACTION_START_NEW && action != ACTION_RESUME_EXISTING) {
            failAndStop("The GPS run request was incomplete.")
            return START_NOT_STICKY
        }
        if (action == ACTION_RESUME_EXISTING && existingId.isNullOrBlank()) {
            failAndStop("The saved run to resume was not identified.")
            return START_NOT_STICKY
        }
        if (!hasFineLocation() || !locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            failAndStop("Precise location and GPS must be enabled before tracking starts.")
            return START_NOT_STICKY
        }

        try {
            promoteToForeground("Preparing GPS run")
        } catch (security: SecurityException) {
            failAndStop("Android did not allow GPS tracking. Check precise location access.")
            return START_NOT_STICKY
        } catch (failure: RuntimeException) {
            failAndStop("Android could not start the run notification.")
            return START_NOT_STICKY
        }

        app.publishTrackingServiceState(RunTrackingServiceState.Starting)
        work = scope.launch {
            try {
                val id = if (action == ACTION_START_NEW) {
                    val energyName = intent?.getStringExtra(EXTRA_PRE_RUN_ENERGY)
                    val energy = energyName?.let { name ->
                        PreRunEnergy.entries.firstOrNull { it.name == name }
                            ?: error("Unknown pre-run Energy choice.")
                    }
                    val musicObservationRequested =
                        intent?.getBooleanExtra(EXTRA_MUSIC_OBSERVATION_REQUESTED, false) == true
                    val musicReflectionSharingAllowed =
                        intent?.getBooleanExtra(EXTRA_MUSIC_REFLECTION_SHARING_ALLOWED, false) == true
                    val snapshot = app.runSessionCoordinator.initialize()
                    check(snapshot.initializationStatus ==
                        com.runstate.mobile.run.InitializationStatus.Completed) {
                        "Run recovery did not finish."
                    }
                    app.runSessionCoordinator.requestStart(
                        energy,
                        RunTrackingMode.GPS,
                        musicObservationRequested,
                        musicReflectionSharingAllowed
                    ).await().runId
                } else {
                    val idToResume = requireNotNull(existingId)
                    val stored = requireNotNull(app.database.runDao().findById(idToResume)) {
                        "The saved run could not be found."
                    }
                    check(stored.trackingMode == RunTrackingMode.GPS &&
                        stored.state != StoredRunState.COMPLETED) {
                        "Only an unfinished GPS run can resume tracking."
                    }
                    val snapshot = app.runSessionCoordinator.initialize()
                    val owned = snapshot.admission as?
                        com.runstate.mobile.run.RunAdmission.RunInProgress
                    check(owned?.session?.runId == idToResume) {
                        "Run recovery did not own the requested GPS run."
                    }
                    policy.trackingInterrupted()
                    idToResume
                }
                runId = id
                app.database.runDao().observeRunById(id).collect { row ->
                    checkNotNull(row) { "The active run disappeared from storage." }
                    check(row.trackingMode == RunTrackingMode.GPS) {
                        "The active run is not a GPS run."
                    }
                    reconcileStoredState(id, row.state)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                failAndStop(failure.message ?: "GPS tracking could not continue.")
            }
        }
        return START_NOT_STICKY
    }

    private fun reconcileStoredState(id: String, state: StoredRunState) {
        if (state == storedState) return
        storedState = state
        when (state) {
            StoredRunState.RUNNING -> {
                if (!hasFineLocation()) {
                    policy.trackingInterrupted()
                    failAndStop("Precise location access was removed during this run.")
                    return
                }
                try {
                    listenForGps(id)
                    if (gpsEnabled) {
                        app.publishTrackingServiceState(RunTrackingServiceState.Tracking(id))
                        updateNotification("GPS run in progress")
                    } else {
                        policy.trackingInterrupted()
                        app.publishTrackingServiceState(
                            RunTrackingServiceState.Failed("GPS is off; distance is incomplete.")
                        )
                        updateNotification("GPS off · distance incomplete")
                    }
                } catch (failure: SecurityException) {
                    policy.trackingInterrupted()
                    failAndStop("Android stopped precise location access during this run.")
                } catch (failure: RuntimeException) {
                    policy.trackingInterrupted()
                    failAndStop("GPS could not start. The saved run can be resumed later.")
                }
            }

            StoredRunState.PAUSED -> {
                stopGps()
                policy.pause()
                app.publishTrackingServiceState(RunTrackingServiceState.Paused(id))
                updateNotification("Run paused · GPS off")
            }

            StoredRunState.COMPLETED -> {
                stopGps()
                finishedNormally = true
                app.publishTrackingServiceState(RunTrackingServiceState.Idle)
                stopSelf()
            }
        }
    }

    @SuppressLint("MissingPermission") // Checked before the service starts and again above.
    private fun listenForGps(id: String) {
        if (listener != null) return
        gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val next = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (storedState != StoredRunState.RUNNING || !gpsEnabled) return
                // A duplicate platform delivery keeps the same key and cannot add distance twice.
                val key = "${location.provider ?: LocationManager.GPS_PROVIDER}:" +
                    "${location.elapsedRealtimeNanos}:${location.time}:" +
                    "${location.latitude.toBits()}:${location.longitude.toBits()}"
                scope.launch {
                    observationLock.withLock {
                        if (storedState != StoredRunState.RUNNING || !gpsEnabled) return@withLock
                        saveObservation(id, key, location)
                    }
                }
            }

            override fun onProviderDisabled(provider: String) {
                if (provider == LocationManager.GPS_PROVIDER) {
                    gpsEnabled = false
                    policy.trackingInterrupted()
                    app.publishTrackingServiceState(
                        RunTrackingServiceState.Failed("GPS was turned off during this run.")
                    )
                    updateNotification("GPS off · distance incomplete")
                }
            }

            override fun onProviderEnabled(provider: String) {
                if (provider == LocationManager.GPS_PROVIDER &&
                    storedState == StoredRunState.RUNNING) {
                    gpsEnabled = true
                    policy.trackingInterrupted()
                    app.publishTrackingServiceState(RunTrackingServiceState.Tracking(id))
                    updateNotification("GPS run in progress")
                }
            }

            @Deprecated("Android no longer uses provider status callbacks.")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }
        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            1_000L,
            0f,
            next,
            Looper.getMainLooper()
        )
        listener = next
    }

    private suspend fun saveObservation(id: String, key: String, location: Location) {
        val elapsedNanos = location.elapsedRealtimeNanos
        val sample = GpsSample(
            latitudeDegrees = location.latitude,
            longitudeDegrees = location.longitude,
            horizontalAccuracyMeters =
                if (location.hasAccuracy()) location.accuracy.toDouble() else null,
            observedEpochMillis = location.time,
            elapsedRealtimeMillis = elapsedNanos / 1_000_000L
        )
        val decision = policy.observe(
            sample = sample,
            nowElapsedRealtimeMillis = SystemClock.elapsedRealtime(),
            isRunning = true
        )
        try {
            val stored = app.database.locationDao().recordObservation(
                RunLocationObservation(
                    runId = id,
                    observationKey = key,
                    observedAtEpochMillis = location.time,
                    elapsedRealtimeNanos = elapsedNanos,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    horizontalAccuracyMeters =
                        if (location.hasAccuracy()) location.accuracy else null,
                    altitudeMeters = if (location.hasAltitude()) location.altitude else null,
                    speedMetersPerSecond = if (location.hasSpeed()) location.speed else null,
                    bearingDegrees = if (location.hasBearing()) location.bearing else null,
                    acceptedForDistance = decision.acceptedForRoute,
                    rejectionReason = if (decision.acceptedForRoute) null else decision.status.name,
                    segmentDistanceMeters = decision.distanceAddedMeters
                )
            )
            // Room rechecks the durable run state and checkpoint. If it downgrades a
            // callback that the in-memory filter liked, the next fix must be a fresh
            // anchor rather than measuring from a point storage refused.
            if (decision.acceptedForRoute && !stored.acceptedForDistance) {
                policy.trackingInterrupted()
            }
            if (gpsEnabled && storedState == StoredRunState.RUNNING &&
                app.trackingServiceState.value is RunTrackingServiceState.Failed) {
                app.publishTrackingServiceState(RunTrackingServiceState.Tracking(id))
                updateNotification("GPS run in progress")
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            policy.trackingInterrupted()
            app.publishTrackingServiceState(
                RunTrackingServiceState.Failed("A GPS point could not be saved; distance is incomplete.")
            )
            updateNotification("GPS record incomplete")
        }
    }

    private fun stopGps() {
        listener?.let {
            try {
                locationManager.removeUpdates(it)
            } catch (security: SecurityException) {
                // Access may have been revoked while the listener was registered.
            }
        }
        listener = null
        gpsEnabled = false
    }

    private fun hasFineLocation(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun promoteToForeground(message: String) {
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Active run tracking", NotificationManager.IMPORTANCE_LOW)
        )
        val notification = notification(message)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(message: String) {
        try {
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, notification(message))
        } catch (security: SecurityException) {
            // Notification permission can be revoked while the service is active.
        }
    }

    private fun notification(message: String): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("RunState active run")
            .setContentText(message)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun failAndStop(reason: String) {
        failed = true
        stopGps()
        app.publishTrackingServiceState(RunTrackingServiceState.Failed(reason))
        stopSelf()
    }

    override fun onDestroy() {
        stopGps()
        scope.cancel()
        if (!finishedNormally && !failed) {
            app.publishTrackingServiceState(
                RunTrackingServiceState.Failed("GPS tracking stopped; reopen the saved run to resume.")
            )
        }
        super.onDestroy()
    }

    companion object {
        private const val ACTION_START_NEW = "com.runstate.mobile.action.START_GPS_RUN"
        private const val ACTION_RESUME_EXISTING = "com.runstate.mobile.action.RESUME_GPS_RUN"
        private const val EXTRA_PRE_RUN_ENERGY = "pre_run_energy"
        private const val EXTRA_MUSIC_OBSERVATION_REQUESTED = "music_observation_requested"
        private const val EXTRA_MUSIC_REFLECTION_SHARING_ALLOWED = "music_reflection_sharing_allowed"
        private const val EXTRA_RUN_ID = "run_id"
        private const val CHANNEL_ID = "active_run_tracking"
        private const val NOTIFICATION_ID = 3207

        /** Call only after a visible Activity's countdown finishes and permissions are ready. */
        fun startNew(
            context: Context,
            preRunEnergy: PreRunEnergy?,
            musicObservationRequested: Boolean = false,
            musicReflectionSharingAllowed: Boolean = false
        ) {
            require(!musicReflectionSharingAllowed || musicObservationRequested) {
                "Music reflection sharing requires this run's music observation opt-in."
            }
            val intent = Intent(context, RunTrackingService::class.java)
                .setAction(ACTION_START_NEW)
                .putExtra(EXTRA_PRE_RUN_ENERGY, preRunEnergy?.name)
                .putExtra(EXTRA_MUSIC_OBSERVATION_REQUESTED, musicObservationRequested)
                .putExtra(EXTRA_MUSIC_REFLECTION_SHARING_ALLOWED, musicReflectionSharingAllowed)
            ContextCompat.startForegroundService(context, intent)
        }

        /** Visible recovery path after process death; never auto-launched from a receiver. */
        fun resumeExisting(context: Context, runId: String) {
            val intent = Intent(context, RunTrackingService::class.java)
                .setAction(ACTION_RESUME_EXISTING)
                .putExtra(EXTRA_RUN_ID, runId)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
