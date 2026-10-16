package com.runstate.mobile.run

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.runstate.mobile.data.local.DistanceUnit
import com.runstate.mobile.data.local.MetricSource
import com.runstate.mobile.data.local.MusicObservationKind
import com.runstate.mobile.data.local.MusicPlaybackState
import com.runstate.mobile.data.local.RunMusicObservation
import com.runstate.mobile.data.local.RunLocationObservation
import com.runstate.mobile.data.local.RunStateDatabase
import com.runstate.mobile.data.local.RunTrackingMode
import com.runstate.mobile.data.local.RunTransitionType
import com.runstate.mobile.data.local.StoredRunState
import com.runstate.mobile.data.local.TelemetryCoverage
import com.runstate.mobile.ui.RunUiModel
import com.runstate.mobile.ui.RunUiState
import com.runstate.mobile.ui.runUiModelFor
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Carries one run through the real coordinator and a real Room database, end to end.
 *
 * `ActiveRunSessionRoomTest` already proves the owner against Room, and the JVM coordinator
 * tests prove reservations against a fake. What only this shows is that the coordinator's
 * process-owned start and lifecycle actions land on one real row, in order, with the shared
 * clock's timestamps — and that the final answer it reports is the one the Saved screen needs.
 *
 * Deliberately one journey. Gesture timing and screen controls are Compose concerns and are
 * tested there, not here.
 */
@RunWith(AndroidJUnit4::class)
class RunSessionCoordinatorRoomTest {

    private companion object {
        const val DATABASE_NAME = "runstate-coordinator-test.db"
        const val OFFICIAL_START = 1_756_000_000_000L

        /** Pause at one minute, resume at two, pause at three, end at four. */
        const val FIRST_PAUSE = OFFICIAL_START + 60_000L
        const val RESUME = OFFICIAL_START + 120_000L
        const val SECOND_PAUSE = OFFICIAL_START + 180_000L
        const val FINISH = OFFICIAL_START + 240_000L

        const val RUN_ID = "0f6a2c1e-9d43-4b7a-9c21-7b5e8a4d1f30"
    }

    /** A clock the test moves before each request, standing in for the shared wall clock. */
    private class SettableClock(var nowMillis: Long) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = Instant.ofEpochMilli(nowMillis)
    }

    private lateinit var context: Context
    private lateinit var database: RunStateDatabase
    private lateinit var applicationScope: CoroutineScope

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()

        // Delete first so a leftover file from an earlier run cannot make this pass.
        context.deleteDatabase(DATABASE_NAME)
        database = Room.databaseBuilder(context, RunStateDatabase::class.java, DATABASE_NAME)
            .build()

        // A process-style scope, separate from the test's own coroutines.
        applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @After
    fun tearDown() {
        applicationScope.cancel()
        database.close()
        context.deleteDatabase(DATABASE_NAME)
    }

    /** Builds a coordinator over whichever database instance is current. */
    private fun coordinator(clock: SettableClock): RunSessionCoordinator =
        RunSessionCoordinator(
            runDao = database.runDao(),
            preparedRunFactory = PreparedRunFactory(
                clock = clock,
                zoneIdSupplier = { ZoneId.of("America/Chicago") },
                uuidSupplier = { UUID.fromString(RUN_ID) }
            ),
            applicationScope = applicationScope,
            locationDao = database.locationDao()
        )

    /**
     * Proves Start, Pause, Resume, Pause and Complete through the coordinator store one
     * completed run with ordered history, and report Saved.
     */
    @Test
    fun theCoordinatorCarriesOneRealRunFromStartToSaved() {

        // Arrange
        val dao = database.runDao()
        val clock = SettableClock(OFFICIAL_START)
        val coordinator = coordinator(clock)

        // Act: the whole journey, each durable step awaited through the coordinator.
        val finalSnapshot = runBlocking {
            coordinator.initialize()
            coordinator.beginCountdown()
            coordinator.requestStart().await()

            clock.nowMillis = FIRST_PAUSE
            coordinator.requestAction(RunActionKind.PAUSE).await()

            clock.nowMillis = RESUME
            coordinator.requestAction(RunActionKind.RESUME).await()

            clock.nowMillis = SECOND_PAUSE
            coordinator.requestAction(RunActionKind.PAUSE).await()

            clock.nowMillis = FINISH
            coordinator.requestAction(RunActionKind.COMPLETE).await()

            coordinator.journeySnapshot()
        }

        // Assert: exactly one run, completed, at the right moments.
        assertEquals(1, runBlocking { dao.countRuns() })
        val stored = runBlocking { dao.findById(RUN_ID) }
            ?: throw AssertionError("The run under test is not stored.")
        assertEquals(StoredRunState.COMPLETED, stored.state)
        assertEquals(OFFICIAL_START, stored.officialStartEpochMillis)
        assertEquals(FINISH, stored.finishEpochMillis)
        assertEquals(FINISH, stored.lastCheckpointEpochMillis)
        assertEquals(360.0, stored.finalDistanceMeters!!, 0.0)
        assertEquals(MetricSource.FIXTURE, stored.metricSource)
        assertEquals(TelemetryCoverage.COMPLETE, stored.telemetryCoverage)
        assertEquals(DistanceUnit.MILES, stored.displayDistanceUnit)
        assertEquals(true, stored.transitionHistoryComplete)

        // Assert: Pause, Resume, Pause — in order, once each, at the clock's readings.
        val history = runBlocking { dao.transitionsFor(RUN_ID) }
        assertEquals(
            listOf(RunTransitionType.PAUSE, RunTransitionType.RESUME, RunTransitionType.PAUSE),
            history.map { it.transitionType }
        )
        assertEquals(listOf(1, 2, 3), history.map { it.sequenceNumber })
        assertEquals(
            listOf(FIRST_PAUSE, RESUME, SECOND_PAUSE),
            history.map { it.occurredAtEpochMillis }
        )

        // Assert: the coordinator's final answer is the Saved screen.
        assertEquals(RunUiModel(RunUiState.Saved), runUiModelFor(finalSnapshot))
    }

    /**
     * Proves provenance survives a real reopen and still permits exact finalization.
     */
    @Test
    fun recoveredVersionThreeRunFinalizesTheSameFixtureDistanceAfterReopen() {
        val clock = SettableClock(OFFICIAL_START)
        val firstCoordinator = coordinator(clock)

        runBlocking {
            firstCoordinator.initialize()
            firstCoordinator.beginCountdown()
            firstCoordinator.requestStart().await()
            clock.nowMillis = FIRST_PAUSE
            firstCoordinator.requestAction(RunActionKind.PAUSE).await()
        }

        applicationScope.cancel()
        database.close()
        database = Room.databaseBuilder(context, RunStateDatabase::class.java, DATABASE_NAME)
            .build()
        applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        clock.nowMillis = FINISH
        val recoveredCoordinator = coordinator(clock)
        val recoveredMetrics = runBlocking {
            recoveredCoordinator.initialize()
            val metrics = recoveredCoordinator.runMetrics()
                ?: throw AssertionError("The recovered run did not expose metrics.")
            recoveredCoordinator.requestAction(RunActionKind.COMPLETE).await()
            metrics
        }

        // The reopened process rebuilds honest live values from the stored row and history.
        assertEquals(240_000L, recoveredMetrics.elapsedMillis)
        assertEquals(60_000L, recoveredMetrics.activeMillis)
        assertEquals(180.0, recoveredMetrics.distanceMeters!!, 0.0)
        assertEquals(1.0 / 3.0, recoveredMetrics.paceSecondsPerMeter!!, 0.0)
        assertEquals(MetricSource.FIXTURE, recoveredMetrics.source)
        assertEquals(TelemetryCoverage.COMPLETE, recoveredMetrics.coverage)
        assertEquals(DistanceUnit.MILES, recoveredMetrics.displayUnit)

        val restored = runBlocking { database.runDao().findById(RUN_ID) }
            ?: throw AssertionError("The recovered run is not stored.")
        assertEquals(StoredRunState.COMPLETED, restored.state)
        assertEquals(180.0, restored.finalDistanceMeters!!, 0.0)
        assertEquals(MetricSource.FIXTURE, restored.metricSource)
        assertEquals(TelemetryCoverage.COMPLETE, restored.telemetryCoverage)
        assertEquals(true, restored.transitionHistoryComplete)
    }

    @Test
    fun gpsRunUsesOnlyDurableLocationDistanceAtCompletion() {
        val clock = SettableClock(OFFICIAL_START)
        val coordinator = coordinator(clock)
        val locationDao = database.locationDao()

        runBlocking {
            coordinator.initialize()
            coordinator.beginCountdown()
            coordinator.requestStart(trackingMode = RunTrackingMode.GPS).await()
            locationDao.recordObservation(gpsObservation("anchor", OFFICIAL_START + 10_000L))
            locationDao.recordObservation(
                gpsObservation("segment", OFFICIAL_START + 20_000L, segmentMeters = 100.0)
            )

            val live = requireNotNull(coordinator.runMetrics())
            assertEquals(MetricSource.GPS, live.source)
            assertEquals(100.0, live.distanceMeters!!, 0.0)
            assertEquals(TelemetryCoverage.PARTIAL, live.coverage)

            clock.nowMillis = FIRST_PAUSE
            coordinator.requestAction(RunActionKind.PAUSE).await()
            clock.nowMillis = FINISH
            coordinator.requestAction(RunActionKind.COMPLETE).await()
        }

        val stored = runBlocking { requireNotNull(database.runDao().findById(RUN_ID)) }
        assertEquals(RunTrackingMode.GPS, stored.trackingMode)
        assertEquals(MetricSource.GPS, stored.metricSource)
        assertEquals(TelemetryCoverage.PARTIAL, stored.telemetryCoverage)
        assertEquals(100.0, stored.finalDistanceMeters!!, 0.0)
    }

    @Test
    fun gpsRunWithoutMeasuredSegmentDoesNotUseFixtureDistance() {
        val clock = SettableClock(OFFICIAL_START)
        val coordinator = coordinator(clock)

        runBlocking {
            coordinator.initialize()
            coordinator.beginCountdown()
            coordinator.requestStart(trackingMode = RunTrackingMode.GPS).await()
            database.locationDao().recordObservation(
                gpsObservation("anchor", OFFICIAL_START + 10_000L)
            )
            clock.nowMillis = FIRST_PAUSE
            coordinator.requestAction(RunActionKind.PAUSE).await()
            clock.nowMillis = FINISH
            coordinator.requestAction(RunActionKind.COMPLETE).await()
        }

        val stored = runBlocking { requireNotNull(database.runDao().findById(RUN_ID)) }
        assertEquals(MetricSource.GPS, stored.metricSource)
        assertEquals(TelemetryCoverage.UNAVAILABLE, stored.telemetryCoverage)
        assertEquals(null, stored.finalDistanceMeters)
    }

    @Test
    fun recoveredRunningGpsRunKeepsDistanceButDoesNotInventActiveTime() {
        val clock = SettableClock(OFFICIAL_START)
        runBlocking {
            val firstProcess = coordinator(clock)
            firstProcess.initialize()
            firstProcess.beginCountdown()
            firstProcess.requestStart(trackingMode = RunTrackingMode.GPS).await()
            database.locationDao().recordObservation(
                gpsObservation("anchor", OFFICIAL_START + 10_000L)
            )
            database.locationDao().recordObservation(
                gpsObservation("segment", OFFICIAL_START + 20_000L, segmentMeters = 100.0)
            )

            // A new coordinator models a restarted process: Room remembers the distance,
            // but no service could have observed the entire intervening active interval.
            clock.nowMillis = OFFICIAL_START + 100_000L
            val recovered = coordinator(clock)
            assertEquals(RUN_ID,
                (recovered.initialize().admission as RunAdmission.RunInProgress).session.runId)
            val metrics = requireNotNull(recovered.runMetrics())
            assertEquals(null, metrics.activeMillis)
            assertEquals(100.0, metrics.distanceMeters!!, 0.0)
            assertEquals(TelemetryCoverage.PARTIAL, metrics.coverage)
        }
    }

    private fun gpsObservation(
        key: String,
        observedAt: Long,
        segmentMeters: Double? = null
    ) = RunLocationObservation(
        runId = RUN_ID,
        observationKey = key,
        observedAtEpochMillis = observedAt,
        elapsedRealtimeNanos = (observedAt - OFFICIAL_START) * 1_000_000L,
        latitude = 29.72,
        longitude = -95.39,
        horizontalAccuracyMeters = 5f,
        altitudeMeters = null,
        speedMetersPerSecond = null,
        bearingDegrees = null,
        acceptedForDistance = true,
        segmentDistanceMeters = segmentMeters
    )

    @Test
    fun explicitShortDiscardCascadesOnlyItsOwnChildrenAndLeavesHistoryUnchanged() = runBlocking {
        val clock = SettableClock(OFFICIAL_START)
        val coordinator = coordinator(clock)
        val dao = database.runDao()
        coordinator.initialize()
        coordinator.beginCountdown()
        coordinator.requestStart(trackingMode = RunTrackingMode.GPS, musicObservationRequested = true).await()
        database.locationDao().recordObservation(gpsObservation("short-anchor", OFFICIAL_START + 1_000L))
        database.musicObservationDao().recordObservation(RunMusicObservation(
            runId = RUN_ID,
            observationKey = "short-music",
            observedAtEpochMillis = OFFICIAL_START + 1_000L,
            elapsedRealtimeNanos = 1_000_000_000L,
            sourcePackage = "test.music",
            title = "Test track",
            artist = "Test artist",
            playbackState = MusicPlaybackState.PLAYING,
            eventKind = MusicObservationKind.SESSION_SNAPSHOT
        ))
        clock.nowMillis += 5_000L
        coordinator.requestAction(RunActionKind.PAUSE).await()
        val savedId = "c4e1b8a2-7d35-4f61-8b0c-2a9e6d4f13b7"
        val saved = requireNotNull(dao.findById(RUN_ID)).copy(
            runId = savedId,
            state = StoredRunState.COMPLETED,
            finishEpochMillis = clock.nowMillis
        )
        dao.insert(saved)
        assertFalse(dao.isShortPausedRunDiscardEligible(savedId))
        assertTrue(runCatching { dao.discardShortPausedRun(savedId) }.isFailure)

        // Even an unexpected child left by another writer must obey its existing FK.
        database.openHelper.writableDatabase.execSQL(
            "INSERT INTO run_reflections (run_id, request_id, evidence_json, selection_deadline_epoch_millis, status, is_test_data, selection_window_started) VALUES (?, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any?>(RUN_ID, UUID.randomUUID().toString(), "{}", clock.nowMillis, "PENDING", 1, 1)
        )
        assertTrue(coordinator.canDiscardShortRun(RUN_ID))
        coordinator.requestDiscard(RUN_ID).await()
        assertNull(dao.findById(RUN_ID))
        assertTrue(dao.transitionsFor(RUN_ID).isEmpty())
        assertTrue(database.locationDao().pointsFor(RUN_ID).isEmpty())
        assertTrue(database.musicObservationDao().observationsFor(RUN_ID).isEmpty())
        assertNull(database.reflectionDao().findByRunId(RUN_ID))
        assertEquals(saved, dao.findById(savedId))
        assertEquals(1, dao.countRuns())
        assertEquals(RunUiModel(RunUiState.ReadyToStart), runUiModelFor(coordinator.journeySnapshot()))
    }

    @Test
    fun databaseDeleteFailureKeepsThePausedRunAndItsChildrenUntilRetrySucceeds() = runBlocking {
        val clock = SettableClock(OFFICIAL_START)
        val coordinator = coordinator(clock)
        coordinator.initialize()
        coordinator.beginCountdown()
        coordinator.requestStart(trackingMode = RunTrackingMode.GPS).await()
        database.locationDao().recordObservation(gpsObservation("kept-anchor", OFFICIAL_START + 1_000L))
        clock.nowMillis += 5_000L
        coordinator.requestAction(RunActionKind.PAUSE).await()
        val paused = database.runDao().findById(RUN_ID)
        val transitions = database.runDao().transitionsFor(RUN_ID)
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER block_test_discard BEFORE DELETE ON runs BEGIN SELECT RAISE(ABORT, 'test storage failure'); END"
        )
        assertTrue(runCatching { coordinator.requestDiscard(RUN_ID).await() }.isFailure)
        assertEquals(paused, database.runDao().findById(RUN_ID))
        assertEquals(transitions, database.runDao().transitionsFor(RUN_ID))
        assertEquals(1, database.locationDao().pointsFor(RUN_ID).size)
        assertEquals(RunActionKind.DISCARD, coordinator.journeySnapshot().lastActionFailed)
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER block_test_discard")
        coordinator.requestDiscard(RUN_ID).await()
        assertNull(database.runDao().findById(RUN_ID))
        assertTrue(database.locationDao().pointsFor(RUN_ID).isEmpty())
    }

    @Test
    fun shortDiscardRefusesLongCompletedAndIncompleteRowsAndSerializesAgainstResume() = runBlocking {
        val clock = SettableClock(OFFICIAL_START)
        val coordinator = coordinator(clock)
        val dao = database.runDao()
        coordinator.initialize()
        coordinator.beginCountdown()
        coordinator.requestStart().await()
        clock.nowMillis += 30_000L
        coordinator.requestAction(RunActionKind.PAUSE).await()
        assertFalse(dao.isShortPausedRunDiscardEligible(RUN_ID))
        assertTrue(runCatching { dao.discardShortPausedRun(RUN_ID) }.isFailure)
        coordinator.requestAction(RunActionKind.COMPLETE).await()
        val saved = dao.findById(RUN_ID)
        assertTrue(runCatching { dao.discardShortPausedRun(RUN_ID) }.isFailure)
        assertEquals(saved, dao.findById(RUN_ID))

        val shortId = "c4e1b8a2-7d35-4f61-8b0c-2a9e6d4f13b7"
        val fresh = requireNotNull(saved).copy(
            runId = shortId,
            state = StoredRunState.RUNNING,
            finishEpochMillis = null,
            lastCheckpointEpochMillis = OFFICIAL_START,
            finalDistanceMeters = null,
            metricSource = null,
            telemetryCoverage = null,
            displayDistanceUnit = null
        )
        dao.insert(fresh)
        dao.pauseRun(shortId, OFFICIAL_START + 5_000L)
        val discard = async(Dispatchers.Default) { runCatching { dao.discardShortPausedRun(shortId) } }
        val resume = async(Dispatchers.Default) { runCatching { dao.resumeRun(shortId, OFFICIAL_START + 6_000L) } }
        assertEquals(1, listOf(discard.await(), resume.await()).count { it.isSuccess })
        val surviving = dao.findById(shortId)
        assertTrue(surviving == null || surviving.state == StoredRunState.RUNNING)
        assertEquals(saved, dao.findById(RUN_ID))

        val legacyId = "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d"
        dao.insert(fresh.copy(runId = legacyId, transitionHistoryComplete = null))
        dao.pauseRun(legacyId, OFFICIAL_START + 5_000L)
        assertFalse(dao.isShortPausedRunDiscardEligible(legacyId))
        assertTrue(runCatching { dao.discardShortPausedRun(legacyId) }.isFailure)
        assertEquals(StoredRunState.PAUSED, dao.findById(legacyId)?.state)
    }
}
