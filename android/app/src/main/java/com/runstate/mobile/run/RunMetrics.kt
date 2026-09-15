package com.runstate.mobile.run

import com.runstate.mobile.data.local.DistanceUnit
import com.runstate.mobile.data.local.MetricSource
import com.runstate.mobile.data.local.TelemetryCoverage

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
