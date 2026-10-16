package com.runstate.mobile.location

/** Freshness of usable GPS evidence; separate from whether any distance was measured earlier. */
internal enum class GpsSignalStatus { ACQUIRING, RECORDING, WEAK, STALE }

/** Room may reject a fix across a pause/checkpoint even when the distance policy accepted it. */
internal fun isDurablyUsableGpsFix(
    decision: GpsSampleStatus,
    storedAcceptedForDistance: Boolean,
    storedRejectionReason: String?
): Boolean = when (decision) {
    GpsSampleStatus.ACCEPTED_ANCHOR, GpsSampleStatus.ACCEPTED_SEGMENT ->
        storedAcceptedForDistance && storedRejectionReason == null
    GpsSampleStatus.IGNORED_WITHIN_ACCURACY ->
        !storedAcceptedForDistance && storedRejectionReason == decision.name
    else -> false
}

/**
 * A monotonic clock-driven view of committed fixes. A stationary, accurate fix is usable even
 * though it adds no distance. The source fix time, never delivery/write completion, sets freshness.
 * The 15-second default matches the distance policy's maximum gap; it is not an accuracy claim.
 */
internal class GpsSignalMonitor(
    private val staleAfterMillis: Long = GpsDistanceConfig().maxPointIntervalMillis
) {
    init { require(staleAfterMillis > 0L) }

    private var startedAtMillis = 0L
    private var lastProcessedFixMillis: Long? = null
    private var lastUsableFixMillis: Long? = null
    private var latestWasUnusable = false

    fun reset(nowElapsedRealtimeMillis: Long) {
        require(nowElapsedRealtimeMillis >= 0L)
        startedAtMillis = nowElapsedRealtimeMillis
        lastProcessedFixMillis = null
        lastUsableFixMillis = null
        latestWasUnusable = false
    }

    fun onStoredFix(
        decision: GpsSampleStatus,
        fixElapsedRealtimeMillis: Long,
        durablyUsable: Boolean,
        nowElapsedRealtimeMillis: Long
    ): GpsSignalStatus {
        require(nowElapsedRealtimeMillis >= 0L)
        if (decision == GpsSampleStatus.REJECTED_OUT_OF_ORDER ||
            decision == GpsSampleStatus.REJECTED_NOT_RUNNING
        ) return status(nowElapsedRealtimeMillis)
        if (fixElapsedRealtimeMillis < startedAtMillis ||
            fixElapsedRealtimeMillis > nowElapsedRealtimeMillis
        ) {
            latestWasUnusable = true
            return status(nowElapsedRealtimeMillis)
        }
        // Repeated storage results cannot refresh the clock or replace a newer signal assessment.
        if (lastProcessedFixMillis?.let { fixElapsedRealtimeMillis <= it } == true) {
            return status(nowElapsedRealtimeMillis)
        }
        lastProcessedFixMillis = fixElapsedRealtimeMillis
        latestWasUnusable = !durablyUsable
        if (durablyUsable) lastUsableFixMillis = fixElapsedRealtimeMillis
        return status(nowElapsedRealtimeMillis)
    }

    fun status(nowElapsedRealtimeMillis: Long): GpsSignalStatus {
        require(nowElapsedRealtimeMillis >= startedAtMillis)
        val freshnessOrigin = lastUsableFixMillis ?: startedAtMillis
        if (nowElapsedRealtimeMillis - freshnessOrigin > staleAfterMillis) {
            return GpsSignalStatus.STALE
        }
        return when {
            latestWasUnusable -> GpsSignalStatus.WEAK
            lastUsableFixMillis != null -> GpsSignalStatus.RECORDING
            else -> GpsSignalStatus.ACQUIRING
        }
    }
}
