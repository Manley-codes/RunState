package com.runstate.mobile.ui

import com.runstate.mobile.data.local.ReflectionBranch
import com.runstate.mobile.music.SongPassageRequest
import com.runstate.mobile.music.SongPassageStatus
import com.runstate.mobile.music.SpotifyLivePlayerState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicReflectionDemoControllerTest {
    private class Speaker : ReflectionSpeaker {
        data class Passage(
            val text: String,
            val request: SongPassageRequest,
            val allowed: () -> Boolean,
            val done: () -> Unit
        )
        val facts = mutableListOf<String>()
        val factsCallbacks = mutableListOf<() -> Unit>()
        val passages = mutableListOf<Passage>()
        var stops = 0
        var closes = 0
        var completeSynchronouslyOnStop = false
        override fun speak(text: String) = error("A demo must not fall back to generic speech")
        override fun speakCompletionFacts(text: String, onFinished: () -> Unit) {
            facts += text
            factsCallbacks += onFinished
        }
        override fun speakWithSongPassage(
            text: String, passage: SongPassageRequest,
            canPlayPassage: () -> Boolean, onFinished: () -> Unit
        ) {
            passages += Passage(text, passage, canPlayPassage, onFinished)
        }
        override fun stop() {
            stops++
            if (completeSynchronouslyOnStop) {
                factsCallbacks.lastOrNull()?.invoke()
                passages.lastOrNull()?.done?.invoke()
            }
        }
        override fun close() { closes++ }
    }

    private class Transport : MusicReflectionDemoTransport {
        override val player = MutableStateFlow(SpotifyLivePlayerState())
        var openings = 0
        var stops = 0
        var closes = 0
        var opening: suspend () -> SongPassageStatus = { SongPassageStatus.FINISHED }
        override suspend fun playOpening(): SongPassageStatus {
            openings++
            return opening()
        }
        override fun stop() { stops++ }
        override fun close() { closes++ }
    }

    private class Fixture {
        val speaker = Speaker()
        val transport = Transport()
        val preparations = mutableListOf<Pair<String, Boolean>>()
        var prepare: suspend (String, Boolean) -> Boolean = { _, _ -> true }
        val controller = MusicReflectionDemoController(
            speaker, transport,
            prepareVoice = { text, runVoice ->
                preparations += text to runVoice
                prepare(text, runVoice)
            },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        )
    }

    private inline fun fixture(check: (Fixture) -> Unit) {
        val value = Fixture()
        try { check(value) } finally { value.controller.close() }
    }

    @Test fun silentPreparationDoesNotPlayMusicOrEitherVoice() = fixture { f ->
        f.controller.prepare()
        assertEquals(setOf(MusicReflectionDemoData.facts to true,
            MusicReflectionDemoData.approvedReflection to false), f.preparations.toSet())
        assertTrue(f.speaker.facts.isEmpty())
        assertTrue(f.speaker.passages.isEmpty())
        assertEquals(0, f.transport.openings)
        assertFalse(f.controller.state.value.active)
    }

    @Test fun missingOriginalRunVoiceShowsErrorWithoutSpeakingOrStartingPassage() = fixture { f ->
        f.prepare = { _, _ -> false }
        f.controller.finishRun()
        f.controller.selectEnergy(ReflectionBranch.POWERED_UP)
        f.controller.setPresentationHeld(false)
        assertTrue(f.speaker.facts.isEmpty())
        assertTrue(f.speaker.passages.isEmpty())
        assertEquals(0, f.transport.openings)
        assertEquals("The run voice couldn’t load. Try again.", f.controller.state.value.error)
        assertFalse(f.controller.state.value.active)
    }

    @Test fun missingOriginalReflectionVoiceNeverFallsBackOrStartsPassage() = fixture { f ->
        f.prepare = { _, runVoice -> runVoice }
        f.controller.finishRun()
        f.speaker.factsCallbacks.single().invoke()
        f.controller.selectEnergy(ReflectionBranch.POWERED_UP)
        f.controller.setPresentationHeld(false)
        assertEquals(listOf(MusicReflectionDemoData.facts), f.speaker.facts)
        assertTrue(f.speaker.passages.isEmpty())
        assertEquals("The reflection voice couldn’t load. Try again.", f.controller.state.value.error)
        assertFalse(f.controller.state.value.active)
    }

    @Test fun factsAndEnergyMergeBothFinishBeforeReflectionAndItsPassage() = fixture { f ->
        f.controller.finishRun()
        f.controller.selectEnergy(ReflectionBranch.POWERED_UP)
        assertTrue(f.speaker.passages.isEmpty())
        f.speaker.factsCallbacks.single().invoke()
        assertTrue("The Energy merge still holds presentation", f.speaker.passages.isEmpty())
        assertFalse(f.controller.state.value.active)
        f.controller.setPresentationHeld(false)
        val passage = f.speaker.passages.single()
        assertEquals(MusicReflectionDemoData.approvedReflection, passage.text)
        assertEquals("Good as Hell", passage.request.trackTitle)
        assertEquals("Lizzo", passage.request.artistName)
        assertEquals(MusicReflectionDemoData.trackUri, passage.request.spotifyMediaId)
        assertEquals(0L, passage.request.startPositionMillis)
        assertTrue(passage.allowed())
        assertTrue(f.controller.state.value.active)
        passage.done()
        assertFalse(f.controller.state.value.active)
        f.controller.setPresentationHeld(false)
        f.controller.selectEnergy(ReflectionBranch.POWERED_UP)
        assertEquals("A repeated effect or selection must not replay it", 1, f.speaker.passages.size)
    }

    @Test fun releasingEnergyHoldEarlyStillWaitsForFacts() = fixture { f ->
        f.controller.finishRun()
        f.controller.selectEnergy(ReflectionBranch.FEELING_GOOD)
        f.controller.setPresentationHeld(false)
        assertTrue(f.speaker.passages.isEmpty())
        f.speaker.factsCallbacks.single().invoke()
        assertEquals(1, f.speaker.passages.size)
    }

    @Test fun selectionAfterFactsSynchronouslyHoldsBeforeComposeEffectArrives() = fixture { f ->
        f.controller.finishRun()
        f.speaker.factsCallbacks.single().invoke()
        f.controller.selectEnergy(ReflectionBranch.SPENT)
        assertTrue(f.speaker.passages.isEmpty())
        f.controller.setPresentationHeld(false)
        assertEquals(1, f.speaker.passages.size)
    }

    @Test fun selectedBranchControlsBothDisplayedAndSpokenWords() {
        val expected = mapOf(
            ReflectionBranch.POWERED_UP to "finished powered up",
            ReflectionBranch.FEELING_GOOD to "finished feeling good",
            ReflectionBranch.SPENT to "finished gassed out",
            ReflectionBranch.NO_SELECTION to "put in twenty-eight active minutes"
        )
        expected.forEach { (branch, phrase) -> fixture { f ->
            f.controller.finishRun()
            f.speaker.factsCallbacks.single().invoke()
            f.controller.selectEnergy(branch)
            f.controller.setPresentationHeld(false)
            val spoken = f.speaker.passages.single().text
            assertTrue(spoken.contains(phrase))
            assertEquals(RunCompleteReflectionDisplay.Ready(spoken), f.controller.state.value.reflection)
            assertTrue(spoken.startsWith("You started high"))
            if (branch != ReflectionBranch.POWERED_UP) assertFalse(spoken.contains("finished powered up"))
            if (branch == ReflectionBranch.NO_SELECTION) assertFalse(spoken.contains("finished"))
        } }
    }

    @Test fun stoppingInvalidatesFactsCallbacksBeforeSpeakerStopCanCompleteSynchronously() = fixture { f ->
        f.controller.finishRun()
        f.controller.selectEnergy(ReflectionBranch.POWERED_UP)
        f.speaker.completeSynchronouslyOnStop = true
        f.controller.stop()
        f.controller.setPresentationHeld(false)
        f.speaker.factsCallbacks.single().invoke()
        assertTrue(f.speaker.passages.isEmpty())
        assertFalse(f.controller.state.value.active)
        f.speaker.completeSynchronouslyOnStop = false
        f.controller.retry()
        assertEquals("Stopped facts were not falsely marked finished", 2, f.speaker.facts.size)
    }

    @Test fun backgroundStopPreventsPendingEnergyEffectFromRestartingAudio() = fixture { f ->
        f.controller.finishRun()
        f.speaker.factsCallbacks.single().invoke()
        f.controller.selectEnergy(ReflectionBranch.POWERED_UP)
        f.controller.stop()
        f.controller.setPresentationHeld(false)
        assertTrue(f.speaker.passages.isEmpty())
        f.controller.retry()
        assertEquals("An explicit retry may restart the selected reflection", 1, f.speaker.passages.size)
    }

    @Test fun stoppedPassageCannotPlayOrFinishANewerRetry() = fixture { f ->
        f.controller.finishRun()
        f.speaker.factsCallbacks.single().invoke()
        f.controller.selectEnergy(ReflectionBranch.NO_SELECTION)
        val stale = f.speaker.passages.single()
        f.controller.stop()
        assertFalse(stale.allowed())
        f.controller.retry()
        assertEquals(2, f.speaker.passages.size)
        assertTrue(f.controller.state.value.active)
        stale.done()
        assertTrue("A stale completion cannot clear the current speech state", f.controller.state.value.active)
        assertTrue(f.speaker.passages.last().allowed())
    }

    @Test fun changingEnergyImmediatelyInvalidatesPreviousReflectionBeforeNewMergeFinishes() = fixture { f ->
        f.controller.finishRun()
        f.speaker.factsCallbacks.single().invoke()
        f.controller.selectEnergy(ReflectionBranch.POWERED_UP)
        f.controller.setPresentationHeld(false)
        val previous = f.speaker.passages.single()
        val stopsBefore = f.speaker.stops
        f.controller.selectEnergy(ReflectionBranch.SPENT)
        assertFalse("Old branch must lose its permission as soon as the choice changes", previous.allowed())
        assertTrue(f.speaker.stops > stopsBefore)
        assertEquals(1, f.speaker.passages.size)
        f.controller.setPresentationHeld(false)
        assertEquals(2, f.speaker.passages.size)
        assertTrue(f.speaker.passages.last().text.contains("finished gassed out"))
        previous.done()
        assertTrue(f.controller.state.value.active)
    }

    @Test fun stoppingDuringVoicePreparationDoesNotAllowLateSuccessToStartSpeech() = fixture { f ->
        val ready = CompletableDeferred<Boolean>()
        f.prepare = { _, _ -> withContext(NonCancellable) { ready.await() } }
        f.controller.finishRun()
        f.controller.stop()
        ready.complete(true)
        assertTrue(f.speaker.facts.isEmpty())
        assertTrue(f.speaker.passages.isEmpty())
        assertFalse(f.controller.state.value.active)
    }

    @Test fun closeInvalidatesCallbacksAndMakesLaterCommandsInert() {
        val f = Fixture()
        f.controller.finishRun()
        f.speaker.factsCallbacks.single().invoke()
        f.controller.selectEnergy(ReflectionBranch.NO_SELECTION)
        val stale = f.speaker.passages.single()
        f.controller.close()
        f.controller.close()
        stale.done()
        f.controller.prepare()
        f.controller.retry()
        f.controller.startMusic()
        f.controller.finishRun()
        f.controller.selectEnergy(ReflectionBranch.POWERED_UP)
        f.controller.setPresentationHeld(false)
        assertFalse(stale.allowed())
        assertFalse(f.controller.state.value.active)
        assertEquals(1, f.speaker.passages.size)
        assertEquals(1, f.speaker.closes)
        assertEquals(1, f.transport.closes)
        assertEquals(0, f.transport.openings)
    }

    @Test fun finishWaitsForCancelledOpeningToReleaseItsMusicLeaseBeforePreparingFacts() = fixture { f ->
        val releaseLease = CompletableDeferred<Unit>()
        var cleanupStarted = false
        var cleanupDone = false
        f.transport.opening = {
            try { awaitCancellation() }
            finally {
                withContext(NonCancellable) {
                    cleanupStarted = true
                    releaseLease.await()
                    cleanupDone = true
                }
            }
        }
        f.controller.startMusic()
        f.controller.finishRun()
        try {
            assertTrue(cleanupStarted)
            assertFalse(cleanupDone)
            assertTrue(f.preparations.isEmpty())
            assertTrue(f.speaker.facts.isEmpty())
        } finally { releaseLease.complete(Unit) }
        assertTrue(cleanupDone)
        assertEquals(listOf(MusicReflectionDemoData.facts to true), f.preparations)
        assertEquals(listOf(MusicReflectionDemoData.facts), f.speaker.facts)
    }

    @Test fun failedVoiceCanRetryAfterReadinessReturns() = fixture { f ->
        f.prepare = { _, _ -> false }
        f.controller.finishRun()
        assertNotNull(f.controller.state.value.error)
        f.prepare = { _, _ -> true }
        f.controller.retry()
        assertNull(f.controller.state.value.error)
        assertEquals(1, f.speaker.facts.size)
        assertTrue(f.controller.state.value.active)
    }

    @Test fun unavailableOpeningShowsConnectionErrorAndDoesNotPretendToPlay() = fixture { f ->
        f.transport.opening = { SongPassageStatus.UNAVAILABLE }
        f.controller.startMusic()
        assertEquals("Connect Spotify in Run settings, then try again.", f.controller.state.value.error)
        assertTrue(f.speaker.facts.isEmpty())
        assertTrue(f.speaker.passages.isEmpty())
        assertFalse(f.controller.state.value.active)
    }
}
