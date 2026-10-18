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
import com.runstate.mobile.location.GpsSampleStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.min

class RunSplitCalculatorTest {
    @Test
    fun `kilometer boundaries are interpolated inside accepted segments and retain final partial`() {
        val fixture = fixture(distance = 2_500.0, duration = 1_001_000L)
        val splits = checkNotNull(fixture.calculate())
        assertEquals(3, splits.size)
        assertEquals(listOf(false, false, true), splits.map { it.isPartial })
        assertEquals(400_400L, splits[0].durationMillis)
        assertEquals(400_400L, splits[1].durationMillis)
        assertEquals(200_200L, splits[2].durationMillis)
        assertEquals(1_000.0, splits[0].endDistanceMeters, 0.0)
        assertEquals(2_000.0, splits[1].endDistanceMeters, 0.0)
        assertEquals(2_500.0, splits[2].endDistanceMeters, 0.0)
        assertEquals(EPOCH + 400_400L, splits[0].endEpochMillis)
        assertEquals(0.4004, splits[0].paceSecondsPerMeter, 0.0000001)
        assertContiguous(splits, fixture, 1_001_000L)
    }

    @Test
    fun `mile choice uses the stored international mile boundary`() {
        val fixture = fixture(distance = 2_000.0, duration = 800_000L, unit = DistanceUnit.MILES)
        val splits = checkNotNull(fixture.calculate())
        assertEquals(2, splits.size)
        assertEquals(1_609.344, splits[0].endDistanceMeters, 0.0)
        assertEquals(643_738L, splits[0].endActiveMillis)
        assertFalse(splits[0].isPartial)
        assertTrue(splits[1].isPartial)
        assertContiguous(splits, fixture, 800_000L)
    }

    @Test
    fun `shorter than one unit is a single partial beginning at official start`() {
        val fixture = fixture(distance = 350.0, duration = 150_000L)
        val splits = checkNotNull(fixture.calculate())
        assertEquals(1, splits.size)
        assertTrue(splits.single().isPartial)
        assertContiguous(splits, fixture, 150_000L)
    }

    @Test
    fun `exact unit distance has no zero distance partial and includes final active tail`() {
        val fixture = fixture(distance = 2_000.0, duration = 800_000L, lastFix = 799_000L)
        val splits = checkNotNull(fixture.calculate())
        assertEquals(2, splits.size)
        assertTrue(splits.none { it.isPartial })
        assertEquals(EPOCH + 800_000L, splits.last().endEpochMillis)
        assertContiguous(splits, fixture, 800_000L)
    }

    @Test
    fun `a tolerance sized floating point remainder does not create an empty partial`() {
        val fixture = fixture(distance = 1_000.00000001, duration = 400_000L)
        val splits = checkNotNull(fixture.calculate())
        assertEquals(1, splits.size)
        assertFalse(splits.single().isPartial)
        assertEquals(fixture.run.finalDistanceMeters!!, splits.single().endDistanceMeters, 0.0)
    }

    @Test
    fun `a real submillisecond final remainder is unavailable rather than widening a full unit`() {
        // Beyond the fixed one-micrometer boundary tolerance: total-distance comparison's
        // relative allowance must not turn this remainder into an oversized full split.
        assertNull(fixture(distance = 2_000.0000015, duration = 800_000L).calculate())

        val normalized = checkNotNull(fixture(distance = 2_000.0000005, duration = 800_000L).calculate())
        assertEquals(2, normalized.size)
        assertTrue(normalized.none { it.isPartial })
        assertTrue(normalized.all {
            kotlin.math.abs((it.endDistanceMeters - it.startDistanceMeters) - 1_000.0) <= 0.000001
        })

        val measurable = checkNotNull(fixture(distance = 2_000.1, duration = 800_000L).calculate())
        assertEquals(3, measurable.size)
        assertTrue(measurable.last().isPartial)
        assertTrue(measurable.last().durationMillis > 0L)
    }

    @Test
    fun `pause is excluded from split active duration but remains in boundary epoch`() {
        val fixture = pausedFixture(firstDistance = 800.0, secondDistance = 700.0)
        val splits = checkNotNull(fixture.calculate())
        assertEquals(2, splits.size)
        assertEquals(571_429L, splits[0].endActiveMillis)
        assertEquals(EPOCH + 631_429L, splits[0].endEpochMillis)
        assertEquals(750_000L, splits[1].endActiveMillis)
        assertContiguous(splits, fixture, 750_000L)
        assertEquals(EPOCH + 810_000L, splits.last().endEpochMillis)
        assertEquals(EPOCH + 820_000L, fixture.run.finishEpochMillis)
    }

    @Test
    fun `a boundary exactly at pause belongs to the interval that earned it`() {
        val fixture = pausedFixture(firstDistance = 1_000.0, secondDistance = 500.0)
        val splits = checkNotNull(fixture.calculate())
        assertEquals(EPOCH + 500_000L, splits.first().endEpochMillis)
        assertEquals(500_000L, splits.first().durationMillis)
        assertEquals(EPOCH + 500_000L, splits.last().startEpochMillis)
        assertEquals(250_000L, splits.last().durationMillis)
    }

    @Test
    fun `accurate stationary fixes add active time without advancing split progress`() {
        val fixture = fixture(distance = 1_500.0, duration = 800_000L)
        val stationaryPoints = fixture.points.map { point ->
            val offset = offset(point)
            val distance = when {
                offset <= 300_000L -> offset / 1_000.0 * 2.5
                offset <= 500_000L -> 750.0
                else -> 750.0 + (offset - 500_000L) / 1_000.0 * 2.5
            }
            point.copy(cumulativeDistanceMeters = distance)
        }.let { points ->
            points.mapIndexed { index, point ->
                val added = if (index == 0) null else
                    point.cumulativeDistanceMeters!! - points[index - 1].cumulativeDistanceMeters!!
                if (added == 0.0) point.copy(
                    acceptedForDistance = false,
                    rejectionReason = GpsSampleStatus.IGNORED_WITHIN_ACCURACY.name,
                    segmentDistanceMeters = null,
                    cumulativeDistanceMeters = null
                ) else point.copy(segmentDistanceMeters = added)
            }
        }
        val splits = checkNotNull(fixture.copy(points = stationaryPoints).calculate())
        assertEquals(600_000L, splits.first().endActiveMillis)
        assertEquals(0.6, splits.first().paceSecondsPerMeter, 0.0000001)
        assertEquals(200_000L, splits.last().durationMillis)
    }

    @Test
    fun `stationary tail after exact boundary remains in last full split`() {
        val fixture = fixture(distance = 1_000.0, duration = 600_000L)
        val modified = fixture.points.map { point ->
            val offset = offset(point)
            when {
                offset <= 400_000L -> point.copy(
                    segmentDistanceMeters = if (offset == 0L) null else 25.0,
                    cumulativeDistanceMeters = offset / 1_000.0 * 2.5
                )
                else -> point.copy(
                    acceptedForDistance = false,
                    rejectionReason = GpsSampleStatus.IGNORED_WITHIN_ACCURACY.name,
                    segmentDistanceMeters = null,
                    cumulativeDistanceMeters = null
                )
            }
        }
        val splits = checkNotNull(fixture.copy(points = modified).calculate())
        assertEquals(1, splits.size)
        assertFalse(splits.single().isPartial)
        assertEquals(600_000L, splits.single().durationMillis)
        assertEquals(EPOCH + 600_000L, splits.single().endEpochMillis)
    }

    @Test
    fun `bounded source clock bias and drift do not shift split epochs`() {
        val baseline = fixture()
        val biased = baseline.copy(points = baseline.points.mapIndexed { index, point ->
            point.copy(observedAtEpochMillis = point.observedAtEpochMillis + 1_000L + index % 30)
        })
        assertEquals(baseline.calculate(), biased.calculate())
        assertNotNull(biased.calculate())
    }

    @Test
    fun `lifecycle tolerance maps boundary onto official timeline and preserves active sum`() {
        val fixture = fixture(distance = 1_500.0, duration = 600_000L)
        val shiftedPause = fixture.transitions.single().copy(occurredAtEpochMillis = EPOCH + 600_200L)
        val modified = fixture.copy(transitions = listOf(shiftedPause))
        val splits = checkNotNull(modified.calculate())
        assertEquals(400_133L, splits.first().endActiveMillis)
        assertEquals(EPOCH + 400_133L, splits.first().endEpochMillis)
        assertContiguous(splits, modified, 600_200L)
    }

    @Test
    fun `fixture partial unavailable and legacy records cannot fabricate splits`() {
        val fixture = fixture()
        assertNull(fixture.copy(run = fixture.run.copy(trackingMode = RunTrackingMode.FIXTURE)).calculate())
        assertNull(fixture.copy(run = fixture.run.copy(metricSource = MetricSource.FIXTURE)).calculate())
        assertNull(fixture.copy(run = fixture.run.copy(telemetryCoverage = TelemetryCoverage.PARTIAL)).calculate())
        assertNull(fixture.copy(run = fixture.run.copy(
            finalDistanceMeters = null, telemetryCoverage = TelemetryCoverage.UNAVAILABLE
        )).calculate())
        assertNull(fixture.copy(run = fixture.run.copy(trackingMode = null)).calculate())
        assertNull(fixture.copy(run = fixture.run.copy(transitionHistoryComplete = null)).calculate())
        assertNull(fixture.copy(run = fixture.run.copy(
            startElapsedRealtimeMillis = null, timingSessionId = null
        )).calculate())
        assertNull(fixture.copy(run = fixture.run.copy(
            finalDistanceMeters = null, metricSource = null, telemetryCoverage = null,
            displayDistanceUnit = null, finishEpochMillis = null
        )).calculate())
    }

    @Test
    fun `unfinished state is rejected even with accepted GPS points`() {
        val fixture = fixture()
        val paused = fixture.run.copy(
            state = StoredRunState.PAUSED, finishEpochMillis = null,
            finalDistanceMeters = null, metricSource = null, telemetryCoverage = null,
            displayDistanceUnit = null
        )
        assertNull(fixture.copy(run = paused).calculate())
        assertNull(fixture.copy(run = paused.copy(state = StoredRunState.RUNNING)).calculate())
    }

    @Test
    fun `a frozen complete label does not bypass final cumulative distance verification`() {
        val fixture = fixture()
        assertNull(fixture.copy(run = fixture.run.copy(finalDistanceMeters = 1_501.0)).calculate())
        assertNull(fixture.copy(run = fixture.run.copy(finalDistanceMeters = 0.0)).calculate())
        assertNull(fixture.copy(points = emptyList()).calculate())
        assertNull(fixture.copy(points = fixture.points.take(1)).calculate())
        assertNull(fixture.copy(points = fixture.points.mapIndexed { index, point ->
            if (index == 10) point.copy(cumulativeDistanceMeters = 251.0) else point
        }).calculate())
    }

    @Test
    fun `child ownership and ordered complete event evidence are mandatory`() {
        val fixture = fixture()
        assertNull(fixture.copy(transitions = fixture.transitions.map { it.copy(runId = OTHER_RUN) }).calculate())
        assertNull(fixture.copy(points = fixture.points.mapIndexed { index, point ->
            if (index == 3) point.copy(runId = OTHER_RUN) else point
        }).calculate())
        assertNull(fixture.copy(points = fixture.points.drop(1)).calculate())
        assertNull(fixture.copy(points = fixture.points.reversed()).calculate())
        assertNull(fixture.copy(transitions = fixture.transitions.map { it.copy(sequenceNumber = 2) }).calculate())
        assertNull(fixture.copy(transitions = emptyList()).calculate())
        assertNull(fixture.copy(transitions = fixture.transitions.map {
            it.copy(transitionType = RunTransitionType.RESUME)
        }).calculate())
    }

    @Test
    fun `missing monotonic evidence and changed process cannot certify a split`() {
        val fixture = fixture()
        assertNull(fixture.copy(transitions = fixture.transitions.map {
            it.copy(elapsedRealtimeMillis = null, timingSessionId = null)
        }).calculate())
        assertNull(fixture.copy(transitions = fixture.transitions.map { it.copy(timingSessionId = OTHER_SESSION) }).calculate())
        assertNull(fixture.copy(points = fixture.points.mapIndexed { index, point ->
            if (index == 20) point.copy(elapsedRealtimeNanos = null) else point
        }).calculate())
    }

    @Test
    fun `clock disagreement and drifting source offsets remain unsupported`() {
        val fixture = fixture()
        assertNull(fixture.copy(transitions = fixture.transitions.map {
            it.copy(occurredAtEpochMillis = it.occurredAtEpochMillis + 251L)
        }).calculate())
        assertNull(fixture.copy(points = fixture.points.map {
            it.copy(observedAtEpochMillis = it.observedAtEpochMillis + 2_001L)
        }).calculate())
        assertNull(fixture.copy(points = fixture.points.mapIndexed { index, point ->
            if (index > 20) point.copy(observedAtEpochMillis = point.observedAtEpochMillis + 1_001L) else point
        }).calculate())
    }

    @Test
    fun `GPS clock reference cannot reset across pause`() {
        val fixture = pausedFixture()
        assertNull(fixture.copy(points = fixture.points.map { point ->
            point.copy(observedAtEpochMillis = point.observedAtEpochMillis +
                if (offset(point) <= 500_000L) -600L else 600L)
        }).calculate())
    }

    @Test
    fun `recovered anchors and interior gaps are not interpolated away`() {
        val fixture = fixture()
        assertNull(fixture.copy(points = fixture.points.filterNot {
            offset(it) in 200_000L..240_000L
        }.mapIndexed { index, point -> point.copy(sequenceNumber = index + 1) }).calculate())
        assertNull(fixture.copy(points = fixture.points.mapIndexed { index, point ->
            if (index == 20) point.copy(segmentDistanceMeters = null) else point
        }).calculate())
    }

    @Test
    fun `acquisition final edge and total edge omissions stay within coverage policy`() {
        assertNull(fixture(distance = 1_500.0, duration = 600_000L, firstFix = 5_001L).calculate())
        assertNull(fixture(distance = 1_500.0, duration = 600_000L, lastFix = 594_999L).calculate())
        assertNull(fixture(distance = 100.0, duration = 40_000L, firstFix = 1_001L, lastFix = 39_000L).calculate())
        assertNotNull(fixture().calculate())
    }

    @Test
    fun `inaccurate stale and implausible observations invalidate otherwise complete label`() {
        val fixture = fixture()
        listOf(
            GpsSampleStatus.REJECTED_INACCURATE.name,
            GpsSampleStatus.REJECTED_STALE.name,
            GpsSampleStatus.REJECTED_IMPLAUSIBLE_JUMP.name,
            "CROSSES_CHECKPOINT"
        ).forEach { reason ->
            assertNull(reason, fixture.copy(points = fixture.points.mapIndexed { index, point ->
                if (index == 20) point.copy(
                    acceptedForDistance = false, rejectionReason = reason,
                    segmentDistanceMeters = null, cumulativeDistanceMeters = null
                ) else point
            }).calculate())
        }
        assertNull(fixture.copy(points = fixture.points.mapIndexed { index, point ->
            if (index == 20) point.copy(horizontalAccuracyMeters = 26f) else point
        }).calculate())
        assertNull(fixture.copy(points = fixture.points.mapIndexed { index, point ->
            if (index == 20) point.copy(segmentDistanceMeters = 100.0) else point
        }).calculate())
    }

    @Test
    fun `harmless delayed fixes and paused callbacks never shift boundaries`() {
        val fixture = pausedFixture()
        val delayed = fixture.points[10].copy(
            acceptedForDistance = false, rejectionReason = GpsSampleStatus.REJECTED_OUT_OF_ORDER.name,
            segmentDistanceMeters = null, cumulativeDistanceMeters = null
        )
        val paused = delayed.copy(
            observedAtEpochMillis = EPOCH + 520_000L,
            elapsedRealtimeNanos = (MONO + 520_000L) * 1_000_000L,
            rejectionReason = "NOT_RUNNING"
        )
        val modified = (fixture.points.take(30) + delayed + fixture.points.drop(30).take(21) +
            paused + fixture.points.drop(51)).mapIndexed { index, point -> point.copy(sequenceNumber = index + 1) }
        assertEquals(fixture.calculate(), fixture.copy(points = modified).calculate())
        assertNotNull(fixture.copy(points = modified).calculate())
    }

    @Test
    fun `completion checkpoint and final paused timeline must agree`() {
        val fixture = fixture()
        assertNull(fixture.copy(run = fixture.run.copy(lastCheckpointEpochMillis = EPOCH)).calculate())
        assertNull(fixture.copy(transitions = fixture.transitions.map {
            it.copy(occurredAtEpochMillis = checkNotNull(fixture.run.finishEpochMillis) + 1L)
        }).calculate())
    }

    @Test
    fun `an unrepresentable split pace is unavailable rather than infinite`() {
        assertNull(fixture(distance = 1e-320, duration = 600_000L).calculate())
    }

    private fun assertContiguous(splits: List<RunSplit>, fixture: Fixture, activeTotal: Long) {
        assertEquals(1, splits.first().index)
        assertEquals(0.0, splits.first().startDistanceMeters, 0.0)
        assertEquals(0L, splits.first().startActiveMillis)
        assertEquals(EPOCH, splits.first().startEpochMillis)
        splits.zipWithNext().forEach { (first, second) ->
            assertEquals(first.index + 1, second.index)
            assertEquals(first.endDistanceMeters, second.startDistanceMeters, 0.0)
            assertEquals(first.endActiveMillis, second.startActiveMillis)
            assertEquals(first.endEpochMillis, second.startEpochMillis)
        }
        assertEquals(fixture.run.finalDistanceMeters!!, splits.last().endDistanceMeters, 0.0)
        assertEquals(activeTotal, splits.sumOf { it.durationMillis })
        assertEquals(fixture.transitions.last().occurredAtEpochMillis, splits.last().endEpochMillis)
        assertTrue(splits.all { it.paceSecondsPerMeter.isFinite() && it.paceSecondsPerMeter > 0.0 })
    }

    private fun fixture(
        distance: Double = 1_500.0,
        duration: Long = 600_000L,
        unit: DistanceUnit = DistanceUnit.KILOMETERS,
        firstFix: Long = 0L,
        lastFix: Long = duration
    ): Fixture = Fixture(
        completed(distance, duration + 10_000L, unit),
        listOf(transition(1, RunTransitionType.PAUSE, duration)),
        route(firstFix, lastFix, 0.0, distance)
    )

    private fun pausedFixture(firstDistance: Double = 1_000.0, secondDistance: Double = 500.0): Fixture {
        // The 60-second pause is real elapsed time, but never split progress or active time.
        val firstDuration = 500_000L
        val points = (route(0L, firstDuration, 0.0, firstDistance) +
            route(560_000L, 810_000L, firstDistance, secondDistance))
            .mapIndexed { index, point -> point.copy(sequenceNumber = index + 1) }
        return Fixture(
            completed(firstDistance + secondDistance, 820_000L, DistanceUnit.KILOMETERS),
            listOf(
                transition(1, RunTransitionType.PAUSE, firstDuration),
                transition(2, RunTransitionType.RESUME, 560_000L),
                transition(3, RunTransitionType.PAUSE, 810_000L)
            ),
            points
        )
    }

    private fun completed(distance: Double, finish: Long, unit: DistanceUnit) = RunEntity(
        runId = RUN,
        state = StoredRunState.COMPLETED,
        officialStartEpochMillis = EPOCH,
        startTimezoneId = "America/Chicago",
        lastCheckpointEpochMillis = EPOCH + finish,
        finishEpochMillis = EPOCH + finish,
        finalDistanceMeters = distance,
        metricSource = MetricSource.GPS,
        telemetryCoverage = TelemetryCoverage.COMPLETE,
        displayDistanceUnit = unit,
        transitionHistoryComplete = true,
        trackingMode = RunTrackingMode.GPS,
        startElapsedRealtimeMillis = MONO,
        timingSessionId = SESSION
    )

    private fun transition(sequence: Int, type: RunTransitionType, offset: Long) = RunTransitionEntity(
        RUN, sequence, type, EPOCH + offset, MONO + offset, SESSION
    )

    private fun route(start: Long, end: Long, initialDistance: Double, distance: Double): List<RunLocationPointEntity> {
        val offsets = generateSequence(start) { previous ->
            if (previous == end) null else min(end, previous + 10_000L)
        }.toList()
        return offsets.mapIndexed { index, offset ->
            val cumulative = initialDistance + distance * (offset - start).toDouble() / (end - start).toDouble()
            val segment = if (index == 0) null else
                distance * (offset - offsets[index - 1]).toDouble() / (end - start).toDouble()
            RunLocationPointEntity(
                runId = RUN,
                sequenceNumber = index + 1,
                observationKey = "point-$offset",
                observedAtEpochMillis = EPOCH + offset,
                elapsedRealtimeNanos = (MONO + offset) * 1_000_000L,
                latitude = 29.7,
                longitude = -95.4,
                horizontalAccuracyMeters = 3f,
                altitudeMeters = null,
                speedMetersPerSecond = null,
                bearingDegrees = null,
                acceptedForDistance = true,
                rejectionReason = null,
                segmentDistanceMeters = segment,
                cumulativeDistanceMeters = cumulative
            )
        }
    }

    private fun offset(point: RunLocationPointEntity) = point.observedAtEpochMillis - EPOCH

    private data class Fixture(
        val run: RunEntity,
        val transitions: List<RunTransitionEntity>,
        val points: List<RunLocationPointEntity>
    ) {
        fun calculate() = RunSplitCalculator.calculate(run, transitions, points)
    }

    private companion object {
        const val EPOCH = 1_800_000_000_000L
        const val MONO = 5_000_000L
        const val RUN = "00000000-0000-4000-8000-000000000001"
        const val OTHER_RUN = "00000000-0000-4000-8000-000000000002"
        const val SESSION = "00000000-0000-4000-8000-000000000003"
        const val OTHER_SESSION = "00000000-0000-4000-8000-000000000004"
    }
}
