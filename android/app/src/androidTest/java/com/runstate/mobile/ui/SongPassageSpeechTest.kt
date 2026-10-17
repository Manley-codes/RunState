package com.runstate.mobile.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.runstate.mobile.music.SongPassageRequest
import com.runstate.mobile.music.AndroidSpotifyPassageSession
import com.runstate.mobile.music.PassagePlayback
import com.runstate.mobile.data.local.*
import com.runstate.mobile.reflection.LocalReflectionHttpClient
import com.runstate.mobile.reflection.AndroidReflectionConnectionStore
import com.runstate.mobile.reflection.CompletedRunEvidence
import com.runstate.mobile.reflection.HostedReflectionHttpClient
import com.runstate.mobile.reflection.PHONE_REFLECTION_ENDPOINT
import com.runstate.mobile.reflection.ReflectionApiRequest
import com.runstate.mobile.reflection.ReflectionMetricsEvidence
import com.runstate.mobile.reflection.ReflectionMusicContext
import com.runstate.mobile.reflection.pairedPassageFor
import com.runstate.mobile.reflection.reflectionRequestFromSavedRun
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Silent ownership checks, plus one explicitly opted-in real audio check; no Room writes. */
@RunWith(AndroidJUnit4::class)
class SongPassageSpeechTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val passage = SongPassageRequest("Selected title", "Selected artist", 5_000L)

    /** Explicit silent hosted smoke check; synthetic facts only, no saved-run or preference writes. */
    @Test fun hostedSampleReflectionUsesSavedConnection() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("allowHostedCheck") == "yes")
        try {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val connection = runBlocking { AndroidReflectionConnectionStore(context).load() }
            assertNotNull("A saved hosted reflection connection is required for this optional check.", connection)
            val finish = System.currentTimeMillis()
            val request = ReflectionApiRequest(
                requestId = UUID.randomUUID().toString(),
                runId = UUID.randomUUID().toString(),
                evidence = CompletedRunEvidence(
                    officialStartEpochMillis = finish - 1_920_000L,
                    finishEpochMillis = finish,
                    startTimezoneId = "America/Chicago",
                    activeDurationMillis = 1_920_000L,
                    metrics = ReflectionMetricsEvidence(3 * 1_609.344, "FIXTURE", "COMPLETE", "MILES"),
                    preRunEnergy = "MODERATE",
                    musicContext = ReflectionMusicContext(
                        state = "MUSIC", trackTitle = "Lovely Day", artistName = "Bill Withers",
                        provenance = "CONTROLLED"
                    )
                )
            )
            // The real client's response parser rejects mismatched requestId/runId before returning.
            // A successful result therefore also verifies this fresh request's exact identifiers.
            val candidates = runBlocking { HostedReflectionHttpClient(requireNotNull(connection)).request(request) }
            assertFalse("The hosted smoke check requires real provider generation.", candidates.isTestData)
            assertTrue("All four hosted reflection candidates must be nonblank.",
                listOf(candidates.spent, candidates.feelingGood, candidates.poweredUp, candidates.noSelection)
                    .all { it.isNotBlank() })
            assertTrue("This sample must not acquire an unrequested curated song cue.", candidates.songCueId == null)
            // This proves authenticated hosted generation, not receipt of optional song enrichment.
        } catch (_: Exception) {
            // Never attach private connection details, provider diagnostics or generated text to failures.
            throw AssertionError("The optional silent hosted reflection check could not complete.")
        }
    }

    /** Explicit opt-in only: one live sample request, real TTS and automatic Spotify transport. */
    @Test fun realSampleReflectionAutomaticallyHandsOffToSpotify() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("allowRealAudio") == "yes")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val session = AndroidSpotifyPassageSession(context)
        val original = session.snapshot()
        assertNotNull("Queue Sunday Best by Surfaces in Spotify before this optional check.", original)
        requireNotNull(original)
        assertTrue("Queue the original Sunday Best by Surfaces before this optional check.",
            original.title.equals("Sunday Best", true) && original.artist.equals("Surfaces", true))
        assertEquals("Pause the song for this controlled automatic check.", PassagePlayback.PAUSED, original.playback)
        val finish = System.currentTimeMillis()
        val runId = UUID.randomUUID().toString()
        val run = RunEntity(runId = runId, state = StoredRunState.COMPLETED,
            officialStartEpochMillis = finish - 60_000L, startTimezoneId = "America/Chicago",
            lastCheckpointEpochMillis = finish, finishEpochMillis = finish,
            finalDistanceMeters = 80.4672, metricSource = MetricSource.FIXTURE,
            telemetryCoverage = TelemetryCoverage.COMPLETE, displayDistanceUnit = DistanceUnit.MILES,
            transitionHistoryComplete = true, musicObservationRequested = true,
            musicReflectionSharingAllowed = true)
        // Controlled sample evidence, never inserted into the runner's production database.
        val report = RunMusicObservationEntity(runId, 1, "controlled-audio-check", finish, null,
            "com.spotify.music", original.title, original.artist, MusicPlaybackState.PLAYING,
            MusicObservationKind.SESSION_SNAPSHOT)
        val transitions = listOf(RunTransitionEntity(runId, 1, RunTransitionType.PAUSE, finish))
        val request = reflectionRequestFromSavedRun(UUID.randomUUID().toString(), run, transitions, listOf(report))
        val candidates = runBlocking { LocalReflectionHttpClient(PHONE_REFLECTION_ENDPOINT).request(request) }
        assertFalse("This explicit listening check requires the live local provider.", candidates.isTestData)
        assertEquals(request.evidence.musicContext.songCue?.cueId, candidates.songCueId)
        val branch = if (InstrumentationRegistry.getArguments().getString("sampleEnergy") == "FEELING_GOOD")
            ReflectionBranch.FEELING_GOOD else ReflectionBranch.NO_SELECTION
        val reflection = if (branch == ReflectionBranch.FEELING_GOOD) candidates.feelingGood else candidates.noSelection
        // Explicit audio test only: expose this fictional sample's text for correlation review.
        InstrumentationRegistry.getInstrumentation().sendStatus(2, android.os.Bundle().apply {
            putString("runstate_sample_reflection", reflection)
        })
        val record = RunReflectionEntity(runId, request.requestId, Gson().toJson(request.evidence), finish,
            status = ReflectionStatus.READY, selectedBranch = branch,
            text = reflection, finalizedAtEpochMillis = finish, isTestData = false)
        val selected = requireNotNull(pairedPassageFor(record, candidates.songCueId))
        val finished = CountDownLatch(1)
        val played = AtomicBoolean(false)
        val failed = AtomicBoolean(false)
        var speaker: AndroidReflectionSpeaker? = null
        try {
            composeTestRule.runOnUiThread {
                val android = AndroidReflectionSpeechPlatform(context)
                val witnessed = object : ReflectionSpeechPlatform by android {
                    override fun playSongPassage(id: String, passage: SongPassageRequest,
                        onDone: (String) -> Unit, onError: (String) -> Unit,
                        onFocusLost: (String) -> Unit): Boolean = android.playSongPassage(id, passage,
                        { played.set(true); onDone(it) },
                        { failed.set(true); onError(it) },
                        { failed.set(true); onFocusLost(it) })
                }
                val owned = AndroidReflectionSpeaker(context, witnessed)
                speaker = owned
                owned.speakReceipt("Automatic sample check. Your reflection will play, followed by its song passage.") {
                    owned.speakWithSongPassage(reflection, selected, onFinished = { finished.countDown() })
                }
            }
            assertTrue("The automatic voice and song sequence did not finish.", finished.await(90, TimeUnit.SECONDS))
            assertTrue("Spotify did not confirm a completed automatic passage.", played.get())
            assertFalse("The automatic passage reported an error.", failed.get())
            val restored = session.snapshot()
            assertEquals(PassagePlayback.PAUSED, restored?.playback)
            assertTrue("The original paused position was not restored.", restored != null &&
                kotlin.math.abs(restored.positionMillis - original.positionMillis) <= 2_000L)
        } finally {
            composeTestRule.runOnUiThread { speaker?.close() }
        }
    }

    private class ControlledPlatform : ReflectionSpeechPlatform {
        data class Spoken(val id: String, val text: String, val rate: Float)
        data class Passage(
            val id: String,
            val request: SongPassageRequest,
            val done: (String) -> Unit,
            val failed: (String) -> Unit,
            val focusLost: (String) -> Unit
        )

        lateinit var ready: (Boolean) -> Unit
        lateinit var voiceDone: (String) -> Unit
        lateinit var voiceFailed: (String) -> Unit
        val focusLosses = mutableMapOf<String, (String) -> Unit>()
        val spoken = mutableListOf<Spoken>()
        val passages = mutableListOf<Passage>()
        val events = mutableListOf<String>()
        var focusGranted = true
        var speechAccepted = true
        var passageAccepted = true
        var loseFocusDuringPassage = false
        var activePassage: String? = null
        var passageStops = 0
        var instrumentalStarts = 0
        var closes = 0

        override fun initialize(
            onReady: (Boolean) -> Unit,
            onDone: (String) -> Unit,
            onError: (String) -> Unit
        ) {
            ready = onReady
            voiceDone = onDone
            voiceFailed = onError
        }

        override fun requestAudioFocus(utteranceId: String, onFocusLost: (String) -> Unit): Boolean {
            focusLosses[utteranceId] = onFocusLost
            events += "voice-focus"
            return focusGranted
        }

        override fun speak(utteranceId: String, text: String, rate: Float): Boolean {
            spoken += Spoken(utteranceId, text, rate)
            events += "voice"
            return speechAccepted
        }

        override fun playSongPassage(
            utteranceId: String,
            passage: SongPassageRequest,
            onDone: (String) -> Unit,
            onError: (String) -> Unit,
            onFocusLost: (String) -> Unit
        ): Boolean {
            passages += Passage(utteranceId, passage, onDone, onError, onFocusLost)
            events += "passage"
            if (passageAccepted) activePassage = utteranceId
            if (loseFocusDuringPassage) onFocusLost(utteranceId)
            return passageAccepted
        }

        override fun stopSongPassage() {
            if (activePassage != null) passageStops++
            activePassage = null
        }

        override fun playInstrumental(
            utteranceId: String,
            onDone: (String) -> Unit,
            onError: (String) -> Unit,
            onFocusLost: (String) -> Unit
        ): Boolean {
            instrumentalStarts++
            return false
        }

        override fun stop() = Unit
        override fun releaseAudioFocus() { events += "release-focus" }
        override fun close() { closes++ }
    }

    private fun speaker(
        platform: ControlledPlatform,
        deadlines: MutableList<Runnable> = mutableListOf()
    ) = AndroidReflectionSpeaker(
        composeTestRule.activity,
        platform,
        scheduleTimeout = { task, _ -> deadlines += task },
        cancelTimeout = {}
    )

    @Test
    fun exactSpeechCompletesBeforeSelectedPassageAndFinishesOnlyOnce() {
        composeTestRule.runOnUiThread {
            val platform = ControlledPlatform()
            val deadlines = mutableListOf<Runnable>()
            val speaker = speaker(platform, deadlines)
            var finished = 0
            val exact = "  Exact saved reflection.\nSecond line.  "
            try {
                speaker.speakWithSongPassage(exact, passage, onFinished = { finished++ })
                assertTrue(platform.spoken.isEmpty())
                assertTrue(platform.passages.isEmpty())
                platform.ready(true)
                val voice = platform.spoken.single()
                assertEquals(exact, voice.text)
                assertTrue(platform.passages.isEmpty())

                platform.voiceDone(voice.id)
                val played = platform.passages.single()
                assertEquals(passage, played.request)
                assertEquals(0, finished)
                assertTrue(platform.events.indexOf("voice") < platform.events.indexOf("passage"))
                assertEquals("release-focus", platform.events[platform.events.indexOf("passage") - 1])

                // TTS may deliver old completion/error/focus callbacks after handing off.
                platform.voiceDone(voice.id)
                platform.voiceFailed(voice.id)
                platform.focusLosses.getValue(voice.id)(voice.id)
                assertEquals(1, platform.passages.size)
                assertEquals(0, finished)
                played.done(played.id)
                played.done(played.id)
                played.failed(played.id)
                played.focusLost(played.id)
                deadlines.last().run()
                assertEquals(1, finished)
                assertEquals(null, platform.activePassage)
                assertEquals(0, platform.instrumentalStarts)
            } finally {
                speaker.close()
            }
        }
    }

    @Test
    fun stoppedOrFailedVoiceNeverStartsSongPassage() {
        composeTestRule.runOnUiThread {
            listOf("stop", "close", "voice error", "focus loss", "focus denial",
                "speech rejected", "startup timeout", "speech timeout", "engine failed").forEach { failure ->
                val platform = ControlledPlatform()
                val deadlines = mutableListOf<Runnable>()
                val speaker = speaker(platform, deadlines)
                var finished = 0
                try {
                    if (failure != "startup timeout" && failure != "engine failed") platform.ready(true)
                    platform.focusGranted = failure != "focus denial"
                    platform.speechAccepted = failure != "speech rejected"
                    speaker.speakWithSongPassage("Saved reflection.", passage, onFinished = { finished++ })
                    when (failure) {
                        "stop" -> speaker.stop()
                        "close" -> speaker.close()
                        "voice error" -> platform.voiceFailed(platform.spoken.single().id)
                        "focus loss" -> platform.spoken.single().id.let { platform.focusLosses.getValue(it)(it) }
                        "startup timeout", "speech timeout" -> deadlines.last().run()
                        "engine failed" -> platform.ready(false)
                    }
                    platform.spoken.lastOrNull()?.let { platform.voiceDone(it.id); platform.voiceFailed(it.id) }
                    platform.ready(true)
                    assertEquals(failure, 1, finished)
                    assertTrue(failure, platform.passages.isEmpty())
                    assertEquals(failure, 0, platform.instrumentalStarts)
                } finally {
                    speaker.close()
                }
            }
        }
    }

    @Test
    fun stopOrCloseDuringPassageCancelsOwnedPlaybackAndConsumesLateCallbacks() {
        composeTestRule.runOnUiThread {
            listOf(false, true).forEach { close ->
                val platform = ControlledPlatform()
                val speaker = speaker(platform)
                var finished = 0
                try {
                    platform.ready(true)
                    speaker.speakWithSongPassage("Saved reflection.", passage, onFinished = { finished++ })
                    platform.voiceDone(platform.spoken.single().id)
                    val played = platform.passages.single()
                    assertEquals(played.id, platform.activePassage)
                    if (close) speaker.close() else speaker.stop()
                    assertEquals(null, platform.activePassage)
                    assertEquals(1, platform.passageStops)
                    played.done(played.id)
                    played.failed(played.id)
                    played.focusLost(played.id)
                    platform.voiceDone(platform.spoken.single().id)
                    assertEquals(1, finished)
                    assertEquals(1, platform.passages.size)
                    assertEquals(0, platform.instrumentalStarts)
                } finally {
                    speaker.close()
                }
                assertEquals(1, platform.closes)
            }
        }
    }

    @Test
    fun obsoleteAndForeignVoiceOrPassageCallbacksCannotFinishTheNextRequest() {
        composeTestRule.runOnUiThread {
            val platform = ControlledPlatform()
            val speaker = speaker(platform)
            var firstFinished = 0
            var secondFinished = 0
            try {
                platform.ready(true)
                speaker.speakWithSongPassage("First.", passage, onFinished = { firstFinished++ })
                val firstVoice = platform.spoken.single().id
                platform.voiceDone(firstVoice)
                val firstPassage = platform.passages.single()
                val nextPassage = passage.copy(startPositionMillis = 8_000L)
                speaker.speakWithSongPassage("Second.", nextPassage, onFinished = { secondFinished++ })
                val secondVoice = platform.spoken.last().id
                assertEquals(1, firstFinished)

                platform.voiceDone(firstVoice)
                platform.voiceFailed(firstVoice)
                platform.voiceDone("foreign-request")
                platform.voiceFailed("foreign-request")
                firstPassage.done(firstPassage.id)
                firstPassage.failed(firstPassage.id)
                firstPassage.focusLost(firstPassage.id)
                assertEquals(0, secondFinished)
                assertEquals(1, platform.passages.size)

                platform.voiceDone(secondVoice)
                val secondPassage = platform.passages.last()
                assertEquals(nextPassage, secondPassage.request)
                firstPassage.done(firstPassage.id)
                firstPassage.failed(firstPassage.id)
                firstPassage.focusLost(firstPassage.id)
                secondPassage.done("foreign-request")
                secondPassage.failed("foreign-request")
                secondPassage.focusLost("foreign-request")
                assertEquals(0, secondFinished)
                assertEquals(secondPassage.id, platform.activePassage)
                secondPassage.done(secondPassage.id)
                assertEquals(1, secondFinished)
                assertEquals(1, firstFinished)
                assertEquals(0, platform.instrumentalStarts)
            } finally {
                speaker.close()
            }
        }
    }

    @Test
    fun unavailableFailedOrTimedOutPassageNeverFallsBackToBundledInstrumental() {
        composeTestRule.runOnUiThread {
            listOf("rejected", "error", "focus loss", "synchronous focus loss", "timeout").forEach { failure ->
                val platform = ControlledPlatform().apply {
                    passageAccepted = failure != "rejected"
                    loseFocusDuringPassage = failure == "synchronous focus loss"
                }
                val deadlines = mutableListOf<Runnable>()
                val speaker = speaker(platform, deadlines)
                var finished = 0
                try {
                    platform.ready(true)
                    speaker.speakWithSongPassage("Saved reflection.", passage, onFinished = { finished++ })
                    platform.voiceDone(platform.spoken.single().id)
                    val played = platform.passages.single()
                    when (failure) {
                        "error" -> played.failed(played.id)
                        "focus loss" -> played.focusLost(played.id)
                        "timeout" -> deadlines.last().run()
                    }
                    played.done(played.id)
                    played.failed(played.id)
                    played.focusLost(played.id)
                    assertEquals(failure, 1, finished)
                    assertEquals(failure, null, platform.activePassage)
                    assertEquals(failure, 1, platform.passages.size)
                    assertEquals(failure, 0, platform.instrumentalStarts)
                } finally {
                    speaker.close()
                }
            }
        }
    }

    @Test
    fun passageEligibilityIsCheckedAgainAfterSpeechCompletes() {
        composeTestRule.runOnUiThread {
            val platform = ControlledPlatform()
            val speaker = speaker(platform)
            var eligible = true
            var finished = 0
            try {
                platform.ready(true)
                speaker.speakWithSongPassage("Saved reflection.", passage, { eligible }, { finished++ })
                eligible = false
                platform.voiceDone(platform.spoken.single().id)
                assertEquals(1, finished)
                assertTrue(platform.passages.isEmpty())
                assertEquals(0, platform.instrumentalStarts)
            } finally {
                speaker.close()
            }
        }
    }
}
