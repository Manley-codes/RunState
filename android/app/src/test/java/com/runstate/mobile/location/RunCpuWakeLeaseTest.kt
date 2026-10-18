package com.runstate.mobile.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RunCpuWakeLeaseTest {
    @Test
    fun `mock fix cannot establish the initial recording anchor or use inaccurate edge exemption`() {
        for (accuracy in listOf(3.0, 50.0)) {
            val policy = GpsDistancePolicy()
            val rejected = recordingGpsDecision(policy,
                gpsSample(0.0, 1_000).copy(horizontalAccuracyMeters = accuracy), 1_000, isMock = true)
            assertEquals(GpsSampleStatus.REJECTED_INVALID, rejected.status)
            assertFalse(rejected.acceptedForRoute)
            assertNull(rejected.distanceAddedMeters)
            assertNull(rejected.totalDistanceMeters)
            assertTrue(rejected.hadUnobservedGap)
            val real = recordingGpsDecision(policy, gpsSample(30.0, 6_000), 6_000, isMock = false)
            assertEquals(GpsSampleStatus.ACCEPTED_ANCHOR, real.status)
            assertNull(real.distanceAddedMeters)
        }
    }

    @Test
    fun `mock fix preserves earned distance and forces the next real fix to reanchor`() {
        val policy = GpsDistancePolicy()
        recordingGpsDecision(policy, gpsSample(0.0, 1_000), 1_000, isMock = false)
        val earned = recordingGpsDecision(policy, gpsSample(30.0, 6_000), 6_000, isMock = false)
        assertEquals(GpsSampleStatus.ACCEPTED_SEGMENT, earned.status)
        val rejected = recordingGpsDecision(policy, gpsSample(60.0, 11_000), 11_000, isMock = true)
        assertEquals(GpsSampleStatus.REJECTED_INVALID, rejected.status)
        assertFalse(rejected.acceptedForRoute)
        assertNull(rejected.distanceAddedMeters)
        assertEquals(earned.totalDistanceMeters, rejected.totalDistanceMeters)
        val anchor = recordingGpsDecision(policy, gpsSample(90.0, 16_000), 16_000, isMock = false)
        assertEquals(GpsSampleStatus.ACCEPTED_ANCHOR, anchor.status)
        assertNull(anchor.distanceAddedMeters)
        val resumed = recordingGpsDecision(policy, gpsSample(120.0, 21_000), 21_000, isMock = false)
        assertEquals(GpsSampleStatus.ACCEPTED_SEGMENT, resumed.status)
        assertEquals(60.0, resumed.totalDistanceMeters!!, 0.8)
    }

    @Test
    fun `pre registration source cannot seed distance even with a forward epoch clock`() {
        val boundary = 2_000_000_001L
        val policy = GpsDistancePolicy()
        var observations = 0
        fun deliver(sample: GpsSample, nanos: Long, now: Long): GpsDecision? {
            if (!gpsFixBelongsToRegistration(nanos, boundary)) return null
            observations++
            return policy.observe(sample, now, true)
        }
        val cached = gpsSample(0.0, 2_000).copy(observedEpochMillis = EPOCH + 60_000)
        assertNull(deliver(cached, boundary - 1, 2_001))
        assertEquals(0, observations)
        assertNull(policy.distanceMeters)

        val anchor = deliver(gpsSample(10.0, 3_000), 3_000_000_000, 3_000)!!
        assertEquals(GpsSampleStatus.ACCEPTED_ANCHOR, anchor.status)
        assertNull(anchor.distanceAddedMeters)
        val segment = deliver(gpsSample(40.0, 8_000), 8_000_000_000, 8_000)!!
        assertEquals(GpsSampleStatus.ACCEPTED_SEGMENT, segment.status)
        assertEquals(30.0, segment.totalDistanceMeters!!, 0.4)
        assertEquals(2, observations)
        assertFalse(segment.hadUnobservedGap)
    }

    @Test
    fun `registration boundary keeps nanosecond equality and moves forward on reacquisition`() {
        val firstRegistration = 2_000_000_001L
        assertFalse(gpsFixBelongsToRegistration(firstRegistration - 1, firstRegistration))
        assertTrue(gpsFixBelongsToRegistration(firstRegistration, firstRegistration))
        assertTrue(gpsFixBelongsToRegistration(firstRegistration + 1, firstRegistration))

        val resumedRegistration = 7_000_000_003L
        assertFalse(gpsFixBelongsToRegistration(firstRegistration + 1, resumedRegistration))
        assertFalse(gpsFixBelongsToRegistration(resumedRegistration - 1, resumedRegistration))
        assertTrue(gpsFixBelongsToRegistration(resumedRegistration, resumedRegistration))
    }

    @Test
    fun `registration admission does not replace future or stale distance policy checks`() {
        val boundary = 2_000_000_000L
        assertFalse(gpsFixBelongsToRegistration(-1, boundary))
        assertTrue(gpsFixBelongsToRegistration(5_000_000_000, boundary))
        val future = GpsDistancePolicy().observe(gpsSample(0.0, 5_000), 4_999, true)
        assertEquals(GpsSampleStatus.REJECTED_INVALID, future.status)
        assertNull(future.totalDistanceMeters)

        assertTrue(gpsFixBelongsToRegistration(3_000_000_000, boundary))
        val stale = GpsDistancePolicy().observe(gpsSample(0.0, 3_000), 13_001, true)
        assertEquals(GpsSampleStatus.REJECTED_STALE, stale.status)
        assertNull(stale.totalDistanceMeters)
    }

    @Test
    fun `start renewal pause and restart never stack wake locks`() {
        val events = mutableListOf<String>()
        val locks = mutableListOf<FakeWakeLock>()
        val lease = RunCpuWakeLease(
            newLock = { FakeWakeLock("lock${locks.size + 1}", events).also { locks.add(it) } },
            timeoutMillis = 120_000L
        )

        lease.renew() // A stopped or paused run must not restart itself.
        lease.start()
        lease.start()
        assertEquals(1, locks.size)
        assertTrue(locks[0].isHeld)

        lease.renew()
        assertEquals(2, locks.size)
        assertEquals(
            listOf("lock1.acquire:120000", "lock2.acquire:120000", "lock1.release"),
            events
        )
        assertFalse(locks[0].isHeld)
        assertTrue(locks[1].isHeld)

        lease.stop() // Pause, provider loss, failure, completion, and destroy use this path.
        lease.stop()
        lease.renew()
        assertFalse(locks[1].isHeld)
        assertEquals(2, locks.size)
        assertEquals(1, events.count { it == "lock2.release" })

        lease.start() // Explicit resume takes a fresh timed lock.
        assertEquals(3, locks.size)
        assertTrue(locks[2].isHeld)
        lease.stop()
        assertTrue(locks.none { it.isHeld })
    }

    @Test
    fun `failed renewal cleans replacement and lets stop release previous lock`() {
        val events = mutableListOf<String>()
        val locks = mutableListOf<FakeWakeLock>()
        val lease = RunCpuWakeLease(
            newLock = {
                FakeWakeLock("lock${locks.size + 1}", events).also {
                    if (locks.isNotEmpty()) it.failAfterAcquire = true
                    locks.add(it)
                }
            },
            timeoutMillis = 120_000L
        )
        lease.start()

        try {
            lease.renew()
            throw AssertionError("Expected the simulated platform acquisition to fail")
        } catch (_: IllegalStateException) {
            // The service catches this and stops GPS, which calls lease.stop().
        }
        assertTrue(locks[0].isHeld)
        assertFalse(locks[1].isHeld)
        lease.stop()
        assertTrue(locks.none { it.isHeld })
    }

    @Test
    fun `expired timeout can be stopped and replaced safely`() {
        val locks = mutableListOf<FakeWakeLock>()
        val lease = RunCpuWakeLease(
            newLock = {
                FakeWakeLock("lock${locks.size + 1}", mutableListOf()).also { locks.add(it) }
            },
            timeoutMillis = 120_000L
        )
        lease.start()
        locks[0].held = false // Android's hard timeout released it before service cleanup.
        lease.stop()
        lease.start()
        assertEquals(2, locks.size)
        assertTrue(locks[1].isHeld)
        lease.stop()
    }

    private class FakeWakeLock(
        private val name: String,
        private val events: MutableList<String>
    ) : TimedCpuWakeLock {
        var held = false
        var failAfterAcquire = false
        override val isHeld: Boolean get() = held

        override fun acquire(timeoutMillis: Long) {
            events.add("$name.acquire:$timeoutMillis")
            held = true
            if (failAfterAcquire) throw IllegalStateException("Simulated platform failure")
        }

        override fun release() {
            events.add("$name.release")
            held = false
        }
    }

    private fun gpsSample(metersEast: Double, sourceMillis: Long) = GpsSample(
        latitudeDegrees = 0.0,
        longitudeDegrees = metersEast / 111_195.0,
        horizontalAccuracyMeters = 3.0,
        observedEpochMillis = EPOCH + sourceMillis,
        elapsedRealtimeMillis = sourceMillis
    )

    private companion object {
        const val EPOCH = 1_780_000_000_000L
    }
}
