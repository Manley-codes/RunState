package com.runstate.mobile

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.runstate.mobile.data.local.RunStateDatabase
import com.runstate.mobile.data.local.ShoePairEntity
import com.runstate.mobile.location.RunTrackingServiceState
import com.runstate.mobile.run.PreparedRunFactory
import com.runstate.mobile.run.RunActionKind
import com.runstate.mobile.run.RunSessionCoordinator
import com.runstate.mobile.ui.ACTIVE_RUN_SCREEN_TAG
import com.runstate.mobile.ui.OPENING_SCREEN_TAG
import com.runstate.mobile.ui.RUN_COMPLETE_SCREEN_TAG
import com.runstate.mobile.ui.RUN_HISTORY_SCREEN_TAG
import com.runstate.mobile.ui.SETTINGS_DIALOG_TAG
import com.runstate.mobile.ui.SETTINGS_DONE_TAG
import com.runstate.mobile.ui.START_HISTORY_TAG
import com.runstate.mobile.ui.START_RUN_BUTTON_TAG
import com.runstate.mobile.ui.START_RUN_SETTINGS_TAG
import com.runstate.mobile.ui.START_OPEN_SHOES_TAG
import com.runstate.mobile.ui.ReflectionSpeaker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/** Isolated Room and silent speech; no phone runs, playback or location requests. */
class OpeningJourneyTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>(StandardTestDispatcher())

    @Test fun customShoeAddsThroughStartAndRestoresItsSelectionWithoutStartingARun() {
        Fixture().use { fixture ->
            val restoration = fixture.render(initialRequestId = 0, autoAdvanceClock = true)
            awaitEnabledTag(START_OPEN_SHOES_TAG)
            compose.onNodeWithTag(START_OPEN_SHOES_TAG).performClick()
            awaitEnabledTag("shoeSheet")
            compose.onNodeWithTag("shoeList")
                .performScrollToNode(hasText("+ Add a different or second pair"))
            compose.onNodeWithText("+ Add a different or second pair").performClick()
            compose.onNodeWithTag("shoeCustomBrand").performScrollTo().performTextInput("Test Brand")
            compose.onNodeWithTag("shoeCustomModel").performScrollTo().performTextInput("Everyday Runner")
            compose.onNodeWithTag("shoeCustomNickname").performScrollTo().performTextInput("Blue pair")
            compose.onNodeWithTag("shoeAddCustom").performScrollTo().performClick()
            awaitNoTag("shoeSheet")
            val pair = runBlocking { fixture.database.shoeDao().observeShoes().first().single() }
            assertEquals("Test Brand", pair.pair.brand)
            assertEquals("Everyday Runner", pair.pair.model)
            assertEquals("Blue pair", pair.pair.nickname)
            assertEquals(0.0, pair.totalDistanceMeters, 0.0)
            assertEquals(pair.pair.shoePairId, runBlocking { fixture.database.shoeDao().selectedActivePairId() })
            compose.onNodeWithText("Blue pair").assertIsDisplayed()
            restoration.emulateSavedInstanceStateRestore()
            awaitEnabledTag(START_OPEN_SHOES_TAG)
            compose.onNodeWithText("Blue pair").assertIsDisplayed()
            compose.onNodeWithTag(START_OPEN_SHOES_TAG).performClick()
            awaitEnabledTag("shoeSelect:${pair.pair.shoePairId}")
            compose.onNodeWithTag("shoeSelect:${pair.pair.shoePairId}").assertIsSelected()
            assertEquals(1, runBlocking { fixture.database.shoeDao().observeShoes().first().size })
            assertEquals(0, runBlocking { fixture.database.runDao().countRuns() })
        }
    }

    @Test fun shoeArchiveUndoAndExplicitNoSelectionOperateThroughRootAndSurviveRestoration() {
        Fixture().use { fixture ->
            val pair = ShoePairEntity(UUID.randomUUID().toString(), "Test Brand", "Road Runner",
                "Morning pair", 1_756_000_000_000L)
            runBlocking { fixture.database.shoeDao().addAndSelect(pair) }
            val restoration = fixture.render(initialRequestId = 0, autoAdvanceClock = true)
            awaitEnabledTag(START_OPEN_SHOES_TAG)
            compose.onNodeWithTag(START_OPEN_SHOES_TAG).performClick()
            awaitEnabledTag("shoeSelect:${pair.shoePairId}")
            compose.onNodeWithTag("shoeArchive:${pair.shoePairId}").performClick()
            awaitEnabledTag("shoeUndo")
            assertNull(runBlocking { fixture.database.shoeDao().selectedActivePairId() })
            assertTrue(runBlocking { fixture.database.shoeDao().findPair(pair.shoePairId)!!.archived })
            fixture.requestLauncherOpening()
            compose.onNodeWithTag(OPENING_SCREEN_TAG).assertDoesNotExist()
            compose.onNodeWithTag("shoeUndo").performClick()
            awaitEnabledTag("shoeSelect:${pair.shoePairId}")
            compose.onNodeWithTag("shoeSelect:${pair.shoePairId}").assertIsSelected()
            assertFalse(runBlocking { fixture.database.shoeDao().findPair(pair.shoePairId)!!.archived })
            compose.onNodeWithTag("shoeNoSelection").performScrollTo().performClick()
            awaitNoTag("shoeSheet")
            assertNull(runBlocking { fixture.database.shoeDao().selectedActivePairId() })
            restoration.emulateSavedInstanceStateRestore()
            awaitEnabledTag(START_OPEN_SHOES_TAG)
            compose.onNodeWithTag(OPENING_SCREEN_TAG).assertDoesNotExist()
            assertNull(runBlocking { fixture.database.shoeDao().selectedActivePairId() })
            assertEquals(1, runBlocking { fixture.database.shoeDao().observeShoes().first().size })
            assertEquals(0, runBlocking { fixture.database.runDao().countRuns() })
        }
    }

    @Test fun freshLaunchCanSkipWithoutStartingRunAndDoesNotReplayOnRestoration() {
        Fixture().use { fixture ->
            val restoration = fixture.render()
            awaitTag("openingScreen")
            compose.onNodeWithTag(START_RUN_BUTTON_TAG).assertDoesNotExist()
            compose.onNodeWithTag("openingScreen").performClick()
            awaitTag(START_RUN_BUTTON_TAG)
            restoration.emulateSavedInstanceStateRestore()
            awaitTag(START_RUN_BUTTON_TAG)
            compose.onNodeWithTag("openingScreen").assertDoesNotExist()
            assertEquals(0, runBlocking { fixture.database.runDao().countRuns() })
        }
    }

    @Test fun eachWarmLauncherRequestCanShowAgainWithoutStartingOrSavingARun() {
        Fixture().use { fixture ->
            val restoration = fixture.render()
            awaitTag(OPENING_SCREEN_TAG)
            compose.onNodeWithTag(OPENING_SCREEN_TAG).performClick()
            awaitTag(START_RUN_BUTTON_TAG)
            repeat(2) {
                fixture.requestLauncherOpening()
                awaitTag(OPENING_SCREEN_TAG)
                compose.onNodeWithTag(START_RUN_BUTTON_TAG).assertDoesNotExist()
                compose.onNodeWithTag(OPENING_SCREEN_TAG).performClick()
                awaitTag(START_RUN_BUTTON_TAG)
            }
            restoration.emulateSavedInstanceStateRestore()
            awaitTag(START_RUN_BUTTON_TAG)
            compose.onNodeWithTag(OPENING_SCREEN_TAG).assertDoesNotExist()
            assertEquals(0, runBlocking { fixture.database.runDao().countRuns() })
        }
    }

    @Test fun nonLauncherEntryGoesStraightToStart() {
        Fixture().use { fixture ->
            fixture.render(initialRequestId = 0)
            awaitTag(START_RUN_BUTTON_TAG)
            compose.onNodeWithTag("openingScreen").assertDoesNotExist()
        }
    }

    @Test fun ordinaryBackgroundAndResumeWithoutLauncherRequestDoesNotReplay() {
        Fixture().use { fixture ->
            fixture.render()
            awaitTag(OPENING_SCREEN_TAG)
            compose.onNodeWithTag(OPENING_SCREEN_TAG).performClick()
            awaitTag(START_RUN_BUTTON_TAG)
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            awaitTag(START_RUN_BUTTON_TAG)
            compose.onNodeWithTag(OPENING_SCREEN_TAG).assertDoesNotExist()
            assertEquals(0, runBlocking { fixture.database.runDao().countRuns() })
        }
    }

    @Test fun recoveredRunningRunBypassesEntrance() = verifyRecovery(paused = false, completed = false)
    @Test fun recoveredPausedRunBypassesEntrance() = verifyRecovery(paused = true, completed = false)
    @Test fun pendingSavedActivityBypassesEntrance() = verifyRecovery(paused = true, completed = true)

    @Test fun launcherRequestWhileSettingsOpenIsConsumedAndDoesNotAppearOnDismiss() {
        Fixture().use { fixture ->
            fixture.render(initialRequestId = 0)
            awaitTag(START_RUN_BUTTON_TAG)
            compose.onNodeWithTag(START_RUN_SETTINGS_TAG).performClick()
            awaitTag(SETTINGS_DIALOG_TAG)
            fixture.requestLauncherOpening()
            compose.onNodeWithTag(SETTINGS_DIALOG_TAG).assertIsDisplayed()
            compose.onNodeWithTag(OPENING_SCREEN_TAG).assertDoesNotExist()
            compose.onNodeWithTag(SETTINGS_DONE_TAG).performClick()
            awaitTag(START_RUN_BUTTON_TAG)
            compose.onNodeWithTag(OPENING_SCREEN_TAG).assertDoesNotExist()
            assertEquals(0, runBlocking { fixture.database.runDao().countRuns() })
        }
    }

    @Test fun launcherRequestWhileHistoryOpenIsConsumedAndDoesNotAppearOnReturn() {
        Fixture().use { fixture ->
            fixture.render(initialRequestId = 0)
            awaitTag(START_RUN_BUTTON_TAG)
            compose.onNodeWithTag(START_HISTORY_TAG).performClick()
            awaitTag(RUN_HISTORY_SCREEN_TAG)
            fixture.requestLauncherOpening()
            compose.onNodeWithTag(RUN_HISTORY_SCREEN_TAG).assertIsDisplayed()
            compose.onNodeWithTag(OPENING_SCREEN_TAG).assertDoesNotExist()
            compose.onNodeWithText("Back to run").performClick()
            awaitTag(START_RUN_BUTTON_TAG)
            compose.onNodeWithTag(OPENING_SCREEN_TAG).assertDoesNotExist()
            assertEquals(0, runBlocking { fixture.database.runDao().countRuns() })
        }
    }

    private fun verifyRecovery(paused: Boolean, completed: Boolean) {
        Fixture().use { fixture ->
            runBlocking {
                val original = fixture.coordinator()
                original.initialize()
                original.beginCountdown()
                original.requestStart().await()
                if (paused) original.requestAction(RunActionKind.PAUSE).await()
                if (completed) original.requestAction(RunActionKind.COMPLETE).await()
            }
            fixture.render()
            awaitTag(if (completed) RUN_COMPLETE_SCREEN_TAG else ACTIVE_RUN_SCREEN_TAG)
            compose.onNodeWithTag("openingScreen").assertDoesNotExist()
            // A later launcher intent must not cover a recovered recording or unfinished
            // activity either. It is consumed now, not kept for a later ReadyToStart state.
            fixture.requestLauncherOpening()
            compose.onNodeWithTag(if (completed) RUN_COMPLETE_SCREEN_TAG else ACTIVE_RUN_SCREEN_TAG)
                .assertIsDisplayed()
            compose.onNodeWithTag(OPENING_SCREEN_TAG).assertDoesNotExist()
            assertEquals(1, runBlocking { fixture.database.runDao().countRuns() })
        }
    }

    private fun awaitTag(tag: String) {
        compose.waitUntil(10_000) {
            compose.mainClock.advanceTimeBy(16)
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag(tag).assertIsDisplayed()
    }

    private fun awaitEnabledTag(tag: String) {
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag(tag) and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag(tag).assertIsDisplayed().assertIsEnabled()
    }

    private fun awaitNoTag(tag: String) {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()
        }
    }

    private inner class Fixture : AutoCloseable {
        private var requestId by mutableIntStateOf(0)
        val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),
            RunStateDatabase::class.java).build()
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private val silent = object : ReflectionSpeaker {
            override fun speak(text: String) = Unit
            override fun stop() = Unit
            override fun close() = Unit
        }

        fun coordinator() = RunSessionCoordinator(database.runDao(), PreparedRunFactory(
            Clock.fixed(Instant.ofEpochMilli(1_756_000_000_000), ZoneOffset.UTC),
            { ZoneOffset.UTC }, UUID::randomUUID), scope, database.locationDao(), shoeDao = database.shoeDao())

        fun requestLauncherOpening() {
            compose.runOnUiThread { requestId++ }
            compose.mainClock.advanceTimeBy(64)
        }

        fun render(initialRequestId: Int = 1, autoAdvanceClock: Boolean = false): StateRestorationTester {
            requestId = initialRequestId
            val coordinator = coordinator()
            val restoration = StateRestorationTester(compose)
            // Entrance timing tests own their clock. Shoe forms instead need normal frame
            // progression for sheet, scroll, focus and IME animations to settle between actions.
            compose.mainClock.autoAdvance = autoAdvanceClock
            restoration.setContent {
                RunJourneyRoot(coordinator, null, database,
                    remember { MutableStateFlow<RunTrackingServiceState>(RunTrackingServiceState.Idle) },
                    LocalLifecycleOwner.current.lifecycle,
                    openingRequestId = requestId,
                    completionSpeakerFactory = { silent },
                    musicAccessProvider = { false }, gpsLocationReady = { false })
            }
            return restoration
        }

        override fun close() {
            compose.activityRule.scenario.close()
            scope.cancel()
            database.close()
        }
    }
}
