package com.runstate.mobile.ui

import androidx.activity.ComponentActivity
import android.os.SystemClock
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.runstate.mobile.music.SongPassageRequest
import com.runstate.mobile.music.AndroidSpotifyPassageSession
import com.runstate.mobile.music.PassagePlayback
import com.runstate.mobile.reflection.AndroidReflectionConnectionStore
import com.runstate.mobile.reflection.CompletedRunEvidence
import com.runstate.mobile.reflection.HostedReflectionHttpClient
import com.runstate.mobile.reflection.ReflectionApiRequest
import com.runstate.mobile.reflection.ReflectionMetricsEvidence
import com.runstate.mobile.reflection.ReflectionMusicContext
import com.runstate.mobile.reflection.ReflectionSpeechGateway
import com.runstate.mobile.reflection.ReflectionSpeechHttpClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import java.util.UUID
import java.util.Base64
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Silent fake checks and explicitly opted-in real audio checks; no Room writes. */
@RunWith(AndroidJUnit4::class)
class ReflectionVoiceSpeechTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val audio = byteArrayOf(0x49, 0x44, 0x33)
    private val passage = SongPassageRequest("Sunday Best", "Surfaces", 15_500)

    /** Private device configuration stays on-device; only controlled sample facts leave it. */
    @Test fun realHostedReflectionUsesCustomVoiceAfterLocalFactsAndBeforeInstrumental() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("allowRealHostedVoice") == "yes")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var speaker: AndroidReflectionSpeaker? = null
        try {
            val config = runBlocking { AndroidReflectionConnectionStore(context).load() }
            assertNotNull("The optional hosted voice check needs the saved online connection.", config)
            val connection = requireNotNull(config)
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
            val candidates = runBlocking { HostedReflectionHttpClient(connection).request(request) }
            assertFalse("The hosted voice check requires real reflection generation.", candidates.isTestData)
            assertTrue("The controlled sample must not acquire a curated song cue.", candidates.songCueId == null)
            val reflection = candidates.feelingGood
            assertTrue("The hosted voice check needs a nonblank selected reflection.", reflection.isNotBlank())
            val receipt = "Sample run saved. Three miles of sample distance in thirty-two minutes."
            val receiptId = AtomicReference<String?>()
            val receiptCompleted = AtomicBoolean(false)
            val cloudAccepted = AtomicBoolean(false)
            val cloudCompleted = AtomicBoolean(false)
            val instrumentalAccepted = AtomicBoolean(false)
            val instrumentalCompleted = AtomicBoolean(false)
            val failed = AtomicBoolean(false)
            val localFallback = AtomicBoolean(false)
            val finished = CountDownLatch(1)
            rule.runOnUiThread {
                val android = AndroidReflectionSpeechPlatform(context)
                val witnessed = object : ReflectionSpeechPlatform by android {
                    override fun reflectionSpeechGateway(): ReflectionSpeechGateway =
                        ReflectionSpeechHttpClient.hosted(connection)

                    override fun initialize(onReady: (Boolean) -> Unit,
                        onDone: (String) -> Unit, onError: (String) -> Unit) {
                        android.initialize(onReady, { id ->
                            if (id == receiptId.get()) receiptCompleted.set(true)
                            onDone(id)
                        }, { id ->
                            if (id == receiptId.get() && !receiptCompleted.get()) failed.set(true)
                            onError(id)
                        })
                    }

                    override fun speakReceipt(id: String, text: String, rate: Float): Boolean {
                        if (text != receipt) failed.set(true)
                        receiptId.set(id)
                        return android.speakReceipt(id, text, rate)
                    }

                    override fun speak(id: String, text: String, rate: Float): Boolean {
                        localFallback.set(true)
                        return android.speak(id, text, rate)
                    }

                    override fun playReflectionAudio(id: String, audio: ByteArray,
                        onDone: (String) -> Unit, onUnavailable: (String) -> Unit,
                        onError: (String) -> Unit): Boolean {
                        if (!receiptCompleted.get()) failed.set(true)
                        val accepted = android.playReflectionAudio(id, audio,
                            { doneId -> cloudCompleted.set(true); onDone(doneId) },
                            { unavailableId -> failed.set(true); onUnavailable(unavailableId) },
                            { errorId -> failed.set(true); onError(errorId) })
                        cloudAccepted.set(accepted)
                        return accepted
                    }

                    override fun playInstrumental(id: String,
                        onDone: (String) -> Unit, onError: (String) -> Unit,
                        onFocusLost: (String) -> Unit): Boolean {
                        if (!cloudCompleted.get()) failed.set(true)
                        val accepted = android.playInstrumental(id,
                            { doneId -> instrumentalCompleted.set(true); onDone(doneId) },
                            { errorId -> failed.set(true); onError(errorId) },
                            { lostId -> failed.set(true); onFocusLost(lostId) })
                        instrumentalAccepted.set(accepted)
                        return accepted
                    }
                }
                val owned = AndroidReflectionSpeaker(context, witnessed)
                speaker = owned
                owned.speakReceipt(receipt) {
                    owned.speakWithInstrumental(reflection,
                        canPlayInstrumental = { cloudCompleted.get() && !failed.get() },
                        onFinished = { finished.countDown() })
                }
            }
            assertTrue("The optional hosted voice sequence did not finish in time.",
                finished.await(120, TimeUnit.SECONDS))
            assertTrue("The local sample-facts voice did not complete naturally.", receiptCompleted.get())
            assertTrue("The custom cloud voice was not accepted and completed.",
                cloudAccepted.get() && cloudCompleted.get())
            assertFalse("The custom voice check unexpectedly used local reflection speech.", localFallback.get())
            assertTrue("The instrumental did not complete after the custom reflection voice.",
                instrumentalAccepted.get() && instrumentalCompleted.get())
            assertFalse("The optional hosted voice sequence reported a playback failure.", failed.get())
        } catch (_: Exception) {
            // No private configuration, generated text or provider diagnostics become test output.
            throw AssertionError("The optional hosted custom-voice check could not complete.")
        } finally {
            rule.runOnUiThread { speaker?.close() }
        }
    }

    /** Audition an already generated controlled reflection without making another model request. */
    @Test fun suppliedAutomaticMusicReflectionUsesCloudVoiceThenSpotifyPassage() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("allowRealMusicAnalysisAudio") == "yes")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var speaker: AndroidReflectionSpeaker? = null
        try {
            val scenario = arguments.getString("musicScenario") ?: "sunday_best"
            require(scenario in setOf("sunday_best", "stronger", "custom"))
            val runScenario = arguments.getString("runScenario") ?: "standard"
            require(runScenario in setOf("standard", "morning_feeling_good", "evening_no_selection", "late_morning_powered_up"))
            val stronger = scenario == "stronger"
            val custom = scenario == "custom"
            fun suppliedIdentity(key: String): String {
                val encodedIdentity = requireNotNull(arguments.getString(key))
                require(encodedIdentity.length in 1..1_200)
                val identity = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(Base64.getUrlDecoder().decode(encodedIdentity))).toString()
                require(identity.isNotBlank() && identity.length <= 200 && identity.none { it.isISOControl() })
                return identity
            }
            val title = if (custom) suppliedIdentity("songTitleBase64") else if (stronger) "Stronger" else "Sunday Best"
            val artist = if (custom) suppliedIdentity("songArtistBase64") else if (stronger) "Kanye West" else "Surfaces"
            // Manley requested comparing Stronger's opening with the previous 24-second entrance.
            val passageStart = if (custom) requireNotNull(arguments.getString("passageStartMillis")?.toLongOrNull())
                else if (stronger) 0L else 15_500L
            require(passageStart in 0L..86_400_000L)
            // An explicit audition can check a complete-phrase ending without changing the catalog.
            val passageDuration = arguments.getString("passageDurationMillis")?.let {
                requireNotNull(it.toLongOrNull())
            } ?: if (custom) error("A custom audition requires an explicit duration.") else 10_000L
            require(passageDuration in 1_000L..10_000L)
            val expectedDuration = arguments.getString("expectedTrackDurationMillis")?.let {
                requireNotNull(it.toLongOrNull()).also { duration -> require(duration in 1_000L..86_400_000L) }
            }
            val encoded = arguments.getString("reflectionBase64")
            require(encoded != null && encoded.length in 1..16_000)
            val bytes = Base64.getUrlDecoder().decode(encoded)
            val reflection = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
            require(reflection.isNotBlank() && reflection.length <= 4_000 && '\u0000' !in reflection)

            // Fail before any voice or network request if the explicitly auditioned recording is absent.
            val session = AndroidSpotifyPassageSession(context)
            val snapshot = session.snapshot()
            assertNotNull("Queue and pause the selected original recording before the optional audio check.", snapshot)
            val original = requireNotNull(snapshot)
            assertTrue("The optional audio check requires the selected original recording, paused.",
                original.sourcePackage == "com.spotify.music" &&
                    original.title.equals(title, true) && original.artist.equals(artist, true) &&
                    original.playback == PassagePlayback.PAUSED && original.canPlay && original.canSeek &&
                    original.durationMillis >= passageStart + passageDuration + 2_000L &&
                    (expectedDuration == null || kotlin.math.abs(original.durationMillis - expectedDuration) <= 1_500L) &&
                    original.positionMillis in 0..original.durationMillis)
            val selectedPassage = SongPassageRequest(title, artist, passageStart, passageDuration,
                spotifyMediaId = original.mediaId)
            val config = runBlocking { AndroidReflectionConnectionStore(context).load() }
            assertNotNull("The optional audio check needs the saved hosted connection.", config)
            val connection = requireNotNull(config)
            val receipt = when (runScenario) {
                "morning_feeling_good" -> "Sample run. Distance, three miles. Elapsed time, thirty-three minutes. " +
                    "Active time, thirty-two minutes."
                "evening_no_selection" -> "Sample run. Distance, four miles. Elapsed time, forty-five minutes. " +
                    "Active time, forty-three minutes."
                "late_morning_powered_up" -> "Sample run. Distance, two point seven five miles. " +
                    "Elapsed time, thirty minutes. Active time, twenty-eight minutes."
                else -> if (stronger || custom) "Sample run. Distance, four point two miles. " +
                "Elapsed time, forty-two minutes. Active time, forty minutes. " +
                "Average pace, nine minutes, thirty-one seconds per mile."
                else "Sample run saved. Three miles of sample distance in thirty-two minutes."
            }
            val receiptId = AtomicReference<String?>()
            val receiptCompleted = AtomicBoolean(false)
            val cloudAccepted = AtomicBoolean(false)
            val cloudCompleted = AtomicBoolean(false)
            val songAccepted = AtomicBoolean(false)
            val songCompleted = AtomicBoolean(false)
            val reflectionCompletedAt = AtomicLong(0)
            val songHandoffGap = AtomicLong(-1)
            val instrumentalAttempted = AtomicBoolean(false)
            val localFallback = AtomicBoolean(false)
            val failed = AtomicBoolean(false)
            val finished = CountDownLatch(1)
            rule.runOnUiThread {
                val android = AndroidReflectionSpeechPlatform(context) { diagnostic ->
                    // Explicit audition only: enum stages and bounded timing deltas, never track metadata.
                    InstrumentationRegistry.getInstrumentation().sendStatus(2, android.os.Bundle().apply {
                        putString("runstate_passage_diagnostic", diagnostic.toString())
                    })
                }
                val witnessed = object : ReflectionSpeechPlatform by android {
                    override fun reflectionSpeechGateway(): ReflectionSpeechGateway =
                        ReflectionSpeechHttpClient.hosted(connection)

                    override fun initialize(onReady: (Boolean) -> Unit,
                        onDone: (String) -> Unit, onError: (String) -> Unit) {
                        android.initialize(onReady, { id ->
                            if (id == receiptId.get()) receiptCompleted.set(true)
                            onDone(id)
                        }, { id ->
                            if (id == receiptId.get() && !receiptCompleted.get()) failed.set(true)
                            onError(id)
                        })
                    }

                    override fun speakReceipt(id: String, text: String, rate: Float): Boolean {
                        if (text != receipt) failed.set(true)
                        receiptId.set(id)
                        return android.speakReceipt(id, text, rate)
                    }

                    override fun speak(id: String, text: String, rate: Float): Boolean {
                        localFallback.set(true)
                        return android.speak(id, text, rate)
                    }

                    override fun playReflectionAudio(id: String, audio: ByteArray,
                        onDone: (String) -> Unit, onUnavailable: (String) -> Unit,
                        onError: (String) -> Unit): Boolean {
                        if (!receiptCompleted.get()) failed.set(true)
                        val accepted = android.playReflectionAudio(id, audio,
                            { doneId ->
                                reflectionCompletedAt.set(SystemClock.uptimeMillis())
                                cloudCompleted.set(true)
                                onDone(doneId)
                            },
                            { unavailableId -> failed.set(true); onUnavailable(unavailableId) },
                            { errorId -> failed.set(true); onError(errorId) })
                        cloudAccepted.set(accepted)
                        return accepted
                    }

                    override fun playSongPassage(id: String, passage: SongPassageRequest,
                        onDone: (String) -> Unit, onError: (String) -> Unit,
                        onFocusLost: (String) -> Unit): Boolean {
                        if (!cloudCompleted.get()) failed.set(true)
                        songHandoffGap.set(SystemClock.uptimeMillis() - reflectionCompletedAt.get())
                        val accepted = android.playSongPassage(id, passage,
                            { doneId -> songCompleted.set(true); onDone(doneId) },
                            { errorId -> failed.set(true); onError(errorId) },
                            { lostId -> failed.set(true); onFocusLost(lostId) })
                        songAccepted.set(accepted)
                        return accepted
                    }

                    override fun playInstrumental(id: String,
                        onDone: (String) -> Unit, onError: (String) -> Unit,
                        onFocusLost: (String) -> Unit): Boolean {
                        instrumentalAttempted.set(true)
                        return false
                    }
                }
                val owned = AndroidReflectionSpeaker(context, witnessed)
                speaker = owned
                owned.speakReceipt(receipt) {
                    owned.speakWithSongPassage(reflection, selectedPassage,
                        canPlayPassage = { cloudCompleted.get() && !failed.get() },
                        onFinished = { finished.countDown() })
                }
            }
            assertTrue("The optional supplied-reflection audio sequence did not finish in time.",
                finished.await(120, TimeUnit.SECONDS))
            assertTrue("The local sample facts did not finish naturally.", receiptCompleted.get())
            assertTrue("The supplied reflection did not complete with the real cloud voice.",
                cloudAccepted.get() && cloudCompleted.get())
            assertFalse("The optional supplied-reflection check used local reflection speech.", localFallback.get())
            assertTrue("The automatic Spotify passage did not complete naturally.",
                songAccepted.get() && songCompleted.get())
            assertTrue("The song started without the intended pause after reflection speech.",
                songHandoffGap.get() >= 650L)
            InstrumentationRegistry.getInstrumentation().sendStatus(2, android.os.Bundle().apply {
                putLong("runstate_reflection_song_gap_ms", songHandoffGap.get().coerceAtMost(120_000L))
            })
            assertFalse("The song check must not substitute the instrumental.", instrumentalAttempted.get())
            assertFalse("The optional supplied-reflection sequence reported a playback failure.", failed.get())
            val restored = session.snapshot()
            assertTrue("Spotify did not restore the same paused recording and original position.",
                restored != null && restored.playback == PassagePlayback.PAUSED &&
                    restored.sessionKey == original.sessionKey && restored.sourcePackage == original.sourcePackage &&
                    restored.mediaId == original.mediaId && restored.title == original.title &&
                    restored.artist == original.artist && restored.durationMillis == original.durationMillis &&
                    kotlin.math.abs(restored.positionMillis - original.positionMillis) <= 2_000L)
        } catch (_: Exception) {
            // Never echo supplied text, private connection details or transport/provider diagnostics.
            throw AssertionError("The optional supplied-reflection music check could not complete.")
        } finally {
            rule.runOnUiThread { speaker?.close() }
        }
    }

    private class Platform(val gateway: ReflectionSpeechGateway) : ReflectionSpeechPlatform {
        data class Voice(val id: String, val done: (String) -> Unit,
            val unavailable: (String) -> Unit, val error: (String) -> Unit)
        lateinit var ready: (Boolean) -> Unit
        lateinit var localDone: (String) -> Unit
        lateinit var localError: (String) -> Unit
        val local = mutableListOf<Pair<String, String>>()
        val localRates = mutableListOf<Float>()
        val receiptRates = mutableListOf<Float>()
        val cloud = mutableListOf<Voice>()
        val songs = mutableListOf<Voice>()
        val focusLoss = mutableMapOf<String, (String) -> Unit>()
        var cloudAccepted = true
        var cloudStops = 0
        var focusReleases = 0
        var closes = 0
        override fun reflectionSpeechGateway() = gateway
        override fun initialize(onReady: (Boolean) -> Unit,
            onDone: (String) -> Unit, onError: (String) -> Unit) {
            ready = onReady; localDone = onDone; localError = onError
        }
        override fun requestAudioFocus(id: String, onFocusLost: (String) -> Unit): Boolean {
            focusLoss[id] = onFocusLost
            return true
        }
        override fun speak(id: String, text: String, rate: Float): Boolean {
            local += id to text
            localRates += rate
            return true
        }
        override fun speakReceipt(id: String, text: String, rate: Float): Boolean {
            receiptRates += rate
            return speak(id, text, rate)
        }
        override fun playReflectionAudio(id: String, audio: ByteArray,
            onDone: (String) -> Unit, onUnavailable: (String) -> Unit,
            onError: (String) -> Unit): Boolean {
            cloud += Voice(id, onDone, onUnavailable, onError)
            return cloudAccepted
        }
        override fun stopReflectionAudio() { cloudStops++ }
        override fun playSongPassage(id: String, passage: SongPassageRequest,
            onDone: (String) -> Unit, onError: (String) -> Unit,
            onFocusLost: (String) -> Unit): Boolean {
            songs += Voice(id, onDone, onError, onFocusLost)
            return true
        }
        override fun stop() = Unit
        override fun releaseAudioFocus() { focusReleases++ }
        override fun close() { closes++ }
    }

    private fun speaker(platform: Platform, deadlines: MutableList<Runnable> = mutableListOf()) =
        AndroidReflectionSpeaker(rule.activity, platform,
            scheduleTimeout = { task, _ -> deadlines += task }, cancelTimeout = {})

    @Test fun receiptStaysLocalThenExactSelectedReflectionUsesCloudBeforeMusic() {
        val uploaded = mutableListOf<String>()
        val platform = Platform(ReflectionSpeechGateway { uploaded += it; audio })
        rule.runOnUiThread {
            val deadlines = mutableListOf<Runnable>()
            val speaker = speaker(platform, deadlines)
            try {
                platform.ready(true)
                speaker.speakReceipt("Run saved. One mile.") { }
                assertEquals(listOf("Run saved. One mile."), platform.local.map { it.second })
                assertEquals(listOf(0.96f), platform.receiptRates)
                assertEquals(listOf(0.96f), platform.localRates)
                assertTrue(uploaded.isEmpty())
                platform.localDone(platform.local.single().first)
                var finished = 0
                speaker.speakWithSongPassage("Your exact selected reflection.", passage) { finished++ }
                assertEquals(listOf("Your exact selected reflection."), uploaded)
                assertEquals(1, platform.local.size)
                assertEquals(1, platform.receiptRates.size)
                val cloud = platform.cloud.single()
                assertTrue(platform.songs.isEmpty())
                // A late local-engine callback with even this ID is not cloud completion.
                platform.localDone(cloud.id)
                assertTrue(platform.songs.isEmpty())
                cloud.done(cloud.id)
                assertTrue(platform.songs.isEmpty())
                deadlines.last().run()
                assertEquals(1, platform.songs.size)
                assertEquals(0, finished)
                platform.songs.single().done(cloud.id)
                assertEquals(1, finished)
                cloud.done(cloud.id)
                assertEquals(1, finished)
            } finally { speaker.close() }
        }
    }

    @Test fun cloudReflectionDoesNotWaitForOrFailWithLocalTtsEngine() {
        for (failedEngine in listOf(false, true)) rule.runOnUiThread {
            val platform = Platform(ReflectionSpeechGateway { audio })
            val speaker = speaker(platform)
            try {
                if (failedEngine) platform.ready(false)
                speaker.speak("Cloud reflection, even without a local engine.")
                assertEquals(1, platform.cloud.size)
                assertTrue(platform.local.isEmpty())
                platform.ready(false)
                val cloud = platform.cloud.single()
                platform.localError(cloud.id)
                cloud.done(cloud.id)
                assertTrue(platform.songs.isEmpty()) // History-style speech remains voice only.
            } finally { speaker.close() }
        }
    }

    @Test fun cloudCompletionKeepsFocusDuringSongGapAndCancellationPreventsLatePlayback() {
        for (action in listOf("stop", "close", "focus loss")) rule.runOnUiThread {
            val platform = Platform(ReflectionSpeechGateway { audio })
            val scheduled = mutableListOf<Pair<Runnable, Long>>()
            val cancelled = mutableListOf<Runnable>()
            val speaker = AndroidReflectionSpeaker(rule.activity, platform,
                scheduleTimeout = { task, delay -> scheduled += task to delay },
                cancelTimeout = { cancelled += it })
            var finished = 0
            try {
                speaker.speakWithSongPassage("Exact cloud reflection.", passage) { finished++ }
                val voice = platform.cloud.single()
                val releasesBeforeCompletion = platform.focusReleases
                voice.done(voice.id)
                val (handoff, delay) = scheduled.last()
                assertEquals(650L, delay)
                assertEquals(releasesBeforeCompletion, platform.focusReleases)
                assertTrue(platform.songs.isEmpty())
                voice.done(voice.id)
                assertSame(handoff, scheduled.last().first)
                when (action) {
                    "stop" -> speaker.stop()
                    "close" -> speaker.close()
                    else -> platform.focusLoss.getValue(voice.id)(voice.id)
                }
                assertTrue(cancelled.contains(handoff))
                handoff.run()
                voice.done(voice.id)
                assertTrue(platform.songs.isEmpty())
                assertEquals(1, finished)
            } finally { speaker.close() }
        }
    }

    @Test fun unavailableServiceOrUnplayableAudioFallsBackOnceBeforeAnyCloudAudio() {
        for (missing in listOf(true, false)) rule.runOnUiThread {
            val platform = Platform(ReflectionSpeechGateway { if (missing) null else audio })
            platform.cloudAccepted = false
            val deadlines = mutableListOf<Runnable>()
            val speaker = speaker(platform, deadlines)
            try {
                platform.ready(true)
                var finished = 0
                speaker.speakWithSongPassage("Exact fallback reflection.", passage) { finished++ }
                assertEquals(listOf("Exact fallback reflection."), platform.local.map { it.second })
                assertEquals(listOf(0.94f), platform.localRates)
                assertTrue(platform.receiptRates.isEmpty())
                platform.cloud.firstOrNull()?.let { cloud ->
                    cloud.unavailable(cloud.id)
                    cloud.done(cloud.id)
                    cloud.error(cloud.id)
                }
                assertEquals(1, platform.local.size)
                assertTrue(platform.songs.isEmpty())
                platform.localDone(platform.local.single().first)
                assertTrue(platform.songs.isEmpty())
                deadlines.last().run()
                assertEquals(1, platform.songs.size)
                platform.songs.single().done(platform.songs.single().id)
                assertEquals(1, finished)
            } finally { speaker.close() }
        }
    }

    @Test fun asynchronousPreparationFailureCanFallbackButAudibleFailureCannotRepeatOrStartMusic() {
        for (audible in listOf(false, true)) rule.runOnUiThread {
            val platform = Platform(ReflectionSpeechGateway { audio })
            val speaker = speaker(platform)
            try {
                platform.ready(true)
                var finished = 0
                speaker.speakWithSongPassage("Exact reflection.", passage) { finished++ }
                val cloud = platform.cloud.single()
                if (audible) cloud.error(cloud.id) else cloud.unavailable(cloud.id)
                cloud.done(cloud.id)
                assertTrue(platform.songs.isEmpty())
                assertEquals(if (audible) 0 else 1, platform.local.size)
                assertEquals(if (audible) 1 else 0, finished)
            } finally { speaker.close() }
        }
    }

    @Test fun stopAndCloseDuringFetchConsumeAttemptAndIgnoreLateAudio() {
        for (closing in listOf(false, true)) {
            val pending = CompletableDeferred<ByteArray?>()
            val platform = Platform(ReflectionSpeechGateway { pending.await() })
            var finished = 0
            lateinit var owned: AndroidReflectionSpeaker
            rule.runOnUiThread {
                owned = speaker(platform)
                owned.speakWithSongPassage("Pending reflection.", passage) { finished++ }
                if (closing) owned.close() else owned.stop()
                pending.complete(audio)
            }
            rule.waitForIdle()
            rule.runOnUiThread {
                assertTrue(platform.cloud.isEmpty())
                assertTrue(platform.local.isEmpty())
                assertTrue(platform.songs.isEmpty())
                assertEquals(1, finished)
                owned.close()
            }
        }
    }

    @Test fun staleCloudCallbacksCannotFinishNewReflectionOrStartItsMusic() {
        val platform = Platform(ReflectionSpeechGateway { audio })
        rule.runOnUiThread {
            val deadlines = mutableListOf<Runnable>()
            val speaker = speaker(platform, deadlines)
            try {
                var oldFinished = 0
                var newFinished = 0
                speaker.speakWithSongPassage("First.", passage) { oldFinished++ }
                val old = platform.cloud.single()
                speaker.speakWithSongPassage("Second.", passage) { newFinished++ }
                val current = platform.cloud.last()
                old.done(old.id); old.unavailable(old.id); old.error(old.id)
                assertEquals(1, oldFinished)
                assertEquals(0, newFinished)
                assertTrue(platform.songs.isEmpty())
                current.done(current.id)
                assertTrue(platform.songs.isEmpty())
                deadlines.last().run()
                assertEquals(1, platform.songs.size)
                platform.songs.single().done(current.id)
                assertEquals(1, newFinished)
            } finally { speaker.close() }
        }
    }

    @Test fun preparationTimeoutFallsBackAndFocusLossNeverStartsMusic() {
        val pending = CompletableDeferred<ByteArray?>()
        val platform = Platform(ReflectionSpeechGateway { pending.await() })
        val deadlines = mutableListOf<Runnable>()
        rule.runOnUiThread {
            val speaker = speaker(platform, deadlines)
            try {
                platform.ready(true)
                var finished = 0
                speaker.speakWithSongPassage("Exact timeout reflection.", passage) { finished++ }
                deadlines.last().run()
                assertEquals(1, platform.local.size)
                pending.complete(audio)
                val id = platform.local.single().first
                platform.focusLoss.getValue(id)(id)
                platform.localDone(id)
                assertTrue(platform.songs.isEmpty())
                assertTrue(platform.cloud.isEmpty())
                assertEquals(1, finished)
            } finally { speaker.close() }
        }
    }
}
