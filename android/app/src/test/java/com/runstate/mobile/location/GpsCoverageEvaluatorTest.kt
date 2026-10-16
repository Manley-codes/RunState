package com.runstate.mobile.location

import com.runstate.mobile.data.local.DistanceUnit
import com.runstate.mobile.data.local.MetricSource
import com.runstate.mobile.data.local.RunEntity
import com.runstate.mobile.data.local.RunLocationPointEntity
import com.runstate.mobile.data.local.RunTrackingMode
import com.runstate.mobile.data.local.RunTransitionEntity
import com.runstate.mobile.data.local.RunTransitionType
import com.runstate.mobile.data.local.StoredRunState
import com.runstate.mobile.data.local.TelemetryCoverage
import com.runstate.mobile.run.RunTimingMark
import org.junit.Assert.assertEquals
import org.junit.Test

class GpsCoverageEvaluatorTest {
    @Test
    fun `continuous live and paused recordings certify the same coverage`() {
        val points = route(1_000L..59_000L)
        assertEquals(TelemetryCoverage.COMPLETE, assess(points = points))
        assertEquals(TelemetryCoverage.COMPLETE, assess(
            run = running(), transitions = emptyList(), points = points, nowOffset = 60_000L
        ))
    }

    @Test
    fun `stationary accurate observations preserve continuity without adding distance`() {
        val points = route(1_000L..59_000L, stationaryOffsets = (20_000L..40_000L step 1_000L).toSet())
        assertEquals(TelemetryCoverage.COMPLETE, assess(points = points))
        assertEquals(74.0, points.last().cumulativeDistanceMeters!!, 0.0)
        assertEquals(21, points.count { it.rejectionReason == GpsSampleStatus.IGNORED_WITHIN_ACCURACY.name })
    }

    @Test
    fun `no measured distance remains unavailable even with a good anchor`() {
        assertEquals(TelemetryCoverage.UNAVAILABLE, assess(points = emptyList()))
        assertEquals(TelemetryCoverage.UNAVAILABLE, assess(points = route(1_000L..1_000L)))
        val stationary = route(1_000L..59_000L, stationaryOffsets = (2_000L..59_000L step 1_000L).toSet())
        assertEquals(TelemetryCoverage.UNAVAILABLE, assess(points = stationary))
    }

    @Test
    fun `zero or inconsistent cumulative measurements cannot be certified`() {
        val points = route(1_000L..59_000L)
        val zero = points.take(2).mapIndexed { index, point ->
            if (index == 0) point else point.copy(segmentDistanceMeters = 0.0, cumulativeDistanceMeters = 0.0)
        }
        assertEquals(TelemetryCoverage.PARTIAL, assess(points = zero))
        assertEquals(TelemetryCoverage.PARTIAL, assess(points = points.mapIndexed { index, point ->
            if (index == 20) point.copy(cumulativeDistanceMeters = point.cumulativeDistanceMeters!! + 10.0) else point
        }))
    }

    @Test
    fun `legacy timing incomplete lifecycle and missing current timing cannot certify coverage`() {
        assertEquals(TelemetryCoverage.PARTIAL, assess(run = paused().copy(
            startElapsedRealtimeMillis = null, timingSessionId = null)))
        assertEquals(TelemetryCoverage.PARTIAL, assess(run = paused().copy(transitionHistoryComplete = null)))
        assertEquals(TelemetryCoverage.PARTIAL, assess(transitions = listOf(pause().copy(
            elapsedRealtimeMillis = null, timingSessionId = null))))
        assertEquals(TelemetryCoverage.PARTIAL, GpsCoverageEvaluator.assess(
            paused(), listOf(pause()), route(1_000L..59_000L), EPOCH + 70_000L, null))
    }

    @Test
    fun `process change and explicit recovered running gap stay partial even when paused`() {
        assertEquals(TelemetryCoverage.PARTIAL, assess(currentSession = OTHER_SESSION))
        assertEquals(TelemetryCoverage.PARTIAL, assess(transitions = listOf(pause().copy(timingSessionId = OTHER_SESSION))))
        assertEquals(TelemetryCoverage.PARTIAL, GpsCoverageEvaluator.assess(
            paused(), listOf(pause()), route(1_000L..59_000L), EPOCH + 70_000L,
            mark(70_000L), recoveredRunningGap = true))
    }

    @Test
    fun `completed verdicts are frozen and never promoted from later evaluation`() {
        val completed = paused().copy(
            state = StoredRunState.COMPLETED,
            finishEpochMillis = EPOCH + 70_000L,
            finalDistanceMeters = 116.0,
            metricSource = MetricSource.GPS,
            telemetryCoverage = TelemetryCoverage.PARTIAL,
            displayDistanceUnit = DistanceUnit.MILES
        )
        assertEquals(TelemetryCoverage.PARTIAL, assess(run = completed))
        assertEquals(TelemetryCoverage.COMPLETE, assess(
            run = completed.copy(telemetryCoverage = TelemetryCoverage.COMPLETE),
            transitions = emptyList(), points = emptyList(), currentSession = OTHER_SESSION
        ))
    }

    @Test
    fun `acquisition and final tail must each fit the five second boundary`() {
        assertEquals(TelemetryCoverage.PARTIAL, assess(points = route(6_000L..59_000L)))
        assertEquals(TelemetryCoverage.PARTIAL, assess(points = route(1_000L..54_000L)))
        val longPause = paused(200_000L)
        assertEquals(TelemetryCoverage.COMPLETE, assess(
            run = longPause, transitions = listOf(pause(200_000L)),
            points = route(5_000L..195_000L), nowOffset = 210_000L
        ))
        assertEquals(TelemetryCoverage.PARTIAL, assess(
            run = longPause, transitions = listOf(pause(200_000L)),
            points = route(6_000L..196_000L), nowOffset = 210_000L
        ))
    }

    @Test
    fun `small individual edges still cannot exceed five percent in total`() {
        assertEquals(TelemetryCoverage.COMPLETE, assess(points = route(1_000L..58_000L)))
        assertEquals(TelemetryCoverage.PARTIAL, assess(points = route(2_000L..58_000L)))
    }

    @Test
    fun `pause time is excluded and each resumed interval begins at its own anchor`() {
        val transitions = listOf(pause(), resume(90_000L, 2), pause(150_000L, 3))
        val points = route(1_000L..59_000L, 91_000L..149_000L)
        assertEquals(TelemetryCoverage.COMPLETE, assess(
            run = paused(150_000L), transitions = transitions, points = points,
            nowOffset = 3_600_000L
        ))
        // A late rejected callback from the intentionally paused gap contributes nothing.
        assertEquals(TelemetryCoverage.COMPLETE, assess(
            run = paused(150_000L), transitions = transitions,
            points = insertRejected(points, 59, 75_000L, "NOT_RUNNING"), nowOffset = 160_000L
        ))
    }

    @Test
    fun `one uncovered resumed interval prevents full run certification`() {
        assertEquals(TelemetryCoverage.PARTIAL, assess(
            run = paused(150_000L),
            transitions = listOf(pause(), resume(90_000L, 2), pause(150_000L, 3)),
            points = route(1_000L..59_000L), nowOffset = 160_000L
        ))
    }

    @Test
    fun `interior source gap and unexpected anchor never become complete`() {
        val points = route(1_000L..59_000L)
        val gap = points.filterNot { offset(it) in 20_000L..40_000L }
            .mapIndexed { index, point -> point.copy(
                sequenceNumber = index + 1,
                cumulativeDistanceMeters = if (index == 0) null else index * 2.0
            ) }
        assertEquals(TelemetryCoverage.PARTIAL, assess(points = gap))
        val newAnchor = points.mapIndexed { index, point ->
            if (index == 20) point.copy(segmentDistanceMeters = null) else point
        }
        assertEquals(TelemetryCoverage.PARTIAL, assess(points = newAnchor))
    }

    @Test
    fun `the interior gap limit uses source time and includes exactly fifteen seconds`() {
        fun withGapThrough(end: Long) = route(1_000L..59_000L)
            .filterNot { offset(it) in 20_000L..end }
            .mapIndexed { index, point -> point.copy(
                sequenceNumber = index + 1,
                cumulativeDistanceMeters = if (index == 0) null else index * 2.0
            ) }
        assertEquals(TelemetryCoverage.COMPLETE, assess(points = withGapThrough(33_000L)))
        assertEquals(TelemetryCoverage.PARTIAL, assess(points = withGapThrough(34_000L)))
    }

    @Test
    fun `bad accuracy stale implausible and durable checkpoint rejection break coverage`() {
        val reasons = listOf(
            GpsSampleStatus.REJECTED_INACCURATE.name, GpsSampleStatus.REJECTED_STALE.name,
            GpsSampleStatus.REJECTED_IMPLAUSIBLE_JUMP.name, GpsSampleStatus.REJECTED_INVALID.name,
            "CROSSES_CHECKPOINT", "BEFORE_CHECKPOINT", "NOT_RUNNING", "UNKNOWN_REASON"
        )
        reasons.forEach { reason ->
            assertEquals(reason, TelemetryCoverage.PARTIAL, assess(points = insertRejected(
                route(1_000L..59_000L), 20, 20_500L, reason)))
        }
    }

    @Test
    fun `true delayed and duplicate callbacks neither damage coverage nor extend its tail`() {
        listOf(GpsSampleStatus.REJECTED_OUT_OF_ORDER.name, "OUT_OF_ORDER").forEach { reason ->
            assertEquals(TelemetryCoverage.COMPLETE, assess(points = insertRejected(
                route(1_000L..59_000L), 30, 15_000L, reason)))
            assertEquals(TelemetryCoverage.COMPLETE, assess(points = insertRejected(
                route(1_000L..59_000L), 30, 30_000L, reason)))
            assertEquals(TelemetryCoverage.PARTIAL, assess(points = insertRejected(
                route(1_000L..53_000L), 53, 15_000L, reason)))
        }
    }

    @Test
    fun `an epoch out of order rejection with newer monotonic time is not a harmless duplicate`() {
        assertEquals(TelemetryCoverage.PARTIAL, assess(points = insertRejected(
            route(1_000L..59_000L), 30, 30_500L, "OUT_OF_ORDER")))
    }

    @Test
    fun `lifecycle clock disagreement beyond allowance prevents certification`() {
        val within = pause().copy(occurredAtEpochMillis = EPOCH + 60_250L)
        assertEquals(TelemetryCoverage.COMPLETE, assess(
            run = paused().copy(lastCheckpointEpochMillis = within.occurredAtEpochMillis),
            transitions = listOf(within)
        ))
        val beyond = pause().copy(occurredAtEpochMillis = EPOCH + 60_251L)
        assertEquals(TelemetryCoverage.PARTIAL, assess(
            run = paused().copy(lastCheckpointEpochMillis = beyond.occurredAtEpochMillis),
            transitions = listOf(beyond)
        ))
        assertEquals(TelemetryCoverage.PARTIAL, GpsCoverageEvaluator.assess(
            paused(), listOf(pause()), route(1_000L..59_000L), EPOCH + 70_251L, mark(70_000L)))
        assertEquals(TelemetryCoverage.PARTIAL, GpsCoverageEvaluator.assess(
            paused(), listOf(pause()), route(1_000L..59_000L), EPOCH + 69_749L, mark(70_000L)))
    }

    @Test
    fun `per boundary tolerances cannot accumulate into inaccurate active duration`() {
        val transitions = listOf(
            pause().copy(occurredAtEpochMillis = EPOCH + 60_250L),
            resume(90_000L, 2).copy(occurredAtEpochMillis = EPOCH + 89_750L),
            pause(150_000L, 3).copy(occurredAtEpochMillis = EPOCH + 150_250L)
        )
        assertEquals(TelemetryCoverage.PARTIAL, assess(
            run = paused(150_000L).copy(lastCheckpointEpochMillis = EPOCH + 150_250L),
            transitions = transitions, points = route(1_000L..59_000L, 91_000L..149_000L),
            nowOffset = 160_000L
        ))
    }

    @Test
    fun `source epoch clock tolerance is explicit and does not replace monotonic ordering`() {
        val points = route(1_000L..59_000L)
        assertEquals(TelemetryCoverage.COMPLETE, assess(points = points.mapIndexed { index, point ->
            if (index == 20) point.copy(observedAtEpochMillis = point.observedAtEpochMillis + 1_000L) else point
        }))
        assertEquals(TelemetryCoverage.PARTIAL, assess(points = points.mapIndexed { index, point ->
            if (index == 20) point.copy(observedAtEpochMillis = point.observedAtEpochMillis + 1_001L) else point
        }))
        assertEquals(TelemetryCoverage.PARTIAL, assess(points = points.mapIndexed { index, point ->
            if (index == 20) point.copy(elapsedRealtimeNanos = 0L) else point
        }))
    }

    @Test
    fun `missing point timing wrong ownership and broken sequences fail closed`() {
        val points = route(1_000L..59_000L)
        assertEquals(TelemetryCoverage.PARTIAL, assess(points = points.mapIndexed { index, point ->
            if (index == 20) point.copy(elapsedRealtimeNanos = null) else point
        }))
        assertEquals(TelemetryCoverage.PARTIAL, assess(points = points.mapIndexed { index, point ->
            if (index == 20) point.copy(runId = OTHER_RUN) else point
        }))
        assertEquals(TelemetryCoverage.PARTIAL, assess(points = points.filterIndexed { index, _ -> index != 20 }))
        assertEquals(TelemetryCoverage.PARTIAL, assess(transitions = listOf(pause().copy(runId = OTHER_RUN))))
        assertEquals(TelemetryCoverage.PARTIAL, assess(transitions = listOf(pause().copy(sequenceNumber = 2))))
        assertEquals(TelemetryCoverage.PARTIAL, assess(transitions = listOf(pause().copy(transitionType = RunTransitionType.RESUME))))
    }

    @Test
    fun `monotonic lifecycle reversal and checkpoint mismatch stay partial`() {
        assertEquals(TelemetryCoverage.PARTIAL, assess(transitions = listOf(pause().copy(elapsedRealtimeMillis = MONO - 1L))))
        assertEquals(TelemetryCoverage.PARTIAL, assess(run = paused().copy(lastCheckpointEpochMillis = EPOCH + 59_000L)))
        assertEquals(TelemetryCoverage.PARTIAL, assess(nowOffset = 59_000L))
    }

    private fun assess(
        run: RunEntity = paused(),
        transitions: List<RunTransitionEntity> = listOf(pause()),
        points: List<RunLocationPointEntity> = route(1_000L..59_000L),
        nowOffset: Long = 70_000L,
        currentSession: String = SESSION
    ) = GpsCoverageEvaluator.assess(run, transitions, points, EPOCH + nowOffset, mark(nowOffset, currentSession))

    private fun running() = RunEntity(
        runId = RUN,
        state = StoredRunState.RUNNING,
        officialStartEpochMillis = EPOCH,
        startTimezoneId = "America/Chicago",
        lastCheckpointEpochMillis = EPOCH,
        transitionHistoryComplete = true,
        trackingMode = RunTrackingMode.GPS,
        startElapsedRealtimeMillis = MONO,
        timingSessionId = SESSION
    )

    private fun paused(offset: Long = 60_000L) = running().copy(
        state = StoredRunState.PAUSED, lastCheckpointEpochMillis = EPOCH + offset)

    private fun pause(offset: Long = 60_000L, sequence: Int = 1) = RunTransitionEntity(
        RUN, sequence, RunTransitionType.PAUSE, EPOCH + offset, MONO + offset, SESSION)

    private fun resume(offset: Long, sequence: Int) = RunTransitionEntity(
        RUN, sequence, RunTransitionType.RESUME, EPOCH + offset, MONO + offset, SESSION)

    private fun mark(offset: Long, session: String = SESSION) = RunTimingMark(MONO + offset, session)

    private fun route(
        vararg ranges: LongRange,
        stationaryOffsets: Set<Long> = emptySet()
    ): List<RunLocationPointEntity> {
        var cumulative = 0.0
        val points = mutableListOf<RunLocationPointEntity>()
        ranges.forEach { range ->
            (range step 1_000L).forEachIndexed { index, offset ->
                val stationary = offset in stationaryOffsets
                val segment = if (index == 0 || stationary) null else 2.0
                if (segment != null) cumulative += segment
                points.add(RunLocationPointEntity(
                    runId = RUN,
                    sequenceNumber = points.size + 1,
                    observationKey = "fix-$offset",
                    observedAtEpochMillis = EPOCH + offset,
                    elapsedRealtimeNanos = (MONO + offset) * 1_000_000L,
                    latitude = 0.0,
                    longitude = cumulative / 111_195.0,
                    horizontalAccuracyMeters = 0.5f,
                    altitudeMeters = null,
                    speedMetersPerSecond = null,
                    bearingDegrees = null,
                    acceptedForDistance = !stationary,
                    rejectionReason = if (stationary) GpsSampleStatus.IGNORED_WITHIN_ACCURACY.name else null,
                    segmentDistanceMeters = segment,
                    cumulativeDistanceMeters = if (stationary || cumulative == 0.0) null else cumulative
                ))
            }
        }
        return points
    }

    private fun insertRejected(
        points: List<RunLocationPointEntity>, index: Int, offset: Long, reason: String
    ): List<RunLocationPointEntity> {
        val rejected = points.first().copy(
            observationKey = "rejected-$index-$offset-$reason",
            observedAtEpochMillis = EPOCH + offset,
            elapsedRealtimeNanos = (MONO + offset) * 1_000_000L,
            acceptedForDistance = false,
            rejectionReason = reason,
            segmentDistanceMeters = null,
            cumulativeDistanceMeters = null
        )
        return (points.take(index) + rejected + points.drop(index))
            .mapIndexed { position, point -> point.copy(sequenceNumber = position + 1) }
    }

    private fun offset(point: RunLocationPointEntity) = point.observedAtEpochMillis - EPOCH

    companion object {
        private const val RUN = "0f6a2c1e-9d43-4b7a-9c21-7b5e8a4d1f30"
        private const val OTHER_RUN = "ef6a2c1e-9d43-4b7a-9c21-7b5e8a4d1f30"
        private const val SESSION = "120e2c1e-9d43-4b7a-9c21-7b5e8a4d1f30"
        private const val OTHER_SESSION = "220e2c1e-9d43-4b7a-9c21-7b5e8a4d1f30"
        private const val EPOCH = 1_800_000_000_000L
        private const val MONO = 100_000L
    }
}
