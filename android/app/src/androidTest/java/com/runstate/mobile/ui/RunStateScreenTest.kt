package com.runstate.mobile.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checks what each of the seven screens shows and which actions it offers.
 *
 * [RunStateScreen] is rendered directly, with a plain [RunUiState] handed to it. No
 * Application, no coordinator and no database is involved, which is only possible because
 * the renderer is stateless — and it is why seven situations that would each need a
 * differently-arranged database can be set up here in one line apiece.
 *
 * The absence assertions matter as much as the presence ones. Offering Start over a
 * blocked database, or over a run that is already going, would be the screen contradicting
 * the coordinator that just refused it.
 */
@RunWith(AndroidJUnit4::class)
class RunStateScreenTest {

    private companion object {
        const val INITIALIZING = "Getting things ready"
        const val READY = "Ready when you are"
        const val COUNTDOWN = "Getting ready to run"
        const val RUNNING = "You have a run in progress"
        const val PAUSED = "You have a paused run"
        const val FAILED = "RunState couldn't get ready."

        const val START = "Start"
        const val CANCEL = "Cancel"
        const val TRY_AGAIN = "Try again"
    }

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    /** Counts each callback, so a tap can be shown to reach the right one and only it. */
    private class RecordedActions {
        var starts = 0
        var cancels = 0
        var retries = 0
    }

    /**
     * Renders one screen and returns the counters its callbacks increment.
     */
    private fun renderScreen(uiState: RunUiState): RecordedActions {
        val actions = RecordedActions()

        composeTestRule.setContent {
            RunStateScreen(
                uiState = uiState,
                onStart = { actions.starts++ },
                onCancelCountdown = { actions.cancels++ },
                onRetry = { actions.retries++ }
            )
        }

        return actions
    }

    /** Fails unless none of the three action labels is on screen. */
    private fun assertNoActionsOffered() {
        composeTestRule.onNodeWithText(START).assertDoesNotExist()
        composeTestRule.onNodeWithText(CANCEL).assertDoesNotExist()
        composeTestRule.onNodeWithText(TRY_AGAIN).assertDoesNotExist()
    }

    /**
     * Proves the waiting screen says so and offers nothing to press.
     */
    @Test
    fun initializingShowsProgressTextAndNoActions() {
        renderScreen(RunUiState.Initializing)

        composeTestRule.onNodeWithText(INITIALIZING).assertIsDisplayed()
        assertNoActionsOffered()
    }

    /**
     * Proves Ready is the one screen that offers Start, and that Start reports the tap.
     */
    @Test
    fun readyOffersStartAndReportsTheTap() {
        val actions = renderScreen(RunUiState.ReadyToStart)

        composeTestRule.onNodeWithText(READY).assertIsDisplayed()
        composeTestRule.onNodeWithText(START).assertIsDisplayed()

        // Assert: the other two actions belong to other screens.
        composeTestRule.onNodeWithText(CANCEL).assertDoesNotExist()
        composeTestRule.onNodeWithText(TRY_AGAIN).assertDoesNotExist()

        // Act
        composeTestRule.onNodeWithText(START).performClick()

        // Assert: exactly one Start, and nothing else fired.
        assertEquals(1, actions.starts)
        assertEquals(0, actions.cancels)
        assertEquals(0, actions.retries)
    }

    /**
     * Proves a countdown offers a way out and never a second Start.
     */
    @Test
    fun countdownOffersCancelAndReportsTheTap() {
        val actions = renderScreen(RunUiState.Countdown)

        composeTestRule.onNodeWithText(COUNTDOWN).assertIsDisplayed()
        composeTestRule.onNodeWithText(CANCEL).assertIsDisplayed()
        composeTestRule.onNodeWithText(START).assertDoesNotExist()

        // Act
        composeTestRule.onNodeWithText(CANCEL).performClick()

        // Assert
        assertEquals(1, actions.cancels)
        assertEquals(0, actions.starts)
        assertEquals(0, actions.retries)
    }

    /**
     * Proves the system back gesture is the same exit as the Cancel button.
     *
     * Both routes go through one callback, so a runner backing out of a countdown gets the
     * same coordinator call either way and the two cannot drift apart.
     */
    @Test
    fun countdownBackActionCancelsTheCountdown() {
        val actions = renderScreen(RunUiState.Countdown)

        // Act: the real dispatcher, not a simulated tap on anything.
        composeTestRule.runOnUiThread {
            composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeTestRule.waitForIdle()

        // Assert: back cancelled the countdown, and did nothing else.
        assertEquals(1, actions.cancels)
        assertEquals(0, actions.starts)
        assertEquals(0, actions.retries)
    }

    /**
     * Proves a live run says so and exposes no controls in this slice.
     *
     * Pause, resume and finish are not built yet, so offering any of them would be the
     * screen promising something the app cannot do.
     */
    @Test
    fun activeRunningShowsTheRunAndNoActions() {
        renderScreen(RunUiState.ActiveRunning)

        composeTestRule.onNodeWithText(RUNNING).assertIsDisplayed()
        assertNoActionsOffered()
    }

    /**
     * Proves a paused run reads as paused rather than as running.
     */
    @Test
    fun activePausedShowsThePausedRunAndNoActions() {
        renderScreen(RunUiState.ActivePaused)

        composeTestRule.onNodeWithText(PAUSED).assertIsDisplayed()
        assertNoActionsOffered()
    }

    /**
     * Proves the failure screen offers a retry and nothing that would start a run.
     */
    @Test
    fun initializationFailedOffersRetryAndReportsTheTap() {
        val actions = renderScreen(RunUiState.InitializationFailed)

        composeTestRule.onNodeWithText(FAILED).assertIsDisplayed()
        composeTestRule.onNodeWithText(TRY_AGAIN).assertIsDisplayed()

        // Assert: a run must not be startable while RunState does not know what is stored.
        composeTestRule.onNodeWithText(START).assertDoesNotExist()
        composeTestRule.onNodeWithText(CANCEL).assertDoesNotExist()

        // Act
        composeTestRule.onNodeWithText(TRY_AGAIN).performClick()

        // Assert
        assertEquals(1, actions.retries)
        assertEquals(0, actions.starts)
        assertEquals(0, actions.cancels)
    }

    /**
     * Proves the blocked screen explains itself without database talk, and offers nothing.
     *
     * There is deliberately no action here. Nothing the runner could press would resolve
     * two unfinished runs, and a button that appeared to would be worse than none.
     */
    @Test
    fun storageInconsistentExplainsTheHoldAndOffersNoActions() {
        renderScreen(RunUiState.StorageInconsistent)

        composeTestRule
            .onNodeWithText("RunState found more than one run that never finished", substring = true)
            .assertIsDisplayed()

        assertNoActionsOffered()
    }
}
