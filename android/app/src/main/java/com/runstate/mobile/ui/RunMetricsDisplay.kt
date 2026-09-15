package com.runstate.mobile.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.runstate.mobile.data.local.DistanceUnit
import com.runstate.mobile.data.local.MetricSource
import com.runstate.mobile.run.RunMetrics
import java.util.Locale
import kotlinx.coroutines.delay
import kotlin.math.roundToLong

/** Live metric refresh cadence. Timestamps, not tick counts, remain the source of truth. */
internal const val METRIC_TICK_MILLIS = 1_000L

/** Text-only values the current foundation screen can draw without owning run data. */
internal data class RunMetricsDisplay(
    val elapsed: String,
    val active: String,
    val distance: String,
    val averagePace: String,
    val sourceLabel: String
)

/** Converts stored-unit-independent metrics into stable runner-facing text. */
internal fun runMetricsDisplayFor(metrics: RunMetrics): RunMetricsDisplay {
    val (metersPerUnit, distanceSuffix, paceSuffix) = when (metrics.displayUnit) {
        DistanceUnit.MILES -> Triple(1_609.344, "mi", "/mi")
        DistanceUnit.KILOMETERS -> Triple(1_000.0, "km", "/km")
    }

    val distance = metrics.distanceMeters?.let { meters ->
        String.format(Locale.US, "%.2f %s", meters / metersPerUnit, distanceSuffix)
    } ?: "—"

    val averagePace = metrics.paceSecondsPerMeter?.let { secondsPerMeter ->
        val totalSeconds = (secondsPerMeter * metersPerUnit).roundToLong()
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        String.format(Locale.US, "%d:%02d %s", minutes, seconds, paceSuffix)
    } ?: "—"

    return RunMetricsDisplay(
        elapsed = formatDuration(metrics.elapsedMillis),
        active = metrics.activeMillis?.let(::formatDuration) ?: "—",
        distance = distance,
        averagePace = averagePace,
        sourceLabel = when (metrics.source) {
            MetricSource.FIXTURE -> "Fixture metrics"
            MetricSource.GPS -> "GPS metrics"
            null -> "Metrics unavailable"
        }
    )
}

/**
 * Runs [onTick] only while a live metric surface is active and the Activity is visible.
 *
 * Dropping below STARTED cancels the loop. Returning performs an immediate fresh read from
 * the process coordinator, so time spent backgrounded is reconstructed from timestamps
 * instead of from ticks nobody observed.
 */
@Composable
internal fun RunMetricsTicker(
    active: Boolean,
    lifecycle: Lifecycle,
    onTick: suspend () -> Unit
) {
    val currentOnTick by rememberUpdatedState(onTick)

    LaunchedEffect(active, lifecycle) {
        if (active) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    currentOnTick()
                    delay(METRIC_TICK_MILLIS)
                }
            }
        }
    }
}

/** Hours remain visible from the first second so the layout never changes shape mid-run. */
private fun formatDuration(durationMillis: Long): String {
    val totalSeconds = durationMillis / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
}
