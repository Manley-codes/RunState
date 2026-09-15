package com.runstate.mobile.run

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Checks that controlled fixture distance is pure, deterministic and bounded. */
class FixtureDistanceCalculatorTest {

    @Test
    fun `zero active duration produces zero distance`() {
        assertEquals(0.0, FixtureDistanceCalculator().distanceMetersAt(0L), 0.0)
    }

    @Test
    fun `known active duration produces deterministic distance`() {
        assertEquals(30.0, FixtureDistanceCalculator().distanceMetersAt(10_000L), 0.0)
    }

    @Test
    fun `repeating the same active duration freezes distance`() {
        val calculator = FixtureDistanceCalculator()
        val first = calculator.distanceMetersAt(10_000L)
        val paused = calculator.distanceMetersAt(10_000L)

        assertEquals(first, paused, 0.0)
    }

    @Test
    fun `larger active duration continues from the same calculation`() {
        val calculator = FixtureDistanceCalculator()

        assertEquals(30.0, calculator.distanceMetersAt(10_000L), 0.0)
        assertEquals(45.0, calculator.distanceMetersAt(15_000L), 0.0)
    }

    @Test
    fun `two calculators with the same rate agree`() {
        assertEquals(
            FixtureDistanceCalculator().distanceMetersAt(12_345L),
            FixtureDistanceCalculator().distanceMetersAt(12_345L),
            0.0
        )
    }

    @Test
    fun `custom valid rate is used`() {
        assertEquals(
            25.0,
            FixtureDistanceCalculator(metersPerSecond = 2.5).distanceMetersAt(10_000L),
            0.0
        )
    }

    @Test
    fun `negative active duration is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            FixtureDistanceCalculator().distanceMetersAt(-1L)
        }
    }

    @Test
    fun `zero negative and non finite rates are rejected`() {
        listOf(
            0.0,
            -1.0,
            Double.NaN,
            Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY
        ).forEach { invalidRate ->
            assertThrows(IllegalArgumentException::class.java) {
                FixtureDistanceCalculator(metersPerSecond = invalidRate)
            }
        }
    }

    @Test
    fun `distance calculation refuses an infinite result`() {
        val calculator = FixtureDistanceCalculator(metersPerSecond = Double.MAX_VALUE)

        assertThrows(IllegalArgumentException::class.java) {
            calculator.distanceMetersAt(2_000L)
        }
    }
}
