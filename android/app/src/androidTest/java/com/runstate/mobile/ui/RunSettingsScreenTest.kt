package com.runstate.mobile.ui

import android.content.Context
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.printToString
import androidx.compose.ui.unit.Density
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.runstate.mobile.ReflectionConnectionState
import com.runstate.mobile.RunJourneyRoot
import com.runstate.mobile.data.local.MetricSource
import com.runstate.mobile.data.local.ReflectionBranch
import com.runstate.mobile.data.local.DistanceUnit
import com.runstate.mobile.data.local.RunEntity
import com.runstate.mobile.data.local.RunStateDatabase
import com.runstate.mobile.data.local.RunTrackingMode
import com.runstate.mobile.data.local.StoredRunState
import com.runstate.mobile.data.local.TelemetryCoverage
import com.runstate.mobile.location.RunTrackingServiceState
import com.runstate.mobile.music.SpotifyLivePlayerState
import com.runstate.mobile.run.PreparedRunFactory
import com.runstate.mobile.run.RunActionKind
import com.runstate.mobile.run.RunAdmission
import com.runstate.mobile.run.RunSessionCoordinator
import com.runstate.mobile.settings.AndroidRunPreferencesStore
import com.runstate.mobile.settings.RunAppearance
import com.runstate.mobile.settings.RunPreferences
import com.runstate.mobile.settings.StartBackground
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Isolated screens and roots: no production database, location service, network or audible engine. */
@RunWith(AndroidJUnit4::class)
class RunSettingsScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val fixtures = mutableListOf<Fixture>()

    @Test
    fun firstNameChoicePersonalizesGreetingAndKeepsMusicConsentSeparate() {
        val fixture = fixture()
        val restoration = renderRoot(fixture)
        waitForTag(WELCOME_NAME_DIALOG_TAG)
        compose.onNodeWithTag(SETTINGS_DIALOG_TAG).assertDoesNotExist()
        compose.onNodeWithTag(WELCOME_NAME_CONTINUE_TAG).assertIsNotEnabled()
        compose.onNodeWithTag(WELCOME_NAME_INPUT_TAG).performTextReplacement("  Maya  Lee  ")
        restoration.emulateSavedInstanceStateRestore()
        waitForTag(WELCOME_NAME_DIALOG_TAG)
        assertEquals("  Maya  Lee  ", compose.onNodeWithTag(WELCOME_NAME_INPUT_TAG)
            .fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        compose.onNodeWithTag(WELCOME_NAME_CONTINUE_TAG).performClick()
        waitForTag(SETTINGS_DIALOG_TAG)
        assertEquals(RunPreferences(runnerName = "Maya Lee"), fixture.loadPreferences())
        compose.onNodeWithTag(WELCOME_NAME_DIALOG_TAG).assertDoesNotExist()
        compose.onNodeWithTag(SETTINGS_MUSIC_OBSERVATION_TAG).performScrollTo().assertIsOff()
        compose.onNodeWithTag(SETTINGS_DONE_TAG).performClick()
        compose.waitUntil(5_000L) { fixture.preferenceWrites == 2 }
        restoration.emulateSavedInstanceStateRestore()
        waitForTag(START_RUN_SCREEN_TAG)
        compose.onNodeWithTag(WELCOME_NAME_DIALOG_TAG).assertDoesNotExist()
        val greeting = compose.onNodeWithTag(START_GREETING_TAG).fetchSemanticsNode()
            .config[SemanticsProperties.Text].joinToString { it.text }
        assertTrue(greeting.endsWith(", Maya Lee"))
        assertEquals(RunPreferences(musicSetupComplete = true, runnerName = "Maya Lee"), fixture.loadPreferences())
        assertEquals(0, fixture.preparedRuns)
    }

    @Test
    fun failedFirstNameSaveKeepsDraftAndRetriesWithoutClaimingSetupComplete() {
        val fixture = fixture()
        fixture.failPreferenceWrites = true
        renderRoot(fixture)
        waitForTag(WELCOME_NAME_DIALOG_TAG)
        compose.onNodeWithTag(WELCOME_NAME_INPUT_TAG).performTextReplacement("Maya")
        compose.onNodeWithTag(WELCOME_NAME_CONTINUE_TAG).performClick()
        compose.waitUntil(5_000L) {
            compose.onAllNodesWithText("Preferences couldn't save. Try again before starting a run.")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag(WELCOME_NAME_DIALOG_TAG).assertIsDisplayed()
        assertEquals(RunPreferences(), fixture.loadPreferences())
        assertEquals(0, fixture.preferenceWrites)
        compose.runOnIdle { fixture.failPreferenceWrites = false }
        compose.onNodeWithTag(WELCOME_NAME_CONTINUE_TAG).performClick()
        waitForTag(SETTINGS_DIALOG_TAG)
        assertEquals(RunPreferences(runnerName = "Maya"), fixture.loadPreferences())
    }

    @Test
    fun existingCompletedSetupWithoutNameIsNotPrompted() {
        val fixture = fixture(RunPreferences(musicSetupComplete = true))
        renderRoot(fixture)
        waitForTag(START_RUN_SCREEN_TAG)
        compose.onNodeWithTag(WELCOME_NAME_DIALOG_TAG).assertDoesNotExist()
        compose.onNodeWithTag(START_RUN_SETTINGS_TAG).performClick()
        waitForTag(SETTINGS_DIALOG_TAG)
        compose.onNodeWithTag(WELCOME_NAME_DIALOG_TAG).assertDoesNotExist()
        compose.onNodeWithTag(SETTINGS_RUNNER_NAME_TAG).performScrollTo().assertIsDisplayed()
        assertEquals(0, fixture.preferenceWrites)
    }

    @Test
    fun recoveredRunTakesPrecedenceOverUnsetFirstNameAndSetup() {
        val fixture = fixture()
        val paused = runBlocking {
            fixture.coordinator.initialize()
            fixture.coordinator.beginCountdown()
            fixture.coordinator.requestStart().await()
            fixture.time += 5_000L
            fixture.coordinator.requestAction(RunActionKind.PAUSE).await()
            fixture.database.runDao().findActiveRuns().single()
        }
        compose.mainClock.autoAdvance = false
        renderRoot(fixture)
        waitForDemoTag(ACTIVE_RUN_SCREEN_TAG)
        compose.onNodeWithContentDescription("Resume run").assertIsDisplayed()
        compose.onNodeWithTag(WELCOME_NAME_DIALOG_TAG).assertDoesNotExist()
        compose.onNodeWithTag(SETTINGS_DIALOG_TAG).assertDoesNotExist()
        assertEquals(RunPreferences(), fixture.loadPreferences())
        assertEquals(0, fixture.preferenceWrites)
        assertEquals(paused, runBlocking { fixture.database.runDao().findById(paused.runId) })
    }

    @Test
    fun runnerNameSaveCancelAndClearUpdateGreetingWithoutChangingRunPreferences() {
        val original = RunPreferences(musicSetupComplete = true, captureWeather = true,
            appearance = RunAppearance.DAY, runnerName = "Avery")
        val fixture = fixture(original)
        val restoration = renderRoot(fixture)
        waitForTag(START_RUN_SCREEN_TAG)
        fun greeting(): String = compose.onNodeWithTag(START_GREETING_TAG)
            .fetchSemanticsNode().config[SemanticsProperties.Text].joinToString { it.text }
        assertTrue(greeting().endsWith(", Avery"))
        compose.onNodeWithTag(START_RUN_SETTINGS_TAG).performClick()
        waitForTag(SETTINGS_DIALOG_TAG)
        compose.onNodeWithTag(SETTINGS_RUNNER_NAME_TAG).performScrollTo().performClick()
        compose.onNodeWithTag(SETTINGS_RUNNER_NAME_INPUT_TAG).performTextReplacement("Discard this")
        compose.onNodeWithTag(SETTINGS_RUNNER_NAME_CANCEL_TAG).performClick()
        compose.runOnIdle { assertEquals(0, fixture.preferenceWrites) }
        assertEquals(original, fixture.loadPreferences())

        compose.onNodeWithTag(SETTINGS_RUNNER_NAME_TAG).performScrollTo().performClick()
        compose.onNodeWithTag(SETTINGS_RUNNER_NAME_INPUT_TAG).performTextReplacement("  Morgan  Lee  ")
        compose.onNodeWithTag(SETTINGS_RUNNER_NAME_SAVE_TAG).performClick()
        compose.waitUntil(5_000L) { fixture.preferenceWrites == 1 }
        assertEquals(original.copy(runnerName = "Morgan Lee"), fixture.loadPreferences())
        compose.onNodeWithTag(SETTINGS_DONE_TAG).performClick()
        waitForTag(START_RUN_SCREEN_TAG)
        assertTrue(greeting().endsWith(", Morgan Lee"))
        restoration.emulateSavedInstanceStateRestore()
        waitForTag(START_RUN_SCREEN_TAG)
        assertTrue(greeting().endsWith(", Morgan Lee"))

        compose.onNodeWithTag(START_RUN_SETTINGS_TAG).performClick()
        waitForTag(SETTINGS_DIALOG_TAG)
        compose.onNodeWithTag(SETTINGS_RUNNER_NAME_TAG).performScrollTo().performClick()
        compose.onNodeWithTag(SETTINGS_RUNNER_NAME_INPUT_TAG).performTextReplacement("  ")
        compose.onNodeWithTag(SETTINGS_RUNNER_NAME_SAVE_TAG).performClick()
        compose.waitUntil(5_000L) { fixture.preferenceWrites == 2 }
        assertEquals(original.copy(runnerName = ""), fixture.loadPreferences())
        compose.onNodeWithTag(SETTINGS_DONE_TAG).performClick()
        waitForTag(START_RUN_SCREEN_TAG)
        assertFalse(greeting().contains(","))
        assertEquals(0, fixture.preparedRuns)
    }

    @After
    fun closeIsolatedFixtures() {
        if (fixtures.isNotEmpty()) {
            // RunAssembly draws a continuous frame loop. Remove it before asking an
            // automatically advancing Compose clock to become idle.
            compose.mainClock.autoAdvance = false
            compose.runOnUiThread { fixtures.forEach { it.shown = false } }
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        compose.mainClock.autoAdvance = true
        fixtures.forEach { fixture ->
            fixture.scope.cancel()
            fixture.database.close()
            check(fixture.preferencesName.startsWith("run-settings-ui-"))
            fixture.context.deleteSharedPreferences(fixture.preferencesName)
        }
    }

    @Test
    fun plainStartHidesBackgroundChoicesAndPreservesSavedArtworkWhileOtherSettingsWork() {
        var background by mutableStateOf(StartBackground.HOUSTON_TRAILS)
        var appearance by mutableStateOf(RunAppearance.NIGHT)
        var observing by mutableStateOf(false)
        var saving by mutableStateOf(false)
        var available by mutableStateOf(true)
        val backgroundChanges = mutableListOf<StartBackground>()
        var appearanceChanges = 0
        var musicChanges = 0
        var dismissals = 0
        var otherActions = 0
        compose.setContent {
            RunSettingsDialog(
                gpsReady = true, gpsMessage = null,
                musicObservationEnabled = observing, musicAccessGranted = true,
                musicReflectionSharingEnabled = false, musicReflectionSharingAvailable = true,
                reflectionConnectionLabel = null, demoMode = false,
                onDismiss = { dismissals++ }, onRequestLocationSetup = { otherActions++ },
                onSetMusicObservation = { observing = it; musicChanges++ },
                onRequestMusicAccess = { otherActions++ },
                onSetMusicReflectionSharing = { otherActions++ }, onSetDemoMode = { otherActions++ },
                onOpenReflectionSetup = { otherActions++ },
                appearance = appearance, onSetAppearance = { appearance = it; appearanceChanges++ },
                onSetWeather = { otherActions++ }, onSetCloudReflections = { otherActions++ },
                settingsLoading = saving, preferencesAvailable = available,
                startBackground = background,
                onSetStartBackground = { background = it; backgroundChanges += it }
            )
        }
        fun assertBackgroundChooserAbsent() {
            compose.onNodeWithText("Start background").assertDoesNotExist()
            compose.onNodeWithText("Hermann Park").assertDoesNotExist()
            compose.onNodeWithText("Houston trails").assertDoesNotExist()
            compose.onNodeWithTag(SETTINGS_START_BACKGROUND_TAG).assertDoesNotExist()
            StartBackground.entries.forEach {
                compose.onNodeWithTag(startBackgroundOptionTag(it)).assertDoesNotExist()
            }
        }
        for ((savedBackground, chosenAppearance) in listOf(
            StartBackground.HOUSTON_TRAILS to RunAppearance.DAY,
            StartBackground.HERMANN_PARK to RunAppearance.NIGHT
        )) {
            // Changing unrelated preferences must not reset either previously saved artwork choice.
            compose.runOnIdle { background = savedBackground }
            assertBackgroundChooserAbsent()
            compose.onNodeWithTag(appearanceOptionTag(chosenAppearance))
                .performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
            compose.onNodeWithTag(appearanceOptionTag(chosenAppearance)).assertIsSelected()
            compose.onNodeWithTag(SETTINGS_MUSIC_OBSERVATION_TAG)
                .performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
            compose.runOnIdle {
                assertEquals(savedBackground, background)
                assertTrue(backgroundChanges.isEmpty())
            }
        }
        for (loading in listOf(true, false)) {
            compose.runOnIdle { saving = loading; available = loading }
            assertBackgroundChooserAbsent()
            compose.onNodeWithTag(appearanceOptionTag(RunAppearance.DAY))
                .performScrollTo().assertIsNotEnabled().performTouchInput { click() }
            compose.onNodeWithTag(SETTINGS_MUSIC_OBSERVATION_TAG)
                .performScrollTo().assertIsNotEnabled().performTouchInput { click() }
        }
        compose.runOnIdle { available = true }
        compose.onNodeWithTag(SETTINGS_DONE_TAG).assertIsDisplayed().assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(StartBackground.HERMANN_PARK, background)
            assertTrue(backgroundChanges.isEmpty())
            assertEquals(2, appearanceChanges)
            assertEquals(2, musicChanges)
            assertFalse(observing)
            assertEquals(1, dismissals)
            assertEquals(0, otherActions)
        }
    }

    @Test
    fun appearanceChoicesReportOnlyAppearanceAndWaitForTheSavedPreference() {
        var appearance by mutableStateOf(RunPreferences().appearance)
        var saving by mutableStateOf(false)
        var available by mutableStateOf(true)
        val choices = mutableListOf<RunAppearance>()
        var otherActions = 0
        compose.setContent {
            RunSettingsDialog(
                gpsReady = true, gpsMessage = null,
                musicObservationEnabled = false, musicAccessGranted = true,
                musicReflectionSharingEnabled = false, musicReflectionSharingAvailable = true,
                reflectionConnectionLabel = null, demoMode = false,
                onDismiss = { otherActions++ }, onRequestLocationSetup = { otherActions++ },
                onSetMusicObservation = { otherActions++ }, onRequestMusicAccess = { otherActions++ },
                onSetMusicReflectionSharing = { otherActions++ }, onSetDemoMode = { otherActions++ },
                onOpenReflectionSetup = { otherActions++ }, settingsLoading = saving,
                preferencesAvailable = available, appearance = appearance,
                onSetAppearance = { choices += it; saving = true }
            )
        }
        compose.onNodeWithTag(appearanceOptionTag(RunAppearance.NIGHT)).performScrollTo().assertIsSelected()
        compose.onNodeWithText("Dark").assertIsDisplayed()
        compose.onNodeWithText("Light").assertIsDisplayed()
        compose.onNodeWithText("Auto").assertIsDisplayed()
        saveDialogPreview("run-appearance-dark-default.png")
        for (choice in listOf(RunAppearance.DAY, RunAppearance.AUTO, RunAppearance.NIGHT)) {
            val previous = appearance
            compose.onNodeWithTag(appearanceOptionTag(choice)).performScrollTo()
                .assertIsDisplayed().assertIsEnabled().performClick()
            // The owner controls the saved choice. A tap cannot claim persistence before it succeeds.
            compose.onNodeWithTag(appearanceOptionTag(previous)).assertIsSelected()
            RunAppearance.entries.forEach {
                compose.onNodeWithTag(appearanceOptionTag(it)).assertIsNotEnabled()
            }
            val calls = choices.size
            compose.onNodeWithTag(appearanceOptionTag(choice)).performTouchInput { click() }
            compose.runOnIdle {
                assertEquals(calls, choices.size)
                appearance = choice
                saving = false
            }
            compose.onNodeWithTag(appearanceOptionTag(choice)).assertIsSelected().assertIsEnabled()
        }
        compose.runOnIdle { available = false }
        RunAppearance.entries.forEach {
            compose.onNodeWithTag(appearanceOptionTag(it)).performScrollTo().assertIsNotEnabled()
        }
        compose.runOnIdle {
            assertEquals(listOf(RunAppearance.DAY, RunAppearance.AUTO, RunAppearance.NIGHT), choices)
            assertEquals(0, otherActions)
        }
    }

    @Test
    fun cleanStartKeepsEnergyStartHistoryAndSettingsWithoutPerRunControlPills() {
        var starts = 0
        var history = 0
        var settings = 0
        var maps = 0
        var shoes = 0
        var energy by mutableStateOf<com.runstate.mobile.data.local.PreRunEnergy?>(null)
        // Keep the infinite decorative animation alive from its first composition.
        // Auto-advancing test clocks cancel it while trying to settle the screen.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            StartRunScreen(
                selectedPreRunEnergy = energy,
                onSelectPreRunEnergy = { energy = it },
                onStart = { starts++ },
                actionsEnabled = true,
                onViewHistory = { history++ },
                showHistory = true,
                gpsSelected = true,
                gpsReady = true,
                musicSelected = true,
                musicAccessGranted = true,
                onOpenRunSettings = { settings++ },
                onOpenMap = { maps++ },
                onOpenShoes = { shoes++ },
                modifier = Modifier.systemBarsPadding()
            )
        }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(START_ENERGY_ENTRY_DURATION_MS.toLong())
        compose.mainClock.advanceTimeByFrame()
        // Permission/provider readiness alone does not claim an acquired satellite fix.
        // The real journey supplies GPS-ready copy separately from its preview evidence.
        compose.onNodeWithTag(START_RUN_STATUS_TAG).assertIsDisplayed().assertTextEquals("GPS connecting…")
        assertEquals("Music observation on", compose.onNodeWithTag(START_RUN_SETTINGS_TAG)
            .fetchSemanticsNode().config[SemanticsProperties.StateDescription])
        compose.onNodeWithText("Music observation on").assertDoesNotExist()
        compose.onNodeWithText("Map artwork").assertDoesNotExist()
        val choices = compose.onNodeWithTag(START_ENERGY_CHOICES_TAG).fetchSemanticsNode().boundsInRoot
        for (tag in listOf(START_OPEN_SHOES_TAG, START_RUN_GOAL_TAG, START_RUN_TAB_TAG,
            START_RUN_SETTINGS_TAG, START_HISTORY_TAG)) {
            val control = compose.onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue("Navigation stays below Energy", control.top >= choices.bottom)
            val minimum = with(compose.density) { androidx.compose.ui.unit.Dp(48f).toPx() }
            assertTrue("The utility retains an accessible touch target",
                control.width >= minimum - 1f && control.height >= minimum - 1f)
        }
        compose.onNodeWithTag(START_GPS_TOGGLE_TAG).assertDoesNotExist()
        compose.onNodeWithTag(START_MUSIC_TOGGLE_TAG).assertDoesNotExist()
        compose.onNodeWithTag(START_MUSIC_SHARING_TOGGLE_TAG).assertDoesNotExist()
        compose.onNodeWithTag(START_REFLECTION_SETUP_TAG).assertDoesNotExist()
        // The accepted Start layout has Shoes here; live map is in Settings -> Location.
        compose.onNodeWithTag("startExpandMap").assertDoesNotExist()
        compose.onNodeWithTag(START_TRAILS_BROWSE_TAG).assertIsDisplayed()
        savePreview(START_RUN_SCREEN_TAG, "demo-start-clean.png")
        savePreview(START_ENERGY_CHOICES_TAG, "start-energy-clean.png")
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag(START_RUN_TAB_TAG).assertIsSelected().performClick()
        assertEquals("Run is the current screen, not the recording button", 0, starts)
        assertEquals(null, energy)
        compose.onNodeWithTag(START_OPEN_SHOES_TAG).performClick()
        compose.onNodeWithTag(START_HISTORY_TAG).performClick()
        compose.onNodeWithTag(START_RUN_SETTINGS_TAG).performClick()
        compose.onNodeWithTag(START_ENERGY_HIGH_TAG).assertIsDisplayed().performClick()
        compose.onNodeWithTag(START_RUN_BUTTON_TAG).performClick()
        assertEquals(1, history)
        assertEquals(1, settings)
        assertEquals(0, maps)
        assertEquals(1, shoes)
        assertEquals(1, starts)
        assertEquals(com.runstate.mobile.data.local.PreRunEnergy.HIGH, energy)
    }

    @Test
    fun spotifyConnectionNeedsAnExplicitTapAndDoesNotEnableMusicSharing() {
        var connectCalls = 0
        var disconnectCalls = 0
        var preferenceChanges = 0
        var connected by mutableStateOf(false)
        var busy by mutableStateOf(false)
        compose.setContent {
            RunSettingsDialog(
                gpsReady = true, gpsMessage = null,
                musicObservationEnabled = false, musicAccessGranted = true,
                musicReflectionSharingEnabled = false, musicReflectionSharingAvailable = true,
                reflectionConnectionLabel = null, demoMode = false,
                onDismiss = {}, onRequestLocationSetup = {},
                onSetMusicObservation = { preferenceChanges++ }, onRequestMusicAccess = {},
                onSetMusicReflectionSharing = { preferenceChanges++ }, onSetDemoMode = {},
                onOpenReflectionSetup = {}, settingsLoading = busy,
                songSnippetsEnabled = false, onSetSongSnippets = { preferenceChanges++ },
                spotifyConnectionLabel = if (connected) "Spotify connected" else "Connect Spotify for automatic snippets",
                spotifyConnectEnabled = !connected, spotifyDisconnectEnabled = connected,
                onConnectSpotify = { connectCalls++; connected = true },
                onDisconnectSpotify = { disconnectCalls++; connected = false }
            )
        }
        compose.runOnIdle { assertEquals(0, connectCalls) }
        compose.onNodeWithTag(SETTINGS_SPOTIFY_CONNECT_TAG).performScrollTo().performClick()
        compose.onNodeWithTag(SETTINGS_SPOTIFY_CONNECT_TAG).assertIsNotEnabled()
        compose.onNodeWithTag(SETTINGS_SONG_SNIPPETS_TAG).performScrollTo().assertIsOff()
        compose.onNodeWithTag(SETTINGS_MUSIC_SHARING_TAG).performScrollTo().assertIsOff()
        compose.runOnIdle { assertEquals(1, connectCalls); assertEquals(0, preferenceChanges); busy = true }
        compose.onNodeWithTag(SETTINGS_SPOTIFY_DISCONNECT_TAG).performScrollTo().assertIsNotEnabled()
            .performTouchInput { click() }
        compose.runOnIdle { assertEquals(0, disconnectCalls); busy = false }
        compose.onNodeWithTag(SETTINGS_SPOTIFY_DISCONNECT_TAG).performClick()
        compose.runOnIdle { assertEquals(1, disconnectCalls); assertEquals(0, preferenceChanges) }
    }

    @Test
    fun snippetLookupIsSeparateDefaultOffFutureRunChoiceAndRequiresMusicSharing() {
        var sharing by mutableStateOf(false)
        var snippets by mutableStateOf(false)
        var loading by mutableStateOf(false)
        var preferencesAvailable by mutableStateOf(true)
        val choices = mutableListOf<Boolean>()
        var otherActions = 0
        compose.setContent {
            RunSettingsDialog(
                gpsReady = false, gpsMessage = null,
                musicObservationEnabled = true, musicAccessGranted = true,
                musicReflectionSharingEnabled = sharing, musicReflectionSharingAvailable = true,
                reflectionConnectionLabel = null, demoMode = false,
                onDismiss = { otherActions++ }, onRequestLocationSetup = { otherActions++ },
                onSetMusicObservation = { otherActions++ }, onRequestMusicAccess = { otherActions++ },
                onSetMusicReflectionSharing = { sharing = it }, onSetDemoMode = { otherActions++ },
                onOpenReflectionSetup = { otherActions++ }, settingsLoading = loading,
                preferencesAvailable = preferencesAvailable, songSnippetsEnabled = snippets,
                onSetSongSnippets = { choices += it; snippets = it }
            )
        }
        compose.onNodeWithTag(SETTINGS_SONG_SNIPPETS_TAG).performScrollTo().assertIsOff().assertIsNotEnabled()
        compose.onNodeWithText("For future runs, send the chosen song's title", substring = true)
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("through RunState to LRCLIB", substring = true).assertExists()
        compose.onNodeWithText("restoring the previous song is best effort and the queue may change.",
            substring = true).assertExists()
        compose.onNodeWithTag(SETTINGS_MUSIC_SHARING_TAG).performScrollTo().performClick()
        compose.onNodeWithTag(SETTINGS_SONG_SNIPPETS_TAG).performScrollTo().assertIsOff().assertIsEnabled()
        compose.runOnIdle { assertTrue(choices.isEmpty()) }
        compose.onNodeWithTag(SETTINGS_SONG_SNIPPETS_TAG).performClick().assertIsOn()
        compose.runOnIdle { assertEquals(listOf(true), choices); loading = true }
        compose.onNodeWithTag(SETTINGS_SONG_SNIPPETS_TAG).performScrollTo().assertIsNotEnabled().performTouchInput { click() }
        compose.runOnIdle {
            assertEquals(listOf(true), choices)
            loading = false
            preferencesAvailable = false
        }
        compose.onNodeWithTag(SETTINGS_SONG_SNIPPETS_TAG).performScrollTo().assertIsNotEnabled()
            .performTouchInput { click() }
        compose.runOnIdle {
            assertEquals(listOf(true), choices)
            assertEquals(0, otherActions)
            preferencesAvailable = true
        }
        compose.onNodeWithTag(SETTINGS_SONG_SNIPPETS_TAG).performScrollTo().performClick().assertIsOff()
        compose.runOnIdle { assertEquals(listOf(true, false), choices); assertEquals(0, otherActions) }
    }

    @Test
    fun settingsDefaultsAreOffAndDoneDoesNotOptIntoObservationOrSharing() {
        var observationChanges = 0
        var sharingChanges = 0
        var done = 0
        renderSettings(
            onObserve = { observationChanges++ },
            onShare = { sharingChanges++ },
            onDismiss = { done++ }
        )
        saveDialogPreview("demo-run-settings.png")
        compose.onNodeWithTag(SETTINGS_MUSIC_OBSERVATION_TAG).performScrollTo().assertIsOff()
        compose.onNodeWithTag(SETTINGS_MUSIC_SHARING_TAG).performScrollTo()
            .assertIsOff().assertIsNotEnabled()
        compose.onNodeWithTag(SETTINGS_DONE_TAG).performClick()
        assertEquals(1, done)
        assertEquals(0, observationChanges)
        assertEquals(0, sharingChanges)
    }

    @Test
    fun publicSettingsKeepNormalChoicesAndHideAllOwnerTools() {
        var ownerActions = 0
        var done = 0
        renderSettings(
            ownerToolsEnabled = false,
            onDemo = { ownerActions++ },
            onOpenRecordingSources = { ownerActions++ },
            onOpenMusicReflectionDemo = { ownerActions++ },
            onDismiss = { done++ }
        )
        assertOwnerToolsHidden()
        compose.onNodeWithTag(SETTINGS_APPEARANCE_TAG).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(SETTINGS_LOCATION_TAG).performScrollTo().assertIsEnabled()
        compose.onNodeWithTag(SETTINGS_DISTANCE_ANNOUNCEMENTS_TAG).performScrollTo().assertIsOn()
        compose.onNodeWithTag(SETTINGS_MUSIC_OBSERVATION_TAG).performScrollTo().assertIsOff()
        compose.onNodeWithTag(SETTINGS_MUSIC_SHARING_TAG).performScrollTo().assertIsOff()
        compose.onNodeWithTag(SETTINGS_SONG_SNIPPETS_TAG).performScrollTo().assertIsOff()
        compose.onNodeWithTag(SETTINGS_WEATHER_TAG).performScrollTo().assertIsOff()
        compose.onNodeWithTag(SETTINGS_REFLECTION_SETUP_TAG).performScrollTo().assertIsEnabled()
        compose.onNodeWithTag(SETTINGS_PRIVACY_TAG).performScrollTo().assertIsEnabled()
        assertOwnerToolsHidden()
        compose.onNodeWithTag(SETTINGS_DONE_TAG).performClick()
        assertEquals(1, done)
        assertEquals(0, ownerActions)
    }

    @Test
    fun privacyCanBeReadWhilePreferencesAreUnavailableWithoutInvokingAnyActions() {
        var actions = 0
        compose.setContent {
            PrivacySettingsForTest(
                onAction = { actions++ },
                settingsLoading = true,
                preferencesAvailable = false,
                actionsEnabled = false
            )
        }
        compose.onNodeWithTag(SETTINGS_PRIVACY_TAG).performScrollTo()
            .assertIsDisplayed().assertIsEnabled().performClick()
        compose.onNodeWithTag(SETTINGS_PRIVACY_DIALOG_TAG).assertIsDisplayed()
        compose.onNodeWithTag(SETTINGS_DIALOG_TAG).assertDoesNotExist()
        compose.onAllNodes(isDialog()).assertCountEquals(1)
        saveDialogPreview("run-privacy-summary.png")
        compose.onNodeWithText("Backups & recovery", ignoreCase = true)
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(SETTINGS_PRIVACY_BACK_TAG).assertIsDisplayed().performClick()
        compose.onNodeWithTag(SETTINGS_DIALOG_TAG).assertIsDisplayed()
        compose.onNodeWithTag(SETTINGS_PRIVACY_TAG).assertIsDisplayed()
        compose.onNodeWithTag(SETTINGS_DONE_TAG).assertIsNotEnabled()
        assertEquals("Reading privacy must not change settings, request access or dismiss Settings", 0, actions)
    }

    @Test
    fun privacyRestoresThenAndroidBackReturnsToTheSameSettingsPosition() {
        var actions = 0
        val restoration = StateRestorationTester(compose)
        restoration.setContent { PrivacySettingsForTest(onAction = { actions++ }) }
        val originalTop = compose.onNodeWithTag(SETTINGS_PRIVACY_TAG).performScrollTo()
            .fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithTag(SETTINGS_PRIVACY_TAG).performClick()
        compose.onNodeWithTag(SETTINGS_PRIVACY_DIALOG_TAG).assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag(SETTINGS_PRIVACY_DIALOG_TAG).assertIsDisplayed()
        compose.onAllNodes(isDialog()).assertCountEquals(1)
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithTag(SETTINGS_DIALOG_TAG).assertIsDisplayed()
        compose.onNodeWithTag(SETTINGS_PRIVACY_DIALOG_TAG).assertDoesNotExist()
        val restoredTop = compose.onNodeWithTag(SETTINGS_PRIVACY_TAG).assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot.top
        assertEquals("Returning from privacy preserves the Settings scroll position", originalTop, restoredTop, 1f)
        compose.onNodeWithTag(SETTINGS_MUSIC_OBSERVATION_TAG).performScrollTo().assertIsOff()
        compose.onNodeWithTag(SETTINGS_MUSIC_SHARING_TAG).performScrollTo().assertIsOff()
        assertEquals(0, actions)
    }

    @Test
    fun publicPrivacyIsReadableAtDoubleTextSizeAndReturnsWithoutChangingChoices() {
        var actions = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                PrivacySettingsForTest(onAction = { actions++ }, ownerToolsEnabled = false)
            }
        }
        assertOwnerToolsHidden()
        compose.onNodeWithTag(SETTINGS_PRIVACY_TAG).performScrollTo().assertIsDisplayed().performClick()
        for (heading in listOf("On this phone", "Reflections & voice", "Music choices",
            "Maps & weather", "Deleting a run", "Backups & recovery")) {
            compose.onNodeWithText(heading, ignoreCase = true).performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithText("currently no export or restore option", substring = true)
            .performScrollTo().assertIsDisplayed()
        saveDialogPreview("run-privacy-large-text.png")
        compose.onNodeWithTag(SETTINGS_PRIVACY_BACK_TAG).assertIsDisplayed().assertIsEnabled().performClick()
        compose.onNodeWithTag(SETTINGS_DIALOG_TAG).assertIsDisplayed()
        assertOwnerToolsHidden()
        compose.onNodeWithTag(SETTINGS_MUSIC_SHARING_TAG).performScrollTo().assertIsOff()
        compose.onNodeWithTag(SETTINGS_WEATHER_TAG).performScrollTo().assertIsOff()
        assertEquals(0, actions)
    }

    @Composable
    private fun PrivacySettingsForTest(
        onAction: () -> Unit,
        ownerToolsEnabled: Boolean = true,
        settingsLoading: Boolean = false,
        preferencesAvailable: Boolean = true,
        actionsEnabled: Boolean = true
    ) {
        RunSettingsDialog(
            gpsReady = false, gpsMessage = null,
            musicObservationEnabled = false, musicAccessGranted = false,
            musicReflectionSharingEnabled = false, musicReflectionSharingAvailable = false,
            reflectionConnectionLabel = null, demoMode = false,
            onDismiss = onAction, onRequestLocationSetup = onAction,
            onSetMusicObservation = { onAction() }, onRequestMusicAccess = onAction,
            onSetMusicReflectionSharing = { onAction() }, onSetDemoMode = { onAction() },
            onOpenReflectionSetup = onAction, onRetryPreferences = onAction,
            onSetWeather = { onAction() }, onSetSongSnippets = { onAction() },
            onSetDistanceAnnouncements = { onAction() }, onConnectSpotify = onAction,
            onDisconnectSpotify = onAction, onSetAppearance = { onAction() },
            onSetMusicPlayerLayout = { onAction() }, onOpenRecordingSources = onAction,
            onOpenMusicReflectionDemo = onAction, ownerToolsEnabled = ownerToolsEnabled,
            settingsLoading = settingsLoading, preferencesAvailable = preferencesAvailable,
            actionsEnabled = actionsEnabled
        )
    }

    @Test
    fun weatherSharingDefaultsOffNeedsItsOwnTapAndIsBlockedWhilePreferencesArePending() {
        val weatherChoices = mutableListOf<Boolean>()
        var otherActions = 0
        var loading by mutableStateOf(false)
        var preferencesAvailable by mutableStateOf(true)
        compose.setContent {
            StartRunScreen(
                selectedPreRunEnergy = null,
                onSelectPreRunEnergy = { otherActions++ },
                onStart = { otherActions++ }, actionsEnabled = true,
                onViewHistory = { otherActions++ }, showHistory = true
            )
            RunSettingsDialog(
                gpsReady = false, gpsMessage = null,
                musicObservationEnabled = false, musicAccessGranted = false,
                musicReflectionSharingEnabled = false, musicReflectionSharingAvailable = false,
                reflectionConnectionLabel = null, demoMode = false,
                onDismiss = { otherActions++ }, onRequestLocationSetup = { otherActions++ },
                onSetMusicObservation = { otherActions++ }, onRequestMusicAccess = { otherActions++ },
                onSetMusicReflectionSharing = { otherActions++ }, onSetDemoMode = { otherActions++ },
                onOpenReflectionSetup = { otherActions++ }, settingsLoading = loading,
                preferencesAvailable = preferencesAvailable,
                onSetWeather = { weatherChoices += it }
                // weatherEnabled intentionally uses its default, independent of location/music access.
            )
        }
        compose.onNodeWithTag(SETTINGS_WEATHER_TAG).performScrollTo().assertIsOff().assertIsEnabled()
        compose.onNodeWithText("Send one approximate start location (rounded to about 1 km) to Open-Meteo",
            substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("The provider also sees your IP address.", substring = true).assertExists()
        compose.onNodeWithText("your route is not sent", substring = true).assertExists()
        compose.runOnIdle { assertTrue(weatherChoices.isEmpty()); assertEquals(0, otherActions) }

        compose.onNodeWithTag(SETTINGS_WEATHER_TAG).performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(listOf(true), weatherChoices)
            assertEquals(0, otherActions)
            loading = true
        }
        compose.onNodeWithTag(SETTINGS_WEATHER_TAG).performScrollTo().assertIsNotEnabled()
            .performTouchInput { click() }
        compose.runOnIdle {
            assertEquals(listOf(true), weatherChoices)
            assertEquals(0, otherActions)
            loading = false
            preferencesAvailable = false
        }
        compose.onNodeWithTag(SETTINGS_WEATHER_TAG).performScrollTo().assertIsNotEnabled()
            .performTouchInput { click() }
        compose.runOnIdle {
            assertEquals(listOf(true), weatherChoices)
            assertEquals(0, otherActions)
        }
    }

    @Test
    fun settingsRemainScrollableAtDoubleTextSizeWithSeparateLocalAndCloudChoices() {
        var observe by mutableStateOf(true)
        var share by mutableStateOf(false)
        var demo by mutableStateOf(false)
        var done = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                RunSettingsDialog(
                    gpsReady = false,
                    gpsMessage = null,
                    musicObservationEnabled = observe,
                    musicAccessGranted = true,
                    musicReflectionSharingEnabled = share,
                    musicReflectionSharingAvailable = true,
                    reflectionConnectionLabel = "Computer connection selected",
                    demoMode = demo,
                    onDismiss = { done++ },
                    onRequestLocationSetup = {},
                    onSetMusicObservation = { observe = it; if (!it) share = false },
                    onRequestMusicAccess = {},
                    onSetMusicReflectionSharing = { share = it },
                    onSetDemoMode = { demo = it },
                    onOpenReflectionSetup = {}
                )
            }
        }
        compose.onNodeWithTag(SETTINGS_LOCATION_TAG).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(SETTINGS_MUSIC_OBSERVATION_TAG).performScrollTo().assertIsOn()
        compose.onNodeWithTag(SETTINGS_MUSIC_SHARING_TAG).performScrollTo().assertIsOff()
            .performClick().assertIsOn()
        assertTrue(observe)
        compose.onNodeWithTag(SETTINGS_REFLECTION_SETUP_TAG).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(SETTINGS_DEMO_MODE_TAG).performScrollTo().performClick().assertIsOn()
        compose.onNodeWithTag(SETTINGS_DONE_TAG).assertIsDisplayed().performClick()
        assertTrue(share)
        assertTrue(demo)
        assertEquals(1, done)
    }

    @Test
    fun unavailablePreferencesDisableConsentAndDemoUntilExplicitRetry() {
        var retries = 0
        var changes = 0
        var done = 0
        renderSettings(
            preferencesAvailable = false,
            settingsError = "Preferences couldn't load.",
            observe = true,
            onObserve = { changes++ },
            onShare = { changes++ },
            onDemo = { changes++ },
            onRetry = { retries++ },
            onDismiss = { done++ }
        )
        compose.onNodeWithTag(SETTINGS_RETRY_PREFERENCES_TAG).performScrollTo().performClick()
        compose.onNodeWithTag(SETTINGS_LOCATION_TAG).performScrollTo().assertIsEnabled()
        compose.onNodeWithTag(SETTINGS_MUSIC_OBSERVATION_TAG).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag(SETTINGS_MUSIC_SHARING_TAG).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag(SETTINGS_DEMO_MODE_TAG).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag(SETTINGS_DONE_TAG).performClick()
        assertEquals(1, retries)
        assertEquals(1, done)
        assertEquals(0, changes)
    }

    @Test
    fun firstReviewSavesDeclinedMusicOnceAndDoesNotRepeatAfterRestoration() {
        val fixture = fixture()
        val restoration = renderRoot(fixture)
        waitForTag(WELCOME_NAME_DIALOG_TAG)
        compose.onNodeWithTag(WELCOME_NAME_SKIP_TAG).performClick()
        waitForTag(SETTINGS_DIALOG_TAG)
        compose.onNodeWithTag(SETTINGS_MUSIC_OBSERVATION_TAG).performScrollTo().assertIsOff()
        compose.onNodeWithTag(SETTINGS_DONE_TAG).performClick()
        waitForTag(START_RUN_SCREEN_TAG)
        compose.waitUntil(5_000L) {
            compose.onAllNodesWithTag(SETTINGS_DIALOG_TAG).fetchSemanticsNodes().isEmpty()
        }
        assertEquals(RunPreferences(musicSetupComplete = true, runnerName = ""), fixture.loadPreferences())
        assertEquals(2, fixture.preferenceWrites)
        restoration.emulateSavedInstanceStateRestore()
        waitForTag(START_RUN_SCREEN_TAG)
        compose.onNodeWithTag(SETTINGS_DIALOG_TAG).assertDoesNotExist()
        compose.onNodeWithTag(WELCOME_NAME_DIALOG_TAG).assertDoesNotExist()
        assertEquals("Music observation off", compose.onNodeWithTag(START_RUN_SETTINGS_TAG)
            .fetchSemanticsNode().config[SemanticsProperties.StateDescription])
        assertEquals(2, fixture.preferenceWrites)
        assertEquals(0, runBlocking { fixture.database.runDao().countRuns() })
    }

    @Test
    fun trackingOnlyChoiceSurvivesReopeningWithoutChangingLocalMusicObservation() {
        val original = RunPreferences(observeMusic = true, shareMusicInReflection = true, musicSetupComplete = true)
        val fixture = fixture(original)
        val restoration = renderRoot(fixture)
        waitForTag(START_RUN_SCREEN_TAG)
        compose.onNodeWithTag(START_RUN_SETTINGS_TAG).assertIsDisplayed().performClick()
        compose.onNodeWithTag("runSettingsCloudReflections").performScrollTo().assertIsOn().performClick()
        compose.waitUntil(5_000L) { fixture.preferenceWrites == 1 }
        assertEquals(original.copy(cloudReflectionsEnabled = false), fixture.loadPreferences())
        compose.onNodeWithTag(SETTINGS_DONE_TAG).performClick()
        restoration.emulateSavedInstanceStateRestore()
        waitForTag(START_RUN_SCREEN_TAG)
        compose.onNodeWithTag(START_RUN_SETTINGS_TAG).assertIsDisplayed().performClick()
        compose.onNodeWithTag("runSettingsCloudReflections").performScrollTo().assertIsOff()
        compose.onNodeWithTag(SETTINGS_MUSIC_OBSERVATION_TAG).performScrollTo().assertIsOn()
    }

    @Test
    fun everyMileChoicePersistsAcrossSettingsReopenAndRestorationWithoutChangingMusic() {
        val original = RunPreferences(observeMusic = true, shareMusicInReflection = true,
            musicSetupComplete = true, captureWeather = true, findSongSnippets = true)
        val fixture = fixture(original)
        val restoration = renderRoot(fixture)
        waitForTag(START_RUN_SCREEN_TAG)
        compose.onNodeWithTag(START_RUN_SETTINGS_TAG).assertIsDisplayed().performClick()
        waitForTag(SETTINGS_DIALOG_TAG)
        compose.onNodeWithTag(SETTINGS_DISTANCE_ANNOUNCEMENTS_TAG)
            .performScrollTo().assertIsOn().performClick()
        compose.waitUntil(5_000L) { fixture.preferenceWrites == 1 }
        val quiet = original.copy(distanceAnnouncements = false)
        assertEquals(quiet, fixture.loadPreferences())
        assertEquals(quiet, fixture.preferences)
        compose.onNodeWithTag(SETTINGS_DONE_TAG).performClick()
        compose.waitUntil(5_000L) {
            compose.onAllNodesWithTag(SETTINGS_DIALOG_TAG).fetchSemanticsNodes().isEmpty()
        }
        restoration.emulateSavedInstanceStateRestore()
        waitForTag(START_RUN_SCREEN_TAG)
        compose.onNodeWithTag(START_RUN_SETTINGS_TAG).assertIsDisplayed().performClick()
        waitForTag(SETTINGS_DIALOG_TAG)
        compose.onNodeWithTag(SETTINGS_DISTANCE_ANNOUNCEMENTS_TAG)
            .performScrollTo().assertIsOff().performClick()
        compose.waitUntil(5_000L) { fixture.preferenceWrites == 2 }
        assertEquals(original, fixture.loadPreferences())
        assertEquals(original, fixture.preferences)
        compose.onNodeWithTag(SETTINGS_MUSIC_OBSERVATION_TAG).performScrollTo().assertIsOn()
        compose.onNodeWithTag(SETTINGS_MUSIC_SHARING_TAG).performScrollTo().assertIsOn()
        compose.onNodeWithTag(SETTINGS_SONG_SNIPPETS_TAG).performScrollTo().assertIsOn()
        assertEquals(0, runBlocking { fixture.database.runDao().countRuns() })
    }

    @Test
    fun recordingSourcesReturnToSettingsAndNeverReplaceNormalRunHistory() {
        val preferences = RunPreferences(musicSetupComplete = true)
        val fixture = fixture(preferences)
        val gps = RunEntity(runId = UUID.randomUUID().toString(), state = StoredRunState.COMPLETED,
            officialStartEpochMillis = fixture.time - 600_000L, startTimezoneId = "America/Chicago",
            lastCheckpointEpochMillis = fixture.time, finishEpochMillis = fixture.time,
            finalDistanceMeters = 1609.344, metricSource = MetricSource.GPS,
            telemetryCoverage = TelemetryCoverage.COMPLETE, displayDistanceUnit = DistanceUnit.MILES,
            trackingMode = RunTrackingMode.GPS)
        val sample = gps.copy(runId = UUID.randomUUID().toString(),
            metricSource = MetricSource.FIXTURE, trackingMode = RunTrackingMode.FIXTURE,
            officialStartEpochMillis = gps.officialStartEpochMillis - 1_000L)
        runBlocking {
            fixture.database.runDao().insert(gps)
            fixture.database.runDao().insert(sample)
        }
        renderRoot(fixture)
        waitForTag(START_RUN_SCREEN_TAG)
        compose.onNodeWithTag(START_RUN_SETTINGS_TAG).assertIsDisplayed().performClick()
        waitForTag(SETTINGS_DIALOG_TAG)
        compose.onNodeWithTag(SETTINGS_RECORDING_SOURCES_TAG).performScrollTo().assertIsEnabled().performClick()
        waitForTag(RUN_HISTORY_SCREEN_TAG)
        waitForTag(RUN_HISTORY_ROW_TAG_PREFIX + gps.runId)
        compose.onNodeWithText("RECORDING SOURCES").assertIsDisplayed()
        compose.onNodeWithText("All records").assertIsDisplayed()
        compose.onNodeWithTag("runHistoryFilter:LONGEST").assertDoesNotExist()
        compose.onNodeWithTag("runHistoryFilter:SAMPLES").performClick()
        compose.onNodeWithTag(RUN_HISTORY_ROW_TAG_PREFIX + sample.runId).assertIsDisplayed()
        compose.onNodeWithTag(RUN_HISTORY_ROW_TAG_PREFIX + gps.runId).assertDoesNotExist()
        androidx.test.espresso.Espresso.pressBack()
        waitForTag(SETTINGS_DIALOG_TAG)
        compose.onNodeWithTag(RUN_HISTORY_SCREEN_TAG).assertDoesNotExist()
        compose.onNodeWithTag(SETTINGS_DONE_TAG).performClick()
        compose.waitUntil(5_000L) {
            compose.onAllNodesWithTag(SETTINGS_DIALOG_TAG).fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithContentDescription("Log History").assertIsDisplayed().performClick()
        waitForTag(RUN_HISTORY_SCREEN_TAG)
        waitForTag(RUN_HISTORY_ROW_TAG_PREFIX + gps.runId)
        compose.onNodeWithText("LOG HISTORY").assertIsDisplayed()
        compose.onNodeWithText("All runs").assertIsDisplayed()
        compose.onNodeWithTag("runHistoryFilter:ALL").assertIsSelected()
        compose.onNodeWithTag("runHistoryFilter:LONGEST").assertIsDisplayed()
        compose.onNodeWithTag("runHistoryFilter:FASTEST").assertIsDisplayed()
        compose.onNodeWithTag("runHistoryFilter:SAMPLES").assertDoesNotExist()
        compose.onNodeWithTag(RUN_HISTORY_ROW_TAG_PREFIX + sample.runId).assertDoesNotExist()
        compose.onNodeWithText("Back to run").performClick()
        waitForTag(START_RUN_SCREEN_TAG)
        compose.onNodeWithTag(SETTINGS_DIALOG_TAG).assertDoesNotExist()
        assertEquals(preferences, fixture.loadPreferences())
        assertEquals(0, fixture.preferenceWrites)
        assertEquals(2, runBlocking { fixture.database.runDao().countRuns() })
        assertEquals(gps, runBlocking { fixture.database.runDao().findById(gps.runId) })
        assertEquals(sample, runBlocking { fixture.database.runDao().findById(sample.runId) })
    }

    @Test
    fun restoredRecordingSourcesBecomeNormalHistoryWhenOwnerToolsAreUnavailable() {
        val preferences = RunPreferences(musicSetupComplete = true)
        val fixture = fixture(preferences)
        val sessionId = fixture.sessionId
        val gps = RunEntity(runId = UUID.randomUUID().toString(), state = StoredRunState.COMPLETED,
            officialStartEpochMillis = fixture.time - 600_000L, startTimezoneId = "America/Chicago",
            lastCheckpointEpochMillis = fixture.time, finishEpochMillis = fixture.time,
            finalDistanceMeters = 1609.344, metricSource = MetricSource.GPS,
            telemetryCoverage = TelemetryCoverage.COMPLETE, displayDistanceUnit = DistanceUnit.MILES,
            trackingMode = RunTrackingMode.GPS)
        val sample = gps.copy(runId = UUID.randomUUID().toString(),
            metricSource = MetricSource.FIXTURE, trackingMode = RunTrackingMode.FIXTURE,
            officialStartEpochMillis = gps.officialStartEpochMillis - 1_000L)
        runBlocking {
            fixture.database.runDao().insert(gps)
            fixture.database.runDao().insert(sample)
        }
        val restoration = renderRoot(fixture)
        waitForTag(START_RUN_SCREEN_TAG)
        compose.onNodeWithTag(START_RUN_SETTINGS_TAG).assertIsDisplayed().performClick()
        waitForTag(SETTINGS_DIALOG_TAG)
        compose.onNodeWithTag(SETTINGS_RECORDING_SOURCES_TAG).performScrollTo().performClick()
        waitForTag(RUN_HISTORY_SCREEN_TAG)
        compose.onNodeWithTag("runHistoryFilter:SAMPLES").performClick()
        waitForTag(RUN_HISTORY_ROW_TAG_PREFIX + sample.runId)
        compose.onNodeWithText("RECORDING SOURCES").assertIsDisplayed()
        // Capture the owner-only destination and filter before the public root restores them.
        compose.runOnIdle { fixture.ownerToolsAfterDisposal = false }
        restoration.emulateSavedInstanceStateRestore()
        waitForTag(RUN_HISTORY_SCREEN_TAG)
        waitForTag(RUN_HISTORY_ROW_TAG_PREFIX + gps.runId)
        compose.onNodeWithText("LOG HISTORY").assertIsDisplayed()
        compose.onNodeWithText("RECORDING SOURCES").assertDoesNotExist()
        compose.onNodeWithText("All records").assertDoesNotExist()
        compose.onNodeWithText("Back to settings").assertDoesNotExist()
        compose.onNodeWithTag("runHistoryFilter:ALL").assertIsSelected()
        compose.onNodeWithTag("runHistoryFilter:LONGEST").assertIsDisplayed()
        compose.onNodeWithTag("runHistoryFilter:FASTEST").assertIsDisplayed()
        compose.onNodeWithTag("runHistoryFilter:GPS").assertDoesNotExist()
        compose.onNodeWithTag("runHistoryFilter:SAMPLES").assertDoesNotExist()
        compose.onNodeWithTag(RUN_HISTORY_ROW_TAG_PREFIX + sample.runId).assertDoesNotExist()
        compose.onNodeWithText("Back to run").performClick()
        waitForTag(START_RUN_SCREEN_TAG)
        compose.onNodeWithTag(SETTINGS_DIALOG_TAG).assertDoesNotExist()
        assertEquals(sessionId, fixture.sessionId)
        assertEquals(preferences, fixture.loadPreferences())
        assertEquals(0, fixture.preferenceWrites)
        assertEquals(0, fixture.preparedRuns)
        assertEquals(2, runBlocking { fixture.database.runDao().countRuns() })
        assertEquals(gps, runBlocking { fixture.database.runDao().findById(gps.runId) })
        assertEquals(sample, runBlocking { fixture.database.runDao().findById(sample.runId) })
    }

    @Test
    fun musicReflectionDemoFinishesWithoutCreatingARunOrChangingSavedPreferences() {
        compose.mainClock.autoAdvance = true
        val choices = RunPreferences(musicSetupComplete = true, observeMusic = true,
            shareMusicInReflection = true, appearance = RunAppearance.NIGHT)
        val fixture = fixture(choices)
        val saved = RunEntity(runId = UUID.randomUUID().toString(), state = StoredRunState.COMPLETED,
            officialStartEpochMillis = fixture.time - 600_000L, startTimezoneId = "America/Chicago",
            lastCheckpointEpochMillis = fixture.time, finishEpochMillis = fixture.time,
            finalDistanceMeters = 1609.344, metricSource = MetricSource.GPS,
            telemetryCoverage = TelemetryCoverage.COMPLETE, displayDistanceUnit = DistanceUnit.MILES,
            trackingMode = RunTrackingMode.GPS)
        demoWork { fixture.database.runDao().insert(saved) }
        renderRoot(fixture)
        openMusicReflectionDemo()
        compose.onNodeWithTag(SETTINGS_DIALOG_TAG).assertDoesNotExist()
        compose.onNodeWithTag(ACTIVE_RUN_MUSIC_TAG).performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithContentDescription("Pause run").performClick()
        compose.mainClock.advanceTimeBy(450L)
        compose.onNodeWithTag(HOLD_TO_STOP_TAG)
            .performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        compose.mainClock.advanceTimeBy(100L)
        compose.onNodeWithTag(RUN_COMPLETE_SCREEN_TAG).assertIsDisplayed()
        compose.onNodeWithText("Not sure").performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(100L)
        compose.onNodeWithTag(RUN_COMPLETE_NOTE_TAG).assertDoesNotExist()
        compose.onNodeWithText("Something else? Add a note").assertDoesNotExist()
        compose.onNodeWithText("Save Activity").assertDoesNotExist()
        compose.onNodeWithTag(RUN_COMPLETE_SAVE_ACTIVITY_TAG).performClick()
        waitForDemoTag(START_RUN_SCREEN_TAG)
        compose.runOnIdle {
            assertEquals(1, fixture.demoAudioCreations)
            assertEquals(1, fixture.demoAudio.starts)
            assertEquals(1, fixture.demoAudio.finishes)
            assertEquals(listOf(ReflectionBranch.NO_SELECTION), fixture.demoAudio.answers)
            assertEquals(1, fixture.demoAudio.closes)
        }
        assertEquals(RunAdmission.ReadyForCountdown,
            demoWork { fixture.coordinator.journeySnapshot().admission })
        assertEquals(0, fixture.preparedRuns)
        assertEquals(0, fixture.preferenceWrites)
        assertEquals(choices, fixture.loadPreferences())
        assertEquals(saved, demoWork { fixture.database.runDao().findById(saved.runId) })
        assertEquals(1, demoWork { fixture.database.runDao().countRuns() })
        assertTrue(demoWork { fixture.database.runDao().findActiveRuns().isEmpty() })
    }

    @Test
    fun musicReflectionDemoDoesNotReopenWhenTheRootIsRecreated() {
        compose.mainClock.autoAdvance = true
        val fixture = fixture(RunPreferences(musicSetupComplete = true))
        renderRoot(fixture)
        openMusicReflectionDemo()
        // StateRestorationTester toggles its content off/on through runOnIdle. With
        // a frozen clock those changes can coalesce before a frame disposes remember.
        // Model the Activity's actual composition removal/recreation explicitly.
        Log.d("RunStateDemoTest", "recreation: remove root")
        compose.runOnUiThread { fixture.shown = false }
        compose.waitUntil(8_000L) {
            compose.mainClock.advanceTimeByFrame()
            compose.onAllNodesWithTag(MUSIC_REFLECTION_DEMO_SCREEN_TAG).fetchSemanticsNodes().isEmpty() &&
                fixture.demoAudio.closes == 1
        }
        Log.d("RunStateDemoTest", "recreation: old controller closed, create root")
        compose.runOnUiThread { fixture.shown = true }
        waitForDemoTag(START_RUN_SCREEN_TAG)
        compose.onNodeWithTag(MUSIC_REFLECTION_DEMO_SCREEN_TAG).assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, fixture.demoAudioCreations)
            assertEquals(1, fixture.demoAudio.closes)
            assertEquals(0, fixture.demoAudio.starts)
        }
        assertEquals(0, fixture.preparedRuns)
        assertEquals(0, fixture.preferenceWrites)
        assertEquals(0, demoWork { fixture.database.runDao().countRuns() })
    }

    @Test
    fun removingOwnerToolsClosesAnOpenDemoWithoutCreatingARun() {
        val choices = RunPreferences(musicSetupComplete = true)
        val fixture = fixture(choices)
        renderRoot(fixture)
        openMusicReflectionDemo()
        compose.runOnUiThread { fixture.ownerToolsEnabled = false }
        waitForDemoTag(START_RUN_SCREEN_TAG)
        compose.onNodeWithTag(MUSIC_REFLECTION_DEMO_SCREEN_TAG).assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, fixture.demoAudioCreations)
            assertEquals(1, fixture.demoAudio.closes)
            assertEquals(0, fixture.demoAudio.starts)
            assertEquals(0, fixture.demoAudio.finishes)
        }
        assertEquals(0, fixture.preparedRuns)
        assertEquals(0, fixture.preferenceWrites)
        assertEquals(choices, fixture.loadPreferences())
        assertEquals(0, demoWork { fixture.database.runDao().countRuns() })
    }

    @Test
    fun aRealPausedRunTakesOverTheDemoWithoutBeingResumedOrCompleted() {
        compose.mainClock.autoAdvance = true
        val fixture = fixture(RunPreferences(musicSetupComplete = true))
        renderRoot(fixture)
        openMusicReflectionDemo()
        // This isolated in-memory coordinator models service recovery while the demo is open.
        // No production service or phone database participates in the test.
        Log.d("RunStateDemoTest", "takeover: prepare real paused fixture")
        val paused = demoWork {
            fixture.coordinator.beginCountdown()
            fixture.coordinator.requestStart().await()
            fixture.time += 5_000L
            fixture.coordinator.requestAction(RunActionKind.PAUSE).await()
            fixture.database.runDao().findActiveRuns().single()
        }
        Log.d("RunStateDemoTest", "takeover: publish paused fixture")
        compose.runOnUiThread { fixture.tracking.value = RunTrackingServiceState.Paused(paused.runId) }
        compose.waitUntil(8_000L) {
            compose.mainClock.advanceTimeByFrame()
            compose.onAllNodesWithTag(MUSIC_REFLECTION_DEMO_SCREEN_TAG).fetchSemanticsNodes().isEmpty() &&
                compose.onAllNodesWithContentDescription("Resume run").fetchSemanticsNodes().isNotEmpty()
        }
        Log.d("RunStateDemoTest", "takeover: actual paused surface visible")
        compose.onNodeWithContentDescription("Resume run").assertIsDisplayed()
        compose.onNodeWithTag(START_RUN_SETTINGS_TAG).assertDoesNotExist()
        compose.onNodeWithTag(SETTINGS_MUSIC_REFLECTION_DEMO_TAG).assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, fixture.demoAudioCreations)
            assertEquals(1, fixture.demoAudio.closes)
            assertEquals(0, fixture.demoAudio.finishes)
        }
        assertEquals(paused, demoWork { fixture.database.runDao().findById(paused.runId) })
        assertEquals(StoredRunState.PAUSED, paused.state)
        assertEquals(1, fixture.preparedRuns)
        assertEquals(1, demoWork { fixture.database.runDao().countRuns() })
        assertEquals(0, fixture.preferenceWrites)
    }

    @Test
    fun sampleDraftSurvivesRotationButColdProcessRestorationDefaultsBackToGps() {
        val fixture = fixture(RunPreferences(musicSetupComplete = true))
        val restoration = renderRoot(fixture)
        waitForTag(START_RUN_SCREEN_TAG)
        chooseSampleDemo()
        compose.onNodeWithText("Sample demo · simulated distance").assertIsDisplayed()
        assertEquals("Sample run", compose.onNodeWithTag(START_RUN_BUTTON_TAG).assertIsDisplayed()
            .fetchSemanticsNode().config[SemanticsProperties.StateDescription])
        compose.onNodeWithText("Sample run", useUnmergedTree = true).assertDoesNotExist()
        restoration.emulateSavedInstanceStateRestore()
        waitForTag(START_RUN_SCREEN_TAG)
        compose.onNodeWithText("Sample demo · simulated distance").assertIsDisplayed()
        // Save the old process's draft first. Only disposal creates the new process identity,
        // so restoration must reject an actually mismatched saved key rather than resaving it.
        compose.runOnIdle { fixture.sessionAfterDisposal = "new-process-${UUID.randomUUID()}" }
        restoration.emulateSavedInstanceStateRestore()
        waitForTag(START_RUN_SCREEN_TAG)
        compose.onNodeWithText("Sample demo · simulated distance").assertDoesNotExist()
        assertEquals("GPS run", compose.onNodeWithTag(START_RUN_BUTTON_TAG).assertIsDisplayed()
            .fetchSemanticsNode().config[SemanticsProperties.StateDescription])
        assertEquals(0, runBlocking { fixture.database.runDao().countRuns() })
    }

    @Test
    fun sameProcessSampleDraftRestoresToGpsWhenOwnerToolsAreUnavailable() {
        val preferences = RunPreferences(musicSetupComplete = true)
        val fixture = fixture(preferences)
        val sessionId = fixture.sessionId
        val restoration = renderRoot(fixture)
        waitForTag(START_RUN_SCREEN_TAG)
        chooseSampleDemo()
        compose.onNodeWithText("Sample demo · simulated distance").assertIsDisplayed()
        // The process identity stays the same, so the owner-tools gate itself must reject
        // the saved Sample draft. Changing the flag before saving would miss that case.
        compose.runOnIdle { fixture.ownerToolsAfterDisposal = false }
        restoration.emulateSavedInstanceStateRestore()
        waitForTag(START_RUN_SCREEN_TAG)
        assertNormalGpsAndPreferences(fixture, preferences)
        compose.onNodeWithTag(START_RUN_SETTINGS_TAG).assertIsDisplayed().performClick()
        waitForTag(SETTINGS_DIALOG_TAG)
        assertOwnerToolsHidden()
        compose.onNodeWithTag(SETTINGS_MUSIC_OBSERVATION_TAG).performScrollTo().assertIsOff()
        assertEquals(sessionId, fixture.sessionId)
        assertEquals(0, fixture.preferenceWrites)
        assertEquals(0, fixture.preparedRuns)
        assertEquals(0, fixture.demoAudioCreations)
        assertEquals(0, runBlocking { fixture.database.runDao().countRuns() })
    }

    @Test
    fun trackingOnlyRunKeepsLocalMusicAndSavesWithoutSharingSongEvidence() {
        val fixture = fixture(RunPreferences(observeMusic = true, shareMusicInReflection = true,
            findSongSnippets = true, cloudReflectionsEnabled = false, musicSetupComplete = true))
        renderRoot(fixture)
        waitForTag(START_RUN_SCREEN_TAG)
        chooseSampleDemo()
        val run = startSample(fixture)
        assertTrue(run.musicObservationRequested)
        assertFalse(run.musicReflectionSharingAllowed)
        finishShortSample(fixture, save = true)
        val saved = requireNotNull(runBlocking { fixture.database.runDao().findById(run.runId) })
        assertEquals(StoredRunState.COMPLETED, saved.state)
        assertFalse(saved.musicReflectionSharingAllowed)
        compose.onNodeWithText("Save Activity").assertIsDisplayed().performClick()
        waitForTag(RUN_ACTIVITY_RECAP_TAG)
    }

    @Test
    fun savedThenDiscardedSamplesRetainMusicPreferencesAndEachReturnsToNormalGps() {
        val savedChoices = RunPreferences(observeMusic = true,
            shareMusicInReflection = true, musicSetupComplete = true)
        val fixture = fixture(savedChoices)
        renderRoot(fixture)
        waitForTag(START_RUN_SCREEN_TAG)
        chooseSampleDemo()
        val first = startSample(fixture)
        assertEquals(RunTrackingMode.FIXTURE, first.trackingMode)
        assertTrue(first.musicObservationRequested)
        assertTrue(first.musicReflectionSharingAllowed)
        finishShortSample(fixture, save = true)
        val saved = runBlocking { fixture.database.runDao().findById(first.runId) }!!
        assertEquals(StoredRunState.COMPLETED, saved.state)
        assertEquals(MetricSource.FIXTURE, saved.metricSource)
        assertTrue(saved.musicObservationRequested)
        assertTrue(saved.musicReflectionSharingAllowed)
        compose.onNodeWithText("Save Activity").assertIsDisplayed().performClick()
        waitForTag(RUN_ACTIVITY_RECAP_TAG)
        compose.onNodeWithText("Done").assertIsDisplayed().performClick()
        waitForTag(START_RUN_SCREEN_TAG)
        assertNormalGpsAndPreferences(fixture, savedChoices)
        chooseSampleDemo()
        val second = startSample(fixture)
        assertTrue(second.musicObservationRequested)
        assertTrue(second.musicReflectionSharingAllowed)
        finishShortSample(fixture, save = false)
        waitForTag(START_RUN_SCREEN_TAG)
        assertNormalGpsAndPreferences(fixture, savedChoices)
        assertEquals(null, runBlocking { fixture.database.runDao().findById(second.runId) })
        assertEquals(saved.copy(activityFinalized = true), runBlocking { fixture.database.runDao().findById(first.runId) })
        assertEquals(1, runBlocking { fixture.database.runDao().countRuns() })
    }

    @Test
    fun rememberedMusicWithoutAndroidAccessDoesNotInventPerRunConsentOrErasePreferences() {
        val choices = RunPreferences(observeMusic = true,
            shareMusicInReflection = true, musicSetupComplete = true)
        val fixture = fixture(choices, musicAccess = false)
        renderRoot(fixture)
        waitForTag(START_RUN_SCREEN_TAG)
        compose.onNodeWithText("Music observation needs access").assertIsDisplayed()
        chooseSampleDemo()
        val run = startSample(fixture)
        assertEquals(RunTrackingMode.FIXTURE, run.trackingMode)
        assertFalse(run.musicObservationRequested)
        assertFalse(run.musicReflectionSharingAllowed)
        finishShortSample(fixture, save = false)
        waitForTag(START_RUN_SCREEN_TAG)
        assertNormalGpsAndPreferences(fixture, choices)
        assertEquals(0, runBlocking { fixture.database.runDao().countRuns() })
    }

    private fun renderSettings(
        preferencesAvailable: Boolean = true,
        settingsError: String? = null,
        observe: Boolean = false,
        onObserve: (Boolean) -> Unit = {},
        onShare: (Boolean) -> Unit = {},
        onDemo: (Boolean) -> Unit = {},
        onRetry: () -> Unit = {},
        onDismiss: () -> Unit = {},
        ownerToolsEnabled: Boolean = true,
        onOpenRecordingSources: (() -> Unit)? = null,
        onOpenMusicReflectionDemo: (() -> Unit)? = null
    ) {
        compose.setContent {
            RunSettingsDialog(
                gpsReady = false,
                gpsMessage = null,
                musicObservationEnabled = observe,
                musicAccessGranted = true,
                musicReflectionSharingEnabled = false,
                musicReflectionSharingAvailable = true,
                reflectionConnectionLabel = "Computer connection selected",
                demoMode = false,
                onDismiss = onDismiss,
                onRequestLocationSetup = {},
                onSetMusicObservation = onObserve,
                onRequestMusicAccess = {},
                onSetMusicReflectionSharing = onShare,
                onSetDemoMode = onDemo,
                onOpenReflectionSetup = {},
                settingsError = settingsError,
                preferencesAvailable = preferencesAvailable,
                onRetryPreferences = onRetry,
                ownerToolsEnabled = ownerToolsEnabled,
                onOpenRecordingSources = onOpenRecordingSources,
                onOpenMusicReflectionDemo = onOpenMusicReflectionDemo
            )
        }
    }

    private fun fixture(initial: RunPreferences? = null, musicAccess: Boolean = true): Fixture =
        Fixture(initial, musicAccess).also { fixtures += it }

    private fun renderRoot(fixture: Fixture): StateRestorationTester {
        runBlocking { fixture.coordinator.initialize() }
        return StateRestorationTester(compose).also { restoration ->
            restoration.setContent {
                DisposableEffect(fixture) {
                    onDispose {
                        fixture.sessionAfterDisposal?.let { next ->
                            fixture.sessionId = next
                            fixture.sessionAfterDisposal = null
                        }
                        fixture.ownerToolsAfterDisposal?.let { next ->
                            fixture.ownerToolsEnabled = next
                            fixture.ownerToolsAfterDisposal = null
                        }
                    }
                }
                if (fixture.shown) RunJourneyRoot(
                    coordinator = fixture.coordinator,
                    reflectionCoordinator = null,
                    database = fixture.database,
                    trackingFlow = fixture.tracking,
                    lifecycle = LocalLifecycleOwner.current.lifecycle,
                    completionSpeakerFactory = { fixture.silentSpeaker },
                    reflectionConnection = ReflectionConnectionState.Local,
                    runPreferences = fixture.preferences,
                    onSaveRunPreferences = { next ->
                        check(!fixture.failPreferenceWrites) { "Simulated preference storage failure" }
                        withContext(Dispatchers.IO) { fixture.store.save(next) }
                        fixture.preferences = next
                        fixture.preferenceWrites++
                    },
                    runSettingsSessionId = fixture.sessionId,
                    musicAccessProvider = { fixture.musicAccess },
                    musicReflectionDemoAudioFactory = fixture.demoAudioFactory,
                    ownerToolsEnabled = fixture.ownerToolsEnabled
                )
            }
        }
    }

    private fun openMusicReflectionDemo() {
        // Settings' scroll semantics wait for their animation. Keep that normal clock
        // until the offscreen demo option is visible, then freeze before opening the
        // continuously animated Active screen.
        check(compose.mainClock.autoAdvance) { "Settings scrolling needs its advancing clock" }
        Log.d("RunStateDemoTest", "entry: waiting for Start")
        waitForTag(START_RUN_SCREEN_TAG)
        Log.d("RunStateDemoTest", "entry: open Settings")
        compose.onNodeWithTag(START_RUN_SETTINGS_TAG).assertIsDisplayed().performClick()
        waitForTag(SETTINGS_DIALOG_TAG)
        Log.d("RunStateDemoTest", "entry: scroll to Music Reflection Demo")
        compose.onNodeWithTag(SETTINGS_MUSIC_REFLECTION_DEMO_TAG).performScrollTo()
            .assertIsEnabled()
        Log.d("RunStateDemoTest", "entry: option visible, freeze before click")
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(SETTINGS_MUSIC_REFLECTION_DEMO_TAG)
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        waitForDemoTag(MUSIC_REFLECTION_DEMO_SCREEN_TAG)
        compose.mainClock.advanceTimeBy(1_800L)
        Log.d("RunStateDemoTest", "entry: demo ready")
    }

    private fun waitForDemoTag(tag: String) {
        try {
            compose.waitUntil(8_000L) {
                compose.mainClock.advanceTimeByFrame()
                compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (failure: Throwable) {
            val tree = runCatching { compose.onRoot(useUnmergedTree = true).printToString(maxDepth = 8) }
                .getOrElse { "Semantics unavailable: ${it.javaClass.simpleName}" }
            Log.e("RunStateDemoTest", "Timed out waiting for $tag\n${tree.take(6_000)}")
            throw failure
        }
    }

    private fun <T> demoWork(block: suspend () -> T): T = runBlocking {
        withTimeout(8_000L) { block() }
    }

    private fun chooseSampleDemo() {
        compose.onNodeWithTag(START_RUN_SETTINGS_TAG).performClick()
        waitForTag(SETTINGS_DIALOG_TAG)
        compose.onNodeWithTag(SETTINGS_DEMO_MODE_TAG).performScrollTo().assertIsOff()
            .performClick().assertIsOn()
        compose.onNodeWithTag(SETTINGS_DONE_TAG).performClick()
        compose.waitUntil(5_000L) {
            compose.onAllNodesWithTag(SETTINGS_DIALOG_TAG).fetchSemanticsNodes().isEmpty()
        }
    }

    private fun startSample(fixture: Fixture): RunEntity {
        compose.onNodeWithText("Sample demo · simulated distance").assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(START_RUN_BUTTON_TAG).performClick()
        // Root admission crosses a real Room/coroutine boundary. Advance frames until the
        // countdown effect itself has begun, rather than advancing time before it.
        compose.waitUntil(5_000L) {
            compose.mainClock.advanceTimeByFrame()
            compose.onAllNodesWithText(COUNTDOWN_SECONDS.toString()).fetchSemanticsNodes().isNotEmpty()
        }
        for (digit in (COUNTDOWN_SECONDS - 1 downTo 1).map(Int::toString)) {
            compose.mainClock.advanceTimeBy(COUNTDOWN_STEP_MILLIS)
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithText(digit).assertIsDisplayed()
        }
        compose.mainClock.advanceTimeBy(COUNTDOWN_STEP_MILLIS)
        compose.mainClock.advanceTimeByFrame()
        // Active has a continuous decorative frame loop. Drive it explicitly until Pause,
        // rather than asking Compose to auto-advance an animation that never becomes idle.
        compose.waitUntil(5_000L) {
            compose.mainClock.advanceTimeByFrame()
            compose.onAllNodesWithContentDescription("Pause run").fetchSemanticsNodes().isNotEmpty()
        }
        compose.mainClock.advanceTimeBy(1_600L)
        compose.onNodeWithContentDescription("Pause run").assertIsDisplayed().assertIsEnabled()
        return runBlocking { fixture.database.runDao().findActiveRuns().single() }
    }

    private fun finishShortSample(fixture: Fixture, save: Boolean) {
        compose.runOnIdle { fixture.time += 5_000L }
        compose.onNodeWithContentDescription("Pause run").performClick()
        compose.waitUntil(5_000L) {
            compose.mainClock.advanceTimeByFrame()
            compose.onAllNodesWithContentDescription("Resume run").fetchSemanticsNodes().isNotEmpty()
        }
        compose.mainClock.advanceTimeBy(450L)
        compose.onNodeWithContentDescription("Resume run").assertIsDisplayed()
        compose.onNodeWithTag(HOLD_TO_STOP_TAG).performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(HOLD_TO_STOP_MILLIS + 100L)
        compose.onNodeWithTag(HOLD_TO_STOP_TAG).performTouchInput { up() }
        compose.waitUntil(5_000L) {
            compose.mainClock.advanceTimeByFrame()
            compose.onAllNodesWithText("Save this short run?").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(if (save) "Save run" else "Discard run").performClick()
        // Keep manual frames while Active remains behind its decision dialog. Resume normal
        // idling only after the saved/discarded result has removed that animated surface.
        compose.waitUntil(5_000L) {
            compose.mainClock.advanceTimeByFrame()
            compose.onAllNodesWithTag(ACTIVE_RUN_SCREEN_TAG).fetchSemanticsNodes().isEmpty()
        }
        compose.mainClock.autoAdvance = true
        waitForTag(if (save) RUN_COMPLETE_SCREEN_TAG else START_RUN_SCREEN_TAG)
    }

    private fun assertNormalGpsAndPreferences(fixture: Fixture, expected: RunPreferences) {
        compose.onNodeWithText("Sample demo · simulated distance").assertDoesNotExist()
        assertEquals("GPS run", compose.onNodeWithTag(START_RUN_BUTTON_TAG).assertIsDisplayed()
            .fetchSemanticsNode().config[SemanticsProperties.StateDescription])
        assertEquals(expected, fixture.loadPreferences())
        assertEquals(expected, fixture.preferences)
    }

    private fun assertOwnerToolsHidden() {
        compose.onNodeWithText("DEMO & TESTING").assertDoesNotExist()
        compose.onNodeWithTag(SETTINGS_DEMO_MODE_TAG).assertDoesNotExist()
        compose.onNodeWithTag(SETTINGS_RECORDING_SOURCES_TAG).assertDoesNotExist()
        compose.onNodeWithTag(SETTINGS_MUSIC_REFLECTION_DEMO_TAG).assertDoesNotExist()
    }

    private fun waitForTag(tag: String) {
        compose.waitUntil(5_000L) {
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun savePreview(tag: String, fileName: String) {
        val bitmap = compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
        File(ApplicationProvider.getApplicationContext<Context>().cacheDir, fileName)
            .outputStream().use { output ->
                assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
            }
    }

    private fun saveDialogPreview(fileName: String) {
        val bitmap = compose.onNode(isDialog()).captureToImage().asAndroidBitmap()
        File(ApplicationProvider.getApplicationContext<Context>().cacheDir, fileName)
            .outputStream().use { output ->
                assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
            }
    }

    private class Fixture(initial: RunPreferences?, val musicAccess: Boolean) {
        val context: Context = ApplicationProvider.getApplicationContext()
        val preferencesName = "run-settings-ui-${UUID.randomUUID()}"
        val store = AndroidRunPreferencesStore(context, preferencesName)
        var preferences by mutableStateOf(runBlocking {
            withContext(Dispatchers.IO) {
                if (initial != null) store.save(initial)
                store.load()
            }
        })
        var shown by mutableStateOf(true)
        var sessionId by mutableStateOf("process-${UUID.randomUUID()}")
        var sessionAfterDisposal: String? = null
        var ownerToolsEnabled by mutableStateOf(true)
        var ownerToolsAfterDisposal: Boolean? = null
        var preferenceWrites = 0
        var failPreferenceWrites = false
        var demoAudioCreations = 0
        val demoAudio = SilentDemoAudio()
        val demoAudioFactory: (Context) -> MusicReflectionDemoAudio = {
            demoAudioCreations++
            demoAudio
        }
        var preparedRuns = 0
        var time = 1_756_000_000_000L
        val database = Room.inMemoryDatabaseBuilder(context, RunStateDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private val clock = object : Clock() {
            override fun getZone(): ZoneId = ZoneOffset.UTC
            override fun withZone(zone: ZoneId): Clock = this
            override fun instant(): Instant = Instant.ofEpochMilli(time)
        }
        val coordinator = RunSessionCoordinator(database.runDao(),
            PreparedRunFactory(clock, { ZoneOffset.UTC }, { preparedRuns++; UUID.randomUUID() }), scope,
            database.locationDao())
        val tracking = MutableStateFlow<RunTrackingServiceState>(RunTrackingServiceState.Idle)
        val silentSpeaker = object : ReflectionSpeaker {
            override fun speak(text: String) = Unit
            override fun stop() = Unit
            override fun close() = Unit
        }
        fun loadPreferences(): RunPreferences = runBlocking {
            withContext(Dispatchers.IO) { store.load() }
        }
    }

    private class SilentDemoAudio : MusicReflectionDemoAudio {
        override val player = MutableStateFlow(SpotifyLivePlayerState(
            connected = true, title = "Good as Hell", artist = "Lizzo", paused = true,
            canSkipPrevious = true, canSkipNext = true, canTogglePlayback = true))
        override val state = MutableStateFlow(MusicReflectionDemoAudioState())
        var starts = 0
        var finishes = 0
        var closes = 0
        val answers = mutableListOf<ReflectionBranch>()
        override fun prepare() = Unit
        override fun startMusic() { starts++ }
        override fun toggleMusic() = Unit
        override fun restartMusic() = Unit
        override fun finishRun() {
            finishes++
            state.value = MusicReflectionDemoAudioState(reflection = RunCompleteReflectionDisplay.Preparing)
        }
        override fun selectEnergy(branch: ReflectionBranch) {
            answers += branch
            state.value = MusicReflectionDemoAudioState(
                reflection = RunCompleteReflectionDisplay.Ready("The exact demo reflection."))
        }
        override fun setPresentationHeld(held: Boolean) = Unit
        override fun stop() = Unit
        override fun retry() = Unit
        override fun close() { closes++ }
    }
}
