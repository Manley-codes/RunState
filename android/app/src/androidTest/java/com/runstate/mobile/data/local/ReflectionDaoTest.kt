package com.runstate.mobile.data.local

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.runstate.mobile.reflection.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URL
import java.security.cert.Certificate
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.HttpsURLConnection
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReflectionDaoTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: RunStateDatabase
    private val completed = RunEntity(
        runId = "0f6a2c1e-9d43-4b7a-9c21-7b5e8a4d1f30",
        state = StoredRunState.COMPLETED,
        officialStartEpochMillis = 1_756_000_000_000L,
        startTimezoneId = "America/Chicago",
        lastCheckpointEpochMillis = 1_756_000_060_000L,
        finishEpochMillis = 1_756_000_060_000L
    )
    private val pending = RunReflectionEntity(
        runId = completed.runId,
        requestId = "8c405ab0-4a80-4be9-9bfb-19f2517ec2ba",
        evidenceJson = "{\"source\":\"saved-run\"}",
        selectionDeadlineEpochMillis = 1_756_000_066_000L
    )
    private val retryId = "03689ffc-65d9-4907-ae77-7a17b3ad77a6"

    @Before
    fun setUp() {
        context.deleteDatabase(DATABASE_NAME)
        database = buildRunStateDatabase(context, DATABASE_NAME)
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(DATABASE_NAME)
    }

    private suspend fun createPending(): ReflectionDao {
        database.runDao().insert(completed)
        return database.reflectionDao().also { it.ensurePending(pending) }
    }

    @Test
    fun explicitNotSureUsesSupportedRoomPathAndReopensDistinctFromTimeout() = runBlocking {
        val dao = createPending()
        val selected = RoomReflectionStore(dao).energy(completed.runId, ReflectionBranch.NO_SELECTION,
            pending.selectionDeadlineEpochMillis - 1, false)
        assertEquals(ReflectionBranch.NO_SELECTION, selected.selectedBranch)
        assertEquals(ReflectionBranch.NO_SELECTION, selected.postRunEnergy)
        dao.saveCandidates(completed.runId, pending.requestId, "{}", true)
        assertTrue(dao.finalizeReady(completed.runId, pending.requestId, ReflectionBranch.NO_SELECTION,
            "Exact unknown-energy reflection.", pending.selectionDeadlineEpochMillis))
        val exact = dao.findByRunId(completed.runId)
        database.close()
        database = buildRunStateDatabase(context, DATABASE_NAME)
        assertEquals(exact, database.reflectionDao().findByRunId(completed.runId))
        val afterFeedback = database.reflectionDao().recordEnergy(completed.runId, ReflectionBranch.SPENT,
            pending.selectionDeadlineEpochMillis + 1)
        assertEquals("Exact unknown-energy reflection.", afterFeedback.text)
        assertEquals(ReflectionBranch.NO_SELECTION, afterFeedback.selectedBranch)
        assertEquals(ReflectionBranch.SPENT, afterFeedback.postRunEnergy)
    }

    @Test
    fun untimedEnergySurvivesReopenAndReceiptWithoutAnyDeadlineOrInventedAnswer() = runBlocking {
        database.runDao().insert(completed.copy(activityFinalized = false))
        var dao = database.reflectionDao()
        dao.ensurePending(pending.copy(selectionDeadlineEpochMillis = ReflectionDao.UNTIMED_SELECTION_DEADLINE,
            selectionWindowStarted = false))
        dao.saveCandidates(completed.runId, pending.requestId, "{}", true)
        database.close()
        database = buildRunStateDatabase(context, DATABASE_NAME)
        dao = database.reflectionDao()
        val opened = dao.beginSelectionWindow(completed.runId, pending.selectionDeadlineEpochMillis)
        assertEquals(ReflectionDao.UNTIMED_SELECTION_DEADLINE, opened.selectionDeadlineEpochMillis)
        assertEquals(opened, dao.selectNoSelectionIfDue(completed.runId, Long.MAX_VALUE))
        assertNull(opened.selectedBranch)
        assertNull(opened.postRunEnergy)
        val selected = dao.recordEnergy(completed.runId, ReflectionBranch.SPENT, Long.MAX_VALUE)
        assertEquals(ReflectionBranch.SPENT, selected.selectedBranch)
        assertEquals(ReflectionBranch.SPENT, selected.postRunEnergy)
        assertTrue(dao.finalizeReady(completed.runId, pending.requestId, ReflectionBranch.SPENT,
            "Exact late Energy reflection.", pending.selectionDeadlineEpochMillis + 1))
    }

    @Test
    fun savingUntimedActivitySelectsNeutralOnlyAfterDurableSaveAndKeepsEnergyUnanswered() = runBlocking {
        val original = completed.copy(activityFinalized = false)
        database.runDao().insert(original)
        val dao = database.reflectionDao()
        val waiting = pending.copy(selectionDeadlineEpochMillis = ReflectionDao.UNTIMED_SELECTION_DEADLINE,
            selectionWindowStarted = false)
        dao.ensurePending(waiting)
        assertEquals(waiting, dao.selectForSavedActivity(completed.runId))
        assertTrue(database.runDao().finalizeActivity(completed.runId))
        val selected = dao.selectForSavedActivity(completed.runId)
        assertEquals(ReflectionBranch.NO_SELECTION, selected.selectedBranch)
        assertNull(selected.postRunEnergy)
        assertTrue(selected.selectionWindowStarted)
        assertEquals(selected, dao.selectForSavedActivity(completed.runId))
        assertEquals(original.copy(activityFinalized = true), database.runDao().findById(completed.runId))
        assertTrue(dao.saveCandidates(completed.runId, pending.requestId, "{}", true))
        assertTrue(dao.finalizeReady(completed.runId, pending.requestId, ReflectionBranch.NO_SELECTION,
            "Exact unanswered reflection.", pending.selectionDeadlineEpochMillis))
        val ready = dao.findByRunId(completed.runId)
        database.close()
        database = buildRunStateDatabase(context, DATABASE_NAME)
        assertEquals(ready, database.reflectionDao().selectForSavedActivity(completed.runId))
    }

    @Test
    fun durableSaveWinsAgainstLateEnergyBeforeTheSaveCallbackRuns() = runBlocking {
        database.runDao().insert(completed.copy(activityFinalized = false))
        val dao = database.reflectionDao()
        dao.ensurePending(pending.copy(selectionDeadlineEpochMillis = ReflectionDao.UNTIMED_SELECTION_DEADLINE))
        assertTrue(database.runDao().finalizeActivity(completed.runId))
        val lateAnswer = dao.recordEnergy(completed.runId, ReflectionBranch.SPENT,
            pending.selectionDeadlineEpochMillis + 60_000L)
        assertEquals(ReflectionBranch.NO_SELECTION, lateAnswer.selectedBranch)
        assertEquals(ReflectionBranch.SPENT, lateAnswer.postRunEnergy)
        assertEquals(lateAnswer, dao.selectForSavedActivity(completed.runId))
    }

    @Test
    fun deliberateNotSureStaysDistinctFromSavingAnUnansweredUntimedActivity() = runBlocking {
        database.runDao().insert(completed.copy(activityFinalized = false))
        val dao = database.reflectionDao()
        dao.ensurePending(pending.copy(selectionDeadlineEpochMillis = ReflectionDao.UNTIMED_SELECTION_DEADLINE))
        val chosen = dao.recordNotSure(completed.runId, pending.selectionDeadlineEpochMillis + 86_400_000L)
        assertEquals(ReflectionBranch.NO_SELECTION, chosen.selectedBranch)
        assertEquals(ReflectionBranch.NO_SELECTION, chosen.postRunEnergy)
        assertTrue(database.runDao().finalizeActivity(completed.runId))
        assertEquals(chosen, dao.selectForSavedActivity(completed.runId))
    }

    @Test
    fun onlyUnansweredUnfinalizedLegacyPendingRowsCanBecomeUntimed() = runBlocking {
        database.runDao().insert(completed.copy(activityFinalized = false))
        val dao = database.reflectionDao()
        dao.ensurePending(pending)
        val promoted = dao.waitForEnergyWithoutDeadline(completed.runId)
        assertEquals(pending.copy(selectionDeadlineEpochMillis = ReflectionDao.UNTIMED_SELECTION_DEADLINE), promoted)
        assertEquals(promoted, dao.waitForEnergyWithoutDeadline(completed.runId))
        dao.recordEnergy(completed.runId, ReflectionBranch.POWERED_UP, pending.selectionDeadlineEpochMillis + 60_000L)
        assertTrue(dao.saveCandidates(completed.runId, pending.requestId, "{}", true))
        assertTrue(dao.finalizeReady(completed.runId, pending.requestId, ReflectionBranch.POWERED_UP,
            "  Exact selected text.\n", pending.selectionDeadlineEpochMillis + 60_001L))
        val ready = requireNotNull(dao.findByRunId(completed.runId))
        assertTrue(database.runDao().finalizeActivity(completed.runId))
        assertEquals(ready, dao.waitForEnergyWithoutDeadline(completed.runId))
        assertEquals(ready, dao.selectForSavedActivity(completed.runId))
    }

    @Test
    fun legacySavedAndAlreadySelectedRowsKeepTheirOriginalPolicy() = runBlocking {
        val dao = createPending() // A historical activity is already finalized.
        assertEquals(pending, dao.waitForEnergyWithoutDeadline(completed.runId))
        assertEquals(pending, dao.selectForSavedActivity(completed.runId))
        val selected = dao.recordEnergy(completed.runId, ReflectionBranch.SPENT,
            pending.selectionDeadlineEpochMillis - 1L)
        database.openHelper.writableDatabase.execSQL(
            "UPDATE runs SET activity_finalized = 0 WHERE run_id = ?", arrayOf(completed.runId))
        assertEquals(selected, dao.waitForEnergyWithoutDeadline(completed.runId))
        assertEquals(selected, dao.selectForSavedActivity(completed.runId))
    }

    @Test
    fun automaticNoSelectionStillLeavesEnergyUnansweredUntilExplicitNotSure() = runBlocking {
        val dao = createPending()
        val automatic = dao.selectNoSelectionIfDue(completed.runId, pending.selectionDeadlineEpochMillis)
        assertNull(automatic.postRunEnergy)
        assertEquals(ReflectionBranch.NO_SELECTION, automatic.selectedBranch)
        val explicit = dao.recordNotSure(completed.runId, pending.selectionDeadlineEpochMillis + 1)
        assertEquals(ReflectionBranch.NO_SELECTION, explicit.postRunEnergy)
        assertEquals(automatic.copy(postRunEnergy = ReflectionBranch.NO_SELECTION), explicit)
    }

    /** Real Room/coordinator/HTTP serialization, isolated fixture storage and in-memory wire only. */
    @Test fun observedSongsThroughHostedContractSaveTheNonFirstWinnerAndReopenWithoutReplay() = runBlocking {
        val start = completed.officialStartEpochMillis
        val finish = start + 150_000L
        val runId = completed.runId
        database.runDao().insert(completed.copy(state = StoredRunState.RUNNING,
            finishEpochMillis = null, lastCheckpointEpochMillis = start,
            trackingMode = RunTrackingMode.FIXTURE, transitionHistoryComplete = true,
            preRunEnergy = PreRunEnergy.MODERATE,
            musicObservationRequested = true, musicReflectionSharingAllowed = true))
        val music = database.musicObservationDao()
        suspend fun observe(key: String, offset: Long, title: String, artist: String,
            playback: MusicPlaybackState = MusicPlaybackState.PLAYING,
            source: String = "com.spotify.music") = music.recordObservation(RunMusicObservation(
                runId, key, start + offset, null, source, title, artist, playback,
                MusicObservationKind.METADATA_CHANGED))
        suspend fun listenForOneMinute(key: String, beginsAt: Long, title: String, artist: String) {
            repeat(12) { sample ->
                val offset = beginsAt + (sample + 1) * 5_000L
                music.recordObservation(RunMusicObservation(runId, "$key-sample-$sample",
                    start + offset, (offset + 1_000L) * 1_000_000L, "com.spotify.music", title, artist,
                    MusicPlaybackState.PLAYING, MusicObservationKind.PLAYBACK_SAMPLE,
                    playbackIntervalMillis = 5_000L))
            }
        }
        val firstSequence = observe("first", 1_000L, "Stronger", "Kanye West").sequenceNumber
        listenForOneMinute("first", 1_000L, "Stronger", "Kanye West")
        val winnerSequence = observe("winner", 65_000L, "Lovely Day", "Bill Withers").sequenceNumber
        listenForOneMinute("winner", 65_000L, "Lovely Day", "Bill Withers")
        assertEquals(1, firstSequence)
        assertEquals(14, winnerSequence)
        observe("duplicate", 126_000L, " LOVELY  DAY ", "Bill Withers")
        observe("paused", 130_000L, "Paused report", "Fixture artist", MusicPlaybackState.PAUSED)
        observe("other-player", 135_000L, "Other player", "Fixture artist", source = "other.player")
        observe("before-start", -1L, "Old report", "Fixture artist")
        observe("after-finish", finish - start + 1L, "Late report", "Fixture artist")

        val config = ReflectionConnectionConfig("https://reflection.example.com/api/reflections/candidates", "a".repeat(43))
        val fingerprint = reflectionDestinationFingerprint(config)
        val firstPreparation = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
        val secondPreparation = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"
        requireNotNull(music.claimPreparation(runId, firstPreparation, firstSequence, fingerprint, start + 62_000L))
        requireNotNull(music.claimPreparation(runId, secondPreparation, winnerSequence, fingerprint, start + 127_000L))
        music.finishPreparation(runId, firstPreparation, true)
        music.finishPreparation(runId, secondPreparation, true)
        database.runDao().pauseRun(runId, finish)
        database.runDao().completeRun(runId, finish,
            FinalRunMetrics(160.9344, MetricSource.FIXTURE, TelemetryCoverage.COMPLETE, DistanceUnit.MILES))
        val originalRun = requireNotNull(database.runDao().findById(runId))
        val originalObservations = music.observationsFor(runId)
        val exactText = "  Exact second-song reflection.\nA deliberate finish.  "
        val posts = AtomicInteger()
        val healthChecks = AtomicInteger()
        val sent = AtomicReference<JsonObject>()
        val client = HostedReflectionHttpClient(config, connectionFactory = { url ->
            SelectionFixtureHttpsConnection(url) { raw ->
                if (url.path == "/health") {
                    healthChecks.incrementAndGet()
                    "{\"status\":\"ok\"}"
                } else {
                    posts.incrementAndGet()
                    val request = JsonParser.parseString(raw).asJsonObject
                    sent.set(request)
                    JsonObject().apply {
                        addProperty("requestId", request.get("requestId").asString)
                        addProperty("runId", request.get("runId").asString)
                        // Simulated production envelope exercises passage eligibility; no provider is contacted.
                        addProperty("generationMode", "ANTHROPIC")
                        addProperty("selectedSongId", winnerSequence.toString())
                        addProperty("songSelectionMethod", "CONTEXT_MATCH")
                        addProperty("songCueId", "lovely_day_bill_withers_v1")
                        add("candidates", JsonObject().apply {
                            addProperty("spent", "Unused spent branch.")
                            addProperty("feeling_good", exactText)
                            addProperty("powered_up", "Unused powered-up branch.")
                            addProperty("no_selection", "Unused unanswered branch.")
                        })
                    }.toString()
                }
            }
        })
        val firstJob = SupervisorJob()
        val reopenedJob = SupervisorJob()
        try {
            val coordinator = RunReflectionCoordinator(database.runDao(),
                RoomReflectionStore(database.reflectionDao(), music), client,
                CoroutineScope(firstJob + Dispatchers.Default),
                requestIdSupplier = { UUID.fromString(pending.requestId) }, nowMillis = { finish + 100L })
            coordinator.onSavedVisible(runId, awaitingReceipt = true).join()
            coordinator.saveEnergy(runId, ReflectionBranch.FEELING_GOOD)
            coordinator.finishReceipt(runId).join()
            val ready = withTimeout(10_000L) { coordinator.state.first { it is RunReflectionState.Ready } }
                as RunReflectionState.Ready
            assertEquals(exactText, ready.text)
            assertEquals("Lovely Day", ready.passage?.trackTitle)
            assertEquals("Bill Withers", ready.passage?.artistName)
            assertEquals(1, posts.get())
            assertEquals(1, healthChecks.get())
            val wire = requireNotNull(sent.get())
            val songs = wire.getAsJsonArray("songCandidates")
            assertEquals(listOf(firstSequence.toString(), winnerSequence.toString()),
                songs.map { it.asJsonObject.get("candidateId").asString })
            assertEquals(listOf(firstPreparation, secondPreparation),
                songs.map { it.asJsonObject.get("songPreparationId").asString })
            assertEquals("Stronger", wire.getAsJsonObject("evidence").getAsJsonObject("musicContext")
                .get("trackTitle").asString)
            val wireText = wire.toString()
            listOf("songPassage", "startPositionMillis", "destinationFingerprint", "preparationReference",
                "songPreparationReference", "selectedSongId", "songSelectionMethod", "Paused report", "Late report",
                "Old report", "Other player").forEach { assertFalse(wireText.contains(it)) }
            assertFalse(wire.getAsJsonObject("evidence").has("songCandidates"))
            val saved = requireNotNull(database.reflectionDao().findByRunId(runId))
            val selected = Gson().fromJson(saved.evidenceJson, CompletedRunEvidence::class.java)
            assertEquals(winnerSequence.toString(), selected.selectedSongId)
            assertEquals("CONTEXT_MATCH", selected.songSelectionMethod)
            assertEquals("Lovely Day", selected.musicContext.trackTitle)
            assertEquals("Lovely Day", selected.songPassage?.trackTitle)
            assertEquals(exactText, saved.text)
            assertEquals(ReflectionBranch.FEELING_GOOD, saved.selectedBranch)
            assertNull(saved.candidatesJson)
            assertEquals(originalRun, database.runDao().findById(runId))
            assertEquals(originalObservations, music.observationsFor(runId))
            firstJob.cancelAndJoin()
            database.close()
            database = buildRunStateDatabase(context, DATABASE_NAME)
            val reopened = RunReflectionCoordinator(database.runDao(),
                RoomReflectionStore(database.reflectionDao(), database.musicObservationDao()), client,
                CoroutineScope(reopenedJob + Dispatchers.Default), nowMillis = { finish + 100L })
            reopened.onSavedVisible(runId).join()
            val restored = withTimeout(10_000L) { reopened.state.first { it is RunReflectionState.Ready } }
                as RunReflectionState.Ready
            assertEquals(exactText, restored.text)
            assertNull(restored.passage)
            assertEquals(saved, database.reflectionDao().findByRunId(runId))
            assertEquals(1, posts.get())
            assertEquals(1, healthChecks.get())
            assertEquals(1, database.runDao().countRuns())
        } finally {
            firstJob.cancelAndJoin()
            reopenedJob.cancelAndJoin()
        }
    }

    /** Deliberately cannot open a socket; this supplies only controlled HTTP response bytes. */
    private class SelectionFixtureHttpsConnection(url: URL, private val response: (String) -> String) :
        HttpsURLConnection(url) {
        private val sent = ByteArrayOutputStream()
        private val body by lazy { response(sent.toString("UTF-8")) }
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy() = false
        override fun getCipherSuite() = "TLS_AES_128_GCM_SHA256"
        override fun getLocalCertificates(): Array<Certificate> = emptyArray()
        override fun getServerCertificates(): Array<Certificate> = emptyArray()
        override fun getOutputStream() = sent
        override fun getResponseCode() = 200
        override fun getInputStream() = ByteArrayInputStream(body.toByteArray(Charsets.UTF_8))
        override fun getHeaderField(name: String?): String? = when {
            name.equals("X-RunState-Song-Cue", true) || name.equals("X-RunState-Song-Study", true) ||
                name.equals("X-RunState-Song-Preparation", true) || name.equals("X-RunState-Song-Selection", true) -> "1"
            else -> null
        }
    }

    @Test fun selectedSongEvidenceIsAtomicAndSurvivesFinalizationAndReopen() = runBlocking {
        val dao = createPending()
        val selected = "{\"selectedSongId\":\"2\",\"songSelectionMethod\":\"CONTEXT_MATCH\"}"
        assertFalse(dao.saveCandidates(completed.runId, retryId, "{}", false, selected))
        assertEquals(pending.evidenceJson, dao.findByRunId(completed.runId)?.evidenceJson)
        assertTrue(dao.saveCandidates(completed.runId, pending.requestId, "{}", false, selected))
        assertFalse(dao.saveCandidates(completed.runId, pending.requestId, "{}", false, "{}"))
        dao.selectNoSelectionIfDue(completed.runId, pending.selectionDeadlineEpochMillis)
        assertTrue(dao.finalizeReady(completed.runId, pending.requestId, ReflectionBranch.NO_SELECTION,
            "Exact selected reply.", pending.selectionDeadlineEpochMillis))
        database.close()
        database = buildRunStateDatabase(context, DATABASE_NAME)
        val saved = requireNotNull(database.reflectionDao().findByRunId(completed.runId))
        assertEquals(selected, saved.evidenceJson)
        assertEquals("Exact selected reply.", saved.text)
        assertNull(saved.candidatesJson)
    }

    @Test
    fun receiptPendingWindowSurvivesReopenAndStartsExactlyOnce() = runBlocking {
        database.runDao().insert(completed)
        var dao = database.reflectionDao()
        dao.ensurePending(pending.copy(selectionWindowStarted = false))
        assertNull(dao.selectNoSelectionIfDue(completed.runId, Long.MAX_VALUE).selectedBranch)
        database.close()
        database = buildRunStateDatabase(context, DATABASE_NAME)
        dao = database.reflectionDao()
        assertFalse(requireNotNull(dao.findByRunId(completed.runId)).selectionWindowStarted)

        val newDeadline = pending.selectionDeadlineEpochMillis + 12_000L
        val started = dao.beginSelectionWindow(completed.runId, newDeadline)
        assertTrue(started.selectionWindowStarted)
        assertEquals(newDeadline, started.selectionDeadlineEpochMillis)
        assertEquals(started, dao.beginSelectionWindow(completed.runId, newDeadline + 100_000L))
        assertNull(dao.selectNoSelectionIfDue(completed.runId, newDeadline - 1L).selectedBranch)
        assertEquals(ReflectionBranch.NO_SELECTION,
            dao.selectNoSelectionIfDue(completed.runId, newDeadline).selectedBranch)
    }

    @Test
    fun receiptAcceptsEarlyEnergyButCannotFinalizeUntilDeliveryEnds() = runBlocking {
        database.runDao().insert(completed)
        val dao = database.reflectionDao()
        dao.ensurePending(pending.copy(selectionWindowStarted = false))
        val afterPlaceholderDeadline = pending.selectionDeadlineEpochMillis + 20_000L
        val chosen = dao.recordEnergy(completed.runId, ReflectionBranch.SPENT, afterPlaceholderDeadline)
        assertEquals(ReflectionBranch.SPENT, chosen.selectedBranch)
        assertTrue(dao.saveCandidates(completed.runId, pending.requestId, "{}", true))
        assertFalse(dao.finalizeReady(completed.runId, pending.requestId,
            ReflectionBranch.SPENT, "Exact prepared reply.", afterPlaceholderDeadline))
        dao.beginSelectionWindow(completed.runId, afterPlaceholderDeadline + 6_000L)
        assertTrue(dao.finalizeReady(completed.runId, pending.requestId,
            ReflectionBranch.SPENT, "Exact prepared reply.", afterPlaceholderDeadline))
    }

    @Test
    fun effortHoldAllowsFirstEnergyAfterDeadlineWithoutReplacingAnExistingSelection() = runBlocking {
        val dao = createPending()
        val afterDeadline = pending.selectionDeadlineEpochMillis + 20_000L
        val chosen = dao.recordEnergy(completed.runId, ReflectionBranch.SPENT,
            afterDeadline, selectionHeld = true)
        assertEquals(ReflectionBranch.SPENT, chosen.selectedBranch)
        val later = dao.recordEnergy(completed.runId, ReflectionBranch.POWERED_UP,
            afterDeadline + 100L, selectionHeld = true)
        assertEquals(ReflectionBranch.SPENT, later.selectedBranch)
        assertEquals(ReflectionBranch.POWERED_UP, later.postRunEnergy)
    }

    @Test
    fun readyTextSurvivesReopenExactlyAndUnusedCandidatesAreDiscarded() = runBlocking {
        val dao = createPending()
        val text = "  Exact reply.\nSecond line\twith Unicode — 🌤  "
        dao.recordEnergy(completed.runId, ReflectionBranch.SPENT, pending.selectionDeadlineEpochMillis - 1)
        assertTrue(dao.saveCandidates(completed.runId, pending.requestId, "{\"spent\":\"candidate\"}", false))
        assertTrue(dao.finalizeReady(completed.runId, pending.requestId, ReflectionBranch.SPENT, text, pending.selectionDeadlineEpochMillis))
        database.close()
        database = buildRunStateDatabase(context, DATABASE_NAME)

        val restored = requireNotNull(database.reflectionDao().findByRunId(completed.runId))
        assertEquals(ReflectionStatus.READY, restored.status)
        assertEquals(text, restored.text)
        assertEquals(ReflectionBranch.SPENT, restored.selectedBranch)
        assertEquals(pending.evidenceJson, restored.evidenceJson)
        assertEquals(pending.selectionDeadlineEpochMillis, restored.finalizedAtEpochMillis)
        assertFalse(restored.isTestData)
        assertNull(restored.candidatesJson)
        assertEquals(1, database.runDao().countRuns())
    }

    @Test
    fun duplicateCreationCannotReplaceEvidenceDeadlineOrRequestIdentity() = runBlocking {
        val dao = createPending()
        assertEquals(pending, dao.ensurePending(pending.copy(
            requestId = retryId,
            evidenceJson = "{\"changed\":true}",
            selectionDeadlineEpochMillis = pending.selectionDeadlineEpochMillis + 60_000
        )))
        assertEquals(listOf(pending), dao.all())
    }

    @Test
    fun firstEnergyFreezesBranchAndLaterEnergyOnlyUpdatesFeedbackEvenAfterReady() = runBlocking {
        val dao = createPending()
        val deadline = pending.selectionDeadlineEpochMillis
        dao.recordEnergy(completed.runId, ReflectionBranch.SPENT, deadline - 100)
        val second = dao.recordEnergy(completed.runId, ReflectionBranch.POWERED_UP, deadline - 50)
        assertEquals(ReflectionBranch.SPENT, second.selectedBranch)
        assertEquals(ReflectionBranch.POWERED_UP, second.postRunEnergy)
        dao.saveCandidates(completed.runId, pending.requestId, "{}", true)
        dao.finalizeReady(completed.runId, pending.requestId, ReflectionBranch.SPENT, "Stored reply", deadline)
        val late = dao.recordEnergy(completed.runId, ReflectionBranch.FEELING_GOOD, deadline + 1)
        assertEquals(ReflectionBranch.SPENT, late.selectedBranch)
        assertEquals(ReflectionBranch.FEELING_GOOD, late.postRunEnergy)
        assertEquals("Stored reply", late.text)
        assertEquals(deadline, late.finalizedAtEpochMillis)
        assertFalse(dao.markFailed(completed.runId, pending.requestId))
        assertFalse(dao.saveCandidates(completed.runId, pending.requestId, "replacement", false))
        assertFalse(dao.finalizeReady(completed.runId, pending.requestId, ReflectionBranch.SPENT, "replacement", deadline + 5))
        assertNull(dao.retry(completed.runId, retryId))
    }

    @Test
    fun quietWindowExpiresAtDeadlineAndLateEnergyDoesNotChangeNoSelection() = runBlocking {
        val dao = createPending()
        val deadline = pending.selectionDeadlineEpochMillis
        assertNull(dao.selectNoSelectionIfDue(completed.runId, deadline - 1).selectedBranch)
        val chosen = dao.recordEnergy(completed.runId, ReflectionBranch.POWERED_UP, deadline)
        assertEquals(ReflectionBranch.NO_SELECTION, chosen.selectedBranch)
        assertEquals(ReflectionBranch.POWERED_UP, chosen.postRunEnergy)
        assertEquals(chosen, dao.selectNoSelectionIfDue(completed.runId, deadline + 1))
    }

    @Test
    fun failureCanStillAcceptSelectionAndRetryPreservesEvidenceAndRejectsOldCallbacks() = runBlocking {
        val dao = createPending()
        assertTrue(dao.markFailed(completed.runId, pending.requestId))
        dao.recordEnergy(completed.runId, ReflectionBranch.FEELING_GOOD, pending.selectionDeadlineEpochMillis - 1)
        val retried = requireNotNull(dao.retry(completed.runId, retryId))
        assertEquals(ReflectionStatus.PENDING, retried.status)
        assertEquals(ReflectionBranch.FEELING_GOOD, retried.selectedBranch)
        assertEquals(pending.evidenceJson, retried.evidenceJson)
        assertEquals(pending.selectionDeadlineEpochMillis, retried.selectionDeadlineEpochMillis)
        assertFalse(dao.saveCandidates(completed.runId, pending.requestId, "old", true))
        assertFalse(dao.markFailed(completed.runId, pending.requestId))
        assertFalse(dao.finalizeReady(completed.runId, pending.requestId, ReflectionBranch.FEELING_GOOD, "old", pending.selectionDeadlineEpochMillis))
        assertTrue(dao.saveCandidates(completed.runId, retryId, "new", true))
        assertFalse(dao.saveCandidates(completed.runId, retryId, "duplicate", true))
        assertFalse(dao.finalizeReady(completed.runId, retryId, ReflectionBranch.SPENT, "wrong branch", pending.selectionDeadlineEpochMillis))
        assertTrue(dao.finalizeReady(completed.runId, retryId, ReflectionBranch.FEELING_GOOD, "new reply", pending.selectionDeadlineEpochMillis))
        assertEquals(1, database.runDao().countRuns())
    }

    @Test
    fun failedUnselectedRecordCanExpireAndRetryRequiresNewIdentity() = runBlocking {
        val dao = createPending()
        dao.markFailed(completed.runId, pending.requestId)
        assertEquals(ReflectionBranch.NO_SELECTION,
            dao.selectNoSelectionIfDue(completed.runId, pending.selectionDeadlineEpochMillis).selectedBranch)
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { dao.retry(completed.runId, pending.requestId) }
        }
        assertEquals(ReflectionBranch.NO_SELECTION, dao.retry(completed.runId, retryId)?.selectedBranch)
    }

    @Test
    fun finalizationRequiresAcceptedCandidatesAndFrozenSelection() = runBlocking {
        val dao = createPending()
        assertFalse(dao.finalizeReady(completed.runId, pending.requestId, ReflectionBranch.NO_SELECTION, "reply", pending.selectionDeadlineEpochMillis))
        dao.selectNoSelectionIfDue(completed.runId, pending.selectionDeadlineEpochMillis)
        assertFalse(dao.finalizeReady(completed.runId, pending.requestId, ReflectionBranch.NO_SELECTION, "reply", pending.selectionDeadlineEpochMillis))
        dao.saveCandidates(completed.runId, pending.requestId, "{}", true)
        assertTrue(dao.finalizeReady(completed.runId, pending.requestId, ReflectionBranch.NO_SELECTION, "reply", pending.selectionDeadlineEpochMillis))
    }

    @Test
    fun missingOrUnfinishedParentsCannotReceiveReflectionsAndDeletesCascade(): Unit = runBlocking {
        val dao = database.reflectionDao()
        assertThrows(IllegalStateException::class.java) { runBlocking { dao.ensurePending(pending) } }
        database.runDao().insert(completed.copy(state = StoredRunState.RUNNING, finishEpochMillis = null))
        assertThrows(IllegalStateException::class.java) { runBlocking { dao.ensurePending(pending) } }
        database.openHelper.writableDatabase.execSQL("DELETE FROM runs")
        database.runDao().insert(completed)
        dao.ensurePending(pending)
        database.openHelper.writableDatabase.execSQL("DELETE FROM runs WHERE run_id = ?", arrayOf(completed.runId))
        assertNull(dao.findByRunId(completed.runId))
        assertTrue(dao.observeAll().first().isEmpty())
        assertThrows<SQLiteConstraintException>(SQLiteConstraintException::class.java) {
            database.openHelper.writableDatabase.execSQL(
                "INSERT INTO run_reflections (run_id, request_id, evidence_json, selection_deadline_epoch_millis, status) VALUES (?, ?, ?, ?, ?)",
                arrayOf<Any>(pending.runId, pending.requestId, pending.evidenceJson, pending.selectionDeadlineEpochMillis, "PENDING")
            )
        }
    }

    @Test
    fun completedHistoryExcludesActiveRunsAndHasStableNewestFirstOrder() = runBlocking {
        val earlier = completed.copy(runId = "03689ffc-65d9-4907-ae77-7a17b3ad77a6", officialStartEpochMillis = completed.officialStartEpochMillis - 1)
        val tied = completed.copy(runId = "8c405ab0-4a80-4be9-9bfb-19f2517ec2ba")
        val active = completed.copy(runId = "fd87fa03-dfbf-47ec-b721-4d41314e52f3", state = StoredRunState.RUNNING, finishEpochMillis = null)
        listOf(earlier, tied, active, completed).forEach { database.runDao().insert(it) }
        assertEquals(listOf(completed, tied, earlier), database.runDao().observeCompletedRuns().first())
    }

    private companion object {
        const val DATABASE_NAME = "runstate-reflection-test.db"
    }
}
