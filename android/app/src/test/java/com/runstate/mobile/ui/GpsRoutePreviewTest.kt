package com.runstate.mobile.ui

import com.runstate.mobile.data.local.RunLocationPointEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GpsRoutePreviewTest {
    private val runId = "123e4567-e89b-12d3-a456-426614174000"

    @Test
    fun `accepted segments draw in durable order and ignored callbacks stay out`() {
        val route = gpsRoutePreviewFor(runId, listOf(
            point(4, 29.7608, -95.3696, segment = 20.0),
            point(2, 0.0, 0.0, accepted = false, reason = "IGNORED_WITHIN_ACCURACY"),
            point(1, 29.7604, -95.3698),
            point(3, 29.7606, -95.3697, segment = 20.0)
        ))
        assertEquals(1, route.sections.size)
        assertEquals(3, route.sections.single().size)
        assertEquals(3, route.acceptedPointCount)
        assertEquals(2, route.measuredSegmentCount)
        assertTrue(route.sections.single().first().y > route.sections.single().last().y)
        assertTrue(route.hasMeasuredMovement)
    }

    @Test
    fun `new anchor preserves pause and recovery gaps even with prior cumulative distance`() {
        val route = gpsRoutePreviewFor(runId, listOf(
            point(1, 29.7600, -95.3690),
            point(2, 29.7602, -95.3690, segment = 22.0, cumulative = 22.0),
            point(3, 29.7610, -95.3690, accepted = false, reason = "CROSSES_CHECKPOINT"),
            point(4, 29.7620, -95.3690, cumulative = 22.0),
            point(5, 29.7622, -95.3690, segment = 22.0, cumulative = 44.0),
            point(6, 29.7630, -95.3690, accepted = false, reason = "REJECTED_INACCURATE"),
            point(7, 29.7640, -95.3690, cumulative = 44.0)
        ))
        assertEquals(listOf(2, 2, 1), route.sections.map { it.size })
        assertEquals(2, route.measuredSegmentCount)
        assertTrue(route.sections[0].last().y > route.sections[1].first().y)
        assertTrue(route.sections[1].last().y > route.sections[2].first().y)
    }

    @Test
    fun `out of order rejected fix cannot become a plotted detour or split`() {
        val route = gpsRoutePreviewFor(runId, listOf(
            point(1, 29.76, -95.36),
            point(2, 80.0, 20.0, accepted = false, reason = "OUT_OF_ORDER"),
            point(3, 29.761, -95.36, segment = 100.0)
        ))
        assertEquals(1, route.sections.size)
        assertEquals(2, route.sections.single().size)
        assertEquals(0.0, route.normalizedWidth, 0.0)
        assertEquals(1.0, route.normalizedHeight, 0.0)
    }

    @Test
    fun `single anchor and repeated locations have centred finite bounds`() {
        val single = gpsRoutePreviewFor(runId, listOf(point(1, 29.76, -95.36)))
        assertEquals(listOf(listOf(GpsRouteCoordinate(0.5, 0.5))), single.sections)
        assertFalse(single.hasMeasuredMovement)
        assertEquals(0.0, single.normalizedWidth, 0.0)
        assertEquals(0.0, single.normalizedHeight, 0.0)
        val repeated = gpsRoutePreviewFor(runId, listOf(
            point(1, 29.76, -95.36), point(2, 29.76, -95.36, segment = 0.0)
        ))
        assertFalse(repeated.hasMeasuredMovement)
        assertTrue(repeated.sections.flatten().all { it.x.isFinite() && it.y.isFinite() })
    }

    @Test
    fun `missing usable points is distinct from zero measured distance`() {
        val empty = gpsRoutePreviewFor(runId, emptyList())
        val rejected = gpsRoutePreviewFor(runId, listOf(
            point(1, 29.76, -95.36, accepted = false, reason = "REJECTED_STALE")
        ))
        assertEquals(empty, rejected)
        assertTrue(empty.sections.isEmpty())
        assertFalse(empty.hasMeasuredMovement)
    }

    @Test
    fun `date line crossing stays locally proportionate`() {
        val route = gpsRoutePreviewFor(runId, listOf(
            point(1, 10.0, 179.999),
            point(2, 10.001, -179.999, segment = 240.0)
        ))
        assertTrue(route.normalizedWidth > route.normalizedHeight)
        assertTrue(route.normalizedHeight > 0.4)
        assertTrue(route.normalizedHeight < 0.6)
        val section = route.sections.single()
        assertTrue(section.last().x > section.first().x)
        assertTrue(section.last().y < section.first().y)
        assertTrue(section.all { it.x in 0.0..1.0 && it.y in 0.0..1.0 })
    }

    @Test
    fun `horizontal and polar routes do not divide by zero`() {
        for (latitude in listOf(29.76, 90.0, -90.0)) {
            val route = gpsRoutePreviewFor(runId, listOf(
                point(1, latitude, 0.0), point(2, latitude, 0.001, segment = 10.0)
            ))
            assertEquals(1.0, route.normalizedWidth, 0.0)
            assertEquals(0.0, route.normalizedHeight, 0.0)
            assertTrue(route.sections.flatten().all { it.x.isFinite() && it.y == 0.5 })
        }
    }

    @Test
    fun `wrong run and duplicate sequence cannot be used as a route`() {
        assertThrows(IllegalArgumentException::class.java) {
            gpsRoutePreviewFor("another-run", listOf(point(1, 29.76, -95.36)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            gpsRoutePreviewFor(runId, listOf(point(1, 29.76, -95.36), point(1, 29.77, -95.36)))
        }
    }

    private fun point(
        sequence: Int,
        latitude: Double,
        longitude: Double,
        segment: Double? = null,
        cumulative: Double? = segment,
        accepted: Boolean = true,
        reason: String? = null
    ) = RunLocationPointEntity(
        runId = runId,
        sequenceNumber = sequence,
        observationKey = "point-$sequence",
        observedAtEpochMillis = 1_756_000_000_000L + sequence * 1_000L,
        elapsedRealtimeNanos = sequence * 1_000_000_000L,
        latitude = latitude,
        longitude = longitude,
        horizontalAccuracyMeters = 4f,
        altitudeMeters = null,
        speedMetersPerSecond = null,
        bearingDegrees = null,
        acceptedForDistance = accepted,
        rejectionReason = reason,
        segmentDistanceMeters = if (accepted) segment else null,
        cumulativeDistanceMeters = if (accepted) cumulative else null
    )
}
