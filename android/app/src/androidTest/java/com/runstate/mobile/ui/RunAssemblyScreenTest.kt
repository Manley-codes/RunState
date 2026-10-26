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
            onOpenMusicTrack = { actions.linkedTrack = it }
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
    fun disconnectedPlayerDoesNotInventSongControlsOrConnectOnOpen() {
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
    fun musicLayoutUsesActualArtworkAndRecoversWhenThatArtworkIsMissing() {
        compose.mainClock.autoAdvance = false
        // Reuse bundled RunState art as an explicit test cover. No live Spotify playback,
        // remote album download or stored run is involved in these presentation captures.
        val bitmap = BitmapFactory.decodeResource(
            InstrumentationRegistry.getInstrumentation().targetContext.resources, R.drawable.start_music_orb)
        var layout by mutableStateOf(RunMusicPlayerLayout.ALBUM)
        var day by mutableStateOf(false)
        var player by mutableStateOf(SpotifyLivePlayerState(connected = true, title = "Observed song",
            artist = "Observed artist", artwork = bitmap, durationMillis = 180_000, positionMillis = 60_000,
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
        assertEquals(1, actions.nextSongs)
        assertEquals(0, actions.pauses)
        compose.onNodeWithText("Back to distance").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag(ACTIVE_RUN_PLAYER_ACCESSIBLE_TAG).assertDoesNotExist()
    }

    private fun assertWholeInsideViewport(tag: String) {
        assertWholeInsideViewport(compose.onNodeWithTag(tag), tag)
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
