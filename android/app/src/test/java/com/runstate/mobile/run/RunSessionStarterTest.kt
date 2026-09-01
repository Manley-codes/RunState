package com.runstate.mobile.run

import com.runstate.mobile.data.local.RunDao
import com.runstate.mobile.data.local.RunEntity
import com.runstate.mobile.data.local.StoredRunState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks the save-before-RUNNING boundary without a phone, an emulator or a database.
 *
 * The DAO is replaced by a fake so the tests can watch exactly when the insert happens,
 * force it to fail, and hold it open while another caller tries to start.
 */
class RunSessionStarterTest {

    /**
     * A stand-in for the real Room DAO.
     *
     * It records what it was handed, can be told to fail, and can run a callback at the
     * moment of insert. That callback is a suspending function so a test can park the
     * insert mid-flight and let another coroutine run while the write is unfinished.
     */
    private class FakeRunDao : RunDao {

        /** Every row the starter handed over, in order. */
        val inserted = mutableListOf<RunEntity>()

        /** When set, the insert throws this instead of storing anything. */
        var failWith: Exception? = null

        /** Runs at the start of the insert, before success or failure is decided. */
        var duringInsert: (suspend () -> Unit)? = null

        override suspend fun insert(run: RunEntity) {
            duringInsert?.invoke()
            failWith?.let { throw it }
            inserted += run
        }

        override suspend fun findById(runId: String): RunEntity? =
            inserted.lastOrNull { it.runId == runId }
    }

    private val officialStart = 1_756_000_000_000L
    private val firstRunId = "0f6a2c1e-9d43-4b7a-9c21-7b5e8a4d1f30"
    private val secondRunId = "c4e1b8a2-7d35-4f61-8b0c-2a9e6d4f13b7"

    /** Builds the row a real countdown would hand over: RUNNING, checkpoint at start. */
    private fun preparedRun(runId: String = firstRunId): RunEntity = RunEntity(
        runId = runId,
        state = StoredRunState.RUNNING,
        officialStartEpochMillis = officialStart,
        startTimezoneId = "America/Chicago",
        lastCheckpointEpochMillis = officialStart
    )

    /** Puts a fresh machine into the only stage a run may start from. */
    private fun countdownMachine(): RunSessionStateMachine =
        RunSessionStateMachine().apply { beginCountdown() }

    /**
     * Proves the write happens before the run is called official, not after.
     */
    @Test
    fun `run is inserted while the session is still in countdown`() {

        // Arrange: a machine in COUNTDOWN and a DAO that reports the state it sees.
        val machine = countdownMachine()
        val dao = FakeRunDao()
        var stateDuringInsert: RunSessionState? = null
        dao.duringInsert = { stateDuringInsert = machine.state }

        // Act: cross the boundary.
        runBlocking { RunSessionStarter(machine, dao).start(preparedRun()) }

        // Assert: the insert was already underway while the run was not yet official.
        assertEquals(RunSessionState.COUNTDOWN, stateDuringInsert)
    }

    /**
     * Proves the session becomes official once the row is safely stored.
     */
    @Test
    fun `session is running after a successful insert`() {

        // Arrange
        val machine = countdownMachine()
        val dao = FakeRunDao()

        // Act
        runBlocking { RunSessionStarter(machine, dao).start(preparedRun()) }

        // Assert: exactly one row stored, and the run is now running.
        assertEquals(1, dao.inserted.size)
        assertEquals(RunSessionState.RUNNING, machine.state)
    }

    /**
     * Proves the prepared row reaches storage untouched, so a retry can reuse the same
     * UUID and official start rather than inventing a second run.
     */
    @Test
    fun `the exact prepared entity reaches the dao unchanged`() {

        // Arrange
        val machine = countdownMachine()
        val dao = FakeRunDao()
        val prepared = preparedRun()

        // Act
        runBlocking { RunSessionStarter(machine, dao).start(prepared) }

        // Assert: the same object, not a copy and not a rebuilt equivalent.
        assertSame(prepared, dao.inserted.single())
    }

    /**
     * Proves a storage failure is reported rather than absorbed, and that the session
     * honestly stays in COUNTDOWN because no run was saved.
     */
    @Test
    fun `insert failure propagates and leaves the session in countdown`() {

        // Arrange: a DAO that cannot store anything.
        val machine = countdownMachine()
        val dao = FakeRunDao()
        dao.failWith = IllegalStateException("disk unavailable")

        // Act + assert: the failure reaches the caller.
        val thrown = assertThrows(IllegalStateException::class.java) {
            runBlocking { RunSessionStarter(machine, dao).start(preparedRun()) }
        }
        assertEquals("disk unavailable", thrown.message)

        // Assert: nothing stored, and the run never became official.
        assertTrue(dao.inserted.isEmpty())
        assertEquals(RunSessionState.COUNTDOWN, machine.state)
    }

    /**
     * Proves a run cannot be saved from a stage that has no countdown behind it.
     */
    @Test
    fun `starting outside countdown throws and inserts nothing`() {

        // Arrange: a brand-new machine that has not begun its countdown.
        val machine = RunSessionStateMachine()
        val dao = FakeRunDao()

        // Act + assert
        assertThrows(IllegalStateException::class.java) {
            runBlocking { RunSessionStarter(machine, dao).start(preparedRun()) }
        }

        // Assert: refused before touching storage, and the machine did not move.
        assertTrue(dao.inserted.isEmpty())
        assertEquals(RunSessionState.NO_SESSION, machine.state)
    }

    /**
     * Proves a row that is not the initial RUNNING moment is refused before any write.
     */
    @Test
    fun `a non running prepared state is rejected without inserting`() {

        // Arrange: a row claiming the run was already paused when it was first saved.
        val machine = countdownMachine()
        val dao = FakeRunDao()
        val alreadyPaused = preparedRun().copy(state = StoredRunState.PAUSED)

        // Act + assert
        assertThrows(IllegalStateException::class.java) {
            runBlocking { RunSessionStarter(machine, dao).start(alreadyPaused) }
        }

        // Assert
        assertTrue(dao.inserted.isEmpty())
        assertEquals(RunSessionState.COUNTDOWN, machine.state)
    }

    /**
     * Proves an initial checkpoint ahead of the official start is refused, since it
     * would claim recovery could trust time the run has not recorded.
     */
    @Test
    fun `a checkpoint that is not the official start is rejected without inserting`() {

        // Arrange: a checkpoint one minute past a run that has only just begun.
        val machine = countdownMachine()
        val dao = FakeRunDao()
        val driftedCheckpoint = preparedRun().copy(
            lastCheckpointEpochMillis = officialStart + 60_000L
        )

        // Act + assert
        assertThrows(IllegalStateException::class.java) {
            runBlocking { RunSessionStarter(machine, dao).start(driftedCheckpoint) }
        }

        // Assert: nothing stored, and nothing readable back under that UUID.
        assertTrue(dao.inserted.isEmpty())
        assertNull(runBlocking { dao.findById(driftedCheckpoint.runId) })
        assertEquals(RunSessionState.COUNTDOWN, machine.state)
    }

    /**
     * Proves two overlapping starts through one starter save exactly one run.
     *
     * This is the double-tapped start button. The second caller reaches the starter
     * while the first write is still in flight, so without the mutex both would read
     * COUNTDOWN and both would insert, producing two runs for one press.
     *
     * The coroutines are coordinated, not timed. CompletableDeferred parks the first
     * insert until the test releases it, and the second call is launched UNDISPATCHED
     * so it runs immediately on this thread until it can go no further — which, with
     * the lock held, is the lock itself. Nothing here depends on a delay.
     */
    @Test
    fun `overlapping starts save only one run and the loser is refused`() {

        // Arrange: one machine and one starter shared by both callers.
        val machine = countdownMachine()
        val dao = FakeRunDao()
        val starter = RunSessionStarter(machine, dao)

        // Arrange: handles for parking and releasing the first insert.
        val firstInsertReached = CompletableDeferred<Unit>()
        val releaseFirstInsert = CompletableDeferred<Unit>()
        dao.duringInsert = {
            firstInsertReached.complete(Unit)
            releaseFirstInsert.await()
        }

        var loserFailure: Throwable? = null

        runBlocking {

            // Act: the first caller runs until it is parked inside the insert.
            val first = launch { starter.start(preparedRun(firstRunId)) }
            firstInsertReached.await()

            // Act: a second caller starts a different run while the first is unfinished.
            val second = launch(start = CoroutineStart.UNDISPATCHED) {
                loserFailure = runCatching { starter.start(preparedRun(secondRunId)) }
                    .exceptionOrNull()
            }

            // Act: let the first caller finish, then wait for both.
            releaseFirstInsert.complete(Unit)
            first.join()
            second.join()
        }

        // Assert: one run saved, and it is the first caller's run.
        assertEquals(1, dao.inserted.size)
        assertEquals(firstRunId, dao.inserted.single().runId)

        // Assert: the session became official exactly once.
        assertEquals(RunSessionState.RUNNING, machine.state)

        // Assert: the loser was refused because it saw RUNNING, not a stale COUNTDOWN.
        assertTrue(
            "Expected IllegalStateException, got $loserFailure",
            loserFailure is IllegalStateException
        )
    }
}
