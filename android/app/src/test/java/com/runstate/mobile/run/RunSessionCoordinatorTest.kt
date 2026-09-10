package com.runstate.mobile.run

import com.runstate.mobile.data.local.FakeRunDao
import com.runstate.mobile.data.local.RunEntity
import com.runstate.mobile.data.local.StoredRunState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks the one gate a run has to pass through, without a phone or a database.
 *
 * The parts underneath are already proved elsewhere: recovery's zero/one/many decision in
 * `ActiveRunRecoveryTest`, the save-before-RUNNING boundary in `RunSessionStarterTest`,
 * and the durable lifecycle in `ActiveRunSessionTest`. What only shows up here is the
 * ordering between them — that recovery runs before anything may start, that exactly one
 * owner is ever held, and that a finished run is retired rather than reset.
 *
 * [FakeRunDao] is used because these tests need to force discovery to fail and to park it
 * mid-query, which is about the coordinator's sequencing rather than about SQLite.
 */
class RunSessionCoordinatorTest {

    private companion object {
        const val OFFICIAL_START = 1_756_000_000_000L

        /** A plausible first run: pause at one minute, end at two. */
        const val FIRST_PAUSE = OFFICIAL_START + 60_000L
        const val FIRST_FINISH = OFFICIAL_START + 120_000L

        /** The next run starts well after the first one ended. */
        const val SECOND_START = OFFICIAL_START + 600_000L

        const val RUN_ID = "0f6a2c1e-9d43-4b7a-9c21-7b5e8a4d1f30"
        const val SECOND_RUN_ID = "c4e1b8a2-7d35-4f61-8b0c-2a9e6d4f13b7"
        const val THIRD_RUN_ID = "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d"
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

    /**
     * Proves an empty database admits a countdown and holds no run.
     */
    @Test
    fun `initialization with no active rows is ready for a countdown`() {

        // Arrange: nothing unfinished on disk.
        val dao = FakeRunDao()
        val coordinator = RunSessionCoordinator(dao)

        // Act
        val status = runBlocking { coordinator.initialize() }

        // Assert: recovery reached a decision, and the decision was "nothing to adopt".
        assertEquals(InitializationStatus.Completed, status)
        assertEquals(
            RunAdmission.ReadyForCountdown,
            runBlocking { coordinator.admission() }
        )
        assertEquals(1, dao.discoveryQueryCalls)
    }

    /**
     * Proves a single unfinished run is adopted and reported as still in progress.
     */
    @Test
    fun `initialization with one active row reports the recovered run in progress`() {

        // Arrange: one run the phone was still in the middle of.
        val dao = FakeRunDao()
        runBlocking { dao.insert(preparedRun()) }
        val coordinator = RunSessionCoordinator(dao)

        // Act
        val status = runBlocking { coordinator.initialize() }

        // Assert
        assertEquals(InitializationStatus.Completed, status)
        val admission = runBlocking { coordinator.admission() }
        val inProgress = admission as RunAdmission.RunInProgress

        // Assert: the owner is bound to the stored run, and it is live.
        assertEquals(RUN_ID, inProgress.session.runId)
        assertEquals(RunSessionState.RUNNING, inProgress.session.state)

        // Assert: the same owner every time, never a fresh one per question.
        val secondAdmission = runBlocking { coordinator.admission() }
        assertSame(
            inProgress.session,
            (secondAdmission as RunAdmission.RunInProgress).session
        )
    }

    /**
     * Proves several unfinished runs block admission instead of being resolved.
     *
     * The candidates come back whole and in discovery order. That order is evidence
     * ordering rather than a ranking, which is exactly why nothing here adopts the first.
     */
    @Test
    fun `initialization with multiple active rows is blocked by inconsistent storage`() {

        // Arrange: two live runs at once, inserted in the wrong order on purpose.
        val dao = FakeRunDao()
        val earlierRun = preparedRun()
        val laterRun = preparedRun(SECOND_RUN_ID, SECOND_START)
        runBlocking {
            dao.insert(laterRun)
            dao.insert(earlierRun)
        }
        val coordinator = RunSessionCoordinator(dao)

        // Act
        val status = runBlocking { coordinator.initialize() }

        // Assert: reaching a refusal is still reaching a decision.
        assertEquals(InitializationStatus.Completed, status)

        // Assert: every candidate, in discovery order, with nothing adopted.
        val blocked = runBlocking { coordinator.admission() }
            as RunAdmission.BlockedByInconsistentStorage
        assertEquals(listOf(earlierRun, laterRun), blocked.activeRuns)
    }

    /**
     * Proves an unreadable database is reported as a failure, not as an empty one.
     */
    @Test
    fun `a discovery failure fails initialization and admits nothing`() {

        // Arrange: a live run on disk that discovery cannot reach.
        val dao = FakeRunDao()
        runBlocking { dao.insert(preparedRun()) }
        val failure = IllegalStateException("disk unavailable")
        dao.failDiscoveryWith = failure
        val coordinator = RunSessionCoordinator(dao)

        // Act
        val status = runBlocking { coordinator.initialize() }

        // Assert: the exact cause is carried, unwrapped.
        val failed = status as InitializationStatus.Failed
        assertSame(failure, failed.cause)

        // Assert: a failed attempt admits nothing, because what storage holds is unknown.
        assertEquals(
            RunAdmission.NotInitialized,
            runBlocking { coordinator.admission() }
        )
    }

    /**
     * Proves a failed attempt is genuinely retried rather than remembered as a verdict.
     *
     * The call count is the assertion. A retry that returned the previous answer without
     * asking storage again would look identical from the outside.
     */
    @Test
    fun `initialization retries after a failure and can then complete`() {

        // Arrange: discovery fails once.
        val dao = FakeRunDao()
        dao.failDiscoveryWith = IllegalStateException("disk unavailable")
        val coordinator = RunSessionCoordinator(dao)
        assertTrue(runBlocking { coordinator.initialize() } is InitializationStatus.Failed)
        assertEquals(1, dao.discoveryQueryCalls)

        // Act: the database becomes readable and the caller tries again.
        dao.failDiscoveryWith = null
        val status = runBlocking { coordinator.initialize() }

        // Assert: a real second query, and a real completion this time.
        assertEquals(InitializationStatus.Completed, status)
        assertEquals(2, dao.discoveryQueryCalls)
        assertEquals(
            RunAdmission.ReadyForCountdown,
            runBlocking { coordinator.admission() }
        )
    }

    /**
     * Proves a completed attempt is not repeated.
     *
     * Asking storage a second time could return a different database than the one this
     * process built its whole in-memory picture from.
     */
    @Test
    fun `initialization after success is idempotent and does not query again`() {

        // Arrange: one successful initialization.
        val dao = FakeRunDao()
        val coordinator = RunSessionCoordinator(dao)
        assertEquals(
            InitializationStatus.Completed,
            runBlocking { coordinator.initialize() }
        )
        assertEquals(1, dao.discoveryQueryCalls)

        // Act
        val status = runBlocking { coordinator.initialize() }

        // Assert: same answer, no second query.
        assertEquals(InitializationStatus.Completed, status)
        assertEquals(1, dao.discoveryQueryCalls)
    }

    /**
     * Proves nothing can start before recovery has established what storage holds.
     *
     * This is the whole reason the coordinator exists: starting first could create a
     * second live run beside one already on disk.
     */
    @Test
    fun `start before initialization is refused and writes nothing`() {

        // Arrange: a coordinator nobody has initialized.
        val dao = FakeRunDao()
        val coordinator = RunSessionCoordinator(dao)

        // Act and Assert
        assertThrows(IllegalStateException::class.java) {
            runBlocking { coordinator.start(preparedRun()) }
        }

        // Assert: storage was never touched, and no countdown was opened either.
        assertTrue(dao.inserted.isEmpty())
        assertEquals(0, dao.discoveryQueryCalls)
        assertThrows(IllegalStateException::class.java) {
            runBlocking { coordinator.beginCountdown() }
        }
    }

    /**
     * Proves a second run cannot begin while the first is still live.
     */
    @Test
    fun `a second start while a run is held is refused and stores no second row`() {

        // Arrange: one run genuinely started through the coordinator.
        val dao = FakeRunDao()
        val coordinator = RunSessionCoordinator(dao)
        val session = runBlocking {
            coordinator.initialize()
            coordinator.beginCountdown()
            coordinator.start(preparedRun())
        }

        // Act and Assert: a second start is refused while that run is RUNNING.
        assertThrows(IllegalStateException::class.java) {
            runBlocking { coordinator.start(preparedRun(SECOND_RUN_ID, SECOND_START)) }
        }

        // Act and Assert: pausing changes nothing about admission.
        runBlocking { session.pause(FIRST_PAUSE) }
        assertThrows(IllegalStateException::class.java) {
            runBlocking { coordinator.start(preparedRun(SECOND_RUN_ID, SECOND_START)) }
        }

        // Assert: still exactly one stored run, and still the same owner.
        assertEquals(1, dao.inserted.size)
        assertEquals(RUN_ID, dao.inserted.single().runId)
        val admission = runBlocking { coordinator.admission() }
        assertSame(session, (admission as RunAdmission.RunInProgress).session)
        assertEquals(RunSessionState.PAUSED, session.state)
    }

    /**
     * Proves a blocked database refuses a start without touching the evidence.
     */
    @Test
    fun `start while blocked by inconsistent storage is refused and changes nothing`() {

        // Arrange: two live runs, so the coordinator refuses to interpret storage.
        val dao = FakeRunDao()
        val earlierRun = preparedRun()
        val laterRun = preparedRun(SECOND_RUN_ID, SECOND_START)
        runBlocking {
            dao.insert(earlierRun)
            dao.insert(laterRun)
        }
        val coordinator = RunSessionCoordinator(dao)
        runBlocking { coordinator.initialize() }
        val storedBefore = dao.inserted.toList()

        // Act and Assert: neither a countdown nor a start is admitted.
        assertThrows(IllegalStateException::class.java) {
            runBlocking { coordinator.beginCountdown() }
        }
        assertThrows(IllegalStateException::class.java) {
            runBlocking { coordinator.start(preparedRun(THIRD_RUN_ID, SECOND_START)) }
        }

        // Assert: no third row was written and the two candidates are untouched.
        assertEquals(storedBefore, dao.inserted)
        assertEquals(2, dao.inserted.size)

        // Assert: the evidence is still reported whole, in the same order.
        val blocked = runBlocking { coordinator.admission() }
            as RunAdmission.BlockedByInconsistentStorage
        assertEquals(listOf(earlierRun, laterRun), blocked.activeRuns)
    }

    /**
     * Proves a finished run is retired at the next countdown so another run can begin.
     *
     * The retired machine is discarded rather than reset, which is why the first session
     * keeps reporting COMPLETED forever afterwards: it still points at that machine, and
     * "this run ended" is not a reversible claim.
     */
    @Test
    fun `a completed run is retired and the next countdown starts a new run`() {

        // Arrange: one full run, taken all the way to completion.
        val dao = FakeRunDao()
        val coordinator = RunSessionCoordinator(dao)
        val firstSession = runBlocking {
            coordinator.initialize()
            coordinator.beginCountdown()

            // Assert: a countdown is underway and no run is official yet.
            assertEquals(
                RunAdmission.CountdownInProgress,
                coordinator.admission()
            )

            val session = coordinator.start(preparedRun())
            session.pause(FIRST_PAUSE)
            session.complete(FIRST_FINISH)
            session
        }

        // Assert: a completed cycle no longer blocks the next one.
        assertEquals(RunSessionState.COMPLETED, firstSession.state)
        assertEquals(
            RunAdmission.ReadyForCountdown,
            runBlocking { coordinator.admission() }
        )

        // Act: the next countdown retires the finished cycle, and a new run begins.
        val secondSession = runBlocking {
            coordinator.beginCountdown()
            coordinator.start(preparedRun(SECOND_RUN_ID, SECOND_START))
        }

        // Assert: two distinct runs, and the finished one stayed finished.
        assertEquals(SECOND_RUN_ID, secondSession.runId)
        assertEquals(RunSessionState.RUNNING, secondSession.state)
        assertEquals(RunSessionState.COMPLETED, firstSession.state)

        // Assert: the coordinator now owns the second run, not the first.
        val admission = runBlocking { coordinator.admission() }
        assertSame(secondSession, (admission as RunAdmission.RunInProgress).session)

        // Assert: both runs are stored, each written once.
        assertEquals(2, dao.inserted.size)
        assertEquals(listOf(RUN_ID, SECOND_RUN_ID), dao.inserted.map { it.runId })
    }

    /**
     * Proves a countdown and start arriving mid-recovery wait rather than interleaving.
     *
     * This is the ordering guarantee the coordinator was written for, and it is asserted
     * as ordering rather than as a final state. Recovery is parked inside the DAO query
     * while it holds the coordinator lock; the competing job is then launched UNDISPATCHED
     * so it runs on this thread until it can go no further — which, with the lock held, is
     * the lock itself. The assertions taken at that moment are what prove the competing
     * start had not crossed into storage, something a check of the end state could not
     * distinguish from a start that ran first and simply lost a later race.
     *
     * Nothing here depends on a delay. CompletableDeferred does the coordinating, and the
     * mutex — not the scheduler — is what makes initialization finish first.
     */
    @Test
    fun `recovery completes before a competing countdown and start reach storage`() {

        // Arrange: a DAO whose discovery parks until this test releases it.
        val dao = FakeRunDao()
        val coordinator = RunSessionCoordinator(dao)
        val discoveryReached = CompletableDeferred<Unit>()
        val releaseDiscovery = CompletableDeferred<Unit>()
        dao.duringDiscovery = {
            discoveryReached.complete(Unit)
            releaseDiscovery.await()
        }

        // Arrange: a record of which job actually finished first.
        val completionOrder = mutableListOf<String>()

        runBlocking {

            // Act: initialization runs until it is parked inside discovery, holding the
            // coordinator lock.
            val initialization = launch {
                coordinator.initialize()
                completionOrder += "initialization"
            }
            discoveryReached.await()

            // Act: a countdown and start arrive while recovery is still in flight.
            val competingStart = launch(start = CoroutineStart.UNDISPATCHED) {
                coordinator.beginCountdown()
                coordinator.start(preparedRun())
                completionOrder += "start"
            }

            // Assert: with recovery parked, the competing job is stuck at the lock. It has
            // not finished, and — the assertion that matters — it has not inserted a run.
            assertFalse(
                "The competing start must not complete while recovery is parked.",
                competingStart.isCompleted
            )
            assertTrue(
                "No run may reach storage before recovery finishes: ${dao.inserted}",
                dao.inserted.isEmpty()
            )
            assertTrue(completionOrder.isEmpty())

            // Act: let recovery finish.
            releaseDiscovery.complete(Unit)
            initialization.join()
            competingStart.join()
        }

        // Assert: initialization finished first, and only then did the run start.
        assertEquals(listOf("initialization", "start"), completionOrder)

        // Assert: the run that was waiting did eventually start, exactly once.
        assertEquals(1, dao.inserted.size)
        assertEquals(RUN_ID, dao.inserted.single().runId)
        val admission = runBlocking { coordinator.admission() }
        assertEquals(RUN_ID, (admission as RunAdmission.RunInProgress).session.runId)
        assertEquals(1, dao.discoveryQueryCalls)
    }

    /**
     * Proves a later initialize call cannot disturb a run that is already underway.
     *
     * A caller that cannot easily tell whether startup already ran should be able to ask
     * again safely. Re-querying here would be worse than useless: discovery would find the
     * live run and recovery would try to adopt a run this coordinator already owns.
     */
    @Test
    fun `initialization during a live run stays completed without querying again`() {

        // Arrange: an initialized coordinator with one run underway.
        val dao = FakeRunDao()
        val coordinator = RunSessionCoordinator(dao)
        val session = runBlocking {
            coordinator.initialize()
            coordinator.beginCountdown()
            coordinator.start(preparedRun())
        }
        assertEquals(1, dao.discoveryQueryCalls)

        // Act
        val status = runBlocking { coordinator.initialize() }

        // Assert: still completed, and storage was not asked a second time.
        assertEquals(InitializationStatus.Completed, status)
        assertEquals(1, dao.discoveryQueryCalls)

        // Assert: the same live run, still owned by the same object.
        val admission = runBlocking { coordinator.admission() }
        assertSame(session, (admission as RunAdmission.RunInProgress).session)
        assertEquals(RunSessionState.RUNNING, session.state)
        assertEquals(1, dao.inserted.size)
    }

    /**
     * Proves a countdown can be backed out of, at no cost and with nothing stored.
     *
     * This is the exit that entering COUNTDOWN requires. The machine has no legal move
     * from COUNTDOWN back to NO_SESSION, so without cancellation a runner who tapped Start
     * by accident could only start a run they did not want or kill the app.
     *
     * The storage assertions are the other half. A countdown never wrote anything, so
     * cancelling it has nothing to undo — which is precisely why it can be offered as a
     * plain Cancel rather than as a discard with consequences.
     */
    @Test
    fun `cancelling a countdown returns to ready and writes nothing`() {

        // Arrange: an initialized coordinator with a countdown underway.
        val dao = FakeRunDao()
        val coordinator = RunSessionCoordinator(dao)
        runBlocking {
            coordinator.initialize()
            coordinator.beginCountdown()
        }
        assertEquals(
            RunAdmission.CountdownInProgress,
            runBlocking { coordinator.admission() }
        )
        val discoveriesBeforeCancel = dao.discoveryQueryCalls

        // Act
        runBlocking { coordinator.cancelCountdown() }

        // Assert: back to the state a countdown began from.
        assertEquals(
            RunAdmission.ReadyForCountdown,
            runBlocking { coordinator.admission() }
        )

        // Assert: nothing was written, and nothing was read either.
        assertTrue("Cancelling wrote a run: ${dao.inserted}", dao.inserted.isEmpty())
        assertTrue(
            "Cancelling wrote a transition: ${dao.transitions}",
            dao.transitions.isEmpty()
        )
        assertEquals(0, runBlocking { dao.countRuns() })
        assertEquals(discoveriesBeforeCancel, dao.discoveryQueryCalls)

        // Assert: the fresh cycle is genuinely usable, not merely reported as ready.
        val session = runBlocking {
            coordinator.beginCountdown()
            coordinator.start(preparedRun())
        }
        assertEquals(RunSessionState.RUNNING, session.state)
        assertEquals(1, dao.inserted.size)
    }

    /**
     * Proves cancelling with no countdown underway fails instead of quietly succeeding.
     *
     * Silently doing nothing would make a stray cancel indistinguishable from a real one,
     * and would leave "cancel" available as a way to discard a cycle that might be holding
     * something.
     */
    @Test
    fun `cancelling without a countdown is refused`() {

        // Arrange: initialized, ready, but no countdown.
        val dao = FakeRunDao()
        val coordinator = RunSessionCoordinator(dao)
        runBlocking { coordinator.initialize() }

        // Act and Assert
        assertThrows(IllegalStateException::class.java) {
            runBlocking { coordinator.cancelCountdown() }
        }

        // Assert: nothing changed, and a countdown is still available.
        assertEquals(
            RunAdmission.ReadyForCountdown,
            runBlocking { coordinator.admission() }
        )
        assertTrue(dao.inserted.isEmpty())

        // Act and Assert: refused before initialization for the same reason.
        val uninitialized = RunSessionCoordinator(FakeRunDao())
        assertThrows(IllegalStateException::class.java) {
            runBlocking { uninitialized.cancelCountdown() }
        }
    }

    /**
     * Proves cancelling cannot be used to drop a run this coordinator owns.
     *
     * A live run is not a countdown, and cancellation must never become a quiet way to let
     * go of an owner while its run is still stored as unfinished.
     */
    @Test
    fun `cancelling while a run is held is refused`() {

        // Arrange: one run genuinely underway.
        val dao = FakeRunDao()
        val coordinator = RunSessionCoordinator(dao)
        val session = runBlocking {
            coordinator.initialize()
            coordinator.beginCountdown()
            coordinator.start(preparedRun())
        }

        // Act and Assert: refused while RUNNING.
        assertThrows(IllegalStateException::class.java) {
            runBlocking { coordinator.cancelCountdown() }
        }

        // Act and Assert: still refused while PAUSED.
        runBlocking { session.pause(FIRST_PAUSE) }
        assertThrows(IllegalStateException::class.java) {
            runBlocking { coordinator.cancelCountdown() }
        }

        // Assert: the run is untouched and still owned by the same object.
        assertEquals(RunSessionState.PAUSED, session.state)
        assertSame(
            session,
            (runBlocking { coordinator.admission() } as RunAdmission.RunInProgress).session
        )
        assertEquals(1, dao.inserted.size)
    }
}
