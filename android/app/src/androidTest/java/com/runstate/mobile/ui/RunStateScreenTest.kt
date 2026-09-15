package com.runstate.mobile.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.runstate.mobile.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checks what each screen shows and offers, the lifecycle-gated countdown, and the hold.
 *
 * [RunStateScreen] and [HoldToStopButton] are rendered directly with plain values, and the
 * countdown is driven through a lifecycle this test owns — no coordinator or database is
 * involved in any of those. One test launches the real [MainActivity] to prove that genuine
 * Activity recreation restarts the countdown; durable storage is proved separately in
 * `RunSessionCoordinatorRoomTest`, not here.
 *
 * The absence assertions matter as much as the presence ones. Offering Cancel while a start
 * may already be stored, or Start over a blocked database, would be the screen contradicting
 * the coordinator.
 */
@RunWith(AndroidJUnit4::class)
class RunStateScreenTest {

    private companion object {
        const val INITIALIZING = "Getting things ready"
        const val READY = "Ready when you are"
        const val COUNTDOWN = "Getting ready to run"
        const val STARTING = "Starting your run — saving its start"
        const val START_FAILED = "RunState couldn't save the start of your run."
        const val RUNNING = "You have a run in progress"
        const val PAUSED = "You have a paused run"
        const val SAVED = "Run saved."
        const val FAILED = "RunState couldn't get ready."

        const val START = "Start"
        const val CANCEL = "Cancel"
        const val TRY_AGAIN = "Try again"
        const val PAUSE = "Pause"
        const val RESUME = "Resume"
        const val START_ANOTHER = "Start another run"

        const val DIGIT_TAG = "countdownDigit"
        const val NO_DIGIT = "none"

        /** Longer than a whole countdown, so "nothing happened" is not merely "not yet". */
        const val LONGER_THAN_COUNTDOWN_MILLIS = 3 * COUNTDOWN_STEP_MILLIS + 500L
    }

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    /** Counts each callback, so a tap can be shown to reach the right one and only it. */
    private class RecordedActions {
        var starts = 0
        var cancels = 0
        var initializationRetries = 0
        var startRetries = 0
        var pauses = 0
        var resumes = 0
        var stops = 0
        var startAnothers = 0

        val total get() = starts + cancels + initializationRetries + startRetries +
            pauses + resumes + stops + startAnothers
    }

    /** Renders one screen and returns the counters its callbacks increment. */
    private fun renderScreen(
        model: RunUiModel,
        metrics: RunMetricsDisplay? = null,
        countdownDigit: Int? = null,
        actionsEnabled: Boolean = true
    ): RecordedActions {
        val actions = RecordedActions()

        composeTestRule.setContent {
            RunStateScreen(
                model = model,
                metrics = metrics,
                countdownDigit = countdownDigit,
                onStart = { actions.starts++ },
                onCancelCountdown = { actions.cancels++ },
                onRetryInitialization = { actions.initializationRetries++ },
                onRetryStart = { actions.startRetries++ },
                onPause = { actions.pauses++ },
                onResume = { actions.resumes++ },
                onStop = { actions.stops++ },
                onStartAnother = { actions.startAnothers++ },
                actionsEnabled = actionsEnabled
            )
        }

        return actions
    }

    private fun renderScreen(state: RunUiState) = renderScreen(RunUiModel(state))

    /** Fails unless none of the action labels, nor the hold control, is on screen. */
    private fun assertNoActionsOffered() {
        listOf(START, CANCEL, TRY_AGAIN, PAUSE, RESUME, START_ANOTHER).forEach {
            composeTestRule.onNodeWithText(it).assertDoesNotExist()
        }
        composeTestRule.onNodeWithTag(HOLD_TO_STOP_TAG).assertDoesNotExist()
    }

    /** Sends a real system back through the Activity's dispatcher. */
    private fun pressBack() {
        composeTestRule.runOnUiThread {
            composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeTestRule.waitForIdle()
    }

    /** True when some screen callback is intercepting back. */
    private fun backIsIntercepted(): Boolean {
        var intercepted = false
        composeTestRule.runOnUiThread {
            intercepted = composeTestRule.activity.onBackPressedDispatcher.hasEnabledCallbacks()
        }
        return intercepted
    }

    // ---------------------------------------------------------------------------------
    // Screens and controls
    // ---------------------------------------------------------------------------------

    /** Proves the waiting screen says so and offers nothing to press. */
    @Test
    fun initializingShowsProgressTextAndNoActions() {
        renderScreen(RunUiState.Initializing)

        composeTestRule.onNodeWithText(INITIALIZING).assertIsDisplayed()
        assertNoActionsOffered()
    }

    /** Proves Ready offers Start, and that Start reports the tap. */
    @Test
    fun readyOffersStartAndReportsTheTap() {
        val actions = renderScreen(RunUiState.ReadyToStart)

        composeTestRule.onNodeWithText(READY).assertIsDisplayed()
        composeTestRule.onNodeWithText(CANCEL).assertDoesNotExist()
        composeTestRule.onNodeWithText(TRY_AGAIN).assertDoesNotExist()

        // Act
        composeTestRule.onNodeWithText(START).performClick()

        // Assert: exactly one Start, and nothing else fired.
        assertEquals(1, actions.starts)
        assertEquals(1, actions.total)
    }

    /** Proves a countdown shows its digit, offers a way out, and never a second Start. */
    @Test
    fun countdownShowsTheDigitAndOffersCancel() {
        val actions = renderScreen(RunUiModel(RunUiState.Countdown), countdownDigit = 2)

        composeTestRule.onNodeWithText(COUNTDOWN).assertIsDisplayed()
        composeTestRule.onNodeWithText("2").assertIsDisplayed()
        composeTestRule.onNodeWithText(START).assertDoesNotExist()

        // Act
        composeTestRule.onNodeWithText(CANCEL).performClick()

        // Assert
        assertEquals(1, actions.cancels)
        assertEquals(1, actions.total)
    }

    /** Proves the system back gesture is the same exit as Cancel during a countdown. */
    @Test
    fun countdownBackActionCancelsTheCountdown() {
        val actions = renderScreen(RunUiState.Countdown)

        // Act: the real dispatcher, not a simulated tap on anything.
        pressBack()

        // Assert
        assertEquals(1, actions.cancels)
        assertEquals(1, actions.total)
    }

    /**
     * Proves a start still writing offers nothing and does not intercept back.
     *
     * The insert may already have landed, so nothing may present itself as a way to undo it.
     */
    @Test
    fun startingRunOffersNoCancelAndNoBackOverride() {
        renderScreen(RunUiState.StartingRun)

        composeTestRule.onNodeWithText(STARTING).assertIsDisplayed()
        assertNoActionsOffered()
        assertFalse(backIsIntercepted())
    }

    /** Proves a failed start offers a retry and a way out, and that back is the way out. */
    @Test
    fun startFailedOffersRetryAndCancelAndBackCancels() {
        val actions = renderScreen(RunUiState.StartFailed)

        composeTestRule.onNodeWithText(START_FAILED).assertIsDisplayed()
        composeTestRule.onNodeWithText(START).assertDoesNotExist()

        // Act and Assert: Try again is the start retry, not the initialization retry.
        composeTestRule.onNodeWithText(TRY_AGAIN).performClick()
        assertEquals(1, actions.startRetries)
        assertEquals(0, actions.initializationRetries)

        // Act and Assert
        composeTestRule.onNodeWithText(CANCEL).performClick()
        assertEquals(1, actions.cancels)

        // Act and Assert
        pressBack()
        assertEquals(2, actions.cancels)
        assertEquals(3, actions.total)
    }

    /** Proves a live run exposes Pause and only Pause. */
    @Test
    fun runningExposesPause() {
        val actions = renderScreen(RunUiState.ActiveRunning)

        composeTestRule.onNodeWithText(RUNNING).assertIsDisplayed()
        composeTestRule.onNodeWithText(RESUME).assertDoesNotExist()
        composeTestRule.onNodeWithTag(HOLD_TO_STOP_TAG).assertDoesNotExist()
        composeTestRule.onNodeWithText(ACTION_FAILED_MESSAGE).assertDoesNotExist()

        // Act
        composeTestRule.onNodeWithText(PAUSE).assertIsEnabled().performClick()

        // Assert
        assertEquals(1, actions.pauses)
        assertEquals(1, actions.total)
    }

    /** Proves a paused run exposes Resume and the hold-to-stop control, and no Pause. */
    @Test
    fun pausedExposesResumeAndStop() {
        val actions = renderScreen(RunUiState.ActivePaused)

        composeTestRule.onNodeWithText(PAUSED).assertIsDisplayed()
        composeTestRule.onNodeWithText(PAUSE).assertDoesNotExist()
        composeTestRule.onNodeWithTag(HOLD_TO_STOP_TAG).assertIsDisplayed().assertIsEnabled()

        // Act
        composeTestRule.onNodeWithText(RESUME).assertIsEnabled().performClick()

        // Assert
        assertEquals(1, actions.resumes)
        assertEquals(1, actions.total)
    }

    /** Proves a pending pause keeps showing Running, with Pause disabled. */
    @Test
    fun pendingPauseDisablesPause() {
        val actions = renderScreen(RunUiModel(RunUiState.ActiveRunning, actionInProgress = true))

        composeTestRule.onNodeWithText(RUNNING).assertIsDisplayed()
        composeTestRule.onNodeWithText(PAUSE).assertIsNotEnabled().performClick()

        assertEquals(0, actions.total)
    }

    /** Proves a pending resume or completion keeps showing Paused, with every control off. */
    @Test
    fun pendingPausedActionDisablesResumeAndStop() {
        composeTestRule.mainClock.autoAdvance = false
        val actions = renderScreen(RunUiModel(RunUiState.ActivePaused, actionInProgress = true))

        composeTestRule.onNodeWithText(PAUSED).assertIsDisplayed()
        composeTestRule.onNodeWithText(RESUME).assertIsNotEnabled().performClick()
        composeTestRule.onNodeWithTag(HOLD_TO_STOP_TAG).assertIsNotEnabled()

        // Act: a full hold on the disabled control.
        composeTestRule.onNodeWithTag(HOLD_TO_STOP_TAG).performTouchInput { down(center) }
        composeTestRule.mainClock.advanceTimeBy(HOLD_TO_STOP_MILLIS + 500L)
        composeTestRule.onNodeWithTag(HOLD_TO_STOP_TAG).performTouchInput { up() }
        composeTestRule.mainClock.advanceTimeBy(100L)

        // Assert
        assertEquals(0, actions.total)
    }

    /** Proves a refused write shows the retry message and leaves the legal controls usable. */
    @Test
    fun actionFailureShowsTheRetryMessageAndReenablesControls() {
        val actions = renderScreen(RunUiModel(RunUiState.ActivePaused, actionFailed = true))

        composeTestRule.onNodeWithText(ACTION_FAILED_MESSAGE).assertIsDisplayed()
        composeTestRule.onNodeWithTag(HOLD_TO_STOP_TAG).assertIsEnabled()

        // Act
        composeTestRule.onNodeWithText(RESUME).assertIsEnabled().performClick()

        // Assert
        assertEquals(1, actions.resumes)
    }

    /** Proves Saved offers another run and leaves back with its ordinary Android meaning. */
    @Test
    fun savedOffersStartAnotherAndKeepsNormalBack() {
        val actions = renderScreen(RunUiState.Saved)

        composeTestRule.onNodeWithText(SAVED).assertIsDisplayed()
        composeTestRule.onNodeWithTag(HOLD_TO_STOP_TAG).assertDoesNotExist()
        assertFalse(backIsIntercepted())

        // Act
        composeTestRule.onNodeWithText(START_ANOTHER).performClick()

        // Assert
        assertEquals(1, actions.startAnothers)
        assertEquals(1, actions.total)
    }

    /** One reusable fixture answer for the three screens that expose run metrics. */
    private fun fixtureMetrics() = RunMetricsDisplay(
        elapsed = "00:02:00",
        active = "00:01:00",
        distance = "0.11 mi",
        averagePace = "8:56 /mi",
        sourceLabel = "Fixture metrics"
    )

    /** Checks the shared metric panel without installing another Compose root. */
    private fun assertFixtureMetricsDisplayed() {
        composeTestRule.onNodeWithText("Fixture metrics").assertIsDisplayed()
        composeTestRule.onNodeWithText("Elapsed").assertIsDisplayed()
        composeTestRule.onNodeWithText("00:02:00").assertIsDisplayed()
        composeTestRule.onNodeWithText("Active").assertIsDisplayed()
        composeTestRule.onNodeWithText("00:01:00").assertIsDisplayed()
        composeTestRule.onNodeWithText("Distance").assertIsDisplayed()
        composeTestRule.onNodeWithText("0.11 mi").assertIsDisplayed()
        composeTestRule.onNodeWithText("Avg pace").assertIsDisplayed()
        composeTestRule.onNodeWithText("8:56 /mi").assertIsDisplayed()
    }

    /** Running discloses and renders fixture metrics. */
    @Test
    fun runningShowsTheValuesAndFixtureDisclosure() {
        renderScreen(model = RunUiModel(RunUiState.ActiveRunning), metrics = fixtureMetrics())
        assertFixtureMetricsDisplayed()
    }

    /** Paused discloses and renders fixture metrics. */
    @Test
    fun pausedShowsTheValuesAndFixtureDisclosure() {
        renderScreen(model = RunUiModel(RunUiState.ActivePaused), metrics = fixtureMetrics())
        assertFixtureMetricsDisplayed()
    }

    /** Saved discloses and renders the final fixture metrics. */
    @Test
    fun savedShowsTheValuesAndFixtureDisclosure() {
        renderScreen(model = RunUiModel(RunUiState.Saved), metrics = fixtureMetrics())
        assertFixtureMetricsDisplayed()
    }

    /** A missing read is named honestly and never rendered as a zero-distance run. */
    @Test
    fun anUnavailableMetricReadShowsNoInventedValues() {
        renderScreen(RunUiState.ActiveRunning)

        composeTestRule.onNodeWithText("Metrics unavailable").assertIsDisplayed()
        composeTestRule.onNodeWithText("0.00 mi").assertDoesNotExist()
        composeTestRule.onNodeWithText("00:00:00").assertDoesNotExist()
    }

    /** Proves the failure screen offers a retry and nothing that would start a run. */
    @Test
    fun initializationFailedOffersRetryAndReportsTheTap() {
        val actions = renderScreen(RunUiState.InitializationFailed)

        composeTestRule.onNodeWithText(FAILED).assertIsDisplayed()
        composeTestRule.onNodeWithText(START).assertDoesNotExist()
        composeTestRule.onNodeWithText(CANCEL).assertDoesNotExist()

        // Act
        composeTestRule.onNodeWithText(TRY_AGAIN).performClick()

        // Assert
        assertEquals(1, actions.initializationRetries)
        assertEquals(1, actions.total)
    }

    /** Proves the blocked screen explains itself without database talk, and offers nothing. */
    @Test
    fun storageInconsistentExplainsTheHoldAndOffersNoActions() {
        renderScreen(RunUiState.StorageInconsistent)

        composeTestRule
            .onNodeWithText("RunState found more than one run that never finished", substring = true)
            .assertIsDisplayed()

        assertNoActionsOffered()
    }

    // ---------------------------------------------------------------------------------
    // Lifecycle-gated countdown
    // ---------------------------------------------------------------------------------

    /** A lifecycle this test drives by hand, starting where an Activity would begin. */
    private class TestLifecycle : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }

    /** What the countdown host observed. */
    private class CountdownRecord {
        val digitsSeen = mutableListOf<Int>()
        var finishes = 0
    }

    /** Hosts the countdown over [owner]'s lifecycle and shows its digit under a tag. */
    private fun renderCountdown(owner: TestLifecycle): CountdownRecord {
        val record = CountdownRecord()

        composeTestRule.setContent {
            val digit = rememberLifecycleCountdown(
                active = true,
                lifecycle = owner.lifecycle,
                onFinished = { record.finishes++ }
            )

            LaunchedEffect(digit) {
                if (digit != null) record.digitsSeen += digit
            }

            Text(
                text = digit?.toString() ?: NO_DIGIT,
                modifier = Modifier.testTag(DIGIT_TAG)
            )
        }

        return record
    }

    private fun moveTo(owner: TestLifecycle, state: Lifecycle.State) {
        composeTestRule.runOnUiThread { owner.registry.currentState = state }
        composeTestRule.waitForIdle()
    }

    /** Metric ticks run only while the Activity-equivalent lifecycle is visible. */
    @Test
    fun metricTicksStopBelowStartedAndRefreshImmediatelyOnReturn() {
        val owner = TestLifecycle()
        moveTo(owner, Lifecycle.State.CREATED)
        val ticks = intArrayOf(0)
        composeTestRule.setContent {
            RunMetricsTicker(active = true, lifecycle = owner.lifecycle) {
                ticks[0]++
            }
        }

        Thread.sleep(METRIC_TICK_MILLIS + 300L)
        composeTestRule.waitForIdle()
        assertEquals(0, ticks[0])

        moveTo(owner, Lifecycle.State.STARTED)
        composeTestRule.waitUntil(METRIC_TICK_MILLIS) { ticks[0] >= 1 }
        composeTestRule.waitUntil(3 * METRIC_TICK_MILLIS) { ticks[0] >= 2 }

        moveTo(owner, Lifecycle.State.CREATED)
        val stoppedAt = ticks[0]
        Thread.sleep(METRIC_TICK_MILLIS + 300L)
        composeTestRule.waitForIdle()
        assertEquals(stoppedAt, ticks[0])

        moveTo(owner, Lifecycle.State.STARTED)
        composeTestRule.waitUntil(METRIC_TICK_MILLIS) { ticks[0] > stoppedAt }
    }

    private fun waitForDigit(text: String, timeoutMillis: Long = 2 * COUNTDOWN_STEP_MILLIS) {
        composeTestRule.waitUntil(timeoutMillis) {
            composeTestRule.onNodeWithTag(DIGIT_TAG)
                .fetchSemanticsNode().config[SemanticsProperties.Text]
                .joinToString { it.text } == text
        }
    }

    /** Proves the countdown visibly shows 3, then 2, then 1, and finishes exactly once. */
    @Test
    fun countdownShowsThreeTwoOneThenFinishesOnce() {
        val owner = TestLifecycle()
        moveTo(owner, Lifecycle.State.RESUMED)
        val record = renderCountdown(owner)

        waitForDigit("3")
        waitForDigit("2")
        waitForDigit("1")
        composeTestRule.waitUntil(2 * COUNTDOWN_STEP_MILLIS) { record.finishes > 0 }

        // Assert: no further finish after more time passes.
        Thread.sleep(LONGER_THAN_COUNTDOWN_MILLIS)
        composeTestRule.waitForIdle()
        assertEquals(listOf(3, 2, 1), record.digitsSeen)
        assertEquals(1, record.finishes)
    }

    /** Proves nothing counts down, and nothing finishes, while the lifecycle is below STARTED. */
    @Test
    fun countdownDoesNotProgressOrFinishBelowStarted() {
        val owner = TestLifecycle()
        moveTo(owner, Lifecycle.State.CREATED)
        val record = renderCountdown(owner)

        // Act: longer than a whole countdown, in the background.
        Thread.sleep(LONGER_THAN_COUNTDOWN_MILLIS)
        composeTestRule.waitForIdle()

        // Assert
        composeTestRule.onNodeWithTag(DIGIT_TAG).assertTextEquals(NO_DIGIT)
        assertTrue(record.digitsSeen.isEmpty())
        assertEquals(0, record.finishes)

        // Act: mid-countdown, the app goes to the background again.
        moveTo(owner, Lifecycle.State.STARTED)
        waitForDigit("2")
        moveTo(owner, Lifecycle.State.CREATED)
        Thread.sleep(LONGER_THAN_COUNTDOWN_MILLIS)
        composeTestRule.waitForIdle()

        // Assert: cleared, and never reached 1 or finished.
        composeTestRule.onNodeWithTag(DIGIT_TAG).assertTextEquals(NO_DIGIT)
        assertEquals(listOf(3, 2), record.digitsSeen)
        assertEquals(0, record.finishes)
    }

    /** Proves returning to STARTED begins again at 3 rather than resuming part-way. */
    @Test
    fun returningToStartedRestartsTheCountdownAtThree() {
        val owner = TestLifecycle()
        moveTo(owner, Lifecycle.State.RESUMED)
        val record = renderCountdown(owner)

        // Act: reach 2, leave, come back.
        waitForDigit("2")
        moveTo(owner, Lifecycle.State.CREATED)
        moveTo(owner, Lifecycle.State.RESUMED)

        // Assert: 3 again, then a full countdown and one finish.
        waitForDigit("3", timeoutMillis = COUNTDOWN_STEP_MILLIS / 2)
        composeTestRule.waitUntil(4 * COUNTDOWN_STEP_MILLIS) { record.finishes > 0 }
        assertEquals(listOf(3, 2, 3, 2, 1), record.digitsSeen)
        assertEquals(1, record.finishes)
    }

    /**
     * Proves a genuine Activity recreation during Countdown restarts it at 3.
     *
     * The real [MainActivity] over the real process coordinator. The countdown is cancelled
     * before it reaches zero, so this test never makes a run official.
     */
    @Test
    fun activityRecreationDuringCountdownRestartsAtThree() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            composeTestRule.waitUntil(10_000) {
                composeTestRule.onAllNodesWithText(READY).fetchSemanticsNodes().isNotEmpty()
            }

            // Act: begin, and wait until the countdown has visibly moved past 3.
            composeTestRule.onNodeWithText(START).performClick()
            waitForText("3")
            waitForText("2")

            scenario.recreate()

            // Assert: the new Activity starts again at 3, well before 1.
            waitForText("3", timeoutMillis = COUNTDOWN_STEP_MILLIS / 2 + 500L)

            // Clean up before zero: nothing official is created.
            composeTestRule.onNodeWithText(CANCEL).performClick()
            waitForText(READY)
        }
    }

    private fun waitForText(text: String, timeoutMillis: Long = 3 * COUNTDOWN_STEP_MILLIS) {
        composeTestRule.waitUntil(timeoutMillis) {
            composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    // ---------------------------------------------------------------------------------
    // Hold to stop
    // ---------------------------------------------------------------------------------

    /** Renders the hold control alone and returns a counter of completed holds. */
    private fun renderHold(enabled: Boolean = true): IntArray {
        val completions = intArrayOf(0)
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            HoldToStopButton(onHoldComplete = { completions[0]++ }, enabled = enabled)
        }
        return completions
    }

    private fun holdProgress(): Float =
        composeTestRule.onNodeWithTag(HOLD_TO_STOP_PROGRESS_TAG)
            .fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current

    /** Proves releasing early fills part-way, then resets and reports nothing. */
    @Test
    fun aShortPressDoesNotStopAndResetsProgress() {
        val completions = renderHold()

        // Act: press for half the hold.
        composeTestRule.onNodeWithTag(HOLD_TO_STOP_TAG).performTouchInput { down(center) }
        composeTestRule.mainClock.advanceTimeBy(HOLD_TO_STOP_MILLIS / 2L)

        // Assert: visibly filling, not finished.
        val midway = holdProgress()
        assertTrue("Expected partial progress, was $midway", midway > 0f && midway < 1f)
        assertEquals(0, completions[0])

        // Act: release, then let well past the hold length go by.
        composeTestRule.onNodeWithTag(HOLD_TO_STOP_TAG).performTouchInput { up() }
        composeTestRule.mainClock.advanceTimeBy(HOLD_TO_STOP_MILLIS * 2L)

        // Assert
        assertEquals(0, completions[0])
        assertEquals(0f, holdProgress())
    }

    /** Proves a cancelled pointer behaves like an early release. */
    @Test
    fun aCancelledPressDoesNotStop() {
        val completions = renderHold()

        composeTestRule.onNodeWithTag(HOLD_TO_STOP_TAG).performTouchInput { down(center) }
        composeTestRule.mainClock.advanceTimeBy(HOLD_TO_STOP_MILLIS / 2L)
        composeTestRule.onNodeWithTag(HOLD_TO_STOP_TAG).performTouchInput { cancel() }
        composeTestRule.mainClock.advanceTimeBy(HOLD_TO_STOP_MILLIS * 2L)

        assertEquals(0, completions[0])
        assertEquals(0f, holdProgress())
    }

    /** Proves a full hold reports exactly once, however long the finger stays down. */
    @Test
    fun aFullHoldStopsExactlyOnce() {
        val completions = renderHold()

        // Act: press and hold past the full length.
        composeTestRule.onNodeWithTag(HOLD_TO_STOP_TAG).performTouchInput { down(center) }
        composeTestRule.mainClock.advanceTimeBy(HOLD_TO_STOP_MILLIS / 2L)
        assertTrue(holdProgress() > 0f)
        composeTestRule.mainClock.advanceTimeBy(HOLD_TO_STOP_MILLIS / 2L + 200L)

        // Assert: one stop the moment the hold completes.
        assertEquals(1, completions[0])

        // Act: keep holding, then release.
        composeTestRule.mainClock.advanceTimeBy(HOLD_TO_STOP_MILLIS * 2L)
        composeTestRule.onNodeWithTag(HOLD_TO_STOP_TAG).performTouchInput { up() }
        composeTestRule.mainClock.advanceTimeBy(100L)

        // Assert: still exactly one.
        assertEquals(1, completions[0])
    }
}
