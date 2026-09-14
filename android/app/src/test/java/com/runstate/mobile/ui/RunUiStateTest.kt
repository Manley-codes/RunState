package com.runstate.mobile.ui

import com.runstate.mobile.data.local.FakeRunDao
import com.runstate.mobile.data.local.RunEntity
import com.runstate.mobile.data.local.StoredRunState
import com.runstate.mobile.run.ActiveRunSession
import com.runstate.mobile.run.InitializationStatus
import com.runstate.mobile.run.PreparedRunFactory
import com.runstate.mobile.run.RunActionKind
import com.runstate.mobile.run.RunAdmission
import com.runstate.mobile.run.RunJourneySnapshot
import com.runstate.mobile.run.RunSessionCoordinator
import com.runstate.mobile.run.RunSessionState
import com.runstate.mobile.run.StartAttemptPhase
import java.lang.reflect.Modifier
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
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Checks the translation from what the coordinator knows to what the screen shows.
 *
 * The mapping is pure, so every screen can be decided here on the JVM rather than on an
 * emulator. What is being checked is the translation itself — that one coordinator snapshot
 * picks exactly one model, and that nothing about a run leaks through it.
 *
 * Inputs are built through a real [RunSessionCoordinator] over [FakeRunDao] wherever the
 * coordinator can reach the situation deterministically. A hand-built snapshot is used only
 * for moments a real coordinator cannot be paused inside — a session that has already
 * advanced in memory while its reservation is still held — and for combinations that must
 * never occur, which is exactly what a real coordinator cannot be made to produce.
 */
class RunUiStateTest {

    private companion object {
        const val OFFICIAL_START = 1_756_000_000_000L
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
    private fun modelOf(coordinator: RunSessionCoordinator): RunUiModel = runBlocking {
        runUiModelFor(coordinator.initialize())
    }

    /** Maps a snapshot read without recovery. */
    private fun currentModel(coordinator: RunSessionCoordinator): RunUiModel = runBlocking {
        runUiModelFor(coordinator.journeySnapshot())
    }

    /** A coordinator holding one live run it started, and that run's owner. */
    private fun startedRun(dao: FakeRunDao = FakeRunDao()): Pair<RunSessionCoordinator, ActiveRunSession> {
        val coordinator = coordinatorOver(dao)
        val session = runBlocking {
            coordinator.initialize()
            coordinator.beginCountdown()
            coordinator.requestStart().await()
        }
        return coordinator to session
    }

    // ---------------------------------------------------------------------------------
    // Ordinary screens
    // ---------------------------------------------------------------------------------

    /**
     * Proves an unstarted attempt shows the waiting screen rather than a failure.
     */
    @Test
    fun `not attempted maps to initializing`() {

        // Arrange: a coordinator nobody has initialized.
        val coordinator = coordinatorOver()

        // Act and Assert: the snapshot is read without any initialize call.
        assertEquals(RunUiModel(RunUiState.Initializing), currentModel(coordinator))
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
        assertEquals(RunUiModel(RunUiState.InitializationFailed), modelOf(coordinator))
    }

    /**
     * Proves an empty database maps to the screen that offers Start.
     */
    @Test
    fun `completed with nothing to recover maps to ready to start`() {
        assertEquals(RunUiModel(RunUiState.ReadyToStart), modelOf(coordinatorOver()))
    }

    /**
     * Proves an open countdown with no start attempt maps to the countdown screen.
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
        assertEquals(RunUiModel(RunUiState.Countdown), currentModel(coordinator))
    }

    /**
     * Proves an official start still underway shows that it is starting, with no controls.
     *
     * The run is not owned yet, so nothing may claim it is running — and nothing may offer
     * Cancel either, because the insert may already have landed.
     */
    @Test
    fun `a start still underway maps to starting run`() {

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
            assertEquals(
                RunUiModel(RunUiState.StartingRun),
                runUiModelFor(coordinator.journeySnapshot())
            )

            // Act and Assert: once published, the same run is shown as running.
            releaseInsert.complete(Unit)
            attempt.await()
            assertEquals(
                RunUiModel(RunUiState.ActiveRunning),
                runUiModelFor(coordinator.journeySnapshot())
            )
        }
    }

    /**
     * Proves a failed start maps to its own retry screen rather than back to the countdown.
     */
    @Test
    fun `a failed start maps to start failed`() {

        // Arrange
        val dao = FakeRunDao()
        dao.failWith = IllegalStateException("disk full")
        val coordinator = coordinatorOver(dao)
        runBlocking {
            coordinator.initialize()
            coordinator.beginCountdown()
            coordinator.requestStart()
        }

        // Act and Assert
        assertEquals(RunUiModel(RunUiState.StartFailed), currentModel(coordinator))
    }

    /**
     * Proves a live run maps to the running screen.
     *
     * The run here is adopted from storage rather than started in this process, and the
     * screen is the same either way.
     */
    @Test
    fun `completed with a running run maps to active running`() {

        // Arrange: one unfinished run on disk, adopted at initialization.
        val dao = FakeRunDao()
        runBlocking { dao.insert(preparedRun()) }

        // Act and Assert
        assertEquals(RunUiModel(RunUiState.ActiveRunning), modelOf(coordinatorOver(dao)))
    }

    /**
     * Proves a paused run maps to its own screen rather than to the running one.
     */
    @Test
    fun `completed with a paused run maps to active paused`() {

        // Arrange: a run started here and then genuinely paused.
        val (coordinator, _) = startedRun()
        runBlocking { coordinator.requestAction(RunActionKind.PAUSE).await() }

        // Act and Assert
        assertEquals(RunUiModel(RunUiState.ActivePaused), modelOf(coordinator))
    }

    /**
     * Proves a durably completed run maps to Saved.
     */
    @Test
    fun `a completed run with no reservation maps to saved`() {

        // Arrange
        val (coordinator, _) = startedRun()
        runBlocking {
            coordinator.requestAction(RunActionKind.PAUSE).await()
            coordinator.requestAction(RunActionKind.COMPLETE).await()
        }

        // Act and Assert
        assertEquals(RunUiModel(RunUiState.Saved), currentModel(coordinator))
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

        // Act and Assert
        assertEquals(RunUiModel(RunUiState.StorageInconsistent), modelOf(coordinatorOver(dao)))
    }

    // ---------------------------------------------------------------------------------
    // Conservative action reporting
    // ---------------------------------------------------------------------------------

    /**
     * Proves a pause still writing remains visibly Running, with controls busy.
     */
    @Test
    fun `a pending pause remains visibly running`() {

        // Arrange: a live run whose pause write parks.
        val dao = FakeRunDao()
        val (coordinator, _) = startedRun(dao)
        val releaseWrite = CompletableDeferred<Unit>()
        dao.duringStateChange = { releaseWrite.await() }

        runBlocking {
            val pause = coordinator.requestAction(RunActionKind.PAUSE)

            // Act and Assert: mid-write.
            assertEquals(
                RunUiModel(RunUiState.ActiveRunning, actionInProgress = true),
                runUiModelFor(coordinator.journeySnapshot())
            )

            // Act and Assert: only once saved is it paused.
            releaseWrite.complete(Unit)
            pause.await()
            assertEquals(
                RunUiModel(RunUiState.ActivePaused),
                runUiModelFor(coordinator.journeySnapshot())
            )
        }
    }

    /**
     * Proves a resume still writing remains visibly Paused, with controls busy.
     */
    @Test
    fun `a pending resume remains visibly paused`() {

        // Arrange: a paused run whose resume write parks.
        val dao = FakeRunDao()
        val (coordinator, _) = startedRun(dao)
        runBlocking { coordinator.requestAction(RunActionKind.PAUSE).await() }
        val releaseWrite = CompletableDeferred<Unit>()
        dao.duringStateChange = { releaseWrite.await() }

        runBlocking {
            coordinator.requestAction(RunActionKind.RESUME)

            // Act and Assert
            assertEquals(
                RunUiModel(RunUiState.ActivePaused, actionInProgress = true),
                runUiModelFor(coordinator.journeySnapshot())
            )
            releaseWrite.complete(Unit)
        }
    }

    /**
     * Proves a completion still writing remains visibly Paused rather than Saved.
     */
    @Test
    fun `a pending completion remains visibly paused rather than saved`() {

        // Arrange: a paused run whose completion write parks.
        val dao = FakeRunDao()
        val (coordinator, _) = startedRun(dao)
        runBlocking { coordinator.requestAction(RunActionKind.PAUSE).await() }
        val releaseWrite = CompletableDeferred<Unit>()
        dao.duringCompletion = { releaseWrite.await() }

        runBlocking {
            val complete = coordinator.requestAction(RunActionKind.COMPLETE)

            // Act and Assert: mid-write.
            assertEquals(
                RunUiModel(RunUiState.ActivePaused, actionInProgress = true),
                runUiModelFor(coordinator.journeySnapshot())
            )

            // Act and Assert: Saved only after the write and its release.
            releaseWrite.complete(Unit)
            complete.await()
            assertEquals(
                RunUiModel(RunUiState.Saved),
                runUiModelFor(coordinator.journeySnapshot())
            )
        }
    }

    /**
     * Proves the pre-action state is shown even when the session has already advanced.
     *
     * This is the moment between the session's successful write and the coordinator applying
     * its result. A real coordinator cannot be parked inside that gap, so the snapshot is
     * built by hand around real owners genuinely in the advanced state.
     */
    @Test
    fun `a reservation shows the pre-action state even after memory has advanced`() {

        // Arrange: a genuinely completed owner, and a genuinely paused one.
        val (completedCoordinator, completed) = startedRun()
        runBlocking {
            completedCoordinator.requestAction(RunActionKind.PAUSE).await()
            completedCoordinator.requestAction(RunActionKind.COMPLETE).await()
        }
        val (pausedCoordinator, paused) = startedRun()
        runBlocking { pausedCoordinator.requestAction(RunActionKind.PAUSE).await() }
        val (_, running) = startedRun()

        // Act and Assert: completion reserved, memory already COMPLETED → still Paused.
        assertEquals(
            RunUiModel(RunUiState.ActivePaused, actionInProgress = true),
            runUiModelFor(inProgress(completed, reserved = RunActionKind.COMPLETE))
        )

        // Act and Assert: pause reserved, memory already PAUSED → still Running.
        assertEquals(
            RunUiModel(RunUiState.ActiveRunning, actionInProgress = true),
            runUiModelFor(inProgress(paused, reserved = RunActionKind.PAUSE))
        )

        // Act and Assert: resume reserved, memory already RUNNING → still Paused.
        assertEquals(
            RunUiModel(RunUiState.ActivePaused, actionInProgress = true),
            runUiModelFor(inProgress(running, reserved = RunActionKind.RESUME))
        )
    }

    /**
     * Proves a refused write shows the surviving state with the failure flagged.
     */
    @Test
    fun `a failed action shows the surviving state with the failure`() {

        // Arrange: a live run whose pause storage refuses.
        val dao = FakeRunDao()
        val (coordinator, _) = startedRun(dao)
        dao.failStateChangeWith = IllegalStateException("disk full")

        // Act
        runBlocking { coordinator.requestAction(RunActionKind.PAUSE) }

        // Assert: still running, not busy, and the retry message is owed.
        assertEquals(
            RunUiModel(RunUiState.ActiveRunning, actionFailed = true),
            currentModel(coordinator)
        )
    }

    /**
     * Proves a retry in flight after a failure is shown as busy, not as the old failure.
     */
    @Test
    fun `a pending retry after a failure shows busy rather than failed`() {

        // Arrange: one refused pause, then a retry that parks.
        val dao = FakeRunDao()
        val (coordinator, _) = startedRun(dao)
        dao.failStateChangeWith = IllegalStateException("disk full")
        runBlocking { coordinator.requestAction(RunActionKind.PAUSE) }
        dao.failStateChangeWith = null
        val releaseWrite = CompletableDeferred<Unit>()
        dao.duringStateChange = { releaseWrite.await() }

        runBlocking {
            coordinator.requestAction(RunActionKind.PAUSE)

            // Act and Assert
            assertEquals(
                RunUiModel(RunUiState.ActiveRunning, actionInProgress = true),
                runUiModelFor(coordinator.journeySnapshot())
            )
            releaseWrite.complete(Unit)
        }
    }

    // ---------------------------------------------------------------------------------
    // Impossible combinations and leakage
    // ---------------------------------------------------------------------------------

    /** A hand-built snapshot of a completed recovery holding [session] in progress. */
    private fun inProgress(
        session: ActiveRunSession,
        reserved: RunActionKind? = null,
        failed: RunActionKind? = null
    ) = RunJourneySnapshot(
        initializationStatus = InitializationStatus.Completed,
        admission = RunAdmission.RunInProgress(session),
        reservedAction = reserved,
        lastActionFailed = failed
    )

    /**
     * Proves snapshots the coordinator cannot produce are raised instead of drawn.
     */
    @Test
    fun `impossible snapshot combinations throw`() {

        // Arrange: real owners in each state.
        val (_, running) = startedRun()
        val (pausedCoordinator, paused) = startedRun()
        runBlocking { pausedCoordinator.requestAction(RunActionKind.PAUSE).await() }
        val (completedCoordinator, completed) = startedRun()
        runBlocking {
            completedCoordinator.requestAction(RunActionKind.PAUSE).await()
            completedCoordinator.requestAction(RunActionKind.COMPLETE).await()
        }
        val done = InitializationStatus.Completed

        val impossible = listOf(

            // Start progress outside a countdown.
            RunJourneySnapshot(
                InitializationStatus.NotAttempted,
                RunAdmission.NotInitialized,
                StartAttemptPhase.InProgress
            ),
            RunJourneySnapshot(done, RunAdmission.ReadyForCountdown, StartAttemptPhase.Failed),
            RunJourneySnapshot(
                done,
                RunAdmission.RunInProgress(running),
                StartAttemptPhase.InProgress
            ),

            // Action reporting without a run in progress.
            RunJourneySnapshot(
                done,
                RunAdmission.CountdownInProgress,
                reservedAction = RunActionKind.PAUSE
            ),
            RunJourneySnapshot(
                done,
                RunAdmission.RunCompleted(completed),
                reservedAction = RunActionKind.COMPLETE
            ),
            RunJourneySnapshot(
                done,
                RunAdmission.RunCompleted(completed),
                lastActionFailed = RunActionKind.PAUSE
            ),

            // A reservation beside a session state it could never produce.
            inProgress(running, reserved = RunActionKind.COMPLETE),
            inProgress(completed, reserved = RunActionKind.RESUME),
            inProgress(completed, reserved = RunActionKind.PAUSE),

            // An unreserved in-progress run that is already finished.
            inProgress(completed),

            // A completed admission whose owner is not completed.
            RunJourneySnapshot(done, RunAdmission.RunCompleted(paused)),

            // Mismatched initialization and admission.
            RunJourneySnapshot(InitializationStatus.NotAttempted, RunAdmission.ReadyForCountdown),
            RunJourneySnapshot(done, RunAdmission.NotInitialized)
        )

        // Act and Assert
        for (snapshot in impossible) {
            assertThrows("Expected $snapshot to be refused.", IllegalStateException::class.java) {
                runUiModelFor(snapshot)
            }
        }
    }

    /**
     * Proves the model itself refuses action flags that only a live run could carry.
     */
    @Test
    fun `the model refuses action flags outside an active run`() {
        assertThrows(IllegalArgumentException::class.java) {
            RunUiModel(RunUiState.Saved, actionInProgress = true)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RunUiModel(RunUiState.Countdown, actionFailed = true)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RunUiModel(RunUiState.ActiveRunning, actionInProgress = true, actionFailed = true)
        }
    }

    /**
     * Proves the presentation model carries nothing but a state and two booleans.
     *
     * Reflection over the declared fields, so a later edit that adds a session, row, UUID
     * or exception — even a private one — fails here rather than in review.
     */
    @Test
    fun `the presentation model contains no session entity uuid or exception`() {

        // Act
        val fieldTypes = RunUiModel::class.java.declaredFields
            .filterNot { Modifier.isStatic(it.modifiers) || it.isSynthetic }
            .associate { it.name to it.type }

        // Assert: exactly these three, of exactly these types.
        assertEquals(
            mapOf(
                "state" to RunUiState::class.java,
                "actionInProgress" to Boolean::class.javaPrimitiveType,
                "actionFailed" to Boolean::class.javaPrimitiveType
            ),
            fieldTypes
        )
    }
}
