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
    val coverage: TelemetryCoverage,
    /** Live average since the latest mile boundary, unavailable while evidence is insufficient. */
    val currentMilePaceSecondsPerMeter: Double? = null
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
        require(currentMilePaceSecondsPerMeter == null ||
            (currentMilePaceSecondsPerMeter.isFinite() && currentMilePaceSecondsPerMeter > 0.0 &&
                activeMillis != null && distanceMeters != null && source == MetricSource.GPS &&
                coverage == TelemetryCoverage.COMPLETE)) {
            "Current-mile pace requires complete GPS evidence and a finite, positive pace."
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

/** A brief GPS recovery may finish a fresh crossing; old or deliberately skipped miles stay silent. */
internal class RunMilestoneAnnouncements(initialDistanceMeters: Double = 0.0) {
    private var passedMile = mileAt(initialDistanceMeters)
    private var lastDistanceMeters = initialDistanceMeters
    private var pendingMile: PendingMile? = null

    /** A pause, disabled setting or interrupted observation must never become a delayed cue. */
    fun clearPending() {
        pendingMile = null
    }

    fun update(
        metrics: RunMetrics?,
        running: Boolean,
        enabled: Boolean,
        nowElapsedMillis: Long = metrics?.elapsedMillis ?: 0L
    ): String? {
        if (metrics == null) {
            pendingMile = null
            return null
        }
        val distance = metrics.distanceMeters
        if (distance == null) {
            pendingMile = null
            return null
        }
        val mile = mileAt(distance)
        val movedBackward = distance < lastDistanceMeters
        lastDistanceMeters = distance
        if (!running || !enabled || metrics.source != MetricSource.GPS || movedBackward) {
            passedMile = maxOf(passedMile, mile)
            pendingMile = null
            return null
        }
        if (mile > passedMile) {
            val previous = passedMile
            passedMile = mile // Recovery/large jumps cannot queue earlier milestones.
            pendingMile = if (mile == previous + 1 && distance - mile * METERS_PER_MILE <= MAX_OVERSHOOT_METERS)
                PendingMile(mile, nowElapsedMillis) else null
        }
        val pending = pendingMile ?: return null
        if (mile != pending.number || nowElapsedMillis - pending.observedAtElapsedMillis !in 0L..GPS_GRACE_MILLIS ||
            distance - mile * METERS_PER_MILE > MAX_OVERSHOOT_METERS) {
            pendingMile = null
            return null
        }
        if (metrics.coverage != TelemetryCoverage.COMPLETE) return null
        pendingMile = null // Once offered to speech it cannot become a duplicate after interruption.
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

    private data class PendingMile(val number: Int, val observedAtElapsedMillis: Long)

    private companion object {
        const val METERS_PER_MILE = 1_609.344
        const val MAX_OVERSHOOT_METERS = 160.9344
        const val GPS_GRACE_MILLIS = 15_000L
    }
}
