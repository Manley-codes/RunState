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

    @Test fun `disabled paused and partial crossings never replay when conditions improve`() {
        listOf(Triple(false, true, TelemetryCoverage.COMPLETE),
            Triple(true, false, TelemetryCoverage.COMPLETE),
            Triple(true, true, TelemetryCoverage.PARTIAL)).forEach { (running, enabled, coverage) ->
            val announcements = RunMilestoneAnnouncements()
            assertNull(announcements.update(gpsAt(1_610.0, coverage), running, enabled))
            assertNull(announcements.update(gpsAt(1_620.0), true, true))
            assertTrue(announcements.update(gpsAt(3_220.0), true, true)!!.startsWith("2 miles completed."))
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
