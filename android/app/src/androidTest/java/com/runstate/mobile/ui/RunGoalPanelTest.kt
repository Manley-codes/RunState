package com.runstate.mobile.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.runstate.mobile.data.local.DistanceUnit
import com.runstate.mobile.run.RunGoal
import com.runstate.mobile.ui.theme.RunStateTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic UI only: no recorded runs, stored shoes, or hosted services are touched. */
@RunWith(AndroidJUnit4::class)
class RunGoalPanelTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun goalIsOptionalAndSavingOrRemovingRequiresAnExplicitTap() {
        var selected by mutableStateOf<RunGoal?>(null)
        val changes = mutableListOf<RunGoal?>()
        rule.setContent {
            RunStateTheme(darkTheme = false, dynamicColor = false) {
                RunGoalPanel(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    selectedGoal = selected, onSave = { selected = it; changes += it })
            }
        }
        rule.onNodeWithTag(RUN_GOAL_SAVE_TAG).assertIsNotEnabled()
        rule.onNodeWithTag(RUN_GOAL_REMOVE_TAG).assertDoesNotExist()
        rule.onNodeWithTag(RUN_GOAL_INPUT_TAG).performTextReplacement("2")
        rule.runOnIdle { assertTrue(changes.isEmpty()); assertNull(selected) }
        rule.onNodeWithTag(RUN_GOAL_SAVE_TAG).performScrollTo().assertIsEnabled().performClick()
        rule.runOnIdle {
            assertEquals(RunGoal.Distance(3_218.688), selected)
            assertEquals(1, changes.size)
        }
        rule.onNodeWithTag(RUN_GOAL_REMOVE_TAG).performScrollTo().performClick()
        rule.runOnIdle { assertNull(selected); assertEquals(2, changes.size) }
    }

    @Test
    fun invalidDraftsCannotSaveAndTimeUsesActiveMinutes() {
        val changes = mutableListOf<RunGoal?>()
        rule.setContent {
            RunStateTheme(darkTheme = false, dynamicColor = false) {
                RunGoalPanel(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    onSave = { changes += it })
            }
        }
        listOf("0", "-1", "NaN", ".", "1e309", "999999999999999999999999").forEach { input ->
            rule.onNodeWithTag(RUN_GOAL_INPUT_TAG).performScrollTo().performTextReplacement(input)
            rule.onNodeWithTag(RUN_GOAL_SAVE_TAG).assertIsNotEnabled()
        }
        rule.onNodeWithTag(RUN_GOAL_TIME_TAG).performScrollTo().performClick()
        rule.onNodeWithTag(RUN_GOAL_INPUT_TAG).performTextReplacement("30.5")
        rule.onNodeWithText("Active running time. Pauses do not count.").assertExists()
        rule.onNodeWithText("Your run keeps going until you finish it.").assertExists()
        rule.runOnIdle { assertTrue(changes.isEmpty()) }
        rule.onNodeWithTag(RUN_GOAL_SAVE_TAG).performScrollTo().performClick()
        rule.runOnIdle { assertEquals(listOf(RunGoal.Time(1_830_000L)), changes) }
    }

    @Test
    fun changingUnitsAndSavingAnUnchangedGoalPreservesTheExactTarget() {
        val original = RunGoal.Distance(12_345.678912345)
        var selected by mutableStateOf<RunGoal>(original)
        var saved: RunGoal? = null
        rule.setContent {
            RunStateTheme(darkTheme = false, dynamicColor = false) {
                RunGoalPanel(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    selectedGoal = selected, onSave = { saved = it })
            }
        }
        rule.onNodeWithTag(RUN_GOAL_KILOMETERS_TAG).performClick()
        rule.onNodeWithTag(RUN_GOAL_MILES_TAG).performClick()
        rule.onNodeWithTag(RUN_GOAL_SAVE_TAG).performScrollTo().performClick()
        rule.runOnIdle { assertSame(original, saved) }
        // A maximum-distance target rounded for the miles field must not become invalid.
        val maximum = RunGoal.Distance(MAX_RUN_GOAL_METERS)
        rule.runOnIdle { selected = maximum }
        rule.onNodeWithTag(RUN_GOAL_SAVE_TAG).performScrollTo().assertIsEnabled().performClick()
        rule.runOnIdle { assertSame(maximum, saved) }
    }

    @Test
    fun closingEditedGoalDoesNotSaveOrRemoveIt() {
        val original = RunGoal.Time(1_800_000L)
        var closes = 0
        var writes = 0
        rule.setContent {
            RunStateTheme(darkTheme = false, dynamicColor = false) {
                RunGoalPanel(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    selectedGoal = original, onDismiss = { closes++ }, onSave = { writes++ })
            }
        }
        rule.onNodeWithTag(RUN_GOAL_INPUT_TAG).performTextReplacement("45")
        rule.onNodeWithTag(RUN_GOAL_CLOSE_TAG).performScrollTo().performClick()
        rule.runOnIdle { assertEquals(1, closes); assertEquals(0, writes) }
    }

    @Test
    fun unsavedGoalTypeAndValueSurviveRecreationWithoutSaving() {
        val restoration = StateRestorationTester(rule)
        val changes = mutableListOf<RunGoal?>()
        restoration.setContent {
            RunStateTheme(darkTheme = false, dynamicColor = false) {
                RunGoalPanel(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    onSave = { changes += it })
            }
        }
        rule.onNodeWithTag(RUN_GOAL_TIME_TAG).performClick()
        rule.onNodeWithTag(RUN_GOAL_INPUT_TAG).performTextReplacement("12.5")
        restoration.emulateSavedInstanceStateRestore()
        rule.onNodeWithTag(RUN_GOAL_TIME_TAG).assertIsSelected()
        rule.onNodeWithTag(RUN_GOAL_INPUT_TAG).assertTextContains("12.5")
        rule.runOnIdle { assertTrue(changes.isEmpty()) }
        rule.onNodeWithTag(RUN_GOAL_SAVE_TAG).performScrollTo().performClick()
        rule.runOnIdle { assertEquals(listOf(RunGoal.Time(750_000L)), changes) }
    }

    @Test
    fun allActionsRemainReachableWithLargeTextInAShortPanel() {
        var saves = 0
        var removals = 0
        var closes = 0
        rule.setContent {
            RunStateTheme(darkTheme = false, dynamicColor = false) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 1.6f)) {
                    Box(Modifier.size(360.dp, 420.dp)) {
                        RunGoalPanel(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                            selectedGoal = RunGoal.Distance(5_000.0),
                            onDismiss = { closes++ },
                            onSave = { if (it == null) removals++ else saves++ })
                    }
                }
            }
        }
        rule.onNodeWithTag(RUN_GOAL_SAVE_TAG).performScrollTo().assertIsDisplayed().performClick()
        rule.onNodeWithTag(RUN_GOAL_REMOVE_TAG).performScrollTo().assertIsDisplayed().performClick()
        rule.onNodeWithTag(RUN_GOAL_CLOSE_TAG).performScrollTo().assertIsDisplayed().performClick()
        rule.runOnIdle { assertEquals(1, saves); assertEquals(1, removals); assertEquals(1, closes) }
    }

    @Test
    fun reopeningGoalKeepsTheUnitUsedToEnterIt() {
        var selected by mutableStateOf<RunGoal?>(null)
        var writes = 0
        rule.setContent {
            RunStateTheme(darkTheme = false, dynamicColor = false) {
                StartRunScreen(null, {}, {}, true, {}, false,
                    modifier = Modifier.fillMaxSize().systemBarsPadding(),
                    selectedRunGoal = selected, onRunGoalChanged = { selected = it; writes++ })
            }
        }
        rule.onNodeWithTag(START_RUN_GOAL_TAG).performClick()
        rule.onNodeWithTag(RUN_GOAL_KILOMETERS_TAG).performClick()
        rule.onNodeWithTag(RUN_GOAL_INPUT_TAG).performTextReplacement("5")
        rule.onNodeWithTag(RUN_GOAL_SAVE_TAG).performScrollTo().performClick()
        rule.onNodeWithTag(RUN_GOAL_SHEET_TAG).assertDoesNotExist()
        rule.onNodeWithTag(START_RUN_GOAL_TAG).performClick()
        rule.onNodeWithTag(RUN_GOAL_KILOMETERS_TAG).assertIsSelected()
        rule.onNodeWithTag(RUN_GOAL_INPUT_TAG).assertTextContains("5")
        rule.onNodeWithTag(RUN_GOAL_CLOSE_TAG).performScrollTo().performClick()
        rule.runOnIdle { assertEquals(RunGoal.Distance(5_000.0), selected); assertEquals(1, writes) }
    }

    @Test
    fun distanceAndTimeSheetPreviewsUseTheSelectedGoalWithoutWriting() {
        var selected by mutableStateOf<RunGoal>(RunGoal.Distance(3_218.688))
        var writes = 0
        rule.setContent {
            RunStateTheme(darkTheme = false, dynamicColor = false) {
                RunGoalSheet(selected, DistanceUnit.MILES, true, {}, { writes++ })
            }
        }
        rule.onNodeWithTag(RUN_GOAL_INPUT_TAG).assertTextContains("2")
        savePreview("run-goal-distance.png")
        rule.runOnIdle { selected = RunGoal.Time(1_800_000L) }
        rule.onNodeWithTag(RUN_GOAL_TIME_TAG).assertIsSelected()
        rule.onNodeWithTag(RUN_GOAL_INPUT_TAG).assertTextContains("30")
        savePreview("run-goal-time.png")
        rule.runOnIdle { assertEquals(0, writes) }
    }

    private fun savePreview(name: String) {
        rule.mainClock.advanceTimeBy(1_000L)
        rule.waitForIdle()
        rule.onNodeWithTag(RUN_GOAL_CLOSE_TAG).assertIsDisplayed()
        rule.onNodeWithTag(RUN_GOAL_INPUT_TAG).assertIsDisplayed()
        rule.onNodeWithTag(RUN_GOAL_REMOVE_TAG).assertIsDisplayed()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // Capture the final window, since capturing a translated modal node can crop its bottom.
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot()) {
            "The settled goal sheet screenshot was unavailable."
        }
        File(instrumentation.targetContext.cacheDir, name)
            .outputStream().use {
                assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
            }
    }
}
