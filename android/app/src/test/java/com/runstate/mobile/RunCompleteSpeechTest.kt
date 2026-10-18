package com.runstate.mobile

import com.runstate.mobile.reflection.RunReflectionState
import com.runstate.mobile.reflection.ReflectionVoicePreparation
import com.runstate.mobile.reflection.CompletedRunEvidence
import com.runstate.mobile.reflection.ReflectionMetricsEvidence
import com.runstate.mobile.reflection.ReflectionMusicContext
import com.runstate.mobile.reflection.ReflectionSongCue
import com.runstate.mobile.reflection.pairedPassageFor
import com.runstate.mobile.music.SongReflectionPlan
import com.runstate.mobile.data.local.RunReflectionEntity
import com.runstate.mobile.data.local.ReflectionStatus
import com.runstate.mobile.data.local.ReflectionBranch
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RunCompleteSpeechTest {
    private val runId = "0f6a2c1e-9d43-4b7a-9c21-7b5e8a4d1f30"
    private val otherRunId = "9b3d7a5c-2e41-4f68-8a90-1c2d3e4f5a6b"

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
