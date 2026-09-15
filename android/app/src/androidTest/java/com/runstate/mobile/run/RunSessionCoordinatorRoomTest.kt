package com.runstate.mobile.run

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.runstate.mobile.data.local.DistanceUnit
import com.runstate.mobile.data.local.MetricSource
import com.runstate.mobile.data.local.RunStateDatabase
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
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
            applicationScope = applicationScope
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
        runBlocking {
            recoveredCoordinator.initialize()
            recoveredCoordinator.requestAction(RunActionKind.COMPLETE).await()
        }

        val restored = runBlocking { database.runDao().findById(RUN_ID) }
            ?: throw AssertionError("The recovered run is not stored.")
        assertEquals(StoredRunState.COMPLETED, restored.state)
        assertEquals(180.0, restored.finalDistanceMeters!!, 0.0)
        assertEquals(MetricSource.FIXTURE, restored.metricSource)
        assertEquals(TelemetryCoverage.COMPLETE, restored.telemetryCoverage)
        assertEquals(true, restored.transitionHistoryComplete)
    }
}
