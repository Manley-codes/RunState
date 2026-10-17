package com.runstate.mobile.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.runstate.mobile.music.SongPassageRequest
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Seven silent fake checks and one explicitly opted-in real hosted voice check; no Room writes. */
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
            val receipt = "Sample run saved. Three miles of sample distance in thirty-two minutes. " +
                "Your reflection will use a different voice, followed by the instrumental."
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

                    override fun speak(id: String, text: String, rate: Float): Boolean {
                        if (text == receipt) receiptId.set(id) else localFallback.set(true)
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

    private class Platform(val gateway: ReflectionSpeechGateway) : ReflectionSpeechPlatform {
        data class Voice(val id: String, val done: (String) -> Unit,
            val unavailable: (String) -> Unit, val error: (String) -> Unit)
        lateinit var ready: (Boolean) -> Unit
        lateinit var localDone: (String) -> Unit
        lateinit var localError: (String) -> Unit
        val local = mutableListOf<Pair<String, String>>()
        val cloud = mutableListOf<Voice>()
        val songs = mutableListOf<Voice>()
        val focusLoss = mutableMapOf<String, (String) -> Unit>()
        var cloudAccepted = true
        var cloudStops = 0
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
            return true
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
        override fun releaseAudioFocus() = Unit
        override fun close() { closes++ }
    }

    private fun speaker(platform: Platform, deadlines: MutableList<Runnable> = mutableListOf()) =
        AndroidReflectionSpeaker(rule.activity, platform,
            scheduleTimeout = { task, _ -> deadlines += task }, cancelTimeout = {})

    @Test fun receiptStaysLocalThenExactSelectedReflectionUsesCloudBeforeMusic() {
        val uploaded = mutableListOf<String>()
        val platform = Platform(ReflectionSpeechGateway { uploaded += it; audio })
        rule.runOnUiThread {
            val speaker = speaker(platform)
            try {
                platform.ready(true)
                speaker.speakReceipt("Run saved. One mile.") { }
                assertEquals(listOf("Run saved. One mile."), platform.local.map { it.second })
                assertTrue(uploaded.isEmpty())
                platform.localDone(platform.local.single().first)
                var finished = 0
                speaker.speakWithSongPassage("Your exact selected reflection.", passage) { finished++ }
                assertEquals(listOf("Your exact selected reflection."), uploaded)
                assertEquals(1, platform.local.size)
                val cloud = platform.cloud.single()
                assertTrue(platform.songs.isEmpty())
                // A late local-engine callback with even this ID is not cloud completion.
                platform.localDone(cloud.id)
                assertTrue(platform.songs.isEmpty())
                cloud.done(cloud.id)
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

    @Test fun unavailableServiceOrUnplayableAudioFallsBackOnceBeforeAnyCloudAudio() {
        for (missing in listOf(true, false)) rule.runOnUiThread {
            val platform = Platform(ReflectionSpeechGateway { if (missing) null else audio })
            platform.cloudAccepted = false
            val speaker = speaker(platform)
            try {
                platform.ready(true)
                var finished = 0
                speaker.speakWithSongPassage("Exact fallback reflection.", passage) { finished++ }
                assertEquals(listOf("Exact fallback reflection."), platform.local.map { it.second })
                platform.cloud.firstOrNull()?.let { cloud ->
                    cloud.unavailable(cloud.id)
                    cloud.done(cloud.id)
                    cloud.error(cloud.id)
                }
                assertEquals(1, platform.local.size)
                assertTrue(platform.songs.isEmpty())
                platform.localDone(platform.local.single().first)
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
            val speaker = speaker(platform)
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
