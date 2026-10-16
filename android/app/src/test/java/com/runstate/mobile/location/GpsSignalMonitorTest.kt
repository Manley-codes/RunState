package com.runstate.mobile.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GpsSignalMonitorTest {
    @Test
    fun `acquiring with no callbacks eventually becomes stale`() {
        val monitor = monitor()
        assertEquals(GpsSignalStatus.ACQUIRING, monitor.status(1_000L))
        assertEquals(GpsSignalStatus.ACQUIRING, monitor.status(16_000L))
        assertEquals(GpsSignalStatus.STALE, monitor.status(16_001L))
    }

    @Test
    fun `an accurate stationary fix records signal without claiming distance`() {
        val policy = GpsDistancePolicy()
        val monitor = monitor()
        policy.observe(sample(2_000L), 2_000L, true)
        val stationary = policy.observe(sample(3_000L), 3_000L, true)
        assertEquals(GpsSampleStatus.IGNORED_WITHIN_ACCURACY, stationary.status)
        assertEquals(null, stationary.totalDistanceMeters)
        assertEquals(
            GpsSignalStatus.RECORDING,
            monitor.onStoredFix(
                stationary.status, 3_000L,
                isDurablyUsableGpsFix(stationary.status, false, stationary.status.name), 3_000L
            )
        )
    }

    @Test
    fun `freshness uses source time rather than a late database completion`() {
        val monitor = monitor()
        assertEquals(
            GpsSignalStatus.RECORDING,
            monitor.onStoredFix(GpsSampleStatus.ACCEPTED_ANCHOR, 2_000L, true, 12_000L)
        )
        assertEquals(GpsSignalStatus.RECORDING, monitor.status(17_000L))
        assertEquals(GpsSignalStatus.STALE, monitor.status(17_001L))
    }

    @Test
    fun `a fix committed after expiry never briefly says recording`() {
        val monitor = monitor()
        assertEquals(
            GpsSignalStatus.STALE,
            monitor.onStoredFix(GpsSampleStatus.ACCEPTED_ANCHOR, 2_000L, true, 25_000L)
        )
    }

    @Test
    fun `signal expiry during delayed persistence preserves source contiguous distance`() {
        val policy = GpsDistancePolicy()
        val monitor = monitor()
        val anchor = policy.observe(sample(2_000L), 2_000L, true)
        monitor.onStoredFix(anchor.status, 2_000L, true, 2_000L)

        val inFlight = policy.observe(sample(6_000L, northMeters = 20.0), 6_000L, true)
        assertEquals(GpsSampleStatus.ACCEPTED_SEGMENT, inFlight.status)
        // The write is slow, so the signal surface expires. This does not establish a
        // source-time gap: the next queued observation was delivered four seconds later.
        assertEquals(GpsSignalStatus.STALE, monitor.status(22_000L))
        assertEquals(
            GpsSignalStatus.STALE,
            monitor.onStoredFix(inFlight.status, 6_000L, true, 23_000L)
        )
        val queued = policy.observe(sample(10_000L, northMeters = 40.0), 10_000L, true)
        assertEquals(GpsSampleStatus.ACCEPTED_SEGMENT, queued.status)
        assertEquals(40.0, queued.totalDistanceMeters!!, 0.3)
        assertFalse(queued.hadUnobservedGap)
        assertEquals(
            GpsSignalStatus.STALE,
            monitor.onStoredFix(queued.status, 10_000L, true, 26_000L)
        )
    }

    @Test
    fun `bad fixes are weak and cannot keep an old usable signal alive`() {
        val monitor = monitor()
        monitor.onStoredFix(GpsSampleStatus.ACCEPTED_ANCHOR, 2_000L, true, 2_000L)
        assertEquals(
            GpsSignalStatus.WEAK,
            monitor.onStoredFix(GpsSampleStatus.REJECTED_INACCURATE, 3_000L, false, 3_000L)
        )
        assertEquals(
            GpsSignalStatus.STALE,
            monitor.onStoredFix(GpsSampleStatus.REJECTED_INACCURATE, 18_000L, false, 18_000L)
        )
    }

    @Test
    fun `rejected stale fix cannot claim recording before any good point`() {
        val monitor = monitor()
        assertEquals(
            GpsSignalStatus.WEAK,
            monitor.onStoredFix(GpsSampleStatus.REJECTED_STALE, 2_000L, false, 13_000L)
        )
        assertEquals(GpsSignalStatus.STALE, monitor.status(16_001L))
    }

    @Test
    fun `duplicates and out of order delivery neither refresh nor spoil current signal`() {
        val monitor = monitor()
        monitor.onStoredFix(GpsSampleStatus.ACCEPTED_ANCHOR, 2_000L, true, 2_000L)
        assertEquals(
            GpsSignalStatus.RECORDING,
            monitor.onStoredFix(GpsSampleStatus.REJECTED_OUT_OF_ORDER, 1_500L, false, 3_000L)
        )
        monitor.onStoredFix(GpsSampleStatus.ACCEPTED_ANCHOR, 2_000L, true, 15_000L)
        assertEquals(GpsSignalStatus.STALE, monitor.status(17_001L))
    }

    @Test
    fun `fresh committed fix recovers after loss but retains its own expiry`() {
        val monitor = monitor()
        assertEquals(GpsSignalStatus.STALE, monitor.status(20_000L))
        assertEquals(
            GpsSignalStatus.RECORDING,
            monitor.onStoredFix(GpsSampleStatus.ACCEPTED_ANCHOR, 20_000L, true, 20_500L)
        )
        assertEquals(GpsSignalStatus.STALE, monitor.status(35_001L))
    }

    @Test
    fun `resume reset does not reuse an earlier good fix`() {
        val monitor = monitor()
        monitor.onStoredFix(GpsSampleStatus.ACCEPTED_ANCHOR, 2_000L, true, 2_000L)
        monitor.reset(10_000L)
        assertEquals(GpsSignalStatus.ACQUIRING, monitor.status(10_000L))
        assertEquals(
            GpsSignalStatus.WEAK,
            monitor.onStoredFix(GpsSampleStatus.ACCEPTED_ANCHOR, 2_000L, true, 11_000L)
        )
        assertEquals(GpsSignalStatus.STALE, monitor.status(25_001L))
    }

    @Test
    fun `durable checkpoint rejection overrides in memory acceptance`() {
        assertFalse(isDurablyUsableGpsFix(GpsSampleStatus.ACCEPTED_SEGMENT, false, "CROSSES_CHECKPOINT"))
        assertFalse(isDurablyUsableGpsFix(GpsSampleStatus.ACCEPTED_ANCHOR, false, "NOT_RUNNING"))
        assertFalse(isDurablyUsableGpsFix(GpsSampleStatus.IGNORED_WITHIN_ACCURACY, false, "BEFORE_CHECKPOINT"))
        assertTrue(isDurablyUsableGpsFix(GpsSampleStatus.ACCEPTED_ANCHOR, true, null))
        assertTrue(isDurablyUsableGpsFix(GpsSampleStatus.ACCEPTED_SEGMENT, true, null))
    }

    @Test
    fun `stored rejected or cached duplicate observations do not qualify as recovery`() {
        GpsSampleStatus.entries.filter {
            it != GpsSampleStatus.ACCEPTED_ANCHOR && it != GpsSampleStatus.ACCEPTED_SEGMENT &&
                it != GpsSampleStatus.IGNORED_WITHIN_ACCURACY
        }.forEach { rejected ->
            assertFalse(isDurablyUsableGpsFix(rejected, false, rejected.name))
            assertFalse(isDurablyUsableGpsFix(rejected, true, null))
        }
        assertTrue(isDurablyUsableGpsFix(
            GpsSampleStatus.IGNORED_WITHIN_ACCURACY, false, GpsSampleStatus.IGNORED_WITHIN_ACCURACY.name
        ))
    }

    @Test
    fun `durably downgraded good fix cannot become recording`() {
        val monitor = monitor()
        assertEquals(
            GpsSignalStatus.WEAK,
            monitor.onStoredFix(
                GpsSampleStatus.ACCEPTED_SEGMENT, 2_000L,
                isDurablyUsableGpsFix(GpsSampleStatus.ACCEPTED_SEGMENT, false, "CROSSES_CHECKPOINT"),
                2_000L
            )
        )
    }

    private fun monitor() = GpsSignalMonitor().apply { reset(1_000L) }

    private fun sample(time: Long, northMeters: Double = 0.0) = GpsSample(
        latitudeDegrees = 41.0 + northMeters / 111_195.0,
        longitudeDegrees = -87.0,
        horizontalAccuracyMeters = 5.0,
        observedEpochMillis = 1_700_000_000_000L + time,
        elapsedRealtimeMillis = time
    )
}
