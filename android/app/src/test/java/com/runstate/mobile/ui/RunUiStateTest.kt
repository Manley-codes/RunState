package com.runstate.mobile.ui

import com.runstate.mobile.data.local.FakeRunDao
import com.runstate.mobile.data.local.RunEntity
import com.runstate.mobile.data.local.StoredRunState
import com.runstate.mobile.run.ActiveRunSession
import com.runstate.mobile.run.PreparedRunFactory
import com.runstate.mobile.run.RunAdmission
import com.runstate.mobile.run.RunSessionCoordinator
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Checks the translation from what the coordinator knows to what the screen shows.
 *
 * The mapping is pure, so all seven screens can be decided here on the JVM rather than on
 * an emulator. What is being checked is the translation itself — that one coordinator
 * snapshot picks exactly one screen, and that nothing about a run leaks through it.
 *
 * The inputs are built through a real [RunSessionCoordinator] over [FakeRunDao] rather
 * than hand-assembled. A hand-built [RunAdmission.RunInProgress] would need an
 * [ActiveRunSession] in a state chosen by the test, which is how a mapping test ends up
 * proving something the coordinator would never actually produce.
 */
class RunUiStateTest {

    private companion object {
        const val OFFICIAL_START = 1_756_000_000_000L
        const val FIRST_PAUSE = OFFICIAL_START + 60_000L
        const val SECOND_START = OFFICIAL_START + 600_000L

        const val RUN_ID = "0f6a2c1e-9d43-4b7a-9c21-7b5e8a4d1f30"
        const val SECOND_RUN_ID = "c4e1b8a2-7d35-4f61-8b0c-2a9e6d4f13b7"
    }

    /** The row a real countdown hands over: RUNNING, checkpoint at the official start. */
    private fun preparedRun(
        runId: String = RUN_ID,
        officialStart: Long = OFFICIAL_START
    ): RunEntity = RunEntity(
        runId = runId,
        state = StoredRunState.RUNNING,
        officialStartEpochMillis = officialStart,
        startTimezoneId = "America/Chicago",
        lastCheckpointEpochMillis = officialStart
    )

    /** A coordinator whose official start is fixed to [preparedRun]'s identity and time. */
    private fun coordinatorOver(dao: FakeRunDao = FakeRunDao()) = RunSessionCoordinator(
        runDao = dao,
        preparedRunFactory = PreparedRunFactory(
            clock = Clock.fixed(Instant.ofEpochMilli(OFFICIAL_START), ZoneOffset.UTC),
            zoneIdSupplier = { ZoneId.of("America/Chicago") },
            uuidSupplier = { UUID.fromString(RUN_ID) }
        ),
        applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    )

    /** Maps whatever the coordinator currently reports, the way first load does. */
    private fun uiStateOf(coordinator: RunSessionCoordinator): RunUiState = runBlocking {
        runUiStateFor(coordinator.initialize())
    }

    /**
     * Proves an unstarted attempt shows the waiting screen rather than a failure.
     */
    @Test
    fun `not attempted maps to initializing`() {

        // Arrange: a coordinator nobody has initialized.
        val coordinator = coordinatorOver()

        // Act and Assert: the snapshot is read without any initialize call.
        assertEquals(
            RunUiState.Initializing,
            runUiStateFor(runBlocking { coordinator.journeySnapshot() })
        )
    }

    /**
     * Proves a failed attempt is a different screen from an unfinished one.
     *
     * Admission reports `NotInitialized` in both cases, so the status is the only thing
     * that separates a spinner from a retry button.
     */
    @Test
    fun `failed initialization maps to the failure screen`() {

        // Arrange: discovery cannot read storage.
        val dao = FakeRunDao()
        dao.failDiscoveryWith = IllegalStateException("disk unavailable")
        val coordinator = coordinatorOver(dao)

        // Act and Assert
        assertEquals(RunUiState.InitializationFailed, uiStateOf(coordinator))
    }

    /**
     * Proves an empty database maps to the screen that offers Start.
     */
    @Test
    fun `completed with nothing to recover maps to ready to start`() {

        // Arrange
        val coordinator = coordinatorOver()

        // Act and Assert
        assertEquals(RunUiState.ReadyToStart, uiStateOf(coordinator))
    }

    /**
     * Proves an open countdown maps to the countdown screen.
     */
    @Test
    fun `completed with a countdown underway maps to countdown`() {

        // Arrange
        val coordinator = coordinatorOver()
        runBlocking {
            coordinator.initialize()
            coordinator.beginCountdown()
        }

        // Act and Assert
        assertEquals(
            RunUiState.Countdown,
            runUiStateFor(runBlocking { coordinator.journeySnapshot() })
        )
    }

    /**
     * Proves an official start still underway keeps the countdown screen.
     *
     * The run is not owned yet, so nothing may claim it is running — and nothing may offer
     * Start either. The conservative screen is the one the start began from.
     */
    @Test
    fun `a start still underway maps to countdown`() {

        // Arrange: a countdown whose insert parks.
        val dao = FakeRunDao()
        val releaseInsert = CompletableDeferred<Unit>()
        dao.duringInsert = { releaseInsert.await() }
        val coordinator = coordinatorOver(dao)

        runBlocking {
            coordinator.initialize()
            coordinator.beginCountdown()
            val attempt = coordinator.requestStart()

            // Act and Assert: mid-insert.
            assertEquals(RunUiState.Countdown, runUiStateFor(coordinator.journeySnapshot()))

            // Act and Assert: once published, the same run is shown as running.
            releaseInsert.complete(Unit)
            attempt.await()
            assertEquals(RunUiState.ActiveRunning, runUiStateFor(coordinator.journeySnapshot()))
        }
    }

    /**
     * Proves a live run maps to the running screen.
     *
     * The run here is adopted from storage rather than started in this process, and the
     * screen is the same either way — which is why the state is named for what is true
     * now rather than for where the run came from.
     */
    @Test
    fun `completed with a running run maps to active running`() {

        // Arrange: one unfinished run on disk, adopted at initialization.
        val dao = FakeRunDao()
        runBlocking { dao.insert(preparedRun()) }
        val coordinator = coordinatorOver(dao)

        // Act and Assert
        assertEquals(RunUiState.ActiveRunning, uiStateOf(coordinator))
    }

    /**
     * Proves a paused run maps to its own screen rather than to the running one.
     */
    @Test
    fun `completed with a paused run maps to active paused`() {

        // Arrange: a run started here and then genuinely paused.
        val coordinator = coordinatorOver()
        runBlocking {
            coordinator.initialize()
            coordinator.beginCountdown()
            coordinator.requestStart().await().pause(FIRST_PAUSE)
        }

        // Act and Assert
        assertEquals(RunUiState.ActivePaused, uiStateOf(coordinator))
    }

    /**
     * Proves a blocked database maps to the withheld screen, not to a startable one.
     */
    @Test
    fun `completed but blocked maps to storage inconsistent`() {

        // Arrange: two unfinished runs, so the coordinator refuses to interpret storage.
        val dao = FakeRunDao()
        runBlocking {
            dao.insert(preparedRun())
            dao.insert(preparedRun(SECOND_RUN_ID, SECOND_START))
        }
        val coordinator = coordinatorOver(dao)

        // Act and Assert
        assertEquals(RunUiState.StorageInconsistent, uiStateOf(coordinator))
    }
}
