package com.runstate.mobile.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GpsDistancePolicyTest {
    @Test
    fun `no points and one anchor do not claim zero measured distance`() {
        val policy = GpsDistancePolicy()
        assertNull(policy.distanceMeters)

        val first = policy.observe(sample(0.0, 1_000L), 1_000L, isRunning = true)
        assertEquals(GpsSampleStatus.ACCEPTED_ANCHOR, first.status)
        assertTrue(first.acceptedForRoute)
        assertNull(first.totalDistanceMeters)
    }

    @Test
    fun `plausible movement counts only after it clears positional uncertainty`() {
        val policy = GpsDistancePolicy()
        policy.observe(sample(0.0, 1_000L, accuracy = 5.0), 1_000L, true)

        val jitter = policy.observe(sample(6.0, 3_000L, accuracy = 5.0), 3_000L, true)
        assertEquals(GpsSampleStatus.IGNORED_WITHIN_ACCURACY, jitter.status)
        assertFalse(jitter.acceptedForRoute)
        assertNull(jitter.totalDistanceMeters)

        val movement = policy.observe(sample(20.0, 6_000L, accuracy = 5.0), 6_000L, true)
        assertEquals(GpsSampleStatus.ACCEPTED_SEGMENT, movement.status)
        assertEquals(20.0, movement.distanceAddedMeters!!, 0.3)
        assertEquals(20.0, movement.totalDistanceMeters!!, 0.3)
    }

    @Test
    fun `one second jitter inside accuracy does not interrupt tracking`() {
        val policy = GpsDistancePolicy()
        policy.observe(sample(0.0, 1_000L, accuracy = 6.0), 1_000L, true)

        val jitter = policy.observe(sample(10.0, 2_000L, accuracy = 6.0), 2_000L, true)

        assertEquals(GpsSampleStatus.IGNORED_WITHIN_ACCURACY, jitter.status)
        assertFalse(jitter.hadUnobservedGap)
        assertNull(jitter.totalDistanceMeters)
    }

    @Test
    fun `poor accuracy breaks the path but retains already measured distance`() {
        val policy = GpsDistancePolicy()
        policy.observe(sample(0.0, 1_000L), 1_000L, true)
        policy.observe(sample(20.0, 5_000L), 5_000L, true)

        val poor = policy.observe(sample(30.0, 7_000L, accuracy = 80.0), 7_000L, true)
        assertEquals(GpsSampleStatus.REJECTED_INACCURATE, poor.status)
        assertTrue(poor.hadUnobservedGap)

        val newAnchor = policy.observe(sample(50.0, 9_000L), 9_000L, true)
        assertEquals(GpsSampleStatus.ACCEPTED_ANCHOR, newAnchor.status)
        val later = policy.observe(sample(80.0, 14_000L), 14_000L, true)
        assertEquals(50.0, later.totalDistanceMeters!!, 0.6)
    }

    @Test
    fun `stale fix cannot close the unknown interval`() {
        val policy = GpsDistancePolicy()
        policy.observe(sample(0.0, 1_000L), 1_000L, true)

        val stale = policy.observe(sample(30.0, 2_000L), 15_000L, true)
        assertEquals(GpsSampleStatus.REJECTED_STALE, stale.status)
        assertTrue(stale.hadUnobservedGap)

        assertEquals(
            GpsSampleStatus.ACCEPTED_ANCHOR,
            policy.observe(sample(30.0, 15_000L), 15_000L, true).status
        )
        val later = policy.observe(sample(60.0, 20_000L), 20_000L, true)
        assertEquals(30.0, later.totalDistanceMeters!!, 0.4)
    }

    @Test
    fun `duplicate and out of order fixes do not count twice or break a current anchor`() {
        val policy = GpsDistancePolicy()
        policy.observe(sample(0.0, 1_000L), 1_000L, true)
        policy.observe(sample(30.0, 6_000L), 6_000L, true)

        assertEquals(
            GpsSampleStatus.REJECTED_OUT_OF_ORDER,
            policy.observe(sample(60.0, 6_000L), 6_000L, true).status
        )
        assertEquals(
            GpsSampleStatus.REJECTED_OUT_OF_ORDER,
            policy.observe(sample(45.0, 5_000L), 6_000L, true).status
        )

        val later = policy.observe(sample(60.0, 11_000L), 11_000L, true)
        assertEquals(60.0, later.totalDistanceMeters!!, 0.6)
        assertFalse(later.hadUnobservedGap)
    }

    @Test
    fun `implausible jump is excluded and subsequent distance starts a new section`() {
        val policy = GpsDistancePolicy()
        policy.observe(sample(0.0, 1_000L), 1_000L, true)

        val jump = policy.observe(sample(300.0, 2_000L), 2_000L, true)
        assertEquals(GpsSampleStatus.REJECTED_IMPLAUSIBLE_JUMP, jump.status)
        assertNull(jump.totalDistanceMeters)
        assertTrue(jump.hadUnobservedGap)

        assertEquals(
            GpsSampleStatus.ACCEPTED_ANCHOR,
            policy.observe(sample(310.0, 3_000L), 3_000L, true).status
        )
        val later = policy.observe(sample(340.0, 8_000L), 8_000L, true)
        assertEquals(30.0, later.totalDistanceMeters!!, 0.4)
    }

    @Test
    fun `long point gap never becomes an invented straight segment`() {
        val policy = GpsDistancePolicy()
        policy.observe(sample(0.0, 1_000L), 1_000L, true)
        policy.observe(sample(30.0, 6_000L), 6_000L, true)

        val resumed = policy.observe(sample(130.0, 30_000L), 30_000L, true)
        assertEquals(GpsSampleStatus.ACCEPTED_ANCHOR, resumed.status)
        assertTrue(resumed.hadUnobservedGap)
        assertEquals(30.0, resumed.totalDistanceMeters!!, 0.4)

        val later = policy.observe(sample(160.0, 35_000L), 35_000L, true)
        assertEquals(60.0, later.totalDistanceMeters!!, 0.6)
    }

    @Test
    fun `pause excludes callbacks and resumed tracking does not span pause`() {
        val policy = GpsDistancePolicy()
        policy.observe(sample(0.0, 1_000L), 1_000L, true)
        policy.observe(sample(30.0, 6_000L), 6_000L, true)
        policy.pause()

        val paused = policy.observe(sample(50.0, 8_000L), 8_000L, isRunning = false)
        assertEquals(GpsSampleStatus.REJECTED_NOT_RUNNING, paused.status)
        assertEquals(30.0, paused.totalDistanceMeters!!, 0.4)

        assertEquals(
            GpsSampleStatus.ACCEPTED_ANCHOR,
            policy.observe(sample(60.0, 40_000L), 40_000L, true).status
        )
        val later = policy.observe(sample(90.0, 45_000L), 45_000L, true)
        assertEquals(60.0, later.totalDistanceMeters!!, 0.6)
        assertFalse(later.hadUnobservedGap)
    }

    @Test
    fun `service interruption cannot bridge its unobserved span`() {
        val policy = GpsDistancePolicy()
        policy.observe(sample(0.0, 1_000L), 1_000L, true)
        policy.observe(sample(30.0, 6_000L), 6_000L, true)
        policy.trackingInterrupted()

        val restart = policy.observe(sample(120.0, 8_000L), 8_000L, true)
        assertEquals(GpsSampleStatus.ACCEPTED_ANCHOR, restart.status)
        assertEquals(30.0, restart.totalDistanceMeters!!, 0.4)
        assertTrue(restart.hadUnobservedGap)
    }

    @Test
    fun `invalid coordinates and missing accuracy never become route evidence`() {
        val policy = GpsDistancePolicy()
        val invalid = policy.observe(
            sample(0.0, 1_000L).copy(latitudeDegrees = Double.NaN),
            1_000L,
            true
        )
        assertEquals(GpsSampleStatus.REJECTED_INVALID, invalid.status)
        assertFalse(invalid.acceptedForRoute)

        val missingAccuracy = policy.observe(
            sample(0.0, 2_000L).copy(horizontalAccuracyMeters = null),
            2_000L,
            true
        )
        assertEquals(GpsSampleStatus.REJECTED_INACCURATE, missingAccuracy.status)
        assertNull(policy.distanceMeters)
    }

    private fun sample(
        metersEast: Double,
        elapsedRealtimeMillis: Long,
        accuracy: Double = 3.0
    ): GpsSample = GpsSample(
        latitudeDegrees = 0.0,
        longitudeDegrees = metersEast / 111_195.0,
        horizontalAccuracyMeters = accuracy,
        observedEpochMillis = 1_780_000_000_000L + elapsedRealtimeMillis,
        elapsedRealtimeMillis = elapsedRealtimeMillis
    )
}
