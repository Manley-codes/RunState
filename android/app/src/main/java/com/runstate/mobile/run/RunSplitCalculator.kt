package com.runstate.mobile.run

import com.runstate.mobile.data.local.DistanceUnit
import com.runstate.mobile.data.local.MetricSource
import com.runstate.mobile.data.local.RunEntity
import com.runstate.mobile.data.local.RunLocationPointEntity
import com.runstate.mobile.data.local.RunTrackingMode
import com.runstate.mobile.data.local.RunTransitionEntity
import com.runstate.mobile.data.local.RunTransitionType
import com.runstate.mobile.data.local.StoredRunState
import com.runstate.mobile.data.local.TelemetryCoverage
import com.runstate.mobile.location.GpsCoverageEvaluator
import com.runstate.mobile.location.GpsSampleStatus
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToLong

/** One derived, unit-sized interval, or the final remaining distance of a completed run. */
data class RunSplit(
    val index: Int,
    val startDistanceMeters: Double,
    val endDistanceMeters: Double,
    val startActiveMillis: Long,
    val endActiveMillis: Long,
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val isPartial: Boolean
) {
    val durationMillis: Long get() = endActiveMillis - startActiveMillis
    val paceSecondsPerMeter: Double
        get() = (durationMillis / 1_000.0) / (endDistanceMeters - startDistanceMeters)
}

/**
 * Derives splits only from completed, continuous GPS evidence. This reads no clock or storage.
 *
 * Source monotonic time locates a distance boundary inside an accepted segment; the official
 * lifecycle timeline supplies its saved epoch and active time. In particular, a bounded GPS wall
 * clock bias is never added to a split's time. Accurate stationary fixes keep distance constant,
 * while their Running time remains in the containing split. Paused gaps add neither.
 */
internal object RunSplitCalculator {
    const val DERIVATION_VERSION = 1

    fun calculate(
        run: RunEntity,
        transitions: List<RunTransitionEntity>,
        points: List<RunLocationPointEntity>
    ): List<RunSplit>? = try {
        derive(run, transitions, points)
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: IllegalStateException) {
        null
    } catch (_: ArithmeticException) {
        null
    }

    private fun derive(
        run: RunEntity,
        transitions: List<RunTransitionEntity>,
        points: List<RunLocationPointEntity>
    ): List<RunSplit>? {
        if (run.state != StoredRunState.COMPLETED || run.trackingMode != RunTrackingMode.GPS ||
            run.metricSource != MetricSource.GPS || run.telemetryCoverage != TelemetryCoverage.COMPLETE ||
            run.transitionHistoryComplete != true
        ) return null
        val finish = run.finishEpochMillis ?: return null
        if (run.lastCheckpointEpochMillis != finish) return null
        val finalDistance = run.finalDistanceMeters?.takeIf { it.isFinite() && it > 0.0 } ?: return null
        val unitMeters = when (run.displayDistanceUnit ?: return null) {
            DistanceUnit.MILES -> 1_609.344
            DistanceUnit.KILOMETERS -> 1_000.0
        }
        val finalPause = transitions.lastOrNull()?.takeIf {
            it.transitionType == RunTransitionType.PAUSE && it.occurredAtEpochMillis <= finish
        } ?: return null
        val finalMono = finalPause.elapsedRealtimeMillis ?: return null
        val sessionId = run.timingSessionId ?: return null

        // A frozen COMPLETE flag alone is insufficient. Recheck the underlying evidence using
        // exactly the live coverage policy, at the last active checkpoint rather than after its
        // potentially long final pause. No saved row or completed verdict is changed.
        val atFinalPause = run.copy(
            state = StoredRunState.PAUSED,
            lastCheckpointEpochMillis = finalPause.occurredAtEpochMillis,
            finishEpochMillis = null,
            finalDistanceMeters = null,
            metricSource = null,
            telemetryCoverage = null,
            displayDistanceUnit = null,
            postRunEffort = null
        )
        if (GpsCoverageEvaluator.assess(
                atFinalPause, transitions, points, finalPause.occurredAtEpochMillis,
                RunTimingMark(finalMono, sessionId)
            ) != TelemetryCoverage.COMPLETE
        ) return null

        val intervals = runningIntervals(run, transitions)
        val activeTotal = RunTimeline.activeMillis(
            run.officialStartEpochMillis, finish, run.state, transitions, finish,
            TransitionHistoryCoverage.COMPLETE
        ) ?: return null
        if (activeTotal <= 0L || intervals.lastOrNull()?.activeEnd != activeTotal) return null

        val boundaries = mutableListOf<Boundary>()
        var nextDistance = unitMeters
        var intervalIndex = 0
        var cumulative = 0.0
        var previousKnot: Knot? = null
        points.forEach { point ->
            // Rejections were assessed above. Only accepted or accurate stationary Running
            // fixes may position a boundary; delayed callbacks never move it backwards.
            if (!point.acceptedForDistance &&
                point.rejectionReason != GpsSampleStatus.IGNORED_WITHIN_ACCURACY.name
            ) return@forEach
            val mono = checkNotNull(point.elapsedRealtimeNanos) / 1_000_000L
            while (intervalIndex < intervals.size && mono > intervals[intervalIndex].endMono) {
                intervalIndex++
                previousKnot = null
            }
            val interval = intervals.getOrNull(intervalIndex) ?: return null
            if (mono < interval.startMono) return null
            if (point.acceptedForDistance) {
                cumulative = point.cumulativeDistanceMeters ?: cumulative
            }
            val current = Knot(mono, cumulative)
            val previous = previousKnot
            if (previous != null && current.distance > previous.distance) {
                while (nextDistance <= finalDistance || sameBoundaryDistance(nextDistance, finalDistance)) {
                    if (nextDistance > current.distance && !sameBoundaryDistance(nextDistance, current.distance)) break
                    val fraction = ((nextDistance - previous.distance) /
                        (current.distance - previous.distance)).coerceIn(0.0, 1.0)
                    val boundaryMono = previous.mono.toDouble() +
                        (current.mono - previous.mono).toDouble() * fraction
                    val runningFraction = ((boundaryMono - interval.startMono.toDouble()) /
                        (interval.endMono - interval.startMono).toDouble()).coerceIn(0.0, 1.0)
                    val wallOffset = (interval.durationMillis.toDouble() * runningFraction).roundToLong()
                    boundaries.add(Boundary(
                        distance = minOf(nextDistance, finalDistance),
                        active = Math.addExact(interval.activeStart, wallOffset),
                        epoch = Math.addExact(interval.startEpoch, wallOffset)
                    ))
                    val followingDistance = (boundaries.size.toDouble() + 1.0) * unitMeters
                    if (!followingDistance.isFinite() || followingDistance <= nextDistance) return null
                    nextDistance = followingDistance
                }
            }
            previousKnot = current
        }
        if (!sameDistance(cumulative, finalDistance)) return null

        val finalBoundary = Boundary(finalDistance, activeTotal, finalPause.occurredAtEpochMillis)
        val exactUnitFinish = boundaries.lastOrNull()?.let {
            sameBoundaryDistance(it.distance, finalDistance)
        } == true
        if (exactUnitFinish) {
            // A final stationary tail still belongs to the run, including when distance landed
            // exactly on a full unit. Do not create a zero-distance partial or lose that time.
            boundaries[boundaries.lastIndex] = finalBoundary
        } else {
            boundaries.add(finalBoundary)
        }

        var start = Boundary(0.0, 0L, run.officialStartEpochMillis)
        return boundaries.mapIndexed { index, end ->
            if (end.distance <= start.distance || end.active <= start.active || end.epoch < start.epoch) {
                return null
            }
            val split = RunSplit(
                index = index + 1,
                startDistanceMeters = start.distance,
                endDistanceMeters = end.distance,
                startActiveMillis = start.active,
                endActiveMillis = end.active,
                startEpochMillis = start.epoch,
                endEpochMillis = end.epoch,
                isPartial = index == boundaries.lastIndex && !exactUnitFinish
            )
            if (!split.paceSecondsPerMeter.isFinite() || split.paceSecondsPerMeter <= 0.0) return null
            start = end
            split
        }
    }

    private fun runningIntervals(
        run: RunEntity,
        transitions: List<RunTransitionEntity>
    ): List<RunningInterval> {
        val intervals = mutableListOf<RunningInterval>()
        var startMono = checkNotNull(run.startElapsedRealtimeMillis)
        var startEpoch = run.officialStartEpochMillis
        var active = 0L
        transitions.forEach { transition ->
            when (transition.transitionType) {
                RunTransitionType.PAUSE -> {
                    val mono = checkNotNull(transition.elapsedRealtimeMillis)
                    val duration = Math.subtractExact(transition.occurredAtEpochMillis, startEpoch)
                    if (mono > startMono && duration > 0L) {
                        intervals.add(RunningInterval(
                            startMono, mono, startEpoch, duration, active, Math.addExact(active, duration)
                        ))
                    }
                    active = Math.addExact(active, duration)
                }
                RunTransitionType.RESUME -> {
                    startMono = checkNotNull(transition.elapsedRealtimeMillis)
                    startEpoch = transition.occurredAtEpochMillis
                }
            }
        }
        return intervals
    }

    private fun sameDistance(first: Double, second: Double): Boolean =
        first.isFinite() && second.isFinite() && abs(first - second) <= max(0.000001, abs(second) * 0.000000001)

    private fun sameBoundaryDistance(first: Double, second: Double): Boolean =
        abs(first - second) <= 0.000001

    private data class Knot(val mono: Long, val distance: Double)
    private data class Boundary(val distance: Double, val active: Long, val epoch: Long)
    private data class RunningInterval(
        val startMono: Long,
        val endMono: Long,
        val startEpoch: Long,
        val durationMillis: Long,
        val activeStart: Long,
        val activeEnd: Long
    )
}
