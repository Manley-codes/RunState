package com.runstate.mobile.ui

import com.runstate.mobile.data.local.MetricSource
import com.runstate.mobile.data.local.RunEntity
import com.runstate.mobile.data.local.RunTrackingMode
import com.runstate.mobile.data.local.StoredRunState
import com.runstate.mobile.data.local.TelemetryCoverage

/** A factual finish cue, separate from generated reflections and History sorting. */
internal fun longestRunAchievementFor(currentRun: RunEntity, previousRuns: List<RunEntity>): String? {
    val distance = currentRun.recordEligibleDistance() ?: return null
    val previousBest = previousRuns.asSequence()
        .filter { it.runId != currentRun.runId && it.activityFinalized &&
            it.finishEpochMillis?.let { finish -> finish <= currentRun.officialStartEpochMillis } == true }
        .mapNotNull { it.recordEligibleDistance() }
        .maxOrNull() ?: return null // The first eligible run establishes a baseline.
    return if (distance > previousBest) "Your longest recorded run." else null
}

private fun RunEntity.recordEligibleDistance(): Double? {
    if (state != StoredRunState.COMPLETED || trackingMode != RunTrackingMode.GPS ||
        metricSource != MetricSource.GPS || telemetryCoverage != TelemetryCoverage.COMPLETE ||
        finishEpochMillis == null || finishEpochMillis < officialStartEpochMillis) return null
    return finalDistanceMeters?.takeIf { it.isFinite() && it > 0.0 }
}
