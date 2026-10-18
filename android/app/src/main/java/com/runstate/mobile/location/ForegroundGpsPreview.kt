package com.runstate.mobile.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.location.LocationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

internal enum class ForegroundGpsPreviewStatus {
    DISABLED, PERMISSION_NEEDED, LOCATION_OFF, ACQUIRING, CURRENT, STALE, WEAK, UNAVAILABLE
}

/** Preparation only: no saved run, recorded route, distance, or reusable run identity. */
internal data class ForegroundGpsPreviewState(
    val status: ForegroundGpsPreviewStatus,
    val marker: GpsMapMarker? = null,
    /** A recent usable live callback, never a cached map anchor or a recorded run point. */
    val hasFreshLiveFix: Boolean = false
) {
    val explanation: String get() = when (status) {
        ForegroundGpsPreviewStatus.DISABLED -> "GPS preview is inactive."
        ForegroundGpsPreviewStatus.PERMISSION_NEEDED -> "Allow precise location to show your position."
        ForegroundGpsPreviewStatus.LOCATION_OFF -> "Turn on phone Location to show your position."
        ForegroundGpsPreviewStatus.ACQUIRING -> "Finding your GPS location · run not started"
        ForegroundGpsPreviewStatus.CURRENT -> "Current GPS location · run not started"
        ForegroundGpsPreviewStatus.STALE -> if (marker == null) "Waiting for a fresh GPS location."
            else "Last known location · waiting for a fresh GPS fix"
        ForegroundGpsPreviewStatus.WEAK -> "GPS signal is weak · current position is uncertain"
        ForegroundGpsPreviewStatus.UNAVAILABLE -> "GPS preview is unavailable. Run setup still works."
    }

    /** Quiet Start status only. Signal strength never grants or blocks permission to start. */
    val startStatusText: String get() = when {
        hasFreshLiveFix -> "GPS ready"
        status == ForegroundGpsPreviewStatus.PERMISSION_NEEDED -> "Allow precise location"
        status == ForegroundGpsPreviewStatus.LOCATION_OFF -> "Location off"
        status == ForegroundGpsPreviewStatus.WEAK -> "GPS signal weak"
        status == ForegroundGpsPreviewStatus.STALE -> "GPS signal lost"
        status == ForegroundGpsPreviewStatus.UNAVAILABLE -> "GPS unavailable"
        else -> "GPS connecting…"
    }

    /** Existing renderer input, with no route geometry or durable run UUID. */
    fun toMapEvidence() = GpsMapEvidence(
        runId = "foreground-gps-preview",
        isGpsRun = true,
        marker = marker,
        evidenceUnavailable = status == ForegroundGpsPreviewStatus.UNAVAILABLE
    )
}

/**
 * A preview handoff must finish before the foreground service is launched, rather than
 * waiting for Compose to apply the next screen state. This owner still delegates as State.
 */
internal class ForegroundGpsPreviewController : State<ForegroundGpsPreviewState> {
    private val state = mutableStateOf(ForegroundGpsPreviewState(ForegroundGpsPreviewStatus.DISABLED))
    private var currentSession: ForegroundGpsPreviewSession? = null
    private var handedOff = false
    override val value: ForegroundGpsPreviewState get() = state.value

    /** Synchronously refresh the informational status; never gates a start. */
    fun hasFreshLiveFixNow(): Boolean {
        currentSession?.tick()
        return state.value.hasFreshLiveFix
    }

    /** Synchronous listener removal; no callback from this preview can publish afterward. */
    fun stopBeforeRun() {
        handedOff = true
        clearCurrentSession()
    }

    internal fun beginPreparation() {
        handedOff = false
    }

    private fun clearCurrentSession() {
        val previous = currentSession
        currentSession = null
        previous?.stop()
        state.value = ForegroundGpsPreviewState(ForegroundGpsPreviewStatus.DISABLED)
    }

    internal fun startSession(
        source: ForegroundGpsPreviewSource,
        nowMillis: () -> Long
    ): ForegroundGpsPreviewSession? {
        if (handedOff) return null
        clearCurrentSession()
        lateinit var next: ForegroundGpsPreviewSession
        next = ForegroundGpsPreviewSession(source, nowMillis) {
            if (currentSession === next) state.value = it
        }
        currentSession = next
        next.start()
        return next
    }

    internal fun stopSession(session: ForegroundGpsPreviewSession) {
        if (currentSession === session) clearCurrentSession()
        else session.stop() // An older lifecycle's finally cannot clear the newer preview.
    }
}

/**
 * In-memory Start/countdown location. The caller disables this before active-run ownership;
 * the foreground service remains the sole run recorder. Backgrounding or leaving this
 * composition removes the listener and erases the preview, even when no fix ever arrived.
 * No permission dialog, run creation, Room write, distance calculation or wake lock occurs here.
 * Keep [restartKey] stable through Start/countdown; change it only to retry a failed handoff.
 */
@Composable
internal fun rememberForegroundGpsPreview(
    enabled: Boolean,
    lifecycle: Lifecycle,
    restartKey: Any? = null,
    sourceOverride: ForegroundGpsPreviewSource? = null
): ForegroundGpsPreviewController {
    val context = LocalContext.current.applicationContext
    val controller = remember { ForegroundGpsPreviewController() }
    val platformSource = remember(context) { AndroidForegroundGpsPreviewSource(context) }
    val source = sourceOverride ?: platformSource
    LaunchedEffect(enabled, lifecycle, source, restartKey) {
        if (!enabled) {
            controller.stopBeforeRun()
            return@LaunchedEffect
        }
        controller.beginPreparation()
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val session = controller.startSession(source, SystemClock::elapsedRealtime)
                ?: return@repeatOnLifecycle
            try {
                while (true) {
                    delay(1_000L)
                    session.tick()
                }
            } finally {
                controller.stopSession(session)
            }
        }
    }
    return controller
}

internal enum class GpsPreviewAvailability { READY, PERMISSION_NEEDED, LOCATION_OFF, UNAVAILABLE }

/** Preview-only provenance; shared recording samples and their persistence stay unchanged. */
internal data class ForegroundGpsPreviewFix(val sample: GpsSample, val isMock: Boolean = false)

/** Narrow platform boundary so listener lifetime and revocation can be checked without a phone. */
internal interface ForegroundGpsPreviewSource {
    fun availability(): GpsPreviewAvailability
    fun lastKnownSample(): ForegroundGpsPreviewFix?
    fun listen(onSample: (ForegroundGpsPreviewFix) -> Unit, onProviderDisabled: () -> Unit): () -> Unit
}

/** Main-thread owner of one temporary listener. Late callbacks lose their generation lease. */
internal class ForegroundGpsPreviewSession(
    private val source: ForegroundGpsPreviewSource,
    private val nowMillis: () -> Long,
    private val publish: (ForegroundGpsPreviewState) -> Unit
) {
    private var active = false
    private var listening = false
    private var generation = 0L
    private var stopUpdates: (() -> Unit)? = null
    private val evidence = ForegroundGpsPreviewEvidence()

    fun start() {
        if (active) return
        active = true
        tick()
    }

    fun tick() {
        if (!active) return
        val available = try { source.availability() } catch (_: RuntimeException) {
            GpsPreviewAvailability.UNAVAILABLE
        }
        if (available != GpsPreviewAvailability.READY) {
            suspendPreview(when (available) {
                GpsPreviewAvailability.PERMISSION_NEEDED -> ForegroundGpsPreviewStatus.PERMISSION_NEEDED
                GpsPreviewAvailability.LOCATION_OFF -> ForegroundGpsPreviewStatus.LOCATION_OFF
                else -> ForegroundGpsPreviewStatus.UNAVAILABLE
            })
            return
        }
        if (!listening) {
            evidence.clear()
            val lease = ++generation
            listening = true
            try {
                val close = source.listen(
                    onSample = { fix ->
                        if (active && listening && lease == generation) {
                            evidence.observe(fix.sample, nowMillis(), isMock = fix.isMock)
                            publish(evidence.state(nowMillis()))
                        }
                    },
                    onProviderDisabled = {
                        if (active && listening && lease == generation) {
                            suspendPreview(ForegroundGpsPreviewStatus.LOCATION_OFF)
                        }
                    }
                )
                if (!active || !listening || lease != generation) {
                    // A source may synchronously report disable while registration is returning.
                    closeQuietly(close)
                    return
                }
                stopUpdates = close
                source.lastKnownSample()?.let {
                    evidence.observe(it.sample, nowMillis(), allowStaleLastKnown = true,
                        isLive = false, isMock = it.isMock)
                }
            } catch (_: SecurityException) {
                suspendPreview(ForegroundGpsPreviewStatus.PERMISSION_NEEDED)
                return
            } catch (_: RuntimeException) {
                suspendPreview(ForegroundGpsPreviewStatus.UNAVAILABLE)
                return
            }
        }
        publish(evidence.state(nowMillis()))
    }

    fun stop() {
        active = false
        suspendPreview(ForegroundGpsPreviewStatus.DISABLED)
    }

    private fun suspendPreview(status: ForegroundGpsPreviewStatus) {
        generation++
        listening = false
        val close = stopUpdates
        stopUpdates = null
        if (close != null) closeQuietly(close)
        evidence.clear()
        publish(ForegroundGpsPreviewState(status))
    }

    private fun closeQuietly(close: () -> Unit) {
        try { close() } catch (_: RuntimeException) {
            // Revocation can make Android reject removal; this owner's callbacks stay invalid.
        }
    }
}

/** Source time ages a fix; callbacks and cached-fix reads never refresh its original timestamp. */
internal class ForegroundGpsPreviewEvidence {
    companion object {
        /** A bounded initial map anchor, never a claim that the runner is still there. */
        internal const val MAX_LAST_KNOWN_AGE_MILLIS = 30 * 60 * 1_000L
        /** Distinct from map freshness and shorter than the recording policy's sample-age bound. */
        internal const val MAX_START_FIX_AGE_MILLIS = 5_000L
    }

    private val config = GpsDistanceConfig()
    private var lastSeenSourceMillis: Long? = null
    private var lastUsable: GpsSample? = null
    private var lastLiveUsable: GpsSample? = null
    private var latestWeak = false
    private var sawStale = false

    fun clear() {
        lastSeenSourceMillis = null
        lastUsable = null
        lastLiveUsable = null
        latestWeak = false
        sawStale = false
    }

    fun observe(
        sample: GpsSample,
        nowElapsedRealtimeMillis: Long,
        allowStaleLastKnown: Boolean = false,
        isLive: Boolean = true,
        isMock: Boolean = false
    ) {
        if (lastSeenSourceMillis?.let { sample.elapsedRealtimeMillis <= it } == true) return
        if (isMock) {
            latestWeak = true
            lastLiveUsable = null
            return
        }
        if (sample.elapsedRealtimeMillis < 0L || sample.elapsedRealtimeMillis > nowElapsedRealtimeMillis) {
            latestWeak = true
            lastLiveUsable = null
            return
        }
        lastSeenSourceMillis = sample.elapsedRealtimeMillis
        val accuracy = sample.horizontalAccuracyMeters
        if (!sample.latitudeDegrees.isFinite() || sample.latitudeDegrees !in -90.0..90.0 ||
            !sample.longitudeDegrees.isFinite() || sample.longitudeDegrees !in -180.0..180.0 ||
            accuracy == null || !accuracy.isFinite() || accuracy <= 0.0 ||
            accuracy > config.maxHorizontalAccuracyMeters || sample.observedEpochMillis <= 0L
        ) {
            latestWeak = true
            lastLiveUsable = null
            return
        }
        val ageMillis = nowElapsedRealtimeMillis - sample.elapsedRealtimeMillis
        if (ageMillis > config.maxPointIntervalMillis &&
            (!allowStaleLastKnown || ageMillis > MAX_LAST_KNOWN_AGE_MILLIS)
        ) {
            sawStale = true
            lastLiveUsable = null
            return
        }
        lastUsable = sample
        if (isLive) lastLiveUsable = sample
        latestWeak = false
        sawStale = false
    }

    fun state(nowElapsedRealtimeMillis: Long): ForegroundGpsPreviewState {
        val usable = lastUsable
        val expired = usable != null && (nowElapsedRealtimeMillis < usable.elapsedRealtimeMillis ||
            nowElapsedRealtimeMillis - usable.elapsedRealtimeMillis > config.maxPointIntervalMillis)
        val status = when {
            expired || (usable == null && sawStale) -> ForegroundGpsPreviewStatus.STALE
            latestWeak -> ForegroundGpsPreviewStatus.WEAK
            usable != null -> ForegroundGpsPreviewStatus.CURRENT
            else -> ForegroundGpsPreviewStatus.ACQUIRING
        }
        val boundedLastKnown = usable?.takeIf {
            nowElapsedRealtimeMillis >= it.elapsedRealtimeMillis &&
                nowElapsedRealtimeMillis - it.elapsedRealtimeMillis <= MAX_LAST_KNOWN_AGE_MILLIS
        }
        val hasFreshLiveFix = status == ForegroundGpsPreviewStatus.CURRENT && lastLiveUsable?.let {
            nowElapsedRealtimeMillis >= it.elapsedRealtimeMillis &&
                nowElapsedRealtimeMillis - it.elapsedRealtimeMillis <= MAX_START_FIX_AGE_MILLIS
        } == true
        return ForegroundGpsPreviewState(status, boundedLastKnown?.let {
            GpsMapMarker(GpsMapCoordinate(it.latitudeDegrees, it.longitudeDegrees),
                it.horizontalAccuracyMeters,
                if (status == ForegroundGpsPreviewStatus.CURRENT) GpsMapMarkerState.CURRENT
                else GpsMapMarkerState.LAST_KNOWN)
        }, hasFreshLiveFix)
    }
}

private class AndroidForegroundGpsPreviewSource(private val context: Context) : ForegroundGpsPreviewSource {
    private val manager = context.getSystemService(LocationManager::class.java)

    override fun availability(): GpsPreviewAvailability {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) return GpsPreviewAvailability.PERMISSION_NEEDED
        val locationManager = manager ?: return GpsPreviewAvailability.UNAVAILABLE
        return if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) GpsPreviewAvailability.READY
        else GpsPreviewAvailability.LOCATION_OFF
    }

    @SuppressLint("MissingPermission") // Rechecked on every foreground tick; revocation is handled by the owner.
    override fun lastKnownSample(): ForegroundGpsPreviewFix? =
        manager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.previewFix()

    @SuppressLint("MissingPermission")
    override fun listen(onSample: (ForegroundGpsPreviewFix) -> Unit, onProviderDisabled: () -> Unit): () -> Unit {
        val locationManager = checkNotNull(manager)
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) { onSample(location.previewFix()) }
            override fun onProviderDisabled(provider: String) {
                if (provider == LocationManager.GPS_PROVIDER) onProviderDisabled()
            }
            override fun onProviderEnabled(provider: String) = Unit
            @Deprecated("Android no longer uses provider status callbacks.")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1_000L, 0f,
                listener, Looper.getMainLooper())
        } catch (failure: RuntimeException) {
            try { locationManager.removeUpdates(listener) } catch (_: RuntimeException) { }
            throw failure
        }
        return { locationManager.removeUpdates(listener) }
    }
}

private fun Location.previewFix() = ForegroundGpsPreviewFix(GpsSample(
    latitudeDegrees = latitude,
    longitudeDegrees = longitude,
    horizontalAccuracyMeters = if (hasAccuracy()) accuracy.toDouble() else null,
    observedEpochMillis = time,
    elapsedRealtimeMillis = elapsedRealtimeNanos / 1_000_000L
), isMock = LocationCompat.isMock(this))
