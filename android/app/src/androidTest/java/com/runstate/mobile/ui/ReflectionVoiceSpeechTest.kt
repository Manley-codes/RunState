package com.runstate.mobile.ui

import androidx.activity.ComponentActivity
import android.os.SystemClock
import androidx.room.Room
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.runstate.mobile.data.local.*
import com.runstate.mobile.music.SongPassageRequest
import com.runstate.mobile.music.AndroidSpotifyPassageSession
import com.runstate.mobile.music.PassagePlayback
import com.runstate.mobile.music.reflectionSongCatalog
import com.runstate.mobile.music.SpotifySongPassageController
import com.runstate.mobile.music.SongPassageStatus
import com.runstate.mobile.music.SongPassageSession
import com.runstate.mobile.music.SongPassageSnapshot
import com.runstate.mobile.reflection.AndroidReflectionConnectionStore
import com.runstate.mobile.reflection.CompletedRunEvidence
import com.runstate.mobile.reflection.HostedReflectionHttpClient
import com.runstate.mobile.reflection.ReflectionApiRequest
import com.runstate.mobile.reflection.ReflectionCandidates
import com.runstate.mobile.reflection.ReflectionGateway
import com.runstate.mobile.reflection.ReflectionMetricsEvidence
import com.runstate.mobile.reflection.ReflectionMusicContext
import com.runstate.mobile.reflection.ReflectionSpeechGateway
import com.runstate.mobile.reflection.ReflectionSpeechHttpClient
import com.runstate.mobile.reflection.HostedSongStudyPreparationClient
import com.runstate.mobile.reflection.reflectionDestinationFingerprint
import com.runstate.mobile.reflection.RoomReflectionStore
import com.runstate.mobile.reflection.RunReflectionCoordinator
import com.runstate.mobile.reflection.RunReflectionState
import com.runstate.mobile.reflection.reflectionRequestFromSavedRun
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.UUID
import java.util.Base64
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Silent fake checks and explicitly opted-in real audio checks; no saved app-history writes. */
@RunWith(AndroidJUnit4::class)
class ReflectionVoiceSpeechTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val audio = byteArrayOf(0x49, 0x44, 0x33)
    private val passage = SongPassageRequest("Sunday Best", "Surfaces", 15_500)
    private enum class HostedMusicFlowStage {
        PREFLIGHT_SESSION, PREFLIGHT_CATALOG, PREFLIGHT_CONTROLS, CONNECTION, IN_MEMORY_DATABASE,
        FIXTURE_INSERT, FIXTURE_MUSIC, FIXTURE_PAUSE, FIXTURE_COMPLETE, FIXTURE_READ,
        EARLY_PREPARATION, COORDINATOR, RECEIPT, GENERATION, PERSISTENCE, AUDIO, RESTORATION, REOPEN, COMPLETE
    }

    /** Silent read-only metadata sampling: no playback, run/history writes or provider requests. */
    @Test fun currentSpotifyDurationReadOnlyProbe() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("allowRealSpotifyDurationProbe") == "yes")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val session = AndroidSpotifyPassageSession(instrumentation.targetContext)
        val initial = requireNotNull(session.snapshot())
        check(initial.playback == PassagePlayback.PAUSED)
        check(reflectionSongCatalog.count {
            it.title.equals(initial.title?.trim(), true) && it.artist.equals(initial.artist?.trim(), true)
        } == 1)
        var minimum = initial.durationMillis
        var maximum = initial.durationMillis
        repeat(30) {
            kotlinx.coroutines.delay(100)
            val next = requireNotNull(session.snapshot())
            check(next.sessionKey == initial.sessionKey && next.sourcePackage == initial.sourcePackage &&
                next.mediaId == initial.mediaId && next.title == initial.title && next.artist == initial.artist)
            check(next.playback == PassagePlayback.PAUSED && next.durationMillis in 1_000L..86_400_000L)
            minimum = minOf(minimum, next.durationMillis)
            maximum = maxOf(maximum, next.durationMillis)
        }
        instrumentation.sendStatus(2, android.os.Bundle().apply {
            putLong("runstate_duration_initial_ms", initial.durationMillis.coerceIn(0, 86_400_000))
            putLong("runstate_duration_min_ms", minimum)
            putLong("runstate_duration_max_ms", maximum)
        })
    }

    /** Explicit standalone passage check; no Claude, ElevenLabs or database is involved. */
    @Test fun currentPausedCatalogPassageProbe() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("allowRealSpotifyPassageProbe") == "yes")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val session = AndroidSpotifyPassageSession(instrumentation.targetContext)
        val initial = requireNotNull(session.snapshot())
        check(initial.playback == PassagePlayback.PAUSED && initial.canPlay && initial.canSeek)
        val song = requireNotNull(reflectionSongCatalog.singleOrNull {
            it.title.equals(initial.title?.trim(), true) && it.artist.equals(initial.artist?.trim(), true)
        })
        instrumentation.sendStatus(2, android.os.Bundle().apply {
            putLong("runstate_duration_initial_ms", initial.durationMillis.coerceIn(0, 86_400_000))
        })
        var issuedPlayAt: Long? = null
        val witnessed = object : SongPassageSession {
            override fun snapshot(): SongPassageSnapshot? = session.snapshot()
            override fun pause() = session.pause()
            override fun seekTo(positionMillis: Long) = session.seekTo(positionMillis)
            override fun play() {
                issuedPlayAt = SystemClock.elapsedRealtime()
                session.play()
            }
        }
        val controller = SpotifySongPassageController(witnessed, diagnosticObserver = { diagnostic ->
            instrumentation.sendStatus(2, android.os.Bundle().apply {
                putString("runstate_passage_diagnostic", diagnostic.toString())
            })
        })
        var completed = false
        val result = try {
            controller.play(song.plan().passage().copy(spotifyMediaId = initial.mediaId)).also {
                completed = it.status == SongPassageStatus.FINISHED
            }
        } finally {
            // Diagnostic-fixture containment only. If this probe started playback, cancel an
            // otherwise identical recording only while it still follows our own trajectory.
            // A new recording, user pause or distant user seek is never changed here.
            if (!completed) try {
                val playedAt = issuedPlayAt
                val next = session.snapshot()
                if (playedAt != null && next != null && next.playback == PassagePlayback.PLAYING &&
                    next.sourcePackage == initial.sourcePackage && next.sessionKey == initial.sessionKey &&
                    next.mediaId == initial.mediaId && next.title == initial.title && next.artist == initial.artist &&
                    next.positionMillis in 0..next.durationMillis &&
                    kotlin.math.abs(next.positionMillis - (song.startPositionMillis +
                        SystemClock.elapsedRealtime() - playedAt)) <= 1_500L) {
                    session.pause()
                    instrumentation.sendStatus(2, android.os.Bundle().apply {
                        putBoolean("runstate_probe_cleanup_pause_sent", true)
                    })
                }
            } catch (_: Exception) { /* The operator can inspect playback without leaking identity. */ }
        }
        instrumentation.sendStatus(2, android.os.Bundle().apply {
            putString("runstate_passage_probe_status", result.status.name)
        })
        assertEquals(SongPassageStatus.FINISHED, result.status)
        val restored = requireNotNull(session.snapshot())
        assertTrue(result.playbackRestored && restored.playback == PassagePlayback.PAUSED &&
            restored.sessionKey == initial.sessionKey && restored.sourcePackage == initial.sourcePackage &&
            restored.mediaId == initial.mediaId && restored.title == initial.title && restored.artist == initial.artist &&
            restored.durationMillis in 1_000L..86_400_000L &&
            kotlin.math.abs(restored.durationMillis - initial.durationMillis) <= 1L &&
            kotlin.math.abs(restored.positionMillis - initial.positionMillis) <= 2_000L)
    }

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

    /** One explicitly approved normal hosted journey; all run/history writes stay in memory. */
    @Test fun realHostedSavedRunUsesAcceptedStudyThenCustomVoiceAndSpotify() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("allowRealHostedMusicFlow") == "yes")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val timingOrigin = SystemClock.uptimeMillis()
        val generationStartedAt = AtomicLong(-1L)
        val generationFinishedAt = AtomicLong(-1L)
        val factsQueuedAt = AtomicLong(-1L)
        val factsFinishedAt = AtomicLong(-1L)
        val voicePreparationObservedAt = AtomicLong(-1L)
        val synthesisStartedAt = AtomicLong(-1L)
        val synthesisFinishedAt = AtomicLong(-1L)
        val playerHandoffAt = AtomicLong(-1L)
        val synthesisRequests = AtomicInteger(0)
        val synthesizedText = AtomicReference<String?>()
        val earlyPreparationStartedAt = AtomicLong(-1L)
        val earlyPreparationFinishedAt = AtomicLong(-1L)
        var database: RunStateDatabase? = null
        var speaker: AndroidReflectionSpeaker? = null
        var stage = HostedMusicFlowStage.PREFLIGHT_SESSION
        fun advance(next: HostedMusicFlowStage) {
            stage = next
            InstrumentationRegistry.getInstrumentation().sendStatus(2, android.os.Bundle().apply {
                putString("runstate_hosted_music_stage", next.name)
            })
        }
        fun reportTiming() {
            InstrumentationRegistry.getInstrumentation().sendStatus(2, android.os.Bundle().apply {
                // Also report on playback failure: these are durations, never private data.
                fun delta(started: Long, ended: Long): Long =
                    if (started >= 0L && ended >= started) (ended - started).coerceAtMost(300_000L) else -1L
                putLong("runstate_generation_start_offset_ms", delta(timingOrigin, generationStartedAt.get()))
                putLong("runstate_generation_end_offset_ms", delta(timingOrigin, generationFinishedAt.get()))
                putLong("runstate_generation_duration_ms", delta(generationStartedAt.get(), generationFinishedAt.get()))
                putLong("runstate_facts_start_offset_ms", delta(timingOrigin, factsQueuedAt.get()))
                putLong("runstate_facts_natural_done_offset_ms", delta(timingOrigin, factsFinishedAt.get()))
                putLong("runstate_facts_duration_ms", delta(factsQueuedAt.get(), factsFinishedAt.get()))
                putLong("runstate_voice_preparation_offset_ms", delta(timingOrigin, voicePreparationObservedAt.get()))
                putLong("runstate_synthesis_start_offset_ms", delta(timingOrigin, synthesisStartedAt.get()))
                putLong("runstate_synthesis_end_offset_ms", delta(timingOrigin, synthesisFinishedAt.get()))
                putLong("runstate_synthesis_duration_ms", delta(synthesisStartedAt.get(), synthesisFinishedAt.get()))
                putLong("runstate_reflection_player_handoff_offset_ms", delta(timingOrigin, playerHandoffAt.get()))
                putLong("runstate_facts_to_player_handoff_ms", delta(factsFinishedAt.get(), playerHandoffAt.get()))
                putInt("runstate_synthesis_requests", synthesisRequests.get())
                putLong("runstate_early_preparation_duration_ms",
                    delta(earlyPreparationStartedAt.get(), earlyPreparationFinishedAt.get()))
                putBoolean("runstate_generation_overlapped_facts", generationStartedAt.get() >= 0L &&
                    generationStartedAt.get() < factsFinishedAt.get() && generationFinishedAt.get() > factsQueuedAt.get())
                putBoolean("runstate_synthesis_started_before_facts_done", synthesisStartedAt.get() >= 0L &&
                    synthesisStartedAt.get() < factsFinishedAt.get())
            })
        }
        try {
            // Read-only preflight happens before either paid generation or audible playback.
            advance(HostedMusicFlowStage.PREFLIGHT_SESSION)
            val session = AndroidSpotifyPassageSession(context)
            val original = requireNotNull(session.snapshot())
            advance(HostedMusicFlowStage.PREFLIGHT_CATALOG)
            val song = requireNotNull(reflectionSongCatalog.singleOrNull {
                it.songStudyVersion == 1 && it.title.equals(original.title?.trim(), true) &&
                    it.artist.equals(original.artist?.trim(), true)
            })
            advance(HostedMusicFlowStage.PREFLIGHT_CONTROLS)
            check(original.sourcePackage == "com.spotify.music" &&
                original.playback == PassagePlayback.PAUSED &&
                original.canPlay && original.canSeek && original.positionMillis in 0..original.durationMillis &&
                original.durationMillis >= song.startPositionMillis + song.durationMillis + 2_000L)
            advance(HostedMusicFlowStage.CONNECTION)
            val connection = requireNotNull(runBlocking { AndroidReflectionConnectionStore(context).load() })
            advance(HostedMusicFlowStage.IN_MEMORY_DATABASE)
            val isolated = Room.inMemoryDatabaseBuilder(context, RunStateDatabase::class.java).build()
            database = isolated
            var finish = System.currentTimeMillis()
            val start = finish - 33 * 60_000L
            val runId = UUID.randomUUID().toString()
            val fixture = RunEntity(runId = runId, state = StoredRunState.RUNNING,
                officialStartEpochMillis = start, startTimezoneId = "America/Chicago",
                lastCheckpointEpochMillis = start, transitionHistoryComplete = true,
                preRunEnergy = PreRunEnergy.MODERATE, trackingMode = RunTrackingMode.FIXTURE,
                musicObservationRequested = true, musicReflectionSharingAllowed = true)
            runBlocking {
                advance(HostedMusicFlowStage.FIXTURE_INSERT)
                isolated.runDao().insert(fixture)
                // This is explicitly fictional test evidence using the paused recording's identity,
                // not a claim that a real run or a real PLAYING observation occurred on this phone.
                advance(HostedMusicFlowStage.FIXTURE_MUSIC)
                isolated.musicObservationDao().recordObservation(RunMusicObservation(
                    runId, "controlled-hosted-flow", start + 60_000L, null,
                    "com.spotify.music", original.title, original.artist, MusicPlaybackState.PLAYING,
                    MusicObservationKind.SESSION_SNAPSHOT))
                if (InstrumentationRegistry.getArguments().getString("allowRealSongPreparation") == "yes") {
                    advance(HostedMusicFlowStage.EARLY_PREPARATION)
                    val early = HostedSongStudyPreparationClient(connection)
                    check(early.supports())
                    val claim = requireNotNull(isolated.musicObservationDao().claimPreparation(
                        runId, UUID.randomUUID().toString(), 1, reflectionDestinationFingerprint(connection),
                        System.currentTimeMillis()))
                    // Still RUNNING: the preparation API receives no finished or provisional facts.
                    check(isolated.runDao().findById(runId)?.state == StoredRunState.RUNNING)
                    earlyPreparationStartedAt.set(SystemClock.uptimeMillis())
                    val prepared = early.prepare(claim.preparationId, claim.trackTitle, claim.artistName)
                    earlyPreparationFinishedAt.set(SystemClock.uptimeMillis())
                    isolated.musicObservationDao().finishPreparation(runId, claim.preparationId, prepared)
                    check(prepared)
                    finish = System.currentTimeMillis()
                }
                advance(HostedMusicFlowStage.FIXTURE_PAUSE)
                isolated.runDao().pauseRun(runId, start + 32 * 60_000L)
                advance(HostedMusicFlowStage.FIXTURE_COMPLETE)
                isolated.runDao().completeRun(runId, finish,
                    FinalRunMetrics(3 * 1_609.344, MetricSource.FIXTURE,
                        TelemetryCoverage.COMPLETE, DistanceUnit.MILES))
            }
            advance(HostedMusicFlowStage.FIXTURE_READ)
            val savedRun = requireNotNull(runBlocking { isolated.runDao().findById(runId) })
            val transitions = runBlocking { isolated.runDao().transitionsFor(runId) }
            val observations = runBlocking { isolated.musicObservationDao().observationsFor(runId) }
            val earlyClaim = runBlocking { isolated.musicObservationDao().preparationFor(runId) }
            val receipt = "Sample check. " + savedRunReceiptFor(savedRun, transitions)
            val requests = AtomicInteger(0)
            val sent = AtomicReference<ReflectionApiRequest?>()
            val delivered = AtomicReference<ReflectionCandidates?>()
            val client = HostedReflectionHttpClient(connection)
            val store = RoomReflectionStore(isolated.reflectionDao(), isolated.musicObservationDao())
            val coordinator = RunReflectionCoordinator(isolated.runDao(), store,
                ReflectionGateway { request ->
                    check(requests.incrementAndGet() == 1)
                    sent.set(request)
                    generationStartedAt.set(SystemClock.uptimeMillis())
                    try { client.request(request).also { delivered.set(it) } }
                    catch (failure: Exception) {
                        // Emit only a fixed category or bounded HTTP status, never remote text,
                        // generated reflections, connection settings or exception diagnostics.
                        val status = Regex("^The hosted reflection service rejected the request \\(HTTP ([1-5][0-9]{2})\\)\\.$")
                            .matchEntire(failure.message.orEmpty())?.groupValues?.get(1)
                        val category = when {
                            status != null -> "HTTP_$status"
                            failure.message == "The hosted reflection response was invalid." -> "INVALID_RESPONSE"
                            failure.message == "The hosted reflection service is unavailable." -> "UNAVAILABLE"
                            failure is kotlinx.coroutines.CancellationException -> "CANCELLED"
                            else -> "UNCLASSIFIED"
                        }
                        InstrumentationRegistry.getInstrumentation().sendStatus(2, android.os.Bundle().apply {
                            putString("runstate_hosted_failure_category", category)
                        })
                        throw failure
                    }
                    finally { generationFinishedAt.set(SystemClock.uptimeMillis()) }
                }, appScope)
            advance(HostedMusicFlowStage.COORDINATOR)
            runBlocking {
                coordinator.onSavedVisible(runId, awaitingReceipt = true).join()
                coordinator.saveEnergy(runId, ReflectionBranch.FEELING_GOOD)
            }

            val receiptId = AtomicReference<String?>()
            val receiptCompleted = AtomicBoolean(false)
            val receiptFinished = CountDownLatch(1)
            val preparationObserved = CountDownLatch(1)
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
            val selectedPassage = AtomicReference<SongPassageRequest?>()
            val speechClient = ReflectionSpeechHttpClient.hosted(connection)
            val timedSpeechGateway = ReflectionSpeechGateway { text ->
                check(synthesisRequests.incrementAndGet() == 1)
                synthesizedText.set(text)
                synthesisStartedAt.set(SystemClock.uptimeMillis())
                try { speechClient.synthesize(text) }
                finally { synthesisFinishedAt.set(SystemClock.uptimeMillis()) }
            }
            advance(HostedMusicFlowStage.RECEIPT)
            rule.runOnUiThread {
                val android = AndroidReflectionSpeechPlatform(context) { diagnostic ->
                    // Enum stages and bounded timing deltas only; no connection, song or text data.
                    InstrumentationRegistry.getInstrumentation().sendStatus(2, android.os.Bundle().apply {
                        putString("runstate_passage_diagnostic", diagnostic.toString())
                    })
                }
                val witnessed = object : ReflectionSpeechPlatform by android {
                    override fun reflectionSpeechGateway(): ReflectionSpeechGateway = timedSpeechGateway

                    override fun initialize(onReady: (Boolean) -> Unit,
                        onDone: (String) -> Unit, onError: (String) -> Unit) {
                        android.initialize(onReady, { id ->
                            if (id == receiptId.get()) {
                                factsFinishedAt.set(SystemClock.uptimeMillis())
                                receiptCompleted.set(true)
                            }
                            onDone(id)
                        }, { id ->
                            if (id == receiptId.get() && !receiptCompleted.get()) failed.set(true)
                            onError(id)
                        })
                    }

                    override fun speakReceipt(id: String, text: String, rate: Float): Boolean {
                        if (text != receipt) failed.set(true)
                        receiptId.set(id)
                        factsQueuedAt.set(SystemClock.uptimeMillis())
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
                        // Player handoff is measured here; prepareAsync still precedes audible speech.
                        playerHandoffAt.set(SystemClock.uptimeMillis())
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
                        if (!cloudCompleted.get() || passage != selectedPassage.get()) failed.set(true)
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
                    coordinator.finishReceipt(runId)
                    receiptFinished.countDown()
                }
                // Preparation may overlap the facts. Playback still waits for their natural finish
                // and the real coordinator's READY result below, exactly as the normal UI gates it.
                appScope.launch(Dispatchers.Main.immediate) {
                    coordinator.voicePreparation.collect { preparation ->
                        if (preparation != null && preparation.runId == runId &&
                            preparation.requestId == sent.get()?.requestId) {
                            voicePreparationObservedAt.compareAndSet(-1L, SystemClock.uptimeMillis())
                            owned.prepare(preparation.text)
                            preparationObserved.countDown()
                        }
                    }
                }
            }
            assertTrue("The fictional run facts did not finish in time.", receiptFinished.await(45, TimeUnit.SECONDS))
            assertTrue("The fictional run facts did not complete naturally.", receiptCompleted.get() && !failed.get())
            advance(HostedMusicFlowStage.GENERATION)
            val state = runBlocking {
                withTimeout(110_000L) {
                    coordinator.state.first { it is RunReflectionState.Ready || it is RunReflectionState.Failed }
                }
            }
            assertTrue("The normal hosted music request did not become ready.", state is RunReflectionState.Ready)
            advance(HostedMusicFlowStage.PERSISTENCE)
            val ready = state as RunReflectionState.Ready
            val frozen = requireNotNull(sent.get())
            val candidates = requireNotNull(delivered.get())
            val record = requireNotNull(runBlocking { isolated.reflectionDao().findByRunId(runId) })
            assertTrue("The hosted journey did not use one exact frozen saved-run request.",
                requests.get() == 1 && frozen == reflectionRequestFromSavedRun(
                    frozen.requestId, savedRun, transitions, observations, songPreparation = earlyClaim) &&
                    frozen.requestId == record.requestId && frozen.runId == runId)
            // The real hosted parser removes a cue unless the actual response also delivered
            // the requested study version. An equal version-one cue proves both guards passed.
            assertTrue("The hosted result did not acknowledge the accepted study and paired passage.",
                frozen.evidence.songPassage?.songStudyVersion == 1 &&
                    frozen.evidence.musicContext.songCue?.cueId == song.cue.cueId &&
                    candidates.songCueId == song.cue.cueId && !candidates.isTestData &&
                    !ready.isTestData && ready.passage == song.plan().passage())
            assertTrue("The selected Energy and exact generated text were not finalized in isolated Room.",
                record.status == ReflectionStatus.READY && record.selectedBranch == ReflectionBranch.FEELING_GOOD &&
                    record.postRunEnergy == ReflectionBranch.FEELING_GOOD && record.text == candidates.feelingGood &&
                    ready.text == record.text && record.candidatesJson == null && !record.isTestData &&
                    Gson().fromJson(record.evidenceJson, CompletedRunEvidence::class.java) == frozen.evidence)
            selectedPassage.set(requireNotNull(ready.passage))
            // Drain the actual preparation emission before submission, without a fixed sleep or
            // waiting for synthesis to finish. This avoids a main-queue race in the test itself.
            assertTrue("The selected voice preparation was not observed.", preparationObserved.await(5, TimeUnit.SECONDS))
            advance(HostedMusicFlowStage.AUDIO)
            rule.runOnUiThread {
                requireNotNull(speaker).speakWithSongPassage(ready.text, requireNotNull(ready.passage),
                    canPlayPassage = { cloudCompleted.get() && !failed.get() },
                    onFinished = { finished.countDown() })
            }
            assertTrue("The normal custom-voice and song sequence did not finish in time.",
                finished.await(90, TimeUnit.SECONDS))
            assertTrue("The exact saved reflection did not complete with the custom cloud voice.",
                cloudAccepted.get() && cloudCompleted.get())
            assertTrue("Voice preparation did not reuse one synthesis of the exact selected reflection.",
                synthesisRequests.get() == 1 && synthesizedText.get() == ready.text &&
                    voicePreparationObservedAt.get() >= timingOrigin &&
                    synthesisStartedAt.get() >= voicePreparationObservedAt.get() &&
                    synthesisFinishedAt.get() >= synthesisStartedAt.get())
            assertTrue("The reflection player was handed audio before the facts finished.",
                factsFinishedAt.get() >= factsQueuedAt.get() &&
                    playerHandoffAt.get() >= factsFinishedAt.get())
            assertTrue("The normal paired Spotify passage did not complete naturally.",
                songAccepted.get() && songCompleted.get())
            assertTrue("The reflection-to-song pause was shorter than intended.", songHandoffGap.get() >= 650L)
            assertFalse("The normal sequence used a local reflection voice.", localFallback.get())
            assertFalse("The normal sequence substituted the instrumental.", instrumentalAttempted.get())
            assertFalse("The normal sequence reported a playback failure.", failed.get())
            advance(HostedMusicFlowStage.RESTORATION)
            val restored = session.snapshot()
            assertTrue("Spotify did not restore the same paused recording and original position.",
                restored != null && restored.playback == PassagePlayback.PAUSED &&
                    restored.sessionKey == original.sessionKey && restored.sourcePackage == original.sourcePackage &&
                    restored.mediaId == original.mediaId && restored.title == original.title &&
                    restored.artist == original.artist && restored.durationMillis > 0L &&
                    kotlin.math.abs(restored.durationMillis - original.durationMillis) <= 1L &&
                    kotlin.math.abs(restored.positionMillis - original.positionMillis) <= 2_000L)

            advance(HostedMusicFlowStage.REOPEN)
            val restartRequests = AtomicInteger(0)
            val reopened = RunReflectionCoordinator(isolated.runDao(), store,
                ReflectionGateway { restartRequests.incrementAndGet(); error("Unexpected replay request.") }, appScope)
            runBlocking { reopened.onSavedVisible(runId).join() }
            val saved = reopened.state.value
            assertTrue("Reopening the saved reflection regenerated text or authorized automatic song replay.",
                saved is RunReflectionState.Ready && saved.text == ready.text && saved.passage == null &&
                    restartRequests.get() == 0 && requests.get() == 1)
            InstrumentationRegistry.getInstrumentation().sendStatus(2, android.os.Bundle().apply {
                putLong("runstate_reflection_song_gap_ms", songHandoffGap.get().coerceAtMost(120_000L))
                putInt("runstate_hosted_music_requests", requests.get())
                putBoolean("runstate_isolated_ready_and_reopen_verified", true)
            })
            advance(HostedMusicFlowStage.COMPLETE)
        } catch (_: Exception) {
            // Never echo saved access codes, source material, generated text or provider diagnostics.
            throw AssertionError("The optional normal hosted music-flow check could not complete at ${stage.name}.")
        } finally {
            reportTiming()
            rule.runOnUiThread { speaker?.close() }
            appScope.cancel()
            database?.close()
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
                    restored.artist == original.artist && restored.durationMillis > 0L &&
                    kotlin.math.abs(restored.durationMillis - original.durationMillis) <= 1L &&
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

    @Test fun silentExactVoicePreparationOverlapsFactsAndIsConsumedOnceWithoutEarlyPlayback() {
        val pending = CompletableDeferred<ByteArray?>()
        val uploaded = mutableListOf<String>()
        val platform = Platform(ReflectionSpeechGateway { uploaded += it; pending.await() })
        rule.runOnUiThread {
            val deadlines = mutableListOf<Runnable>()
            val owned = speaker(platform, deadlines)
            try {
                platform.ready(true)
                owned.speakReceipt("Run saved. One mile.") { }
                val factsId = platform.local.single().first
                val factsFocusRequests = platform.focusLoss.size
                owned.prepare("Exact prepared reflection.")
                owned.prepare("Exact prepared reflection.")
                assertEquals(listOf("Exact prepared reflection."), uploaded)
                assertEquals(factsFocusRequests, platform.focusLoss.size)
                assertTrue(platform.cloud.isEmpty())
                assertTrue(platform.songs.isEmpty())
                pending.complete(audio)
                assertTrue(platform.cloud.isEmpty())
                platform.localDone(factsId)
                owned.speakWithSongPassage("Exact prepared reflection.", passage) { }
                assertEquals(1, uploaded.size)
                assertEquals(1, platform.cloud.size)
                assertEquals(1, platform.local.size)
                assertTrue(platform.songs.isEmpty())
                val voice = platform.cloud.single()
                voice.done(voice.id)
                assertTrue(platform.songs.isEmpty())
                deadlines.last().run()
                assertEquals(1, platform.songs.size)
            } finally { owned.close() }
        }
    }

    @Test fun matchingSpeechAdoptsThePendingVoiceRequestInsteadOfStartingAnother() {
        val pending = CompletableDeferred<ByteArray?>()
        var calls = 0
        val platform = Platform(ReflectionSpeechGateway { calls++; pending.await() })
        rule.runOnUiThread {
            val owned = speaker(platform)
            try {
                owned.prepare("Still preparing this exact reflection.")
                owned.speakWithSongPassage("Still preparing this exact reflection.", passage) { }
                assertEquals(1, calls)
                assertTrue(platform.cloud.isEmpty())
                assertTrue(platform.local.isEmpty())
                assertTrue(platform.focusLoss.isEmpty())
                owned.cancelPreparation() // The audio has already moved to the speech owner.
                pending.complete(audio)
                assertEquals(1, calls)
                assertEquals(1, platform.cloud.size)
            } finally { owned.close() }
        }
    }

    @Test fun replacingPreparedTextAndSubmittingDifferentTextCancelUnusedRequests() {
        val first = CompletableDeferred<ByteArray?>()
        val second = CompletableDeferred<ByteArray?>()
        val uploaded = mutableListOf<String>()
        var cancelled = 0
        val platform = Platform(ReflectionSpeechGateway { text ->
            uploaded += text
            try {
                when (text) {
                    "First prepared reflection." -> first.await()
                    "Second prepared reflection." -> second.await()
                    else -> audio
                }
            } catch (cancelledRequest: kotlinx.coroutines.CancellationException) {
                cancelled++
                throw cancelledRequest
            }
        })
        rule.runOnUiThread {
            val owned = speaker(platform)
            try {
                owned.prepare("First prepared reflection.")
                owned.prepare("Second prepared reflection.")
                assertEquals(1, cancelled)
                owned.speak("Different selected reflection.")
                assertEquals(2, cancelled)
                assertEquals(listOf("First prepared reflection.", "Second prepared reflection.",
                    "Different selected reflection."), uploaded)
                assertEquals(1, platform.cloud.size)
                first.complete(audio); second.complete(audio)
                assertEquals(1, platform.cloud.size)
            } finally { owned.close() }
        }
    }

    @Test fun stopCloseAndExplicitCancellationReleaseSilentPreparationWithoutPlayback() {
        for (action in listOf("stop", "close", "cancel", "adopt then stop")) {
            val pending = CompletableDeferred<ByteArray?>()
            var calls = 0
            var cancellations = 0
            val platform = Platform(ReflectionSpeechGateway {
                calls++
                try { pending.await() }
                catch (cancelled: kotlinx.coroutines.CancellationException) { cancellations++; throw cancelled }
            })
            rule.runOnUiThread {
                val owned = speaker(platform)
                try {
                    owned.prepare("Exact reflection being prepared.")
                    when (action) {
                        "stop" -> owned.stop()
                        "close" -> owned.close()
                        "cancel" -> owned.cancelPreparation()
                        else -> {
                            owned.speak("Exact reflection being prepared.")
                            owned.stop()
                        }
                    }
                    pending.complete(audio)
                    assertEquals(1, calls)
                    assertEquals(1, cancellations)
                    assertTrue(platform.cloud.isEmpty())
                    assertTrue(platform.local.isEmpty())
                    assertTrue(platform.songs.isEmpty())
                    assertTrue(platform.focusLoss.isEmpty())
                } finally { owned.close() }
            }
        }
    }

    @Test fun unusablePreparedAudioFallsBackOnceWithoutRepeatingTheVoiceRequest() {
        for (failure in listOf("null", "exception", "empty", "oversize")) {
            var calls = 0
            val platform = Platform(ReflectionSpeechGateway {
                calls++
                when (failure) {
                    "exception" -> throw IllegalStateException("Fixture voice unavailable")
                    "empty" -> byteArrayOf()
                    "oversize" -> ByteArray(com.runstate.mobile.reflection.MAX_REFLECTION_AUDIO_BYTES + 1)
                    else -> null
                }
            })
            rule.runOnUiThread {
                val owned = speaker(platform)
                try {
                    platform.ready(true)
                    owned.prepare("Exact fallback reflection.")
                    owned.prepare("Exact fallback reflection.")
                    assertEquals(1, calls)
                    assertTrue(platform.local.isEmpty())
                    owned.speak("Exact fallback reflection.")
                    assertEquals(1, calls)
                    assertEquals(listOf("Exact fallback reflection."), platform.local.map { it.second })
                    assertTrue(platform.cloud.isEmpty())
                } finally { owned.close() }
            }
        }
    }

    @Test fun separatelyCancelledPreparedWorkerFallsBackImmediatelyWithoutAnotherRequest() {
        var calls = 0
        val platform = Platform(ReflectionSpeechGateway {
            calls++
            throw kotlinx.coroutines.CancellationException("Fixture prepared worker cancelled")
        })
        rule.runOnUiThread {
            val owned = speaker(platform)
            try {
                platform.ready(true)
                owned.prepare("Exact reflection from cancelled preparation.")
                assertTrue(platform.local.isEmpty())
                owned.speak("Exact reflection from cancelled preparation.")
                assertEquals(1, calls)
                assertEquals(listOf("Exact reflection from cancelled preparation."),
                    platform.local.map { it.second })
                assertTrue(platform.cloud.isEmpty())
            } finally { owned.close() }
        }
    }
}
