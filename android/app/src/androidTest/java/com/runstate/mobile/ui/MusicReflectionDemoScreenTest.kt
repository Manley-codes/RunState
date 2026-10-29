package com.runstate.mobile.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.runstate.mobile.data.local.ReflectionBranch
import com.runstate.mobile.music.SpotifyLivePlayerState
import com.runstate.mobile.ui.theme.RunStateTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Actual screens with an inert audio boundary: no app database, services, network or playback. */
@RunWith(AndroidJUnit4::class)
class MusicReflectionDemoScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private class Audio : MusicReflectionDemoAudio {
        override val player = MutableStateFlow(SpotifyLivePlayerState(
            connected = true, title = "Good as Hell", artist = "Lizzo",
            album = "Cuz I Love You (Super Deluxe)", paused = true,
            canSkipPrevious = true, canSkipNext = true, canTogglePlayback = true
        ))
        override val state = MutableStateFlow(MusicReflectionDemoAudioState())
        var preparations = 0
        var starts = 0
        var restarts = 0
        var toggles = 0
        var finishes = 0
        var stops = 0
        var retries = 0
        var closes = 0
        val answers = mutableListOf<ReflectionBranch>()
        val holds = mutableListOf<Boolean>()
        override fun prepare() { preparations++ }
        override fun startMusic() { starts++ }
        override fun toggleMusic() { toggles++ }
        override fun restartMusic() { restarts++ }
        override fun finishRun() {
            finishes++
            state.value = MusicReflectionDemoAudioState(reflection = RunCompleteReflectionDisplay.Preparing)
        }
        override fun selectEnergy(branch: ReflectionBranch) {
            answers += branch
            state.value = MusicReflectionDemoAudioState(
                reflection = RunCompleteReflectionDisplay.Ready("Prepared demo reflection."))
        }
        override fun setPresentationHeld(held: Boolean) { holds += held }
        override fun stop() { stops++ }
        override fun retry() { retries++; state.value = state.value.copy(error = null) }
        override fun close() { closes++ }
    }

    private fun show(audio: MusicReflectionDemoAudio, onExit: () -> Unit = {}) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            RunStateTheme {
                MusicReflectionDemoScreen(audio, day = false, onExit = onExit,
                    modifier = Modifier.fillMaxSize().systemBarsPadding())
            }
        }
        compose.mainClock.advanceTimeBy(1_800L)
    }

    /** Explicit listening check only: original hosted voices and Spotify; no run or settings writes. */
    @Test
    fun realMusicReflectionDemoAudition() {
        org.junit.Assume.assumeTrue(androidx.test.platform.app.InstrumentationRegistry
            .getArguments().getString("runstateMusicReflectionDemoCheck") == "true")
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val app = context.applicationContext as com.runstate.mobile.RunStateApplication
        app.initializeReflectionConnection()
        compose.waitUntil(15_000) {
            app.reflectionConnection.value is com.runstate.mobile.ReflectionConnectionState.Hosted
        }
        lateinit var audio: AndroidMusicReflectionDemoAudio
        compose.runOnUiThread {
            // ComponentActivity does not run MainActivity's normal foreground connection hook.
            com.runstate.mobile.music.SpotifyAppRemoteConnection.get(context).onForeground()
            audio = AndroidMusicReflectionDemoAudio(context)
        }
        fun capture(name: String) {
            val bitmap = instrumentation.uiAutomation.takeScreenshot()
            java.io.File(context.cacheDir, "demo-$name.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
        try {
            show(audio)
            capture("distance")
            compose.onNodeWithTag(ACTIVE_RUN_MUSIC_TAG).performClick()
            compose.mainClock.advanceTimeBy(500)
            compose.waitUntil(20_000) { !audio.player.value.paused || audio.state.value.error != null }
            assertEquals(null, audio.state.value.error)
            assertFalse(audio.player.value.paused)
            capture("music")
            completeRun()
            compose.waitUntil(45_000) { !audio.state.value.active }
            assertEquals(null, audio.state.value.error)
            compose.onNodeWithText("POWERED UP").performScrollTo().performClick()
            compose.mainClock.advanceTimeBy(1_900)
            compose.onNodeWithTag(RUN_COMPLETE_EFFORT_RATING_TAG_PREFIX + "7")
                .performScrollTo().performClick()
            compose.mainClock.advanceTimeBy(500)
            compose.waitUntil(60_000) { !audio.state.value.active }
            assertEquals(null, audio.state.value.error)
            assertEquals(RunCompleteReflectionDisplay.Ready(MusicReflectionDemoData.approvedReflection),
                audio.state.value.reflection)
            capture("reflection")
            compose.onNodeWithTag(RUN_COMPLETE_SAVE_ACTIVITY_TAG).performClick()
        } finally {
            compose.runOnUiThread { audio.close() }
        }
    }

    private fun completeRun() {
        compose.onNodeWithContentDescription("Pause run").performClick()
        compose.mainClock.advanceTimeBy(450L)
        // Exercise the existing accessible long-press finish action, not a new demo shortcut.
        compose.onNodeWithTag(HOLD_TO_STOP_TAG)
            .performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        compose.mainClock.advanceTimeBy(100L)
        compose.onNodeWithTag(RUN_COMPLETE_SCREEN_TAG).assertIsDisplayed()
    }

    @Test
    fun distanceRevealsAlbumAndRunPauseDoesNotPauseMusic() {
        val audio = Audio()
        show(audio)
        compose.onNodeWithTag(ACTIVE_RUN_DISTANCE_TAG)
            .assertTextEquals(MusicReflectionDemoData.metrics.distance.substringBefore(' '))
        compose.onNodeWithTag(ACTIVE_RUN_PLAYER_NEXT_TAG).assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, audio.preparations)
            assertEquals(0, audio.starts)
        }
        compose.onNodeWithTag(ACTIVE_RUN_MUSIC_TAG).performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Good as Hell").assertIsDisplayed()
        compose.onNodeWithTag(ACTIVE_RUN_PLAYER_NEXT_TAG).performClick()
        compose.onNodeWithTag(ACTIVE_RUN_PLAYER_PREVIOUS_TAG).performClick()
        compose.onNodeWithTag(ACTIVE_RUN_PLAYER_PLAY_TAG).performClick()
        compose.onNodeWithContentDescription("Pause run").performClick()
        compose.mainClock.advanceTimeBy(450L)
        compose.onNodeWithContentDescription("Resume run").assertIsDisplayed().performClick()
        compose.mainClock.advanceTimeBy(450L)
        compose.onNodeWithTag(ACTIVE_RUN_MUSIC_TAG).performClick()
        compose.onNodeWithTag(ACTIVE_RUN_MUSIC_TAG).performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle {
            assertEquals(1, audio.starts)
            assertEquals(2, audio.restarts)
            assertEquals(1, audio.toggles)
            assertEquals(0, audio.finishes)
            assertEquals(0, audio.stops)
        }
    }

    @Test
    fun normalCompletionIntakeAndOptionalEffortEndWithoutSavingARun() {
        val audio = Audio()
        var exits = 0
        show(audio) { exits++ }
        completeRun()
        compose.onNodeWithTag(RUN_COMPLETE_REFLECTION_TAG).assertDoesNotExist()
        compose.onNodeWithText("Save Activity").assertDoesNotExist()
        compose.onNodeWithText("POWERED UP").performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(200L)
        compose.onNodeWithTag(RUN_COMPLETE_REFLECTION_TEXT_TAG).assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, audio.finishes)
            assertEquals(listOf(ReflectionBranch.POWERED_UP), audio.answers)
            assertTrue(audio.holds.contains(true))
        }
        compose.mainClock.advanceTimeBy(1_800L)
        compose.onNodeWithTag(RUN_COMPLETE_REFLECTION_TEXT_TAG)
            .assertTextEquals("Prepared demo reflection.")
        compose.onNodeWithTag(RUN_COMPLETE_EFFORT_RATING_TAG_PREFIX + "7")
            .performScrollTo().performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag(RUN_COMPLETE_EFFORT_TAG).assertDoesNotExist()
        compose.onNodeWithTag(RUN_COMPLETE_SAVE_ACTIVITY_TAG).assertTextEquals("Done").performClick()
        compose.runOnIdle {
            assertEquals(1, exits)
            assertEquals(1, audio.finishes)
            assertEquals(1, audio.stops)
            assertEquals(0, audio.closes) // The route owner releases the controller.
        }
    }

    @Test
    fun notSureAndSkippedEffortStillRevealTheReflection() {
        val audio = Audio()
        show(audio)
        completeRun()
        compose.onNodeWithText("Not sure").performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(100L)
        compose.onNodeWithTag(RUN_COMPLETE_REFLECTION_TEXT_TAG)
            .assertTextEquals("Prepared demo reflection.")
        compose.onNodeWithText("Skip").performScrollTo().performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag(RUN_COMPLETE_EFFORT_TAG).assertDoesNotExist()
        compose.onNodeWithTag(RUN_COMPLETE_REFLECTION_TEXT_TAG)
            .assertTextEquals("Prepared demo reflection.")
        compose.runOnIdle {
            assertEquals(listOf(ReflectionBranch.NO_SELECTION), audio.answers)
            assertFalse(audio.holds.contains(true))
        }
    }

    @Test
    fun backgroundAndExitStopAudioWithoutClosingTheOwnersController() {
        val audio = Audio()
        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry(this)
            override val lifecycle: Lifecycle get() = registry
        }
        val visible = mutableStateOf(true)
        var exits = 0
        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            if (visible.value) CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                RunStateTheme {
                    MusicReflectionDemoScreen(audio, false, { exits++ },
                        Modifier.fillMaxSize().systemBarsPadding())
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_800L)
        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.CREATED }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { assertTrue(audio.stops > 0) }
        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.runOnIdle {
            assertEquals(1, exits)
            visible.value = false
        }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { assertEquals(0, audio.closes) }
    }

    @Test
    fun audioFailureOffersRetryWithoutRestartingTheRun() {
        val audio = Audio()
        show(audio)
        compose.runOnIdle { audio.state.value = MusicReflectionDemoAudioState(error = "Audio unavailable.") }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag(MUSIC_REFLECTION_DEMO_ERROR_TAG).assertIsDisplayed()
        compose.onNodeWithTag(MUSIC_REFLECTION_DEMO_RETRY_TAG).performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag(MUSIC_REFLECTION_DEMO_ERROR_TAG).assertDoesNotExist()
        compose.onNodeWithTag(ACTIVE_RUN_DISTANCE_TAG).assertExists()
        compose.runOnIdle {
            assertEquals(1, audio.retries)
            assertEquals(0, audio.finishes)
            assertEquals(0, audio.starts)
        }
    }
}
