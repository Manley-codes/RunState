package com.runstate.mobile.weather

import com.runstate.mobile.data.local.RunEntity
import com.runstate.mobile.data.local.RunLocationPointEntity
import com.runstate.mobile.data.local.RunTrackingMode
import com.runstate.mobile.data.local.RunWeatherStore
import com.runstate.mobile.data.local.StoredRunState
import kotlin.math.abs
import kotlin.math.round
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** One process-owned optional lookup after a durable near-start GPS fix, never a tracking dependency. */
internal class RunWeatherCapture(
    private val scope: CoroutineScope,
    private val store: RunWeatherStore,
    private val gateway: RunWeatherGateway = OpenMeteoWeatherClient(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val seenRuns = mutableSetOf<String>()

    /**
     * Called on main after persistence. True means this run was handled or work was scheduled;
     * false leaves room for a later eligible fix. Database work and HTTP stay off this callback.
     */
    fun onPoint(run: RunEntity, point: RunLocationPointEntity, allowed: Boolean): Boolean {
        if (run.state != StoredRunState.RUNNING || run.trackingMode != RunTrackingMode.GPS ||
            point.runId != run.runId || !point.acceptedForDistance) return false
        if (run.runId in seenRuns) return true
        // Older/recovered runs without a monotonic start cannot establish this bounded window.
        // Stop offering them without manufacturing a historical weather attempt.
        val startElapsed = run.startElapsedRealtimeMillis
        if (startElapsed == null || run.timingSessionId == null) {
            seenRuns.add(run.runId)
            return true
        }
        val epochExpired = point.observedAtEpochMillis > run.officialStartEpochMillis &&
            point.observedAtEpochMillis - run.officialStartEpochMillis > 120_000L
        val monotonicExpired = point.elapsedRealtimeNanos?.div(1_000_000L)?.let { elapsed ->
            elapsed > startElapsed && elapsed - startElapsed > 120_000L
        } == true
        val expired = epochExpired || monotonicExpired
        if (!expired && !eligible(run, point)) return false
        seenRuns.add(run.runId)
        val latitude = round(point.latitude * 100.0) / 100.0
        val longitude = round(point.longitude * 100.0) / 100.0
        scope.launch(ioDispatcher) {
            var pending = false
            try {
                if (!store.claim(run.runId, allowed)) return@launch
                if (!allowed) return@launch
                pending = true
                val snapshot = if (expired) null else withTimeoutOrNull(WEATHER_TIMEOUT_MILLIS) {
                    gateway.fetch(run.officialStartEpochMillis, latitude, longitude)
                }?.takeIf { it.targetEpochMillis == run.officialStartEpochMillis }
                store.finish(run.runId, snapshot?.toJson())
                pending = false
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Saving/finishing a run and its reflection do not depend on optional weather.
            } finally {
                if (pending) withContext(NonCancellable) {
                    try { store.finish(run.runId, null) } catch (_: Exception) { }
                }
            }
        }
        return true
    }

    private fun eligible(run: RunEntity, point: RunLocationPointEntity): Boolean {
        if (run.state != StoredRunState.RUNNING || run.trackingMode != RunTrackingMode.GPS ||
            point.runId != run.runId || !point.acceptedForDistance || run.timingSessionId == null) return false
        val accuracy = point.horizontalAccuracyMeters ?: return false
        if (!accuracy.isFinite() || accuracy <= 0f || accuracy > 25f) return false
        val startElapsed = run.startElapsedRealtimeMillis ?: return false
        val pointElapsed = point.elapsedRealtimeNanos?.div(1_000_000L) ?: return false
        if (pointElapsed < startElapsed || point.observedAtEpochMillis < run.officialStartEpochMillis) return false
        val elapsedDelta = pointElapsed - startElapsed
        val epochDelta = point.observedAtEpochMillis - run.officialStartEpochMillis
        return elapsedDelta in 0L..120_000L && epochDelta in 0L..120_000L &&
            abs(elapsedDelta - epochDelta) <= 2_000L
    }
}
