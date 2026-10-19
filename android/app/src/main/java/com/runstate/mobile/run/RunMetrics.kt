package com.runstate.mobile.run

import com.runstate.mobile.data.local.DistanceUnit
import com.runstate.mobile.data.local.MetricSource
import com.runstate.mobile.data.local.TelemetryCoverage
import kotlin.math.floor
import kotlin.math.roundToLong

/**
 * One read-only metric answer for the run this process currently owns.
 *
 * This is deliberately smaller than a stored run. No UUID, Room entity, session owner or
 * exception crosses the coordinator boundary. Live values are derived from the durable
 * timeline; completed distance is the value frozen by the completion transaction.
 *
 * [activeMillis], [distanceMeters] and [paceSecondsPerMeter] are nullable because a
 * migrated run can have an honest gap in its transition history. Null means unavailable,
 * never zero.
 */
internal data class RunMetrics(
    val elapsedMillis: Long,
    val activeMillis: Long?,
    val distanceMeters: Double?,
    val paceSecondsPerMeter: Double?,
    val displayUnit: DistanceUnit,
    val source: MetricSource?,
    val coverage: TelemetryCoverage
) {
    init {
        require(elapsedMillis >= 0L) {
            "Elapsed duration cannot be negative: $elapsedMillis."
        }
        require(activeMillis == null || activeMillis in 0L..elapsedMillis) {
            "Active duration must be null or between zero and elapsed duration: " +
                "$activeMillis of $elapsedMillis."
        }
        require(distanceMeters == null || (distanceMeters.isFinite() && distanceMeters >= 0.0)) {
            "Distance must be null or a finite, non-negative value: $distanceMeters."
        }
        require(
            paceSecondsPerMeter == null ||
                (paceSecondsPerMeter.isFinite() && paceSecondsPerMeter > 0.0)
        ) {
            "Average pace must be null or a finite, positive value: $paceSecondsPerMeter."
        }
        require(paceSecondsPerMeter == null || (activeMillis != null && distanceMeters != null)) {
            "Average pace requires both active duration and distance."
        }

        when (coverage) {
            TelemetryCoverage.UNAVAILABLE -> require(
                distanceMeters == null && paceSecondsPerMeter == null
            ) {
                "Unavailable telemetry cannot claim distance or pace."
            }

            TelemetryCoverage.COMPLETE,
            TelemetryCoverage.PARTIAL -> {
                require(source != null) {
                    "$coverage telemetry must identify its source."
                }
                require(distanceMeters != null) {
                    "$coverage telemetry must carry a distance."
                }
            }
        }
    }
}

/** Consumes crossings once, including silent ones. Recovery seeds from durable distance. */
internal class RunMilestoneAnnouncements(initialDistanceMeters: Double = 0.0) {
    private var passedMile = mileAt(initialDistanceMeters)

    fun update(metrics: RunMetrics, running: Boolean, enabled: Boolean): String? {
        val distance = metrics.distanceMeters ?: return null
        val mile = mileAt(distance)
        if (mile <= passedMile) return null
        val previous = passedMile
        passedMile = mile // Failed speech, disabled updates and recovery never produce a backlog.
        if (!running || !enabled || metrics.source != MetricSource.GPS ||
            metrics.coverage != TelemetryCoverage.COMPLETE || mile != previous + 1 ||
            distance - mile * METERS_PER_MILE > 160.9344) return null
        val paceSeconds = metrics.paceSecondsPerMeter?.times(METERS_PER_MILE)
            ?.takeIf { it.isFinite() && it > 0 && it < Long.MAX_VALUE.toDouble() }?.roundToLong()
        val activeSeconds = metrics.activeMillis?.div(1_000)
        return listOfNotNull(
            "$mile ${if (mile == 1) "mile" else "miles"} completed.",
            paceSeconds?.let { "Average pace, ${spokenDuration(it)} per mile." },
            activeSeconds?.let { "Active time, ${spokenDuration(it)}." }
        ).joinToString(" ")
    }

    private fun mileAt(distance: Double): Int = if (distance.isFinite() && distance >= 0)
        floor(distance / METERS_PER_MILE).coerceAtMost(Int.MAX_VALUE.toDouble()).toInt() else 0

    private fun spokenDuration(seconds: Long): String {
        val parts = listOf(seconds / 3_600 to "hour", seconds / 60 % 60 to "minute", seconds % 60 to "second")
            .filter { it.first > 0 }.map { (n, unit) -> "$n $unit${if (n == 1L) "" else "s"}" }
        return parts.ifEmpty { listOf("0 seconds") }.joinToString(", ")
    }

    private companion object { const val METERS_PER_MILE = 1_609.344 }
}
