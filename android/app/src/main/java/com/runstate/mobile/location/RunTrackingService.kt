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
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.runstate.mobile.MainActivity
import com.runstate.mobile.RunStateApplication
import com.runstate.mobile.data.local.PreRunEnergy
import com.runstate.mobile.data.local.RunLocationObservation
import com.runstate.mobile.data.local.RunTrackingMode
import com.runstate.mobile.data.local.StoredRunState
import com.runstate.mobile.run.InitializationStatus
import com.runstate.mobile.run.RunAdmission
import com.runstate.mobile.run.RunJourneySnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Process-local service progress. A stored run row remains the source of truth. */
internal sealed interface RunTrackingServiceState {
    data object Idle : RunTrackingServiceState
    data object Starting : RunTrackingServiceState
    data class Tracking(
        val runId: String,
        val signalStatus: GpsSignalStatus = GpsSignalStatus.ACQUIRING
    ) : RunTrackingServiceState
    data class Paused(val runId: String) : RunTrackingServiceState
    data class Failed(val reason: String) : RunTrackingServiceState
}

/** Missing storage is expected only after this coordinator has retired that exact owner. */
internal fun missingTrackedRunWasRetired(runId: String, snapshot: RunJourneySnapshot): Boolean {
    if (snapshot.initializationStatus != InitializationStatus.Completed) return false
    return when (val admission = snapshot.admission) {
        RunAdmission.ReadyForCountdown, RunAdmission.CountdownInProgress -> true
        is RunAdmission.RunInProgress -> admission.session.runId != runId
        is RunAdmission.RunCompleted -> admission.session.runId != runId
        RunAdmission.NotInitialized, is RunAdmission.BlockedByInconsistentStorage -> false
    }
}

/** Each timed platform lock is acquired once; renewal swaps in a new lock before releasing it. */
internal interface TimedCpuWakeLock {
    val isHeld: Boolean
    fun acquire(timeoutMillis: Long)
    fun release()
}

internal class RunCpuWakeLease(
    private val newLock: () -> TimedCpuWakeLock,
    private val timeoutMillis: Long
) {
    init {
        require(timeoutMillis > 0L)
    }

    private var current: TimedCpuWakeLock? = null

    fun start() {
        if (current == null) replace()
    }

    fun renew() {
        if (current != null) replace()
    }

    fun stop() {
        val previous = current
        current = null
        if (previous?.isHeld == true) previous.release()
    }

    private fun replace() {
        val replacement = newLock()
        try {
            replacement.acquire(timeoutMillis)
        } catch (failure: RuntimeException) {
            if (replacement.isHeld) replacement.release()
            throw failure
        }
        val previous = current
        current = replacement
        if (previous?.isHeld == true) previous.release()
    }
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
    private var policy = GpsDistancePolicy()
    private val signalMonitor = GpsSignalMonitor()
    private var signalWatchdog: Job? = null
    private var storageFailureActive = false
    private var work: Job? = null
    private var acceptedStartId = 0
    private var listener: LocationListener? = null
    /** Invalidates callbacks queued before a pause, provider change, or registration removal. */
    private var locationGeneration = 0L
    private var runId: String? = null
    private var storedState: StoredRunState? = null
    private var gpsEnabled = false
    private var cpuWakeLease: RunCpuWakeLease? = null
    private var wakeRenewal: Job? = null
    private var finishedNormally = false
    private var failed = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (work?.isActive == true) {
            retryCommandAfterRetiredOwner(intent, flags, startId)
            return START_NOT_STICKY
        }

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

        finishedNormally = false
        failed = false
        storedState = null
        acceptedStartId = startId
        storageFailureActive = false
        signalMonitor.reset(SystemClock.elapsedRealtime())

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
                    check(snapshot.admission == RunAdmission.CountdownInProgress) {
                        "A new GPS run requires its own countdown."
                    }
                    // A reused service is not a reused run. Retire the old listener and
                    // drain its in-flight observation before assigning a clean policy.
                    stopGps()
                    runId = null
                    observationLock.withLock { policy = GpsDistancePolicy() }
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
                app.database.runDao().observeRunById(id).takeWhile { row ->
                    if (row != null) true else {
                        finishRetiredRunObservation(id, startId)
                        false
                    }
                }.collect { stored ->
                    val row = checkNotNull(stored)
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

    /** A discard can finish while the next countdown delivers its new service command. */
    private fun retryCommandAfterRetiredOwner(intent: Intent?, flags: Int, startId: Int) {
        val previousWork = work ?: return
        val previousId = runId ?: return
        val nextIntent = intent?.let(::Intent) ?: return
        scope.launch {
            try {
                app.runSessionCoordinator.awaitReservedWork()
                val snapshot = app.runSessionCoordinator.journeySnapshot()
                val admission = snapshot.admission
                val isNextCountdown = nextIntent.action == ACTION_START_NEW &&
                    admission == RunAdmission.CountdownInProgress
                val isDifferentResume = nextIntent.action == ACTION_RESUME_EXISTING &&
                    admission is RunAdmission.RunInProgress && admission.session.runId != previousId &&
                    admission.session.runId == nextIntent.getStringExtra(EXTRA_RUN_ID)
                if ((!isNextCountdown && !isDifferentResume) ||
                    !missingTrackedRunWasRetired(previousId, snapshot) ||
                    app.database.runDao().findById(previousId) != null ||
                    work !== previousWork || runId != previousId
                ) return@launch
                // Identity is checked after every suspension: this old observer may not
                // cancel a worker already installed for the newer command.
                previousWork.cancel()
                work = null
                stopGps()
                policy.trackingInterrupted()
                onStartCommand(nextIntent, flags, startId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // The original observer still owns error reporting for its saved run.
            }
        }
    }

    private suspend fun finishRetiredRunObservation(id: String, startId: Int) {
        app.runSessionCoordinator.awaitReservedWork()
        val snapshot = app.runSessionCoordinator.journeySnapshot()
        check(missingTrackedRunWasRetired(id, snapshot)) {
            "The active run disappeared from storage."
        }
        if (runId != id || acceptedStartId != startId) return
        stopGps()
        policy.trackingInterrupted()
        finishedNormally = true
        // Never replace a newer run's progress with this old run's Idle event.
        if (snapshot.admission == RunAdmission.ReadyForCountdown ||
            snapshot.admission == RunAdmission.CountdownInProgress
        ) app.publishTrackingServiceState(RunTrackingServiceState.Idle)
        // Android ignores this stop when it has already received a newer startId.
        stopSelf(startId)
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
                    if (!gpsEnabled) {
                        policy.trackingInterrupted()
                        app.publishTrackingServiceState(
                            RunTrackingServiceState.Failed("GPS is off; distance is incomplete.")
                        )
                        updateNotification("GPS off · distance incomplete")
                    } else if (startCpuWakeLease()) {
                        startSignalWatchdog(id)
                        publishSignalState(id)
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
        signalMonitor.reset(SystemClock.elapsedRealtime())
        gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val next = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (listener !== this || storedState != StoredRunState.RUNNING || !gpsEnabled) return
                val callbackListener = this
                val deliveredGeneration = locationGeneration
                // Snapshot both the fix and its delivery time before this callback queues
                // behind an earlier Room write. Processing delay must not make a fresh fix
                // appear stale, while a genuinely delayed platform fix remains rejectable.
                val delivered = Location(location)
                val deliveredAtElapsedRealtimeMillis = SystemClock.elapsedRealtime()
                // A duplicate platform delivery keeps the same key and cannot add distance twice.
                val key = "${delivered.provider ?: LocationManager.GPS_PROVIDER}:" +
                    "${delivered.elapsedRealtimeNanos}:${delivered.time}:" +
                    "${delivered.latitude.toBits()}:${delivered.longitude.toBits()}"
                scope.launch {
                    observationLock.withLock {
                        if (listener !== callbackListener ||
                            deliveredGeneration != locationGeneration ||
                            storedState != StoredRunState.RUNNING || !gpsEnabled
                        ) return@withLock
                        saveObservation(
                            id, key, delivered, deliveredAtElapsedRealtimeMillis,
                            callbackListener, deliveredGeneration
                        )
                    }
                }
            }

            override fun onProviderDisabled(provider: String) {
                if (listener === this && provider == LocationManager.GPS_PROVIDER) {
                    locationGeneration++
                    gpsEnabled = false
                    stopSignalWatchdog()
                    signalMonitor.reset(SystemClock.elapsedRealtime())
                    stopCpuWakeLease()
                    policy.trackingInterrupted()
                    app.publishTrackingServiceState(
                        RunTrackingServiceState.Failed("GPS was turned off during this run.")
                    )
                    updateNotification("GPS off · distance incomplete")
                }
            }

            override fun onProviderEnabled(provider: String) {
                if (listener === this && provider == LocationManager.GPS_PROVIDER &&
                    storedState == StoredRunState.RUNNING) {
                    locationGeneration++
                    gpsEnabled = true
                    policy.trackingInterrupted()
                    signalMonitor.reset(SystemClock.elapsedRealtime())
                    if (startCpuWakeLease()) {
                        startSignalWatchdog(id)
                        publishSignalState(id)
                    }
                }
            }

            @Deprecated("Android no longer uses provider status callbacks.")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }
        listener = next
        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1_000L,
                0f,
                next,
                Looper.getMainLooper()
            )
        } catch (failure: RuntimeException) {
            // A failed registration must not leave a live request consuming location power.
            try {
                locationManager.removeUpdates(next)
            } catch (_: RuntimeException) {
                // The original registration failure is the actionable error.
            }
            listener = null
            gpsEnabled = false
            throw failure
        }
    }

    private suspend fun saveObservation(
        id: String,
        key: String,
        location: Location,
        deliveredAtElapsedRealtimeMillis: Long,
        callbackListener: LocationListener,
        deliveredGeneration: Long
    ) {
        val elapsedNanos = location.elapsedRealtimeNanos
        val sample = GpsSample(
            latitudeDegrees = location.latitude,
            longitudeDegrees = location.longitude,
            horizontalAccuracyMeters =
                if (location.hasAccuracy()) location.accuracy.toDouble() else null,
            observedEpochMillis = location.time,
            elapsedRealtimeMillis = elapsedNanos / 1_000_000L
        )
        val observationPolicy = policy
        val decision = observationPolicy.observe(
            sample = sample,
            nowElapsedRealtimeMillis = deliveredAtElapsedRealtimeMillis,
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
            if (!ownsObservation(id, observationPolicy, callbackListener, deliveredGeneration)) return
            // Room rechecks the durable run state and checkpoint. If it downgrades a
            // callback that the in-memory filter liked, the next fix must be a fresh
            // anchor rather than measuring from a point storage refused.
            if (decision.acceptedForRoute && !stored.acceptedForDistance) {
                observationPolicy.trackingInterrupted()
            }
            val usable = isDurablyUsableGpsFix(
                decision.status, stored.acceptedForDistance, stored.rejectionReason
            )
            val signal = signalMonitor.onStoredFix(
                decision.status, sample.elapsedRealtimeMillis, usable, SystemClock.elapsedRealtime()
            )
            // An inaccurate/replayed observation may be durably saved for diagnostics; that
            // alone must not erase a storage failure or claim that GPS is recording again.
            if (usable && signal == GpsSignalStatus.RECORDING) storageFailureActive = false
            publishSignalState(id)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (!ownsObservation(id, observationPolicy, callbackListener, deliveredGeneration)) return
            observationPolicy.trackingInterrupted()
            storageFailureActive = true
            publishSignalState(id)
        }
    }

    private fun stopGps() {
        stopSignalWatchdog()
        stopCpuWakeLease()
        locationGeneration++
        signalMonitor.reset(SystemClock.elapsedRealtime())
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

    /** Recheck after every suspended Room write: the same UUID can pause and resume meanwhile. */
    private fun ownsObservation(
        id: String,
        observationPolicy: GpsDistancePolicy,
        callbackListener: LocationListener,
        generation: Long
    ): Boolean = runId == id && policy === observationPolicy &&
        listener === callbackListener && generation == locationGeneration &&
        storedState == StoredRunState.RUNNING && gpsEnabled

    private fun startSignalWatchdog(id: String) {
        stopSignalWatchdog()
        val generation = locationGeneration
        signalWatchdog = scope.launch {
            while (runId == id && generation == locationGeneration &&
                listener != null && gpsEnabled && storedState == StoredRunState.RUNNING) {
                delay(1_000L)
                if (runId != id || generation != locationGeneration || listener == null ||
                    !gpsEnabled || storedState != StoredRunState.RUNNING) return@launch
                // A delayed Room write can make committed evidence old while source fixes
                // remain continuous. Only the distance policy judges source-time route gaps.
                publishSignalState(id)
            }
        }
    }

    private fun stopSignalWatchdog() {
        signalWatchdog?.cancel()
        signalWatchdog = null
    }

    private fun publishSignalState(id: String) {
        if (runId != id || storedState != StoredRunState.RUNNING || !gpsEnabled || listener == null) return
        if (storageFailureActive) {
            val failure = RunTrackingServiceState.Failed(
                "A GPS point could not be saved; distance is incomplete."
            )
            if (app.trackingServiceState.value != failure) {
                app.publishTrackingServiceState(failure)
                updateNotification("GPS record incomplete")
            }
            return
        }
        val signal = signalMonitor.status(SystemClock.elapsedRealtime())
        val next = RunTrackingServiceState.Tracking(id, signal)
        if (app.trackingServiceState.value == next) return
        app.publishTrackingServiceState(next)
        updateNotification(when (signal) {
            GpsSignalStatus.ACQUIRING -> "Finding GPS signal"
            GpsSignalStatus.RECORDING -> "GPS recording"
            GpsSignalStatus.WEAK -> "Weak GPS · distance may be incomplete"
            GpsSignalStatus.STALE -> "GPS unavailable · waiting for a fresh fix"
        })
    }

    /** A foreground notification alone does not guarantee prompt callbacks with the screen off. */
    private fun startCpuWakeLease(): Boolean {
        if (storedState != StoredRunState.RUNNING || !gpsEnabled || listener == null) return false
        if (wakeRenewal?.isActive == true) return true
        try {
            if (!hasFineLocation() ||
                !locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            ) {
                policy.trackingInterrupted()
                failAndStop("Precise location or GPS was turned off during this run.")
                return false
            }
            val lease = cpuWakeLease ?: RunCpuWakeLease(::newTimedCpuWakeLock, WAKE_TIMEOUT_MS)
                .also { cpuWakeLease = it }
            lease.start()
            wakeRenewal = scope.launch {
                while (true) {
                    delay(WAKE_RENEW_MS)
                    if (storedState != StoredRunState.RUNNING || !gpsEnabled || listener == null) {
                        stopCpuWakeLease()
                        return@launch
                    }
                    try {
                        if (!hasFineLocation() ||
                            !locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                        ) {
                            policy.trackingInterrupted()
                            failAndStop("Precise location or GPS was turned off during this run.")
                            return@launch
                        }
                        lease.renew()
                    } catch (failure: RuntimeException) {
                        policy.trackingInterrupted()
                        failAndStop("Android could not keep GPS processing active; reopen the saved run.")
                        return@launch
                    }
                }
            }
            return true
        } catch (failure: RuntimeException) {
            failAndStop("Android could not keep GPS processing active; reopen the saved run.")
            return false
        }
    }

    private fun stopCpuWakeLease() {
        wakeRenewal?.cancel()
        wakeRenewal = null
        try {
            cpuWakeLease?.stop()
        } catch (_: RuntimeException) {
            // Each acquisition has a hard timeout even if Android rejects explicit release.
        }
    }

    private fun newTimedCpuWakeLock(): TimedCpuWakeLock {
        val power = requireNotNull(getSystemService(PowerManager::class.java))
        val platformLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_TAG)
        return object : TimedCpuWakeLock {
            override val isHeld: Boolean get() = platformLock.isHeld
            override fun acquire(timeoutMillis: Long) = platformLock.acquire(timeoutMillis)
            override fun release() = platformLock.release()
        }
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
        private const val WAKE_TAG = "com.runstate.mobile:RunTrackingService"
        private const val WAKE_TIMEOUT_MS = 120_000L
        private const val WAKE_RENEW_MS = 60_000L

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
