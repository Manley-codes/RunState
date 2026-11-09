package com.runstate.mobile

import com.runstate.mobile.reflection.RunReflectionState
import com.runstate.mobile.reflection.ReflectionVoicePreparation
import com.runstate.mobile.reflection.CompletedRunEvidence
import com.runstate.mobile.reflection.ReflectionMetricsEvidence
import com.runstate.mobile.reflection.ReflectionMusicContext
import com.runstate.mobile.reflection.ReflectionSongCue
import com.runstate.mobile.reflection.pairedPassageFor
import com.runstate.mobile.reflection.ReflectionSongCandidate
import com.runstate.mobile.music.SongReflectionPlan
import com.runstate.mobile.music.SongPassageSnapshot
import com.runstate.mobile.music.PassagePlayback
import com.runstate.mobile.ui.CompletionAudioSession
import com.runstate.mobile.ui.CompletionAudioSession.Event as CompletionEvent
import com.runstate.mobile.data.local.RunReflectionEntity
import com.runstate.mobile.data.local.ReflectionStatus
import com.runstate.mobile.data.local.ReflectionBranch
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RunCompleteSpeechTest {
    private val runId = "0f6a2c1e-9d43-4b7a-9c21-7b5e8a4d1f30"
    private val otherRunId = "9b3d7a5c-2e41-4f68-8a90-1c2d3e4f5a6b"

    private class CompletionAudioFixture {
        var current: SongPassageSnapshot? = SongPassageSnapshot(
            sessionKey = "spotify-session", sourcePackage = "com.spotify.music",
            mediaId = "spotify:track:fictional-original", title = "Fictional original",
            artist = "Fictional artist", durationMillis = 180_000L, positionMillis = 30_000L,
            playback = PassagePlayback.PLAYING, canPause = true, canPlay = true, canSeek = true)
        var snapshotReads = 0
        var pauseRequests = 0
        var releases = 0
        var interruptions = 0
        var loseDuringRequest = false
        var grantPause = true
        var onLoss: (() -> Unit)? = null
        val lostVoices = mutableListOf<String>()
        val events = mutableListOf<CompletionEvent>()
        val session = CompletionAudioSession(
            snapshot = { snapshotReads++; current },
            requestPause = { lost ->
                pauseRequests++
                onLoss = lost
                if (loseDuringRequest) lost()
                grantPause
            },
            releasePause = { releases++ },
            onInterrupted = { interruptions++ },
            diagnostic = { event, _ -> events += event })
    }

    @Test fun completionPauseIsSharedAcrossFactsIdleGapAndReflection() {
        val fixture = CompletionAudioFixture()
        val facts = Any()
        val reflection = Any()
        assertTrue(fixture.session.begin())
        assertTrue(fixture.session.acquireVoice(facts, "facts", fixture.lostVoices::add))
        fixture.current = fixture.current!!.copy(playback = PassagePlayback.PAUSED)
        fixture.session.releaseVoice(facts)
        assertEquals("The preparation gap must keep the same pause", 0, fixture.releases)
        assertTrue(fixture.session.acquireVoice(reflection, "reflection", fixture.lostVoices::add))
        fixture.session.releaseVoice(reflection)
        assertEquals(1, fixture.pauseRequests)
        assertEquals("The playing baseline must not be replaced by the paused second voice", 1, fixture.snapshotReads)
        assertEquals(0, fixture.releases)
        fixture.session.finish()
        fixture.session.finish()
        fixture.session.cancel()
        assertEquals(1, fixture.releases)
        assertEquals(0, fixture.interruptions)
        assertTrue(fixture.lostVoices.isEmpty())
    }

    @Test fun cancelledCompletionCannotRestartOrAcquireAnotherVoice() {
        val fixture = CompletionAudioFixture()
        assertTrue(fixture.session.acquireVoice(Any(), "facts", fixture.lostVoices::add))
        fixture.session.cancel()
        fixture.session.cancel()
        fixture.session.finish()
        assertFalse(fixture.session.begin())
        assertFalse(fixture.session.acquireVoice(Any(), "late reflection", fixture.lostVoices::add))
        fixture.current = fixture.current!!.copy(playback = PassagePlayback.PAUSED)
        assertFalse(fixture.session.resumePausedOriginal())
        fixture.onLoss!!.invoke()
        assertEquals(listOf("facts"), fixture.lostVoices)
        assertEquals(1, fixture.pauseRequests)
        assertEquals(1, fixture.releases)
        assertEquals(0, fixture.interruptions)
    }

    @Test fun completionFocusLossStopsActiveVoicesAndAlsoCancelsAnIdleGap() {
        for (idle in listOf(false, true)) {
            val fixture = CompletionAudioFixture()
            val owner = Any()
            assertTrue(fixture.session.acquireVoice(owner, "facts", fixture.lostVoices::add))
            if (idle) fixture.session.releaseVoice(owner)
            fixture.onLoss!!.invoke()
            fixture.onLoss!!.invoke()
            fixture.session.finish()
            fixture.session.cancel()
            assertEquals(1, fixture.interruptions)
            assertEquals(1, fixture.releases)
            assertEquals(if (idle) emptyList<String>() else listOf("facts"), fixture.lostVoices)
            assertFalse(fixture.session.acquireVoice(Any(), "late reflection", fixture.lostVoices::add))
            assertEquals(1, fixture.pauseRequests)
        }
    }

    @Test fun queuedFocusLossAfterPassageHandoffCannotCancelTheSong() {
        val fixture = CompletionAudioFixture()
        assertTrue(fixture.session.acquireVoice(Any(), "reflection", fixture.lostVoices::add))
        val oldLoss = requireNotNull(fixture.onLoss)
        fixture.session.releaseForPassage()
        fixture.session.releaseForPassage()
        oldLoss()
        assertEquals(1, fixture.releases)
        assertEquals(0, fixture.interruptions)
        assertTrue(fixture.lostVoices.isEmpty())
        assertFalse(fixture.session.begin())
        fixture.session.finish()
        fixture.session.cancel()
        oldLoss()
        assertEquals(1, fixture.releases)
        assertEquals(0, fixture.interruptions)
    }

    @Test fun anAudibleSongLoadTreatsItsOwnFocusLossAsTheHandoffAndKeepsTheLeaseUntilTheEntrance() {
        val fixture = CompletionAudioFixture()
        val reflection = Any()
        assertTrue(fixture.session.acquireVoice(reflection, "reflection", fixture.lostVoices::add))
        fixture.session.releaseVoice(reflection)
        fixture.session.expectPassageLoad()
        fixture.onLoss!!.invoke() // The chosen song's player takes focus as it starts loading.
        assertEquals(0, fixture.interruptions)
        assertEquals("The lease stays until the entrance", 0, fixture.releases)
        assertFalse(fixture.session.canContinue)
        assertFalse(fixture.session.acquireVoice(Any(), "late voice", fixture.lostVoices::add))
        fixture.session.releaseForPassage()
        fixture.session.releaseForPassage()
        fixture.session.finish()
        fixture.onLoss!!.invoke()
        assertEquals(1, fixture.releases)
        assertEquals(0, fixture.interruptions)
        assertTrue(fixture.lostVoices.isEmpty())
        assertEquals(listOf(CompletionEvent.PAUSE_HELD, CompletionEvent.PASSAGE_LOADING,
            CompletionEvent.FOCUS_TO_PASSAGE, CompletionEvent.PASSAGE_READY, CompletionEvent.FINISHED),
            fixture.events)
    }

    @Test fun leavingDuringAnAudibleSongLoadReleasesTheLeaseOnceWithoutAnotherInterruption() {
        val fixture = CompletionAudioFixture()
        assertTrue(fixture.session.begin())
        fixture.session.expectPassageLoad()
        fixture.session.cancel()
        fixture.session.releaseForPassage()
        fixture.onLoss!!.invoke()
        assertEquals(1, fixture.releases)
        assertEquals("Stopping the passage itself stays with the passage owner", 0, fixture.interruptions)
        assertEquals(listOf(CompletionEvent.PAUSE_HELD, CompletionEvent.PASSAGE_LOADING,
            CompletionEvent.CANCELLED), fixture.events)
    }

    @Test fun audibleLoadHandoffAppliesOnlyWhileTheCompletionHoldsThePause() {
        val unstarted = CompletionAudioFixture()
        unstarted.session.expectPassageLoad()
        assertTrue("An early call cannot skip the shared pause", unstarted.session.begin())
        assertEquals(listOf(CompletionEvent.PAUSE_HELD), unstarted.events)

        val interrupted = CompletionAudioFixture()
        assertTrue(interrupted.session.begin())
        interrupted.onLoss!!.invoke()
        interrupted.session.expectPassageLoad()
        interrupted.session.releaseForPassage()
        assertEquals(1, interrupted.interruptions)
        assertEquals(1, interrupted.releases)
        assertEquals(listOf(CompletionEvent.PAUSE_HELD, CompletionEvent.FOCUS_LOST,
            CompletionEvent.CANCELLED), interrupted.events)
    }

    @Test fun takingThePauseBackAfterAnAudibleLoadLetsAnOutsideLossInterruptUntilTheEntrance() {
        val fixture = CompletionAudioFixture()
        assertTrue(fixture.session.begin())
        fixture.session.expectPassageLoad()
        fixture.onLoss!!.invoke() // The chosen song's player takes focus as it starts loading.
        fixture.session.reholdAfterPassageLoad()
        assertEquals(2, fixture.pauseRequests)
        assertFalse("No new voice can start during the passage", fixture.session.canContinue)
        fixture.onLoss!!.invoke() // Another app starts playing during the entrance seek.
        fixture.session.releaseForPassage()
        assertEquals(1, fixture.interruptions)
        assertEquals(1, fixture.releases)
        assertEquals(listOf(CompletionEvent.PAUSE_HELD, CompletionEvent.PASSAGE_LOADING,
            CompletionEvent.FOCUS_TO_PASSAGE, CompletionEvent.PAUSE_REHELD, CompletionEvent.FOCUS_LOST,
            CompletionEvent.CANCELLED), fixture.events)
    }

    @Test fun thePauseIsTakenBackOnlyAfterTheSongsOwnFocusChangeAndARefusalEndsTheCompletion() {
        val early = CompletionAudioFixture()
        assertTrue(early.session.begin())
        early.session.expectPassageLoad()
        early.session.reholdAfterPassageLoad()
        assertEquals("Without the song's own focus change, nothing is asked again", 1, early.pauseRequests)
        early.onLoss!!.invoke() // That change can still arrive late; it belongs to the passage.
        early.session.releaseForPassage()
        early.session.reholdAfterPassageLoad()
        assertEquals("Nothing is asked again after the entrance", 1, early.pauseRequests)
        assertEquals(0, early.interruptions)
        assertEquals(1, early.releases)

        val refused = CompletionAudioFixture()
        assertTrue(refused.session.begin())
        refused.session.expectPassageLoad()
        refused.onLoss!!.invoke()
        refused.grantPause = false // For example, a call is in progress.
        refused.session.reholdAfterPassageLoad()
        refused.session.releaseForPassage()
        assertEquals(1, refused.interruptions)
        assertEquals(1, refused.releases)
        assertEquals(listOf(CompletionEvent.PAUSE_HELD, CompletionEvent.PASSAGE_LOADING,
            CompletionEvent.FOCUS_TO_PASSAGE, CompletionEvent.CANCELLED), refused.events)
    }

    @Test fun onlyTheOriginalPlayingRecordingCanBeResumedAfterTheSharedPause() {
        val fixture = CompletionAudioFixture()
        val original = requireNotNull(fixture.current)
        assertTrue(fixture.session.acquireVoice(Any(), "facts", fixture.lostVoices::add))
        fixture.current = original.copy(playback = PassagePlayback.PAUSED)
        assertTrue(fixture.session.acquireVoice(Any(), "reflection", fixture.lostVoices::add))
        assertEquals(1, fixture.snapshotReads)
        assertTrue(fixture.session.resumePausedOriginal())
        val paused = requireNotNull(fixture.current)
        listOf(
            paused.copy(mediaId = "spotify:track:another-recording"),
            paused.copy(title = "Another title"),
            paused.copy(artist = "Another artist"),
            paused.copy(sessionKey = "replacement-session"),
            paused.copy(sourcePackage = "another.player"),
            paused.copy(durationMillis = original.durationMillis + 2L),
            paused.copy(playback = PassagePlayback.PLAYING)
        ).forEach { changed ->
            fixture.current = changed
            assertFalse("Never authorize resuming changed playback", fixture.session.resumePausedOriginal())
        }
        assertEquals(1, fixture.pauseRequests)
        fixture.session.finish()
        assertFalse(fixture.session.resumePausedOriginal())
    }

    @Test fun originallyPausedOrMissingPlaybackNeverBecomesAnAutomaticResume() {
        for (missing in listOf(false, true)) {
            val fixture = CompletionAudioFixture()
            val paused = requireNotNull(fixture.current).copy(playback = PassagePlayback.PAUSED)
            fixture.current = if (missing) null else paused
            assertTrue(fixture.session.begin())
            fixture.current = paused
            assertFalse(fixture.session.resumePausedOriginal())
            fixture.current = paused.copy(playback = PassagePlayback.PLAYING)
            assertFalse(fixture.session.resumePausedOriginal())
            fixture.session.finish()
            assertEquals(1, fixture.releases)
        }
    }

    @Test fun transportAdmissionMustMatchThePausedAndroidRecordingAndRespectAUserSeek() {
        val fixture = CompletionAudioFixture()
        assertTrue(fixture.session.begin())
        val original = requireNotNull(fixture.current)
        val paused = original.copy(playback = PassagePlayback.PAUSED)
        fixture.current = paused
        val sdkAdmission = paused.copy(sessionKey = "different-sdk-transport-session")
        assertFalse(fixture.session.resumePausedOriginal(
            sdkAdmission.copy(mediaId = "spotify:track:another-recording")))
        assertTrue("SDK and Android session keys differ for the same admitted recording",
            fixture.session.resumePausedOriginal(sdkAdmission))
        fixture.current = paused.copy(positionMillis = original.positionMillis + 5_000L)
        assertFalse("A user seek breaks ownership even if both transports show the original URI",
            fixture.session.resumePausedOriginal(sdkAdmission))
        fixture.session.finish()
        assertEquals(1, fixture.releases)
    }

    @Test fun synchronousFocusLossDuringPauseRequestIsTerminalAndReleasesOnlyOnce() {
        val fixture = CompletionAudioFixture().apply { loseDuringRequest = true }
        assertFalse(fixture.session.acquireVoice(Any(), "facts", fixture.lostVoices::add))
        assertEquals(1, fixture.interruptions)
        assertEquals(1, fixture.releases)
        assertTrue("The voice was never admitted", fixture.lostVoices.isEmpty())
        assertFalse(fixture.session.begin())
        fixture.session.cancel()
        fixture.session.finish()
        assertEquals(1, fixture.pauseRequests)
        assertEquals(1, fixture.releases)
    }

    @Test fun sameCanonicalRecordingCanResumeAcrossSubsecondTransportRoundingOnly() {
        val fixture = CompletionAudioFixture()
        val canonical = "spotify:track:0123456789ABCDEFGHIJKL"
        fixture.current = fixture.current!!.copy(mediaId = canonical, durationMillis = 180_845L)
        assertTrue(fixture.session.begin())
        val paused = fixture.current!!.copy(playback = PassagePlayback.PAUSED, durationMillis = 180_000L)
        fixture.current = paused
        val admitted = paused.copy(sessionKey = "sdk", durationMillis = 180_845L)
        assertTrue(fixture.session.resumePausedOriginal(admitted))
        assertFalse(fixture.session.resumePausedOriginal(admitted.copy(durationMillis = 181_000L)))
        assertFalse(fixture.session.resumePausedOriginal(
            admitted.copy(mediaId = "spotify:track:ABCDEFGHIJKL0123456789")))
        fixture.current = paused.copy(positionMillis = paused.positionMillis + 2_001L)
        assertFalse(fixture.session.resumePausedOriginal(admitted))
        fixture.session.finish()
    }

    @Test fun silentPreparationRequiresThisUnspokenNewCompletion() {
        val preparation = ReflectionVoicePreparation(runId, otherRunId, "Exact selected text")
        assertEquals(preparation, eligibleCompletionVoicePreparation(runId, runId, null, preparation))
        assertNull(eligibleCompletionVoicePreparation(runId, runId, runId, preparation))
        assertNull(eligibleCompletionVoicePreparation(runId, null, null, preparation))
        assertNull(eligibleCompletionVoicePreparation(otherRunId, otherRunId, null, preparation))
        assertNull(eligibleCompletionVoicePreparation(null, null, null, preparation))
        assertNull(eligibleCompletionVoicePreparation(runId, runId, null, null))
    }

    @Test
    fun newlyCompletedRunSpeaksItsExactReadyTextOnlyOnce() {
        val exact = "  First line.\nSecond line with a tab\tand emoji 🌤  "
        val ready = RunReflectionState.Ready(runId, exact)

        assertEquals(exact, readyCompletionSpeechText(runId, runId, null, ready))
        assertNull(readyCompletionSpeechText(runId, runId, runId, ready))
        assertNull(readyCompletionSpeechText(runId, null, null, ready))
    }

    @Test
    fun pendingFailedAndDifferentRunNeverSpeak() {
        assertNull(readyCompletionSpeechText(runId, runId, null,
            RunReflectionState.Preparing(runId)))
        assertNull(readyCompletionSpeechText(runId, runId, null,
            RunReflectionState.Failed(runId)))
        assertNull(readyCompletionSpeechText(runId, runId, null,
            RunReflectionState.Ready(otherRunId, "Other run")))
        assertNull(readyCompletionSpeechText(otherRunId, runId, null,
            RunReflectionState.Ready(runId, "Previous run")))
    }

    @Test fun songPassageRequiresAnExactServerAcknowledgementAndLiveReadyReflection() {
        val evidence = songEvidence()
        val record = readyRecord(Gson().toJson(evidence))
        assertNull(pairedPassageFor(record))
        assertNull(pairedPassageFor(record, "another_cue"))
        assertNull(pairedPassageFor(record.copy(isTestData = true), "checked_cue_v1"))
        assertEquals(evidence.songPassage?.passage(), pairedPassageFor(record, "checked_cue_v1"))
        assertNull(pairedPassageFor(record.copy(status = ReflectionStatus.PENDING,
            text = null, finalizedAtEpochMillis = null), "checked_cue_v1"))
    }

    @Test fun oldCorruptOrMismatchedSongPlansCannotLaunchAnUnrelatedPassage() {
        val evidence = songEvidence()
        val bad = listOf(
            "{bad json",
            Gson().toJson(evidence.copy(songPassage = null)),
            Gson().toJson(evidence.copy(musicContext = ReflectionMusicContext())),
            Gson().toJson(evidence.copy(songPassage = evidence.songPassage!!.copy(cueId = "wrong"))),
            Gson().toJson(evidence.copy(songPassage = evidence.songPassage!!.copy(trackTitle = "Other song"))),
            Gson().toJson(evidence).replace("10000", "999999")
        )
        for (json in bad) assertNull(pairedPassageFor(readyRecord(json), "checked_cue_v1"))
    }

    @Test fun theSongPausedForTheFinishNeverGetsAClipFromASavedPlan() {
        val evidence = songEvidence()
        fun candidate(title: String, loaded: Boolean) = ReflectionSongCandidate("1",
            ReflectionMusicContext("MUSIC", title, "Selected artist", provenance = "MEDIA_SESSION_PARTIAL"),
            loadedAtFinish = loaded)
        fun passageFor(candidates: List<ReflectionSongCandidate>) =
            pairedPassageFor(readyRecord(Gson().toJson(evidence.copy(songCandidates = candidates))), "checked_cue_v1")
        assertNull(passageFor(listOf(candidate(" selected  TITLE", true))))
        assertEquals(evidence.songPassage?.passage(), passageFor(listOf(candidate("Another title", true))))
        assertEquals(evidence.songPassage?.passage(), passageFor(listOf(candidate("Selected title", false))))
        // A record saved before the mark existed simply has no mark.
        val older = Gson().toJson(evidence.copy(songCandidates = listOf(candidate("Selected title", false))))
            .replace(",\"loadedAtFinish\":false", "")
        assertFalse(older.contains("loadedAtFinish"))
        assertEquals(evidence.songPassage?.passage(), pairedPassageFor(readyRecord(older), "checked_cue_v1"))
    }

    private fun songEvidence() = CompletedRunEvidence(
        1_000L, 61_000L, "America/Chicago", 60_000L,
        ReflectionMetricsEvidence(null, null, null, null),
        musicContext = ReflectionMusicContext("MUSIC", "Selected title", "Selected artist", null,
            "MEDIA_SESSION_PARTIAL", ReflectionSongCue("checked_cue_v1", "RUNSTATE_CURATED",
                "A checked theme", "A checked editorial connection")),
        songPassage = SongReflectionPlan("checked_cue_v1", "Selected title", "Selected artist", 0L, 10_000L)
    )

    private fun readyRecord(evidence: String) = RunReflectionEntity(
        runId, otherRunId, evidence, 70_000L,
        status = ReflectionStatus.READY, selectedBranch = ReflectionBranch.NO_SELECTION,
        text = "Exact saved reflection.", finalizedAtEpochMillis = 70_000L, isTestData = false
    )
}
