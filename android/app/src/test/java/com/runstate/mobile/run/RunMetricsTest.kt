package com.runstate.mobile.run

import com.runstate.mobile.data.local.DistanceUnit
import com.runstate.mobile.data.local.MetricSource
import com.runstate.mobile.data.local.TelemetryCoverage
import org.junit.Assert.assertThrows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the small coordinator-to-screen metric contract. */
class RunMetricsTest {

    private fun gpsAt(meters: Double, coverage: TelemetryCoverage = TelemetryCoverage.COMPLETE) =
        RunMetrics(600_000, 600_000, meters, 600.0 / meters,
            DistanceUnit.MILES, MetricSource.GPS, coverage)

    @Test fun `mile announcement uses measured facts only and consumes crossing once`() {
        val announcements = RunMilestoneAnnouncements()
        assertNull(announcements.update(gpsAt(1_600.0), true, true))
        val text = announcements.update(gpsAt(1_609.344), true, true)
        assertEquals("1 mile completed. Average pace, 10 minutes per mile. Active time, 10 minutes.", text)
        assertNull(announcements.update(gpsAt(1_610.0), true, true))
        assertNull(announcements.update(gpsAt(1_610.0), false, true))
        assertNull(announcements.update(gpsAt(1_620.0), true, true))
    }

    @Test fun `disabled and paused crossings never replay when conditions improve`() {
        listOf(Triple(false, true, TelemetryCoverage.COMPLETE),
            Triple(true, false, TelemetryCoverage.COMPLETE)).forEach { (running, enabled, coverage) ->
            val announcements = RunMilestoneAnnouncements()
            assertNull(announcements.update(gpsAt(1_610.0, coverage), running, enabled))
            assertNull(announcements.update(gpsAt(1_620.0), true, true))
            assertTrue(announcements.update(gpsAt(3_220.0), true, true)!!.startsWith("2 miles completed."))
        }
    }

    @Test fun `fresh partial crossing can announce once after brief GPS recovery`() {
        val announcements = RunMilestoneAnnouncements()
        assertNull(announcements.update(gpsAt(1_610.0, TelemetryCoverage.PARTIAL), true, true, 10_000))
        assertNull(announcements.update(gpsAt(1_620.0, TelemetryCoverage.PARTIAL), true, true, 14_000))
        assertTrue(announcements.update(gpsAt(1_625.0), true, true, 24_000)!!.startsWith("1 mile completed."))
        assertNull(announcements.update(gpsAt(1_630.0), true, true, 25_000))
    }

    @Test fun `partial crossing expires from its first observation without extending on repeated samples`() {
        val announcements = RunMilestoneAnnouncements()
        assertNull(announcements.update(gpsAt(1_610.0, TelemetryCoverage.PARTIAL), true, true, 10_000))
        assertNull(announcements.update(gpsAt(1_620.0, TelemetryCoverage.PARTIAL), true, true, 24_000))
        assertNull(announcements.update(gpsAt(1_625.0), true, true, 25_001))
        assertNull(announcements.update(gpsAt(1_630.0), true, true, 26_000))
        assertTrue(announcements.update(gpsAt(3_220.0), true, true, 600_000)!!.startsWith("2 miles completed."))
    }

    @Test fun `pausing or disabling clears a pending partial crossing even without a new mile`() {
        for ((running, enabled) in listOf(false to true, true to false)) {
            val announcements = RunMilestoneAnnouncements()
            assertNull(announcements.update(gpsAt(1_610.0, TelemetryCoverage.PARTIAL), true, true, 10_000))
            assertNull(announcements.update(gpsAt(1_615.0), running, enabled, 11_000))
            assertNull(announcements.update(gpsAt(1_620.0), true, true, 12_000))
        }
    }

    @Test fun `service lifecycle or read failure discard keeps crossing consumed and later miles eligible`() {
        val announcements = RunMilestoneAnnouncements()
        assertNull(announcements.update(gpsAt(1_610.0, TelemetryCoverage.PARTIAL), true, true, 10_000))
        // The service can observe pause or a failed read between metric samples. It clears
        // pending work immediately; the gate need not receive a separate paused sample.
        announcements.clearPending()
        assertNull(announcements.update(gpsAt(1_620.0), true, true, 11_000))
        assertTrue(announcements.update(gpsAt(3_220.0), true, true, 600_000)!!.startsWith("2 miles completed."))
        announcements.clearPending()
        assertNull(announcements.update(gpsAt(3_225.0), true, true, 601_000))
    }

    @Test fun `distance cap backward distance and invalid clock discard pending crossings`() {
        for ((meters, elapsed) in listOf(1_775.0 to 11_000L, 1_609.5 to 11_000L, 1_620.0 to 9_000L)) {
            val announcements = RunMilestoneAnnouncements()
            assertNull(announcements.update(gpsAt(1_610.0, TelemetryCoverage.PARTIAL), true, true, 10_000))
            assertNull(announcements.update(gpsAt(meters), true, true, elapsed))
            val nextDistance = if (meters > 1_770.0) meters + 1 else 1_625.0
            assertNull(announcements.update(gpsAt(nextDistance), true, true, 12_000))
        }
    }

    @Test fun `unknown distance and a switch to fixture data discard pending GPS crossings`() {
        val unavailable = RunMetrics(10_000, null, null, null,
            DistanceUnit.MILES, null, TelemetryCoverage.UNAVAILABLE)
        for (replacement in listOf(null, unavailable, gpsAt(1_615.0).copy(source = MetricSource.FIXTURE))) {
            val announcements = RunMilestoneAnnouncements()
            assertNull(announcements.update(gpsAt(1_610.0, TelemetryCoverage.PARTIAL), true, true, 10_000))
            assertNull(announcements.update(replacement, true, true, 11_000))
            assertNull(announcements.update(gpsAt(1_620.0), true, true, 12_000))
        }
    }

    @Test fun `recovery seed and distance jumps suppress old milestones`() {
        val recovered = RunMilestoneAnnouncements(2_000.0)
        assertNull(recovered.update(gpsAt(2_001.0), true, true))
        assertTrue(recovered.update(gpsAt(3_220.0), true, true)!!.startsWith("2 miles completed."))
        val delayed = RunMilestoneAnnouncements()
        assertNull(delayed.update(gpsAt(3_300.0), true, true))
        assertNull(delayed.update(gpsAt(3_310.0), true, true))
        assertTrue(delayed.update(gpsAt(4_830.0), true, true)!!.startsWith("3 miles completed."))
        val stale = RunMilestoneAnnouncements()
        assertNull(stale.update(gpsAt(1_900.0), true, true))
        assertNull(stale.update(gpsAt(1_610.0), true, true))
    }

    @Test fun `fixture and unavailable evidence produce no live GPS update`() {
        assertNull(RunMilestoneAnnouncements().update(gpsAt(1_610.0).copy(source = MetricSource.FIXTURE), true, true))
        assertNull(RunMilestoneAnnouncements().update(RunMetrics(1_000, null, null, null,
            DistanceUnit.MILES, null, TelemetryCoverage.UNAVAILABLE), true, true))
        assertEquals("1 mile completed.", RunMilestoneAnnouncements().update(
            RunMetrics(600_000, null, 1_609.344, null, DistanceUnit.MILES, MetricSource.GPS,
                TelemetryCoverage.COMPLETE), true, true))
    }

    @Test
    fun `current mile requires complete GPS timing evidence and a positive finite pace`() {
        val valid = gpsAt(1_800.0).copy(currentMilePaceSecondsPerMeter = 0.3)
        listOf(Double.NaN, Double.POSITIVE_INFINITY, 0.0, -1.0).forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) {
                valid.copy(currentMilePaceSecondsPerMeter = invalid)
            }
        }
        assertThrows(IllegalArgumentException::class.java) { valid.copy(coverage = TelemetryCoverage.PARTIAL) }
        assertThrows(IllegalArgumentException::class.java) { valid.copy(source = MetricSource.FIXTURE) }
        assertThrows(IllegalArgumentException::class.java) {
            valid.copy(activeMillis = null, paceSecondsPerMeter = null)
        }
        assertNull(gpsAt(1_800.0).currentMilePaceSecondsPerMeter)
    }

    @Test
    fun `active duration cannot exceed elapsed duration`() {
        assertThrows(IllegalArgumentException::class.java) {
            completeMetrics(elapsedMillis = 1_000L, activeMillis = 1_001L)
        }
    }

    @Test
    fun `unavailable metrics cannot claim distance`() {
        assertThrows(IllegalArgumentException::class.java) {
            RunMetrics(
                elapsedMillis = 1_000L,
                activeMillis = null,
                distanceMeters = 1.0,
                paceSecondsPerMeter = null,
                displayUnit = DistanceUnit.MILES,
                source = MetricSource.FIXTURE,
                coverage = TelemetryCoverage.UNAVAILABLE
            )
        }
    }

    @Test
    fun `pace requires active duration and distance`() {
        assertThrows(IllegalArgumentException::class.java) {
            RunMetrics(
                elapsedMillis = 1_000L,
                activeMillis = null,
                distanceMeters = null,
                paceSecondsPerMeter = 0.3,
                displayUnit = DistanceUnit.MILES,
                source = MetricSource.FIXTURE,
                coverage = TelemetryCoverage.UNAVAILABLE
            )
        }
    }

    private fun completeMetrics(
        elapsedMillis: Long,
        activeMillis: Long
    ): RunMetrics = RunMetrics(
        elapsedMillis = elapsedMillis,
        activeMillis = activeMillis,
        distanceMeters = 3.0,
        paceSecondsPerMeter = 1.0 / 3.0,
        displayUnit = DistanceUnit.MILES,
        source = MetricSource.FIXTURE,
        coverage = TelemetryCoverage.COMPLETE
    )
}
