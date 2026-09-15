package com.runstate.mobile.ui

import com.runstate.mobile.data.local.DistanceUnit
import com.runstate.mobile.data.local.MetricSource
import com.runstate.mobile.data.local.TelemetryCoverage
import com.runstate.mobile.run.RunMetrics
import org.junit.Assert.assertEquals
import org.junit.Test

/** Checks metric text without Compose, a database or an Android device. */
class RunMetricsDisplayTest {

    @Test
    fun `mile metrics use stable duration distance and pace text`() {
        val display = runMetricsDisplayFor(
            RunMetrics(
                elapsedMillis = 3_661_999L,
                activeMillis = 60_000L,
                distanceMeters = 1_609.344,
                paceSecondsPerMeter = 360.0 / 1_609.344,
                displayUnit = DistanceUnit.MILES,
                source = MetricSource.FIXTURE,
                coverage = TelemetryCoverage.COMPLETE
            )
        )

        assertEquals("01:01:01", display.elapsed)
        assertEquals("00:01:00", display.active)
        assertEquals("1.00 mi", display.distance)
        assertEquals("6:00 /mi", display.averagePace)
        assertEquals("Fixture metrics", display.sourceLabel)
    }

    @Test
    fun `kilometer metrics use the stored display unit`() {
        val display = runMetricsDisplayFor(
            RunMetrics(
                elapsedMillis = 300_000L,
                activeMillis = 300_000L,
                distanceMeters = 1_000.0,
                paceSecondsPerMeter = 0.3,
                displayUnit = DistanceUnit.KILOMETERS,
                source = MetricSource.GPS,
                coverage = TelemetryCoverage.COMPLETE
            )
        )

        assertEquals("1.00 km", display.distance)
        assertEquals("5:00 /km", display.averagePace)
        assertEquals("GPS metrics", display.sourceLabel)
    }

    @Test
    fun `unavailable values remain visibly unknown rather than becoming zero`() {
        val display = runMetricsDisplayFor(
            RunMetrics(
                elapsedMillis = 90_000L,
                activeMillis = null,
                distanceMeters = null,
                paceSecondsPerMeter = null,
                displayUnit = DistanceUnit.MILES,
                source = null,
                coverage = TelemetryCoverage.UNAVAILABLE
            )
        )

        assertEquals("00:01:30", display.elapsed)
        assertEquals("—", display.active)
        assertEquals("—", display.distance)
        assertEquals("—", display.averagePace)
        assertEquals("Metrics unavailable", display.sourceLabel)
    }

    @Test
    fun `partial milliseconds are not rounded into time that has not happened`() {
        val display = runMetricsDisplayFor(
            RunMetrics(
                elapsedMillis = 999L,
                activeMillis = 999L,
                distanceMeters = 2.997,
                paceSecondsPerMeter = 1.0 / 3.0,
                displayUnit = DistanceUnit.MILES,
                source = MetricSource.FIXTURE,
                coverage = TelemetryCoverage.COMPLETE
            )
        )

        assertEquals("00:00:00", display.elapsed)
        assertEquals("00:00:00", display.active)
    }
}
