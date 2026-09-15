package com.runstate.mobile.run

import com.runstate.mobile.data.local.DistanceUnit
import com.runstate.mobile.data.local.MetricSource
import com.runstate.mobile.data.local.TelemetryCoverage
import org.junit.Assert.assertThrows
import org.junit.Test

/** Guards the small coordinator-to-screen metric contract. */
class RunMetricsTest {

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
