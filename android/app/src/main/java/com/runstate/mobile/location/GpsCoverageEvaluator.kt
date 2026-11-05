package com.runstate.mobile.location

import com.runstate.mobile.data.local.RunEntity
import com.runstate.mobile.data.local.RunLocationPointEntity
import com.runstate.mobile.data.local.RunTrackingMode
import com.runstate.mobile.data.local.RunTransitionEntity
import com.runstate.mobile.data.local.RunTransitionType
import com.runstate.mobile.data.local.StoredRunState
import com.runstate.mobile.data.local.TelemetryCoverage
import com.runstate.mobile.run.RunTimeline
import com.runstate.mobile.run.RunTimingMark
import com.runstate.mobile.run.TransitionHistoryCoverage
import kotlin.math.abs
import kotlin.math.max

/**
 * Certifies recording continuity, not GPS distance accuracy. Completed verdicts are immutable.
 *
 * Acquisition/tail edges and recovery from an inaccurate fix or implausible jump may each take
 * at most 15 seconds. Their combined unmeasured time must not exceed five percent of active time.
 * Recovery requires a fresh anchor with unchanged distance: rejected movement is never bridged.
 * Interior usable fixes still obey the distance policy's 15-second source-time limit. Accurate
 * stationary fixes count as coverage even though they deliberately add no distance.
 *
 * Lifecycle marks must share one process and agree with the wall timeline within 250 ms, both
 * at each boundary and for total active duration. GPS epoch time may have a stable offset from
 * the device's wall clock: its absolute offset is bounded to 2,000 ms, with at most 1,000 ms drift
 * from the first valid Running anchor. That reference never follows later drift or resets at a
 * pause. Location ordering, interval edges and gaps still use monotonic source time. These explicit
 * allowances are provisional recording rules, not a measured-route accuracy claim or permission
 * to infer missing movement.
 */
internal object GpsCoverageEvaluator {
    internal const val MAX_EDGE_MILLIS = 15_000L
    internal const val LIFECYCLE_CLOCK_TOLERANCE_MILLIS = 250L
    internal const val MAX_SOURCE_CLOCK_OFFSET_MILLIS = 2_000L
    internal const val SOURCE_CLOCK_TOLERANCE_MILLIS = 1_000L

    private val distanceConfig = GpsDistanceConfig()
    private val delayedReasons = setOf(GpsSampleStatus.REJECTED_OUT_OF_ORDER.name, "OUT_OF_ORDER")
    private val recoverableReasons = setOf(
        GpsSampleStatus.REJECTED_INACCURATE.name,
        GpsSampleStatus.REJECTED_IMPLAUSIBLE_JUMP.name
    )
    private val outsideRunningReasons = setOf(
        "NOT_RUNNING", "BEFORE_CHECKPOINT", GpsSampleStatus.REJECTED_NOT_RUNNING.name
    )

    fun assess(
        run: RunEntity,
        transitions: List<RunTransitionEntity>,
        points: List<RunLocationPointEntity>,
        nowEpochMillis: Long,
        nowTimingMark: RunTimingMark?,
        recoveredRunningGap: Boolean = false
    ): TelemetryCoverage {
        if (run.state == StoredRunState.COMPLETED) {
            return run.telemetryCoverage ?: if (run.finalDistanceMeters == null) {
                TelemetryCoverage.UNAVAILABLE
            } else TelemetryCoverage.PARTIAL
        }
        val hasDistance = points.any {
            it.runId == run.runId && it.acceptedForDistance &&
                it.cumulativeDistanceMeters?.let { distance ->
                    distance.isFinite() && distance >= 0.0
                } == true
        }
        if (!hasDistance) return TelemetryCoverage.UNAVAILABLE
        return try {
            if (certifies(run, transitions, points, nowEpochMillis, nowTimingMark, recoveredRunningGap)) {
                TelemetryCoverage.COMPLETE
            } else TelemetryCoverage.PARTIAL
        } catch (_: IllegalArgumentException) {
            TelemetryCoverage.PARTIAL
        } catch (_: IllegalStateException) {
            TelemetryCoverage.PARTIAL
        } catch (_: ArithmeticException) {
            TelemetryCoverage.PARTIAL
        }
    }

    private fun certifies(
        run: RunEntity,
        transitions: List<RunTransitionEntity>,
        points: List<RunLocationPointEntity>,
        nowEpochMillis: Long,
        nowTimingMark: RunTimingMark?,
        recoveredRunningGap: Boolean
    ): Boolean {
        if (run.trackingMode != RunTrackingMode.GPS || run.transitionHistoryComplete != true ||
            recoveredRunningGap || run.finishEpochMillis != null
        ) return false
        val startMono = run.startElapsedRealtimeMillis ?: return false
        val sessionId = run.timingSessionId ?: return false
        val now = nowTimingMark ?: return false
        if (now.sessionId != sessionId || now.elapsedRealtimeMillis < startMono) return false
        if (transitions.any { it.runId != run.runId } ||
            points.withIndex().any { (index, point) ->
                point.runId != run.runId || point.sequenceNumber != index + 1
            }
        ) return false

        val wallActive = RunTimeline.activeMillis(
            run.officialStartEpochMillis, null, run.state, transitions, nowEpochMillis,
            TransitionHistoryCoverage.COMPLETE
        ) ?: return false
        val checkpoint = transitions.lastOrNull()?.occurredAtEpochMillis ?: run.officialStartEpochMillis
        if (run.lastCheckpointEpochMillis != checkpoint || nowEpochMillis < checkpoint) return false

        fun clockOffsetMillis(epoch: Long, mono: Long): Long {
            val wallDelta = Math.subtractExact(epoch, run.officialStartEpochMillis)
            val monoDelta = Math.subtractExact(mono, startMono)
            return Math.subtractExact(wallDelta, monoDelta)
        }
        fun clocksAgree(epoch: Long, mono: Long, tolerance: Long): Boolean =
            clockOffsetMillis(epoch, mono) in -tolerance..tolerance
        if (!clocksAgree(nowEpochMillis, now.elapsedRealtimeMillis, LIFECYCLE_CLOCK_TOLERANCE_MILLIS)) {
            return false
        }

        val intervals = mutableListOf<RunningInterval>()
        var previousMono = startMono
        var openStart: Long? = startMono
        transitions.forEach { transition ->
            val mono = transition.elapsedRealtimeMillis ?: return false
            if (transition.timingSessionId != sessionId || mono < previousMono ||
                mono > now.elapsedRealtimeMillis ||
                !clocksAgree(transition.occurredAtEpochMillis, mono, LIFECYCLE_CLOCK_TOLERANCE_MILLIS)
            ) return false
            when (transition.transitionType) {
                RunTransitionType.PAUSE -> {
                    val start = openStart ?: return false
                    if (mono > start) intervals.add(RunningInterval(start, mono))
                    openStart = null
                }
                RunTransitionType.RESUME -> {
                    if (openStart != null) return false
                    openStart = mono
                }
            }
            previousMono = mono
        }
        if (run.state == StoredRunState.RUNNING) {
            val start = openStart ?: return false
            if (now.elapsedRealtimeMillis > start) intervals.add(RunningInterval(start, now.elapsedRealtimeMillis))
        } else if (openStart != null) return false
        if (intervals.isEmpty()) return false
        val monoActive = intervals.fold(0L) { total, interval ->
            Math.addExact(total, Math.subtractExact(interval.end, interval.start))
        }
        if (Math.subtractExact(wallActive, monoActive) !in
            -LIFECYCLE_CLOCK_TOLERANCE_MILLIS..LIFECYCLE_CLOCK_TOLERANCE_MILLIS
        ) return false

        var intervalIndex = 0
        var lastEvidenceMono: Long? = null
        var distanceAnchorMono: Long? = null
        var recoveryMillis = 0L
        var cumulative = 0.0
        var measuredSegmentSeen = false
        var sourceClockOffset: Long? = null
        var initialInaccurateLastMono: Long? = null
        val initialInaccurateOffsets = mutableListOf<Long>()
        fun sourceClocksAgree(epoch: Long, mono: Long): Boolean {
            val reference = sourceClockOffset ?: return false
            val offset = clockOffsetMillis(epoch, mono)
            if (offset !in -MAX_SOURCE_CLOCK_OFFSET_MILLIS..MAX_SOURCE_CLOCK_OFFSET_MILLIS) return false
            return Math.subtractExact(offset, reference) in
                -SOURCE_CLOCK_TOLERANCE_MILLIS..SOURCE_CLOCK_TOLERANCE_MILLIS
        }
        points.forEach { point ->
            val mono = point.elapsedRealtimeNanos?.div(1_000_000L) ?: return false
            val ignoredDelayed = !point.acceptedForDistance && point.rejectionReason in delayedReasons
            if (ignoredDelayed) {
                // A newer monotonic point rejected only by epoch order is a clock anomaly,
                // not evidence of harmless delayed delivery.
                if (lastEvidenceMono == null || mono > lastEvidenceMono!!) return false
                if (!sourceClocksAgree(point.observedAtEpochMillis, mono)) return false
                return@forEach
            }

            while (intervalIndex < intervals.size && mono > intervals[intervalIndex].end) {
                intervalIndex++
                distanceAnchorMono = null
            }
            val interval = intervals.getOrNull(intervalIndex)
            if (interval == null || mono < interval.start) {
                // Queued callbacks rejected while Paused do not spoil intentionally paused time.
                if (!point.acceptedForDistance && point.rejectionReason in outsideRunningReasons) return@forEach
                return false
            }
            if (lastEvidenceMono?.let { mono <= it } == true) return false
            lastEvidenceMono = mono
            // An honestly inaccurate acquisition point before the first anchor is part
            // of the already allowed starting edge, not an interior recording gap. Its
            // source clock must still be valid and agree with the eventual anchor. No
            // stale, invalid or mock/unknown rejection gets this exception.
            if (intervalIndex == 0 && sourceClockOffset == null &&
                !point.acceptedForDistance &&
                point.rejectionReason == GpsSampleStatus.REJECTED_INACCURATE.name
            ) {
                val rawAccuracy = point.horizontalAccuracyMeters?.toDouble()
                if (rawAccuracy != null && rawAccuracy.isFinite() && rawAccuracy > 0.0 &&
                    rawAccuracy <= distanceConfig.maxHorizontalAccuracyMeters
                ) return false
                if (mono - interval.start !in 0L..MAX_EDGE_MILLIS ||
                    initialInaccurateLastMono?.let { mono <= it } == true ||
                    point.observedAtEpochMillis <= 0L
                ) return false
                val offset = clockOffsetMillis(point.observedAtEpochMillis, mono)
                if (offset !in -MAX_SOURCE_CLOCK_OFFSET_MILLIS..MAX_SOURCE_CLOCK_OFFSET_MILLIS) return false
                initialInaccurateOffsets.add(offset)
                initialInaccurateLastMono = mono
                return@forEach
            }
            if (sourceClockOffset == null && initialInaccurateLastMono?.let { mono <= it } == true) return false
            val recoverableRejection = !point.acceptedForDistance && point.rejectionReason in recoverableReasons
            if (recoverableRejection) {
                if (!sourceClocksAgree(point.observedAtEpochMillis, mono)) return false
                val rawAccuracy = point.horizontalAccuracyMeters?.toDouble()
                val accurate = rawAccuracy != null && rawAccuracy.isFinite() &&
                    rawAccuracy > 0.0 && rawAccuracy <= distanceConfig.maxHorizontalAccuracyMeters
                // Validate the rejection's meaning; malformed evidence is not a recovery case.
                if (point.rejectionReason == GpsSampleStatus.REJECTED_INACCURATE.name && accurate) return false
                if (point.rejectionReason == GpsSampleStatus.REJECTED_IMPLAUSIBLE_JUMP.name &&
                    (!accurate || interval.firstFix == null)) return false
                if (point.segmentDistanceMeters != null || point.cumulativeDistanceMeters != null) return false
                interval.recoveryPending = true
                distanceAnchorMono = null
                return@forEach
            }
            val accuracy = point.horizontalAccuracyMeters?.toDouble() ?: return false
            if (!accuracy.isFinite() || accuracy <= 0.0 || accuracy > distanceConfig.maxHorizontalAccuracyMeters) {
                return false
            }
            val stationary = !point.acceptedForDistance &&
                point.rejectionReason == GpsSampleStatus.IGNORED_WITHIN_ACCURACY.name
            if (!point.acceptedForDistance && !stationary) return false
            if (interval.firstFix == null) {
                if (!point.acceptedForDistance || point.segmentDistanceMeters != null) return false
                interval.firstFix = mono
            } else {
                if (mono - checkNotNull(interval.lastFix) > distanceConfig.maxPointIntervalMillis) return false
                if (interval.recoveryPending) {
                    if (!point.acceptedForDistance || point.segmentDistanceMeters != null) return false
                    recoveryMillis = Math.addExact(recoveryMillis, mono - checkNotNull(interval.lastFix))
                } else if (point.acceptedForDistance && point.segmentDistanceMeters == null) {
                    // Unexplained restarts/service gaps are not brief rejected-fix recovery.
                    return false
                }
            }
            if (point.acceptedForDistance) {
                val segment = point.segmentDistanceMeters
                if (segment == null) {
                    val storedTotal = point.cumulativeDistanceMeters ?: 0.0
                    if (!sameDistance(storedTotal, cumulative)) return false
                } else {
                    val anchor = distanceAnchorMono ?: return false
                    if (segment <= 0.0 || mono <= anchor ||
                        segment / ((mono - anchor) / 1_000.0) > distanceConfig.maxPlausibleSpeedMetersPerSecond
                    ) return false
                    cumulative += segment
                    if (!cumulative.isFinite() ||
                        !sameDistance(point.cumulativeDistanceMeters ?: return false, cumulative)
                    ) return false
                    measuredSegmentSeen = true
                }
                distanceAnchorMono = mono
            }
            if (sourceClockOffset == null) {
                // Only the first valid in-Running accepted anchor establishes the reference.
                // Keep it across every later point and pause/resume interval; never rebase.
                if (!point.acceptedForDistance || point.segmentDistanceMeters != null) return false
                sourceClockOffset = clockOffsetMillis(point.observedAtEpochMillis, mono)
                if (initialInaccurateOffsets.any {
                        Math.subtractExact(it, checkNotNull(sourceClockOffset)) !in
                            -SOURCE_CLOCK_TOLERANCE_MILLIS..SOURCE_CLOCK_TOLERANCE_MILLIS
                    }
                ) return false
            }
            if (!sourceClocksAgree(point.observedAtEpochMillis, mono)) return false
            interval.lastFix = mono
            interval.recoveryPending = false
        }
        if (!measuredSegmentSeen || cumulative <= 0.0) return false
        var uncoveredEdges = 0L
        intervals.forEach { interval ->
            if (interval.recoveryPending) return false
            val first = interval.firstFix ?: return false
            val last = interval.lastFix ?: return false
            val initialEdge = first - interval.start
            val finalEdge = interval.end - last
            if (initialEdge !in 0L..MAX_EDGE_MILLIS || finalEdge !in 0L..MAX_EDGE_MILLIS) return false
            uncoveredEdges = Math.addExact(uncoveredEdges, Math.addExact(initialEdge, finalEdge))
        }
        return Math.addExact(uncoveredEdges, recoveryMillis).toDouble() <= monoActive.toDouble() * 0.05
    }

    private fun sameDistance(first: Double, second: Double): Boolean =
        first.isFinite() && second.isFinite() && abs(first - second) <= max(0.000001, abs(second) * 0.000000001)

    private data class RunningInterval(
        val start: Long,
        val end: Long,
        var firstFix: Long? = null,
        var lastFix: Long? = null,
        var recoveryPending: Boolean = false
    )
}
