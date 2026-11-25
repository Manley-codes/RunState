package com.runstate.mobile.run

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RunGoalTest {

    @Test
    fun acceptsPositiveDistance() {
        val goal = RunGoal.Distance(5000.0)

        assertEquals(5000.0, goal.meters, 0.0)
    }

    @Test
    fun acceptsPositiveTime() {
        val goal = RunGoal.Time(30 * 60 * 1000L)

        assertEquals(1_800_000L, goal.activeMillis)
    }

    @Test
    fun rejectsInvalidDistances() {
        val invalidDistances = listOf(
            0.0,
            -1.0,
            Double.NaN,
            Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY
        )

        for (meters in invalidDistances) {
            assertThrows(IllegalArgumentException::class.java) {
                RunGoal.Distance(meters)
            }
        }
    }

    @Test
    fun rejectsInvalidTimes() {
        for (millis in listOf(0L, -1L)) {
            assertThrows(IllegalArgumentException::class.java) {
                RunGoal.Time(millis)
            }
        }
    }
}