package com.runstate.mobile.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.runstate.mobile.location.GpsSignalStatus
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Standalone presentation fixtures: no database, GPS service, Spotify transport or speech. */
@RunWith(AndroidJUnit4::class)
class RunAssemblyScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val metrics = RunMetricsDisplay(
        elapsed = "00:31:42", active = "00:28:42", distance = "3.24 mi",
        averagePace = "8:51 /mi", sourceLabel = "GPS metrics",
        gpsDistanceAvailable = true, currentMilePace = "8:42 /mi"
    )

    private class Actions {
        var pauses = 0
        var resumes = 0
        var stops = 0
        var cancels = 0
        var startsNow = 0
        var maps = 0
        var music = 0
    }

    @Composable
    private fun Fixture(
        state: RunUiState = RunUiState.ActiveRunning,
        digit: Int? = null,
        day: Boolean = false,
        enabled: Boolean = true,
        failed: Boolean = false,
        gps: Boolean = true,
        values: RunMetricsDisplay? = metrics,
        actions: Actions = Actions(),
        modifier: Modifier = Modifier.fillMaxSize().systemBarsPadding(),
        onPause: () -> Unit = { actions.pauses++ },
        onResume: () -> Unit = { actions.resumes++ }
    ) {
        RunAssemblyScreen(
            state = state, countdownDigit = digit, metrics = values, day = day,
            actionsEnabled = enabled, actionFailed = failed, isGpsRun = gps,
            gpsServicePresent = true, gpsSignalStatus = GpsSignalStatus.RECORDING,
            gpsMessage = null, onPause = onPause, onResume = onResume,
            onStop = { actions.stops++ }, onCancel = { actions.cancels++ },
            onStartNow = { actions.startsNow++ }, onResumeGpsTracking = {},
            onOpenRunMap = { actions.maps++ }, onOpenSpotify = { actions.music++ },
            modifier = modifier, countdownTickKey = digit?.toLong() ?: 0L,
            countdownCadenceActive = digit != null
        )
    }

    @Test
    fun dayAndNightShowRealMetricValuesAndCaptureTheAcceptedLayout() {
        compose.mainClock.autoAdvance = false
        var day by mutableStateOf(false)
        var state by mutableStateOf(RunUiState.Countdown)
        compose.setContent { Fixture(state = state, digit = 10, day = day) }
        for (isDay in listOf(false, true)) {
            compose.runOnIdle { day = isDay; state = RunUiState.Countdown }
            compose.mainClock.advanceTimeBy(400)
            compose.onNodeWithTag(COUNTDOWN_DIGIT_TAG, useUnmergedTree = true).assertTextEquals("10").assertIsDisplayed()
            compose.onNodeWithTag(ACTIVE_RUN_DISTANCE_TAG).assertDoesNotExist()
            save(COUNTDOWN_SCREEN_TAG, "assembly-${if (isDay) "day" else "night"}-countdown.png")
            compose.runOnIdle { state = RunUiState.ActiveRunning }
            compose.mainClock.advanceTimeBy(1_600)
            assertMetric(ACTIVE_RUN_DISTANCE_TAG, metrics.distance.substringBefore(' '))
            compose.onNodeWithContentDescription("Distance, ${metrics.distance}").assertExists()
            assertMetric(ACTIVE_RUN_ACTIVE_TAG, metrics.active)
            assertMetric(ACTIVE_RUN_PACE_TAG, metrics.averagePace)
            assertMetric(ACTIVE_RUN_SPLIT_TAG, metrics.currentMilePace)
            compose.onNodeWithText("Average pace").assertIsDisplayed()
            compose.onNodeWithText("Calories", ignoreCase = true).assertDoesNotExist()
            compose.onNodeWithText(metrics.elapsed).assertDoesNotExist()
            save(ACTIVE_RUN_SCREEN_TAG, "assembly-${if (isDay) "day" else "night"}-active.png")
        }
    }

    @Test
    fun introductionShowsTenAndOnlyAnAdmittedCountdownEnablesStartNow() {
        compose.mainClock.autoAdvance = false
        val actions = Actions()
        var digit by mutableStateOf<Int?>(null)
        compose.setContent { Fixture(RunUiState.Countdown, digit, actions = actions) }
        compose.mainClock.advanceTimeBy(350)
        compose.onNodeWithTag(COUNTDOWN_DIGIT_TAG, useUnmergedTree = true).assertTextEquals("10").assertIsDisplayed()
        compose.onNodeWithTag(COUNTDOWN_START_NOW_TAG).assertIsNotEnabled()
        compose.runOnIdle { digit = 7 }
        compose.mainClock.advanceTimeBy(350)
        compose.onNodeWithTag(COUNTDOWN_DIGIT_TAG, useUnmergedTree = true).assertTextEquals("7")
        compose.onNodeWithTag(COUNTDOWN_START_NOW_TAG).assertIsEnabled().performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(1, actions.startsNow)
        assertEquals(1, actions.cancels)
        assertEquals(0, actions.stops)
    }

    @Test
    fun savingTheStartDoesNotInventAnActiveRunOrOfferCancellation() {
        compose.mainClock.autoAdvance = false
        val actions = Actions()
        compose.setContent { Fixture(RunUiState.StartingRun, actions = actions) }
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("Starting your run", ignoreCase = true).assertIsDisplayed()
        compose.onNodeWithTag(ACTIVE_RUN_DISTANCE_TAG).assertDoesNotExist()
        compose.onNodeWithTag(COUNTDOWN_START_NOW_TAG).assertIsNotEnabled()
        compose.onNodeWithText("Cancel").assertDoesNotExist()
        compose.onNodeWithContentDescription("Pause run").assertDoesNotExist()
        assertEquals(0, actions.startsNow)
        assertEquals(0, actions.stops)
    }

    @Test
    fun activeActionsRemainDistinctAndEndStillRequiresAContinuousHold() {
        compose.mainClock.autoAdvance = false
        val actions = Actions()
        var state by mutableStateOf(RunUiState.ActiveRunning)
        compose.setContent {
            Fixture(state, actions = actions,
                onPause = { actions.pauses++; state = RunUiState.ActivePaused },
                onResume = { actions.resumes++; state = RunUiState.ActiveRunning })
        }
        compose.mainClock.advanceTimeBy(1_600)
        compose.onNodeWithTag(ACTIVE_RUN_MAP_BUTTON_TAG).performClick()
        compose.onNodeWithTag(ACTIVE_RUN_MUSIC_TAG).performClick()
        compose.onNodeWithContentDescription("Pause run").scrollWithClock().performClick()
        compose.mainClock.advanceTimeByFrame()
        assertEquals(1, actions.maps)
        assertEquals(1, actions.music)
        assertEquals(1, actions.pauses)
        compose.onNodeWithContentDescription("Resume run").scrollWithClock().assertIsDisplayed().performClick()
        compose.mainClock.advanceTimeByFrame()
        assertEquals(1, actions.resumes)
        compose.onNodeWithContentDescription("Pause run").scrollWithClock().performClick()
        compose.mainClock.advanceTimeByFrame()
        val hold = compose.onNodeWithTag(HOLD_TO_STOP_TAG).assertIsDisplayed()
        hold.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(HOLD_TO_STOP_MILLIS / 2L)
        hold.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(200)
        assertEquals(0, actions.stops)
        hold.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(HOLD_TO_STOP_MILLIS + 200L)
        hold.performTouchInput { up() }
        compose.mainClock.advanceTimeByFrame()
        assertEquals(1, actions.stops)
    }

    @Test
    fun aPendingWriteDisablesActionsAndAFailedWriteCanBeRetried() {
        compose.mainClock.autoAdvance = false
        var enabled by mutableStateOf(false)
        var failed by mutableStateOf(false)
        val actions = Actions()
        compose.setContent { Fixture(enabled = enabled, failed = failed, actions = actions) }
        compose.mainClock.advanceTimeBy(1_600)
        compose.onNodeWithContentDescription("Pause run").assertIsNotEnabled()
        compose.runOnIdle { enabled = true; failed = true }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText(ACTION_FAILED_MESSAGE).scrollWithClock().assertIsDisplayed()
        compose.onNodeWithContentDescription("Pause run").scrollWithClock().assertIsEnabled().performClick()
        assertEquals(1, actions.pauses)
        assertEquals(0, actions.stops)
    }

    @Test
    fun pausingDuringAssemblySettlesTheFactsAndLeavesResumeAndStopAvailable() {
        compose.mainClock.autoAdvance = false
        var state by mutableStateOf(RunUiState.Countdown)
        val actions = Actions()
        compose.setContent { Fixture(state = state, digit = 1, actions = actions) }
        compose.mainClock.advanceTimeBy(350)
        compose.onNodeWithTag(COUNTDOWN_DIGIT_TAG, useUnmergedTree = true)
            .assertTextEquals("1").assertIsDisplayed()

        compose.runOnIdle { state = RunUiState.ActiveRunning }
        compose.mainClock.advanceTimeBy(400)
        // A notification or coordinator update can pause before the visual entrance finishes.
        compose.runOnIdle { state = RunUiState.ActivePaused }
        compose.mainClock.advanceTimeByFrame()

        assertMetric(ACTIVE_RUN_ACTIVE_TAG, metrics.active)
        compose.onNodeWithContentDescription("Resume run").scrollWithClock().assertIsDisplayed()
            .assertIsEnabled().performClick()
        assertEquals(1, actions.resumes)
        compose.onNodeWithTag(ACTIVE_RUN_DISTANCE_TAG).scrollWithClock()
            .assertTextEquals("3.24").assertIsDisplayed()
        compose.onNodeWithTag(ACTIVE_RUN_PACE_TAG).scrollWithClock()
            .assertTextEquals(metrics.averagePace).assertIsDisplayed()
        compose.onNodeWithTag(ACTIVE_RUN_SPLIT_TAG).scrollWithClock()
            .assertTextEquals(metrics.currentMilePace).assertIsDisplayed()
        compose.onNodeWithTag(HOLD_TO_STOP_TAG).assertIsDisplayed().assertIsEnabled()
        assertEquals(0, actions.stops)
    }

    @Test
    fun aSampleNeverOffersARecordedMapOrInventsAnUnavailableMilePace() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Fixture(gps = false, values = metrics.copy(sourceLabel = "Fixture metrics", currentMilePace = "—"))
        }
        compose.mainClock.advanceTimeBy(1_600)
        compose.onNodeWithTag(ACTIVE_RUN_MAP_BUTTON_TAG).assertDoesNotExist()
        assertMetric(ACTIVE_RUN_SPLIT_TAG, "—")
        compose.onNodeWithText("Calories", ignoreCase = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("Pause run").assertIsEnabled()
    }

    @Test
    fun shortLargeTextKeepsExactValuesAndPauseResumeStopReachable() {
        compose.mainClock.autoAdvance = false
        var state by mutableStateOf(RunUiState.ActiveRunning)
        val longValues = metrics.copy(distance = "1234.56 km", active = "100:59:59",
            averagePace = "123:59 /km", currentMilePace = "123:45 /mi")
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                Fixture(state = state, values = longValues, modifier = Modifier.size(360.dp, 480.dp),
                    onPause = { state = RunUiState.ActivePaused },
                    onResume = { state = RunUiState.ActiveRunning })
            }
        }
        compose.mainClock.advanceTimeBy(1_600)
        compose.onNodeWithContentDescription("Pause run").assertIsDisplayed().performClick()
        compose.mainClock.advanceTimeByFrame()
        // Paused content has less room because the safe stop footer remains visible. Check
        // complete readings in this stricter viewport without an active decorative frame loop.
        for ((tag, text) in listOf(ACTIVE_RUN_DISTANCE_TAG to longValues.distance.substringBefore(' '),
            ACTIVE_RUN_ACTIVE_TAG to longValues.active, ACTIVE_RUN_PACE_TAG to longValues.averagePace,
            ACTIVE_RUN_SPLIT_TAG to longValues.currentMilePace)) {
            val node = compose.onNodeWithTag(tag).scrollWithClock().assertTextEquals(text).assertIsDisplayed()
            val layouts = mutableListOf<TextLayoutResult>()
            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertFalse("Clipped metric $tag", layouts.single().hasVisualOverflow)
        }
        compose.onNodeWithContentDescription("Resume run").scrollWithClock().assertIsDisplayed().performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithContentDescription("Pause run").assertIsDisplayed().performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithContentDescription("Resume run").scrollWithClock().assertIsDisplayed()
        val resumeLayout = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("Resume").performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
            it(resumeLayout)
        }
        assertEquals(1, resumeLayout.single().lineCount)
        assertFalse("Resume label must fit at double text size", resumeLayout.single().hasVisualOverflow)
        compose.onNodeWithTag(HOLD_TO_STOP_TAG).assertIsDisplayed()
        save(ACTIVE_RUN_SCREEN_TAG, "assembly-large-text-paused.png")
    }

    /** Scroll actions need frames even when the presentation's animation clock is frozen. */
    private fun SemanticsNodeInteraction.scrollWithClock(): SemanticsNodeInteraction {
        // Already visible controls need no scroll or clock change. Auto-advancing an active
        // visualizer would otherwise ask the test scheduler to finish an endless decoration.
        if (isDisplayed()) return this
        val previous = compose.mainClock.autoAdvance
        try {
            compose.mainClock.autoAdvance = true
            return performScrollTo()
        } finally {
            compose.mainClock.autoAdvance = previous
        }
    }

    private fun assertMetric(tag: String, text: String) {
        compose.onNodeWithTag(tag).assertTextEquals(text).assertIsDisplayed()
    }

    private fun save(tag: String, name: String) {
        val bitmap = compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
        File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, name).outputStream().use {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }
}
