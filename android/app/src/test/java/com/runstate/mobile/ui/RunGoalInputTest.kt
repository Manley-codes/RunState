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
    fun hoursAndMinutesConvertToActiveMillisWithoutOverflow() {
        assertEquals(RunGoal.Time(1_800_000L), runGoalFromInput("00:30", false, DistanceUnit.MILES))
        assertEquals(RunGoal.Time(4_500_000L), runGoalFromInput("1:15", false, DistanceUnit.MILES))
        assertEquals(RunGoal.Time(1_830_123L), runGoalFromInput("00:30:30.123", false, DistanceUnit.MILES))
        assertEquals(RunGoal.Time(30_000L), runGoalFromInput("00:00:30", false, DistanceUnit.MILES))
        assertEquals(RunGoal.Time(604_800_000L), runGoalFromInput("168:00", false, DistanceUnit.MILES))
        listOf("", "00:00", "-1:30", "NaN", "30", "30.5", "00:60", "0:3", "168:01", "999999:00",
            "00:00:60", "00:00:01.1234", "168:00:00.001")
            .forEach { input -> assertNull(input, runGoalFromInput(input, false, DistanceUnit.MILES)) }
    }

    @Test
    fun editorClockFormattingDoesNotChangeHistoryMinuteFormatting() {
        val goal = RunGoal.Time(1_830_000L)
        assertEquals("00:30:30", runGoalTimeInput(goal))
        assertEquals("30.5", runGoalInput(goal, DistanceUnit.MILES))
        assertEquals("00:00:30", runGoalTimeInput(RunGoal.Time(30_000L)))
        assertEquals("00:30:30.123", runGoalTimeInput(RunGoal.Time(1_830_123L)))
        assertEquals("02:05", runGoalTimeInput(RunGoal.Time(7_500_000L)))
        assertEquals("168:00", runGoalTimeInput(RunGoal.Time(604_800_000L)))
    }
}
