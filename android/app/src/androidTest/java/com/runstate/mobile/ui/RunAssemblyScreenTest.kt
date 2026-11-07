package com.runstate.mobile.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
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
import com.runstate.mobile.R
import com.runstate.mobile.music.SpotifyLivePlayerState
import com.runstate.mobile.settings.RunMusicPlayerLayout
import com.runstate.mobile.ui.theme.RunStateTheme
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
        var previousSongs = 0
        var nextSongs = 0
        var musicToggles = 0
        var musicConnections = 0
        val musicVisibilityChanges = mutableListOf<Boolean>()
        var linkedTrack: String? = null
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
        onResume: () -> Unit = { actions.resumes++ },
        player: SpotifyLivePlayerState = SpotifyLivePlayerState(),
        playerLayout: RunMusicPlayerLayout = RunMusicPlayerLayout.HEADPHONES
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
            countdownCadenceActive = digit != null,
            musicPlayer = player, musicPlayerLayout = playerLayout,
            onPreviousTrack = { actions.previousSongs++ }, onNextTrack = { actions.nextSongs++ },
            onToggleMusicPlayback = { actions.musicToggles++ },
            onConnectMusic = { actions.musicConnections++ },
            onOpenMusicTrack = { actions.linkedTrack = it },
            onMusicVisibilityChange = { actions.musicVisibilityChanges += it }
        )
    }

    /** Match MainActivity's edge-to-edge Scaffold and RunStateScreen host, without services. */
    @Composable
    private fun WindowFixture(
        state: RunUiState,
        day: Boolean,
        gps: Boolean = true,
        signal: GpsSignalStatus = GpsSignalStatus.RECORDING,
        values: RunMetricsDisplay = metrics,
        service: Boolean = true,
        failed: Boolean = false,
        onPause: () -> Unit = {},
        onResume: () -> Unit = {}
    ) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, 1f)) {
            RunStateTheme {
                Scaffold(Modifier.fillMaxSize()) { innerPadding ->
                    RunStateScreen(
                        model = RunUiModel(state, actionFailed = failed), metrics = values,
                        countdownDigit = if (state == RunUiState.Countdown) 10 else null,
                        onStart = {}, onCancelCountdown = {}, onRetryInitialization = {},
                        onRetryStart = {}, onPause = onPause, onResume = onResume,
                        onStop = {}, onStartAnother = {},
                        modifier = Modifier.padding(innerPadding),
                        isGpsRun = gps, gpsSelected = gps, gpsServicePresent = service,
                        gpsSignalStatus = signal, runAppearanceDay = day,
                        countdownTickKey = 10L, countdownCadenceActive = state == RunUiState.Countdown
                    )
                }
            }
        }
    }

    @Test
    fun realWindowInsetsAndNoticesKeepBothBottomPanelsAndPausedStopFullyVisible() {
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { compose.activity.enableEdgeToEdge() }
        var day by mutableStateOf(false)
        var state by mutableStateOf(RunUiState.ActiveRunning)
        var gps by mutableStateOf(true)
        var signal by mutableStateOf(GpsSignalStatus.RECORDING)
        var values by mutableStateOf(metrics)
        var service by mutableStateOf(true)
        var failed by mutableStateOf(false)
        compose.setContent {
            WindowFixture(state, day, gps, signal, values, service, failed,
                onPause = { state = RunUiState.ActivePaused },
                onResume = { state = RunUiState.ActiveRunning })
        }
        for (isDay in listOf(false, true)) {
            for (notice in listOf("maximum", "partial", "weak", "sample")) {
                compose.runOnIdle {
                    day = isDay
                    state = RunUiState.ActiveRunning
                    gps = notice != "sample"
                    service = notice != "maximum"
                    failed = notice == "maximum"
                    signal = if (notice == "weak") GpsSignalStatus.WEAK else GpsSignalStatus.RECORDING
                    values = when (notice) {
                        "sample" -> metrics.copy(sourceLabel = "Sample metrics", gpsDistanceAvailable = false,
                            currentMilePace = "—")
                        "partial", "maximum" -> metrics.copy(sourceLabel = "GPS metrics · partial",
                            averagePace = "—", currentMilePace = "—")
                        else -> metrics
                    }
                }
                compose.mainClock.advanceTimeBy(1_600)
                val noticeText = when (notice) {
                    "sample" -> "Sample run · simulated distance"
                    "partial", "maximum" -> "Partial GPS coverage · pace unavailable"
                    else -> "GPS signal is limited. Distance may be incomplete."
                }
                if (notice == "maximum") {
                    // A capped notice area may scroll independently; it must not force the
                    // main run controls below their own viewport.
                    compose.onNodeWithText(noticeText).assertExists()
                    compose.onNodeWithText(ACTION_FAILED_MESSAGE).assertIsDisplayed()
                    compose.onNodeWithText("Restart GPS tracking").assertIsDisplayed()
                } else {
                    compose.onNodeWithText(noticeText).assertIsDisplayed()
                }
                assertViewportAtInitialScrollPosition()
                assertWholeInsideViewport(ACTIVE_RUN_SPLIT_PANEL_TAG)
                assertWholeInsideViewport(ACTIVE_RUN_MUSIC_TAG)
                compose.onNodeWithTag(HOLD_TO_STOP_TAG).assertDoesNotExist()

                // Operate the actual circle, not its separate caption. No scrolling is allowed
                // in this normal-size host test: partial visibility is the regression itself.
                compose.onNodeWithContentDescription("Pause run").assertIsDisplayed().performClick()
                compose.mainClock.advanceTimeBy(450)
                compose.onNodeWithContentDescription("Resume run").assertIsDisplayed()
                assertViewportAtInitialScrollPosition()
                assertWholeInsideViewport(ACTIVE_RUN_METRICS_TAG)
                assertWholeInsideViewport(ACTIVE_RUN_SPLIT_PANEL_TAG)
                assertWholeInsideViewport(ACTIVE_RUN_MUSIC_TAG)
                assertWholeInsideViewport(ACTIVE_RUN_FINISH_TAG)
                assertWholeInsideViewport(HOLD_TO_STOP_TAG)
                compose.onNodeWithTag(HOLD_TO_STOP_TAG).assertIsEnabled()
                if (notice == "sample") {
                    save(ACTIVE_RUN_SCREEN_TAG, "viewport-${if (isDay) "day" else "night"}-paused.png")
                }
            }
        }
    }

    @Test
    fun countdownFrameIsCenteredInTheInsetWindowInBothAppearances() {
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { compose.activity.enableEdgeToEdge() }
        var day by mutableStateOf(false)
        compose.setContent { WindowFixture(RunUiState.Countdown, day) }
        for (isDay in listOf(false, true)) {
            compose.runOnIdle { day = isDay }
            compose.mainClock.advanceTimeBy(350)
            val viewport = compose.onNodeWithTag(RUN_ASSEMBLY_VIEWPORT_TAG).getUnclippedBoundsInRoot()
            val frame = compose.onNodeWithTag(COUNTDOWN_FRAME_TAG).getUnclippedBoundsInRoot()
            assertEquals("The tall countdown frame should sit at the usable screen center",
                (viewport.top.value + viewport.bottom.value) / 2f,
                (frame.top.value + frame.bottom.value) / 2f, 4f)
            assertViewportAtInitialScrollPosition()
            assertWholeInsideViewport(COUNTDOWN_FRAME_TAG)
            assertWholeInsideViewport(COUNTDOWN_START_NOW_TAG)
            assertWholeInsideViewport(compose.onNodeWithText("Cancel"), "Cancel")
            val start = compose.onNodeWithTag(COUNTDOWN_START_NOW_TAG).getUnclippedBoundsInRoot()
            val cancel = compose.onNodeWithText("Cancel").getUnclippedBoundsInRoot()
            assertTrue("The separate Start now dial must sit below the countdown frame",
                start.top.value > frame.bottom.value)
            assertTrue("Cancel must sit below the complete Start now control",
                cancel.top.value > start.bottom.value)
            compose.onNodeWithText("Start now").assertIsDisplayed()
            compose.onNodeWithTag(COUNTDOWN_DIGIT_TAG, useUnmergedTree = true)
                .assertTextEquals("10").assertIsDisplayed()
            compose.onNodeWithText("Starting in", ignoreCase = true).assertDoesNotExist()
            compose.onNodeWithText("Tap the number to start now").assertDoesNotExist()
            save(COUNTDOWN_SCREEN_TAG, "viewport-${if (isDay) "day" else "night"}-countdown.png")
            save(COUNTDOWN_SCREEN_TAG, "start-now-${if (isDay) "day" else "night"}-countdown.png")
        }
    }

    @Test
    fun pausedWeakSignalDoesNotReserveAnEmptyNoticeFooter() {
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { compose.activity.enableEdgeToEdge() }
        var signal by mutableStateOf(GpsSignalStatus.RECORDING)
        compose.setContent { WindowFixture(RunUiState.ActivePaused, day = false, signal = signal) }
        compose.mainClock.advanceTimeBy(1_600)
        val healthy = compose.onNodeWithTag(RUN_ASSEMBLY_VIEWPORT_TAG).getUnclippedBoundsInRoot()
        compose.runOnIdle { signal = GpsSignalStatus.WEAK }
        compose.mainClock.advanceTimeByFrame()
        val weak = compose.onNodeWithTag(RUN_ASSEMBLY_VIEWPORT_TAG).getUnclippedBoundsInRoot()
        assertEquals("A suppressed paused signal warning must not consume screen height",
            healthy.bottom.value - healthy.top.value, weak.bottom.value - weak.top.value, 1f)
        compose.onNodeWithText("GPS signal is limited. Distance may be incomplete.").assertDoesNotExist()
        assertViewportAtInitialScrollPosition()
        assertWholeInsideViewport(ACTIVE_RUN_FINISH_TAG)
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
            compose.onNodeWithTag(HOLD_TO_STOP_TAG).assertDoesNotExist()
            compose.runOnIdle { state = RunUiState.ActivePaused }
            compose.mainClock.advanceTimeBy(450)
            compose.onNodeWithTag(HOLD_TO_STOP_TAG).assertIsDisplayed().assertIsEnabled()
            val screen = compose.onNodeWithTag(ACTIVE_RUN_SCREEN_TAG).fetchSemanticsNode().boundsInRoot
            val pace = compose.onNodeWithTag(ACTIVE_RUN_METRICS_TAG).fetchSemanticsNode().boundsInRoot
            val stop = compose.onNodeWithTag(HOLD_TO_STOP_TAG).fetchSemanticsNode().boundsInRoot
            assertEquals(screen.center.x, pace.center.x, 1f)
            assertEquals(screen.center.x, stop.center.x, 1f)
            assertTrue("Pace must leave generous space on each side", pace.width < screen.width * .65f)
            save(ACTIVE_RUN_SCREEN_TAG, "assembly-${if (isDay) "day" else "night"}-paused.png")
        }
    }

    @Test
    fun introductionShowsTenAndOnlyAnAdmittedCountdownEnablesStartNow() {
        compose.mainClock.autoAdvance = false
        val actions = Actions()
        var digit by mutableStateOf<Int?>(null)
        var enabled by mutableStateOf(true)
        compose.setContent { Fixture(RunUiState.Countdown, digit, enabled = enabled, actions = actions) }
        compose.mainClock.advanceTimeBy(350)
        compose.onNodeWithTag(COUNTDOWN_DIGIT_TAG, useUnmergedTree = true).assertTextEquals("10").assertIsDisplayed()
        compose.onNodeWithText("Start now").assertIsDisplayed()
        compose.onNodeWithTag(COUNTDOWN_START_NOW_TAG).assertIsNotEnabled()
            .performTouchInput { down(center); up() }
        assertEquals(0, actions.startsNow)
        compose.runOnIdle { digit = 7 }
        compose.mainClock.advanceTimeBy(350)
        compose.onNodeWithTag(COUNTDOWN_DIGIT_TAG, useUnmergedTree = true).assertTextEquals("7")
        val frame = compose.onNodeWithTag(COUNTDOWN_FRAME_TAG)
        assertFalse("The countdown number/frame is decorative, not a second start control",
            frame.fetchSemanticsNode().config.contains(SemanticsActions.OnClick))
        frame.performTouchInput { down(center); up() }
        compose.mainClock.advanceTimeByFrame()
        assertEquals(0, actions.startsNow)
        compose.runOnIdle { enabled = false }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag(COUNTDOWN_START_NOW_TAG).assertIsNotEnabled()
            .performTouchInput { down(center); up() }
        assertEquals(0, actions.startsNow)
        compose.runOnIdle { enabled = true }
        compose.mainClock.advanceTimeByFrame()
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
        compose.onNodeWithTag(COUNTDOWN_START_NOW_TAG).assertIsDisplayed().assertIsNotEnabled()
            .performTouchInput { down(center); up() }
        compose.onNodeWithText("Cancel").assertDoesNotExist()
        compose.onNodeWithContentDescription("Pause run").assertDoesNotExist()
        assertEquals(0, actions.startsNow)
        assertEquals(0, actions.stops)
    }

    @Test
    fun shortLargeTextCountdownKeepsStartNowAndCancelReachable() {
        compose.mainClock.autoAdvance = false
        val actions = Actions()
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                Fixture(RunUiState.Countdown, digit = 8, actions = actions,
                    modifier = Modifier.size(360.dp, 480.dp))
            }
        }
        compose.mainClock.advanceTimeBy(1_200)
        compose.onNodeWithTag(COUNTDOWN_START_NOW_TAG).scrollWithClock()
            .assertIsDisplayed().assertIsEnabled()
        assertWholeInsideViewport(COUNTDOWN_START_NOW_TAG)
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("Start now", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        save(COUNTDOWN_SCREEN_TAG, "start-now-short-large-countdown.png")
        val labelLayout = layouts.single()
        assertFalse("The Start now label must retain its full text: size=${labelLayout.size}, " +
            "lines=${labelLayout.lineCount}, widthOverflow=${labelLayout.didOverflowWidth}, " +
            "heightOverflow=${labelLayout.didOverflowHeight}", labelLayout.hasVisualOverflow)
        compose.onNodeWithTag(COUNTDOWN_START_NOW_TAG).performClick()
        assertEquals(1, actions.startsNow)
        compose.onNodeWithText("Cancel").scrollWithClock().assertIsDisplayed()
        assertWholeInsideViewport(compose.onNodeWithText("Cancel"), "Cancel")
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(1, actions.cancels)
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
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag(ACTIVE_RUN_PLAYER_TAG).assertExists()
        compose.onNodeWithTag(ACTIVE_RUN_DISTANCE_TAG).assertDoesNotExist()
        compose.onNodeWithTag(ACTIVE_RUN_PLAYER_CLOSE_TAG).scrollWithClock().performClick()
        compose.onNodeWithContentDescription("Pause run").scrollWithClock().performClick()
        compose.mainClock.advanceTimeByFrame()
        assertEquals(1, actions.maps)
        assertEquals(0, actions.music)
        assertEquals(1, actions.pauses)
        compose.onNodeWithContentDescription("Resume run").scrollWithClock().assertIsDisplayed().performClick()
        compose.mainClock.advanceTimeByFrame()
        assertEquals(1, actions.resumes)
        compose.onNodeWithContentDescription("Pause run").scrollWithClock().performClick()
        compose.mainClock.advanceTimeBy(450)
        val hold = compose.onNodeWithTag(HOLD_TO_STOP_TAG).scrollWithClock().assertIsDisplayed()
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
    fun stopOnlyAppearsAfterPauseAndAFullHoldIsStillRequiredToEndTheRun() {
        compose.mainClock.autoAdvance = false
        val actions = Actions()
        var state by mutableStateOf(RunUiState.ActiveRunning)
        compose.setContent { Fixture(state, actions = actions,
            onPause = { actions.pauses++; state = RunUiState.ActivePaused }) }
        compose.mainClock.advanceTimeBy(1_600)
        compose.onNodeWithTag(ACTIVE_RUN_FINISH_TAG).assertDoesNotExist()
        compose.onNodeWithTag(HOLD_TO_STOP_TAG).assertDoesNotExist()
        compose.onNodeWithContentDescription("Pause run").performClick()
        compose.mainClock.advanceTimeBy(450)
        assertEquals(1, actions.pauses)
        assertEquals(0, actions.stops)
        compose.onNodeWithText("Stop").scrollWithClock().assertIsDisplayed()
        compose.onNodeWithText("Hold to finish run").assertDoesNotExist()
        val hold = compose.onNodeWithTag(HOLD_TO_STOP_TAG).scrollWithClock().assertIsDisplayed()
        hold.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(HOLD_TO_STOP_MILLIS / 2L)
        hold.performTouchInput { up() }
        compose.mainClock.advanceTimeByFrame()
        assertEquals(0, actions.stops)
        hold.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(HOLD_TO_STOP_MILLIS + 200L)
        hold.performTouchInput { up() }
        compose.mainClock.advanceTimeByFrame()
        assertEquals(1, actions.stops)
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
        compose.mainClock.advanceTimeBy(450)

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
        compose.onNodeWithTag(HOLD_TO_STOP_TAG).scrollWithClock().assertIsDisplayed().assertIsEnabled()
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
        compose.mainClock.advanceTimeBy(450)
        // A short viewport may scroll, but the complete readings and Stop remain reachable.
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
        compose.onNodeWithTag(HOLD_TO_STOP_TAG).scrollWithClock().assertIsDisplayed()
        val stopLabel = compose.onNodeWithText("Stop").scrollWithClock().assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val screen = compose.onNodeWithTag(ACTIVE_RUN_SCREEN_TAG).fetchSemanticsNode().boundsInRoot
        assertTrue("The full Stop label must fit above the screen edge", stopLabel.bottom <= screen.bottom)
        save(ACTIVE_RUN_SCREEN_TAG, "assembly-large-text-paused.png")
    }

    @Test
    fun musicControlsUseLiveMetadataAndNeverPauseOrStopTheRun() {
        compose.mainClock.autoAdvance = false
        val actions = Actions()
        var player by mutableStateOf(SpotifyLivePlayerState(connected = true,
            trackUri = "spotify:track:test", title = "Observed song", artist = "Observed artist",
            durationMillis = 180_000, positionMillis = 60_000, paused = true,
            canSkipPrevious = true, canSkipNext = true, canTogglePlayback = true))
        compose.setContent { Fixture(player = player, actions = actions) }
        compose.mainClock.advanceTimeBy(1_600)
        compose.onNodeWithTag(ACTIVE_RUN_MUSIC_TAG).scrollWithClock().performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithContentDescription("Observed song, Observed artist. Open in Spotify")
            .scrollWithClock().assertIsDisplayed().performClick()
        assertEquals("spotify:track:test", actions.linkedTrack)
        compose.onNodeWithTag(ACTIVE_RUN_PLAYER_PREVIOUS_TAG).scrollWithClock().performClick()
        compose.onNodeWithTag(ACTIVE_RUN_PLAYER_NEXT_TAG).performClick()
        compose.onNodeWithTag(ACTIVE_RUN_PLAYER_PLAY_TAG).performClick()
        assertEquals(1, actions.previousSongs)
        assertEquals(1, actions.nextSongs)
        assertEquals(1, actions.musicToggles)
        assertEquals(0, actions.pauses)
        assertEquals(0, actions.resumes)
        assertEquals(0, actions.stops)
        compose.runOnIdle { player = player.copy(title = "Next observed song", canSkipNext = false, busy = true) }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag(ACTIVE_RUN_PLAYER_NEXT_TAG).assertIsNotEnabled()
        compose.onNodeWithTag(ACTIVE_RUN_PLAYER_PREVIOUS_TAG).assertIsNotEnabled()
        compose.onNodeWithTag(ACTIVE_RUN_PLAYER_PLAY_TAG).assertIsNotEnabled()
        compose.onNodeWithTag(ACTIVE_RUN_PLAYER_CLOSE_TAG).scrollWithClock().performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag(ACTIVE_RUN_DISTANCE_TAG).scrollWithClock().assertTextEquals("3.24")
    }

    @Test
    fun musicDistanceCompanionRepositionsOnlyThePaceRowAndRestoresItOnClose() {
        compose.mainClock.autoAdvance = false
        val actions = Actions()
        var day by mutableStateOf(false)
        var state by mutableStateOf(RunUiState.ActiveRunning)
        val artwork = BitmapFactory.decodeResource(
            InstrumentationRegistry.getInstrumentation().targetContext.resources, R.drawable.demo_lizzo_cover)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1f)) {
                Fixture(state = state, day = day, actions = actions,
                    playerLayout = RunMusicPlayerLayout.ALBUM,
                    player = SpotifyLivePlayerState(connected = true, title = "Good as Hell",
                        artist = "Lizzo", artwork = artwork, paused = true,
                        durationMillis = 180_000, positionMillis = 60_000,
                        canSkipPrevious = true, canSkipNext = true, canTogglePlayback = true))
            }
        }
        for (isDay in listOf(false, true)) {
            for (runState in listOf(RunUiState.ActiveRunning, RunUiState.ActivePaused)) {
                compose.runOnIdle { day = isDay; state = runState }
                compose.mainClock.advanceTimeBy(1_600)
                compose.onNodeWithTag(ACTIVE_RUN_DISTANCE_PANEL_TAG).assertDoesNotExist()
                val paceBefore = compose.onNodeWithTag(ACTIVE_RUN_METRICS_TAG).getUnclippedBoundsInRoot()
                val fixedTags = listOf(ACTIVE_RUN_SPLIT_PANEL_TAG, ACTIVE_RUN_MUSIC_TAG) +
                    if (runState == RunUiState.ActivePaused) listOf(HOLD_TO_STOP_TAG) else emptyList()
                val fixedBounds = fixedTags.associateWith {
                    compose.onNodeWithTag(it).getUnclippedBoundsInRoot()
                }
                compose.onNodeWithTag(ACTIVE_RUN_MUSIC_TAG).assertIsDisplayed().performClick()
                compose.mainClock.advanceTimeBy(600)
                compose.onNodeWithTag(ACTIVE_RUN_DISTANCE_TAG).assertDoesNotExist()
                compose.onNodeWithTag(ACTIVE_RUN_MUSIC_DISTANCE_TAG).assertIsDisplayed().assertTextEquals("3.24 mi")
                compose.onNodeWithContentDescription("Distance, 3.24 mi").assertIsDisplayed()
                compose.onNodeWithTag(ACTIVE_RUN_PACE_TAG).assertIsDisplayed().assertTextEquals(metrics.averagePace)
                val paceOpen = compose.onNodeWithTag(ACTIVE_RUN_METRICS_TAG).getUnclippedBoundsInRoot()
                val distance = compose.onNodeWithTag(ACTIVE_RUN_DISTANCE_PANEL_TAG)
                    .assertIsDisplayed().getUnclippedBoundsInRoot()
                assertTrue("Distance must occupy the left side of the row", distance.left < paceBefore.left)
                assertTrue("Average pace must move to the right", paceOpen.left > paceBefore.left)
                assertEquals("Distance must inherit the original large panel width",
                    (paceBefore.right - paceBefore.left).value, (distance.right - distance.left).value, 1f)
                assertTrue("Average pace must use the smaller panel",
                    paceOpen.right - paceOpen.left < distance.right - distance.left)
                assertEquals("Pace must remain on the same row", paceBefore.top.value, paceOpen.top.value, 1f)
                assertEquals("Pace height must remain unchanged",
                    (paceBefore.bottom - paceBefore.top).value, (paceOpen.bottom - paceOpen.top).value, 1f)
                assertTrue("Average pace must stay to the right without overlap", paceOpen.left > distance.right)
                assertEquals(paceOpen.top.value, distance.top.value, 1f)
                assertEquals(paceOpen.bottom.value, distance.bottom.value, 1f)
                assertEquals("The two-panel row must stay centered",
                    (paceBefore.left.value + paceBefore.right.value) / 2f,
                    (distance.left.value + paceOpen.right.value) / 2f, 1f)
                assertWholeInsideViewport(ACTIVE_RUN_DISTANCE_PANEL_TAG)
                assertWholeInsideViewport(ACTIVE_RUN_METRICS_TAG)
                for ((tag, before) in fixedBounds) {
                    assertEquals("Opening music must not move $tag", before,
                        compose.onNodeWithTag(tag).getUnclippedBoundsInRoot())
                }
                save(ACTIVE_RUN_SCREEN_TAG,
                    "assembly-music-distance-${if (isDay) "day" else "night"}-${if (runState == RunUiState.ActivePaused) "paused" else "running"}.png")
                compose.onNodeWithTag(ACTIVE_RUN_PLAYER_CLOSE_TAG).performClick()
                compose.mainClock.advanceTimeBy(600)
                compose.onNodeWithTag(ACTIVE_RUN_DISTANCE_PANEL_TAG).assertDoesNotExist()
                compose.onNodeWithTag(ACTIVE_RUN_MUSIC_DISTANCE_TAG).assertDoesNotExist()
                compose.onNodeWithTag(ACTIVE_RUN_DISTANCE_TAG).assertIsDisplayed().assertTextEquals("3.24")
                assertEquals("Closing music must restore the centered pace panel", paceBefore,
                    compose.onNodeWithTag(ACTIVE_RUN_METRICS_TAG).getUnclippedBoundsInRoot())
                for ((tag, before) in fixedBounds) {
                    assertEquals("Closing music must not move $tag", before,
                        compose.onNodeWithTag(tag).getUnclippedBoundsInRoot())
                }
            }
        }
        assertEquals(0, actions.music)
        assertEquals(0, actions.musicToggles)
        assertEquals(0, actions.musicConnections)
        assertEquals(0, actions.pauses)
        assertEquals(0, actions.resumes)
        assertEquals(0, actions.stops)
        assertEquals(0, actions.startsNow)
    }

    @Test
    fun musicDistanceCompanionUsesLiveValuesAndFitsLongReadings() {
        compose.mainClock.autoAdvance = false
        var values by mutableStateOf<RunMetricsDisplay?>(null)
        var fontScale by mutableStateOf(1.25f)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                Fixture(state = RunUiState.ActivePaused, values = values,
                    player = SpotifyLivePlayerState(connected = true, title = "Observed song", paused = true))
            }
        }
        compose.mainClock.advanceTimeBy(1_600)
        compose.onNodeWithTag(ACTIVE_RUN_MUSIC_TAG).scrollWithClock().performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithTag(ACTIVE_RUN_MUSIC_DISTANCE_TAG).scrollWithClock().assertTextEquals("—")
        compose.onNodeWithContentDescription("Distance, —").assertExists()
        compose.onNodeWithTag(ACTIVE_RUN_PACE_TAG).assertTextEquals("—")
        for (scale in listOf(1.25f, 1.3f)) {
            for ((distance, pace) in listOf("5.22 km" to "8:51 /km", "1234.56 km" to "123:59 /km")) {
                compose.runOnIdle { fontScale = scale; values = metrics.copy(distance = distance, averagePace = pace) }
                compose.mainClock.advanceTimeBy(100)
                val reading = compose.onNodeWithTag(ACTIVE_RUN_MUSIC_DISTANCE_TAG)
                    .scrollWithClock().assertIsDisplayed().assertTextEquals(distance)
                compose.onNodeWithContentDescription("Distance, $distance").assertExists()
                val paceReading = compose.onNodeWithTag(ACTIVE_RUN_PACE_TAG).assertIsDisplayed().assertTextEquals(pace)
                val texts = listOf(
                    reading to ACTIVE_RUN_DISTANCE_PANEL_TAG,
                    compose.onNodeWithText("Distance", useUnmergedTree = true) to ACTIVE_RUN_DISTANCE_PANEL_TAG,
                    paceReading to ACTIVE_RUN_METRICS_TAG,
                    compose.onNodeWithText("Average pace", useUnmergedTree = true) to ACTIVE_RUN_METRICS_TAG)
                for ((text, panelTag) in texts) {
                    val panel = compose.onNodeWithTag(panelTag).getUnclippedBoundsInRoot()
                    text.assertIsDisplayed()
                    val layouts = mutableListOf<TextLayoutResult>()
                    text.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                    val layout = layouts.single()
                    if (layout.hasVisualOverflow) save(ACTIVE_RUN_SCREEN_TAG, "assembly-distance-overflow.png")
                    assertFalse("Companion '${layout.layoutInput.text}' must fit at font scale $scale: " +
                        "size=${layout.size}, constraints=${layout.layoutInput.constraints}, " +
                        "widthOverflow=${layout.didOverflowWidth}, heightOverflow=${layout.didOverflowHeight}",
                        layout.hasVisualOverflow)
                    val bounds = text.getUnclippedBoundsInRoot()
                    assertTrue("'${layout.layoutInput.text}' must remain inside $panelTag at font scale $scale",
                        bounds.left >= panel.left && bounds.right <= panel.right &&
                            bounds.top >= panel.top && bounds.bottom <= panel.bottom)
                }
            }
        }
        compose.onNodeWithTag(ACTIVE_RUN_MUSIC_TAG).scrollWithClock().performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithTag(ACTIVE_RUN_DISTANCE_PANEL_TAG).assertDoesNotExist()
        compose.onNodeWithTag(ACTIVE_RUN_DISTANCE_TAG).scrollWithClock().assertTextEquals("1234.56")
        compose.onNodeWithContentDescription("Distance, 1234.56 km").assertExists()
        compose.onNodeWithTag(ACTIVE_RUN_PACE_TAG).assertTextEquals("123:59 /km")
    }

    @Test
    fun tallMusicArrowsAcceptUpperAndLowerTapsWithoutRunActions() {
        compose.mainClock.autoAdvance = false
        val actions = Actions()
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1f)) {
                Fixture(actions = actions, player = SpotifyLivePlayerState(connected = true,
                    title = "Observed song", artist = "Observed artist", paused = true,
                    canSkipPrevious = true, canSkipNext = true, canTogglePlayback = true))
            }
        }
        compose.mainClock.advanceTimeBy(1_600)
        compose.onNodeWithTag(ACTIVE_RUN_MUSIC_TAG).scrollWithClock().performClick()
        compose.mainClock.advanceTimeByFrame()
        val circle = compose.onNodeWithTag(ACTIVE_RUN_PLAYER_TAG).scrollWithClock().assertIsDisplayed()

        // Tap well above and below the arrow tip, through the parent surface instead of
        // invoking a semantic click. The old small bottom-row targets miss the upper taps.
        circle.tapMusicPoint(75f, 118f)
        assertEquals(1, actions.previousSongs)
        circle.tapMusicPoint(75f, 232f)
        assertEquals(2, actions.previousSongs)
        circle.tapMusicPoint(234f, 118f)
        assertEquals(1, actions.nextSongs)
        circle.tapMusicPoint(234f, 232f)
        assertEquals(2, actions.nextSongs)
        assertEquals(0, actions.musicToggles)
        assertEquals(null, actions.linkedTrack)
        assertEquals(0, actions.pauses)
        assertEquals(0, actions.resumes)
        assertEquals(0, actions.stops)
    }

    @Test
    fun musicHeaderAndMetadataInterceptSideTapsAndPauseSitsBelowTheDetails() {
        compose.mainClock.autoAdvance = false
        val actions = Actions()
        val uri = "spotify:track:observed"
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1f)) {
                Fixture(actions = actions, player = SpotifyLivePlayerState(connected = true,
                    trackUri = uri, title = "Observed song", artist = "Observed artist",
                    durationMillis = 180_000, positionMillis = 60_000, paused = true,
                    canSkipPrevious = true, canSkipNext = true, canTogglePlayback = true))
            }
        }
        compose.mainClock.advanceTimeBy(1_600)
        compose.onNodeWithTag(ACTIVE_RUN_MUSIC_TAG).scrollWithClock().performClick()
        compose.mainClock.advanceTimeByFrame()
        val circle = compose.onNodeWithTag(ACTIVE_RUN_PLAYER_TAG).scrollWithClock().assertIsDisplayed()

        // These visible controls overlap the side targets' bounding rectangles. Real
        // touches must open Spotify, never leak through to the Previous command underneath.
        circle.tapMusicPoint(105f, 80f)
        assertEquals(uri, actions.linkedTrack)
        actions.linkedTrack = null
        circle.tapMusicPoint(105f, 150f)
        assertEquals(uri, actions.linkedTrack)
        assertEquals(0, actions.previousSongs)
        assertEquals(0, actions.nextSongs)

        val metadata = compose.onNodeWithContentDescription("Observed song, Observed artist. Open in Spotify")
            .getUnclippedBoundsInRoot()
        val progress = compose.onNodeWithTag(ACTIVE_RUN_PLAYER_PROGRESS_TAG).getUnclippedBoundsInRoot()
        val playback = compose.onNodeWithTag(ACTIVE_RUN_PLAYER_PLAY_TAG).getUnclippedBoundsInRoot()
        assertTrue("Pause should leave the song details above it", playback.top >= metadata.bottom)
        assertTrue("Pause should not cover the song progress line", playback.top >= progress.bottom)
        compose.onNodeWithTag(ACTIVE_RUN_PLAYER_PLAY_TAG).performTouchInput { down(center); up() }
        assertEquals(1, actions.musicToggles)
        assertEquals(0, actions.pauses)
        assertEquals(0, actions.resumes)
        assertEquals(0, actions.stops)

        // The close control also overlaps the broad right-side rectangle.
        circle.tapMusicPoint(204f, 80f)
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag(ACTIVE_RUN_PLAYER_TAG).assertDoesNotExist()
        compose.onNodeWithTag(ACTIVE_RUN_DISTANCE_TAG).scrollWithClock().assertTextEquals("3.24")
        assertEquals(0, actions.previousSongs)
        assertEquals(0, actions.nextSongs)
    }

    @Test
    fun musicArrowCancellationAndUnavailableCommandsDoNotDispatch() {
        compose.mainClock.autoAdvance = false
        val actions = Actions()
        var player by mutableStateOf(SpotifyLivePlayerState(connected = true,
            title = "Observed song", artist = "Observed artist", paused = true,
            canSkipPrevious = true, canSkipNext = true, canTogglePlayback = true))
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1f)) {
                Fixture(actions = actions, player = player)
            }
        }
        compose.mainClock.advanceTimeBy(1_600)
        compose.onNodeWithTag(ACTIVE_RUN_MUSIC_TAG).scrollWithClock().performClick()
        compose.mainClock.advanceTimeByFrame()
        val circle = compose.onNodeWithTag(ACTIVE_RUN_PLAYER_TAG).scrollWithClock().assertIsDisplayed()

        circle.performTouchInput {
            val unit = width / 309f
            down(Offset(234f * unit, 154f * unit))
            cancel()
        }
        circle.performTouchInput {
            val unit = width / 309f
            down(Offset(234f * unit, 154f * unit))
            moveTo(Offset(154f * unit, 154f * unit))
            up()
        }
        assertEquals(0, actions.nextSongs)

        compose.runOnIdle { player = player.copy(canSkipNext = false, canTogglePlayback = false) }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag(ACTIVE_RUN_PLAYER_NEXT_TAG).assertIsNotEnabled()
        compose.onNodeWithTag(ACTIVE_RUN_PLAYER_PLAY_TAG).assertIsNotEnabled()
        compose.onNodeWithTag(ACTIVE_RUN_PLAYER_PREVIOUS_TAG).assertIsEnabled()
        circle.tapMusicPoint(234f, 118f)
        compose.onNodeWithTag(ACTIVE_RUN_PLAYER_PLAY_TAG).performTouchInput { down(center); up() }
        circle.tapMusicPoint(75f, 118f)
        assertEquals(0, actions.nextSongs)
        assertEquals(0, actions.musicToggles)
        assertEquals(1, actions.previousSongs)

        compose.runOnIdle { player = player.copy(canSkipNext = true, canTogglePlayback = true, busy = true) }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag(ACTIVE_RUN_PLAYER_PREVIOUS_TAG).assertIsNotEnabled()
        compose.onNodeWithTag(ACTIVE_RUN_PLAYER_NEXT_TAG).assertIsNotEnabled()
        compose.onNodeWithTag(ACTIVE_RUN_PLAYER_PLAY_TAG).assertIsNotEnabled()
        circle.tapMusicPoint(75f, 232f)
        circle.tapMusicPoint(234f, 232f)
        compose.onNodeWithTag(ACTIVE_RUN_PLAYER_PLAY_TAG).performTouchInput { down(center); up() }
        assertEquals(1, actions.previousSongs)
        assertEquals(0, actions.nextSongs)
        assertEquals(0, actions.musicToggles)
        assertEquals(0, actions.pauses)
        assertEquals(0, actions.stops)
    }

    @Test
    fun disconnectedPlayerDoesNotInventSongControlsOrAuthorizeOnOpen() {
        compose.mainClock.autoAdvance = false
        val actions = Actions()
        var player by mutableStateOf(SpotifyLivePlayerState())
        compose.setContent { Fixture(actions = actions, player = player) }
        compose.mainClock.advanceTimeBy(1_600)
        compose.onNodeWithTag(ACTIVE_RUN_MUSIC_TAG).scrollWithClock().performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Spotify not connected").scrollWithClock().assertIsDisplayed()
        compose.onNodeWithTag(ACTIVE_RUN_PLAYER_NEXT_TAG).assertDoesNotExist()
        compose.onNodeWithTag(ACTIVE_RUN_PLAYER_PLAY_TAG).assertDoesNotExist()
        assertEquals(0, actions.musicConnections)
        compose.onNodeWithText("Connect Spotify").scrollWithClock().performClick()
        compose.onNodeWithText("Open Spotify").performClick()
        assertEquals(1, actions.musicConnections)
        assertEquals(1, actions.music)
        assertEquals(0, actions.pauses)
        assertEquals(0, actions.stops)
        compose.runOnIdle { player = player.copy(connecting = true, canConnect = false,
            unavailableMessage = "Connecting to Spotify…") }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Connecting to Spotify…").assertIsDisplayed()
        compose.onNodeWithText("Connect Spotify").assertIsNotEnabled()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag(ACTIVE_RUN_PLAYER_TAG).assertDoesNotExist()
        compose.onNodeWithTag(ACTIVE_RUN_DISTANCE_TAG).scrollWithClock().assertIsDisplayed()
    }

    @Test
    fun musicVisibilityRequestsOneRefreshPerOpenWithoutAuthorizationOrPlayback() {
        compose.mainClock.autoAdvance = false
        val actions = Actions()
        var player by mutableStateOf(SpotifyLivePlayerState())
        compose.setContent { Fixture(actions = actions, player = player) }
        compose.mainClock.advanceTimeBy(1_600)
        assertEquals(listOf(false), actions.musicVisibilityChanges)

        compose.onNodeWithTag(ACTIVE_RUN_MUSIC_TAG).scrollWithClock().performClick()
        compose.mainClock.advanceTimeByFrame()
        assertEquals(listOf(false, true), actions.musicVisibilityChanges)

        // A connection result recomposes the open player without requesting another retry.
        compose.runOnIdle {
            player = player.copy(connecting = true, canConnect = false,
                unavailableMessage = "Connecting to Spotify…")
        }
        compose.mainClock.advanceTimeByFrame()
        assertEquals(listOf(false, true), actions.musicVisibilityChanges)

        compose.onNodeWithTag(ACTIVE_RUN_PLAYER_CLOSE_TAG).scrollWithClock().performClick()
        compose.mainClock.advanceTimeByFrame()
        assertEquals(listOf(false, true, false), actions.musicVisibilityChanges)

        compose.onNodeWithTag(ACTIVE_RUN_MUSIC_TAG).scrollWithClock().performClick()
        compose.mainClock.advanceTimeByFrame()
        assertEquals(listOf(false, true, false, true), actions.musicVisibilityChanges)
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.mainClock.advanceTimeByFrame()
        assertEquals(listOf(false, true, false, true, false), actions.musicVisibilityChanges)

        assertEquals(0, actions.musicConnections)
        assertEquals(0, actions.music)
        assertEquals(0, actions.previousSongs)
        assertEquals(0, actions.nextSongs)
        assertEquals(0, actions.musicToggles)
        assertEquals(0, actions.pauses)
        assertEquals(0, actions.resumes)
        assertEquals(0, actions.stops)
        assertEquals(0, actions.startsNow)
        assertEquals(0, actions.cancels)
    }

    @Test
    fun musicLayoutUsesActualArtworkAndRecoversWhenThatArtworkIsMissing() {
        compose.mainClock.autoAdvance = false
        // Reuse the bundled demo album as an explicit test cover. No live Spotify playback,
        // remote album download or stored run is involved in these presentation captures.
        val bitmap = BitmapFactory.decodeResource(
            InstrumentationRegistry.getInstrumentation().targetContext.resources, R.drawable.demo_lizzo_cover)
        var layout by mutableStateOf(RunMusicPlayerLayout.ALBUM)
        var day by mutableStateOf(false)
        var player by mutableStateOf(SpotifyLivePlayerState(connected = true, title = "Good as Hell",
            artist = "Lizzo", artwork = bitmap, durationMillis = 180_000, positionMillis = 60_000,
            canSkipPrevious = true, canSkipNext = true, canTogglePlayback = true))
        compose.setContent { Fixture(player = player, playerLayout = layout, day = day) }
        compose.mainClock.advanceTimeBy(1_600)
        compose.onNodeWithTag(ACTIVE_RUN_MUSIC_TAG).scrollWithClock().performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithContentDescription("Current song album artwork").scrollWithClock().assertIsDisplayed()
        for (isDay in listOf(false, true)) {
            compose.runOnIdle { day = isDay; layout = RunMusicPlayerLayout.ALBUM }
            compose.mainClock.advanceTimeByFrame()
            save(ACTIVE_RUN_SCREEN_TAG, "assembly-music-album-${if (isDay) "day" else "night"}.png")
            compose.runOnIdle { layout = RunMusicPlayerLayout.HEADPHONES }
            compose.mainClock.advanceTimeByFrame()
            save(ACTIVE_RUN_SCREEN_TAG, "assembly-music-headphones-${if (isDay) "day" else "night"}.png")
        }
        compose.runOnIdle { layout = RunMusicPlayerLayout.ALBUM; player = player.copy(artwork = null) }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithContentDescription("Album artwork unavailable").assertExists()
        compose.runOnIdle { layout = RunMusicPlayerLayout.HEADPHONES }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithContentDescription("RunState headphones artwork").assertExists()
        save(ACTIVE_RUN_SCREEN_TAG, "assembly-music-headphones.png")
    }

    @Test
    fun compactMusicStatusAtIncreasedTextSizeStaysClearAndKeepsBusyControlsDisabled() {
        compose.mainClock.autoAdvance = false
        val actions = Actions()
        val title = "Observed song"
        val message = "Spotify is reconnecting. Music controls will return shortly."
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                ActiveRunMusicPlayer(
                    player = SpotifyLivePlayerState(connected = true, title = title,
                        artist = "Observed artist", durationMillis = 180_000, paused = true,
                        canSkipPrevious = true, canSkipNext = true, canTogglePlayback = true,
                        busy = true, unavailableMessage = message),
                    layout = RunMusicPlayerLayout.HEADPHONES, reducedMotion = true,
                    onClose = {}, onOpenSpotify = { actions.music++ },
                    onConnect = { actions.musicConnections++ }, onOpenTrack = { actions.linkedTrack = it },
                    onPrevious = { actions.previousSongs++ }, onNext = { actions.nextSongs++ },
                    onTogglePlayback = { actions.musicToggles++ },
                    // Exercise the compact circle just above its accessible-dialog cutoff.
                    modifier = Modifier.size(279.dp)
                )
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag(ACTIVE_RUN_PLAYER_ACCESSIBLE_TAG).assertDoesNotExist()
        val status = compose.onNodeWithText(message).assertIsDisplayed()
        val statusBounds = status.getUnclippedBoundsInRoot()
        val titleBounds = compose.onNodeWithText(title, useUnmergedTree = true)
            .assertIsDisplayed().getUnclippedBoundsInRoot()
        val closeBounds = compose.onNodeWithTag(ACTIVE_RUN_PLAYER_CLOSE_TAG).getUnclippedBoundsInRoot()
        assertTrue("Status must clear the header controls", statusBounds.top >= closeBounds.bottom)
        assertTrue("Status must not overlap the song title at increased text size",
            statusBounds.bottom <= titleBounds.top)
        val layouts = mutableListOf<TextLayoutResult>()
        status.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals("A connection status must stay on one line in the compact circle", 1, layouts.single().lineCount)
        for (tag in listOf(ACTIVE_RUN_PLAYER_PREVIOUS_TAG, ACTIVE_RUN_PLAYER_NEXT_TAG, ACTIVE_RUN_PLAYER_PLAY_TAG)) {
            compose.onNodeWithTag(tag).assertIsNotEnabled().performTouchInput { down(center); up() }
        }
        assertEquals(0, actions.previousSongs)
        assertEquals(0, actions.nextSongs)
        assertEquals(0, actions.musicToggles)
        assertEquals(0, actions.musicConnections)
        save(ACTIVE_RUN_PLAYER_TAG, "assembly-music-compact-status.png")
    }

    @Test
    fun largeTextMusicKeepsFullMetadataAndControlsAvailable() {
        compose.mainClock.autoAdvance = false
        val actions = Actions()
        val title = "A long observed song title that should remain fully readable"
        val artist = "The complete observed artist name"
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                Fixture(player = SpotifyLivePlayerState(connected = true, title = title, artist = artist,
                    canTogglePlayback = true, canSkipPrevious = true, canSkipNext = true), actions = actions)
            }
        }
        compose.mainClock.advanceTimeBy(1_600)
        compose.onNodeWithTag(ACTIVE_RUN_MUSIC_TAG).scrollWithClock().performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag(ACTIVE_RUN_PLAYER_ACCESSIBLE_TAG).assertIsDisplayed()
        compose.onNodeWithText(title).scrollWithClock().assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(title).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertFalse(layouts.single().hasVisualOverflow)
        compose.onNodeWithTag(ACTIVE_RUN_PLAYER_NEXT_TAG).scrollWithClock().assertIsDisplayed().performClick()
        compose.onNodeWithTag(ACTIVE_RUN_PLAYER_PREVIOUS_TAG).scrollWithClock().assertIsDisplayed().performClick()
        compose.onNodeWithTag(ACTIVE_RUN_PLAYER_PLAY_TAG).scrollWithClock().assertIsDisplayed().performClick()
        assertEquals(1, actions.nextSongs)
        assertEquals(1, actions.previousSongs)
        assertEquals(1, actions.musicToggles)
        assertEquals(0, actions.pauses)
        assertEquals(0, actions.stops)
        compose.onNodeWithText("Back to distance").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag(ACTIVE_RUN_PLAYER_ACCESSIBLE_TAG).assertDoesNotExist()
    }

    private fun assertWholeInsideViewport(tag: String) {
        assertWholeInsideViewport(compose.onNodeWithTag(tag), tag)
    }

    /** Reference coordinates identify the user-visible upper/lower portions of the circle. */
    private fun SemanticsNodeInteraction.tapMusicPoint(x: Float, y: Float) {
        performTouchInput {
            val unit = width / 309f
            down(Offset(x * unit, y * unit))
            up()
        }
    }

    private fun assertWholeInsideViewport(interaction: SemanticsNodeInteraction, description: String) {
        val viewport = compose.onNodeWithTag(RUN_ASSEMBLY_VIEWPORT_TAG).getUnclippedBoundsInRoot()
        val node = interaction.assertIsDisplayed().getUnclippedBoundsInRoot()
        // Clipped semantics bounds could make a cut-off panel appear to pass. Compare the
        // actual full layout rectangle against the viewport instead, with one dp for rounding.
        assertTrue("$description must have a measurable full layout",
            node.right.value > node.left.value && node.bottom.value > node.top.value)
        assertTrue("$description extends above the viewport", node.top.value >= viewport.top.value - 1f)
        assertTrue("$description extends below the viewport", node.bottom.value <= viewport.bottom.value + 1f)
        assertTrue("$description extends left of the viewport", node.left.value >= viewport.left.value - 1f)
        assertTrue("$description extends right of the viewport", node.right.value <= viewport.right.value + 1f)
    }

    private fun assertViewportAtInitialScrollPosition() {
        val range = compose.onNode(
            hasScrollAction() and hasAnyAncestor(hasTestTag(RUN_ASSEMBLY_VIEWPORT_TAG)),
            useUnmergedTree = true
        ).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertEquals("Normal-size content should be visible without scrolling", 0f, range.value(), .5f)
        assertEquals("Normal-size content should fit the viewport", 0f, range.maxValue(), .5f)
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
