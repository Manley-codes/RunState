package com.runstate.mobile.ui

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.runstate.mobile.music.SongPassageRequest
import com.runstate.mobile.music.SongPassageSession
import com.runstate.mobile.music.SongPassageSnapshot
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.android.asCoroutineDispatcher
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
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

    /** Visible, real-time listening check: production countdown UI/owner, no run/Room writes or Spotify transport. */
    @Test fun realRunVoiceCountdownAndPacedMileAudition() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("runstateRunVoiceCheck") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val lines = com.runstate.mobile.reflection.COUNTDOWN_VOICE_LINES
        val mile = "One mile completed. Your average pace is ten minutes per mile."
        // This is a real-time listening check; Compose's virtual clock must not drive the cues.
        val audioDispatcher = android.os.Handler(android.os.Looper.getMainLooper())
            .asCoroutineDispatcher("Run voice audition")
        // Fetch everything before speaking. This audition must never silently use the phone voice.
        val (cache, mileAudio) = runBlocking {
            withTimeout(75_000L) {
                // ComponentActivity does not initialize the app's saved connection. Read it
                // directly without changing settings, and keep prefetch off Compose's clock.
                val config = requireNotNull(AndroidReflectionConnectionStore(context).load()) {
                    "A saved hosted connection is required; no audition audio started."
                }
                val gateway = com.runstate.mobile.reflection.ReflectionSpeechHttpClient.hosted(
                    config, com.runstate.mobile.reflection.SpeechVoice.RUN)
                val prepared = com.runstate.mobile.reflection.RunVoiceCueCache(this, gateway, lines)
                var nextAttempt = 0L
                while (!prepared.hasAll(lines)) {
                    val now = android.os.SystemClock.elapsedRealtime()
                    if (now >= nextAttempt) {
                        prepared.prefetch()
                        nextAttempt = now + 5_000L
                    }
                    delay(100L)
                }
                prepared to gateway.synthesize(mile)
            }
        }
        assertTrue("Custom run voice was unavailable; no audition audio started.",
            mileAudio != null && mileAudio.isNotEmpty() &&
                mileAudio.size <= com.runstate.mobile.reflection.MAX_REFLECTION_AUDIO_BYTES)
        val finished = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>(null)
        val playbackFailed = AtomicBoolean(false)
        val scope = CoroutineScope(SupervisorJob() + audioDispatcher)
        var speaker: AndroidReflectionSpeaker? = null
        val completedCloudAudio = mutableSetOf<ByteArray>()
        var startedCloudLines = 0
        val owner = com.runstate.mobile.run.RunCountdownOwner()
        val token = owner.reserve()
        val renderedDigits = linkedSetOf<Int>()
        try {
            val displayed = renderRealtimeCountdown(owner, token, renderedDigits)
            composeTestRule.runOnUiThread {
                val android = AndroidReflectionSpeechPlatform(context)
                val witnessed = object : ReflectionSpeechPlatform by android {
                    override fun preparedRunVoiceAudio(text: String): ByteArray? =
                        if (text == mile) mileAudio else cache.audioFor(text)
                    override fun runVoiceGateway(): com.runstate.mobile.reflection.ReflectionSpeechGateway? = null
                    override fun speak(utteranceId: String, text: String, rate: Float): Boolean {
                        playbackFailed.set(true)
                        return false
                    }
                    override fun speakReceipt(utteranceId: String, text: String, rate: Float) =
                        speak(utteranceId, text, rate)
                    override fun playRunVoiceAudio(id: String, audio: ByteArray,
                        onDone: (String) -> Unit, onUnavailable: (String) -> Unit, onError: (String) -> Unit): Boolean {
                        startedCloudLines++
                        return android.playRunVoiceAudio(id, audio,
                            { completedCloudAudio += audio; onDone(it) },
                            { playbackFailed.set(true); onUnavailable(it) },
                            { playbackFailed.set(true); onError(it) })
                    }
                    override fun playMileAnnouncementAudio(id: String, audio: ByteArray,
                        onDone: (String) -> Unit, onUnavailable: (String) -> Unit, onError: (String) -> Unit): Boolean {
                        startedCloudLines++
                        return android.playMileAnnouncementAudio(id, audio,
                            { completedCloudAudio += audio; onDone(it) },
                            { playbackFailed.set(true); onUnavailable(it) },
                            { playbackFailed.set(true); onError(it) })
                    }
                }
                val voice = AndroidReflectionSpeaker(context, witnessed).also { speaker = it }
                scope.launch {
                    try {
                        suspend fun cue(text: String) {
                            val done = CompletableDeferred<Unit>()
                            voice.speakTimedCue(text, true) { done.complete(Unit) }
                            withTimeout(7_000L) { done.await() }
                            check(!playbackFailed.get()) { "The custom run voice could not play this check." }
                        }
                        var oneFinished: CompletableDeferred<Unit>? = null
                        var simulatedStarts = 0
                        val started = owner.execute(token,
                            intro = { cue(com.runstate.mobile.reflection.COUNTDOWN_INTRO_TEXT) },
                            cue = { step ->
                                if (step == 10) return@execute // Included in the uninterrupted opening.
                                val text = com.runstate.mobile.reflection.countdownCueText(step)
                                if (step == 1) {
                                    val done = CompletableDeferred<Unit>().also { oneFinished = it }
                                    voice.speakTimedCue(text, true) { done.complete(Unit) }
                                } else voice.speakTimedCue(text, true)
                            }, start = { simulatedStarts++ })
                        check(started) { "The visible countdown check was cancelled." }
                        // Same post-deadline ordering as production; the start above is only a counter.
                        oneFinished?.let { withTimeoutOrNull(2_000L) { it.await() } }
                        cue(com.runstate.mobile.reflection.COUNTDOWN_FINISH_TEXT)
                        delay(300L)
                        val mileFinished = CompletableDeferred<Unit>()
                        voice.speakMileAnnouncement(mile) { mileFinished.complete(Unit) }
                        withTimeout(30_000L) { mileFinished.await() }
                        val completionRequired = listOf(
                            cache.audioFor(com.runstate.mobile.reflection.COUNTDOWN_INTRO_TEXT),
                            cache.audioFor(com.runstate.mobile.reflection.countdownCueText(1)),
                            cache.audioFor(com.runstate.mobile.reflection.COUNTDOWN_FINISH_TEXT), mileAudio)
                        check(simulatedStarts == 1 && !playbackFailed.get() && startedCloudLines == 6 &&
                            completionRequired.all { it in completedCloudAudio }) {
                            "The audition did not complete every custom-voice line."
                        }
                    } catch (problem: Throwable) {
                        failure.set(problem)
                    } finally { finished.countDown() }
                }
            }
            awaitRealtimeCountdown(finished, owner, displayed)
            assertTrue("The run-voice listening check timed out.", finished.count == 0L)
            failure.get()?.let { throw AssertionError("The run-voice listening check did not finish cleanly.", it) }
            composeTestRule.runOnUiThread {
                assertEquals("The visible countdown did not render every number.",
                    (10 downTo 1).toList(), renderedDigits.toList())
            }
        } finally {
            composeTestRule.runOnUiThread { scope.cancel(); speaker?.close() }
            composeTestRule.mainClock.autoAdvance = true
        }
    }

    @Test fun visibleCountdownAuditionFollowsRealtimeOwnerWithoutAudio() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("runstateVisibleCountdownCheck") == "true")
        val dispatcher = android.os.Handler(android.os.Looper.getMainLooper()).asCoroutineDispatcher()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val owner = com.runstate.mobile.run.RunCountdownOwner()
        val token = owner.reserve()
        val rendered = linkedSetOf<Int>()
        val finished = CountDownLatch(1)
        var starts = 0
        try {
            val displayed = renderRealtimeCountdown(owner, token, rendered)
            scope.launch {
                try { owner.execute(token, intro = { delay(1_000L) }, cue = {}, start = { starts++ }) }
                finally { finished.countDown() }
            }
            awaitRealtimeCountdown(finished, owner, displayed)
            composeTestRule.runOnUiThread {
                assertEquals(1, starts)
                assertEquals((10 downTo 1).toList(), rendered.toList())
            }
        } finally {
            scope.cancel()
            composeTestRule.mainClock.autoAdvance = true
        }
    }

    private fun renderRealtimeCountdown(
        owner: com.runstate.mobile.run.RunCountdownOwner,
        token: Long,
        rendered: MutableSet<Int>
    ): MutableState<com.runstate.mobile.run.RunCountdownState> {
        val displayed = mutableStateOf(owner.state.value)
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.runOnUiThread { composeTestRule.activity.enableEdgeToEdge() }
        composeTestRule.setContent {
            val countdown = displayed.value
            SideEffect { countdown.digit?.let { rendered += it } }
            com.runstate.mobile.ui.theme.RunStateTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    CountdownScreen(
                        digit = countdown.digit,
                        actionsEnabled = countdown.canControl,
                        onStartNow = { owner.startNow(token) },
                        onCancel = { owner.cancel(token) },
                        transitioning = !countdown.active,
                        modifier = Modifier.padding(padding)
                    )
                }
            }
        }
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.onNodeWithTag(COUNTDOWN_SCREEN_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(COUNTDOWN_DIGIT_TAG).assertTextEquals("10").assertIsDisplayed()
        return displayed
    }

    private fun awaitRealtimeCountdown(
        finished: CountDownLatch,
        owner: com.runstate.mobile.run.RunCountdownOwner,
        displayed: MutableState<com.runstate.mobile.run.RunCountdownState>
    ) {
        val beganAt = android.os.SystemClock.elapsedRealtime()
        val beganFrame = composeTestRule.mainClock.currentTime
        while (finished.count > 0 && android.os.SystemClock.elapsedRealtime() - beganAt < 65_000L) {
            // The audition's audio/owner use a real Handler, outside the virtual Compose clock.
            // Bridge its current state and flush snapshots before advancing/drawing real-time frames.
            composeTestRule.runOnUiThread {
                displayed.value = owner.state.value
                Snapshot.sendApplyNotifications()
            }
            val elapsed = android.os.SystemClock.elapsedRealtime() - beganAt
            val remaining = elapsed - (composeTestRule.mainClock.currentTime - beganFrame)
            if (remaining >= 16L) composeTestRule.mainClock.advanceTimeBy(remaining)
            composeTestRule.waitForIdle()
            Thread.sleep(8L)
        }
        assertTrue("The visible countdown check timed out.", finished.count == 0L)
    }

    @Test fun replacementPassageWaitsForCancelledTransportCleanupWithoutTouchingSpotify() {
        val cleanupStarted = CountDownLatch(1)
        val secondFinished = CountDownLatch(1)
        val releaseGate = CompletableDeferred<Unit>()
        var factories = 0
        var platform: AndroidReflectionSpeechPlatform? = null
        var replacement: AndroidReflectionSpeechPlatform? = null
        try {
            composeTestRule.runOnUiThread {
                val factory: () -> SongPassageSession = {
                    val first = ++factories == 1
                    object : SongPassageSession {
                        override suspend fun prepareForPlayback() { if (first) awaitCancellation() }
                        override fun snapshot(): SongPassageSnapshot? = null
                        override fun pause() = Unit
                        override fun play() = Unit
                        override fun seekTo(positionMillis: Long) = Unit
                        override suspend fun release() {
                            if (first) { cleanupStarted.countDown(); releaseGate.await() }
                        }
                    }
                }
                platform = AndroidReflectionSpeechPlatform(composeTestRule.activity, passageSessionFactory = factory)
                requireNotNull(platform).playSongPassage("first", passage, {}, {}, {})
                requireNotNull(platform).close()
                replacement = AndroidReflectionSpeechPlatform(composeTestRule.activity, passageSessionFactory = factory)
                requireNotNull(replacement).playSongPassage("second", passage, {}, { secondFinished.countDown() }, {})
                assertEquals("The replacement cannot acquire a transport while old cleanup runs.", 1, factories)
            }
            assertTrue(cleanupStarted.await(5, TimeUnit.SECONDS))
            releaseGate.complete(Unit)
            assertTrue(secondFinished.await(5, TimeUnit.SECONDS))
            composeTestRule.runOnUiThread { assertEquals(2, factories) }
        } finally {
            releaseGate.complete(Unit)
            composeTestRule.runOnUiThread { platform?.close(); replacement?.close() }
        }
    }

    @Test fun plainReflectionAndFactsDoNotPrepareSpotifyConnection() {
        composeTestRule.runOnUiThread {
            val platform = ControlledPlatform()
            val speaker = speaker(platform)
            try {
                speaker.prepare("Exact reflection.")
                speaker.speakReceipt("Run facts.") {}
                speaker.speak("Exact reflection.")
                assertEquals(0, platform.connectionPreparations)
            } finally { speaker.close() }
        }
    }

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
        var connectionPreparations = 0

        override fun prepareSongPassageConnection() { connectionPreparations++ }

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
        deadlines: MutableList<Runnable> = mutableListOf(),
        delays: MutableList<Long> = mutableListOf(),
        cancelled: MutableList<Runnable> = mutableListOf()
    ) = AndroidReflectionSpeaker(
        composeTestRule.activity,
        platform,
        scheduleTimeout = { task, delay -> deadlines += task; delays += delay },
        cancelTimeout = { task -> cancelled += task }
    )

    @Test
    fun completionFactsSlowOnlyTheirLocalFallback() {
        composeTestRule.runOnUiThread {
            val platform = ControlledPlatform()
            val speaker = speaker(platform)
            try {
                platform.ready(true)
                speaker.speakCompletionFacts("Your saved run facts.") { }
                assertEquals(0.864f, platform.spoken.last().rate, 0.0001f)
                speaker.speakMileAnnouncement("One mile completed.") { }
                assertEquals(0.864f, platform.spoken.last().rate, 0.0001f)
                speaker.speakReceipt("Choose your Energy when you are ready.") { }
                assertEquals(0.96f, platform.spoken.last().rate, 0.0001f)
                speaker.speakTimedCue("Three", useRunVoice = false)
                assertEquals(0.96f, platform.spoken.last().rate, 0.0001f)
                speaker.speak("Your exact saved reflection.")
                assertEquals(0.94f, platform.spoken.last().rate, 0.0001f)
                assertEquals(listOf("Your saved run facts.", "One mile completed.", "Choose your Energy when you are ready.",
                    "Three", "Your exact saved reflection."), platform.spoken.map { it.text })
            } finally { speaker.close() }
        }
    }

    @Test
    fun completionFactsUseTheirOwnCloudPlaybackWithoutChangingOtherRunLines() {
        composeTestRule.runOnUiThread {
            val platform = ControlledPlatform()
            val routes = mutableListOf<String>()
            val witnessed = object : ReflectionSpeechPlatform by platform {
                override fun preparedRunVoiceAudio(text: String): ByteArray = byteArrayOf(1, 2, 3)
                override fun playCompletionFactsAudio(
                    utteranceId: String, audio: ByteArray,
                    onDone: (String) -> Unit, onUnavailable: (String) -> Unit, onError: (String) -> Unit
                ): Boolean {
                    routes += "completion-facts"
                    return true
                }
                override fun playRunVoiceAudio(
                    utteranceId: String, audio: ByteArray,
                    onDone: (String) -> Unit, onUnavailable: (String) -> Unit, onError: (String) -> Unit
                ): Boolean {
                    routes += "run-voice"
                    return true
                }
                override fun playMileAnnouncementAudio(
                    utteranceId: String, audio: ByteArray,
                    onDone: (String) -> Unit, onUnavailable: (String) -> Unit, onError: (String) -> Unit
                ): Boolean {
                    routes += "mile-announcement"
                    return true
                }
            }
            val speaker = AndroidReflectionSpeaker(composeTestRule.activity, witnessed,
                scheduleTimeout = { _, _ -> }, cancelTimeout = { })
            try {
                speaker.speakCompletionFacts("Your saved run facts.") { }
                speaker.speakReceipt("Choose your Energy when you are ready.") { }
                speaker.speakMileAnnouncement("One mile completed.") { }
                speaker.speakTimedCue("Three", useRunVoice = true)
                assertEquals(listOf("completion-facts", "run-voice", "mile-announcement", "run-voice"), routes)
                assertTrue("Prepared run audio must not also speak through the phone engine.", platform.spoken.isEmpty())
            } finally { speaker.close() }
        }
    }

    @Test fun timedCueWaitsForActualCompletionAndIgnoresStaleCallbacks() {
        composeTestRule.runOnUiThread {
            for (cloud in listOf(false, true)) {
                val platform = ControlledPlatform()
                var cloudId: String? = null
                var cloudDone: ((String) -> Unit)? = null
                var fetches = 0
                val witnessed = object : ReflectionSpeechPlatform by platform {
                    override fun preparedRunVoiceAudio(text: String): ByteArray? =
                        if (cloud) byteArrayOf(1) else null
                    override fun runVoiceGateway() = com.runstate.mobile.reflection.ReflectionSpeechGateway {
                        fetches++
                        byteArrayOf(1)
                    }
                    override fun playRunVoiceAudio(utteranceId: String, audio: ByteArray,
                        onDone: (String) -> Unit, onUnavailable: (String) -> Unit, onError: (String) -> Unit): Boolean {
                        cloudId = utteranceId
                        cloudDone = onDone
                        return true
                    }
                }
                val speaker = AndroidReflectionSpeaker(composeTestRule.activity, witnessed,
                    scheduleTimeout = { _, _ -> }, cancelTimeout = {})
                var introFinished = 0
                var tenFinished = 0
                try {
                    platform.ready(true)
                    // Missing prepared audio must fall back locally without fetching against the clock.
                    speaker.speakTimedCue("Starting in.", useRunVoice = true) { introFinished++ }
                    assertEquals(0, introFinished)
                    assertEquals(0, fetches)
                    val introId = if (cloud) requireNotNull(cloudId) else platform.spoken.last().id
                    val finishIntro = if (cloud) requireNotNull(cloudDone) else platform.voiceDone
                    finishIntro(introId)
                    assertEquals(1, introFinished)
                    speaker.speakTimedCue("Ten.", useRunVoice = cloud) { tenFinished++ }
                    finishIntro(introId)
                    platform.voiceFailed(introId)
                    assertEquals(1, introFinished)
                    assertEquals(0, tenFinished)
                    speaker.stop()
                    speaker.stop()
                    assertEquals("Stopping an owner releases its waiter exactly once.", 1, tenFinished)
                } finally { speaker.close() }
            }
        }
    }

    @Test fun timedCueFailureOrTimeoutCompletesOnceWithoutNetworkOrMusic() {
        composeTestRule.runOnUiThread {
            for (failure in listOf("engine failed", "engine timeout", "voice failed", "voice timeout", "focus denied")) {
                val platform = ControlledPlatform()
                val deadlines = mutableListOf<Runnable>()
                val speaker = speaker(platform, deadlines)
                var completed = 0
                try {
                    if (failure != "engine timeout") platform.ready(failure != "engine failed")
                    platform.focusGranted = failure != "focus denied"
                    speaker.speakTimedCue("Starting in.", useRunVoice = false) { completed++ }
                    when (failure) {
                        "engine timeout", "voice timeout" -> deadlines.last().run()
                        "voice failed" -> platform.voiceFailed(platform.spoken.single().id)
                    }
                    speaker.stop()
                    deadlines.forEach { it.run() }
                    assertEquals(failure, 1, completed)
                    assertTrue(platform.passages.isEmpty())
                    assertEquals(0, platform.connectionPreparations)
                } finally { speaker.close() }
            }
        }
    }

    @Test fun delayedMileVoiceFallsBackOnceAtAcceptedPaceAndReportsOnlyDeliveryMetadata() {
        composeTestRule.runOnUiThread {
            val platform = ControlledPlatform()
            val deadlines = mutableListOf<Runnable>()
            val delays = mutableListOf<Long>()
            val diagnostics = mutableListOf<SpeechDeliveryDiagnostic>()
            var cloudCalls = 0
            var cloudPlays = 0
            var completed = 0
            val pendingAudio = CompletableDeferred<ByteArray?>()
            val witnessed = object : ReflectionSpeechPlatform by platform {
                override fun runVoiceGateway() = com.runstate.mobile.reflection.ReflectionSpeechGateway {
                    cloudCalls++
                    pendingAudio.await()
                }
                override fun playMileAnnouncementAudio(utteranceId: String, audio: ByteArray,
                    onDone: (String) -> Unit, onUnavailable: (String) -> Unit, onError: (String) -> Unit): Boolean {
                    cloudPlays++
                    return true
                }
            }
            val speaker = AndroidReflectionSpeaker(composeTestRule.activity, witnessed,
                scheduleTimeout = { task, delay -> deadlines += task; delays += delay }, cancelTimeout = {},
                deliveryDiagnostic = diagnostics::add)
            try {
                platform.ready(true)
                speaker.speakMileAnnouncement("One mile. Your pace is ten minutes per mile.") { completed++ }
                assertEquals(1, cloudCalls)
                assertEquals(5_000L, delays.single())
                assertTrue(platform.spoken.isEmpty())
                val timedOut = deadlines.single()
                timedOut.run()
                pendingAudio.complete(byteArrayOf(1))
                timedOut.run()
                assertEquals(0, cloudPlays)
                assertEquals(1, platform.spoken.size)
                assertEquals(0.864f, platform.spoken.single().rate, 0.0001f)
                platform.voiceDone(platform.spoken.single().id)
                assertEquals(1, completed)
                assertEquals(SpeechDeliverySource.PHONE, diagnostics.last().source)
                assertTrue(diagnostics.all { it.kind == SpeechDeliveryKind.MILE_ANNOUNCEMENT && it.elapsedMillis >= 0 })
                assertEquals(SpeechDeliveryStage.FINISHED, diagnostics.last().stage)
            } finally { speaker.close() }
        }
    }

    @Test fun cloudMilePlaybackHasTheSamePaceAsFactsAndCannotDoubleSpeakAfterAudibleFailure() {
        composeTestRule.runOnUiThread {
            val platform = ControlledPlatform()
            val diagnostics = mutableListOf<SpeechDeliveryDiagnostic>()
            var cloudId: String? = null
            var failCloud: ((String) -> Unit)? = null
            var completed = 0
            val witnessed = object : ReflectionSpeechPlatform by platform {
                override fun runVoiceGateway() = com.runstate.mobile.reflection.ReflectionSpeechGateway { byteArrayOf(1) }
                override fun playMileAnnouncementAudio(utteranceId: String, audio: ByteArray,
                    onDone: (String) -> Unit, onUnavailable: (String) -> Unit, onError: (String) -> Unit): Boolean {
                    cloudId = utteranceId
                    failCloud = onError
                    return true
                }
            }
            val speaker = AndroidReflectionSpeaker(composeTestRule.activity, witnessed,
                scheduleTimeout = { _, _ -> }, cancelTimeout = {}, deliveryDiagnostic = diagnostics::add)
            try {
                platform.ready(true)
                speaker.speakMileAnnouncement("Two miles completed.") { completed++ }
                val queued = diagnostics.last()
                assertEquals(SpeechDeliveryStage.CLOUD_QUEUED, queued.stage)
                assertEquals(SpeechDeliverySource.CLOUD, queued.source)
                assertEquals(0.90f, queued.rate, 0.0001f)
                requireNotNull(failCloud)(requireNotNull(cloudId))
                requireNotNull(failCloud)(requireNotNull(cloudId))
                assertEquals(1, completed)
                assertTrue("An audible cloud failure must not repeat the announcement in another voice.", platform.spoken.isEmpty())
            } finally { speaker.close() }
        }
    }

    @Test
    fun exactSpeechCompletesBeforeSelectedPassageAndFinishesOnlyOnce() {
        composeTestRule.runOnUiThread {
            val platform = ControlledPlatform()
            val deadlines = mutableListOf<Runnable>()
            val delays = mutableListOf<Long>()
            val speaker = speaker(platform, deadlines, delays)
            var finished = 0
            val exact = "  Exact saved reflection.\nSecond line.  "
            try {
                speaker.speakWithSongPassage(exact, passage, onFinished = { finished++ })
                assertEquals("Connect silently while the voice is still preparing.", 1, platform.connectionPreparations)
                assertTrue(platform.spoken.isEmpty())
                assertTrue(platform.passages.isEmpty())
                platform.ready(true)
                val voice = platform.spoken.single()
                assertEquals(exact, voice.text)
                assertTrue(platform.passages.isEmpty())

                platform.voiceDone(voice.id)
                assertTrue("Music must wait after the reflection ends.", platform.passages.isEmpty())
                assertEquals(150L, delays.last())
                assertEquals("Speech focus is retained during the quiet gap.", "voice", platform.events.last())
                val handoff = deadlines.last()
                platform.voiceDone(voice.id)
                assertEquals("Duplicate speech completion must not reschedule music.", handoff, deadlines.last())
                handoff.run()
                assertEquals("The outer budget includes load, clip, restore and cleanup.", 60_000L, delays.last())
                val played = platform.passages.single()
                assertEquals(passage, played.request)
                assertEquals(0, finished)
                assertTrue(platform.events.indexOf("voice") < platform.events.indexOf("passage"))
                assertEquals("The transport retains speech focus through its baseline/load/seek.",
                    "voice", platform.events[platform.events.indexOf("passage") - 1])

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
                handoff.run()
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
                val deadlines = mutableListOf<Runnable>()
                val speaker = speaker(platform, deadlines)
                var finished = 0
                try {
                    platform.ready(true)
                    speaker.speakWithSongPassage("Saved reflection.", passage, onFinished = { finished++ })
                    platform.voiceDone(platform.spoken.single().id)
                    deadlines.last().run()
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
            val deadlines = mutableListOf<Runnable>()
            val speaker = speaker(platform, deadlines)
            var firstFinished = 0
            var secondFinished = 0
            try {
                platform.ready(true)
                speaker.speakWithSongPassage("First.", passage, onFinished = { firstFinished++ })
                val firstVoice = platform.spoken.single().id
                platform.voiceDone(firstVoice)
                deadlines.last().run()
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
                deadlines.last().run()
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
                    deadlines.last().run()
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

    @Test
    fun stopCloseAndFocusLossDuringQuietGapCancelMusicEvenWhenCancelledTaskRuns() {
        composeTestRule.runOnUiThread {
            listOf("stop", "close", "focus loss").forEach { interruption ->
                val platform = ControlledPlatform()
                val deadlines = mutableListOf<Runnable>()
                val cancelled = mutableListOf<Runnable>()
                val speaker = speaker(platform, deadlines, cancelled = cancelled)
                var finished = 0
                try {
                    platform.ready(true)
                    speaker.speakWithSongPassage("Saved reflection.", passage, onFinished = { finished++ })
                    val voice = platform.spoken.single().id
                    platform.voiceDone(voice)
                    val handoff = deadlines.last()
                    assertTrue(platform.passages.isEmpty())
                    when (interruption) {
                        "stop" -> speaker.stop()
                        "close" -> speaker.close()
                        "focus loss" -> platform.focusLosses.getValue(voice)(voice)
                    }
                    assertTrue(interruption, handoff in cancelled)
                    // Removing a scheduled task cannot recall one already queued for delivery.
                    handoff.run()
                    handoff.run()
                    platform.voiceDone(voice)
                    platform.voiceFailed(voice)
                    assertTrue(interruption, platform.passages.isEmpty())
                    assertEquals(interruption, 1, finished)
                    assertEquals(interruption, 0, platform.instrumentalStarts)
                } finally {
                    speaker.close()
                }
            }
        }
    }

    @Test
    fun replacingReflectionDuringQuietGapPreventsOldTaskFromStartingEitherSong() {
        composeTestRule.runOnUiThread {
            val platform = ControlledPlatform()
            val deadlines = mutableListOf<Runnable>()
            val cancelled = mutableListOf<Runnable>()
            val speaker = speaker(platform, deadlines, cancelled = cancelled)
            var firstFinished = 0
            var secondFinished = 0
            try {
                platform.ready(true)
                speaker.speakWithSongPassage("First reflection.", passage) { firstFinished++ }
                val firstVoice = platform.spoken.single().id
                platform.voiceDone(firstVoice)
                val oldHandoff = deadlines.last()

                val secondPassage = passage.copy(startPositionMillis = 8_000L)
                speaker.speakWithSongPassage("Second reflection.", secondPassage) { secondFinished++ }
                assertTrue(oldHandoff in cancelled)
                oldHandoff.run()
                platform.voiceDone(firstVoice)
                assertTrue(platform.passages.isEmpty())
                assertEquals(1, firstFinished)
                assertEquals(0, secondFinished)

                val secondVoice = platform.spoken.last().id
                platform.voiceDone(secondVoice)
                val currentHandoff = deadlines.last()
                oldHandoff.run()
                assertTrue(platform.passages.isEmpty())
                currentHandoff.run()
                oldHandoff.run()
                currentHandoff.run()
                val played = platform.passages.single()
                assertEquals(secondPassage, played.request)
                played.done(played.id)
                assertEquals(1, firstFinished)
                assertEquals(1, secondFinished)
            } finally {
                speaker.close()
            }
        }
    }

    @Test
    fun musicEligibilityRevokedOrUnavailableDuringQuietGapNeverStartsPlayback() {
        composeTestRule.runOnUiThread {
            listOf(false, true).forEach { eligibilityThrows ->
                val platform = ControlledPlatform()
                val deadlines = mutableListOf<Runnable>()
                val speaker = speaker(platform, deadlines)
                var gapStarted = false
                var finished = 0
                try {
                    platform.ready(true)
                    speaker.speakWithSongPassage("Saved reflection.", passage, canPlayPassage = {
                        if (gapStarted && eligibilityThrows) throw IllegalStateException("Unavailable owner")
                        !gapStarted
                    }, onFinished = { finished++ })
                    platform.voiceDone(platform.spoken.single().id)
                    val handoff = deadlines.last()
                    assertTrue(platform.passages.isEmpty())
                    gapStarted = true
                    handoff.run()
                    handoff.run()
                    assertTrue(platform.passages.isEmpty())
                    assertEquals(1, finished)
                    assertEquals(0, platform.instrumentalStarts)
                } finally {
                    speaker.close()
                }
            }
        }
    }
}
