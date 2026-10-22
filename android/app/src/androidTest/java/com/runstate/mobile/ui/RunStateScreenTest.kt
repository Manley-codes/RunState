package com.runstate.mobile.ui

import android.media.MediaMetadataRetriever
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.swipe
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.runstate.mobile.R
import com.runstate.mobile.RunCompleteSpeechEffect
import com.runstate.mobile.RunCompleteReceiptEffect
import com.runstate.mobile.RunJourneyRoot
import com.runstate.mobile.data.local.RunStateDatabase
import com.runstate.mobile.data.local.StoredRunState
import com.runstate.mobile.location.RunTrackingServiceState
import com.runstate.mobile.location.GpsSignalStatus
import com.runstate.mobile.location.GpsMapEvidence
import com.runstate.mobile.location.GpsMapCoordinate
import com.runstate.mobile.location.GpsMapMarker
import com.runstate.mobile.location.GpsMapMarkerState
import com.runstate.mobile.location.ForegroundGpsPreviewFix
import com.runstate.mobile.location.ForegroundGpsPreviewSource
import com.runstate.mobile.location.GpsPreviewAvailability
import com.runstate.mobile.location.GpsSample
import com.runstate.mobile.run.PreparedRunFactory
import com.runstate.mobile.run.RunActionKind
import com.runstate.mobile.run.RunSessionCoordinator
import com.runstate.mobile.run.RunAdmission
import com.runstate.mobile.data.local.ReflectionBranch
import com.runstate.mobile.data.local.PostRunEffort
import com.runstate.mobile.data.local.PreRunEnergy
import com.runstate.mobile.data.local.MetricSource
import com.runstate.mobile.data.local.RunSplitStatus
import com.runstate.mobile.data.local.TelemetryCoverage
import com.runstate.mobile.reflection.RunReflectionState
import com.runstate.mobile.reflection.ReflectionVoicePreparation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.TestRule
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Point
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Checks what each screen shows and offers, the lifecycle-gated countdown, and the hold.
 *
 * [RunStateScreen] and [HoldToStopButton] are rendered directly with plain values, and the
 * countdown is driven through a lifecycle this test owns. Bounded real-root cases use isolated
 * in-memory Room and fake GPS/service boundaries, never the phone's saved runs or location.
 * Saved-state restoration checks recreate the timer/root composition in isolation; deeper
 * durable storage is proved in `RunSessionCoordinatorRoomTest`.
 *
 * The absence assertions matter as much as the presence ones. Offering Cancel while a start
 * may already be stored, or Start over a blocked database, would be the screen contradicting
 * the coordinator.
 */
@RunWith(AndroidJUnit4::class)
class RunStateScreenTest {

    private companion object {
        const val INITIALIZING = "Getting things ready"
        const val READY = "TO RUN"
        const val COUNTDOWN = "Getting ready to run"
        const val STARTING = "Starting your run — saving its start"
        const val START_FAILED = "RunState couldn't save the start of your run."
        const val RUNNING = "You have a run in progress"
        const val PAUSED = "PAUSED"
        const val SAVED = "RUN SAVED"
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
        const val LONGER_THAN_COUNTDOWN_MILLIS = COUNTDOWN_SECONDS * COUNTDOWN_STEP_MILLIS + 500L
    }

    private lateinit var composeTestRule:
        AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>

    @get:Rule
    val composeRule: TestRule = TestRule { statement, description ->
        // These real-root cases collect Room flows. Compose's default unconfined test
        // dispatcher can resume composition on Room's worker after a snapshot notification.
        // Queue GPS-root continuations for the UI-driven test clock instead; keep Android's
        // main-thread lifecycle checks and the unrelated screen tests' existing scheduler.
        val effectContext = if (description.methodName.orEmpty().startsWith("gpsRoot")) {
            StandardTestDispatcher()
        } else EmptyCoroutineContext
        composeTestRule = createAndroidComposeRule<ComponentActivity>(effectContext)
        composeTestRule.apply(statement, description)
    }

    /** Counts each callback, so a tap can be shown to reach the right one and only it. */
    private class RecordedActions {
        var starts = 0
        var startsNow = 0
        var cancels = 0
        var initializationRetries = 0
        var startRetries = 0
        var pauses = 0
        var resumes = 0
        var stops = 0
        var startAnothers = 0
        var reflectionRetries = 0
        var audioStops = 0
        val energyAnswers = mutableListOf<ReflectionBranch>()
        var historyOpens = 0

        val total get() = starts + startsNow + cancels + initializationRetries + startRetries +
            pauses + resumes + stops + startAnothers + reflectionRetries + audioStops
    }

    /** Controls engine/focus callbacks without producing audio or touching another app's player. */
    private class ControlledSpeechPlatform : ReflectionSpeechPlatform {
        data class Spoken(val id: String, val text: String, val rate: Float)
        data class Instrumental(
            val id: String,
            val done: (String) -> Unit,
            val failed: (String) -> Unit,
            val focusLost: (String) -> Unit
        )
        lateinit var ready: (Boolean) -> Unit
        lateinit var done: (String) -> Unit
        lateinit var failed: (String) -> Unit
        val focusLosses = mutableMapOf<String, (String) -> Unit>()
        val spoken = mutableListOf<Spoken>()
        val instrumentals = mutableListOf<Instrumental>()
        var focusGranted = true
        var speechAccepted = true
        var instrumentalAccepted = true
        var loseFocusDuringInstrumental = false
        var loseFocusDuringRequest = false
        var stopThrows = false
        var focusOwner: String? = null
        var stops = 0
        var instrumentalStops = 0
        var closes = 0

        override fun initialize(onReady: (Boolean) -> Unit, onDone: (String) -> Unit, onError: (String) -> Unit) {
            ready = onReady
            done = onDone
            failed = onError
        }
        override fun requestAudioFocus(utteranceId: String, onFocusLost: (String) -> Unit): Boolean {
            focusLosses[utteranceId] = onFocusLost
            focusOwner = if (focusGranted) utteranceId else null
            if (loseFocusDuringRequest) onFocusLost(utteranceId)
            return focusGranted
        }
        override fun speak(utteranceId: String, text: String, rate: Float): Boolean {
            spoken += Spoken(utteranceId, text, rate)
            return speechAccepted
        }
        override fun stop() {
            stops++
            if (stopThrows) error("Disconnected speech engine")
        }
        override fun playInstrumental(
            utteranceId: String,
            onDone: (String) -> Unit,
            onError: (String) -> Unit,
            onFocusLost: (String) -> Unit
        ): Boolean {
            instrumentals += Instrumental(utteranceId, onDone, onError, onFocusLost)
            if (loseFocusDuringInstrumental) onFocusLost(utteranceId)
            return instrumentalAccepted
        }
        override fun stopInstrumental() { instrumentalStops++ }
        override fun releaseAudioFocus() { focusOwner = null }
        override fun close() { closes++ }
    }

    private class ControlledCompletionSpeaker : ReflectionSpeaker {
        data class AudioRequest(val text: String, val canPlayCue: () -> Boolean, val finished: () -> Unit)
        val requests = mutableListOf<AudioRequest>()
        val preparations = mutableListOf<String>()
        val preparationEvents = mutableListOf<String>()
        var preparedText: String? = null
        var preparationCancellations = 0
        var stops = 0
        var closes = 0
        override fun prepare(text: String) {
            preparations += text
            preparationEvents += "prepare:$text"
            preparedText = text
        }
        override fun cancelPreparation() {
            preparationCancellations++
            preparationEvents += "cancel"
            preparedText = null
        }
        override fun speak(text: String) { error("Completion must choose its explicit audio sequence") }
        override fun speakWithInstrumental(
            text: String, canPlayInstrumental: () -> Boolean, onFinished: () -> Unit
        ) { requests += AudioRequest(text, canPlayInstrumental, onFinished) }
        override fun stop() {
            stops++
            // A real engine can synchronously deliver completion while being stopped.
            requests.lastOrNull()?.finished?.invoke()
        }
        override fun close() { closes++; stop() }
    }

    /** Renders one screen and returns the counters its callbacks increment. */
    private fun renderScreen(
        model: RunUiModel,
        metrics: RunMetricsDisplay? = null,
        countdownDigit: Int? = null,
        actionsEnabled: Boolean = true,
        modifier: Modifier = Modifier,
        fontScale: Float? = null,
        reflection: RunCompleteReflectionDisplay? = RunCompleteReflectionDisplay.Preparing,
        energyEnabled: Boolean = false,
        selectedEnergy: ReflectionBranch? = ReflectionBranch.NO_SELECTION,
        isTestData: Boolean = false,
        showHistory: Boolean = false,
        reflectionAudioActive: Boolean = false
    ): RecordedActions {
        val actions = RecordedActions()

        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(
                    density = density.density,
                    fontScale = fontScale ?: density.fontScale
                )
            ) {
                RunStateScreen(
                    model = model,
                    metrics = metrics,
                    countdownDigit = countdownDigit,
                    onStart = { actions.starts++ },
                    onStartNow = { actions.startsNow++ },
                    onCancelCountdown = { actions.cancels++ },
                    onRetryInitialization = { actions.initializationRetries++ },
                    onRetryStart = { actions.startRetries++ },
                    onPause = { actions.pauses++ },
                    onResume = { actions.resumes++ },
                    onStop = { actions.stops++ },
                    onStartAnother = { actions.startAnothers++ },
                    reflection = reflection,
                    onRetryReflection = { actions.reflectionRetries++ },
                    onSelectEnergy = { actions.energyAnswers += it },
                    energyEnabled = energyEnabled,
                    selectedEnergy = selectedEnergy,
                    isTestData = isTestData,
                    onViewHistory = { actions.historyOpens++ },
                    showHistory = showHistory,
                    reflectionAudioActive = reflectionAudioActive,
                    onStopReflectionAudio = { actions.audioStops++ },
                    modifier = modifier,
                    actionsEnabled = actionsEnabled
                )
            }
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

    @Test
    fun startEnergyIsOptionalAndSelectionRemainsUntilTheStartAction() {
        var selectedAtStart: PreRunEnergy? = PreRunEnergy.HIGH
        var selected by mutableStateOf<PreRunEnergy?>(null)
        val choices = mutableListOf<PreRunEnergy?>()
        var historyOpens = 0
        composeTestRule.setContent {
            StartRunScreen(
                selectedPreRunEnergy = selected,
                onSelectPreRunEnergy = { selected = it; choices += it },
                onStart = { selectedAtStart = selected },
                actionsEnabled = true,
                onViewHistory = { historyOpens++ },
                showHistory = true
            )
        }

        composeTestRule.onNodeWithTag(START_RUN_BUTTON_TAG).performClick()
        assertEquals(null, selectedAtStart)

        composeTestRule.onNodeWithTag(START_ENERGY_LOW_TAG).performClick()
        composeTestRule.onNodeWithTag(START_ENERGY_SELECTED_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag("startEnergyUndo").assertDoesNotExist()
        composeTestRule.onNodeWithText("Undo").assertDoesNotExist()
        composeTestRule.onNodeWithTag(START_ENERGY_MODERATE_TAG).assertDoesNotExist()
        // The selected agent is an indicator, not a replacement reset control.
        composeTestRule.onNodeWithTag(START_ENERGY_SELECTED_TAG).performTouchInput { click() }
        composeTestRule.onNodeWithTag(START_RUN_BUTTON_TAG).performClick()
        assertEquals(PreRunEnergy.LOW, selectedAtStart)
        assertEquals(PreRunEnergy.LOW, selected)
        assertEquals(listOf(PreRunEnergy.LOW), choices)

        composeTestRule.onNodeWithTag(START_HISTORY_TAG).performClick()
        assertEquals(1, historyOpens)
    }

    @Test
    fun startBlankAndEnergySwipesKeepNormalPhoneLayoutFixedAndSelectionPersists() {
        composeTestRule.mainClock.autoAdvance = false
        var selected by mutableStateOf<PreRunEnergy?>(null)
        var selectedAtStart: PreRunEnergy? = null
        var densityValue = 1f
        composeTestRule.setContent {
            val density = LocalDensity.current
            densityValue = density.density
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1f)) {
                StartRunScreen(
                    selectedPreRunEnergy = selected, onSelectPreRunEnergy = { selected = it },
                    onStart = { selectedAtStart = selected }, actionsEnabled = true,
                    onViewHistory = {}, showHistory = true, gpsReady = true, gpsStatusText = "GPS ready",
                    onOpenMap = {}, modifier = Modifier.fillMaxSize().systemBarsPadding()
                )
            }
        }
        composeTestRule.mainClock.advanceTimeByFrame()
        val fixedTags = listOf(START_AGENT_CARD_TAG, START_AGENT_VISUALIZER_TAG, START_UTILITY_ACTIONS_TAG,
            START_ENERGY_CHOICES_TAG, START_ENERGY_LOW_TAG, START_ENERGY_MODERATE_TAG,
            START_ENERGY_HIGH_TAG, START_FOOTER_TAG, START_RUN_BUTTON_TAG)
        val boundsBefore = fixedTags.associateWith { tag ->
            composeTestRule.onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        }
        val card = boundsBefore.getValue(START_AGENT_CARD_TAG)
        val gps = composeTestRule.onNodeWithTag(START_RUN_STATUS_TAG)
            .assertIsDisplayed().assertTextEquals("GPS Ready").fetchSemanticsNode().boundsInRoot
        assertTrue("Current GPS status belongs inside the compact Agent panel",
            gps.left >= card.left && gps.right <= card.right && gps.top >= card.top && gps.bottom <= card.bottom)
        val ready = composeTestRule.onNodeWithText("READY").fetchSemanticsNode().boundsInRoot
        val toRun = composeTestRule.onNodeWithText("TO RUN").fetchSemanticsNode().boundsInRoot
        assertEquals("GPS must share READY's right edge", ready.right, gps.right, 1f)
        assertEquals("The entire run-status group must share one right edge", toRun.right, gps.right, 1f)
        assertEquals("RUN and READY must begin on the same row",
            composeTestRule.onNodeWithText("RUN").fetchSemanticsNode().boundsInRoot.top, ready.top, 1f)
        assertEquals("STATE and TO RUN must begin on the same row",
            composeTestRule.onNodeWithText("STATE").fetchSemanticsNode().boundsInRoot.top, toRun.top, 1f)
        assertEquals("AGENT and GPS must begin on the same row",
            composeTestRule.onNodeWithText("AGENT  •  READY").fetchSemanticsNode().boundsInRoot.top, gps.top, 1f)
        val utilities = boundsBefore.getValue(START_UTILITY_ACTIONS_TAG)
        assertTrue("The utility stack must sit outside and below the Agent panel", utilities.top >= card.bottom)
        val utilityTags = listOf("startExpandMap", START_HISTORY_TAG, START_RUN_SETTINGS_TAG)
        val descriptions = listOf("Open live map", "Log History", "Run settings")
        val utilityBounds = utilityTags.mapIndexed { index, tag ->
            val node = composeTestRule.onNodeWithTag(tag).assertIsDisplayed().assertIsEnabled()
                .fetchSemanticsNode()
            assertEquals(Role.Button, node.config[SemanticsProperties.Role])
            assertEquals(listOf(descriptions[index]), node.config[SemanticsProperties.ContentDescription])
            assertTrue("Utility actions use icons without visible text", !node.config.contains(SemanticsProperties.Text))
            node.boundsInRoot.also { bounds ->
                assertTrue("Each utility target must remain at least 48 dp wide", bounds.width + 1f >= 48f * densityValue)
                assertTrue("Each utility target must remain at least 48 dp tall", bounds.height + 1f >= 48f * densityValue)
                assertTrue("Utilities cannot overlap the Agent panel", bounds.top >= card.bottom)
            }
        }
        utilityBounds.zipWithNext().forEach { (above, below) ->
            assertTrue("Map, History and Settings must form one vertical stack", above.bottom <= below.top)
            assertEquals("Utility icons share a horizontal center", above.center.x, below.center.x, 1f)
        }
        for (label in listOf("Ready when you are", "Map artwork", "Map", "History", "Settings")) {
            composeTestRule.onNodeWithText(label, useUnmergedTree = true).assertDoesNotExist()
        }
        composeTestRule.onNodeWithTag(START_MAP_STATUS_TAG).assertDoesNotExist()
        saveNativePreview(START_RUN_SCREEN_TAG, "start-compact-agent-external-icons-preview.png")
        fun assertFixed() {
            fixedTags.forEach { tag ->
                assertEquals("A swipe must not move $tag on the normal Start screen",
                    boundsBefore.getValue(tag),
                    composeTestRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot)
            }
            val moderate = composeTestRule.onNodeWithTag(START_ENERGY_MODERATE_TAG)
                .fetchSemanticsNode().boundsInRoot
            val footer = composeTestRule.onNodeWithTag(START_FOOTER_TAG).fetchSemanticsNode().boundsInRoot
            val start = composeTestRule.onNodeWithTag(START_RUN_BUTTON_TAG).fetchSemanticsNode().boundsInRoot
            assertTrue("Moderate must clear the entire footer, including its crest, by 12 dp",
                moderate.bottom + 12f * densityValue <= footer.top + 1f)
            assertTrue("The Start action must remain inside its own footer", start.top >= footer.top)
        }
        assertFixed()

        // Swipes originate in the unoccupied lower part of the upper content viewport.
        for (upward in listOf(true, false)) {
            composeTestRule.onNodeWithTag(START_RUN_UPPER_CONTENT_TAG).performTouchInput {
                val high = Offset(width * 0.9f, height * 0.68f)
                val low = Offset(width * 0.9f, height * 0.9f)
                swipe(if (upward) low else high, if (upward) high else low, durationMillis = 350)
            }
            composeTestRule.mainClock.advanceTimeBy(400L)
            assertFixed()
        }
        for (tag in listOf(START_ENERGY_LOW_TAG, START_ENERGY_MODERATE_TAG, START_ENERGY_HIGH_TAG)) {
            for (upward in listOf(true, false)) {
                composeTestRule.onNodeWithTag(tag).performTouchInput {
                    val high = Offset(width * 0.5f, height * 0.15f)
                    val low = Offset(width * 0.5f, height * 0.85f)
                    swipe(if (upward) low else high, if (upward) high else low, durationMillis = 350)
                }
                composeTestRule.mainClock.advanceTimeBy(400L)
                assertFixed()
                composeTestRule.runOnIdle { assertEquals(null, selected) }
            }
        }

        composeTestRule.onNodeWithTag(START_ENERGY_MODERATE_TAG).performTouchInput { click() }
        composeTestRule.mainClock.advanceTimeBy(200L)
        composeTestRule.onNodeWithTag(START_ENERGY_INTAKE_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(START_RUN_BUTTON_TAG).performClick()
        composeTestRule.runOnIdle { assertEquals(PreRunEnergy.MODERATE, selectedAtStart) }
        composeTestRule.onNodeWithTag("startEnergyUndo").assertDoesNotExist()
        composeTestRule.mainClock.advanceTimeBy(1_900L)
        composeTestRule.onNodeWithTag(START_ENERGY_INTAKE_TAG).assertDoesNotExist()
        composeTestRule.runOnIdle { assertEquals(PreRunEnergy.MODERATE, selected) }
        // A new isolated draft restores the original layout; no UI reset is offered.
        composeTestRule.runOnIdle { selected = null }
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.onNodeWithTag(START_ENERGY_INTAKE_TAG).assertDoesNotExist()
        composeTestRule.runOnIdle { assertEquals(null, selected) }
        assertFixed()
    }

    @Test
    fun shortLargeTextStartKeepsEnergyAndStartFixedWhileUpperActionsRemainReachable() {
        var selected by mutableStateOf<PreRunEnergy?>(null)
        var fontScale by mutableStateOf(1.5f)
        var settingsOpens = 0
        var historyOpens = 0
        var selectedAtStart: PreRunEnergy? = null
        var densityValue = 1f
        composeTestRule.setContent {
            val density = LocalDensity.current
            densityValue = density.density
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                StartRunScreen(
                    selectedPreRunEnergy = selected, onSelectPreRunEnergy = { selected = it },
                    onStart = { selectedAtStart = selected }, actionsEnabled = true,
                    onViewHistory = { historyOpens++ }, showHistory = true,
                    onOpenRunSettings = { settingsOpens++ },
                    onOpenMap = {}, modifier = Modifier.size(360.dp, 480.dp)
                )
            }
        }
        for (scale in listOf(1.5f, 2f)) {
            // Each font-scale scenario starts from a fresh test-owned draft.
            composeTestRule.runOnIdle { fontScale = scale; selected = null }
            listOf(START_ENERGY_LOW_TAG, START_ENERGY_MODERATE_TAG, START_ENERGY_HIGH_TAG)
                .forEach { composeTestRule.onNodeWithTag(it).assertIsDisplayed().assertIsEnabled() }
            composeTestRule.onNodeWithTag(START_RUN_BUTTON_TAG).assertIsDisplayed().assertIsEnabled()
            val footer = composeTestRule.onNodeWithTag(START_FOOTER_TAG).fetchSemanticsNode().boundsInRoot
            val start = composeTestRule.onNodeWithTag(START_RUN_BUTTON_TAG).fetchSemanticsNode().boundsInRoot
            val choices = composeTestRule.onNodeWithTag(START_ENERGY_CHOICES_TAG).fetchSemanticsNode().boundsInRoot
            val moderate = composeTestRule.onNodeWithTag(START_ENERGY_MODERATE_TAG).fetchSemanticsNode().boundsInRoot
            assertTrue("Large-text Energy must remain above the measured footer",
                moderate.bottom + 12f * densityValue <= footer.top + 1f)

            composeTestRule.onNodeWithTag(START_RUN_SETTINGS_TAG).performScrollTo()
                .assertIsDisplayed().performClick()
            composeTestRule.onNodeWithTag(START_HISTORY_TAG).performScrollTo()
                .assertIsDisplayed().performClick()
            assertEquals(footer, composeTestRule.onNodeWithTag(START_FOOTER_TAG).fetchSemanticsNode().boundsInRoot)
            assertEquals(start, composeTestRule.onNodeWithTag(START_RUN_BUTTON_TAG).fetchSemanticsNode().boundsInRoot)
            assertEquals(choices, composeTestRule.onNodeWithTag(START_ENERGY_CHOICES_TAG).fetchSemanticsNode().boundsInRoot)

            composeTestRule.onNodeWithTag(START_ENERGY_MODERATE_TAG).performClick()
            composeTestRule.onNodeWithTag(START_RUN_BUTTON_TAG).performClick()
            composeTestRule.runOnIdle { assertEquals(PreRunEnergy.MODERATE, selectedAtStart) }
            composeTestRule.onNodeWithTag("startEnergyUndo").assertDoesNotExist()
            composeTestRule.runOnIdle { assertEquals(PreRunEnergy.MODERATE, selected) }
        }
        composeTestRule.runOnIdle {
            assertEquals(2, settingsOpens)
            assertEquals(2, historyOpens)
        }
    }

    @Test
    fun landscapeStartScrollsOneStackWithoutLosingEnergyFooterOrUpperActions() {
        var selected by mutableStateOf<PreRunEnergy?>(null)
        var selectedAtStart: PreRunEnergy? = null
        var starts = 0
        var settingsOpens = 0
        var historyOpens = 0
        composeTestRule.setContent {
            // A logical landscape viewport without rotating the user's phone or writing settings.
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.5f)) {
                StartRunScreen(
                    selectedPreRunEnergy = selected, onSelectPreRunEnergy = { selected = it },
                    onStart = { starts++; selectedAtStart = selected }, actionsEnabled = true,
                    onViewHistory = { historyOpens++ }, showHistory = true,
                    onOpenRunSettings = { settingsOpens++ },
                    onOpenMap = {}, modifier = Modifier.size(640.dp, 320.dp)
                )
            }
        }
        composeTestRule.onNodeWithTag(START_RUN_FALLBACK_SCROLL_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(START_RUN_SETTINGS_TAG).performScrollTo()
            .assertIsDisplayed().performTouchInput { click() }
        composeTestRule.onNodeWithTag(START_HISTORY_TAG).performScrollTo()
            .assertIsDisplayed().performTouchInput { click() }
        composeTestRule.onNodeWithTag(START_ENERGY_MODERATE_TAG).performScrollTo()
            .assertIsDisplayed().performTouchInput { click() }
        composeTestRule.runOnIdle { assertEquals(PreRunEnergy.MODERATE, selected) }
        composeTestRule.onNodeWithTag("startEnergyUndo").assertDoesNotExist()

        composeTestRule.onNodeWithTag(START_FOOTER_TAG).performScrollTo().assertIsDisplayed()
        val energySlot = composeTestRule.onNodeWithTag(START_ENERGY_CUE_TAG)
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val footer = composeTestRule.onNodeWithTag(START_FOOTER_TAG).fetchSemanticsNode().boundsInRoot
        val start = composeTestRule.onNodeWithTag(START_RUN_BUTTON_TAG)
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("The scrolling landscape stack preserves the Energy/footer gap",
            energySlot.bottom + 12f <= footer.top + 1f)
        assertTrue("The Start control remains inside the same measured footer", start.top >= footer.top)
        composeTestRule.onNodeWithTag(START_RUN_BUTTON_TAG).performTouchInput { click() }
        composeTestRule.runOnIdle {
            assertEquals(PreRunEnergy.MODERATE, selectedAtStart)
            assertEquals(1, starts)
            assertEquals(1, settingsOpens)
            assertEquals(1, historyOpens)
        }
    }

    /** The visible agent changes shape over time without moving its layout or nearby controls. */
    @Test
    fun startAgentActuallyAnimatesInsideStableBounds() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            StartRunScreen(
                selectedPreRunEnergy = null,
                onSelectPreRunEnergy = {},
                onStart = {},
                actionsEnabled = true,
                onViewHistory = {},
                showHistory = false
            )
        }
        composeTestRule.mainClock.advanceTimeByFrame()
        val ring = composeTestRule.onNodeWithTag(START_AGENT_VISUALIZER_TAG)
        ring.assertIsDisplayed()
        val initialBounds = ring.fetchSemanticsNode().boundsInRoot
        val initialImage = ring.captureToImage()
        val initialPixels = IntArray(initialImage.width * initialImage.height)
        initialImage.readPixels(initialPixels)

        composeTestRule.mainClock.advanceTimeBy(400L)
        val laterImage = ring.captureToImage()
        val laterPixels = IntArray(laterImage.width * laterImage.height)
        laterImage.readPixels(laterPixels)

        assertEquals(initialBounds, ring.fetchSemanticsNode().boundsInRoot)
        assertEquals(initialImage.width, laterImage.width)
        assertEquals(initialImage.height, laterImage.height)
        assertFalse("The actual agent drawing must change between animation frames",
            initialPixels.contentEquals(laterPixels))
        composeTestRule.onNodeWithTag(START_RUN_BUTTON_TAG).assertIsDisplayed().assertIsEnabled()
    }

    /** Selecting is immediate; its decorative tile then travels toward the measured agent. */
    @Test
    fun startEnergyFeedsTheAgentWithoutDelayingTheStartChoice() {
        composeTestRule.mainClock.autoAdvance = false
        var selected by mutableStateOf<PreRunEnergy?>(null)
        var selectedAtStart: PreRunEnergy? = null
        val choices = mutableListOf<PreRunEnergy?>()
        composeTestRule.setContent {
            StartRunScreen(
                selectedPreRunEnergy = selected,
                onSelectPreRunEnergy = { selected = it; choices += it },
                onStart = { selectedAtStart = selected },
                actionsEnabled = true,
                onViewHistory = {},
                showHistory = false
            )
        }

        // The frozen clock must deliver layout and its measured ring bounds before intake.
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(START_AGENT_VISUALIZER_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(START_ENERGY_HIGH_TAG).performClick()
        composeTestRule.runOnIdle {
            assertEquals(listOf(PreRunEnergy.HIGH), choices)
            assertEquals(PreRunEnergy.HIGH, selected)
        }
        composeTestRule.mainClock.advanceTimeBy(200L)
        val source = composeTestRule.onNodeWithTag(START_ENERGY_INTAKE_TAG)
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val target = composeTestRule.onNodeWithTag(START_AGENT_VISUALIZER_TAG)
            .fetchSemanticsNode().boundsInRoot.center
        composeTestRule.onNodeWithTag("startEnergyUndo").assertDoesNotExist()
        composeTestRule.onNodeWithTag(START_RUN_BUTTON_TAG).performClick()
        assertEquals(PreRunEnergy.HIGH, selectedAtStart)

        composeTestRule.mainClock.advanceTimeBy(550L)
        val travelling = composeTestRule.onNodeWithTag(START_ENERGY_INTAKE_TAG)
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("The chosen tile must move toward the visible agent",
            (travelling.center - target).getDistance() < (source.center - target).getDistance())
        assertTrue("The chosen tile must shrink as the agent receives it", travelling.width < source.width)

        composeTestRule.mainClock.advanceTimeBy(1_100L)
        composeTestRule.onNodeWithTag(START_ENERGY_INTAKE_TAG).assertDoesNotExist()
        val agentState = composeTestRule.onNodeWithTag(START_ENERGY_SELECTED_TAG, useUnmergedTree = true)
            .assertIsDisplayed().fetchSemanticsNode().config
        assertTrue(agentState[SemanticsProperties.Selected])
        assertEquals("Pre-run energy: HIGH", agentState[SemanticsProperties.StateDescription])
        composeTestRule.onNodeWithTag(START_ENERGY_HIGH_TAG).assertDoesNotExist()
        composeTestRule.onNodeWithTag("startEnergyUndo").assertDoesNotExist()
        assertEquals(PreRunEnergy.HIGH, selected)
    }

    /** Replacing a draft cancels its old decorative flight without changing the new selection. */
    @Test
    fun freshDraftDuringEnergyIntakeDoesNotReceiveStaleCompletion() {
        composeTestRule.mainClock.autoAdvance = false
        var selected by mutableStateOf<PreRunEnergy?>(null)
        var selectedAtStart: PreRunEnergy? = null
        val choices = mutableListOf<PreRunEnergy?>()
        composeTestRule.setContent {
            StartRunScreen(
                selectedPreRunEnergy = selected,
                onSelectPreRunEnergy = { selected = it; choices += it },
                onStart = { selectedAtStart = selected },
                actionsEnabled = true,
                onViewHistory = {},
                showHistory = false
            )
        }

        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(START_ENERGY_HIGH_TAG).performClick()
        composeTestRule.mainClock.advanceTimeBy(700L)
        composeTestRule.onNodeWithTag(START_ENERGY_INTAKE_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag("startEnergyUndo").assertDoesNotExist()
        // Simulate the journey owner supplying a fresh draft, not a hidden UI reset.
        composeTestRule.runOnIdle { selected = null }
        // The effect clears the old flight after this external state change; its removal then
        // needs another composition frame. Stay well short of the original flight's finish.
        for (frame in 1..10) {
            composeTestRule.mainClock.advanceTimeByFrame()
            composeTestRule.waitForIdle()
            if (composeTestRule.onAllNodesWithTag(START_ENERGY_INTAKE_TAG).fetchSemanticsNodes().isEmpty()) break
        }
        composeTestRule.onNodeWithTag(START_ENERGY_INTAKE_TAG).assertDoesNotExist()
        composeTestRule.onNodeWithTag(START_ENERGY_LOW_TAG).performClick()
        composeTestRule.mainClock.advanceTimeBy(1_000L)

        // The first flight would have finished by now; the second must retain its own draft.
        composeTestRule.onNodeWithTag(START_ENERGY_INTAKE_TAG).assertExists()
        composeTestRule.onNodeWithTag(START_RUN_BUTTON_TAG).performClick()
        assertEquals(PreRunEnergy.LOW, selectedAtStart)
        composeTestRule.mainClock.advanceTimeBy(900L)
        composeTestRule.onNodeWithTag(START_ENERGY_INTAKE_TAG).assertDoesNotExist()
        assertEquals(PreRunEnergy.LOW, selected)
        assertEquals(listOf(PreRunEnergy.HIGH, PreRunEnergy.LOW), choices)
        composeTestRule.onNodeWithTag("startEnergyUndo").assertDoesNotExist()
    }

    /** Restoring or backgrounding preserves the draft without replaying an old visual hand-off. */
    @Test
    fun restoredEnergyDoesNotReplayAndBackgroundingCancelsOnlyTheNewVisualFlight() {
        composeTestRule.mainClock.autoAdvance = false
        val owner = TestLifecycle()
        moveTo(owner, Lifecycle.State.RESUMED)
        var selected by mutableStateOf<PreRunEnergy?>(PreRunEnergy.HIGH)
        var selectedAtStart: PreRunEnergy? = null
        composeTestRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                StartRunScreen(
                    selectedPreRunEnergy = selected,
                    onSelectPreRunEnergy = { selected = it },
                    onStart = { selectedAtStart = selected },
                    actionsEnabled = true,
                    onViewHistory = {},
                    showHistory = false
                )
            }
        }

        composeTestRule.onNodeWithTag(START_ENERGY_SELECTED_TAG, useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag(START_ENERGY_INTAKE_TAG).assertDoesNotExist()
        composeTestRule.mainClock.advanceTimeBy(1_900L)
        composeTestRule.onNodeWithTag(START_ENERGY_INTAKE_TAG).assertDoesNotExist()
        composeTestRule.onNodeWithTag("startEnergyUndo").assertDoesNotExist()
        // Begin a fresh fixture draft before testing a new flight's lifecycle.
        composeTestRule.runOnIdle { selected = null }
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.onNodeWithTag(START_ENERGY_MODERATE_TAG).performClick()
        composeTestRule.mainClock.advanceTimeBy(650L)
        composeTestRule.onNodeWithTag(START_ENERGY_INTAKE_TAG).assertIsDisplayed()

        moveTo(owner, Lifecycle.State.CREATED)
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.onNodeWithTag(START_ENERGY_INTAKE_TAG).assertDoesNotExist()
        assertEquals(PreRunEnergy.MODERATE, selected)
        moveTo(owner, Lifecycle.State.RESUMED)
        composeTestRule.mainClock.advanceTimeBy(1_900L)
        composeTestRule.onNodeWithTag(START_ENERGY_INTAKE_TAG).assertDoesNotExist()
        composeTestRule.onNodeWithTag(START_RUN_BUTTON_TAG).performClick()
        assertEquals(PreRunEnergy.MODERATE, selectedAtStart)
        composeTestRule.onNodeWithTag("startEnergyUndo").assertDoesNotExist()
    }

    @Test
    fun startReportsGpsReadinessWithoutAModeSwitch() {
        var gpsReady by mutableStateOf(false)
        var gpsSelected by mutableStateOf(true)
        var loading by mutableStateOf(false)
        var statusText by mutableStateOf<String?>(null)
        composeTestRule.setContent {
            StartRunScreen(
                selectedPreRunEnergy = null,
                onSelectPreRunEnergy = {},
                onStart = {},
                actionsEnabled = true,
                onViewHistory = {},
                showHistory = false,
                gpsSelected = gpsSelected,
                gpsReady = gpsReady,
                gpsStatusText = statusText,
                setupLoading = loading
            )
        }

        composeTestRule.onNodeWithText("Location setup on Start").assertExists()
        composeTestRule.onNodeWithText("GPS Ready").assertDoesNotExist()
        composeTestRule.onNodeWithTag(START_GPS_TOGGLE_TAG).assertDoesNotExist()
        composeTestRule.runOnIdle { gpsReady = true }
        // Permission alone is not a current usable GPS fix.
        composeTestRule.onNodeWithTag(START_RUN_STATUS_TAG).assertTextEquals("GPS connecting…")
        composeTestRule.onNodeWithText("GPS Ready").assertDoesNotExist()
        composeTestRule.onNodeWithText("Sample demo · simulated distance").assertDoesNotExist()
        for (label in listOf("GPS connecting…", "GPS signal weak", "GPS signal lost", "GPS ready")) {
            composeTestRule.runOnIdle { statusText = label }
            composeTestRule.onNodeWithTag(START_RUN_STATUS_TAG)
                .assertTextEquals(if (label == "GPS ready") "GPS Ready" else label)
            composeTestRule.onNodeWithText("GPS enabled").assertDoesNotExist()
            if (label != "GPS ready") composeTestRule.onNodeWithText("GPS Ready").assertDoesNotExist()
            composeTestRule.onNodeWithTag(START_RUN_BUTTON_TAG).assertIsEnabled()
            composeTestRule.onNode(isDialog()).assertDoesNotExist()
        }
        composeTestRule.runOnIdle { loading = true }
        composeTestRule.onNodeWithTag(START_RUN_STATUS_TAG).assertTextEquals("Loading run preferences…")
        composeTestRule.onNodeWithText("GPS Ready").assertDoesNotExist()
        composeTestRule.onNodeWithTag(START_RUN_BUTTON_TAG).assertIsNotEnabled()
        composeTestRule.runOnIdle { loading = false; gpsSelected = false }
        composeTestRule.onNodeWithTag(START_RUN_STATUS_TAG).assertTextEquals("Sample demo · simulated distance")
        composeTestRule.onNodeWithText("GPS Ready").assertDoesNotExist()
        composeTestRule.runOnIdle { gpsSelected = true; gpsReady = false; statusText = null }
        composeTestRule.onNodeWithTag(START_RUN_STATUS_TAG).assertTextEquals("Location setup on Start")
        composeTestRule.onNodeWithText("GPS Ready").assertDoesNotExist()
        composeTestRule.onNodeWithTag(START_RUN_BUTTON_TAG).assertIsEnabled()
    }

    @Test
    fun gpsRootWarmsTheStaticStartScreenWithoutStartingARun() {
        GpsStartRootHarness().use { root ->
            composeTestRule.onNodeWithText("Map artwork").assertDoesNotExist()
            composeTestRule.onNodeWithTag(START_MAP_STATUS_TAG).assertDoesNotExist()
            composeTestRule.onNodeWithText("GPS connecting…").assertExists()
            composeTestRule.onNode(isDialog()).assertDoesNotExist()
            composeTestRule.onNodeWithTag(START_RUN_BUTTON_TAG).assertIsEnabled()
            composeTestRule.mainClock.advanceTimeBy(LONGER_THAN_COUNTDOWN_MILLIS)
            root.assertUnstarted()
            assertEquals(1, root.source.registrations)
            assertEquals(1, root.source.activeListeners)
            root.emit(gpsStartFix())
            composeTestRule.onNodeWithText("GPS Ready").assertExists()
            root.assertUnstarted()
        }
    }

    @Test
    fun gpsRootStartsCountdownWithoutWaitingForAnyFix() {
        GpsStartRootHarness().use { root ->
            root.tapStart()
            root.awaitCountdown()
            composeTestRule.onNodeWithText("Finding GPS").assertDoesNotExist()
            composeTestRule.onNode(isDialog()).assertDoesNotExist()
            assertEquals(1, root.source.activeListeners)
            assertEquals(0, root.rowCount())
            composeTestRule.mainClock.advanceTimeBy((COUNTDOWN_SECONDS - 1) * COUNTDOWN_STEP_MILLIS)
            assertEquals(0, root.serviceStarts)
            composeTestRule.mainClock.advanceTimeBy(COUNTDOWN_STEP_MILLIS + 100)
            root.awaitSingleHandoff()
            assertEquals(1, root.source.registrations)
            assertEquals(1, root.source.removals)
        }
    }

    @Test
    fun gpsRootCachedOrWeakFixNeverClaimsReadyButDoesNotBlockStart() {
        GpsStartRootHarness(ControlledGpsStartSource(gpsStartFix())).use { root ->
            assertEquals(1, root.source.cacheReads)
            composeTestRule.onNodeWithText("GPS Ready").assertDoesNotExist()
            composeTestRule.onNodeWithTag(START_RUN_BUTTON_TAG).assertIsEnabled()
            root.emit(gpsStartFix(accuracyMeters = 100.0))
            composeTestRule.onNodeWithText("GPS signal weak").assertExists()
            composeTestRule.onNodeWithText("GPS Ready").assertDoesNotExist()
            root.tapStart()
            root.awaitCountdown()
            composeTestRule.mainClock.advanceTimeBy(LONGER_THAN_COUNTDOWN_MILLIS)
            root.awaitSingleHandoff()
        }
    }

    @Test
    fun gpsRootLiveFixKeepsListenerUntilSingleHandoffAndRestoresOnlyDispatchedMarker() {
        GpsStartRootHarness().use { root ->
            root.emit(gpsStartFix())
            composeTestRule.onNodeWithText("GPS Ready").assertExists()
            root.tapStart()
            root.awaitCountdown()
            assertEquals(1, root.source.registrations)
            assertEquals(1, root.source.activeListeners)
            assertEquals(0, root.source.removals)
            assertEquals(0, root.rowCount())
            composeTestRule.mainClock.advanceTimeBy(LONGER_THAN_COUNTDOWN_MILLIS)
            root.awaitSingleHandoff()
            root.restoreComposition(expectWarmup = false)
            awaitGpsRoot { root.admission() == RunAdmission.CountdownInProgress }
            composeTestRule.mainClock.advanceTimeBy(LONGER_THAN_COUNTDOWN_MILLIS)
            assertEquals(1, root.serviceStarts)
            assertEquals(1, root.source.registrations)
            assertEquals(0, root.rowCount())
            assertEquals(RunAdmission.CountdownInProgress, root.admission())
        }
    }

    @Test
    fun gpsRootRestorationCancelsUndispatchedCountdownAndRewarms() {
        GpsStartRootHarness().use { root ->
            val firstListener = root.source.callbacks.last()
            root.restoreComposition(expectWarmup = true)
            composeTestRule.runOnUiThread { firstListener.sample(gpsStartFix()) }
            composeTestRule.mainClock.advanceTimeBy(LONGER_THAN_COUNTDOWN_MILLIS)
            root.assertUnstarted()
            composeTestRule.onNodeWithText("GPS Ready").assertDoesNotExist()
            assertEquals(2, root.source.registrations)
            root.tapStart()
            root.awaitCountdown()
            root.restoreComposition(expectWarmup = true)
            awaitGpsRoot { root.admission() == RunAdmission.ReadyForCountdown }
            composeTestRule.mainClock.advanceTimeBy(LONGER_THAN_COUNTDOWN_MILLIS)
            root.assertUnstarted()
            composeTestRule.onNodeWithTag(START_RUN_BUTTON_TAG).assertIsEnabled()
            assertEquals(3, root.source.registrations)
        }
    }

    @Test
    fun gpsRootStartNowClosesCountdownVoiceAndHandsOffOnlyOnce() {
        val spoken = mutableListOf<String>()
        var closes = 0
        val voice = object : ReflectionSpeaker {
            override fun speak(text: String) { spoken += text }
            override fun stop() = Unit
            override fun close() { closes++ }
        }
        GpsStartRootHarness(countdownVoice = voice).use { root ->
            root.tapStart()
            root.awaitCountdown()
            awaitGpsRoot { spoken.isNotEmpty() }
            assertEquals(listOf("Starting in ten."), spoken)
            composeTestRule.onNodeWithTag(COUNTDOWN_START_NOW_TAG).performClick()
            root.awaitSingleHandoff()
            composeTestRule.mainClock.advanceTimeBy(LONGER_THAN_COUNTDOWN_MILLIS)
            assertEquals(1, root.serviceStarts)
            assertEquals(listOf("Starting in ten."), spoken)
            assertTrue(closes >= 1)
        }
    }

    @Test
    fun gpsRootCancelKeepsOneWarmListenerForTheNextStart() {
        GpsStartRootHarness().use { root ->
            root.tapStart()
            root.awaitCountdown()
            composeTestRule.onNodeWithText(CANCEL).assertIsEnabled().performClick()
            awaitGpsRoot { root.admission() == RunAdmission.ReadyForCountdown }
            composeTestRule.mainClock.advanceTimeBy(LONGER_THAN_COUNTDOWN_MILLIS)
            root.assertUnstarted()
            assertEquals(1, root.source.registrations)
            assertEquals(0, root.source.removals)
            assertEquals(1, root.source.activeListeners)
            root.emit(gpsStartFix())
            composeTestRule.onNodeWithText("GPS Ready").assertExists()
            root.tapStart()
            root.awaitCountdown()
            composeTestRule.mainClock.advanceTimeBy(LONGER_THAN_COUNTDOWN_MILLIS)
            root.awaitSingleHandoff()
            assertEquals(1, root.source.registrations)
        }
    }

    @Test
    fun gpsRootBackgroundStopsWarmupAndCancelsCountdownUntilAnotherTap() {
        GpsStartRootHarness().use { root ->
            val waiting = root.source.callbacks.last()
            moveTo(root.lifecycleOwner, Lifecycle.State.CREATED)
            awaitGpsRoot { root.source.activeListeners == 0 }
            composeTestRule.runOnUiThread { waiting.sample(gpsStartFix()) }
            moveTo(root.lifecycleOwner, Lifecycle.State.RESUMED)
            awaitGpsRoot { root.source.activeListeners == 1 }
            composeTestRule.mainClock.advanceTimeBy(LONGER_THAN_COUNTDOWN_MILLIS)
            root.assertUnstarted()
            composeTestRule.onNodeWithText("GPS Ready").assertDoesNotExist()
            root.tapStart()
            root.awaitCountdown()
            val counting = root.source.callbacks.last()
            moveTo(root.lifecycleOwner, Lifecycle.State.CREATED)
            awaitGpsRoot { root.admission() == RunAdmission.ReadyForCountdown && root.source.activeListeners == 0 }
            composeTestRule.runOnUiThread { counting.sample(gpsStartFix()) }
            moveTo(root.lifecycleOwner, Lifecycle.State.RESUMED)
            awaitGpsRoot { root.source.activeListeners == 1 }
            composeTestRule.mainClock.advanceTimeBy(LONGER_THAN_COUNTDOWN_MILLIS)
            root.assertUnstarted()
            composeTestRule.onNodeWithTag(START_RUN_BUTTON_TAG).assertIsEnabled()
            assertEquals(3, root.source.registrations)
            assertEquals(2, root.source.removals)
            root.tapStart()
            root.awaitCountdown()
            composeTestRule.mainClock.advanceTimeBy(LONGER_THAN_COUNTDOWN_MILLIS)
            root.awaitSingleHandoff()
        }
    }

    @Test
    fun gpsRootLosingUsableFixDuringCountdownStillStartsOnce() {
        GpsStartRootHarness().use { root ->
            root.emit(gpsStartFix())
            root.tapStart()
            root.awaitCountdown()
            root.emit(gpsStartFix(ageMillis = 0, accuracyMeters = 100.0))
            composeTestRule.mainClock.advanceTimeBy(LONGER_THAN_COUNTDOWN_MILLIS)
            root.awaitSingleHandoff()
            assertEquals(1, root.source.registrations)
        }
    }

    @Test
    fun gpsRootPermissionLossDuringCountdownCancelsBeforeServiceHandoff() {
        GpsStartRootHarness().use { root ->
            root.tapStart()
            root.awaitCountdown()
            composeTestRule.runOnUiThread {
                root.locationReady = false
                root.source.currentAvailability = GpsPreviewAvailability.PERMISSION_NEEDED
            }
            composeTestRule.mainClock.advanceTimeBy(LONGER_THAN_COUNTDOWN_MILLIS)
            awaitGpsRoot { root.admission() == RunAdmission.ReadyForCountdown }
            root.assertUnstarted()
            composeTestRule.onNodeWithText("Precise GPS is unavailable", substring = true).assertExists()
        }
    }

    @Test
    fun gpsRootProviderLossDuringCountdownCancelsBeforeServiceHandoff() {
        GpsStartRootHarness().use { root ->
            root.tapStart()
            root.awaitCountdown()
            composeTestRule.runOnUiThread {
                root.source.currentAvailability = GpsPreviewAvailability.LOCATION_OFF
                root.source.callbacks.last().disabled()
            }
            composeTestRule.mainClock.advanceTimeBy(LONGER_THAN_COUNTDOWN_MILLIS)
            awaitGpsRoot { root.admission() == RunAdmission.ReadyForCountdown }
            root.assertUnstarted()
            composeTestRule.onNodeWithText("Precise GPS is unavailable", substring = true).assertExists()
        }
    }

    @Test
    fun gpsRootServiceLaunchFailureRewarmsAndAllowsANewExplicitStart() {
        GpsStartRootHarness().use { root ->
            root.serviceFailuresRemaining = 1
            root.tapStart()
            root.awaitCountdown()
            composeTestRule.mainClock.advanceTimeBy(LONGER_THAN_COUNTDOWN_MILLIS)
            awaitGpsRoot { root.admission() == RunAdmission.ReadyForCountdown && root.source.activeListeners == 1 }
            assertEquals(1, root.serviceStarts)
            assertEquals(0, root.rowCount())
            assertEquals(2, root.source.registrations)
            composeTestRule.onNodeWithText("Android could not start GPS tracking", substring = true).assertExists()
            composeTestRule.mainClock.advanceTimeBy(LONGER_THAN_COUNTDOWN_MILLIS)
            assertEquals(1, root.serviceStarts)
            root.tapStart()
            root.awaitCountdown()
            composeTestRule.mainClock.advanceTimeBy(LONGER_THAN_COUNTDOWN_MILLIS)
            awaitGpsRoot { root.serviceStarts == 2 }
            assertEquals(listOf(0, 0), root.listenersAtHandoff)
            assertEquals(listOf(0, 0), root.rowsAtHandoff)
            assertEquals(0, root.source.activeListeners)
            assertEquals(0, root.rowCount())
        }
    }

    @Test
    fun gpsRootMapTogglesAndMapStartKeepTheSameWarmListener() {
        GpsStartRootHarness().use { root ->
            repeat(2) {
                composeTestRule.onNodeWithTag("startExpandMap").performClick()
                composeTestRule.mainClock.advanceTimeByFrame()
                composeTestRule.onNodeWithTag(ACTIVE_RUN_MAP_DIALOG_TAG).assertIsDisplayed()
                assertEquals(1, root.source.registrations)
                assertEquals(1, root.source.activeListeners)
                assertEquals(0, root.source.removals)
                composeTestRule.onNodeWithText("Close map").performClick()
                composeTestRule.mainClock.advanceTimeByFrame()
                composeTestRule.onNodeWithTag(START_RUN_BUTTON_TAG).assertIsDisplayed()
                assertEquals(1, root.source.registrations)
                assertEquals(1, root.source.activeListeners)
                assertEquals(0, root.source.removals)
                root.assertUnstarted()
            }
            composeTestRule.onNodeWithTag("startExpandMap").performClick()
            composeTestRule.mainClock.advanceTimeByFrame()
            composeTestRule.onNodeWithTag(START_RUN_MAP_START_TAG).assertIsEnabled().performClick()
            root.awaitCountdown()
            assertEquals(1, root.source.registrations)
            assertEquals(1, root.source.activeListeners)
            composeTestRule.mainClock.advanceTimeBy(LONGER_THAN_COUNTDOWN_MILLIS)
            root.awaitSingleHandoff()
        }
    }

    @Test
    fun gpsRootHistoryAndSettingsReleaseThenResumeWarmup() {
        GpsStartRootHarness().use { root ->
            composeTestRule.onNodeWithTag(START_HISTORY_TAG).performClick()
            awaitGpsRoot { root.source.activeListeners == 0 }
            composeTestRule.onNodeWithText("Back to run").assertIsDisplayed().performClick()
            awaitGpsRoot { root.source.activeListeners == 1 }
            assertEquals(2, root.source.registrations)
            root.assertUnstarted()
            composeTestRule.onNodeWithTag(START_RUN_SETTINGS_TAG).performClick()
            awaitGpsRoot { root.source.activeListeners == 0 }
            composeTestRule.onNodeWithTag(SETTINGS_DIALOG_TAG).assertIsDisplayed()
            composeTestRule.onNodeWithTag(SETTINGS_DONE_TAG).performClick()
            awaitGpsRoot { root.source.activeListeners == 1 }
            assertEquals(3, root.source.registrations)
            assertEquals(2, root.source.removals)
            root.assertUnstarted()
        }
    }

    /** Synthetic fixes and isolated Room exercise the real root without requesting device GPS. */
    private inner class GpsStartRootHarness(
        val source: ControlledGpsStartSource = ControlledGpsStartSource(),
        private val countdownVoice: ReflectionSpeaker? = null
    ) : AutoCloseable {
        private val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), RunStateDatabase::class.java).build()
        private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private val coordinator = RunSessionCoordinator(database.runDao(), PreparedRunFactory(
            Clock.fixed(Instant.ofEpochMilli(1_756_000_000_000), ZoneOffset.UTC),
            { ZoneOffset.UTC }, UUID::randomUUID), applicationScope, database.locationDao())
        val lifecycleOwner = TestLifecycle()
        private val restoration = StateRestorationTester(composeTestRule)
        private var shown by mutableStateOf(true)
        private var compositionEntries = 0
        private var compositionDisposals = 0
        var locationReady = true
        var serviceFailuresRemaining = 0
        var serviceStarts = 0
            private set
        val listenersAtHandoff = mutableListOf<Int>()
        val rowsAtHandoff = mutableListOf<Int>()
        private val silentSpeaker = object : ReflectionSpeaker {
            override fun speak(text: String) = Unit
            override fun stop() = Unit
            override fun close() = Unit
        }

        init {
            moveTo(lifecycleOwner, Lifecycle.State.RESUMED)
            restoration.setContent {
                if (shown) {
                    DisposableEffect(Unit) {
                        compositionEntries++
                        onDispose { compositionDisposals++ }
                    }
                    RunJourneyRoot(coordinator, null, database,
                        remember { MutableStateFlow<RunTrackingServiceState>(RunTrackingServiceState.Idle) },
                        lifecycleOwner.lifecycle, completionSpeakerFactory = { silentSpeaker },
                        countdownSpeakerFactory = if (countdownVoice == null) null else ({ countdownVoice }),
                        musicAccessProvider = { false }, gpsPreviewSourceOverride = source,
                        gpsLocationReady = { locationReady && source.currentAvailability == GpsPreviewAvailability.READY }, startGpsRun = { _, _, _, _, _ ->
                            listenersAtHandoff += source.activeListeners
                            rowsAtHandoff += rowCount()
                            serviceStarts++
                            if (serviceFailuresRemaining > 0) {
                                serviceFailuresRemaining--
                                throw IllegalStateException("Synthetic service launch failure")
                            }
                        })
                }
            }
            composeTestRule.waitUntil(5_000) {
                composeTestRule.onAllNodesWithTag(START_RUN_BUTTON_TAG).fetchSemanticsNodes().isNotEmpty() &&
                    source.activeListeners == 1
            }
            composeTestRule.mainClock.autoAdvance = false
            assertEquals(1, source.registrations)
            assertUnstarted()
        }

        fun tapStart() {
            composeTestRule.onNodeWithTag(START_RUN_BUTTON_TAG).assertIsEnabled().performClick()
        }

        fun emit(fix: ForegroundGpsPreviewFix) {
            composeTestRule.runOnUiThread { source.callbacks.last().sample(fix) }
            composeTestRule.mainClock.advanceTimeByFrame()
            composeTestRule.waitForIdle()
        }

        fun awaitCountdown() {
            awaitGpsRoot {
                admission() == RunAdmission.CountdownInProgress &&
                    composeTestRule.onAllNodesWithText(COUNTDOWN).fetchSemanticsNodes().isNotEmpty()
            }
        }

        fun awaitSingleHandoff() {
            awaitGpsRoot { serviceStarts == 1 }
            assertEquals(listOf(0), listenersAtHandoff)
            assertEquals(listOf(0), rowsAtHandoff)
            assertEquals(0, source.activeListeners)
            assertEquals(0, rowCount())
        }

        fun admission() = runBlocking { coordinator.journeySnapshot().admission }
        fun rowCount() = runBlocking { database.runDao().countRuns() }
        fun restoreComposition(expectWarmup: Boolean) {
            val entriesBefore = compositionEntries
            val disposalsBefore = compositionDisposals
            val listenersBefore = source.activeListeners
            val removalsBefore = source.removals
            val registrationsBefore = source.registrations
            val startsBefore = serviceStarts
            val previousAutoAdvance = composeTestRule.mainClock.autoAdvance
            try {
                // StateRestorationTester uses separate runOnIdle calls for remove/re-emit.
                // With a paused clock those states can collapse without a disposal frame.
                composeTestRule.mainClock.autoAdvance = true
                restoration.emulateSavedInstanceStateRestore()
            } finally {
                composeTestRule.mainClock.autoAdvance = previousAutoAdvance
            }
            awaitGpsRoot {
                compositionEntries == entriesBefore + 1 && compositionDisposals == disposalsBefore + 1 &&
                    source.activeListeners == (if (expectWarmup) 1 else 0) &&
                    (composeTestRule.onAllNodesWithTag(START_RUN_BUTTON_TAG).fetchSemanticsNodes().isNotEmpty() ||
                        composeTestRule.onAllNodesWithText(COUNTDOWN).fetchSemanticsNodes().isNotEmpty())
            }
            assertEquals("Restoration must dispose the old root", disposalsBefore + 1, compositionDisposals)
            assertEquals("Restoration must create a new root", entriesBefore + 1, compositionEntries)
            assertEquals("Only an undispatched start rewarms location",
                registrationsBefore + (if (expectWarmup) 1 else 0), source.registrations)
            assertEquals("Every previous preview listener is removed", removalsBefore + listenersBefore, source.removals)
            assertEquals(if (expectWarmup) 1 else 0, source.activeListeners)
            assertEquals("Restoration cannot dispatch a start", startsBefore, serviceStarts)
        }

        fun assertUnstarted() {
            assertEquals(RunAdmission.ReadyForCountdown, admission())
            assertEquals(0, rowCount())
            assertEquals(0, serviceStarts)
        }

        override fun close() {
            composeTestRule.runOnUiThread { shown = false }
            composeTestRule.mainClock.advanceTimeByFrame()
            composeTestRule.waitForIdle()
            applicationScope.cancel()
            database.close()
            composeTestRule.mainClock.autoAdvance = true
        }
    }

    private class ControlledGpsStartSource(
        private val cached: ForegroundGpsPreviewFix? = null
    ) : ForegroundGpsPreviewSource {
        data class Callbacks(val sample: (ForegroundGpsPreviewFix) -> Unit, val disabled: () -> Unit)
        val callbacks = mutableListOf<Callbacks>()
        var registrations = 0
            private set
        var removals = 0
            private set
        var activeListeners = 0
            private set
        var cacheReads = 0
            private set
        var currentAvailability = GpsPreviewAvailability.READY
        override fun availability() = currentAvailability
        override fun lastKnownSample(): ForegroundGpsPreviewFix? { cacheReads++; return cached }
        override fun listen(onSample: (ForegroundGpsPreviewFix) -> Unit, onProviderDisabled: () -> Unit): () -> Unit {
            callbacks += Callbacks(onSample, onProviderDisabled)
            registrations++
            activeListeners++
            var removed = false
            return {
                if (!removed) { removed = true; removals++; activeListeners-- }
            }
        }
    }

    private fun gpsStartFix(ageMillis: Long = 100, accuracyMeters: Double = 4.0) = ForegroundGpsPreviewFix(
        GpsSample(29.7174, -95.3905, accuracyMeters, System.currentTimeMillis(),
            (SystemClock.elapsedRealtime() - ageMillis).coerceAtLeast(0)))

    private fun awaitGpsRoot(condition: () -> Boolean) {
        composeTestRule.waitUntil(5_000) {
            composeTestRule.mainClock.advanceTimeByFrame()
            condition()
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun startSpotifyLauncherOnlyReportsDeliberateTapAndRespectsDisabledActions() {
        val actions = RecordedActions()
        var actionsEnabled by mutableStateOf(true)
        var selected by mutableStateOf<PreRunEnergy?>(PreRunEnergy.HIGH)
        var energyChanges = 0
        var spotifyOpens = 0
        var settingsOpens = 0
        composeTestRule.setContent {
            RunStateScreen(
                model = RunUiModel(RunUiState.ReadyToStart), countdownDigit = null,
                onStart = { actions.starts++ }, onCancelCountdown = { actions.cancels++ },
                onRetryInitialization = { actions.initializationRetries++ },
                onRetryStart = { actions.startRetries++ }, onPause = { actions.pauses++ },
                onResume = { actions.resumes++ }, onStop = { actions.stops++ },
                onStartAnother = { actions.startAnothers++ }, actionsEnabled = actionsEnabled,
                selectedPreRunEnergy = selected,
                onSelectPreRunEnergy = { selected = it; energyChanges++ },
                onOpenSpotify = { spotifyOpens++ }, onOpenRunSettings = { settingsOpens++ },
                onViewHistory = { actions.historyOpens++ }, showHistory = true
            )
        }

        val music = composeTestRule.onNodeWithTag(START_OPEN_SPOTIFY_TAG)
            .assertIsDisplayed().assertIsEnabled().assertTextEquals("MUSIC", "SPOTIFY")
        assertEquals(Role.Button, music.fetchSemanticsNode().config[SemanticsProperties.Role])
        composeTestRule.runOnIdle { assertEquals(0, spotifyOpens) }
        music.performTouchInput { click() }
        composeTestRule.runOnIdle {
            assertEquals(1, spotifyOpens)
            assertEquals(PreRunEnergy.HIGH, selected)
            assertEquals(0, energyChanges)
            assertEquals(0, actions.total)
            assertEquals(0, actions.historyOpens)
            assertEquals(0, settingsOpens)
            actionsEnabled = false
        }
        assertEquals("Music observation off", composeTestRule.onNodeWithTag(START_RUN_SETTINGS_TAG)
            .fetchSemanticsNode().config[SemanticsProperties.StateDescription])
        composeTestRule.onNodeWithTag("startEnergyUndo").assertDoesNotExist()
        music.assertIsNotEnabled().performTouchInput { click() }
        composeTestRule.runOnIdle {
            assertEquals(1, spotifyOpens)
            assertEquals(PreRunEnergy.HIGH, selected)
            assertEquals(0, energyChanges)
            assertEquals(0, actions.total)
            actionsEnabled = true
        }
        music.assertIsEnabled()
        composeTestRule.onNodeWithTag(START_RUN_BUTTON_TAG).assertIsEnabled()
        composeTestRule.runOnIdle { assertEquals(1, spotifyOpens) }
    }

    @Test
    fun startReportsMusicPreferenceAndMissingAccessWithoutAToggle() {
        var accessGranted by mutableStateOf(false)
        var musicSelected by mutableStateOf(false)
        composeTestRule.setContent {
            StartRunScreen(
                selectedPreRunEnergy = null,
                onSelectPreRunEnergy = {},
                onStart = {},
                actionsEnabled = true,
                onViewHistory = {},
                showHistory = false,
                musicSelected = musicSelected,
                musicAccessGranted = accessGranted
            )
        }

        composeTestRule.onNodeWithTag(START_MUSIC_TOGGLE_TAG).assertDoesNotExist()
        assertEquals("Music observation off", composeTestRule.onNodeWithTag(START_RUN_SETTINGS_TAG)
            .fetchSemanticsNode().config[SemanticsProperties.StateDescription])
        composeTestRule.onNodeWithText("Music observation off").assertDoesNotExist()
        composeTestRule.runOnIdle { musicSelected = true }
        composeTestRule.onNodeWithText("Music observation needs access").assertExists()
        composeTestRule.runOnIdle { accessGranted = true }
        assertEquals("Music observation on", composeTestRule.onNodeWithTag(START_RUN_SETTINGS_TAG)
            .fetchSemanticsNode().config[SemanticsProperties.StateDescription])
        composeTestRule.onNodeWithText("Music observation needs access").assertDoesNotExist()
    }

    @Test
    fun settingsKeepLocalObservationAndCloudSharingAsSeparateChoices() {
        var observing by mutableStateOf(false)
        var access by mutableStateOf(false)
        var sharingAvailable by mutableStateOf(true)
        var sharing by mutableStateOf(false)
        var choices = 0
        composeTestRule.setContent {
            RunSettingsDialog(
                gpsReady = true, gpsMessage = null,
                musicObservationEnabled = observing, musicAccessGranted = access,
                musicReflectionSharingAvailable = sharingAvailable,
                musicReflectionSharingEnabled = sharing,
                reflectionConnectionLabel = null, demoMode = false, onDismiss = {},
                onRequestLocationSetup = {}, onSetMusicObservation = { observing = it },
                onRequestMusicAccess = {}, onSetDemoMode = {}, onOpenReflectionSetup = {},
                onSetMusicReflectionSharing = { choices++; sharing = it }
            )
        }
        composeTestRule.onNodeWithTag(SETTINGS_MUSIC_SHARING_TAG).performScrollTo().assertIsNotEnabled()
        composeTestRule.runOnIdle { observing = true }
        composeTestRule.runOnIdle { assertFalse(sharing); assertEquals(0, choices) }
        composeTestRule.runOnIdle { access = true }
        composeTestRule.onNodeWithTag(SETTINGS_MUSIC_SHARING_TAG).performClick()
        composeTestRule.runOnIdle { assertTrue(sharing); assertEquals(1, choices) }
        composeTestRule.onNodeWithTag(SETTINGS_MUSIC_SHARING_TAG).performClick()
        composeTestRule.runOnIdle { assertFalse(sharing); assertEquals(2, choices); access = false }
        composeTestRule.onNodeWithText("Music access is needed before any song can be observed or shared.").assertExists()
        composeTestRule.runOnIdle { access = true; sharingAvailable = false; sharing = true }
        composeTestRule.onNodeWithText("A reflection connection is needed to use observed music in reflections.").assertExists()
    }

    @Test
    fun shoeSearchFiltersSavedPairsAndCatalogWithoutSelectingAnything() {
        val selections = mutableListOf<String?>()
        val additions = mutableListOf<Triple<String, String, String?>>()
        val shoes = listOf(
            ShoePairDisplay("road", "ASICS", "Novablast 6", "Morning pair", "12.4 MI"),
            ShoePairDisplay("easy", "HOKA", "Clifton 11", mileageLabel = "8.0 MI")
        )
        composeTestRule.setContent {
            ShoeSelectionSheet(shoes, "road", true, onDismiss = {},
                onSelect = { selections += it }, onAdd = { brand, model, nickname ->
                    additions += Triple(brand, model, nickname)
                }, onArchive = {}, onRestore = {}, onUndoArchive = {})
        }
        saveNativePreview("shoeSheet", "shoe-picker-saved-preview.png")
        val search = composeTestRule.onNodeWithTag("shoeSearch")
        search.performTextReplacement("  nOvAbLaSt  ")
        composeTestRule.onNodeWithTag("shoeSelect:road").assertIsDisplayed()
        composeTestRule.onNodeWithTag("shoeSelect:easy").assertDoesNotExist()
        assertEquals(1, composeTestRule.onAllNodesWithText("Novablast 6").fetchSemanticsNodes().size)
        composeTestRule.runOnIdle { assertTrue(selections.isEmpty()); assertTrue(additions.isEmpty()) }

        search.performTextReplacement("  bRoOkS  ")
        composeTestRule.onNodeWithTag("shoeSelect:road").assertDoesNotExist()
        composeTestRule.onNodeWithTag("shoeList").performScrollToNode(hasText("Ghost 18"))
        composeTestRule.onNodeWithText("Ghost 18").assertIsDisplayed().performClick()
        composeTestRule.runOnIdle {
            assertEquals(listOf(Triple("Brooks", "Ghost 18", null)), additions)
            assertTrue(selections.isEmpty())
        }
        search.performTextReplacement("No such model")
        composeTestRule.onNodeWithTag("shoeList").performScrollToIndex(0)
        composeTestRule.onNodeWithText("No saved shoes match your search.").assertExists()
        composeTestRule.onNodeWithTag("shoeList")
            .performScrollToNode(hasText("No catalog match. Add your pair below."))
        composeTestRule.onNodeWithText("No catalog match. Add your pair below.").assertIsDisplayed()
        composeTestRule.runOnIdle { assertEquals(1, additions.size); assertTrue(selections.isEmpty()) }
    }

    @Test
    fun shoeCustomPairRequiresNamesTrimsFieldsAndWaitsForEnabledSave() {
        var enabled by mutableStateOf(true)
        val additions = mutableListOf<Triple<String, String, String?>>()
        composeTestRule.setContent {
            ShoeSelectionSheet(emptyList(), null, enabled, onDismiss = {}, onSelect = {},
                onAdd = { brand, model, nickname -> additions += Triple(brand, model, nickname) },
                onArchive = {}, onRestore = {}, onUndoArchive = {})
        }
        composeTestRule.onNodeWithTag("shoeSearch").performTextReplacement("My custom shoes")
        composeTestRule.onNodeWithTag("shoeList").performScrollToNode(hasTestTag("shoeCustomToggle"))
        composeTestRule.onNodeWithTag("shoeCustomToggle").performClick()
        val add = composeTestRule.onNodeWithTag("shoeAddCustom")
        add.performScrollTo().assertIsNotEnabled()
        composeTestRule.onNodeWithTag("shoeCustomBrand").performScrollTo().performTextInput("   ")
        composeTestRule.onNodeWithTag("shoeCustomModel").performScrollTo().performTextInput("  Daily Trainer  ")
        add.performScrollTo().assertIsNotEnabled()
        composeTestRule.onNodeWithTag("shoeCustomBrand").performScrollTo().performTextReplacement("  Acme  ")
        composeTestRule.onNodeWithTag("shoeCustomNickname").performScrollTo().performTextInput("  Park pair  ")
        add.performScrollTo().assertIsEnabled()
        composeTestRule.runOnIdle { assertTrue(additions.isEmpty()); enabled = false }
        add.assertIsNotEnabled().performTouchInput { click() }
        composeTestRule.runOnIdle { assertTrue(additions.isEmpty()); enabled = true }
        add.assertIsEnabled().performClick()
        composeTestRule.runOnIdle {
            assertEquals(listOf(Triple("Acme", "Daily Trainer", "Park pair")), additions)
        }
        composeTestRule.onNodeWithTag("shoeCustomNickname").performScrollTo().performTextReplacement("   ")
        add.performScrollTo().performClick()
        composeTestRule.runOnIdle {
            assertEquals(2, additions.size)
            assertEquals(Triple("Acme", "Daily Trainer", null), additions.last())
        }
    }

    @Test
    fun shoeSavedPairAndNoShoesAreExplicitSelectionsAndClosingDoesNotChangeThem() {
        val selections = mutableListOf<String?>()
        var closed = 0
        var otherWrites = 0
        val pair = ShoePairDisplay("road", "ASICS", "Novablast 6", mileageLabel = "12.4 MI")
        composeTestRule.setContent {
            ShoeSelectionSheet(listOf(pair), null, true, onDismiss = { closed++ },
                onSelect = { selections += it }, onAdd = { _, _, _ -> otherWrites++ },
                onArchive = { otherWrites++ }, onRestore = { otherWrites++ },
                onUndoArchive = { otherWrites++ })
        }
        composeTestRule.onNodeWithTag("shoeSelect:road").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("shoeNoSelection").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Close").performClick()
        composeTestRule.runOnIdle {
            assertEquals(listOf("road", null), selections)
            assertEquals(1, closed)
            assertEquals(0, otherWrites)
        }
    }

    @Test
    fun shoeArchiveUndoAndRestoreDoNotAlsoInvokeTheSelectionCallback() {
        val original = ShoePairDisplay("road", "ASICS", "Novablast 6", "Park pair", "12.4 MI")
        var shoes by mutableStateOf(listOf(original))
        var undoLabel by mutableStateOf<String?>(null)
        var archives = 0
        var undos = 0
        var restores = 0
        var selections = 0
        var additions = 0
        composeTestRule.setContent {
            ShoeSelectionSheet(shoes, "road", true, onDismiss = {}, onSelect = { selections++ },
                onAdd = { _, _, _ -> additions++ },
                onArchive = { id ->
                    assertEquals("road", id)
                    archives++
                    shoes = listOf(original.copy(archived = true))
                    undoLabel = "Park pair removed"
                },
                onRestore = { id ->
                    assertEquals("road", id)
                    restores++
                    shoes = listOf(original)
                    undoLabel = null
                },
                onUndoArchive = {
                    undos++
                    shoes = listOf(original)
                    undoLabel = null
                }, undoLabel = undoLabel)
        }
        composeTestRule.onNodeWithTag("shoeSearch").performTextReplacement("ASICS")
        // A real pointer tap checks that the nested Archive button does not select its row.
        composeTestRule.onNodeWithTag("shoeArchive:road").performTouchInput { click() }
        composeTestRule.onNodeWithTag("shoeSelect:road").assertDoesNotExist()
        composeTestRule.onNodeWithTag("shoeUndo").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("shoeSelect:road").assertExists()
        composeTestRule.runOnIdle {
            assertEquals(1, archives); assertEquals(1, undos); assertEquals(0, selections)
        }
        composeTestRule.onNodeWithTag("shoeArchive:road").performTouchInput { click() }
        // Archived models must not reappear as a new catalog pair.
        composeTestRule.onNodeWithText("Novablast 6").assertDoesNotExist()
        composeTestRule.onNodeWithTag("shoeList").performScrollToNode(hasTestTag("shoeArchivedToggle"))
        composeTestRule.onNodeWithTag("shoeArchivedToggle").performClick()
        composeTestRule.onNodeWithTag("shoeList").performScrollToNode(hasTestTag("shoeRestore:road"))
        composeTestRule.onNodeWithTag("shoeRestore:road").assertIsDisplayed().performClick()
        composeTestRule.runOnIdle {
            assertEquals(2, archives); assertEquals(1, undos); assertEquals(1, restores)
            assertEquals(0, selections); assertEquals(0, additions)
        }
    }

    @Test
    fun shoeLoadingDisablesWritesAndLoadErrorOffersRetryWithoutSelection() {
        var loading by mutableStateOf(true)
        var retries = 0
        var writes = 0
        val pair = ShoePairDisplay("road", "ASICS", "Novablast 6", mileageLabel = "12.4 MI")
        composeTestRule.setContent {
            ShoeSelectionSheet(listOf(pair), "road", false, onDismiss = {},
                onSelect = { writes++ }, onAdd = { _, _, _ -> writes++ },
                onArchive = { writes++ }, onRestore = { writes++ }, onUndoArchive = { writes++ },
                loading = loading, error = "Your shoes could not be loaded.", onRetryLoad = { retries++ })
        }
        composeTestRule.onNodeWithText("Loading your shoes…").assertIsDisplayed()
        composeTestRule.onNodeWithTag("shoeSelect:road").assertIsNotEnabled().performTouchInput { click() }
        composeTestRule.onNodeWithTag("shoeArchive:road").assertIsNotEnabled().performTouchInput { click() }
        composeTestRule.onNodeWithTag("shoeNoSelection").performScrollTo().assertIsNotEnabled()
            .performTouchInput { click() }
        composeTestRule.onNodeWithText("Retry").assertIsNotEnabled().performTouchInput { click() }
        composeTestRule.runOnIdle { assertEquals(0, writes); assertEquals(0, retries); loading = false }
        composeTestRule.onNodeWithText("Loading your shoes…").assertDoesNotExist()
        composeTestRule.onNodeWithText("Retry").assertIsEnabled().performClick()
        composeTestRule.onNodeWithTag("shoeSelect:road").assertIsNotEnabled()
        composeTestRule.runOnIdle { assertEquals(0, writes); assertEquals(1, retries) }
    }

    @Test
    fun shoeCustomFormRemainsReachableWithLargeTextInACompactSheet() {
        val additions = mutableListOf<Triple<String, String, String?>>()
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.6f)) {
                ShoeSelectionSheet(emptyList(), null, true, onDismiss = {}, onSelect = {},
                    onAdd = { brand, model, nickname -> additions += Triple(brand, model, nickname) },
                    onArchive = {}, onRestore = {}, onUndoArchive = {},
                    modifier = Modifier.size(360.dp, 480.dp))
            }
        }
        composeTestRule.onNodeWithTag("shoeSearch").performTextReplacement("Personal pair")
        composeTestRule.onNodeWithTag("shoeList").performScrollToNode(hasTestTag("shoeCustomToggle"))
        composeTestRule.onNodeWithTag("shoeCustomToggle").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("shoeCustomBrand").performScrollTo().assertIsDisplayed().performTextInput("Acme")
        composeTestRule.onNodeWithTag("shoeCustomModel").performScrollTo().assertIsDisplayed().performTextInput("Runner")
        composeTestRule.onNodeWithTag("shoeAddCustom").performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.runOnIdle { assertEquals(listOf(Triple("Acme", "Runner", null)), additions) }
    }

    @Test
    fun startShoesControlOpensOnlyShoesAndSelectedLabelPreservesStartBounds() {
        var shoe by mutableStateOf<ShoePairDisplay?>(null)
        var enabled by mutableStateOf(true)
        var shoeOpens = 0
        var starts = 0
        var energyChanges = 0
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            StartRunScreen(selectedPreRunEnergy = null, onSelectPreRunEnergy = { energyChanges++ },
                onStart = { starts++ }, actionsEnabled = enabled, onViewHistory = {}, showHistory = false,
                selectedShoe = shoe, onOpenShoes = { shoeOpens++ },
                modifier = Modifier.fillMaxSize().systemBarsPadding().testTag("shoeStartPreview"))
        }
        composeTestRule.mainClock.advanceTimeByFrame()
        val control = composeTestRule.onNodeWithTag(START_OPEN_SHOES_TAG)
            .assertIsDisplayed().assertIsEnabled().assertTextEquals("SHOES", "ADD PAIR")
        assertEquals(Role.Button, control.fetchSemanticsNode().config[SemanticsProperties.Role])
        assertEquals("No shoes selected", control.fetchSemanticsNode().config[SemanticsProperties.StateDescription])
        val stableTags = listOf(START_RUN_BUTTON_TAG, START_ENERGY_CHOICES_TAG, START_FOOTER_TAG)
        val before = stableTags.associateWith { composeTestRule.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot }
        control.performTouchInput { click() }
        composeTestRule.runOnIdle {
            assertEquals(1, shoeOpens); assertEquals(0, starts); assertEquals(0, energyChanges)
            shoe = ShoePairDisplay("road", "ASICS", "Novablast 6", "My longer daily road pair", "123.4 MI")
        }
        composeTestRule.mainClock.advanceTimeByFrame()
        control.assertTextEquals("SHOES", "My longer daily road pair", "123.4 MI")
        assertEquals("ASICS · Novablast 6 · My longer daily road pair, 123.4 MI",
            control.fetchSemanticsNode().config[SemanticsProperties.StateDescription])
        saveNativePreview("shoeStartPreview", "shoe-selected-start-preview.png")
        stableTags.forEach { tag ->
            assertEquals("Selecting shoes moved $tag", before[tag],
                composeTestRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot)
        }
        composeTestRule.runOnIdle { enabled = false }
        composeTestRule.mainClock.advanceTimeByFrame()
        control.assertIsNotEnabled().performTouchInput { click() }
        composeTestRule.runOnIdle { assertEquals(1, shoeOpens); assertEquals(0, starts); assertEquals(0, energyChanges) }
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

    @Test
    fun countdownStartNowReportsOnlyItsExplicitAction() {
        val actions = renderScreen(RunUiModel(RunUiState.Countdown), countdownDigit = 10)
        composeTestRule.onNodeWithTag(COUNTDOWN_START_NOW_TAG).assertIsDisplayed().performClick()
        assertEquals(1, actions.startsNow)
        assertEquals(1, actions.total)
    }

    @Test
    fun countdownAwaitingLifecycleDoesNotOfferAnEnabledStartNow() {
        val actions = renderScreen(RunUiModel(RunUiState.Countdown), countdownDigit = null)
        composeTestRule.onNodeWithTag(COUNTDOWN_START_NOW_TAG).assertIsNotEnabled().performClick()
        assertEquals(0, actions.total)
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

        composeTestRule.onNodeWithTag(RUN_COMPLETE_SCREEN_TAG).assertIsDisplayed()
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
        composeTestRule.onNodeWithText("Fixture metrics").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("SAMPLE RUN · SIMULATED DISTANCE").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Sample distance increases automatically, even when you are still.")
            .performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Distance").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag(ACTIVE_RUN_DISTANCE_TAG).assertTextEquals("0.11 mi")
        composeTestRule.onNodeWithText("Active time").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag(ACTIVE_RUN_ACTIVE_TAG).assertTextEquals("00:01:00")
        composeTestRule.onNodeWithText("Total incl. breaks").assertDoesNotExist()
        composeTestRule.onNodeWithTag(ACTIVE_RUN_ELAPSED_TAG).assertDoesNotExist()
        composeTestRule.onNodeWithText("Avg pace").assertIsDisplayed()
        composeTestRule.onNodeWithTag(ACTIVE_RUN_PACE_TAG).assertTextEquals("8:56 /mi")
    }

    /** Running discloses and renders fixture metrics. */
    @Test
    fun runningShowsTheValuesAndFixtureDisclosure() {
        renderScreen(model = RunUiModel(RunUiState.ActiveRunning), metrics = fixtureMetrics())
        assertFixtureMetricsDisplayed()
        composeTestRule.onNodeWithText("Active time and distance are paused.")
            .assertDoesNotExist()
    }

    /** Paused discloses and renders fixture metrics. */
    @Test
    fun pausedShowsTheValuesAndFixtureDisclosure() {
        renderScreen(model = RunUiModel(RunUiState.ActivePaused), metrics = fixtureMetrics())
        assertFixtureMetricsDisplayed()
        composeTestRule.onNodeWithText("Active time and distance are paused.")
            .performScrollTo().assertIsDisplayed()
    }

    @Test
    fun pausedMainTimerStaysFixedAndNeverDisplaysWallTimeIncludingBreaks() {
        var total by mutableStateOf("00:02:00")
        composeTestRule.setContent {
            ActiveRunScreen(true, fixtureMetrics().copy(elapsed = total), false, true, {}, {}, {})
        }
        composeTestRule.onNodeWithTag(ACTIVE_RUN_ACTIVE_TAG).performScrollTo()
            .assertTextEquals("00:01:00")
        val active = composeTestRule.onNodeWithTag(ACTIVE_RUN_ACTIVE_TAG).fetchSemanticsNode()
        val panel = composeTestRule.onNodeWithTag(ACTIVE_RUN_METRICS_TAG).fetchSemanticsNode()
        assertTrue(active.positionInRoot.y >= panel.positionInRoot.y)
        assertTrue(active.positionInRoot.y + active.size.height <= panel.positionInRoot.y + panel.size.height)
        composeTestRule.runOnIdle { total = "00:02:45" }
        composeTestRule.onNodeWithTag(ACTIVE_RUN_ACTIVE_TAG).assertTextEquals("00:01:00")
        composeTestRule.onNodeWithTag(ACTIVE_RUN_ELAPSED_TAG).assertDoesNotExist()
        composeTestRule.onNodeWithText("00:02:45").assertDoesNotExist()
    }

    @Test
    fun pausedPortraitKeepsAllMetricsAboveControlsWithoutScrolling() {
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1f)) {
                ActiveRunScreen(true, fixtureMetrics(), false, true, {}, {}, {},
                    modifier = Modifier.size(360.dp, 640.dp), isGpsRun = true,
                    gpsServicePresent = true)
            }
        }
        val controlTop = composeTestRule.onNodeWithText(RESUME)
            .assertIsDisplayed().fetchSemanticsNode().positionInRoot.y
        for (tag in listOf(ACTIVE_RUN_DISTANCE_TAG, ACTIVE_RUN_ACTIVE_TAG,
            ACTIVE_RUN_PACE_TAG)) {
            val node = composeTestRule.onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode()
            assertTrue("$tag must stay above the paused controls",
                node.positionInRoot.y + node.size.height <= controlTop)
        }
        composeTestRule.onNodeWithTag(HOLD_TO_STOP_TAG).assertIsDisplayed()
        saveNativePreview(ACTIVE_RUN_SCREEN_TAG, "debrief-paused.png")
    }

    @Test
    fun shortRunDiscardReturnsToStartWithoutCompletionOrSpeech() = shortRunDecisionThroughRoot(save = false)

    @Test
    fun shortRunSaveUsesTheNormalDurableCompletion() = shortRunDecisionThroughRoot(save = true)

    @Test
    fun shortRunDecisionSurvivesScreenRestorationWithoutSaving() =
        shortRunDecisionThroughRoot(save = false, restore = true)

    @Test
    fun completionRootWaitsForExplicitNotSureBeforeVoiceAndRestoresItsAnswer() =
        shortRunDecisionThroughRoot(save = true, verifyReflectionAcknowledgement = true)

    /** Real root + real isolated Room: these actions never touch the phone's runstate.db. */
    private fun shortRunDecisionThroughRoot(save: Boolean, restore: Boolean = false,
        verifyReflectionAcknowledgement: Boolean = false) {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), RunStateDatabase::class.java).build()
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        var time = 1_756_000_000_000L
        val clock = object : Clock() {
            override fun getZone(): ZoneId = ZoneOffset.UTC
            override fun withZone(zone: ZoneId): Clock = this
            override fun instant(): Instant = Instant.ofEpochMilli(time)
        }
        val id = UUID.randomUUID()
        val coordinator = RunSessionCoordinator(database.runDao(),
            PreparedRunFactory(clock, { ZoneOffset.UTC }, { id }), appScope,
            database.locationDao())
        val exactReflection = "The prepared reply waits for the runner's check-in."
        val reflectionOwner = if (verifyReflectionAcknowledgement)
            com.runstate.mobile.reflection.RunReflectionCoordinator(database.runDao(),
                com.runstate.mobile.reflection.RoomReflectionStore(database.reflectionDao()),
                com.runstate.mobile.reflection.ReflectionGateway {
                    com.runstate.mobile.reflection.ReflectionCandidates("Spent reply.", "Good reply.",
                        "Powered reply.", exactReflection)
                }, appScope, quietWindowMillis = 0L, nowMillis = { time }) else null
        var shown by mutableStateOf(true)
        var receipts = 0
        var reflections = 0
        val silentSpeaker = object : ReflectionSpeaker {
            override fun speak(text: String) { reflections++ }
            override fun speakReceipt(text: String, onFinished: () -> Unit) {
                if (text != "How’s your energy now?") receipts++
                onFinished()
            }
            override fun stop() = Unit
            override fun close() = Unit
        }
        try {
            runBlocking {
                coordinator.initialize()
                coordinator.beginCountdown()
                coordinator.requestStart().await()
                time += 5_000L
                coordinator.requestAction(RunActionKind.PAUSE).await()
            }
            val restoration = StateRestorationTester(composeTestRule)
            restoration.setContent {
                if (shown) RunJourneyRoot(coordinator, reflectionOwner, database,
                    remember { MutableStateFlow(RunTrackingServiceState.Idle) },
                    LocalLifecycleOwner.current.lifecycle,
                    completionSpeakerFactory = { silentSpeaker },
                    reflectionConnection = com.runstate.mobile.ReflectionConnectionState.Local)
            }
            composeTestRule.waitUntil(5_000L) {
                composeTestRule.onAllNodesWithText(RESUME).fetchSemanticsNodes().isNotEmpty()
            }
            if (!save && !restore) saveNativePreview(ACTIVE_RUN_SCREEN_TAG, "demo-active-paused.png")
            composeTestRule.mainClock.autoAdvance = false
            composeTestRule.onNodeWithTag(HOLD_TO_STOP_TAG).performTouchInput { down(center) }
            composeTestRule.mainClock.advanceTimeBy(HOLD_TO_STOP_MILLIS + 100L)
            composeTestRule.onNodeWithTag(HOLD_TO_STOP_TAG).performTouchInput { up() }
            composeTestRule.mainClock.autoAdvance = true
            composeTestRule.waitUntil(5_000L) {
                composeTestRule.onAllNodesWithText("Save this short run?").fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals(StoredRunState.PAUSED, runBlocking { database.runDao().findById(id.toString()) }?.state)
            assertEquals(0, receipts)
            if (!save && !restore) {
                val bitmap = composeTestRule.onNode(isDialog()).captureToImage().asAndroidBitmap()
                File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
                    "demo-short-run-choice.png").outputStream().use { output ->
                    assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
                }
            }
            if (restore) {
                restoration.emulateSavedInstanceStateRestore()
                composeTestRule.waitUntil(5_000L) {
                    composeTestRule.onAllNodesWithText("Save this short run?").fetchSemanticsNodes().isNotEmpty()
                }
                assertEquals(StoredRunState.PAUSED,
                    runBlocking { database.runDao().findById(id.toString()) }?.state)
                assertEquals(0, receipts)
            }
            // Backing out never ends the run or deletes it. A later hold can decide again.
            composeTestRule.onNodeWithText("Keep paused").performClick()
            composeTestRule.onNodeWithText("Save this short run?").assertDoesNotExist()
            composeTestRule.onNodeWithText(RESUME).assertIsDisplayed()
            composeTestRule.mainClock.autoAdvance = false
            composeTestRule.onNodeWithTag(HOLD_TO_STOP_TAG).performTouchInput { down(center) }
            composeTestRule.mainClock.advanceTimeBy(HOLD_TO_STOP_MILLIS + 100L)
            composeTestRule.onNodeWithTag(HOLD_TO_STOP_TAG).performTouchInput { up() }
            composeTestRule.mainClock.autoAdvance = true
            composeTestRule.waitUntil(5_000L) {
                composeTestRule.onAllNodesWithText("Save this short run?").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText(if (save) "Save run" else "Discard run").performClick()
            composeTestRule.waitUntil(5_000L) {
                composeTestRule.onAllNodesWithText(if (save) "RUN COMPLETE" else READY).fetchSemanticsNodes().isNotEmpty()
            }
            val stored = runBlocking { database.runDao().findById(id.toString()) }
            assertEquals(if (save) StoredRunState.COMPLETED else null, stored?.state)
            assertEquals(if (save) 1 else 0, runBlocking { database.runDao().countRuns() })
            if (save) composeTestRule.waitUntil(5_000L) { receipts == 1 }
            assertEquals(if (save) 1 else 0, receipts)
            assertEquals(0, reflections)
            if (save) {
                if (verifyReflectionAcknowledgement) {
                    composeTestRule.waitUntil(5_000L) {
                        reflectionOwner?.state?.value is RunReflectionState.Ready
                    }
                    composeTestRule.onNodeWithTag(RUN_COMPLETE_REFLECTION_TAG).assertDoesNotExist()
                    composeTestRule.onNodeWithTag(RUN_COMPLETE_ENERGY_TAG).assertExists()
                    composeTestRule.onNodeWithTag(RUN_COMPLETE_SAVE_ACTIVITY_TAG).assertIsEnabled()
                    composeTestRule.runOnIdle { assertEquals(0, reflections) }
                    composeTestRule.onNodeWithText("Not sure").performScrollTo().performClick()
                    composeTestRule.waitUntil(5_000L) { reflections == 1 }
                    assertEquals(ReflectionBranch.NO_SELECTION, runBlocking {
                        database.reflectionDao().findByRunId(id.toString()) }?.postRunEnergy)
                    restoration.emulateSavedInstanceStateRestore()
                    composeTestRule.waitUntil(5_000L) {
                        composeTestRule.onAllNodesWithTag(RUN_COMPLETE_REFLECTION_TEXT_TAG)
                            .fetchSemanticsNodes().isNotEmpty()
                    }
                    composeTestRule.onNodeWithTag(RUN_COMPLETE_ENERGY_TAG).assertDoesNotExist()
                    composeTestRule.onNodeWithTag(RUN_COMPLETE_REFLECTION_TEXT_TAG)
                        .performScrollTo().assertTextEquals(exactReflection)
                    composeTestRule.runOnIdle { assertEquals(1, reflections) }
                }
                assertFalse(requireNotNull(stored).activityFinalized)
                composeTestRule.onNodeWithText("Something else? Add a note").performScrollTo().performClick()
                composeTestRule.onNodeWithTag(RUN_COMPLETE_NOTE_TAG).performScrollTo()
                    .performTextInput("Legs tired, mind clear")
                composeTestRule.onNodeWithTag(RUN_COMPLETE_NOTE_SAVE_TAG).performScrollTo().performClick()
                composeTestRule.waitUntil(5_000L) {
                    runBlocking { database.runDao().findById(id.toString()) }?.postRunNote == "Legs tired, mind clear"
                }
                composeTestRule.onNodeWithTag(RUN_COMPLETE_SAVE_ACTIVITY_TAG).assertIsEnabled().performClick()
                composeTestRule.waitUntil(5_000L) {
                    composeTestRule.onAllNodesWithTag(RUN_ACTIVITY_RECAP_TAG).fetchSemanticsNodes().isNotEmpty()
                }
                assertTrue(runBlocking { database.runDao().findById(id.toString()) }?.activityFinalized == true)
                composeTestRule.onNodeWithText("Legs tired, mind clear").performScrollTo().assertIsDisplayed()
                composeTestRule.onNodeWithTag(RUN_COMPLETE_HISTORY_TAG).performScrollTo().performClick()
                composeTestRule.waitUntil(5_000L) {
                    composeTestRule.onAllNodesWithTag(RUN_HISTORY_ROW_TAG_PREFIX + id).fetchSemanticsNodes().isNotEmpty()
                }
                composeTestRule.onNodeWithText("Back to run").performClick()
                composeTestRule.onNodeWithTag(RUN_ACTIVITY_RECAP_TAG).assertIsDisplayed()
                composeTestRule.onNodeWithText("Done").performClick()
                composeTestRule.waitUntil(5_000L) {
                    composeTestRule.onAllNodesWithTag(START_RUN_SCREEN_TAG).fetchSemanticsNodes().isNotEmpty()
                }
                assertEquals(1, runBlocking { database.runDao().countRuns() })
                assertEquals(1, receipts)
            }
        } finally {
            composeTestRule.runOnIdle { shown = false }
            composeTestRule.waitForIdle()
            appScope.cancel()
            database.close()
        }
    }

    /** Saved gives the frozen metrics the dedicated Run Complete hierarchy. */
    @Test
    fun savedShowsTheValuesAndFixtureDisclosure() {
        renderScreen(model = RunUiModel(RunUiState.Saved), metrics = fixtureMetrics())

        composeTestRule.onNodeWithTag(RUN_COMPLETE_SCREEN_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(RUN_COMPLETE_DISTANCE_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(RUN_COMPLETE_VISUALIZER_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("FIXTURE METRICS").assertIsDisplayed()
        composeTestRule.onNodeWithText("0.11").assertIsDisplayed()
        composeTestRule.onNodeWithText("MI").assertIsDisplayed()
        composeTestRule.onNodeWithText("TIME").assertIsDisplayed()
        composeTestRule.onNodeWithText("00:02:00").assertDoesNotExist()
        composeTestRule.onNodeWithText("PACE").assertIsDisplayed()
        composeTestRule.onNodeWithText("8:56").assertIsDisplayed()
        composeTestRule.onNodeWithText("/MI").assertIsDisplayed()
        composeTestRule.onNodeWithText("ACTIVE").assertDoesNotExist()
        composeTestRule.onNodeWithText("00:01:00").assertIsDisplayed()
    }

    /** Real-phone fonts and changing timer digits must not place decoration over saved facts. */
    @Test
    fun savedPhoneWidthKeepsDecorationClearAndMetricLabelsOnOneLine() {
        var metrics by mutableStateOf(fixtureMetrics().copy(
            elapsed = "00:00:34", active = "00:00:04", distance = "0.03 mi"
        ))
        var fontScale by mutableStateOf(1f)
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                RunCompleteScreen(
                    metrics = metrics,
                    reflection = null,
                    onRetryReflection = {},
                    onStartAnother = {},
                    actionsEnabled = true,
                    modifier = Modifier.size(width = 440.dp, height = 840.dp)
                )
            }
        }

        fun assertFactsUncovered() {
            val distance = composeTestRule.onNodeWithTag(RUN_COMPLETE_DISTANCE_TAG)
                .fetchSemanticsNode().boundsInRoot
            val compactMetrics = composeTestRule.onNodeWithTag(RUN_COMPLETE_METRICS_TAG)
                .fetchSemanticsNode().boundsInRoot
            val mark = composeTestRule.onNodeWithTag(RUN_COMPLETE_VISUALIZER_TAG)
                .fetchSemanticsNode().boundsInRoot
            assertFalse("Completion mark covers distance or its unit", mark.overlaps(distance))
            assertFalse("Completion mark covers time, pace or active time", mark.overlaps(compactMetrics))
            assertFalse("Distance and compact metrics overlap", distance.overlaps(compactMetrics))
            listOf("TIME", "PACE", "MI", metrics.active).forEach { text ->
                val layouts = mutableListOf<TextLayoutResult>()
                composeTestRule.onNodeWithText(text).performSemanticsAction(
                    SemanticsActions.GetTextLayoutResult
                ) { action -> action(layouts) }
                val layout = layouts.single()
                assertEquals("$text wraps onto another line", 1, layout.lineCount)
                assertEquals("$text omits characters", text.length, layout.getLineEnd(0, visibleEnd = true))
                val characterBounds = text.indices.map(layout::getBoundingBox)
                val textLeft = characterBounds.minOf { it.left }
                val textRight = characterBounds.maxOf { it.right }
                // Text semantics may reconstruct a paragraph at the parent's maximum
                // width while retaining the measured Text size. Check every letter's
                // bounds rather than that reconstructed paragraph's container width.
                assertTrue(
                    "$text letters are clipped: bounds=$textLeft..$textRight, width=${layout.size.width}, " +
                        "paragraphWidth=${layout.multiParagraph.width}",
                    textLeft >= 0f && textRight <= layout.size.width
                )
                assertFalse(
                    "$text is clipped vertically: size=${layout.size}, paragraphHeight=${layout.multiParagraph.height}",
                    layout.didOverflowHeight
                )
            }
        }

        assertFactsUncovered()
        composeTestRule.runOnIdle {
            metrics = metrics.copy(elapsed = "00:10:48", active = "00:08:44", distance = "12.34 mi")
            fontScale = 1.3f
        }
        assertFactsUncovered()
    }

    @Test
    fun savedShowsTheReflectionPreparingStateWithoutSkipOrUnconfirmedFakeLabel() {
        renderScreen(model = RunUiModel(RunUiState.Saved), metrics = fixtureMetrics())

        composeTestRule.onNodeWithTag(RUN_COMPLETE_REFLECTION_TAG)
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("FAKE BACKEND · TEST DATA").assertDoesNotExist()
        composeTestRule.onNodeWithText("Preparing your reflection…").assertIsDisplayed()
        composeTestRule.onNodeWithText("Your run is already saved.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Skip").assertDoesNotExist()
    }

    @Test
    fun savedShowsTheExactSelectedFakeText() {
        val exact = "  [FAKE] Exact no-selection candidate.  "
        renderScreen(
            model = RunUiModel(RunUiState.Saved),
            metrics = fixtureMetrics(),
            reflection = RunCompleteReflectionDisplay.Ready(exact),
            isTestData = true
        )

        composeTestRule.onNodeWithTag(RUN_COMPLETE_REFLECTION_TEXT_TAG)
            .performScrollTo()
            .assertTextEquals(exact)
    }

    @Test
    fun savedEnergyBranchesStayAvailableBesideACompletedReply() {
        val exact = "  Exact stored reply.  "
        val actions = renderScreen(
            model = RunUiModel(RunUiState.Saved),
            metrics = fixtureMetrics(),
            reflection = RunCompleteReflectionDisplay.Ready(exact),
            energyEnabled = true,
            showHistory = true
        )

        listOf("SPENT", "FEELING GOOD", "POWERED UP").forEach { label ->
            composeTestRule.onNodeWithText("Energy +").performScrollTo().performClick()
            composeTestRule.onNodeWithText(label).performScrollTo().performClick()
            composeTestRule.onNodeWithTag(RUN_COMPLETE_ENERGY_TAG).assertDoesNotExist()
        }
        assertEquals(listOf(
            ReflectionBranch.SPENT,
            ReflectionBranch.FEELING_GOOD,
            ReflectionBranch.POWERED_UP
        ), actions.energyAnswers)
        composeTestRule.onNodeWithTag(RUN_COMPLETE_REFLECTION_TEXT_TAG)
            .performScrollTo().assertTextEquals(exact)
        composeTestRule.onNodeWithTag(RUN_COMPLETE_HISTORY_TAG)
            .performScrollTo().performClick()
        assertEquals(1, actions.historyOpens)
    }

    @Test
    fun selectedEnergyIlluminatesStartAndFreshDraftRestoresItsIdleAppearance() {
        composeTestRule.mainClock.autoAdvance = false
        var selected by mutableStateOf<PreRunEnergy?>(null)
        composeTestRule.setContent {
            StartRunScreen(selected, { selected = it }, {}, true, {}, false)
        }
        composeTestRule.mainClock.advanceTimeByFrame()
        fun dialPixels(): IntArray {
            val image = composeTestRule.onNodeWithTag(START_RUN_BUTTON_TAG).captureToImage()
            return IntArray(image.width * image.height).also { image.readPixels(it) }
        }
        val idle = dialPixels()
        composeTestRule.onNodeWithTag(START_ENERGY_HIGH_TAG).performClick()
        composeTestRule.mainClock.advanceTimeBy(1_900L)
        composeTestRule.onNodeWithTag(START_RUN_BUTTON_TAG).assertIsEnabled()
        assertFalse("Energy must visibly illuminate the actual Start control", idle.contentEquals(dialPixels()))
        composeTestRule.onNodeWithTag("startEnergyUndo").assertDoesNotExist()
        assertEquals(PreRunEnergy.HIGH, selected)
        // This is a new test-owned draft, not an interaction available on Start.
        composeTestRule.runOnIdle { selected = null }
        composeTestRule.mainClock.advanceTimeBy(800L)
        assertTrue("A fresh draft must return Start to its original appearance", idle.contentEquals(dialPixels()))
        assertEquals(null, selected)
    }

    @Test
    fun unansweredCompletionKeepsChoicesAndSaveAvailableWithoutAnyReflectionPanel() {
        var display by mutableStateOf<RunCompleteReflectionDisplay>(RunCompleteReflectionDisplay.Preparing)
        var saved = 0
        composeTestRule.setContent {
            RunCompleteScreen(fixtureMetrics(), display, {}, {}, true,
                energyEnabled = true, onSaveActivity = { saved++ })
        }
        for (state in listOf(RunCompleteReflectionDisplay.Preparing,
            RunCompleteReflectionDisplay.Ready("Background timeout already finalized this exact reply."),
            RunCompleteReflectionDisplay.Failed, RunCompleteReflectionDisplay.ConnectionNeeded)) {
            composeTestRule.runOnIdle { display = state }
            composeTestRule.onNodeWithTag(RUN_COMPLETE_REFLECTION_TAG).assertDoesNotExist()
            composeTestRule.onNodeWithTag(RUN_COMPLETE_ENERGY_TAG).assertExists()
            composeTestRule.onNodeWithText("Energy +").assertDoesNotExist()
            composeTestRule.onNodeWithTag(RUN_COMPLETE_SAVE_ACTIVITY_TAG).assertIsEnabled()
        }
        composeTestRule.onNodeWithTag(RUN_COMPLETE_SAVE_ACTIVITY_TAG).performClick()
        assertEquals(1, saved)
        composeTestRule.runOnIdle { display = RunCompleteReflectionDisplay.Ready("Ready before Not sure.") }
        saveNativePreview(RUN_COMPLETE_SCREEN_TAG, "energy-reflection-hidden.png")
        composeTestRule.onNodeWithText("Not sure").performScrollTo().performClick()
        composeTestRule.onNodeWithTag(RUN_COMPLETE_ENERGY_TAG).assertDoesNotExist()
        composeTestRule.onNodeWithTag(RUN_COMPLETE_REFLECTION_TEXT_TAG).performScrollTo()
            .assertTextEquals("Ready before Not sure.")
    }

    @Test
    fun alreadyPreparedReflectionStillWaitsForFirstEnergyIntakeAndVoiceAcknowledgement() {
        composeTestRule.mainClock.autoAdvance = false
        var selected by mutableStateOf<ReflectionBranch?>(null)
        var held by mutableStateOf(false)
        var spokenId by mutableStateOf<String?>(null)
        val spoken = mutableListOf<String>()
        val exact = "Already prepared, presented only after the intake."
        val id = "prepared-before-choice"
        val speaker = object : ReflectionSpeaker {
            override fun speak(text: String) { spoken += text }
            override fun stop() = Unit
            override fun close() = Unit
        }
        composeTestRule.setContent {
            RunCompleteSpeechEffect(id, id, spokenId, RunReflectionState.Ready(id, exact),
                completionVisible = selected != null && !held, speakerFactory = { speaker },
                onSpoken = { spokenId = it })
            RunCompleteScreen(fixtureMetrics(), RunCompleteReflectionDisplay.Ready(exact), {}, {}, true,
                selectedEnergy = selected, energyEnabled = true,
                onSelectEnergy = { assertTrue(held); selected = it },
                onReflectionPresentationHoldChange = { held = it })
        }
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.onNodeWithTag(RUN_COMPLETE_REFLECTION_TAG).assertDoesNotExist()
        assertTrue(spoken.isEmpty())
        composeTestRule.onNodeWithText("FEELING GOOD").performClick()
        composeTestRule.mainClock.advanceTimeBy(500L)
        composeTestRule.onNodeWithTag(RUN_COMPLETE_ENERGY_INTAKE_TAG).assertExists()
        composeTestRule.onNodeWithTag(RUN_COMPLETE_REFLECTION_TAG).assertDoesNotExist()
        assertTrue(spoken.isEmpty())
        composeTestRule.mainClock.advanceTimeBy(1400L)
        composeTestRule.onNodeWithTag(RUN_COMPLETE_REFLECTION_TEXT_TAG).assertTextEquals(exact)
        saveNativePreview(RUN_COMPLETE_SCREEN_TAG, "energy-reflection-revealed.png")
        composeTestRule.runOnIdle { assertEquals(listOf(exact), spoken) }
    }

    @Test
    fun recoveredExplicitNotSureShowsSavedReflectionWithoutRepeatingEnergyOrIntake() {
        composeTestRule.setContent {
            RunCompleteScreen(fixtureMetrics(), RunCompleteReflectionDisplay.Ready("Exact restored reply."),
                {}, {}, true, selectedEnergy = ReflectionBranch.NO_SELECTION, energyEnabled = true)
        }
        composeTestRule.onNodeWithTag(RUN_COMPLETE_ENERGY_TAG).assertDoesNotExist()
        composeTestRule.onNodeWithTag(RUN_COMPLETE_ENERGY_INTAKE_TAG).assertDoesNotExist()
        composeTestRule.onNodeWithText("Not sure").assertExists()
        composeTestRule.onNodeWithTag(RUN_COMPLETE_REFLECTION_TEXT_TAG).performScrollTo()
            .assertTextEquals("Exact restored reply.")
    }

    @Test
    fun completionAndRecapUseOnlyActiveTimeAndNeverFallBackToWallTime() {
        var recap by mutableStateOf(false)
        var active by mutableStateOf("00:05:01")
        composeTestRule.setContent {
            val metrics = fixtureMetrics().copy(elapsed = "99:59:59", active = active)
            if (recap) RunActivityRecapScreen(
                RunHistoryItem("time-proof", "Today", metrics, null, null, null, false),
                null, {}, {})
            else RunCompleteScreen(metrics, null, {}, {}, true)
        }
        for (showRecap in listOf(false, true)) {
            composeTestRule.runOnIdle { recap = showRecap; active = "00:05:01" }
            composeTestRule.onNodeWithText("TIME").assertExists()
            composeTestRule.onNodeWithText("00:05:01").assertExists()
            composeTestRule.onNodeWithText("99:59:59").assertDoesNotExist()
            composeTestRule.onNodeWithText("ACTIVE").assertDoesNotExist()
            composeTestRule.runOnIdle { active = "—" }
            composeTestRule.onNodeWithText("00:05:01").assertDoesNotExist()
            composeTestRule.onNodeWithText("99:59:59").assertDoesNotExist()
            composeTestRule.onNodeWithText("TIME").assertExists()
        }
    }

    @Test
    fun completionAgentReceivesEnergyBeforeShowingAndSpeakingTheSavedReply() {
        composeTestRule.mainClock.autoAdvance = false
        var selected by mutableStateOf<ReflectionBranch?>(null)
        var ready by mutableStateOf(false)
        val answers = mutableListOf<ReflectionBranch>()
        var starts = 0
        var held by mutableStateOf(false)
        var spokenRunId by mutableStateOf<String?>(null)
        val spoken = mutableListOf<String>()
        val runId = "controlled-completion"
        val exact = "The saved reflection appears after the Energy intake."
        val speaker = object : ReflectionSpeaker {
            override fun speak(text: String) { spoken += text }
            override fun stop() = Unit
            override fun close() = Unit
        }
        composeTestRule.setContent {
            RunCompleteSpeechEffect(runId, runId, spokenRunId,
                if (ready) RunReflectionState.Ready(runId, exact) else RunReflectionState.Preparing(runId),
                completionVisible = !held, speakerFactory = { speaker },
                onSpoken = { spokenRunId = it })
            RunCompleteScreen(
                metrics = fixtureMetrics(),
                reflection = if (ready) RunCompleteReflectionDisplay.Ready(exact)
                    else RunCompleteReflectionDisplay.Preparing,
                onRetryReflection = {}, onStartAnother = { starts++ }, actionsEnabled = true,
                modifier = Modifier.systemBarsPadding(),
                selectedEnergy = selected, energyEnabled = true,
                onSelectEnergy = { assertTrue(held); answers += it; selected = it; ready = true },
                onReflectionPresentationHoldChange = { held = it }
            )
        }
        composeTestRule.mainClock.advanceTimeByFrame()
        val ring = composeTestRule.onNodeWithTag(RUN_COMPLETE_VISUALIZER_TAG)
        val initialBounds = ring.fetchSemanticsNode().boundsInRoot
        val first = ring.captureToImage()
        val firstPixels = IntArray(first.width * first.height).also(first::readPixels)
        composeTestRule.mainClock.advanceTimeBy(400L)
        val later = ring.captureToImage()
        val laterPixels = IntArray(later.width * later.height).also(later::readPixels)
        assertEquals(initialBounds, ring.fetchSemanticsNode().boundsInRoot)
        assertFalse("Completion must draw the same traveling agent wave as Start",
            firstPixels.contentEquals(laterPixels))

        composeTestRule.onNodeWithText("FEELING GOOD").performClick()
        composeTestRule.runOnIdle { assertEquals(listOf(ReflectionBranch.FEELING_GOOD), answers) }
        composeTestRule.mainClock.advanceTimeBy(200L)
        composeTestRule.onNodeWithTag(RUN_COMPLETE_REFLECTION_TEXT_TAG).assertDoesNotExist()
        assertTrue(spoken.isEmpty())
        val flight = composeTestRule.onNodeWithTag(RUN_COMPLETE_ENERGY_INTAKE_TAG)
        flight.assertIsDisplayed()
        saveNativePreview(RUN_COMPLETE_SCREEN_TAG, "demo-completion-intake-a.png")
        val before = flight.fetchSemanticsNode().boundsInRoot.center
        composeTestRule.mainClock.advanceTimeBy(600L)
        val after = flight.fetchSemanticsNode().boundsInRoot.center
        composeTestRule.onNodeWithTag(RUN_COMPLETE_REFLECTION_TEXT_TAG).assertDoesNotExist()
        assertTrue(spoken.isEmpty())
        saveNativePreview(RUN_COMPLETE_SCREEN_TAG, "demo-completion-intake-b.png")
        val target = ring.fetchSemanticsNode().boundsInRoot.center
        assertTrue("The chosen Energy tile travels toward the measured agent",
            (after - target).getDistance() < (before - target).getDistance())
        composeTestRule.onNodeWithText(START_ANOTHER).assertIsEnabled().performClick()
        assertEquals(1, starts)
        composeTestRule.mainClock.advanceTimeBy(1000L)
        flight.assertDoesNotExist()
        composeTestRule.mainClock.advanceTimeByFrame()
        assertEquals(listOf(ReflectionBranch.FEELING_GOOD), answers)
        composeTestRule.onNodeWithTag(RUN_COMPLETE_REFLECTION_TEXT_TAG).assertTextEquals(exact)
        composeTestRule.runOnIdle { assertEquals(listOf(exact), spoken) }
    }

    @Test
    fun completionAnswerStaysHiddenWhileAResponseIsStillPreparing() {
        composeTestRule.mainClock.autoAdvance = false
        var ready by mutableStateOf(false)
        val answers = mutableListOf<ReflectionBranch>()
        composeTestRule.setContent {
            RunCompleteScreen(fixtureMetrics(), if (ready) RunCompleteReflectionDisplay.Ready("Exact late reply.")
                else RunCompleteReflectionDisplay.Preparing, {}, {}, true,
                energyEnabled = true, onSelectEnergy = { answers += it })
        }
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.onNodeWithText("SPENT").performClick()
        composeTestRule.mainClock.advanceTimeBy(2000L)
        composeTestRule.onNodeWithTag(RUN_COMPLETE_ENERGY_TAG).assertDoesNotExist()
        composeTestRule.onNodeWithText("Preparing your reflection…").assertExists()
        assertEquals(listOf(ReflectionBranch.SPENT), answers)
        composeTestRule.runOnIdle { ready = true }
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.onNodeWithTag(RUN_COMPLETE_REFLECTION_TEXT_TAG).assertTextEquals("Exact late reply.")
        composeTestRule.onNodeWithTag(RUN_COMPLETE_ENERGY_TAG).assertDoesNotExist()
    }

    @Test
    fun interruptingInitialEnergyIntakeReleasesItsPresentationHold() {
        composeTestRule.mainClock.autoAdvance = false
        val owner = TestLifecycle()
        moveTo(owner, Lifecycle.State.RESUMED)
        var selected by mutableStateOf<ReflectionBranch?>(null)
        var held = false
        var answers = 0
        composeTestRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                RunCompleteScreen(fixtureMetrics(), RunCompleteReflectionDisplay.Preparing, {}, {}, true,
                    selectedEnergy = selected, energyEnabled = true,
                    onSelectEnergy = { selected = it; answers++ },
                    onReflectionPresentationHoldChange = { held = it })
            }
        }
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.onNodeWithText("SPENT").performClick()
        composeTestRule.mainClock.advanceTimeBy(600L)
        assertTrue(held)
        moveTo(owner, Lifecycle.State.CREATED)
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.runOnIdle { assertFalse(held) }
        moveTo(owner, Lifecycle.State.RESUMED)
        composeTestRule.mainClock.advanceTimeBy(2000L)
        composeTestRule.onNodeWithTag(RUN_COMPLETE_ENERGY_INTAKE_TAG).assertDoesNotExist()
        composeTestRule.onNodeWithTag(RUN_COMPLETE_ENERGY_TAG).assertDoesNotExist()
        assertEquals(ReflectionBranch.SPENT, selected)
        assertEquals(1, answers)
    }

    @Test
    fun restoredAnsweredPreparationDoesNotShowInitialChoicesOrReplayIntake() {
        composeTestRule.setContent {
            RunCompleteScreen(fixtureMetrics(), RunCompleteReflectionDisplay.Preparing, {}, {}, true,
                selectedEnergy = ReflectionBranch.FEELING_GOOD, energyEnabled = true)
        }
        composeTestRule.onNodeWithTag(RUN_COMPLETE_ENERGY_TAG).assertDoesNotExist()
        composeTestRule.onNodeWithTag(RUN_COMPLETE_ENERGY_INTAKE_TAG).assertDoesNotExist()
        composeTestRule.onNodeWithText("Preparing your reflection…").assertExists()
        composeTestRule.onNodeWithText("Energy +").assertExists()
    }

    @Test
    fun completionRestoredEnergyDoesNotReplayAndBackgroundingCancelsOnlyItsFlight() {
        composeTestRule.mainClock.autoAdvance = false
        val owner = TestLifecycle()
        moveTo(owner, Lifecycle.State.RESUMED)
        var selected by mutableStateOf<ReflectionBranch?>(ReflectionBranch.SPENT)
        val answers = mutableListOf<ReflectionBranch>()
        val exact = "Late feedback keeps this saved reflection unchanged."
        composeTestRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                RunCompleteScreen(fixtureMetrics(), RunCompleteReflectionDisplay.Ready(exact), {}, {}, true,
                    selectedEnergy = selected, energyEnabled = true,
                    onSelectEnergy = { selected = it; answers += it })
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1900L)
        composeTestRule.onNodeWithTag(RUN_COMPLETE_ENERGY_INTAKE_TAG).assertDoesNotExist()
        composeTestRule.onNodeWithText("Energy +").performClick()
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.onNodeWithText("SPENT").performClick()
        composeTestRule.mainClock.advanceTimeBy(650L)
        composeTestRule.onNodeWithTag(RUN_COMPLETE_ENERGY_INTAKE_TAG).assertIsDisplayed()
        moveTo(owner, Lifecycle.State.CREATED)
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.onNodeWithTag(RUN_COMPLETE_ENERGY_INTAKE_TAG).assertDoesNotExist()
        assertEquals(ReflectionBranch.SPENT, selected)
        moveTo(owner, Lifecycle.State.RESUMED)
        composeTestRule.mainClock.advanceTimeBy(1900L)
        composeTestRule.onNodeWithTag(RUN_COMPLETE_ENERGY_INTAKE_TAG).assertDoesNotExist()
        composeTestRule.onNodeWithTag(RUN_COMPLETE_REFLECTION_TEXT_TAG).assertTextEquals(exact)
        assertEquals(listOf(ReflectionBranch.SPENT), answers)
    }

    @Test
    fun completionReferenceLayoutShowsSavedFactsAndKeepsLateEnergyAvailable() {
        val metrics = fixtureMetrics().copy(distance = "4.20 mi", elapsed = "00:38:17",
            averagePace = "8:24 /mi", active = "00:35:17")
        var ready by mutableStateOf(false)
        var selected by mutableStateOf<ReflectionBranch?>(null)
        composeTestRule.setContent {
            RunCompleteScreen(
                metrics = metrics,
                reflection = if (ready) RunCompleteReflectionDisplay.Ready("A saved reflection stays with this run.")
                    else RunCompleteReflectionDisplay.Preparing,
                onRetryReflection = {}, onStartAnother = {}, actionsEnabled = true,
                modifier = Modifier.systemBarsPadding(),
                selectedEnergy = selected, onSelectEnergy = { selected = it }, energyEnabled = true,
                selectedEffort = null, effortEnabled = true, showHistory = true,
                savedPreRunEnergy = PreRunEnergy.MODERATE,
                completionDateLabel = "Thu, Oct 15 · 6:53 AM", isTestData = true
            )
        }
        composeTestRule.onNodeWithText("Moderate").assertIsDisplayed()
        composeTestRule.onNodeWithText("Thu, Oct 15 · 6:53 AM", substring = true, ignoreCase = true).assertExists()
        composeTestRule.onNodeWithText("SPENT").assertIsDisplayed()
        saveNativePreview(RUN_COMPLETE_SCREEN_TAG, "demo-completion-initial.png")
        composeTestRule.onNodeWithText("POWERED UP").performScrollTo().assertIsDisplayed()
        composeTestRule.runOnIdle { selected = ReflectionBranch.FEELING_GOOD; ready = true }
        composeTestRule.onNodeWithTag(RUN_COMPLETE_REFLECTION_TEXT_TAG).performScrollTo()
            .assertTextEquals("A saved reflection stays with this run.")
        composeTestRule.onNodeWithText("Energy +").performScrollTo().assertIsEnabled()
        composeTestRule.onNodeWithText("RUN SAVED").performScrollTo()
        saveNativePreview(RUN_COMPLETE_SCREEN_TAG, "demo-completion-ready.png")
    }

    @Test
    fun historyFiltersPreserveUnknownProvenanceAndStopTheOpenedReply() {
        val spoken = mutableListOf<String>()
        var stops = 0
        val gps = RunHistoryItem("gps-demo", "Oct 15, 2026 · 6:53 AM",
            fixtureMetrics().copy(distance = "4.20 mi", elapsed = "00:38:17", active = "—",
                averagePace = "—", sourceLabel = "GPS metrics · partial"),
            "READY", "4.2 miles recorded by partial GPS over 38 minutes. " +
                "You marked Feeling Good after the run. The saved reply stays unchanged when you update feedback.",
            "Feeling Good", false, isGpsRun = true, musicObservationRequested = true,
            musicObservationCount = 2,
            lastObservedMedia = "Morning Miles — Demo Artist · Spotify",
            reflectionMusicSummary = "Music observations kept on this phone")
        val sample = gps.copy(runId = "sample-demo", metrics = fixtureMetrics(), isGpsRun = false)
        val legacy = gps.copy(runId = "unknown-demo", metrics = null, isGpsRun = false)
        composeTestRule.setContent {
            RunHistoryScreen(listOf(gps, sample, legacy), {}, {}, modifier = Modifier.systemBarsPadding(),
                speakerFactory = { object : ReflectionSpeaker {
                    override fun speak(text: String) { spoken += text }
                    override fun stop() { stops++ }
                    override fun close() = Unit
                } },
                routeLoader = { GpsRoutePreviewData(listOf(listOf(
                    GpsRouteCoordinate(0.15, 0.85), GpsRouteCoordinate(0.25, 0.45),
                    GpsRouteCoordinate(0.55, 0.25), GpsRouteCoordinate(0.65, 0.55),
                    GpsRouteCoordinate(0.85, 0.15))), 5, 4, 0.7, 0.7) })
        }
        saveNativePreview(RUN_HISTORY_SCREEN_TAG, "demo-history-closed.png")
        composeTestRule.onNodeWithTag(RUN_HISTORY_ROW_TAG_PREFIX + "gps-demo").performClick()
        composeTestRule.onNodeWithTag(RUN_HISTORY_REFLECTION_TAG_PREFIX + "gps-demo")
            .assertTextEquals(gps.reflectionText!!)
        saveNativePreview(RUN_HISTORY_SCREEN_TAG, "demo-history-expanded.png")
        composeTestRule.onNodeWithTag("runHistoryFilter:SAMPLES").performClick()
        composeTestRule.onNodeWithTag(RUN_HISTORY_ROW_TAG_PREFIX + "sample-demo").assertIsDisplayed()
        composeTestRule.onNodeWithTag(RUN_HISTORY_ROW_TAG_PREFIX + "unknown-demo").assertDoesNotExist()
        composeTestRule.onNodeWithTag(RUN_HISTORY_ROW_TAG_PREFIX + "gps-demo").assertDoesNotExist()
        assertEquals(listOf(gps.reflectionText), spoken)
        assertTrue(stops > 0)
        composeTestRule.onNodeWithTag("runHistoryFilter:GPS").performClick()
        composeTestRule.onNodeWithTag(RUN_HISTORY_ROW_TAG_PREFIX + "gps-demo").assertIsDisplayed()
        composeTestRule.onNodeWithTag("runHistoryFilter:ALL").performClick()
        composeTestRule.onNodeWithTag(RUN_HISTORY_ROW_TAG_PREFIX + "unknown-demo").performScrollTo().assertIsDisplayed()
        assertEquals(1, spoken.size)
    }

    @Test
    fun shortLargeTextHistoryKeepsHeadingAndLastSavedDetailsReachable() {
        val exact = "You kept your own rhythm through the morning.\n" +
            "Three miles are saved with the time and pace you actually recorded.\n" +
            "This final sentence must remain available with the rest of the exact saved reply."
        val rows = (1..12).map { number ->
            energyInsightHistoryRun(number).copy(preRunEnergy = null,
                reflectionText = if (number == 12) exact else "Saved reply $number.",
                splitStatus = RunSplitStatus.READY)
        }
        val last = rows.last()
        val spoken = mutableListOf<String>()
        val energies = mutableListOf<Pair<String, ReflectionBranch>>()
        val efforts = mutableListOf<Pair<String, PostRunEffort?>>()
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                RunHistoryScreen(rows, onBack = {},
                    onRetryReflection = { throw AssertionError("Reading saved details must not regenerate a reply") },
                    modifier = Modifier.size(360.dp, 480.dp),
                    speakerFactory = { object : ReflectionSpeaker {
                        override fun speak(text: String) { spoken += text }
                        override fun stop() = Unit
                        override fun close() = Unit
                    } },
                    onSelectEnergy = { id, energy -> energies += id to energy },
                    onSelectEffort = { id, effort -> efforts += id to effort },
                    splitLoader = { HistorySplitDisplay.Ready(listOf(
                        RunSplitDisplayRow("1 mi", "10:12", "10:12/mi"),
                        RunSplitDisplayRow("Final 0.25 mi", "2:33", "10:12/mi")
                    ), "mi") })
            }
        }
        val heading = composeTestRule.onNodeWithText("YOUR SAVED RUNS")
        val headingBounds = heading.assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(heading.fetchSemanticsNode().config.contains(SemanticsProperties.Heading))
        saveNativePreview(RUN_HISTORY_SCREEN_TAG, "qa-history-short-large-top.png")
        for (text in listOf("YOUR SAVED RUNS", "12 activities")) {
            val layouts = mutableListOf<TextLayoutResult>()
            composeTestRule.onNodeWithText(text).assertIsDisplayed()
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            assertFalse("The enlarged History caption must not truncate $text: size=${layout.size}, " +
                "paragraph=${layout.multiParagraph.width}x${layout.multiParagraph.height}, " +
                "widthOverflow=${layout.didOverflowWidth}, heightOverflow=${layout.didOverflowHeight}",
                layout.hasVisualOverflow)
        }
        composeTestRule.onNodeWithTag(RUN_HISTORY_LIST_TAG).performScrollToIndex(5)
            .performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 35f) }
        saveNativePreview(RUN_HISTORY_SCREEN_TAG, "qa-history-short-large-scrolled.png")
        composeTestRule.onNodeWithTag(RUN_HISTORY_LIST_TAG).performScrollToIndex(rows.lastIndex)
        composeTestRule.onNodeWithTag(RUN_HISTORY_ROW_TAG_PREFIX + last.runId)
            .assertIsDisplayed().performClick()
        val reflection = composeTestRule.onNodeWithTag(RUN_HISTORY_REFLECTION_TAG_PREFIX + last.runId)
        reflection.performScrollTo().assertIsDisplayed().assertTextEquals(exact)
        val layouts = mutableListOf<TextLayoutResult>()
        reflection.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertFalse("The saved reply must wrap rather than truncate", layouts.single().hasVisualOverflow)
        assertEquals(exact.length, layouts.single().getLineEnd(layouts.single().lineCount - 1, visibleEnd = true))
        saveNativePreview(RUN_HISTORY_SCREEN_TAG, "qa-history-short-large-expanded.png")
        composeTestRule.onNodeWithText("Final 0.25 mi").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("2:33").assertIsDisplayed()
        composeTestRule.onNodeWithText("Estimated from recorded GPS. Times exclude pauses.")
            .performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag(RUN_HISTORY_ENERGY_TAG_PREFIX + last.runId)
            .performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
        composeTestRule.onNodeWithTag(RUN_HISTORY_ENERGY_CHOICE_TAG_PREFIX + "FEELING_GOOD")
            .assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag(RUN_HISTORY_EFFORT_TAG_PREFIX + last.runId)
            .performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
        composeTestRule.onNodeWithTag(RUN_COMPLETE_EFFORT_CHOICE_TAG_PREFIX + "WORKING")
            .assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag(RUN_HISTORY_EFFORT_TAG_PREFIX + last.runId)
            .performScrollTo().assertIsDisplayed()
        saveNativePreview(RUN_HISTORY_SCREEN_TAG, "qa-history-short-large-feedback.png")
        heading.assertIsDisplayed()
        assertEquals("The History heading stays fixed while saved details scroll", headingBounds,
            heading.fetchSemanticsNode().boundsInRoot)
        composeTestRule.onNodeWithText("12 activities").assertIsDisplayed()
        reflection.assertTextEquals(exact)
        composeTestRule.runOnIdle {
            assertEquals(listOf(exact), spoken)
            assertEquals(listOf(last.runId to ReflectionBranch.FEELING_GOOD), energies)
            assertEquals(listOf(last.runId to PostRunEffort.WORKING), efforts)
        }
    }

    private fun saveNativePreview(tag: String, name: String) {
        val bitmap = composeTestRule.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
        val file = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, name)
        file.outputStream().use { output ->
            assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
        }
    }

    @Test
    fun completionEffortFollowsEnergyAndDoesNotHoldTheStoredReflection() {
        val exact = "Exact saved reflection."
        val choices = mutableListOf<PostRunEffort?>()
        val sheetStates = mutableListOf<Boolean>()
        var energySelections = 0
        composeTestRule.setContent {
            var selected by remember { mutableStateOf<PostRunEffort?>(null) }
            RunCompleteScreen(
                metrics = fixtureMetrics(),
                reflection = RunCompleteReflectionDisplay.Ready(exact),
                onRetryReflection = {},
                onStartAnother = {},
                actionsEnabled = true,
                selectedEnergy = null,
                onSelectEnergy = { energySelections++ },
                energyEnabled = true,
                selectedEffort = selected,
                onSelectEffort = { choice ->
                    choices += choice
                    selected = choice
                },
                effortEnabled = true,
                onEffortSheetOpenChange = { sheetStates += it }
            )
        }

        composeTestRule.onNodeWithTag(RUN_COMPLETE_EFFORT_TAG).assertDoesNotExist()
        composeTestRule.onNodeWithText("FEELING GOOD").performScrollTo().performClick()
        composeTestRule.mainClock.advanceTimeBy(2000L)
        composeTestRule.onNodeWithTag(RUN_COMPLETE_REFLECTION_TEXT_TAG)
            .performScrollTo().assertTextEquals(exact)
        composeTestRule.onNodeWithTag(RUN_COMPLETE_EFFORT_CHOICE_TAG_PREFIX + "HEAVY")
            .performScrollTo().performClick()

        composeTestRule.onNodeWithTag(RUN_COMPLETE_EFFORT_TAG).assertDoesNotExist()
        composeTestRule.onNodeWithTag(RUN_COMPLETE_REFLECTION_TEXT_TAG)
            .performScrollTo().assertTextEquals(exact)
        assertEquals(listOf(PostRunEffort.HEAVY), choices)
        assertEquals(1, energySelections)
        assertEquals(false, sheetStates.last())
        assertFalse(sheetStates.any { it })
    }

    @Test
    fun savedEffortIsUnavailableWhileItsSaveIsPending() {
        composeTestRule.setContent {
            RunCompleteScreen(
                metrics = fixtureMetrics(),
                reflection = RunCompleteReflectionDisplay.Preparing,
                onRetryReflection = {},
                onStartAnother = {},
                actionsEnabled = true,
                selectedEffort = PostRunEffort.SMOOTH,
                effortEnabled = true,
                effortSaving = true
            )
        }

        composeTestRule.onNodeWithTag(RUN_COMPLETE_EFFORT_TAG).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag(RUN_COMPLETE_EFFORT_CHOICE_TAG_PREFIX + "SMOOTH")
            .assertDoesNotExist()
        composeTestRule.onNodeWithText("Saving Effort…").assertExists()
    }

    @Test
    fun optionalEffortSkipKeepsReflectionAndSaveActivityAvailable() {
        var saved = 0
        var effortSelections = 0
        val holds = mutableListOf<Boolean>()
        composeTestRule.setContent {
            RunCompleteScreen(fixtureMetrics(), RunCompleteReflectionDisplay.Ready("Exact final reflection."),
                {}, {}, true, energyEnabled = true, effortEnabled = true,
                onSelectEffort = { effortSelections++ }, onSaveActivity = { saved++ },
                onEffortSheetOpenChange = { holds += it })
        }
        composeTestRule.onNodeWithText("SPENT").performScrollTo().performClick()
        composeTestRule.mainClock.advanceTimeBy(2000L)
        composeTestRule.onNodeWithText("Skip").performScrollTo().performClick()
        composeTestRule.onNodeWithTag(RUN_COMPLETE_EFFORT_TAG).assertDoesNotExist()
        composeTestRule.onNodeWithTag(RUN_COMPLETE_REFLECTION_TEXT_TAG).performScrollTo()
            .assertTextEquals("Exact final reflection.")
        composeTestRule.onNodeWithTag(RUN_COMPLETE_SAVE_ACTIVITY_TAG).assertIsEnabled().performClick()
        assertEquals(1, saved)
        assertEquals(0, effortSelections)
        assertFalse(holds.any { it })
    }

    @Test
    fun completionNoteHasExplicitSaveFailureAndCancelWithoutChangingEnergy() {
        val notes = mutableListOf<String?>()
        var energySelections = 0
        var saveError by mutableStateOf<String?>(null)
        composeTestRule.setContent {
            RunCompleteScreen(fixtureMetrics(), null, {}, {}, true,
                onSaveActivity = {}, localNote = "Original note", noteError = saveError,
                onSaveLocalNote = { notes += it; saveError = "Note could not be saved. Try again." },
                onSelectEnergy = { energySelections++ })
        }
        composeTestRule.onNodeWithText("Edit your note").performScrollTo().performClick()
        composeTestRule.onNodeWithTag(RUN_COMPLETE_NOTE_TAG).performScrollTo()
            .performTextInput("Physically tired, mentally great")
        composeTestRule.onNodeWithTag(RUN_COMPLETE_SAVE_ACTIVITY_TAG).assertIsNotEnabled()
        composeTestRule.onNodeWithTag(RUN_COMPLETE_NOTE_SAVE_TAG).performScrollTo().performClick()
        composeTestRule.onNodeWithText("Note could not be saved. Try again.").assertExists()
        composeTestRule.onNodeWithText("Cancel note changes").performScrollTo().performClick()
        composeTestRule.onNodeWithTag(RUN_COMPLETE_NOTE_TAG).assertDoesNotExist()
        composeTestRule.onNodeWithTag(RUN_COMPLETE_SAVE_ACTIVITY_TAG).assertIsEnabled()
        assertEquals(1, notes.size)
        assertEquals(0, energySelections)
    }

    @Test
    fun notSureSavesAnUnknownEnergyAndActivityHasOnlyTheNewPrimaryAction() {
        val choices = mutableListOf<ReflectionBranch>()
        var saves = 0
        composeTestRule.setContent {
            RunCompleteScreen(fixtureMetrics(), RunCompleteReflectionDisplay.Preparing, {},
                { throw AssertionError("Save must not start another run") }, true,
                energyEnabled = true, onSelectEnergy = { choices += it }, showHistory = true,
                onSaveActivity = { saves++ })
        }
        saveNativePreview(RUN_COMPLETE_SCREEN_TAG, "debrief-completion.png")
        composeTestRule.onNodeWithText("Not sure").performScrollTo().performClick()
        composeTestRule.onNodeWithTag(RUN_COMPLETE_ENERGY_TAG).assertDoesNotExist()
        composeTestRule.onNodeWithTag(RUN_COMPLETE_HISTORY_TAG).assertDoesNotExist()
        composeTestRule.onNodeWithText("Start another run").assertDoesNotExist()
        composeTestRule.onNodeWithTag(RUN_COMPLETE_SAVE_ACTIVITY_TAG).performClick()
        assertEquals(listOf(ReflectionBranch.NO_SELECTION), choices)
        assertEquals(1, saves)
    }

    @Test
    fun activityRecapShowsExactSavedFactsReflectionAndLocalNote() {
        var historyOpens = 0
        var done = 0
        val item = RunHistoryItem("recap", "Oct 18 · morning", fixtureMetrics(), "READY",
            "Exact saved reflection without a new generation.", "Feeling Good", true,
            energy = ReflectionBranch.FEELING_GOOD, preRunEnergy = PreRunEnergy.LOW,
            effort = PostRunEffort.WORKING)
        composeTestRule.setContent {
            RunActivityRecapScreen(item, "Legs tired, mind clear", { historyOpens++ }, { done++ })
        }
        composeTestRule.onNodeWithTag(RUN_ACTIVITY_RECAP_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("0.11").assertExists()
        saveNativePreview(RUN_ACTIVITY_RECAP_TAG, "debrief-recap.png")
        composeTestRule.onNodeWithText("Legs tired, mind clear").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag(RUN_COMPLETE_REFLECTION_TEXT_TAG).performScrollTo()
            .assertTextEquals(item.reflectionText!!)
        composeTestRule.onNodeWithTag(RUN_COMPLETE_HISTORY_TAG).performScrollTo().performClick()
        composeTestRule.onNodeWithText("Done").performClick()
        assertEquals(1, historyOpens)
        assertEquals(1, done)
    }

    @Test
    fun historyEnergyInsightRendersSavedCountsWithoutStartingSpeech() {
        composeTestRule.setContent {
            RunHistoryScreen(
                items = listOf(energyInsightHistoryRun(1), energyInsightHistoryRun(2),
                    energyInsightHistoryRun(3), energyInsightHistoryRun(4, ReflectionBranch.SPENT)),
                onBack = {},
                onRetryReflection = { throw AssertionError("Insight must not generate a reflection") },
                speakerFactory = { throw AssertionError("Insight must not start speech") }
            )
        }

        composeTestRule.onNodeWithTag(RUNNER_STATE_INSIGHT_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("After starting Low").assertExists()
        composeTestRule.onNodeWithText(
            "You finished Feeling Good in 3 of 4 runs you logged with Low starting Energy."
        ).assertExists()
        composeTestRule.onNodeWithText(
            "Based on your Energy check-ins. 4 of your latest 4 GPS runs have both answers."
        ).assertExists()
        saveNativePreview(RUN_HISTORY_SCREEN_TAG, "qa-energy-insight.png")
    }

    @Test
    fun historyEnergyInsightWaitsForThreePairsAndStaysOutOfSamples() {
        val sample = energyInsightHistoryRun(99).copy(isGpsRun = false,
            metricSource = MetricSource.FIXTURE, isTestData = true, metrics = fixtureMetrics())
        var rows by mutableStateOf(listOf(energyInsightHistoryRun(1), energyInsightHistoryRun(2), sample))
        composeTestRule.setContent {
            RunHistoryScreen(rows, onBack = {},
                onRetryReflection = { throw AssertionError("Insight must not generate a reflection") },
                speakerFactory = { throw AssertionError("Insight must not start speech") })
        }
        composeTestRule.onNodeWithTag(RUNNER_STATE_INSIGHT_TAG).assertDoesNotExist()
        composeTestRule.runOnIdle { rows = rows + energyInsightHistoryRun(3) }
        composeTestRule.onNodeWithTag(RUNNER_STATE_INSIGHT_TAG).assertExists()

        composeTestRule.onNodeWithTag("runHistoryFilter:SAMPLES").performClick()
        composeTestRule.onNodeWithTag(RUNNER_STATE_INSIGHT_TAG).assertDoesNotExist()
        composeTestRule.onNodeWithTag("runHistoryFilter:GPS").performClick()
        composeTestRule.onNodeWithTag(RUNNER_STATE_INSIGHT_TAG).assertExists()
        composeTestRule.onNodeWithTag("runHistoryFilter:ALL").performClick()
        composeTestRule.onNodeWithText(
            "Based on your Energy check-ins. 3 of your latest 3 GPS runs have both answers."
        ).assertExists()
    }

    @Test
    fun historyEnergyInsightRecomputesAfterSavedAnswerChanges() {
        var rows by mutableStateOf((1..3).map { energyInsightHistoryRun(it) })
        composeTestRule.setContent {
            RunHistoryScreen(rows, onBack = {},
                onRetryReflection = { throw AssertionError("Insight must not generate a reflection") },
                speakerFactory = { throw AssertionError("Insight must not start speech") })
        }
        val original = "You finished Feeling Good in 3 of 3 runs you logged with Low starting Energy."
        composeTestRule.onNodeWithText(original).assertExists()
        composeTestRule.runOnIdle {
            rows = rows.mapIndexed { index, row ->
                if (index == 0) row.copy(energy = ReflectionBranch.SPENT, energyLabel = "Spent") else row
            }
        }
        composeTestRule.onNodeWithText(original).assertDoesNotExist()
        composeTestRule.onNodeWithText(
            "You finished Feeling Good in 2 of 3 runs you logged with Low starting Energy."
        ).assertExists()
        composeTestRule.runOnIdle {
            rows = rows.mapIndexed { index, row -> if (index == 0) row.copy(preRunEnergy = null) else row }
        }
        composeTestRule.onNodeWithTag(RUNNER_STATE_INSIGHT_TAG).assertDoesNotExist()
    }

    @Test
    fun historyEnergyInsightHidesDuringLoadingOrFailedRead() {
        val rows = (1..3).map { energyInsightHistoryRun(it) }
        var loading by mutableStateOf(true)
        var loadError by mutableStateOf<String?>(null)
        composeTestRule.setContent {
            RunHistoryScreen(rows, onBack = {}, isLoading = loading, error = loadError,
                onRetryReflection = { throw AssertionError("Insight must not generate a reflection") },
                speakerFactory = { throw AssertionError("Insight must not start speech") })
        }
        composeTestRule.onNodeWithTag(RUNNER_STATE_INSIGHT_TAG).assertDoesNotExist()
        composeTestRule.runOnIdle { loading = false }
        composeTestRule.onNodeWithTag(RUNNER_STATE_INSIGHT_TAG).assertExists()
        composeTestRule.runOnIdle { loadError = "Saved runs are temporarily unavailable." }
        composeTestRule.onNodeWithTag(RUNNER_STATE_INSIGHT_TAG).assertDoesNotExist()
        composeTestRule.runOnIdle { loadError = null }
        composeTestRule.onNodeWithTag(RUNNER_STATE_INSIGHT_TAG).assertExists()
    }

    private fun energyInsightHistoryRun(
        number: Int,
        post: ReflectionBranch = ReflectionBranch.FEELING_GOOD
    ) = RunHistoryItem(
        runId = "223e4567-e89b-12d3-a456-${number.toString().padStart(12, '0')}",
        dateLabel = "Oct 17, 2026 · 7:00 AM",
        metrics = fixtureMetrics().copy(sourceLabel = "GPS metrics", gpsDistanceAvailable = true),
        reflectionStatus = "READY",
        reflectionText = "Saved reflection remains unchanged.",
        energyLabel = when (post) {
            ReflectionBranch.SPENT -> "Spent"
            ReflectionBranch.FEELING_GOOD -> "Feeling Good"
            ReflectionBranch.POWERED_UP -> "Powered Up"
            ReflectionBranch.NO_SELECTION -> null
        },
        isTestData = false,
        isGpsRun = true,
        energy = post,
        preRunEnergy = PreRunEnergy.LOW,
        metricSource = MetricSource.GPS
    )

    @Test
    fun historySplitsReadyTableShowsFinalPartialAndPauseExplanation() {
        composeTestRule.setContent {
            HistorySplitsPanel(HistorySplitDisplay.Ready(listOf(
                RunSplitDisplayRow("1 mi", "10:12", "10:12/mi"),
                RunSplitDisplayRow("Final 0.25 mi", "2:33", "10:12/mi")
            ), "mi"), onRetry = { throw AssertionError("Ready splits do not need retry") })
        }
        composeTestRule.onNodeWithTag("historySplits").assertIsDisplayed()
        composeTestRule.onNodeWithText("Distance").assertExists()
        composeTestRule.onNodeWithText("Time").assertExists()
        composeTestRule.onNodeWithText("Pace").assertExists()
        composeTestRule.onNodeWithText("1 mi").assertExists()
        composeTestRule.onNodeWithText("Final 0.25 mi").assertExists()
        composeTestRule.onNodeWithText("2:33").assertExists()
        composeTestRule.onNodeWithText("Estimated from recorded GPS. Times exclude pauses.").assertExists()
        composeTestRule.onNodeWithTag("historySplitsRetry").assertDoesNotExist()
    }

    @Test
    fun historySplitLoadFailureRetriesWithoutRepeatingSpeechOrChangingSavedReply() {
        val item = energyInsightHistoryRun(1).copy(splitStatus = RunSplitStatus.READY,
            reflectionText = "Exact saved reflection.")
        val spoken = mutableListOf<String>()
        val retries = mutableListOf<String>()
        var reads = 0
        composeTestRule.setContent {
            RunHistoryScreen(listOf(item), onBack = {},
                onRetryReflection = { throw AssertionError("Split retry must not generate a reflection") },
                speakerFactory = { object : ReflectionSpeaker {
                    override fun speak(text: String) { spoken += text }
                    override fun stop() = Unit
                    override fun close() = Unit
                } },
                splitLoader = { id ->
                    assertEquals(item.runId, id)
                    reads++
                    if (reads == 1) throw IllegalStateException("Optional split read unavailable")
                    HistorySplitDisplay.Ready(listOf(RunSplitDisplayRow("1 mi", "10:12", "10:12/mi")), "mi")
                },
                onRetrySplits = { retries += it })
        }
        composeTestRule.onNodeWithTag(RUN_HISTORY_ROW_TAG_PREFIX + item.runId).performClick()
        composeTestRule.onNodeWithText("Split details couldn’t load. Your run is saved.").assertExists()
        composeTestRule.onNodeWithTag("historySplitsRetry").performScrollTo().performClick()
        composeTestRule.onNodeWithText("1 mi").assertExists()
        composeTestRule.onNodeWithTag("historySplitsRetry").assertDoesNotExist()
        composeTestRule.onNodeWithTag(RUN_HISTORY_REFLECTION_TAG_PREFIX + item.runId)
            .assertTextEquals("Exact saved reflection.")
        composeTestRule.runOnIdle {
            assertEquals(2, reads)
            assertEquals(listOf(item.runId), retries)
            assertEquals(listOf("Exact saved reflection."), spoken)
        }
        composeTestRule.onNodeWithTag("historySplits").performScrollTo()
        saveNativePreview(RUN_HISTORY_SCREEN_TAG, "qa-history-splits.png")
    }

    @Test
    fun historySplitDetailsLoadOnlyForExpandedGpsAndStayHiddenForSamples() {
        val gps = energyInsightHistoryRun(1).copy(reflectionStatus = null,
            reflectionText = null, splitStatus = RunSplitStatus.READY)
        val sample = energyInsightHistoryRun(2).copy(reflectionStatus = null,
            reflectionText = null, isGpsRun = false, metricSource = MetricSource.FIXTURE,
            isTestData = true, metrics = fixtureMetrics(), splitStatus = RunSplitStatus.READY)
        val reads = mutableListOf<String>()
        composeTestRule.setContent {
            RunHistoryScreen(listOf(gps, sample), onBack = {},
                onRetryReflection = { throw AssertionError("Split view must not generate a reflection") },
                speakerFactory = { throw AssertionError("These rows have no reflection to speak") },
                splitLoader = { id ->
                    reads += id
                    HistorySplitDisplay.Ready(listOf(RunSplitDisplayRow("1 mi", "10:12", "10:12/mi")), "mi")
                })
        }
        composeTestRule.onNodeWithTag("historySplits").assertDoesNotExist()
        composeTestRule.runOnIdle { assertTrue(reads.isEmpty()) }
        composeTestRule.onNodeWithTag(RUN_HISTORY_ROW_TAG_PREFIX + sample.runId).performScrollTo().performClick()
        composeTestRule.onNodeWithTag("historySplits").assertDoesNotExist()
        composeTestRule.runOnIdle { assertTrue(reads.isEmpty()) }
        composeTestRule.onNodeWithTag(RUN_HISTORY_ROW_TAG_PREFIX + gps.runId).performScrollTo().performClick()
        composeTestRule.onNodeWithTag("historySplits").assertExists()
        composeTestRule.runOnIdle { assertEquals(listOf(gps.runId), reads) }
        composeTestRule.onNodeWithTag(RUN_HISTORY_ROW_TAG_PREFIX + gps.runId).performScrollTo().performClick()
        composeTestRule.onNodeWithTag("historySplits").assertDoesNotExist()
        composeTestRule.runOnIdle { assertEquals(listOf(gps.runId), reads) }
    }

    @Test
    fun historyOpensExactStoredTextWithoutRetryingGeneration() {
        val runId = "0f6a2c1e-9d43-4b7a-9c21-7b5e8a4d1f30"
        val exact = "  First line.\nSecond line with a tab\tand emoji 🌤  "
        var retries = 0
        val spoken = mutableListOf<String>()
        var stops = 0
        val speaker = object : ReflectionSpeaker {
            override fun speak(text: String) { spoken += text }
            override fun stop() { stops++ }
            override fun close() = Unit
        }
        val item = RunHistoryItem(
            runId = runId,
            dateLabel = "Oct 12, 2026 · 5:39 AM",
            metrics = fixtureMetrics(),
            reflectionStatus = "READY",
            reflectionText = exact,
            energyLabel = "Spent",
            isTestData = false
        )
        composeTestRule.setContent {
            RunHistoryScreen(
                items = listOf(item),
                onBack = {},
                onRetryReflection = { retries++ },
                speakerFactory = { speaker }
            )
        }

        composeTestRule.onNodeWithTag(RUN_HISTORY_ROW_TAG_PREFIX + runId).performClick()
        composeTestRule.onNodeWithTag(RUN_HISTORY_REFLECTION_TAG_PREFIX + runId)
            .assertTextEquals(exact)
        assertEquals(listOf(exact), spoken)
        composeTestRule.onNodeWithTag(RUN_HISTORY_RETRY_TAG_PREFIX + runId).assertDoesNotExist()
        composeTestRule.onNodeWithTag(RUN_HISTORY_ROW_TAG_PREFIX + runId).performClick()
        assertTrue(stops > 0)
        assertEquals(listOf(exact), spoken)
        assertEquals(0, retries)
    }

    @Test
    fun newRunAppearsAtHistoryTopWithoutJumpingOnFeedbackEdits() {
        val old = (0..11).map { index ->
            RunHistoryItem("old-$index", "Older run $index", fixtureMetrics(), "READY",
                "Saved reply $index", null, false)
        }
        val new = RunHistoryItem("new-run", "Newest run", fixtureMetrics(), "READY",
            "Newest saved reply", null, false)
        var rows by mutableStateOf(old)
        var visible by mutableStateOf(true)
        var spoken = 0
        var retries = 0
        composeTestRule.setContent {
            if (visible) {
                RunHistoryScreen(rows, onBack = { visible = false },
                    onRetryReflection = { retries++ },
                    speakerFactory = { object : ReflectionSpeaker {
                        override fun speak(text: String) { spoken++ }
                        override fun stop() = Unit
                        override fun close() = Unit
                    } },
                    modifier = Modifier.size(360.dp, 600.dp))
            } else {
                Text("Run Complete")
            }
        }
        composeTestRule.onNodeWithTag(RUN_HISTORY_LIST_TAG).performScrollToIndex(9)
        composeTestRule.onNodeWithTag(RUN_HISTORY_ROW_TAG_PREFIX + "old-9").assertIsDisplayed()

        // Re-enter while the old mapped list is still present, then receive the new Room row.
        composeTestRule.runOnIdle { visible = false }
        composeTestRule.runOnIdle { visible = true }
        composeTestRule.runOnIdle { rows = listOf(new) + old }
        composeTestRule.onNodeWithTag(RUN_HISTORY_ROW_TAG_PREFIX + "new-run").assertIsDisplayed()
        composeTestRule.onNodeWithTag(RUN_HISTORY_REFLECTION_TAG_PREFIX + "new-run")
            .assertDoesNotExist()

        composeTestRule.onNodeWithTag(RUN_HISTORY_LIST_TAG).performScrollToIndex(10)
        composeTestRule.onNodeWithTag(RUN_HISTORY_ROW_TAG_PREFIX + "old-9").assertIsDisplayed()
        composeTestRule.runOnIdle {
            rows = rows.map { row ->
                if (row.runId == "old-9") row.copy(effort = PostRunEffort.SMOOTH,
                    energy = ReflectionBranch.SPENT) else row
            }
        }
        composeTestRule.onNodeWithTag(RUN_HISTORY_ROW_TAG_PREFIX + "old-9").assertIsDisplayed()
        composeTestRule.onNodeWithTag(RUN_HISTORY_ROW_TAG_PREFIX + "new-run").assertDoesNotExist()
        composeTestRule.runOnIdle { assertEquals(0, spoken); assertEquals(0, retries) }
    }

    @Test
    fun expandedHistoryCanEditEffortWithoutSpeakingTheReflectionAgain() {
        val runId = "5f6a2c1e-9d43-4b7a-9c21-7b5e8a4d1f30"
        val exact = "Exact saved reflection."
        val spoken = mutableListOf<String>()
        val selected = mutableListOf<PostRunEffort?>()
        val speaker = object : ReflectionSpeaker {
            override fun speak(text: String) { spoken += text }
            override fun stop() = Unit
            override fun close() = Unit
        }
        composeTestRule.setContent {
            var item by remember {
                mutableStateOf(
                    RunHistoryItem(
                        runId = runId,
                        dateLabel = "Oct 12, 2026",
                        metrics = fixtureMetrics(),
                        reflectionStatus = "READY",
                        reflectionText = exact,
                        energyLabel = null,
                        isTestData = false
                    )
                )
            }
            RunHistoryScreen(
                items = listOf(item),
                onBack = {},
                onRetryReflection = {},
                onSelectEffort = { selectedRunId, effort ->
                    assertEquals(runId, selectedRunId)
                    selected += effort
                    item = item.copy(effort = effort)
                },
                speakerFactory = { speaker }
            )
        }

        composeTestRule.onNodeWithTag(RUN_HISTORY_ROW_TAG_PREFIX + runId).performClick()
        composeTestRule.onNodeWithTag(RUN_HISTORY_EFFORT_TAG_PREFIX + runId)
            .assertTextEquals("EFFORT +").performClick()
        composeTestRule.onNodeWithTag(RUN_COMPLETE_EFFORT_CHOICE_TAG_PREFIX + "WORKING")
            .performClick()
        composeTestRule.onNodeWithTag(RUN_HISTORY_EFFORT_TAG_PREFIX + runId)
            .assertTextEquals("EFFORT · WORKING").performClick()
        composeTestRule.onNodeWithText("Clear Effort").performClick()

        composeTestRule.onNodeWithTag(RUN_HISTORY_EFFORT_TAG_PREFIX + runId)
            .assertTextEquals("EFFORT +")
        composeTestRule.onNodeWithTag(RUN_HISTORY_REFLECTION_TAG_PREFIX + runId)
            .assertTextEquals(exact)
        assertEquals(listOf(PostRunEffort.WORKING, null), selected)
        assertEquals(listOf(exact), spoken)
    }

    @Test
    fun expandedHistoryCanAddAndChangeEnergyWithoutReplacingOrSpeakingTheReplyAgain() {
        val runId = "6f6a2c1e-9d43-4b7a-9c21-7b5e8a4d1f30"
        val exact = "  Exact saved reply.\nIts spacing stays intact.  "
        val spoken = mutableListOf<String>()
        val selected = mutableListOf<ReflectionBranch>()
        var retries = 0
        val speaker = object : ReflectionSpeaker {
            override fun speak(text: String) { spoken += text }
            override fun stop() = Unit
            override fun close() = Unit
        }
        composeTestRule.setContent {
            var item by remember {
                mutableStateOf(
                    RunHistoryItem(
                        runId = runId,
                        dateLabel = "Oct 13, 2026",
                        metrics = fixtureMetrics(),
                        reflectionStatus = "READY",
                        reflectionText = exact,
                        energyLabel = null,
                        isTestData = false,
                        effort = PostRunEffort.SMOOTH
                    )
                )
            }
            RunHistoryScreen(
                items = listOf(item),
                onBack = {},
                onRetryReflection = { retries++ },
                onSelectEnergy = { selectedRunId, energy ->
                    assertEquals(runId, selectedRunId)
                    selected += energy
                    item = item.copy(
                        energy = energy,
                        energyLabel = when (energy) {
                            ReflectionBranch.SPENT -> "Spent"
                            ReflectionBranch.FEELING_GOOD -> "Feeling Good"
                            ReflectionBranch.POWERED_UP -> "Powered Up"
                            ReflectionBranch.NO_SELECTION -> null
                        }
                    )
                },
                speakerFactory = { speaker }
            )
        }

        composeTestRule.onNodeWithTag(RUN_HISTORY_ROW_TAG_PREFIX + runId).performClick()
        composeTestRule.onNodeWithTag(RUN_HISTORY_ENERGY_TAG_PREFIX + runId)
            .assertTextEquals("After run +").performClick()
        composeTestRule.onNodeWithTag(RUN_HISTORY_ENERGY_CHOICE_TAG_PREFIX + "SPENT")
            .assertIsDisplayed()
        androidx.test.espresso.Espresso.pressBack()
        composeTestRule.onNodeWithTag(RUN_HISTORY_ENERGY_SHEET_TAG).assertDoesNotExist()
        assertTrue(selected.isEmpty())
        composeTestRule.onNodeWithTag(RUN_HISTORY_ENERGY_TAG_PREFIX + runId)
            .assertTextEquals("After run +").performClick()
        composeTestRule.onNodeWithTag(RUN_HISTORY_ENERGY_CHOICE_TAG_PREFIX + "SPENT")
            .performClick()
        composeTestRule.onNodeWithTag(RUN_HISTORY_ENERGY_SHEET_TAG).assertDoesNotExist()
        composeTestRule.onNodeWithTag(RUN_HISTORY_ENERGY_TAG_PREFIX + runId)
            .assertTextEquals("After run · Spent").performClick()
        assertTrue(
            composeTestRule.onNodeWithTag(RUN_HISTORY_ENERGY_CHOICE_TAG_PREFIX + "SPENT")
                .fetchSemanticsNode().config[SemanticsProperties.Selected]
        )
        composeTestRule.onNodeWithTag(RUN_HISTORY_ENERGY_CHOICE_TAG_PREFIX + "FEELING_GOOD")
            .performClick()

        composeTestRule.onNodeWithTag(RUN_HISTORY_ENERGY_TAG_PREFIX + runId)
            .assertTextEquals("After run · Feeling Good")
        composeTestRule.onNodeWithTag(RUN_HISTORY_EFFORT_TAG_PREFIX + runId)
            .assertTextEquals("EFFORT · SMOOTH")
        composeTestRule.onNodeWithTag(RUN_HISTORY_REFLECTION_TAG_PREFIX + runId)
            .assertTextEquals(exact)
        assertEquals(listOf(ReflectionBranch.SPENT, ReflectionBranch.FEELING_GOOD), selected)
        assertEquals(listOf(exact), spoken)
        assertEquals(0, retries)
    }

    @Test
    fun historyEnergySaveFeedbackStaysOnItsOwnRunAndBlocksRepeatedTaps() {
        val runId = "7f6a2c1e-9d43-4b7a-9c21-7b5e8a4d1f30"
        val otherRunId = "8f6a2c1e-9d43-4b7a-9c21-7b5e8a4d1f30"
        val item = RunHistoryItem(
            runId = runId,
            dateLabel = "Oct 13, 2026",
            metrics = fixtureMetrics(),
            reflectionStatus = "PENDING",
            reflectionText = null,
            energyLabel = "Powered Up",
            isTestData = false,
            energy = ReflectionBranch.POWERED_UP
        )
        composeTestRule.setContent {
            RunHistoryScreen(
                items = listOf(item, item.copy(runId = otherRunId)),
                onBack = {},
                onRetryReflection = {},
                energySavingRunId = runId,
                energyError = otherRunId to "Could not save that run's Energy."
            )
        }

        composeTestRule.onNodeWithTag(RUN_HISTORY_ROW_TAG_PREFIX + runId).performClick()
        composeTestRule.onNodeWithTag(RUN_HISTORY_ENERGY_TAG_PREFIX + runId)
            .assertIsNotEnabled()
        composeTestRule.onNodeWithText("Saving Energy…").assertExists()
        composeTestRule.onNodeWithText("Could not save that run's Energy.").assertDoesNotExist()
        composeTestRule.onNodeWithTag(RUN_HISTORY_ENERGY_SHEET_TAG).assertDoesNotExist()

        composeTestRule.onNodeWithTag(RUN_HISTORY_ROW_TAG_PREFIX + otherRunId)
            .performScrollTo().performClick()
        composeTestRule.onNodeWithTag(RUN_HISTORY_ENERGY_TAG_PREFIX + otherRunId)
            .performScrollTo().assertIsEnabled()
        composeTestRule.onNodeWithText("Could not save that run's Energy.").assertExists()
        composeTestRule.onNodeWithText("Saving Energy…").assertDoesNotExist()
    }

    @Test
    fun historyWithoutAReflectionRecordDoesNotOfferEnergyEditing() {
        val runId = "9f6a2c1e-9d43-4b7a-9c21-7b5e8a4d1f30"
        composeTestRule.setContent {
            RunHistoryScreen(
                items = listOf(
                    RunHistoryItem(
                        runId = runId,
                        dateLabel = "Oct 13, 2026",
                        metrics = fixtureMetrics(),
                        reflectionStatus = null,
                        reflectionText = null,
                        energyLabel = null,
                        isTestData = false
                    )
                ),
                onBack = {},
                onRetryReflection = {}
            )
        }

        composeTestRule.onNodeWithTag(RUN_HISTORY_ROW_TAG_PREFIX + runId).performClick()
        composeTestRule.onNodeWithTag(RUN_HISTORY_ENERGY_TAG_PREFIX + runId).assertDoesNotExist()
        composeTestRule.onNodeWithTag(RUN_HISTORY_ENERGY_SHEET_TAG).assertDoesNotExist()
        composeTestRule.onNodeWithTag(RUN_HISTORY_EFFORT_TAG_PREFIX + runId).assertExists()
    }

    @Test
    fun completionSpeaksOnlyNewReadyTextAndStopsWhenItIsHidden() {
        val runId = "0f6a2c1e-9d43-4b7a-9c21-7b5e8a4d1f30"
        val exact = "  Saved words, exactly.\nSecond line.  "
        val spoken = mutableListOf<String>()
        var stops = 0
        var closes = 0
        val speaker = object : ReflectionSpeaker {
            override fun speak(text: String) { spoken += text }
            override fun stop() { stops++ }
            override fun close() { closes++ }
        }
        var state by mutableStateOf<RunReflectionState>(RunReflectionState.Preparing(runId))
        var eligibleRunId by mutableStateOf<String?>(null)
        var spokenRunId by mutableStateOf<String?>(null)
        var visible by mutableStateOf(true)
        var includeEffect by mutableStateOf(true)

        composeTestRule.setContent {
            if (includeEffect) {
                RunCompleteSpeechEffect(
                    completedRunId = runId,
                    eligibleRunId = eligibleRunId,
                    spokenRunId = spokenRunId,
                    reflectionState = state,
                    completionVisible = visible,
                    speakerFactory = { speaker },
                    onSpoken = { spokenRunId = it }
                )
            }
        }
        composeTestRule.runOnIdle { state = RunReflectionState.Ready(runId, exact) }
        composeTestRule.waitForIdle()
        assertTrue(spoken.isEmpty()) // An old READY run does not announce itself on reopen.

        composeTestRule.runOnIdle { eligibleRunId = runId }
        composeTestRule.waitForIdle()
        assertEquals(listOf(exact), spoken)
        assertEquals(runId, spokenRunId)

        composeTestRule.runOnIdle { visible = false }
        composeTestRule.waitForIdle()
        assertTrue(stops > 0)
        composeTestRule.runOnIdle { visible = true }
        composeTestRule.waitForIdle()
        assertEquals(listOf(exact), spoken)

        composeTestRule.runOnIdle { includeEffect = false }
        composeTestRule.waitForIdle()
        assertEquals(1, closes)
    }

    @Test
    fun completionDeliveryFinishesOnlyAfterTheSpeakerChainCallbackNotSilentPreparationOrFacts() {
        val runId = "completion-shared-hold-finish"
        val exact = "Exact reflection prepared behind the facts."
        val speaker = ControlledCompletionSpeaker()
        var held = false
        var releases = 0
        var finished = 0
        var cancelled = 0
        var visible by mutableStateOf(false)
        var spokenId by mutableStateOf<String?>(null)
        var audioActive = false
        val session = CompletionAudioSession(snapshot = { null },
            requestPause = { held = true; true },
            releasePause = { held = false; releases++ }, onInterrupted = {})
        assertTrue(session.begin()) // The facts own the shared pause before the reflection is visible.
        composeTestRule.setContent {
            RunCompleteSpeechEffect(runId, runId, spokenId, RunReflectionState.Ready(runId, exact),
                visible, { speaker }, { spokenId = it },
                onAudioActiveChange = { audioActive = it },
                voicePreparation = ReflectionVoicePreparation(runId, "shared-hold-request", exact),
                preparationVisible = true,
                onDeliveryFinished = { finished++; session.finish() },
                onDeliveryCancelled = { cancelled++; session.cancel() })
        }
        composeTestRule.runOnIdle {
            assertEquals(listOf(exact), speaker.preparations)
            assertTrue(speaker.requests.isEmpty())
            assertEquals(0, finished)
            assertEquals(0, cancelled)
            assertFalse(audioActive)
            assertTrue(held)
            assertEquals(0, releases)
            visible = true // Facts/intake have released the reflection gate.
        }
        composeTestRule.runOnIdle {
            assertEquals(exact, speaker.requests.single().text)
            assertTrue(audioActive)
            assertEquals(0, finished)
            assertTrue(held)
            speaker.requests.single().finished()
        }
        composeTestRule.runOnIdle {
            assertFalse(audioActive)
            assertEquals(1, finished)
            assertEquals(0, cancelled)
            assertFalse(held)
            assertEquals(1, releases)
            speaker.requests.single().finished() // Duplicate engine completion is stale.
            visible = false
        }
        composeTestRule.runOnIdle {
            assertEquals(1, finished)
            assertEquals(0, cancelled)
            assertEquals(1, releases)
        }
    }

    @Test
    fun completionFactoryAndSpeechSetupFailuresCancelDeliveryAndReleaseTheSharedHold() {
        var runId by mutableStateOf("completion-factory-setup-failure")
        var spokenId by mutableStateOf<String?>(null)
        val held = mutableSetOf<String>()
        val releases = mutableMapOf<String, Int>()
        val finished = mutableListOf<String>()
        val cancelled = mutableListOf<String>()
        val sessions = mutableMapOf<String, CompletionAudioSession>()
        var factories = 0
        var stops = 0
        fun beginSession(id: String) {
            val session = CompletionAudioSession(snapshot = { null },
                requestPause = { held += id; true },
                releasePause = { held -= id; releases[id] = releases.getOrDefault(id, 0) + 1 },
                onInterrupted = {})
            sessions[id] = session
            assertTrue(session.begin())
        }
        beginSession(runId)
        composeTestRule.setContent {
            val deliveryId = runId
            RunCompleteSpeechEffect(deliveryId, deliveryId, spokenId,
                RunReflectionState.Ready(deliveryId, "Exact saved reflection remains available."), true,
                {
                    factories++
                    if (deliveryId == "completion-factory-setup-failure") error("Fictional factory failure")
                    object : ReflectionSpeaker {
                        override fun speak(text: String) = error("Unexpected plain speech")
                        override fun speakWithInstrumental(text: String, canPlayInstrumental: () -> Boolean,
                            onFinished: () -> Unit) = error("Fictional chain setup failure")
                        override fun stop() { stops++ }
                        override fun close() = Unit
                    }
                }, { spokenId = it },
                onDeliveryFinished = { finished += deliveryId; sessions.getValue(deliveryId).finish() },
                onDeliveryCancelled = { cancelled += deliveryId; sessions.getValue(deliveryId).cancel() })
        }
        composeTestRule.runOnIdle {
            assertEquals(listOf("completion-factory-setup-failure"), cancelled)
            assertTrue(finished.isEmpty())
            assertTrue(held.isEmpty())
            assertEquals(1, releases[runId])
            assertEquals(1, factories)
            val nextId = "completion-speech-setup-failure"
            beginSession(nextId)
            runId = nextId
        }
        composeTestRule.runOnIdle {
            assertEquals(listOf("completion-factory-setup-failure", "completion-speech-setup-failure"), cancelled)
            assertTrue(finished.isEmpty())
            assertTrue(held.isEmpty())
            assertEquals(1, releases[runId])
            assertEquals(2, factories)
            assertEquals(1, stops)
            assertEquals(runId, spokenId)
        }
    }

    @Test
    fun completionPreparesSilentlyBehindFactsAndIntakeThenSpeaksOnceWhenReleased() {
        val runId = "completion-prepared-behind-facts"
        val exact = "  Exact prepared reflection.\nSecond line.  "
        val speaker = ControlledCompletionSpeaker()
        var visible by mutableStateOf(false)
        var spokenId by mutableStateOf<String?>(null)
        val consumed = mutableListOf<String>()
        composeTestRule.setContent {
            RunCompleteSpeechEffect(runId, runId, spokenId, RunReflectionState.Ready(runId, exact),
                visible, { speaker }, { consumed += it; spokenId = it },
                voicePreparation = ReflectionVoicePreparation(runId, "prepared-request", exact),
                preparationVisible = true)
        }
        composeTestRule.runOnIdle {
            assertEquals(listOf(exact), speaker.preparations)
            assertEquals(exact, speaker.preparedText)
            assertTrue(speaker.requests.isEmpty())
            assertTrue(consumed.isEmpty())
            visible = true
        }
        composeTestRule.runOnIdle {
            assertEquals(exact, speaker.requests.single().text)
            assertTrue(speaker.requests.single().canPlayCue())
            assertEquals(listOf(runId), consumed)
            visible = false
        }
        composeTestRule.runOnIdle { visible = true }
        composeTestRule.runOnIdle {
            assertEquals(1, speaker.requests.size)
            assertEquals(listOf(runId), consumed)
        }
    }

    @Test
    fun backgroundingCompletionCancelsSilentPreparationAndReturningDoesNotSpeakThroughTheGate() {
        val runId = "completion-preparation-background"
        val speaker = ControlledCompletionSpeaker()
        var foreground by mutableStateOf(true)
        var cancelledBeforeBackground = 0
        composeTestRule.setContent {
            RunCompleteSpeechEffect(runId, runId, null, RunReflectionState.Preparing(runId),
                false, { speaker }, { error("Facts still own audible delivery.") },
                voicePreparation = ReflectionVoicePreparation(runId, "background-request", "Prepared text."),
                preparationVisible = foreground)
        }
        composeTestRule.runOnIdle {
            assertEquals("Prepared text.", speaker.preparedText)
            cancelledBeforeBackground = speaker.preparationCancellations
            foreground = false
        }
        composeTestRule.runOnIdle {
            assertEquals(null, speaker.preparedText)
            assertTrue(speaker.preparationCancellations > cancelledBeforeBackground)
            assertTrue(speaker.requests.isEmpty())
            foreground = true
        }
        composeTestRule.runOnIdle {
            assertEquals(listOf("Prepared text.", "Prepared text."), speaker.preparations)
            assertTrue(speaker.requests.isEmpty())
        }
    }

    @Test
    fun replacementRequestCancelsPreviousPreparationEvenWhenItsWordsAreUnchanged() {
        val runId = "completion-preparation-retry"
        val exact = "Same words, a different request."
        val speaker = ControlledCompletionSpeaker()
        var preparation by mutableStateOf<ReflectionVoicePreparation?>(
            ReflectionVoicePreparation(runId, "first-request", exact))
        var cancelledBeforeClear = 0
        composeTestRule.setContent {
            RunCompleteSpeechEffect(runId, runId, null, RunReflectionState.Preparing(runId),
                false, { speaker }, { error("Facts still own audible delivery.") },
                voicePreparation = preparation, preparationVisible = true)
        }
        composeTestRule.runOnIdle {
            assertEquals(listOf(exact), speaker.preparations)
            preparation = ReflectionVoicePreparation(runId, "replacement-request", exact)
        }
        composeTestRule.runOnIdle {
            assertEquals(listOf(exact, exact), speaker.preparations)
            assertEquals(listOf("cancel", "prepare:$exact"), speaker.preparationEvents.takeLast(2))
            assertTrue(speaker.requests.isEmpty())
            cancelledBeforeClear = speaker.preparationCancellations
            preparation = null
        }
        composeTestRule.runOnIdle {
            assertEquals(null, speaker.preparedText)
            assertTrue(speaker.preparationCancellations > cancelledBeforeClear)
            assertTrue(speaker.requests.isEmpty())
        }
    }

    @Test
    fun oldIneligibleMismatchedAndConsumedCompletionsNeverPrepareAVoice() {
        val runId = "completion-preparation-eligibility"
        val speaker = ControlledCompletionSpeaker()
        var factories = 0
        var eligibleId by mutableStateOf<String?>(null)
        var spokenId by mutableStateOf<String?>(null)
        var preparation by mutableStateOf(ReflectionVoicePreparation(runId, "eligibility-request", "Exact text."))
        composeTestRule.setContent {
            RunCompleteSpeechEffect(runId, eligibleId, spokenId, RunReflectionState.Ready(runId, "Exact text."),
                false, { factories++; speaker }, { error("An old or consumed run cannot speak.") },
                voicePreparation = preparation, preparationVisible = true)
        }
        composeTestRule.runOnIdle {
            assertEquals(0, factories) // Reopened History has no new-arrival eligibility.
            eligibleId = "another-run"
        }
        composeTestRule.runOnIdle {
            assertEquals(0, factories)
            eligibleId = runId
            preparation = preparation.copy(runId = "another-run")
        }
        composeTestRule.runOnIdle {
            assertEquals(0, factories)
            spokenId = runId
            preparation = preparation.copy(runId = runId)
        }
        composeTestRule.runOnIdle {
            assertEquals(0, factories)
            assertTrue(speaker.preparations.isEmpty())
            assertTrue(speaker.requests.isEmpty())
        }
    }

    @Test
    fun optionalPreparationFailureDoesNotHideTextOrConsumeTheLaterSpeechAttempt() {
        val runId = "completion-preparation-failure"
        val exact = "The exact saved reflection remains available."
        val speaker = ControlledCompletionSpeaker()
        var factories = 0
        var visible by mutableStateOf(false)
        var spokenId by mutableStateOf<String?>(null)
        val consumed = mutableListOf<String>()
        composeTestRule.setContent {
            Text(exact, Modifier.testTag("prepared-voice-failure-text"))
            RunCompleteSpeechEffect(runId, runId, spokenId, RunReflectionState.Ready(runId, exact),
                visible, {
                    factories++
                    if (factories == 1) error("The optional preparation engine is unavailable.")
                    speaker
                }, { consumed += it; spokenId = it },
                voicePreparation = ReflectionVoicePreparation(runId, "failure-request", exact),
                preparationVisible = true)
        }
        composeTestRule.runOnIdle {
            assertEquals(1, factories)
            assertTrue(consumed.isEmpty())
            assertTrue(speaker.requests.isEmpty())
            visible = true
        }
        composeTestRule.runOnIdle {
            assertEquals(2, factories)
            assertEquals(listOf(runId), consumed)
            assertEquals(exact, speaker.requests.single().text)
        }
        composeTestRule.onNodeWithTag("prepared-voice-failure-text").assertIsDisplayed()
    }

    @Test
    fun completionVoiceFailuresConsumeArrivalWithoutHidingSavedText() {
        var runId by mutableStateOf("voice-factory-run")
        var visible by mutableStateOf(true)
        var attempts = 0
        var spokenId by mutableStateOf<String?>(null)
        val consumed = mutableListOf<String>()
        composeTestRule.setContent {
            Text("Exact saved text.", Modifier.testTag("saved-voice-failure-text"))
            RunCompleteSpeechEffect(runId, runId, spokenId,
                RunReflectionState.Ready(runId, "Exact saved text."), visible,
                {
                    attempts++
                    if (runId == "voice-factory-run") error("Voice engine missing")
                    object : ReflectionSpeaker {
                        override fun speak(text: String) { error("Voice engine disconnected") }
                        override fun stop() { error("Stop failed") }
                        override fun close() { error("Close failed") }
                    }
                }, { consumed += it; spokenId = it })
        }
        composeTestRule.runOnIdle { assertEquals(listOf("voice-factory-run"), consumed) }
        composeTestRule.runOnIdle { visible = false }
        composeTestRule.runOnIdle { visible = true }
        composeTestRule.runOnIdle { assertEquals(1, attempts); runId = "voice-speak-run" }
        composeTestRule.runOnIdle {
            assertEquals(listOf("voice-factory-run", "voice-speak-run"), consumed)
            assertEquals(2, attempts)
            visible = false
        }
        composeTestRule.runOnIdle { visible = true }
        composeTestRule.runOnIdle { assertEquals(2, attempts) }
        composeTestRule.onNodeWithTag("saved-voice-failure-text").assertIsDisplayed()
    }

    @Test
    fun completionChangingRunsClosesOldSpeechBeforeTheNextReplyIsReady() {
        var runId by mutableStateOf("first-speech-run")
        var state by mutableStateOf<RunReflectionState>(RunReflectionState.Ready(runId, "First reply."))
        var spokenId by mutableStateOf<String?>(null)
        var closes = 0
        val spoken = mutableListOf<String>()
        composeTestRule.setContent {
            RunCompleteSpeechEffect(runId, runId, spokenId, state, true,
                { object : ReflectionSpeaker {
                    override fun speak(text: String) { spoken += text }
                    override fun stop() = Unit
                    override fun close() { closes++ }
                } }, { spokenId = it })
        }
        composeTestRule.runOnIdle { assertEquals(listOf("First reply."), spoken) }
        composeTestRule.runOnIdle {
            runId = "second-speech-run"
            state = RunReflectionState.Preparing(runId)
        }
        composeTestRule.runOnIdle {
            assertEquals(1, closes)
            assertEquals(listOf("First reply."), spoken)
            state = RunReflectionState.Ready(runId, "Second reply.")
        }
        composeTestRule.runOnIdle { assertEquals(listOf("First reply.", "Second reply."), spoken) }
    }

    @Test
    fun completionDirectAudioStopConsumesArrivalAndDoesNotReplayOnReturn() {
        val runId = "completion-audio-stop"
        val exact = "  Exact completion.\nSecond line.  "
        val speaker = ControlledCompletionSpeaker()
        var spokenId by mutableStateOf<String?>(null)
        var visible by mutableStateOf(true)
        var present by mutableStateOf(true)
        var audioActive = false
        var stopAudio: (() -> Unit)? = null
        val consumed = mutableListOf<String>()
        composeTestRule.setContent {
            if (present) RunCompleteSpeechEffect(runId, runId, spokenId,
                RunReflectionState.Ready(runId, exact), visible, { speaker },
                { consumed += it; spokenId = it },
                onAudioActiveChange = { audioActive = it }, onStopReady = { stopAudio = it })
        }
        composeTestRule.runOnIdle {
            assertTrue(audioActive)
            assertEquals(exact, speaker.requests.single().text)
            assertTrue(speaker.requests.single().canPlayCue())
            requireNotNull(stopAudio).invoke()
            assertFalse(audioActive)
            assertFalse(speaker.requests.single().canPlayCue())
            speaker.requests.single().finished()
            visible = false
        }
        composeTestRule.runOnIdle { visible = true }
        composeTestRule.runOnIdle {
            assertEquals(listOf(runId), consumed)
            assertEquals(1, speaker.requests.size)
            assertFalse(audioActive)
            assertTrue(speaker.stops > 0)
            present = false
        }
        composeTestRule.runOnIdle {
            assertEquals(null, stopAudio)
            assertEquals(1, speaker.closes)
        }
    }

    @Test
    fun hiddenOrReplacedCompletionInvalidatesCueAndOldFinishCannotStopNewAudio() {
        var runId by mutableStateOf("completion-first-audio")
        var state by mutableStateOf<RunReflectionState>(RunReflectionState.Ready(runId, "First."))
        var spokenId by mutableStateOf<String?>(null)
        var visible by mutableStateOf(true)
        var audioActive = false
        val speakers = mutableListOf<ControlledCompletionSpeaker>()
        composeTestRule.setContent {
            RunCompleteSpeechEffect(runId, runId, spokenId, state, visible,
                { ControlledCompletionSpeaker().also { speakers += it } }, { spokenId = it },
                onAudioActiveChange = { audioActive = it })
        }
        composeTestRule.runOnIdle {
            assertTrue(audioActive)
            runId = "completion-second-audio"
            state = RunReflectionState.Preparing(runId)
        }
        composeTestRule.runOnIdle {
            assertEquals(1, speakers.single().closes)
            assertFalse(speakers.single().requests.single().canPlayCue())
            assertFalse(audioActive)
            state = RunReflectionState.Ready(runId, "Second.")
        }
        composeTestRule.runOnIdle {
            assertEquals(2, speakers.size)
            assertTrue(audioActive)
            speakers.first().requests.single().finished()
            assertTrue(audioActive)
            assertTrue(speakers.last().requests.single().canPlayCue())
            visible = false
        }
        composeTestRule.runOnIdle {
            assertFalse(audioActive)
            assertFalse(speakers.last().requests.single().canPlayCue())
            visible = true
        }
        composeTestRule.runOnIdle {
            assertEquals(2, speakers.size)
            assertEquals(1, speakers.last().requests.size)
            assertFalse(audioActive)
        }
    }

    @Test
    fun completionAudioStopRemainsReachableOnShortLargeTextScreen() {
        val actions = renderScreen(RunUiModel(RunUiState.Saved), fixtureMetrics(),
            reflection = RunCompleteReflectionDisplay.Ready("Exact saved reflection. ".repeat(12)),
            showHistory = true, reflectionAudioActive = true,
            modifier = Modifier.size(360.dp, 480.dp), fontScale = 1.5f)
        composeTestRule.onNodeWithTag(RUN_COMPLETE_AUDIO_STOP_TAG)
            .performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
        composeTestRule.runOnIdle {
            assertEquals("Stop audio must receive the touch, including above the fixed footer", 1, actions.audioStops)
            assertEquals(1, actions.total)
        }
        composeTestRule.onNodeWithText(START_ANOTHER).assertIsDisplayed()
    }

    @Test
    fun receiptTeardownFailureStillReleasesItsSelectionWindowOnce() {
        val runId = "receipt-teardown-run"
        var visible by mutableStateOf(true)
        var present by mutableStateOf(true)
        var lateFinished: (() -> Unit)? = null
        var finishes = 0
        val speaker = object : ReflectionSpeaker {
            override fun speak(text: String) = Unit
            override fun speakReceipt(text: String, onFinished: () -> Unit) { lateFinished = onFinished }
            override fun stop() { error("Disconnected stop") }
            override fun close() { error("Disconnected close") }
        }
        composeTestRule.setContent {
            var started by remember { mutableStateOf<String?>(null) }
            var finished by remember { mutableStateOf<String?>(null) }
            if (present) RunCompleteReceiptEffect(runId, runId, "Run saved.", started, finished,
                visible, { speaker }, { started = it }, { finishes++; finished = it })
        }
        composeTestRule.runOnIdle { visible = false }
        composeTestRule.runOnIdle { assertEquals(1, finishes); present = false }
        composeTestRule.runOnIdle { requireNotNull(lateFinished).invoke(); assertEquals(1, finishes) }
    }

    @Test
    fun startOpensRunSettingsWithoutShowingBackendControls() {
        var loading by mutableStateOf(true)
        var actionsEnabled by mutableStateOf(true)
        var opens = 0
        var maps = 0
        var histories = 0
        composeTestRule.setContent {
            StartRunScreen(null, {}, {}, actionsEnabled, { histories++ }, true,
                onOpenMap = { maps++ }, onOpenRunSettings = { opens++ }, setupLoading = loading)
        }
        composeTestRule.onNodeWithTag(START_REFLECTION_SETUP_TAG).assertDoesNotExist()
        composeTestRule.onNodeWithTag(START_RUN_SETTINGS_TAG).assertIsNotEnabled()
            .performTouchInput { click() }
        composeTestRule.runOnIdle { assertEquals(0, opens) }
        composeTestRule.runOnIdle { loading = false }
        composeTestRule.onNodeWithTag(START_RUN_SETTINGS_TAG).performClick()
        composeTestRule.onNodeWithTag("startExpandMap").performClick()
        composeTestRule.onNodeWithTag(START_HISTORY_TAG).performClick()
        composeTestRule.runOnIdle {
            assertEquals(1, opens); assertEquals(1, maps); assertEquals(1, histories)
            actionsEnabled = false
        }
        for (tag in listOf(START_RUN_SETTINGS_TAG, "startExpandMap", START_HISTORY_TAG)) {
            composeTestRule.onNodeWithTag(tag).assertIsNotEnabled().performTouchInput { click() }
        }
        composeTestRule.runOnIdle { assertEquals(1, opens); assertEquals(1, maps); assertEquals(1, histories) }
    }

    @Test
    fun reflectionConnectionDialogRequiresBothInputsAndProtectsAnInProgressSave() {
        var saving by mutableStateOf(false)
        var hasSaved by mutableStateOf(false)
        var clears = 0
        var saves = 0
        val address = "https://demo.example.com/api/reflections/candidates"
        val code = "test_code_" + "x".repeat(43)
        composeTestRule.setContent {
            ReflectionConnectionDialog("", hasSaved, saving, null, {}, { endpoint, accessCode ->
                assertEquals(address, endpoint)
                assertEquals(code, accessCode)
                saves++
            }, { clears++ })
        }
        composeTestRule.onNodeWithTag(REFLECTION_CONNECTION_SAVE_TAG).assertIsNotEnabled()
        composeTestRule.onNodeWithTag(REFLECTION_CONNECTION_CLEAR_TAG).assertDoesNotExist()
        composeTestRule.onNodeWithTag(REFLECTION_CONNECTION_ADDRESS_TAG).performTextInput(address)
        composeTestRule.onNodeWithTag(REFLECTION_CONNECTION_SAVE_TAG).assertIsNotEnabled()
        composeTestRule.onNodeWithTag(REFLECTION_CONNECTION_CODE_TAG).performTextInput(code)
        composeTestRule.onNodeWithText("retries you choose in Log History", substring = true)
            .performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("saved run facts and any music context you chose to share", substring = true)
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Use only a service you trust", substring = true)
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(REFLECTION_CONNECTION_SAVE_TAG).performClick()
        composeTestRule.runOnIdle { assertEquals(1, saves); saving = true; hasSaved = true }
        composeTestRule.onNodeWithTag(REFLECTION_CONNECTION_SAVE_TAG).assertIsNotEnabled()
        composeTestRule.onNodeWithTag(REFLECTION_CONNECTION_CODE_TAG).assertIsNotEnabled()
        composeTestRule.onNodeWithTag(REFLECTION_CONNECTION_CLEAR_TAG).performScrollTo().assertIsNotEnabled()
        composeTestRule.runOnIdle { saving = false }
        composeTestRule.onNodeWithTag(REFLECTION_CONNECTION_CLEAR_TAG).performClick()
        composeTestRule.runOnIdle { assertEquals(1, clears) }
    }

    @Test
    fun speechIgnoresOldFocusAndEngineCallbacksAfterAnotherUtteranceStarts() {
        composeTestRule.runOnUiThread {
            val platform = ControlledSpeechPlatform()
            val speaker = AndroidReflectionSpeaker(composeTestRule.activity, platform, { _, _ -> }, {})
            platform.ready(true)
            var receiptsFinished = 0
            speaker.speakReceipt("Run saved.") { receiptsFinished++ }
            val first = platform.spoken.single().id
            val exact = "  Saved reflection.\nSecond line.  "
            speaker.speak(exact)
            val second = platform.spoken.last().id
            val stops = platform.stops

            platform.focusLosses.getValue(first).invoke(first)
            platform.done(first)
            platform.failed(first)
            assertEquals(stops, platform.stops)
            assertEquals(second, platform.focusOwner)
            assertEquals(exact, platform.spoken.last().text)
            assertEquals(0.94f, platform.spoken.last().rate, 0f)
            assertEquals(1, receiptsFinished)
            platform.done(second)
            assertEquals(null, platform.focusOwner)
            speaker.close()
        }
    }

    @Test
    fun receiptColdStartupHasSeparatePlaybackDeadlineAndTimesOutOnlyOnce() {
        composeTestRule.runOnUiThread {
            val platform = ControlledSpeechPlatform()
            val timeouts = mutableListOf<Pair<Runnable, Long>>()
            val cancelled = mutableListOf<Runnable>()
            val speaker = AndroidReflectionSpeaker(composeTestRule.activity, platform,
                { task, delay -> timeouts += task to delay }, { cancelled += it })
            var finished = 0
            speaker.speakReceipt("Run saved. Partial GPS distance, one mile. Elapsed time, 1 hour, 2 minutes, 3 seconds.") {
                finished++
            }
            val startup = timeouts.single()
            assertEquals(15_000L, startup.second)
            assertTrue(platform.spoken.isEmpty())
            platform.ready(true)
            val playback = timeouts.last()
            assertTrue(playback.second >= 30_000L)
            assertTrue(cancelled.contains(startup.first))
            val id = platform.spoken.single().id
            startup.first.run() // An obsolete startup deadline must not cut audible playback short.
            assertEquals(0, finished)
            assertEquals(id, platform.focusOwner)
            playback.first.run()
            platform.done(id)
            platform.failed(id)
            playback.first.run()
            assertEquals(1, finished)
            assertEquals(null, platform.focusOwner)
            speaker.close()
        }
    }

    @Test
    fun missingEngineAndRejectedFocusOrSpeechReleaseReceiptWithoutStalling() {
        composeTestRule.runOnUiThread {
            val platform = ControlledSpeechPlatform()
            val timeouts = mutableListOf<Runnable>()
            val speaker = AndroidReflectionSpeaker(composeTestRule.activity, platform,
                { task, _ -> timeouts += task }, {})
            var finished = 0
            speaker.speakReceipt("Waiting for engine.") { finished++ }
            timeouts.last().run()
            assertEquals(1, finished)
            platform.ready(true)
            assertTrue(platform.spoken.isEmpty())
            platform.focusGranted = false
            speaker.speakReceipt("Focus unavailable.") { finished++ }
            assertTrue(platform.spoken.isEmpty())
            assertEquals(2, finished)
            platform.focusGranted = true
            platform.speechAccepted = false
            speaker.speakReceipt("Engine rejected speech.") { finished++ }
            val rejectedId = platform.spoken.single().id
            platform.done(rejectedId)
            platform.failed(rejectedId)
            assertEquals(3, finished)
            assertEquals(null, platform.focusOwner)
            platform.speechAccepted = true
            platform.stopThrows = true
            speaker.speakReceipt("Asynchronous engine failure.") { finished++ }
            platform.failed(platform.spoken.last().id)
            assertEquals(4, finished)
            assertEquals(null, platform.focusOwner)
            speaker.close()
        }
    }

    @Test
    fun focusLostDuringAcquisitionCannotQueueAnAlreadyCancelledUtterance() {
        composeTestRule.runOnUiThread {
            val platform = ControlledSpeechPlatform().apply { loseFocusDuringRequest = true }
            val speaker = AndroidReflectionSpeaker(composeTestRule.activity, platform, { _, _ -> }, {})
            platform.ready(true)
            var finished = 0
            speaker.speakReceipt("Run saved.") { finished++ }
            assertEquals(1, finished)
            assertTrue(platform.spoken.isEmpty())
            assertEquals(null, platform.focusOwner)
            speaker.close()
        }
    }

    @Test
    fun instrumentalWaitsForExactReflectionSpeechAndFinishesOnlyOnceAfterItsCue() {
        composeTestRule.runOnUiThread {
            val platform = ControlledSpeechPlatform()
            val deadlines = mutableListOf<Pair<Runnable, Long>>()
            val speaker = AndroidReflectionSpeaker(composeTestRule.activity, platform,
                { task, delay -> deadlines += task to delay }, {})
            var finished = 0
            val exact = "  Exact saved reflection.\nSecond line.  "
            speaker.speakWithInstrumental(exact, onFinished = { finished++ })
            assertTrue(platform.spoken.isEmpty())
            assertTrue(platform.instrumentals.isEmpty())
            platform.ready(true)
            val voice = platform.spoken.single()
            assertEquals(exact, voice.text)
            assertEquals(0.94f, voice.rate, 0f)
            assertTrue(platform.instrumentals.isEmpty())

            platform.done(voice.id)
            val cue = platform.instrumentals.single()
            assertEquals(0, finished)
            assertEquals(15_000L, deadlines.last().second)
            // A delayed voice callback must not cancel the cue that now owns this request.
            platform.done(voice.id)
            platform.failed(voice.id)
            platform.focusLosses.getValue(voice.id).invoke(voice.id)
            assertEquals(1, platform.instrumentals.size)
            assertEquals(0, finished)
            cue.done(cue.id)
            cue.done(cue.id)
            cue.failed(cue.id)
            cue.focusLost(cue.id)
            deadlines.last().first.run()
            assertEquals(1, finished)
            speaker.close()
        }
    }

    @Test
    fun packagedRunStateInstrumentalDecodesAsTenSecondsOfAudio() {
        val decoder = MediaMetadataRetriever()
        try {
            composeTestRule.activity.resources.openRawResourceFd(R.raw.runstate_reflection_instrumental).use { asset ->
                decoder.setDataSource(asset.fileDescriptor, asset.startOffset, asset.length)
            }
            val duration = requireNotNull(decoder.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)).toLong()
            assertTrue("Bundled cue duration was $duration ms", duration in 9_500L..10_500L)
            val mime = requireNotNull(decoder.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE))
            assertTrue("Bundled cue MIME was $mime", mime.startsWith("audio/"))
        } finally {
            decoder.release()
        }
    }

    @Test
    fun packagedInstrumentalPlaysToOneNaturalCompletionThroughAndroidPlayer() {
        val finished = java.util.concurrent.CountDownLatch(1)
        val completions = java.util.concurrent.atomic.AtomicInteger()
        val errors = java.util.concurrent.atomic.AtomicInteger()
        val focusLosses = java.util.concurrent.atomic.AtomicInteger()
        var platform: ReflectionSpeechPlatform? = null
        var accepted = false
        try {
            composeTestRule.runOnUiThread {
                platform = AndroidReflectionSpeechPlatform(composeTestRule.activity)
                accepted = requireNotNull(platform).playInstrumental("packaged-cue",
                    onDone = { completions.incrementAndGet(); finished.countDown() },
                    onError = { errors.incrementAndGet(); finished.countDown() },
                    onFocusLost = { focusLosses.incrementAndGet(); finished.countDown() })
            }
            assertTrue("Android player rejected the bundled instrumental", accepted)
            // The wait stays off the UI thread so preparation/completion callbacks can run.
            assertTrue("Bundled instrumental did not complete within sixteen seconds",
                finished.await(16, java.util.concurrent.TimeUnit.SECONDS))
            composeTestRule.runOnUiThread {
                assertEquals(1, completions.get())
                assertEquals(0, errors.get())
                assertEquals(0, focusLosses.get())
            }
        } finally {
            composeTestRule.runOnUiThread { platform?.close() }
        }
    }

    @Test
    fun interruptedOrFailedVoiceNeverStartsAnInstrumental() {
        composeTestRule.runOnUiThread {
            listOf("initialization", "startup timeout", "focus denial", "focus loss",
                "speech rejection", "speech error", "playback timeout", "stop", "close").forEach { failure ->
                val platform = ControlledSpeechPlatform()
                val deadlines = mutableListOf<Runnable>()
                val speaker = AndroidReflectionSpeaker(composeTestRule.activity, platform,
                    { task, _ -> deadlines += task }, {})
                var finished = 0
                if (failure != "initialization" && failure != "startup timeout") platform.ready(true)
                platform.focusGranted = failure != "focus denial"
                platform.loseFocusDuringRequest = failure == "focus loss"
                platform.speechAccepted = failure != "speech rejection"
                speaker.speakWithInstrumental("Saved reflection.", onFinished = { finished++ })
                when (failure) {
                    "initialization" -> platform.ready(false)
                    "startup timeout", "playback timeout" -> deadlines.last().run()
                    "speech error" -> platform.failed(platform.spoken.single().id)
                    "stop" -> { platform.stopThrows = true; speaker.stop() }
                    "close" -> speaker.close()
                }
                // Native engines sometimes report done after stop/error: it is not success.
                platform.spoken.lastOrNull()?.let { platform.done(it.id); platform.failed(it.id) }
                platform.ready(true)
                assertEquals(failure, 1, finished)
                assertTrue(failure, platform.instrumentals.isEmpty())
                speaker.close()
            }
        }
    }

    @Test
    fun cueFailureFocusLossTimeoutAndStopFinishWithoutReplaying() {
        composeTestRule.runOnUiThread {
            listOf("rejected", "error", "focus loss", "synchronous focus loss", "timeout", "stop", "close")
                .forEach { failure ->
                    val platform = ControlledSpeechPlatform().apply {
                        instrumentalAccepted = failure != "rejected"
                        loseFocusDuringInstrumental = failure == "synchronous focus loss"
                    }
                    val deadlines = mutableListOf<Runnable>()
                    val speaker = AndroidReflectionSpeaker(composeTestRule.activity, platform,
                        { task, _ -> deadlines += task }, {})
                    platform.ready(true)
                    var finished = 0
                    speaker.speakWithInstrumental("Saved reflection.", onFinished = { finished++ })
                    platform.done(platform.spoken.single().id)
                    val cue = platform.instrumentals.single()
                    when (failure) {
                        "error" -> cue.failed(cue.id)
                        "focus loss" -> cue.focusLost(cue.id)
                        "timeout" -> deadlines.last().run()
                        "stop" -> speaker.stop()
                        "close" -> speaker.close()
                    }
                    cue.done(cue.id)
                    cue.failed(cue.id)
                    cue.focusLost(cue.id)
                    assertEquals(failure, 1, finished)
                    assertEquals(failure, 1, platform.spoken.size)
                    assertEquals(failure, 1, platform.instrumentals.size)
                    assertTrue(failure, platform.instrumentalStops > 0)
                    speaker.close()
                }
        }
    }

    @Test
    fun oldVoiceAndCueCallbacksCannotFinishTheNextReflection() {
        composeTestRule.runOnUiThread {
            val platform = ControlledSpeechPlatform()
            val speaker = AndroidReflectionSpeaker(composeTestRule.activity, platform, { _, _ -> }, {})
            platform.ready(true)
            var firstFinished = 0
            var secondFinished = 0
            speaker.speakWithInstrumental("First.", onFinished = { firstFinished++ })
            val firstVoice = platform.spoken.single().id
            platform.done(firstVoice)
            val firstCue = platform.instrumentals.single()
            speaker.speakWithInstrumental("Second.", onFinished = { secondFinished++ })
            val secondVoice = platform.spoken.last().id
            assertEquals(1, firstFinished)
            platform.done(firstVoice)
            platform.failed(firstVoice)
            platform.focusLosses.getValue(firstVoice).invoke(firstVoice)
            firstCue.done(firstCue.id)
            firstCue.failed(firstCue.id)
            firstCue.focusLost(firstCue.id)
            assertEquals(secondVoice, platform.focusOwner)
            assertEquals(0, secondFinished)
            assertEquals(1, platform.instrumentals.size)
            platform.done(secondVoice)
            val secondCue = platform.instrumentals.last()
            firstCue.done(firstCue.id)
            firstCue.focusLost(firstCue.id)
            assertEquals(0, secondFinished)
            secondCue.done(secondCue.id)
            assertEquals(1, secondFinished)
            assertEquals(1, firstFinished)
            speaker.close()
        }
    }

    @Test
    fun instrumentalGuardIsCheckedAfterSpeechAndPlainHistorySpeechNeverPlaysCue() {
        composeTestRule.runOnUiThread {
            val platform = ControlledSpeechPlatform()
            val speaker = AndroidReflectionSpeaker(composeTestRule.activity, platform, { _, _ -> }, {})
            platform.ready(true)
            var visible = true
            var finished = 0
            speaker.speakWithInstrumental("Completion.", { visible }, { finished++ })
            visible = false
            platform.done(platform.spoken.single().id)
            assertEquals(1, finished)
            assertTrue(platform.instrumentals.isEmpty())
            speaker.speak("History voice only.")
            platform.done(platform.spoken.last().id)
            speaker.speakReceipt("Run saved.") { finished++ }
            platform.done(platform.spoken.last().id)
            assertEquals(2, finished)
            assertTrue(platform.instrumentals.isEmpty())
            speaker.close()
        }
    }

    @Test
    fun completionReceiptFinishesBeforeReflectionAndDoesNotReplayOnReturn() {
        val runId = "0f6a2c1e-9d43-4b7a-9c21-7b5e8a4d1f30"
        val receipts = mutableListOf<String>()
        val reflections = mutableListOf<String>()
        var finishReceipt: (() -> Unit)? = null
        var visible by mutableStateOf(true)
        val speaker = object : ReflectionSpeaker {
            override fun speak(text: String) { reflections += text }
            override fun speakReceipt(text: String, onFinished: () -> Unit) {
                receipts += text
                finishReceipt = onFinished
            }
            override fun stop() = Unit
            override fun close() = Unit
        }
        composeTestRule.setContent {
            var started by remember { mutableStateOf<String?>(null) }
            var finished by remember { mutableStateOf<String?>(null) }
            var spoken by remember { mutableStateOf<String?>(null) }
            RunCompleteReceiptEffect(runId, runId, "Run saved.", started, finished, visible,
                { speaker }, { started = it }, { finished = it })
            RunCompleteSpeechEffect(runId, runId, spoken,
                RunReflectionState.Ready(runId, "Exact reflection."),
                visible && finished == runId, { speaker }, { spoken = it })
        }
        composeTestRule.runOnIdle {
            assertEquals(listOf("Run saved."), receipts)
            assertTrue(reflections.isEmpty())
            requireNotNull(finishReceipt).invoke()
        }
        composeTestRule.runOnIdle { assertEquals(listOf("Exact reflection."), reflections) }
        composeTestRule.runOnIdle { visible = false }
        composeTestRule.waitForIdle()
        composeTestRule.runOnIdle { visible = true }
        composeTestRule.runOnIdle {
            assertEquals(1, receipts.size)
            assertEquals(1, reflections.size)
        }
    }

    @Test
    fun energyInvitationFollowsFactsAndHoldsReflectionUntilItsOwnCompletion() {
        val id = "0f6a2c1e-9d43-4b7a-9c21-7b5e8a4d1f30"
        val utterances = mutableListOf<String>()
        val doneCallbacks = mutableListOf<() -> Unit>()
        val reflections = mutableListOf<String>()
        val speaker = object : ReflectionSpeaker {
            override fun speak(text: String) { reflections += text }
            override fun speakReceipt(text: String, onFinished: () -> Unit) {
                utterances += text
                doneCallbacks += onFinished
            }
            override fun stop() = Unit
            override fun close() = Unit
        }
        composeTestRule.setContent {
            var started by remember { mutableStateOf<String?>(null) }
            var finished by remember { mutableStateOf<String?>(null) }
            var spoken by remember { mutableStateOf<String?>(null) }
            RunCompleteReceiptEffect(id, id, "Run complete. Three miles.", started, finished,
                true, { speaker }, { started = it }, { finished = it }, shouldPromptEnergy = { true })
            RunCompleteSpeechEffect(id, id, spoken, RunReflectionState.Ready(id, "Exact reflection."),
                finished == id, { speaker }, { spoken = it })
        }
        composeTestRule.runOnIdle {
            assertEquals(listOf("Run complete. Three miles."), utterances)
            doneCallbacks.first().invoke()
            doneCallbacks.first().invoke() // Late duplicate facts callback cannot repeat the invitation.
        }
        composeTestRule.runOnIdle {
            assertEquals(listOf("Run complete. Three miles.", "How’s your energy now?"), utterances)
            assertTrue(reflections.isEmpty())
            doneCallbacks.last().invoke()
        }
        composeTestRule.runOnIdle { assertEquals(listOf("Exact reflection."), reflections) }
    }

    @Test
    fun choosingEnergyDuringFactsSkipsTheSpokenInvitation() {
        val id = "0f6a2c1e-9d43-4b7a-9c21-7b5e8a4d1f30"
        val utterances = mutableListOf<String>()
        var done: (() -> Unit)? = null
        var answered by mutableStateOf(false)
        var finished: String? = null
        val speaker = object : ReflectionSpeaker {
            override fun speak(text: String) = Unit
            override fun speakReceipt(text: String, onFinished: () -> Unit) {
                utterances += text; done = onFinished
            }
            override fun stop() = Unit
            override fun close() = Unit
        }
        composeTestRule.setContent {
            var started by remember { mutableStateOf<String?>(null) }
            RunCompleteReceiptEffect(id, id, "Run complete.", started, null, true,
                { speaker }, { started = it }, { finished = it }, shouldPromptEnergy = { !answered })
        }
        composeTestRule.runOnIdle { answered = true }
        composeTestRule.runOnIdle { requireNotNull(done).invoke() }
        composeTestRule.runOnIdle {
            assertEquals(listOf("Run complete."), utterances)
            assertEquals(id, finished)
        }
    }

    @Test
    fun restoredConsumedReceiptReleasesItsWindowWithoutRepeatingSpeech() {
        val runId = "0f6a2c1e-9d43-4b7a-9c21-7b5e8a4d1f30"
        val finished = mutableListOf<String>()
        composeTestRule.setContent {
            var finishedId by remember { mutableStateOf<String?>(null) }
            RunCompleteReceiptEffect(runId, runId, "Run saved.", runId, finishedId, true,
                { error("A consumed receipt must not create another voice engine.") },
                { error("A consumed receipt must not start twice.") },
                { finished += it; finishedId = it })
        }
        composeTestRule.runOnIdle { assertEquals(listOf(runId), finished) }
    }

    @Test
    fun interruptedReceiptFinishesOnceEvenIfTheVoiceEngineReportsLate() {
        val runId = "0f6a2c1e-9d43-4b7a-9c21-7b5e8a4d1f30"
        val finishes = mutableListOf<String>()
        var visible by mutableStateOf(true)
        var lateCompletion: (() -> Unit)? = null
        var receipts = 0
        val speaker = object : ReflectionSpeaker {
            override fun speak(text: String) = Unit
            override fun speakReceipt(text: String, onFinished: () -> Unit) {
                receipts++
                lateCompletion = onFinished
            }
            override fun stop() = Unit
            override fun close() = Unit
        }
        composeTestRule.setContent {
            var started by remember { mutableStateOf<String?>(null) }
            var finished by remember { mutableStateOf<String?>(null) }
            RunCompleteReceiptEffect(runId, runId, "Run saved.", started, finished, visible,
                { speaker }, { started = it }, { finishes += it; finished = it })
        }
        composeTestRule.runOnIdle { visible = false }
        composeTestRule.runOnIdle {
            requireNotNull(lateCompletion).invoke()
            visible = true
        }
        composeTestRule.runOnIdle {
            assertEquals(listOf(runId), finishes)
            assertEquals(1, receipts)
        }
    }

    @Test
    fun activeLargeTextKeepsWholeTimerAndPauseResumeFinishControlsReachable() {
        var paused by mutableStateOf(false)
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                ActiveRunScreen(paused, fixtureMetrics().copy(active = "10:59:59"), false, true,
                    {}, {}, {}, modifier = Modifier.size(360.dp, 480.dp))
            }
        }
        composeTestRule.onNodeWithText(PAUSE).assertIsDisplayed()
        composeTestRule.onNodeWithTag(ACTIVE_RUN_ACTIVE_TAG).performScrollTo()
            .assertTextEquals("10:59:59").assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        composeTestRule.onNodeWithTag(ACTIVE_RUN_ACTIVE_TAG)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertFalse(layouts.single().hasVisualOverflow)
        composeTestRule.runOnIdle { paused = true }
        composeTestRule.onNodeWithText(RESUME).assertIsDisplayed()
        composeTestRule.onNodeWithTag(HOLD_TO_STOP_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Active time and distance are paused.")
            .performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag(ACTIVE_RUN_METRICS_TAG).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun activeHierarchyKeepsExactMetricValuesAtLargeTextSizes() {
        var paused by mutableStateOf(false)
        var fontScale by mutableStateOf(1.5f)
        val metrics = fixtureMetrics().copy(
            distance = "1234.56 km",
            elapsed = "100:59:59",
            averagePace = "123:59 /km",
            active = "100:59:59"
        )
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                ActiveRunScreen(paused, metrics, false, true,
                    { paused = true }, { paused = false }, {},
                    modifier = Modifier.size(360.dp, 480.dp))
            }
        }

        fun assertWholeValue(tag: String, value: String) {
            val node = composeTestRule.onNodeWithTag(tag)
            node.performScrollTo().assertTextEquals(value).assertIsDisplayed()
            val layouts = mutableListOf<TextLayoutResult>()
            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertFalse(layouts.single().hasVisualOverflow)
        }

        for (scale in listOf(1.5f, 2f)) {
            composeTestRule.runOnIdle { fontScale = scale }
            composeTestRule.onNodeWithText(PAUSE).assertIsDisplayed()
            assertWholeValue(ACTIVE_RUN_DISTANCE_TAG, metrics.distance)
            assertEquals(1, composeTestRule.onAllNodesWithText(metrics.distance).fetchSemanticsNodes().size)
            val distance = composeTestRule.onNodeWithTag(ACTIVE_RUN_DISTANCE_TAG).fetchSemanticsNode()
            val circle = composeTestRule.onNodeWithTag(ACTIVE_RUN_CIRCLE_TAG).fetchSemanticsNode()
            assertTrue(distance.positionInRoot.y + distance.size.height <= circle.positionInRoot.y)
            composeTestRule.onNodeWithTag(ACTIVE_RUN_ELAPSED_TAG).assertDoesNotExist()
            assertWholeValue(ACTIVE_RUN_PACE_TAG, metrics.averagePace)
            assertWholeValue(ACTIVE_RUN_ACTIVE_TAG, metrics.active)
            composeTestRule.onNodeWithText(metrics.sourceLabel).performScrollTo().assertIsDisplayed()

            composeTestRule.onNodeWithText(PAUSE).performClick()
            composeTestRule.onNodeWithText(RESUME).assertIsDisplayed()
            composeTestRule.onNodeWithTag(HOLD_TO_STOP_TAG).assertIsDisplayed()
            composeTestRule.onNodeWithText("Active time and distance are paused.")
                .performScrollTo().assertIsDisplayed()
            composeTestRule.onNodeWithText(RESUME).performClick()
        }
    }

    @Test
    fun activeGpsUsesFreshSignalEvenWhenEarlierDistanceExists() {
        var signal by mutableStateOf(GpsSignalStatus.ACQUIRING)
        composeTestRule.setContent {
            ActiveRunScreen(false, fixtureMetrics().copy(
                distance = "0.03 mi", sourceLabel = "GPS metrics · partial",
                gpsDistanceAvailable = true), false, true, {}, {}, {},
                isGpsRun = true, gpsServicePresent = true, gpsSignalStatus = signal)
        }
        composeTestRule.onNodeWithText("ILLUSTRATIVE MAP · GPS WAITING").assertExists()
        composeTestRule.onNodeWithText("ILLUSTRATIVE MAP · GPS RECORDING").assertDoesNotExist()
        composeTestRule.runOnIdle { signal = GpsSignalStatus.RECORDING }
        composeTestRule.onNodeWithText("ILLUSTRATIVE MAP · GPS RECORDING").assertExists()
        composeTestRule.onNodeWithText("ILLUSTRATIVE MAP · GPS WAITING").assertDoesNotExist()
        composeTestRule.runOnIdle { signal = GpsSignalStatus.WEAK }
        composeTestRule.onNodeWithText("ILLUSTRATIVE MAP · GPS WEAK SIGNAL").assertExists()
        composeTestRule.onNodeWithText("ILLUSTRATIVE MAP · GPS RECORDING").assertDoesNotExist()
        composeTestRule.runOnIdle { signal = GpsSignalStatus.STALE }
        composeTestRule.onNodeWithText("ILLUSTRATIVE MAP · GPS SIGNAL UNAVAILABLE").assertExists()
        composeTestRule.onNodeWithText("ILLUSTRATIVE MAP · GPS RECORDING").assertDoesNotExist()
        composeTestRule.onNodeWithTag(ACTIVE_RUN_DISTANCE_TAG).assertTextEquals("0.03 mi")
        composeTestRule.onNodeWithText(PAUSE).assertIsEnabled()
    }

    @Test
    fun liveMapEntryExistsOnlyForGpsAndForwardsTheAction() {
        var gps by mutableStateOf(true)
        var paused by mutableStateOf(false)
        var opens = 0
        composeTestRule.setContent {
            RunStateScreen(
                model = RunUiModel(if (paused) RunUiState.ActivePaused else RunUiState.ActiveRunning),
                metrics = fixtureMetrics(), countdownDigit = null,
                onStart = {}, onCancelCountdown = {}, onRetryInitialization = {}, onRetryStart = {},
                onPause = {}, onResume = {}, onStop = {}, onStartAnother = {},
                isGpsRun = gps, gpsServicePresent = true, onOpenRunMap = { opens++ })
        }
        composeTestRule.onNodeWithTag(ACTIVE_RUN_MAP_BUTTON_TAG).performScrollTo().performClick()
        composeTestRule.runOnIdle { assertEquals(1, opens); paused = true }
        composeTestRule.onNodeWithText("View recorded map").performScrollTo().assertIsDisplayed()
        composeTestRule.runOnIdle { gps = false }
        composeTestRule.onNodeWithTag(ACTIVE_RUN_MAP_BUTTON_TAG).assertDoesNotExist()
        composeTestRule.onNodeWithText(RESUME).assertIsEnabled()
    }

    @Test
    fun mapDialogKeepsControlsAndDistinguishesStaleAndPausedLocations() {
        var state by mutableStateOf(GpsMapMarkerState.CURRENT)
        var paused by mutableStateOf(false)
        var open by mutableStateOf(true)
        composeTestRule.setContent {
            if (open) ActiveRunMapDialog(
                GpsMapEvidence("map-ui-test", true, marker = GpsMapMarker(
                    GpsMapCoordinate(29.7174, -95.3905), 8.0, state)),
                paused = paused, actionsEnabled = true, actionFailed = false,
                onPause = { paused = true; state = GpsMapMarkerState.PAUSED },
                onResume = { paused = false; state = GpsMapMarkerState.LAST_KNOWN },
                onDismiss = { open = false })
        }
        composeTestRule.onNodeWithTag(RECORDED_RUN_MAP_STATUS_TAG)
            .assertTextEquals("Current recorded location")
        composeTestRule.runOnIdle { state = GpsMapMarkerState.LAST_KNOWN }
        composeTestRule.onNodeWithTag(RECORDED_RUN_MAP_STATUS_TAG)
            .assertTextEquals("Last recorded location · current position is unknown")
        composeTestRule.onNodeWithText("Pause run").performClick()
        composeTestRule.onNodeWithTag(RECORDED_RUN_MAP_STATUS_TAG)
            .assertTextEquals("Paused · last recorded location")
        composeTestRule.onNodeWithText("Resume run").performClick()
        composeTestRule.onNodeWithText("Pause run").assertIsEnabled()
        composeTestRule.onNodeWithText("Close").performClick()
        composeTestRule.onNodeWithTag(ACTIVE_RUN_MAP_DIALOG_TAG).assertDoesNotExist()
    }

    @Test
    fun mapWithoutUsableEvidenceStillOffersPauseAndCloseAtLargeText() {
        var paused = false
        var closed = false
        composeTestRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.8f)) {
                ActiveRunMapDialog(GpsMapEvidence("map-unavailable-test", true, evidenceUnavailable = true),
                    paused = false, actionsEnabled = true, actionFailed = false,
                    onPause = { paused = true }, onResume = {}, onDismiss = { closed = true })
            }
        }
        composeTestRule.onNodeWithText("Pause run").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithText("Close").assertIsDisplayed().performClick()
        composeTestRule.runOnIdle { assertTrue(paused); assertTrue(closed) }
    }

    @Test
    fun nativeMapRendersLocalRouteAndMarkerAcrossBackgroundWithoutTileRequests() {
        val first = GpsMapCoordinate(29.7174, -95.3905)
        val second = GpsMapCoordinate(29.7184, -95.3905)
        var visible by mutableStateOf(true)
        composeTestRule.setContent {
            if (visible) RecordedRunMap(
                GpsMapEvidence("native-map-test", true, sections = listOf(listOf(first, second)),
                    marker = GpsMapMarker(second, 6.0, GpsMapMarkerState.CURRENT)),
                Modifier.fillMaxSize().systemBarsPadding(), enableNativeMap = true,
                loadStreetTiles = false)
        }
        composeTestRule.onNodeWithTag(RECORDED_RUN_MAP_RECENTER_TAG).assertIsDisplayed()
        // This is a native renderer check, not a request to the community tile service.
        composeTestRule.waitUntil(15_000L) {
            val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            if (bitmap == null) false else {
                try {
                    var markerPixels = 0
                    for (y in 0 until bitmap.height step 3) {
                        for (x in 0 until bitmap.width step 3) {
                            val pixel = bitmap.getPixel(x, y)
                            if (kotlin.math.abs(android.graphics.Color.red(pixel) - 22) < 6 &&
                                kotlin.math.abs(android.graphics.Color.green(pixel) - 116) < 6 &&
                                kotlin.math.abs(android.graphics.Color.blue(pixel) - 154) < 6) markerPixels++
                        }
                    }
                    markerPixels > 10
                } finally { bitmap.recycle() }
            }
        }
        composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeTestRule.onNodeWithTag(RECORDED_RUN_MAP_RECENTER_TAG).performClick()
        composeTestRule.onNodeWithTag(RECORDED_RUN_MAP_STATUS_TAG)
            .assertTextEquals("Current recorded location")
        composeTestRule.runOnIdle { visible = false }
        composeTestRule.onNodeWithTag(RECORDED_RUN_MAP_TAG).assertDoesNotExist()
        composeTestRule.runOnIdle { visible = true }
        composeTestRule.onNodeWithTag(RECORDED_RUN_MAP_RECENTER_TAG).assertIsDisplayed()
    }

    @Test
    fun optionalStartMapLocationAndCloseDoNotStartAndOnlyStartBeginsCountdown() {
        var locationRequests = 0
        var starts = 0
        var open by mutableStateOf(false)
        var gpsSelected by mutableStateOf(true)
        var state by mutableStateOf(RunUiState.ReadyToStart)
        composeTestRule.setContent {
            RunStateScreen(
                model = RunUiModel(state), countdownDigit = 3,
                onStart = { starts++; state = RunUiState.Countdown },
                onCancelCountdown = {}, onRetryInitialization = {},
                onRetryStart = {}, onPause = {}, onResume = {}, onStop = {}, onStartAnother = {},
                gpsSelected = gpsSelected, liveMapEnabled = true, mapEvidence = null,
                nativeMapsEnabled = true, loadStreetTiles = false,
                previewMapStatus = "Enable precise location to show your position.",
                onRequestMapLocation = { locationRequests++ },
                onOpenRunMap = { open = true }, mapExpanded = open,
                onCloseRunMap = { open = false },
                modifier = Modifier.fillMaxSize().systemBarsPadding()
            )
        }
        composeTestRule.onNodeWithTag(START_RUN_BUTTON_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(START_MAP_LOCATION_ACCESS_TAG).assertDoesNotExist()
        composeTestRule.onNodeWithTag(ACTIVE_RUN_MAP_DIALOG_TAG).assertDoesNotExist()
        composeTestRule.onNodeWithText("© OpenStreetMap contributors").assertDoesNotExist()
        assertTrue(nativeMapViewsInActivity().isEmpty())

        composeTestRule.onNodeWithTag("startExpandMap").performScrollTo().performClick()
        composeTestRule.onNodeWithTag(ACTIVE_RUN_MAP_DIALOG_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Enable precise location to show your position.").assertIsDisplayed()
        composeTestRule.onNodeWithText("© OpenStreetMap contributors").assertIsDisplayed()
        composeTestRule.onNodeWithTag(START_MAP_LOCATION_ACCESS_TAG).assertIsDisplayed().performClick()
        composeTestRule.runOnIdle {
            assertEquals(1, locationRequests)
            assertEquals(0, starts)
        }
        assertTrue(nativeMapViewsInActivity().isEmpty())

        composeTestRule.onNodeWithText("Close map").performClick()
        composeTestRule.onNodeWithTag(ACTIVE_RUN_MAP_DIALOG_TAG).assertDoesNotExist()
        composeTestRule.onNodeWithTag(START_MAP_LOCATION_ACCESS_TAG).assertDoesNotExist()
        composeTestRule.onNodeWithTag(START_RUN_BUTTON_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("© OpenStreetMap contributors").assertDoesNotExist()
        composeTestRule.runOnIdle {
            assertEquals(0, starts)
            gpsSelected = false
        }
        composeTestRule.onNodeWithTag("startExpandMap").assertDoesNotExist()
        assertTrue(nativeMapViewsInActivity().isEmpty())
        composeTestRule.runOnIdle { gpsSelected = true; open = true }
        composeTestRule.onNodeWithTag(START_RUN_MAP_START_TAG).assertIsDisplayed().performClick()
        composeTestRule.onNodeWithText(COUNTDOWN).assertIsDisplayed()
        // A stale expanded flag cannot put a live preview behind the countdown.
        composeTestRule.onNodeWithTag(ACTIVE_RUN_MAP_DIALOG_TAG).assertDoesNotExist()
        composeTestRule.onNodeWithText("© OpenStreetMap contributors").assertDoesNotExist()
        composeTestRule.runOnIdle { assertEquals(1, starts) }
        assertTrue(nativeMapViewsInActivity().isEmpty())
    }

    @Test
    fun nativeMapsOpenOnlyOnRequestAndActiveMapUsesItsOwnEvidence() {
        // Deliberately synthetic public-park coordinates. No Room run, device location engine,
        // community map tiles, reflection provider or audio is used by this integrated check.
        val previewPoint = GpsMapCoordinate(29.7174, -95.3905)
        val runStart = GpsMapCoordinate(29.7180, -95.3900)
        val runPoint = GpsMapCoordinate(29.7185, -95.3897)
        var state by mutableStateOf(RunUiState.ReadyToStart)
        var evidence by mutableStateOf<GpsMapEvidence?>(GpsMapEvidence(
            "foreground-gps-preview", true,
            marker = GpsMapMarker(previewPoint, 6.0, GpsMapMarkerState.CURRENT)))
        var expanded by mutableStateOf(false)
        var starts = 0
        var pauses = 0
        var resumes = 0
        composeTestRule.setContent {
            RunStateScreen(
                model = RunUiModel(state), countdownDigit = 3,
                metrics = fixtureMetrics().copy(sourceLabel = "GPS metrics · partial", gpsDistanceAvailable = true),
                onStart = { starts++; expanded = false; state = RunUiState.Countdown },
                onCancelCountdown = {},
                onRetryInitialization = {}, onRetryStart = {},
                onPause = {
                    pauses++
                    state = RunUiState.ActivePaused
                    evidence = evidence?.copy(marker = evidence?.marker?.copy(state = GpsMapMarkerState.PAUSED))
                },
                onResume = {
                    resumes++
                    state = RunUiState.ActiveRunning
                    evidence = evidence?.copy(marker = evidence?.marker?.copy(state = GpsMapMarkerState.LAST_KNOWN))
                }, onStop = {}, onStartAnother = {},
                gpsSelected = true, gpsReady = true, isGpsRun = true, gpsServicePresent = true,
                gpsSignalStatus = GpsSignalStatus.RECORDING,
                liveMapEnabled = true, mapEvidence = evidence, nativeMapsEnabled = true,
                loadStreetTiles = false, previewMapStatus = "Current preview location",
                onOpenRunMap = { expanded = true }, mapExpanded = expanded,
                onCloseRunMap = { expanded = false },
                modifier = Modifier.fillMaxSize().systemBarsPadding().testTag("syntheticLiveMapJourney")
            )
        }
        // Even an immediately available GPS preview cannot create a native map before a tap.
        composeTestRule.onNodeWithTag(START_RUN_BUTTON_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(ACTIVE_RUN_MAP_DIALOG_TAG).assertDoesNotExist()
        composeTestRule.onNodeWithText("© OpenStreetMap contributors").assertDoesNotExist()
        assertTrue(nativeMapViewsInActivity().isEmpty())
        saveNativePreview("syntheticLiveMapJourney", "start-live-map-preview.png")
        composeTestRule.onNodeWithTag("startExpandMap").performScrollTo().performClick()
        composeTestRule.waitUntil(15_000L) { nativeMapViewsInActivity().size == 1 }
        val firstView = nativeMapViewsInActivity().single()
        val map = AtomicReference<MapLibreMap?>(null)
        composeTestRule.runOnUiThread { firstView.getMapAsync { map.set(it) } }

        fun assertInteractiveMap() {
            composeTestRule.runOnUiThread {
                val settings = checkNotNull(map.get()).uiSettings
                assertTrue("The explicitly opened map allows panning", settings.isScrollGesturesEnabled)
                assertTrue("The explicitly opened map allows zooming", settings.isZoomGesturesEnabled)
                assertFalse("The running map stays north-up", settings.isRotateGesturesEnabled)
                assertFalse("The running map stays flat", settings.isTiltGesturesEnabled)
            }
        }

        fun awaitOverlay(marker: GpsMapCoordinate?, hasRoute: Boolean) {
            composeTestRule.waitUntil(15_000L) {
                var matches = false
                composeTestRule.runOnUiThread {
                    val style = map.get()?.style
                    val markerSource = style?.getSourceAs<GeoJsonSource>("runstate-marker")
                    val routeSource = style?.getSourceAs<GeoJsonSource>("runstate-route")
                    val anchorSource = style?.getSourceAs<GeoJsonSource>("runstate-anchors")
                    if (markerSource != null && routeSource != null && anchorSource != null) {
                        val points = markerSource.querySourceFeatures(null).mapNotNull { it.geometry() as? Point }
                        val markerMatches = if (marker == null) points.isEmpty() else points.isNotEmpty() && points.all {
                            kotlin.math.abs(it.latitude() - marker.latitude) < 0.0001 &&
                                kotlin.math.abs(it.longitude() - marker.longitude) < 0.0001
                        }
                        val routeMatches = routeSource.querySourceFeatures(null).isNotEmpty() == hasRoute
                        val noOldAnchors = marker != null || anchorSource.querySourceFeatures(null).isEmpty()
                        matches = markerMatches && routeMatches && noOldAnchors
                    }
                }
                matches
            }
        }

        awaitOverlay(previewPoint, hasRoute = false)
        assertInteractiveMap()
        composeTestRule.onNodeWithText("© OpenStreetMap contributors").assertIsDisplayed()
        composeTestRule.onNodeWithTag(START_RUN_BUTTON_TAG).assertDoesNotExist()
        composeTestRule.runOnIdle { assertEquals(0, starts) }

        composeTestRule.onNodeWithText("Close map").performClick()
        composeTestRule.onNodeWithTag(ACTIVE_RUN_MAP_DIALOG_TAG).assertDoesNotExist()
        composeTestRule.onNodeWithTag(START_RUN_BUTTON_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("© OpenStreetMap contributors").assertDoesNotExist()
        composeTestRule.waitUntil(15_000L) { nativeMapViewsInActivity().isEmpty() }

        composeTestRule.onNodeWithTag(START_RUN_BUTTON_TAG).assertIsDisplayed().performClick()
        composeTestRule.onNodeWithText(COUNTDOWN).assertIsDisplayed()
        composeTestRule.runOnIdle { assertEquals(1, starts) }
        assertTrue(nativeMapViewsInActivity().isEmpty())

        composeTestRule.runOnIdle {
            state = RunUiState.ActiveRunning
            evidence = GpsMapEvidence("synthetic-running-owner", true)
        }
        composeTestRule.onNodeWithText(PAUSE).assertIsEnabled()
        composeTestRule.onNodeWithTag(ACTIVE_RUN_MAP_DIALOG_TAG).assertDoesNotExist()
        assertTrue(nativeMapViewsInActivity().isEmpty())
        saveNativePreview("syntheticLiveMapJourney", "active-live-map-preview.png")
        composeTestRule.onNodeWithTag(ACTIVE_RUN_MAP_BUTTON_TAG).performScrollTo().performClick()
        composeTestRule.onNodeWithTag(ACTIVE_RUN_MAP_DIALOG_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText(PAUSE).assertIsEnabled()
        // The old preview must not be recycled as this run's first location.
        assertTrue(nativeMapViewsInActivity().isEmpty())

        composeTestRule.runOnIdle {
            evidence = GpsMapEvidence("synthetic-running-owner", true,
                sections = listOf(listOf(runStart, runPoint)),
                marker = GpsMapMarker(runPoint, 6.0, GpsMapMarkerState.CURRENT))
        }
        composeTestRule.waitUntil(15_000L) { nativeMapViewsInActivity().size == 1 }
        val activeView = nativeMapViewsInActivity().single()
        assertFalse("Closing Start must dispose its renderer", firstView === activeView)
        map.set(null)
        composeTestRule.runOnUiThread { activeView.getMapAsync { map.set(it) } }
        awaitOverlay(runPoint, hasRoute = true)
        assertInteractiveMap()
        composeTestRule.onNodeWithTag(RECORDED_RUN_MAP_RECENTER_TAG).assertIsDisplayed().performClick()
        composeTestRule.onNodeWithText(PAUSE).performClick()
        composeTestRule.onNodeWithText(RESUME).assertIsEnabled()
        composeTestRule.runOnIdle { assertEquals(1, pauses) }

        composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeTestRule.onNodeWithText(RESUME).assertIsEnabled()
        assertSame(activeView, nativeMapViewsInActivity().single())
        assertInteractiveMap()
        awaitOverlay(runPoint, hasRoute = true)
        composeTestRule.onNodeWithText(RESUME).performClick()
        composeTestRule.onNodeWithText(PAUSE).assertIsEnabled()
        composeTestRule.runOnIdle { assertEquals(1, resumes) }
        composeTestRule.runOnIdle { evidence = null }
        awaitOverlay(null, hasRoute = false)
        composeTestRule.onNodeWithText("Close map").performClick()
        composeTestRule.onNodeWithTag(ACTIVE_RUN_MAP_DIALOG_TAG).assertDoesNotExist()
        composeTestRule.onNodeWithText(PAUSE).assertIsEnabled()
        composeTestRule.onNodeWithTag(ACTIVE_RUN_MAP_BUTTON_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("© OpenStreetMap contributors").assertDoesNotExist()
        composeTestRule.waitUntil(15_000L) { nativeMapViewsInActivity().isEmpty() }
    }

    /** Inspect the real Android view tree, rather than counting a Compose placeholder tag. */
    private fun nativeMapViewsInActivity(): List<MapView> {
        val found = mutableListOf<MapView>()
        composeTestRule.runOnUiThread {
            fun visit(view: View) {
                if (view is MapView) found.add(view)
                if (view is ViewGroup) for (index in 0 until view.childCount) visit(view.getChildAt(index))
            }
            visit(composeTestRule.activity.window.decorView)
        }
        return found
    }

    @Test
    fun activeGpsCanHaveFreshStationarySignalWithoutInventingDistance() {
        composeTestRule.setContent {
            ActiveRunScreen(false, fixtureMetrics().copy(
                distance = "—", sourceLabel = "GPS distance unavailable",
                gpsDistanceAvailable = false), false, true, {}, {}, {},
                isGpsRun = true, gpsServicePresent = true, gpsSignalStatus = GpsSignalStatus.RECORDING)
        }
        composeTestRule.onNodeWithText("ILLUSTRATIVE MAP · GPS RECORDING").assertExists()
        composeTestRule.onNodeWithTag(ACTIVE_RUN_DISTANCE_TAG).assertTextEquals("—")
    }

    @Test
    fun runScreenForwardsSignalAndKeepsPausedStoppedAndSampleStatesDistinct() {
        var paused by mutableStateOf(false)
        var servicePresent by mutableStateOf(true)
        var gps by mutableStateOf(true)
        var signal by mutableStateOf(GpsSignalStatus.STALE)
        composeTestRule.setContent {
            RunStateScreen(
                model = RunUiModel(if (paused) RunUiState.ActivePaused else RunUiState.ActiveRunning),
                metrics = fixtureMetrics(), countdownDigit = null,
                onStart = {}, onCancelCountdown = {}, onRetryInitialization = {}, onRetryStart = {},
                onPause = {}, onResume = {}, onStop = {}, onStartAnother = {},
                isGpsRun = gps, gpsServicePresent = servicePresent, gpsSignalStatus = signal
            )
        }
        composeTestRule.onNodeWithText("ILLUSTRATIVE MAP · GPS SIGNAL UNAVAILABLE").assertExists()
        composeTestRule.runOnIdle { paused = true }
        composeTestRule.onNodeWithText("ILLUSTRATIVE MAP · GPS PAUSED").assertExists()
        composeTestRule.runOnIdle { paused = false; signal = GpsSignalStatus.ACQUIRING }
        composeTestRule.onNodeWithText("ILLUSTRATIVE MAP · GPS WAITING").assertExists()
        composeTestRule.runOnIdle { servicePresent = false }
        composeTestRule.onNodeWithText("ILLUSTRATIVE MAP · GPS STOPPED").assertExists()
        composeTestRule.runOnIdle { gps = false; signal = GpsSignalStatus.RECORDING }
        composeTestRule.onNodeWithText("ILLUSTRATIVE MAP · LOCATION OFF").assertExists()
        composeTestRule.onNodeWithText("ILLUSTRATIVE MAP · GPS RECORDING").assertDoesNotExist()
    }

    @Test
    fun historyLoadsGpsPathOnlyWhenExpandedAndKeepsItDuringFeedbackChanges() {
        val runId = "0f6a2c1e-9d43-4b7a-9c21-7b5e8a4d1f30"
        val loads = mutableListOf<String>()
        var energy by mutableStateOf<ReflectionBranch?>(null)
        composeTestRule.setContent {
            RunHistoryScreen(
                items = listOf(RunHistoryItem(runId, "Oct 13, 2026", fixtureMetrics(),
                    "READY", "Exact saved reflection.", null, false,
                    isGpsRun = true, energy = energy)),
                onBack = {}, onRetryReflection = {},
                speakerFactory = { object : ReflectionSpeaker {
                    override fun speak(text: String) = Unit
                    override fun stop() = Unit
                    override fun close() = Unit
                } },
                routeLoader = { id ->
                    loads += id
                    GpsRoutePreviewData(listOf(listOf(GpsRouteCoordinate(0.5, 0.5))), 1, 0, 0.0, 0.0)
                }
            )
        }
        composeTestRule.runOnIdle { assertTrue(loads.isEmpty()) }
        composeTestRule.onNodeWithTag(RUN_HISTORY_ROW_TAG_PREFIX + runId).performClick()
        composeTestRule.onNodeWithTag(GPS_ROUTE_PREVIEW_TAG).assertExists()
        composeTestRule.onNodeWithText("Recorded GPS path · partial").assertExists()
        composeTestRule.runOnIdle { energy = ReflectionBranch.POWERED_UP }
        composeTestRule.runOnIdle { assertEquals(listOf(runId), loads) }
    }

    @Test
    fun historyGpsPathLabelAndAccessibilityFollowFrozenCoverage() {
        val runId = "0f6a2c1e-9d43-4b7a-9c21-7b5e8a4d1f30"
        var coverage by mutableStateOf<TelemetryCoverage?>(TelemetryCoverage.COMPLETE)
        composeTestRule.setContent {
            RunHistoryScreen(
                items = listOf(RunHistoryItem(runId, "Oct 16, 2026", fixtureMetrics(),
                    "READY", "Exact saved reflection.", null, false,
                    isGpsRun = true, telemetryCoverage = coverage)),
                onBack = {}, onRetryReflection = {},
                speakerFactory = { object : ReflectionSpeaker {
                    override fun speak(text: String) = Unit
                    override fun stop() = Unit
                    override fun close() = Unit
                } },
                routeLoader = {
                    GpsRoutePreviewData(listOf(listOf(
                        GpsRouteCoordinate(0.1, 0.1), GpsRouteCoordinate(0.9, 0.9)
                    )), 2, 1, 0.8, 0.8)
                }
            )
        }
        composeTestRule.onNodeWithTag(RUN_HISTORY_ROW_TAG_PREFIX + runId).performClick()
        composeTestRule.onNodeWithText("Recorded GPS path").assertExists()
        composeTestRule.onNodeWithText("Recorded GPS path · partial").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription(
            "Recorded GPS path in 1 separate recorded sections. No line joins unrecorded intervals."
        ).assertExists()

        composeTestRule.runOnIdle { coverage = TelemetryCoverage.PARTIAL }
        composeTestRule.onNodeWithText("Recorded GPS path · partial").assertExists()
        composeTestRule.onNodeWithText("Recorded GPS path").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription(
            "Partial recorded GPS path in 1 separate recorded sections. No line joins unrecorded intervals."
        ).assertExists()

        composeTestRule.runOnIdle { coverage = null }
        composeTestRule.onNodeWithText("Recorded GPS path · partial").assertExists()
        composeTestRule.onNodeWithText("Recorded GPS path").assertDoesNotExist()
    }

    @Test
    fun historySpeechStopsOnBackgroundAndDoesNotReplayOnResume() {
        val runId = "0f6a2c1e-9d43-4b7a-9c21-7b5e8a4d1f30"
        val owner = TestLifecycle()
        moveTo(owner, Lifecycle.State.RESUMED)
        var speeches = 0
        var stops = 0
        var instrumentals = 0
        val speaker = object : ReflectionSpeaker {
            override fun speak(text: String) { speeches++ }
            override fun speakWithInstrumental(
                text: String, canPlayInstrumental: () -> Boolean, onFinished: () -> Unit
            ) { instrumentals++; onFinished() }
            override fun stop() { stops++ }
            override fun close() = Unit
        }
        composeTestRule.setContent {
            RunHistoryScreen(listOf(RunHistoryItem(runId, "Oct 13, 2026", fixtureMetrics(),
                "READY", "Exact reflection.", null, false)), {}, {},
                speakerFactory = { speaker }, speechLifecycle = owner.registry)
        }
        composeTestRule.onNodeWithTag(RUN_HISTORY_ROW_TAG_PREFIX + runId).performClick()
        val beforeBackground = stops
        moveTo(owner, Lifecycle.State.CREATED)
        composeTestRule.runOnIdle { assertTrue(stops > beforeBackground) }
        moveTo(owner, Lifecycle.State.RESUMED)
        composeTestRule.runOnIdle {
            assertEquals(1, speeches)
            assertEquals(0, instrumentals)
        }
    }

    @Test
    fun historyAudioFailureKeepsExactSavedTextAndNavigationUsable() {
        val runId = "0f6a2c1e-9d43-4b7a-9c21-7b5e8a4d1f30"
        val exact = "  Exact saved reflection.  "
        var creations = 0
        var closes = 0
        var backs = 0
        composeTestRule.setContent {
            RunHistoryScreen(listOf(RunHistoryItem(runId, "Oct 13, 2026", fixtureMetrics(),
                "READY", exact, null, false)), { backs++ }, {},
                speakerFactory = {
                    creations++
                    if (creations == 1) error("No voice engine available")
                    object : ReflectionSpeaker {
                        override fun speak(text: String) { error("Voice service disconnected") }
                        override fun stop() { error("Voice stop unavailable") }
                        override fun close() { closes++; error("Voice close unavailable") }
                    }
                })
        }

        composeTestRule.onNodeWithTag(RUN_HISTORY_ROW_TAG_PREFIX + runId).performClick()
        composeTestRule.onNodeWithTag(RUN_HISTORY_REFLECTION_TAG_PREFIX + runId).assertTextEquals(exact)
        composeTestRule.onNodeWithTag(RUN_HISTORY_ROW_TAG_PREFIX + runId).performClick()
        composeTestRule.onNodeWithTag(RUN_HISTORY_ROW_TAG_PREFIX + runId).performClick()
        composeTestRule.onNodeWithTag(RUN_HISTORY_REFLECTION_TAG_PREFIX + runId).assertTextEquals(exact)
        composeTestRule.onNodeWithText("Back to run").performClick()
        assertEquals(2, creations)
        assertEquals(1, closes)
        assertEquals(1, backs)
    }

    @Test
    fun historyOpeningWhileNotResumedDoesNotQueueSpeechForItsReturn() {
        val runId = "0f6a2c1e-9d43-4b7a-9c21-7b5e8a4d1f30"
        val exact = "Exact saved reflection."
        val owner = TestLifecycle()
        moveTo(owner, Lifecycle.State.CREATED)
        val spoken = mutableListOf<String>()
        var creations = 0
        composeTestRule.setContent {
            RunHistoryScreen(listOf(RunHistoryItem(runId, "Oct 13, 2026", fixtureMetrics(),
                "READY", exact, null, false)), {}, {},
                speakerFactory = {
                    creations++
                    object : ReflectionSpeaker {
                        override fun speak(text: String) { spoken += text }
                        override fun stop() = Unit
                        override fun close() = Unit
                    }
                }, speechLifecycle = owner.registry)
        }

        composeTestRule.onNodeWithTag(RUN_HISTORY_ROW_TAG_PREFIX + runId).performClick()
        composeTestRule.onNodeWithTag(RUN_HISTORY_REFLECTION_TAG_PREFIX + runId).assertTextEquals(exact)
        assertEquals(0, creations)
        moveTo(owner, Lifecycle.State.RESUMED)
        composeTestRule.runOnIdle { assertTrue(spoken.isEmpty()) }
        composeTestRule.onNodeWithTag(RUN_HISTORY_ROW_TAG_PREFIX + runId).performClick()
        composeTestRule.onNodeWithTag(RUN_HISTORY_ROW_TAG_PREFIX + runId).performClick()
        assertEquals(listOf(exact), spoken)
        assertEquals(1, creations)
    }

    @Test
    fun historyShowsMusicAsPartialEvidenceRatherThanAFullSoundtrack() {
        val runId = "0f6a2c1e-9d43-4b7a-9c21-7b5e8a4d1f30"
        composeTestRule.setContent {
            RunHistoryScreen(
                items = listOf(
                    RunHistoryItem(
                        runId = runId,
                        dateLabel = "Oct 12, 2026",
                        metrics = fixtureMetrics(),
                        reflectionStatus = null,
                        reflectionText = null,
                        energyLabel = null,
                        isTestData = false,
                        musicObservationRequested = true,
                        musicObservationCount = 2,
                        lastObservedMedia = "Later title — Artist · com.example.player",
                        reflectionMusicSummary = "Music observations kept on this phone"
                    )
                ),
                onBack = {},
                onRetryReflection = {}
            )
        }

        composeTestRule.onNodeWithText("OBSERVED MEDIA · PARTIAL").assertDoesNotExist()
        composeTestRule.onNodeWithText("Last observed · Later title — Artist · com.example.player").assertDoesNotExist()
        composeTestRule.onNodeWithTag(RUN_HISTORY_ROW_TAG_PREFIX + runId).performClick()
        composeTestRule.onNodeWithText("OBSERVED MEDIA · PARTIAL").assertExists()
        composeTestRule.onNodeWithText(
            "Last observed · Later title — Artist · com.example.player"
        ).assertExists()
        composeTestRule.onNodeWithText(
            "2 listening updates saved. This may not be the full soundtrack."
        ).assertExists()
        composeTestRule.onNodeWithText("Music observations kept on this phone").assertExists()
    }

    @Test
    fun historyWithoutMusicOptInExplainsMissingDetailsOnlyWhenExpanded() {
        val id = "music-not-enabled"
        composeTestRule.setContent {
            RunHistoryScreen(listOf(RunHistoryItem(id, "Oct 15, 2026", fixtureMetrics(),
                null, null, null, false)), {}, {})
        }
        composeTestRule.onNodeWithText("Music wasn’t recorded for this run.").assertDoesNotExist()
        composeTestRule.onNodeWithTag(RUN_HISTORY_ROW_TAG_PREFIX + id).performClick()
        composeTestRule.onNodeWithText("Music wasn’t recorded for this run.").assertExists()
        composeTestRule.onNodeWithText("Last observed", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("OBSERVED MEDIA · PARTIAL").assertDoesNotExist()
    }

    @Test
    fun historyMusicWithoutObservationsRemainsUnknownInsideTheExpandedRecord() {
        val id = "no-observations"
        composeTestRule.setContent {
            RunHistoryScreen(listOf(RunHistoryItem(id, "Oct 15, 2026", fixtureMetrics(),
                null, null, null, false, musicObservationRequested = true)), {}, {})
        }
        composeTestRule.onNodeWithText("OBSERVED MEDIA · PARTIAL").assertDoesNotExist()
        composeTestRule.onNodeWithTag(RUN_HISTORY_ROW_TAG_PREFIX + id).performClick()
        composeTestRule.onNodeWithText("No media session was observed. Music remains unknown.").assertExists()
        composeTestRule.onNodeWithText("Last observed", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("listening updates saved", substring = true).assertDoesNotExist()
    }

    @Test
    fun failedReflectionOffersRetryWithoutReplacingStartAnother() {
        val actions = renderScreen(
            model = RunUiModel(RunUiState.Saved),
            metrics = fixtureMetrics(),
            reflection = RunCompleteReflectionDisplay.Failed
        )

        composeTestRule.onNodeWithTag(RUN_COMPLETE_REFLECTION_RETRY_TAG)
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        assertEquals(1, actions.reflectionRetries)
        assertEquals(0, actions.startAnothers)
        composeTestRule.onNodeWithText(START_ANOTHER).assertIsDisplayed()
    }

    @Test
    fun savedWithoutDebugBridgeShowsMetricsAndRestartButNoFakePanel() {
        renderScreen(
            model = RunUiModel(RunUiState.Saved),
            metrics = fixtureMetrics(),
            reflection = null
        )

        composeTestRule.onNodeWithTag(RUN_COMPLETE_DISTANCE_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText(START_ANOTHER).assertIsDisplayed()
        composeTestRule.onNodeWithTag(RUN_COMPLETE_REFLECTION_TAG).assertDoesNotExist()
        composeTestRule.onNodeWithTag(RUN_COMPLETE_REFLECTION_RETRY_TAG).assertDoesNotExist()
        composeTestRule.onNodeWithText("FAKE BACKEND · TEST DATA").assertDoesNotExist()
    }

    /** Large type and a short viewport keep every fact scrollable and the main action usable. */
    @Test
    fun savedRemainsUsableWithLargeTextOnShortViewport() {
        val actions = renderScreen(
            model = RunUiModel(RunUiState.Saved),
            metrics = fixtureMetrics(),
            modifier = Modifier.size(width = 360.dp, height = 480.dp),
            fontScale = 2f
        )

        composeTestRule.onNodeWithText("ACTIVE").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(START_ANOTHER).assertIsDisplayed().performClick()

        assertEquals(1, actions.startAnothers)
    }

    /** A completed run with no readable metrics stays visibly unknown. */
    @Test
    fun savedWithUnavailableMetricsInventsNoResults() {
        renderScreen(RunUiState.Saved)

        composeTestRule.onNodeWithText("METRICS UNAVAILABLE").assertIsDisplayed()
        assertEquals(4, composeTestRule.onAllNodesWithText("—").fetchSemanticsNodes().size)
        composeTestRule.onNodeWithText("0.00 mi").assertDoesNotExist()
        composeTestRule.onNodeWithText("00:00:00").assertDoesNotExist()
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
        val cues = mutableListOf<Int?>()
        var finishes = 0
    }

    /** Hosts the countdown over [owner]'s lifecycle and shows its digit under a tag. */
    private fun renderCountdown(owner: TestLifecycle, startNow: () -> Boolean = { false },
        active: () -> Boolean = { true }): CountdownRecord {
        val record = CountdownRecord()

        composeTestRule.setContent {
            val digit = rememberLifecycleCountdown(
                active = active(),
                lifecycle = owner.lifecycle,
                onFinished = { record.finishes++ },
                startNowRequested = startNow(),
                onCountdownCue = { record.cues += it }
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

    /** Every preparation second remains visible; only the opening and final three speak. */
    @Test
    fun countdownShowsTenSecondsAndSpeaksOpeningAndFinalThreeThenFinishesOnce() {
        // Advance the same virtual clock as the effect's delays, keeping every visible
        // digit observable independently of physical-device semantics polling speed.
        composeTestRule.mainClock.autoAdvance = false
        val owner = TestLifecycle()
        moveTo(owner, Lifecycle.State.RESUMED)
        val record = renderCountdown(owner)

        composeTestRule.mainClock.advanceTimeByFrame()
        for (expected in COUNTDOWN_SECONDS downTo 1) {
            composeTestRule.onNodeWithTag(DIGIT_TAG).assertTextEquals(expected.toString())
            assertEquals(0, record.finishes)
            composeTestRule.mainClock.advanceTimeBy(COUNTDOWN_STEP_MILLIS)
            composeTestRule.mainClock.advanceTimeByFrame()
        }
        assertEquals((COUNTDOWN_SECONDS downTo 1).toList(), record.digitsSeen)
        assertEquals(listOf(10, 3, 2, 1, null), record.cues)
        assertEquals(1, record.finishes)

        // Assert: no further finish after more time passes.
        composeTestRule.mainClock.advanceTimeBy(LONGER_THAN_COUNTDOWN_MILLIS)
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.waitForIdle()
        assertEquals((COUNTDOWN_SECONDS downTo 1).toList(), record.digitsSeen)
        assertEquals(1, record.finishes)
    }

    /** Proves nothing counts down, and nothing finishes, while the lifecycle is below STARTED. */
    @Test
    fun countdownDoesNotProgressOrFinishBelowStarted() {
        // The countdown delays use Compose's clock, so advance that same clock rather
        // than combining wall-time sleeps with a short virtual-time polling window.
        composeTestRule.mainClock.autoAdvance = false
        val owner = TestLifecycle()
        moveTo(owner, Lifecycle.State.CREATED)
        val record = renderCountdown(owner)
        composeTestRule.mainClock.advanceTimeByFrame()

        // Act: longer than a whole countdown, in the background.
        composeTestRule.mainClock.advanceTimeBy(LONGER_THAN_COUNTDOWN_MILLIS)
        composeTestRule.waitForIdle()

        // Assert
        composeTestRule.onNodeWithTag(DIGIT_TAG).assertTextEquals(NO_DIGIT)
        assertTrue(record.digitsSeen.isEmpty())
        assertEquals(0, record.finishes)

        // Act: mid-countdown, the app goes to the background again.
        moveTo(owner, Lifecycle.State.STARTED)
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.onNodeWithTag(DIGIT_TAG).assertTextEquals("10")
        composeTestRule.mainClock.advanceTimeBy(COUNTDOWN_STEP_MILLIS)
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.onNodeWithTag(DIGIT_TAG).assertTextEquals("9")
        moveTo(owner, Lifecycle.State.CREATED)
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.mainClock.advanceTimeBy(LONGER_THAN_COUNTDOWN_MILLIS)
        composeTestRule.waitForIdle()

        // Assert: cleared, and never reached 1 or finished.
        composeTestRule.onNodeWithTag(DIGIT_TAG).assertTextEquals(NO_DIGIT)
        assertEquals(listOf(10, 9), record.digitsSeen)
        assertEquals(listOf(10, null), record.cues)
        assertEquals(0, record.finishes)
    }

    /** An unreserved visual helper restarts, never silently finishes while backgrounded. */
    @Test
    fun returningToStartedRestartsTheCountdownAtTen() {
        composeTestRule.mainClock.autoAdvance = false
        val owner = TestLifecycle()
        moveTo(owner, Lifecycle.State.RESUMED)
        val record = renderCountdown(owner)

        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.mainClock.advanceTimeBy(COUNTDOWN_STEP_MILLIS)
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.onNodeWithTag(DIGIT_TAG).assertTextEquals("9")
        moveTo(owner, Lifecycle.State.CREATED)
        moveTo(owner, Lifecycle.State.RESUMED)

        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.onNodeWithTag(DIGIT_TAG).assertTextEquals("10")
        composeTestRule.mainClock.advanceTimeBy(LONGER_THAN_COUNTDOWN_MILLIS)
        composeTestRule.mainClock.advanceTimeByFrame()
        assertEquals(listOf(10, 9) + (COUNTDOWN_SECONDS downTo 1).toList(), record.digitsSeen)
        assertEquals(1, record.finishes)
    }

    @Test
    fun startNowDuringFinalSecondFinishesOnlyOnceAndStopsSpokenCountdown() {
        composeTestRule.mainClock.autoAdvance = false
        val owner = TestLifecycle()
        moveTo(owner, Lifecycle.State.RESUMED)
        var startNow by mutableStateOf(false)
        val record = renderCountdown(owner, startNow = { startNow })
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.mainClock.advanceTimeBy((COUNTDOWN_SECONDS - 1) * COUNTDOWN_STEP_MILLIS)
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.onNodeWithTag(DIGIT_TAG).assertTextEquals("1")
        composeTestRule.runOnIdle { startNow = true }
        composeTestRule.mainClock.advanceTimeByFrame()
        assertEquals(1, record.finishes)
        assertEquals(null, record.cues.last())
        // The first frame applies the input and runs its effect; rendering that effect's
        // cleared digit takes the next frame while automatic clock advancement is disabled.
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.onNodeWithTag(DIGIT_TAG).assertTextEquals(NO_DIGIT)
        composeTestRule.mainClock.advanceTimeBy(LONGER_THAN_COUNTDOWN_MILLIS)
        moveTo(owner, Lifecycle.State.CREATED)
        moveTo(owner, Lifecycle.State.RESUMED)
        composeTestRule.mainClock.advanceTimeBy(LONGER_THAN_COUNTDOWN_MILLIS)
        assertEquals(1, record.finishes)
        assertEquals(listOf(10, 3, 2, 1, null), record.cues)
    }

    @Test
    fun cancellingCountdownWinsAgainstPendingStartNowAndAllowsFreshPreparation() {
        composeTestRule.mainClock.autoAdvance = false
        val owner = TestLifecycle()
        moveTo(owner, Lifecycle.State.RESUMED)
        var active by mutableStateOf(true)
        var startNow by mutableStateOf(false)
        val record = renderCountdown(owner, startNow = { startNow }, active = { active })
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.runOnIdle { active = false; startNow = true }
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.mainClock.advanceTimeBy(LONGER_THAN_COUNTDOWN_MILLIS)
        assertEquals(0, record.finishes)
        assertEquals(null, record.cues.last())
        composeTestRule.onNodeWithTag(DIGIT_TAG).assertTextEquals(NO_DIGIT)
        composeTestRule.runOnIdle { startNow = false; active = true }
        composeTestRule.mainClock.advanceTimeByFrame()
        // Applying the new active input launches an effect; show its first digit next frame.
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.onNodeWithTag(DIGIT_TAG).assertTextEquals("10")
        composeTestRule.mainClock.advanceTimeBy(LONGER_THAN_COUNTDOWN_MILLIS)
        assertEquals(1, record.finishes)
    }

    @Test
    fun startNowWhileBackgroundedCannotFinishRun() {
        composeTestRule.mainClock.autoAdvance = false
        val owner = TestLifecycle()
        moveTo(owner, Lifecycle.State.RESUMED)
        var startNow by mutableStateOf(false)
        val record = renderCountdown(owner, startNow = { startNow })
        composeTestRule.mainClock.advanceTimeByFrame()
        moveTo(owner, Lifecycle.State.CREATED)
        composeTestRule.runOnIdle { startNow = true }
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.mainClock.advanceTimeBy(LONGER_THAN_COUNTDOWN_MILLIS)
        assertEquals(0, record.finishes)
        assertEquals(listOf(10, null), record.cues)
        composeTestRule.onNodeWithTag(DIGIT_TAG).assertTextEquals(NO_DIGIT)
    }

    /** The visual timer never restores a half-finished or already-authorized start. */
    @Test
    fun countdownCompositionRestorationRestartsAtTenWithoutFinishing() {
        composeTestRule.mainClock.autoAdvance = false
        val owner = TestLifecycle()
        moveTo(owner, Lifecycle.State.RESUMED)
        var active by mutableStateOf(true)
        var entries = 0
        var disposals = 0
        var finishes = 0
        val restoration = StateRestorationTester(composeTestRule)
        restoration.setContent {
            DisposableEffect(Unit) {
                entries++
                onDispose { disposals++ }
            }
            val digit = rememberLifecycleCountdown(active, owner.lifecycle, { finishes++ })
            Text(digit?.toString() ?: NO_DIGIT, Modifier.testTag(DIGIT_TAG))
        }
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.mainClock.advanceTimeBy(COUNTDOWN_STEP_MILLIS)
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.onNodeWithTag(DIGIT_TAG).assertTextEquals("9")

        // Restoration must actually dispose/recreate the composition. A paused clock could
        // otherwise combine its removal/re-entry updates into the same frame.
        composeTestRule.mainClock.autoAdvance = true
        restoration.emulateSavedInstanceStateRestore()
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.mainClock.advanceTimeByFrame()
        assertEquals(2, entries)
        assertEquals(1, disposals)
        composeTestRule.onNodeWithTag(DIGIT_TAG).assertTextEquals("10")
        assertEquals(0, finishes)
        composeTestRule.runOnIdle { active = false }
        composeTestRule.mainClock.advanceTimeBy(LONGER_THAN_COUNTDOWN_MILLIS)
        assertEquals(0, finishes)
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
