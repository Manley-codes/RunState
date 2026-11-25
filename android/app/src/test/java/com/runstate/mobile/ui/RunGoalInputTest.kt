package com.runstate.mobile.ui

import com.runstate.mobile.data.local.DistanceUnit
import com.runstate.mobile.run.RunGoal
import org.junit.Assert.*
import org.junit.Test

class RunGoalInputTest {
    @Test
    fun distanceInputConvertsMilesAndKilometersIncludingDecimalComma() {
        assertEquals(RunGoal.Distance(3_218.688), runGoalFromInput("2", true, DistanceUnit.MILES))
        assertEquals(RunGoal.Distance(5_000.0), runGoalFromInput("5", true, DistanceUnit.KILOMETERS))
        assertEquals(RunGoal.Distance(2_500.0), runGoalFromInput(" 2,5 ", true, DistanceUnit.KILOMETERS))
        assertEquals("2", runGoalInput(RunGoal.Distance(3_218.688), DistanceUnit.MILES))
    }

    @Test
    fun partialNonFiniteNegativeAndOverLimitDistancesAreRejected() {
        listOf("", ".", "0", "-5", "NaN", "Infinity", "1e309", "1.2.3", "1000.001")
            .forEach { input -> assertNull(input, runGoalFromInput(input, true, DistanceUnit.KILOMETERS)) }
        assertEquals(RunGoal.Distance(MAX_RUN_GOAL_METERS),
            runGoalFromInput("1000", true, DistanceUnit.KILOMETERS))
    }

    @Test
    fun activeMinutesConvertToMillisWithoutOverflowOrZeroRounding() {
        assertEquals(RunGoal.Time(1_830_000L), runGoalFromInput("30.5", false, DistanceUnit.MILES))
        assertEquals(RunGoal.Time(604_800_000L), runGoalFromInput("10080", false, DistanceUnit.MILES))
        listOf("0", "-1", "NaN", "10080.001", "999999999999999999999999", "0.000000001")
            .forEach { input -> assertNull(input, runGoalFromInput(input, false, DistanceUnit.MILES)) }
    }
}
