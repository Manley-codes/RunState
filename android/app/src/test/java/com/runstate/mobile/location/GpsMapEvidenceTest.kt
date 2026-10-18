package com.runstate.mobile.location

import com.runstate.mobile.data.local.RunEntity
import com.runstate.mobile.data.local.RunLocationPointEntity
import com.runstate.mobile.data.local.RunTrackingMode
import com.runstate.mobile.data.local.RunTransitionEntity
import com.runstate.mobile.data.local.RunTransitionType
import com.runstate.mobile.data.local.StoredRunState
import com.runstate.mobile.run.RunTimingMark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GpsMapEvidenceTest {
    @Test
    fun `sample and unknown sources never display geographic evidence`() {
        for (mode in listOf(RunTrackingMode.FIXTURE, null)) {
            val result = evidence(run = running().copy(trackingMode = mode))
            assertFalse(result.isGpsRun)
            assertTrue(result.sections.isEmpty())
            assertNull(result.marker)
        }
    }

    @Test
    fun `gps without usable fixes waits without a fabricated default marker`() {
        val result = evidence(points = listOf(rejected(1, 1_000, "REJECTED_INACCURATE")))
        assertTrue(result.isGpsRun)
        assertFalse(result.evidenceUnavailable)
        assertTrue(result.sections.isEmpty())
        assertNull(result.marker)
    }

    @Test
    fun `accepted geographic route follows durable order and a fresh live fix`() {
        val result = evidence(points = listOf(point(3, 3_000, 3.0), point(1, 1_000), point(2, 2_000, 3.0)))
        assertEquals(listOf(3), result.sections.map { it.size })
        assertEquals(point(1, 1_000).latitude, result.sections.single().first().latitude, 0.0)
        assertEquals(point(3, 3_000, 3.0).longitude, result.marker!!.coordinate.longitude, 0.0)
        assertEquals(GpsMapMarkerState.CURRENT, result.marker.state)
        assertEquals(4.0, result.marker.accuracyMeters!!, 0.0)
    }

    @Test
    fun `the measured Moto source clock profile keeps fresh map evidence current`() {
        val points = (0..12).map { index ->
            point(index + 1, 3_000L + index * 1_000L, if (index == 0) null else 3.0)
                .withSourceWallOffset(1_022L + index)
        }
        val result = evidence(points = points.reversed(), nowOffset = 18_000)
        assertEquals(listOf(13), result.sections.map { it.size })
        assertEquals(GpsMapMarkerState.CURRENT, result.marker?.state)
        assertEquals(points.last().latitude, result.marker!!.coordinate.latitude, 0.0)
    }

    @Test
    fun `bounded source clock offsets work in both directions including two seconds`() {
        for (bias in listOf(-2_000L, -1_034L, 1_034L, 2_000L)) {
            val points = listOf(point(1, 3_000), point(2, 4_000, 3.0))
                .map { it.withSourceWallOffset(bias) }
            val result = evidence(points = points, nowOffset = 7_000)
            assertEquals("bias $bias", listOf(2), result.sections.map { it.size })
            assertEquals("bias $bias", GpsMapMarkerState.CURRENT, result.marker?.state)
        }
    }

    @Test
    fun `absolute source clock offset cannot exceed two seconds at the anchor or later fix`() {
        for (sign in listOf(-1L, 1L)) {
            for (badAnchor in listOf(false, true)) {
                val points = listOf(
                    point(1, 3_000).withSourceWallOffset(sign * if (badAnchor) 2_001L else 1_500L),
                    point(2, 4_000, 3.0).withSourceWallOffset(sign * 2_001L)
                )
                assertEquals("sign $sign, badAnchor $badAnchor", GpsMapMarkerState.LAST_KNOWN,
                    evidence(points = points, nowOffset = 8_000).marker?.state)
            }
        }
    }

    @Test
    fun `residual source drift is bounded against the first anchor in both directions`() {
        for (sign in listOf(-1L, 1L)) {
            for (drift in listOf(1_000L, 1_001L)) {
                val points = listOf(
                    point(1, 3_000).withSourceWallOffset(sign * -500L),
                    point(2, 4_000, 3.0).withSourceWallOffset(sign * (-500L + drift))
                )
                assertEquals("sign $sign, drift $drift",
                    if (drift == 1_000L) GpsMapMarkerState.CURRENT else GpsMapMarkerState.LAST_KNOWN,
                    evidence(points = points, nowOffset = 7_000).marker?.state)
            }
        }
    }

    @Test
    fun `a fresh accepted anchor cannot rebase an excessive source clock drift`() {
        val points = listOf(
            point(1, 1_000).withSourceWallOffset(-500),
            point(2, 2_000, 3.0).withSourceWallOffset(-500),
            point(3, 3_000).withSourceWallOffset(501),
            point(4, 4_000, 3.0).withSourceWallOffset(501)
        )
        val result = evidence(points = points, nowOffset = 7_000)
        assertEquals(listOf(2, 2), result.sections.map { it.size })
        assertEquals(GpsMapMarkerState.LAST_KNOWN, result.marker?.state)
    }

    @Test
    fun `pause resume in the same process cannot rebase an excessive source clock drift`() {
        val points = listOf(
            point(1, 1_000).withSourceWallOffset(-500),
            point(2, 2_000, 3.0).withSourceWallOffset(-500),
            point(3, 6_000).withSourceWallOffset(501),
            point(4, 7_000, 3.0).withSourceWallOffset(501)
        )
        val result = evidence(run = running().copy(lastCheckpointEpochMillis = EPOCH + 5_000),
            transitions = listOf(pause(3_000), resume(5_000)), points = points, nowOffset = 9_000)
        assertEquals(listOf(2, 2), result.sections.map { it.size })
        assertEquals(GpsMapMarkerState.LAST_KNOWN, result.marker?.state)
    }

    @Test
    fun `biased source fixes use monotonic running membership on both sides of a pause`() {
        for (bias in listOf(-1_500L, 1_500L)) {
            val points = listOf(point(1, 3_000), point(2, 8_100), point(3, 9_000, 3.0))
                .map { it.withSourceWallOffset(bias) }
            val result = evidence(run = running().copy(lastCheckpointEpochMillis = EPOCH + 8_000),
                transitions = listOf(pause(4_000), resume(8_000)), points = points, nowOffset = 12_000)
            assertEquals("bias $bias", listOf(1, 2), result.sections.map { it.size })
            assertEquals("bias $bias", GpsMapMarkerState.CURRENT, result.marker?.state)
            assertEquals(points.last().latitude, result.marker!!.coordinate.latitude, 0.0)
        }
    }

    @Test
    fun `monotonic fix age controls freshness despite a bounded wall clock bias`() {
        val negativeBias = listOf(point(1, 3_000).withSourceWallOffset(-2_000))
        assertEquals(GpsMapMarkerState.CURRENT,
            evidence(points = negativeBias, nowOffset = 18_000).marker?.state)

        val positiveBias = listOf(point(1, 3_000).withSourceWallOffset(2_000))
        assertEquals(GpsMapMarkerState.LAST_KNOWN,
            evidence(points = positiveBias, nowOffset = 18_001).marker?.state)
    }

    @Test
    fun `source calibration cannot excuse future source time or a device wall clock change`() {
        val futureSource = listOf(point(1, 5_000).withSourceWallOffset(-2_000))
        assertEquals(GpsMapMarkerState.LAST_KNOWN,
            evidence(points = futureSource, nowOffset = 4_000).marker?.state)

        val biasedPoints = listOf(point(1, 3_000), point(2, 4_000, 3.0))
            .map { it.withSourceWallOffset(1_034) }
        for (wallChange in listOf(-251L, 251L)) {
            assertEquals("wall change $wallChange", GpsMapMarkerState.LAST_KNOWN,
                evidence(points = biasedPoints, nowOffset = 7_000 + wallChange,
                    nowMark = mark(7_000)).marker?.state)
        }
    }

    @Test
    fun `a small backward wall clock step cannot claim current before the latest resume`() {
        val points = listOf(point(1, 3_000), point(2, 8_000))
            .map { it.withSourceWallOffset(-1_500) }
        val result = evidence(run = running().copy(lastCheckpointEpochMillis = EPOCH + 8_000),
            transitions = listOf(pause(4_000), resume(8_000)), points = points,
            nowOffset = 7_900, nowMark = mark(8_000))
        assertEquals(listOf(1, 1), result.sections.map { it.size })
        assertEquals(GpsMapMarkerState.LAST_KNOWN, result.marker?.state)
    }

    @Test
    fun `an accurate stationary fix refreshes position but cannot add route geometry`() {
        val stationary = rejected(3, 3_000, "IGNORED_WITHIN_ACCURACY")
        val result = evidence(points = listOf(point(1, 1_000), point(2, 2_000, 3.0), stationary))
        assertEquals(2, result.sections.single().size)
        assertEquals(stationary.latitude, result.marker!!.coordinate.latitude, 0.0)
        assertEquals(GpsMapMarkerState.CURRENT, result.marker.state)
    }

    @Test
    fun `rejected jump cannot move marker and weak signal loses current claim`() {
        val anchor = point(1, 1_000)
        val jump = rejected(2, 2_000, "REJECTED_IMPLAUSIBLE_JUMP").copy(latitude = 70.0)
        val result = evidence(points = listOf(anchor, jump))
        assertEquals(anchor.latitude, result.marker!!.coordinate.latitude, 0.0)
        assertEquals(GpsMapMarkerState.LAST_KNOWN, result.marker.state)
        assertEquals(listOf(1), result.sections.map { it.size })
    }

    @Test
    fun `service unavailable acquiring weak and stale states never claim current position`() {
        for (signal in listOf(null, GpsSignalStatus.ACQUIRING, GpsSignalStatus.WEAK, GpsSignalStatus.STALE)) {
            assertEquals(GpsMapMarkerState.LAST_KNOWN, evidence(signal = signal).marker?.state)
        }
    }

    @Test
    fun `source time expiry happens without any new callback`() {
        assertEquals(GpsMapMarkerState.CURRENT, evidence(points = listOf(point(1, 1_000)), nowOffset = 16_000).marker?.state)
        assertEquals(GpsMapMarkerState.LAST_KNOWN, evidence(points = listOf(point(1, 1_000)), nowOffset = 16_001).marker?.state)
    }

    @Test
    fun `future source times cannot claim current even if persisted accepted`() {
        assertEquals(GpsMapMarkerState.LAST_KNOWN,
            evidence(points = listOf(point(1, 1_000).copy(elapsedRealtimeNanos = (MONO + 5_000) * 1_000_000)),
                nowOffset = 4_000).marker?.state)
    }

    @Test
    fun `old process missing timing and wall clock changes keep last known marker`() {
        assertEquals(GpsMapMarkerState.LAST_KNOWN,
            evidence(nowMark = RunTimingMark(MONO + 4_000, OTHER_SESSION)).marker?.state)
        assertEquals(GpsMapMarkerState.LAST_KNOWN, evidence(nowMark = null).marker?.state)
        assertEquals(GpsMapMarkerState.LAST_KNOWN, evidence(
            run = running().copy(startElapsedRealtimeMillis = null, timingSessionId = null)).marker?.state)
        assertEquals(GpsMapMarkerState.LAST_KNOWN,
            evidence(nowOffset = 4_251, nowMark = mark(4_000)).marker?.state)
        assertEquals(GpsMapMarkerState.LAST_KNOWN,
            evidence(points = listOf(point(1, 1_000).withSourceWallOffset(2_001))).marker?.state)
    }

    @Test
    fun `paused marker stays explicitly paused while no new position is recorded`() {
        val result = evidence(run = running().copy(state = StoredRunState.PAUSED,
            lastCheckpointEpochMillis = EPOCH + 4_000), transitions = listOf(pause(4_000)), nowOffset = 60_000)
        assertEquals(GpsMapMarkerState.PAUSED, result.marker?.state)
        assertEquals(listOf(2), result.sections.map { it.size })
    }

    @Test
    fun `resuming cannot call the previous interval last fix current`() {
        val result = evidence(run = running().copy(lastCheckpointEpochMillis = EPOCH + 4_000),
            transitions = listOf(pause(3_000), resume(4_000)), nowOffset = 5_000)
        assertEquals(GpsMapMarkerState.LAST_KNOWN, result.marker?.state)
    }

    @Test
    fun `pause resume separates sections even if input claims a measured connecting segment`() {
        val result = evidence(run = running().copy(lastCheckpointEpochMillis = EPOCH + 4_000),
            transitions = listOf(pause(3_000), resume(4_000)), nowOffset = 6_000,
            points = listOf(point(1, 1_000), point(2, 2_000, 3.0), point(3, 5_000, 3.0)))
        assertEquals(listOf(2, 1), result.sections.map { it.size })
        assertEquals(GpsMapMarkerState.CURRENT, result.marker?.state)
    }

    @Test
    fun `new recorded anchors cannot close an unobserved interval`() {
        val result = evidence(points = listOf(point(1, 1_000), point(2, 2_000, 3.0),
            point(3, 3_000), point(4, 4_000, 3.0)))
        assertEquals(listOf(2, 2), result.sections.map { it.size })
    }

    @Test
    fun `rejections and missing sequence preserve gaps even if input next segment is nonnull`() {
        val badFix = evidence(points = listOf(point(1, 1_000), rejected(2, 2_000, "REJECTED_INACCURATE"),
            point(3, 3_000, 3.0)))
        val missing = evidence(points = listOf(point(1, 1_000), point(3, 3_000, 3.0)))
        assertEquals(listOf(1, 1), badFix.sections.map { it.size })
        assertEquals(listOf(1, 1), missing.sections.map { it.size })
    }

    @Test
    fun `source gaps split routes even when epoch gaps falsely look short`() {
        val result = evidence(points = listOf(point(1, 1_000),
            point(2, 2_000, 3.0).copy(elapsedRealtimeNanos = (MONO + 20_000) * 1_000_000)))
        assertEquals(listOf(1, 1), result.sections.map { it.size })
    }

    @Test
    fun `stationary continuity can keep an older distance anchor connected`() {
        val result = evidence(points = listOf(point(1, 1_000),
            rejected(2, 10_000, "IGNORED_WITHIN_ACCURACY"),
            rejected(3, 20_000, "IGNORED_WITHIN_ACCURACY"), point(4, 25_000, 3.0)), nowOffset = 26_000)
        assertEquals(listOf(2), result.sections.map { it.size })
        assertEquals(GpsMapMarkerState.CURRENT, result.marker?.state)
    }

    @Test
    fun `stationary fix after a source gap cannot repair the route`() {
        val result = evidence(points = listOf(point(1, 1_000),
            rejected(2, 20_000, "IGNORED_WITHIN_ACCURACY"), point(3, 21_000, 3.0)), nowOffset = 22_000)
        assertEquals(listOf(1, 1), result.sections.map { it.size })
    }

    @Test
    fun `harmless delayed rejection cannot move marker or split a valid route`() {
        val result = evidence(points = listOf(point(1, 1_000), point(2, 2_000, 3.0),
            rejected(3, 1_000, "REJECTED_OUT_OF_ORDER").copy(latitude = 70.0), point(4, 3_000, 3.0)))
        assertEquals(listOf(3), result.sections.map { it.size })
        assertEquals(GpsMapMarkerState.CURRENT, result.marker?.state)
    }

    @Test
    fun `a newer rejected out of order source is not treated as harmless delay`() {
        val result = evidence(points = listOf(point(1, 1_000), rejected(2, 2_000, "OUT_OF_ORDER")))
        assertEquals(GpsMapMarkerState.LAST_KNOWN, result.marker?.state)
    }

    @Test
    fun `mixed ownership duplicate sequence and invalid lifecycle fail quietly without coordinates`() {
        val cases = listOf(
            evidence(points = listOf(point(1, 1_000).copy(runId = OTHER_RUN))),
            evidence(points = listOf(point(1, 1_000), point(1, 2_000))),
            evidence(transitions = listOf(pause(2_000).copy(runId = OTHER_RUN))),
            evidence(transitions = listOf(resume(2_000))),
            evidence(transitions = listOf(pause(2_000).copy(sequenceNumber = 2)))
        )
        cases.forEach {
            assertTrue(it.evidenceUnavailable)
            assertTrue(it.sections.isEmpty())
            assertNull(it.marker)
        }
    }

    @Test
    fun `old incomplete lifecycle never presents a live current location`() {
        val result = evidence(run = running().copy(transitionHistoryComplete = null))
        assertEquals(listOf(2), result.sections.map { it.size })
        assertEquals(GpsMapMarkerState.LAST_KNOWN, result.marker?.state)
    }

    @Test
    fun `explicit resume after a reboot can acquire fresh evidence without joining the old route`() {
        val freshResume = resume(3_000).copy(elapsedRealtimeMillis = 2_000, timingSessionId = OTHER_SESSION)
        val freshPoint = point(2, 4_000).copy(elapsedRealtimeNanos = 3_000_000_000L)
        val delayedOldPoint = rejected(3, 1_000, "REJECTED_OUT_OF_ORDER")
        val result = evidence(run = running().copy(lastCheckpointEpochMillis = EPOCH + 3_000),
            transitions = listOf(pause(2_000), freshResume),
            points = listOf(point(1, 1_000), freshPoint, delayedOldPoint),
            nowOffset = 5_000, nowMark = RunTimingMark(4_000, OTHER_SESSION))
        assertEquals(listOf(1, 1), result.sections.map { it.size })
        assertEquals(GpsMapMarkerState.CURRENT, result.marker?.state)
        assertEquals(freshPoint.latitude, result.marker!!.coordinate.latitude, 0.0)
    }

    @Test
    fun `explicit resume in a new timing session calibrates its own bounded source offset`() {
        val freshResume = resume(3_000).copy(elapsedRealtimeMillis = 2_000, timingSessionId = OTHER_SESSION)
        val freshPoint = point(2, 4_000).copy(elapsedRealtimeNanos = 3_000_000_000L)
            .withSourceWallOffset(1_500)
        val delayedOldPoint = rejected(3, 1_000, "REJECTED_OUT_OF_ORDER")
        val result = evidence(run = running().copy(lastCheckpointEpochMillis = EPOCH + 3_000),
            transitions = listOf(pause(2_000), freshResume),
            points = listOf(point(1, 1_000), freshPoint, delayedOldPoint),
            nowOffset = 7_000, nowMark = RunTimingMark(6_000, OTHER_SESSION))
        assertEquals(listOf(1, 1), result.sections.map { it.size })
        assertEquals(GpsMapMarkerState.CURRENT, result.marker?.state)
        assertEquals(freshPoint.latitude, result.marker!!.coordinate.latitude, 0.0)
    }

    @Test
    fun `overlapping timing sessions with ambiguous bounded wall mappings never claim current`() {
        val earlierPoint = point(1, 500)
        val ambiguousPoint = point(2, 1_500).withSourceWallOffset(500)
        val freshResume = resume(3_000).copy(elapsedRealtimeMillis = MONO + 1_000,
            timingSessionId = OTHER_SESSION)
        val result = evidence(run = running().copy(lastCheckpointEpochMillis = EPOCH + 3_000),
            transitions = listOf(pause(2_000), freshResume),
            points = listOf(earlierPoint, ambiguousPoint), nowOffset = 5_000,
            nowMark = RunTimingMark(MONO + 3_000, OTHER_SESSION))
        assertFalse(result.evidenceUnavailable)
        assertEquals(listOf(1), result.sections.map { it.size })
        assertEquals(GpsMapMarkerState.LAST_KNOWN, result.marker?.state)
        assertEquals(earlierPoint.latitude, result.marker!!.coordinate.latitude, 0.0)
    }

    @Test
    fun `a completed route never labels its final recorded position current`() {
        val result = evidence(run = running().copy(state = StoredRunState.COMPLETED,
            finishEpochMillis = EPOCH + 4_000, lastCheckpointEpochMillis = EPOCH + 4_000),
            transitions = listOf(pause(3_000)))
        assertFalse(result.evidenceUnavailable)
        assertEquals(listOf(2), result.sections.map { it.size })
        assertEquals(GpsMapMarkerState.LAST_KNOWN, result.marker?.state)
    }

    @Test
    fun `latitude and dateline longitude stay raw for geographic map projection`() {
        val points = listOf(point(1, 1_000).copy(latitude = 10.0, longitude = 179.999),
            point(2, 2_000, 220.0).copy(latitude = 10.001, longitude = -179.999))
        assertEquals(listOf(179.999, -179.999), evidence(points = points).sections.single().map { it.longitude })
    }

    private fun evidence(
        run: RunEntity = running(),
        points: List<RunLocationPointEntity> = listOf(point(1, 1_000), point(2, 2_000, 3.0)),
        transitions: List<RunTransitionEntity> = emptyList(),
        nowOffset: Long = 4_000,
        nowMark: RunTimingMark? = mark(nowOffset),
        signal: GpsSignalStatus? = GpsSignalStatus.RECORDING
    ) = gpsMapEvidenceFor(run, transitions, points, EPOCH + nowOffset, nowMark, signal)

    private fun running() = RunEntity(
        RUN, StoredRunState.RUNNING, EPOCH, "America/Chicago", EPOCH,
        transitionHistoryComplete = true, trackingMode = RunTrackingMode.GPS,
        startElapsedRealtimeMillis = MONO, timingSessionId = SESSION)

    private fun point(sequence: Int, offset: Long, segment: Double? = null) = RunLocationPointEntity(
        runId = RUN, sequenceNumber = sequence, observationKey = "point-$sequence",
        observedAtEpochMillis = EPOCH + offset, elapsedRealtimeNanos = (MONO + offset) * 1_000_000L,
        latitude = 29.76 + sequence * 0.00001, longitude = -95.36 + sequence * 0.00001,
        horizontalAccuracyMeters = 4f, altitudeMeters = null, speedMetersPerSecond = null,
        bearingDegrees = null, acceptedForDistance = true, rejectionReason = null,
        segmentDistanceMeters = segment, cumulativeDistanceMeters = segment)

    private fun rejected(sequence: Int, offset: Long, reason: String) = point(sequence, offset).copy(
        acceptedForDistance = false, rejectionReason = reason)

    private fun RunLocationPointEntity.withSourceWallOffset(offsetMillis: Long) =
        copy(observedAtEpochMillis = observedAtEpochMillis + offsetMillis)

    private fun pause(offset: Long) = RunTransitionEntity(
        RUN, 1, RunTransitionType.PAUSE, EPOCH + offset, MONO + offset, SESSION)

    private fun resume(offset: Long) = RunTransitionEntity(
        RUN, 2, RunTransitionType.RESUME, EPOCH + offset, MONO + offset, SESSION)

    private fun mark(offset: Long) = RunTimingMark(MONO + offset, SESSION)

    companion object {
        private const val RUN = "0f6a2c1e-9d43-4b7a-9c21-7b5e8a4d1f30"
        private const val OTHER_RUN = "ef6a2c1e-9d43-4b7a-9c21-7b5e8a4d1f30"
        private const val SESSION = "120e2c1e-9d43-4b7a-9c21-7b5e8a4d1f30"
        private const val OTHER_SESSION = "220e2c1e-9d43-4b7a-9c21-7b5e8a4d1f30"
        private const val EPOCH = 1_800_000_000_000L
        private const val MONO = 100_000L
    }
}
