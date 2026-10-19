package com.runstate.mobile.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.Modifier
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.runstate.mobile.ReflectionConnectionState
import com.runstate.mobile.RunJourneyRoot
import com.runstate.mobile.data.local.MetricSource
import com.runstate.mobile.data.local.RunEntity
import com.runstate.mobile.data.local.RunStateDatabase
import com.runstate.mobile.data.local.RunTrackingMode
import com.runstate.mobile.data.local.StoredRunState
import com.runstate.mobile.location.RunTrackingServiceState
import com.runstate.mobile.run.PreparedRunFactory
import com.runstate.mobile.run.RunSessionCoordinator
import com.runstate.mobile.settings.AndroidRunPreferencesStore
import com.runstate.mobile.settings.RunPreferences
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

    @After
    fun closeIsolatedFixtures() {
        compose.mainClock.autoAdvance = true
        if (fixtures.isNotEmpty()) {
            compose.runOnIdle { fixtures.forEach { it.shown = false } }
            compose.waitForIdle()
        }
        fixtures.forEach { fixture ->
            fixture.scope.cancel()
            fixture.database.close()
            check(fixture.preferencesName.startsWith("run-settings-ui-"))
            fixture.context.deleteSharedPreferences(fixture.preferencesName)
        }
    }

    @Test
    fun cleanStartKeepsEnergyStartHistoryAndSettingsWithoutPerRunControlPills() {
        var starts = 0
        var history = 0
        var settings = 0
        var energy by mutableStateOf<com.runstate.mobile.data.local.PreRunEnergy?>(null)
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
                modifier = Modifier.systemBarsPadding()
            )
        }
        // Permission/provider readiness alone does not claim an acquired satellite fix.
        // The real journey supplies GPS-ready copy separately from its preview evidence.
        compose.onNodeWithText("GPS enabled").assertIsDisplayed()
        compose.onNodeWithText("Music observation on").assertIsDisplayed()
        compose.onNodeWithTag(START_GPS_TOGGLE_TAG).assertDoesNotExist()
        compose.onNodeWithTag(START_MUSIC_TOGGLE_TAG).assertDoesNotExist()
        compose.onNodeWithTag(START_MUSIC_SHARING_TOGGLE_TAG).assertDoesNotExist()
        compose.onNodeWithTag(START_REFLECTION_SETUP_TAG).assertDoesNotExist()
        savePreview(START_RUN_SCREEN_TAG, "demo-start-clean.png")
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(400L)
        savePreview(START_ENERGY_CHOICES_TAG, "energy-edge-beam-a.png")
        compose.mainClock.advanceTimeBy(800L)
        savePreview(START_ENERGY_CHOICES_TAG, "energy-edge-beam-b.png")
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag(START_HISTORY_TAG).performClick()
        compose.onNodeWithTag(START_RUN_SETTINGS_TAG).performClick()
        compose.onNodeWithTag(START_ENERGY_HIGH_TAG).assertIsDisplayed().performClick()
        compose.onNodeWithTag(START_RUN_BUTTON_TAG).performClick()
        assertEquals(1, history)
        assertEquals(1, settings)
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
        waitForTag(SETTINGS_DIALOG_TAG)
        compose.onNodeWithTag(SETTINGS_MUSIC_OBSERVATION_TAG).performScrollTo().assertIsOff()
        compose.onNodeWithTag(SETTINGS_DONE_TAG).performClick()
        waitForTag(START_RUN_SCREEN_TAG)
        compose.waitUntil(5_000L) {
            compose.onAllNodesWithTag(SETTINGS_DIALOG_TAG).fetchSemanticsNodes().isEmpty()
        }
        assertEquals(RunPreferences(musicSetupComplete = true), fixture.loadPreferences())
        assertEquals(1, fixture.preferenceWrites)
        restoration.emulateSavedInstanceStateRestore()
        waitForTag(START_RUN_SCREEN_TAG)
        compose.onNodeWithTag(SETTINGS_DIALOG_TAG).assertDoesNotExist()
        compose.onNodeWithText("Music observation off").assertIsDisplayed()
        assertEquals(1, fixture.preferenceWrites)
        assertEquals(0, runBlocking { fixture.database.runDao().countRuns() })
    }

    @Test
    fun everyMileChoicePersistsAcrossSettingsReopenAndRestorationWithoutChangingMusic() {
        val original = RunPreferences(observeMusic = true, shareMusicInReflection = true,
            musicSetupComplete = true, captureWeather = true, findSongSnippets = true)
        val fixture = fixture(original)
        val restoration = renderRoot(fixture)
        waitForTag(START_RUN_SCREEN_TAG)
        compose.onNodeWithTag(START_RUN_SETTINGS_TAG).performScrollTo().performClick()
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
        compose.onNodeWithTag(START_RUN_SETTINGS_TAG).performScrollTo().performClick()
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
    fun sampleDraftSurvivesRotationButColdProcessRestorationDefaultsBackToGps() {
        val fixture = fixture(RunPreferences(musicSetupComplete = true))
        val restoration = renderRoot(fixture)
        waitForTag(START_RUN_SCREEN_TAG)
        chooseSampleDemo()
        compose.onNodeWithText("Sample demo · simulated distance").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        waitForTag(START_RUN_SCREEN_TAG)
        compose.onNodeWithText("Sample demo · simulated distance").assertIsDisplayed()
        // Save the old process's draft first. Only disposal creates the new process identity,
        // so restoration must reject an actually mismatched saved key rather than resaving it.
        compose.runOnIdle { fixture.sessionAfterDisposal = "new-process-${UUID.randomUUID()}" }
        restoration.emulateSavedInstanceStateRestore()
        waitForTag(START_RUN_SCREEN_TAG)
        compose.onNodeWithText("Sample demo · simulated distance").assertDoesNotExist()
        compose.onNodeWithText("GPS run").assertIsDisplayed()
        assertEquals(0, runBlocking { fixture.database.runDao().countRuns() })
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
        onDismiss: () -> Unit = {}
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
                onRetryPreferences = onRetry
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
                        withContext(Dispatchers.IO) { fixture.store.save(next) }
                        fixture.preferences = next
                        fixture.preferenceWrites++
                    },
                    runSettingsSessionId = fixture.sessionId,
                    musicAccessProvider = { fixture.musicAccess }
                )
            }
        }
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
        compose.mainClock.autoAdvance = true
        compose.waitUntil(5_000L) {
            compose.onAllNodesWithText("Pause").fetchSemanticsNodes().isNotEmpty()
        }
        return runBlocking { fixture.database.runDao().findActiveRuns().single() }
    }

    private fun finishShortSample(fixture: Fixture, save: Boolean) {
        compose.runOnIdle { fixture.time += 5_000L }
        compose.onNodeWithText("Pause").performClick()
        compose.waitUntil(5_000L) {
            compose.onAllNodesWithText("Resume").fetchSemanticsNodes().isNotEmpty()
        }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(HOLD_TO_STOP_TAG).performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(HOLD_TO_STOP_MILLIS + 100L)
        compose.onNodeWithTag(HOLD_TO_STOP_TAG).performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitUntil(5_000L) {
            compose.onAllNodesWithText("Save this short run?").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(if (save) "Save run" else "Discard run").performClick()
        waitForTag(if (save) RUN_COMPLETE_SCREEN_TAG else START_RUN_SCREEN_TAG)
    }

    private fun assertNormalGpsAndPreferences(fixture: Fixture, expected: RunPreferences) {
        compose.onNodeWithText("Sample demo · simulated distance").assertDoesNotExist()
        compose.onNodeWithText("GPS run").assertIsDisplayed()
        assertEquals(expected, fixture.loadPreferences())
        assertEquals(expected, fixture.preferences)
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
        var preferenceWrites = 0
        var time = 1_756_000_000_000L
        val database = Room.inMemoryDatabaseBuilder(context, RunStateDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private val clock = object : Clock() {
            override fun getZone(): ZoneId = ZoneOffset.UTC
            override fun withZone(zone: ZoneId): Clock = this
            override fun instant(): Instant = Instant.ofEpochMilli(time)
        }
        val coordinator = RunSessionCoordinator(database.runDao(),
            PreparedRunFactory(clock, { ZoneOffset.UTC }, { UUID.randomUUID() }), scope,
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
}
