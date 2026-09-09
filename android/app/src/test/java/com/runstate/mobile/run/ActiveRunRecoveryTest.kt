package com.runstate.mobile.run

import com.runstate.mobile.data.local.FakeRunDao
import com.runstate.mobile.data.local.RunEntity
import com.runstate.mobile.data.local.StoredRunState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks what recovery decides, given what storage turns out to be holding.
 *
 * This file is about the decision and nothing else. Whether the query filters and
 * orders correctly belongs to `FakeRunDaoTest` and `RunStateDatabaseTest`; whether a
 * restored machine enforces its own rules belongs to `RunSessionStateMachineTest`. What
 * is only visible here is the join: an empty database, one adoptable run and several
 * live runs at once must produce three different answers, and none of them may be
 * reached by writing to the database.
 *
 * There is deliberately no test for the defensive COMPLETED branch in `recover`. Getting
 * there would mean giving [FakeRunDao] a way to return a row the real query cannot
 * match, and a fake that contradicts the DAO would undermine every other JVM test that
 * trusts it.
 */
class ActiveRunRecoveryTest {

    private companion object {
        const val OFFICIAL_START = 1_756_000_000_000L
        const val PAUSED_AT = OFFICIAL_START + 60_000L

        /** A second run, started ten minutes later, for the inconsistent case. */
        const val SECOND_START = OFFICIAL_START + 600_000L

        const val RUN_ID = "0f6a2c1e-9d43-4b7a-9c21-7b5e8a4d1f30"
        const val SECOND_RUN_ID = "c4e1b8a2-7d35-4f61-8b0c-2a9e6d4f13b7"
        const val COMPLETED_RUN_ID = "7b3d9e10-2c4f-4a86-9d05-6e8f1a2b3c4d"
    }

    /** The row a run has while it is live: RUNNING, checkpoint at the official start. */
    private fun runningRun(
        runId: String = RUN_ID,
        officialStart: Long = OFFICIAL_START
    ): RunEntity = RunEntity(
        runId = runId,
        state = StoredRunState.RUNNING,
        officialStartEpochMillis = officialStart,
        startTimezoneId = "America/Chicago",
        lastCheckpointEpochMillis = officialStart
    )

    /** A machine as a freshly restarted process would build it. */
    private fun freshMachine() = RunSessionStateMachine()

    /**
     * Proves an empty database produces the ordinary answer and touches nothing.
     */
    @Test
    fun `no active runs recovers nothing and leaves the machine alone`() {

        // Arrange: storage holds one finished run and nothing unfinished.
        val dao = FakeRunDao()
        runBlocking {
            dao.insert(runningRun(COMPLETED_RUN_ID))
            dao.pauseRun(COMPLETED_RUN_ID, PAUSED_AT)
            dao.completeRun(COMPLETED_RUN_ID, PAUSED_AT + 60_000L)
        }
        val machine = freshMachine()

        // Act
        val result = runBlocking { ActiveRunRecovery(machine, dao).recover() }

        // Assert: history is not a recovery candidate.
        assertEquals(ActiveRunRecoveryResult.NothingToRecover, result)

        // Assert: a machine with nothing to adopt is left exactly as it was found.
        assertEquals(RunSessionState.NO_SESSION, machine.state)

        // Assert: storage was asked once, not repeatedly.
        assertEquals(1, dao.discoveryQueryCalls)
    }

    /**
     * Proves one running row is adopted directly, with no countdown replayed.
     */
    @Test
    fun `one running run is recovered into a running owner`() {

        // Arrange: a single live run on disk.
        val dao = FakeRunDao()
        val storedRow = runningRun()
        runBlocking { dao.insert(storedRow) }
        val machine = freshMachine()

        // Act
        val result = runBlocking { ActiveRunRecovery(machine, dao).recover() }

        // Assert: the exact row discovery read is handed back as the snapshot.
        val recovered = result as ActiveRunRecoveryResult.Recovered
        assertEquals(storedRow, recovered.runAtRecovery)
        assertEquals(RUN_ID, recovered.runAtRecovery.runId)

        // Assert: the owner is bound to that same run.
        assertEquals(RUN_ID, recovered.session.runId)

        // Assert: memory landed on the state storage reported, in both places.
        assertEquals(RunSessionState.RUNNING, machine.state)
        assertEquals(RunSessionState.RUNNING, recovered.session.state)

        // Assert: exactly one query answered all of that.
        assertEquals(1, dao.discoveryQueryCalls)
    }

    /**
     * Proves a paused run is recovered as paused without inventing the pause.
     *
     * The stored run below has no transition rows at all — it is paused according to
     * its own state column and nothing else. That is the honest shape of a run whose
     * history recovery has no business reconstructing, and it makes the assertion that
     * no event appeared meaningful: any PAUSE found afterwards could only have been
     * manufactured by recovery itself.
     */
    @Test
    fun `one paused run is recovered without inventing a pause event`() {

        // Arrange: a paused row written directly, with no transition children.
        val dao = FakeRunDao()
        val pausedRow = runningRun().copy(
            state = StoredRunState.PAUSED,
            lastCheckpointEpochMillis = PAUSED_AT
        )
        runBlocking { dao.insert(pausedRow) }

        val machine = freshMachine()

        // Act
        val result = runBlocking { ActiveRunRecovery(machine, dao).recover() }

        // Assert: recovered as paused, exactly as stored.
        val recovered = result as ActiveRunRecoveryResult.Recovered
        assertEquals(pausedRow, recovered.runAtRecovery)
        assertEquals(RUN_ID, recovered.session.runId)
        assertEquals(RunSessionState.PAUSED, machine.state)
        assertEquals(RunSessionState.PAUSED, recovered.session.state)

        // Assert: no history was fabricated to explain how it got there.
        assertTrue(
            "Recovery must not invent transition events: ${dao.transitions}",
            runBlocking { dao.transitionsFor(RUN_ID) }.isEmpty()
        )
    }

    /**
     * Proves several live runs are reported rather than resolved.
     *
     * Being first in the discovery order is evidence ordering, not a ranking. Recovery
     * refusing to adopt the earliest is what keeps a state the app cannot explain from
     * becoming a state it has silently committed to.
     */
    @Test
    fun `multiple active runs are reported as inconsistent`() {

        // Arrange: two unfinished runs and one finished one.
        val dao = FakeRunDao()
        val earlierRun = runningRun()
        val laterRun = runningRun(SECOND_RUN_ID, SECOND_START).copy(
            state = StoredRunState.PAUSED,
            lastCheckpointEpochMillis = SECOND_START
        )
        runBlocking {
            // Inserted later-first so insertion order cannot be mistaken for the answer.
            dao.insert(laterRun)
            dao.insert(earlierRun)
            dao.insert(runningRun(COMPLETED_RUN_ID))
            dao.pauseRun(COMPLETED_RUN_ID, PAUSED_AT)
            dao.completeRun(COMPLETED_RUN_ID, PAUSED_AT + 60_000L)
        }
        val storageBefore = dao.inserted.toList()
        val machine = freshMachine()

        // Act
        val result = runBlocking { ActiveRunRecovery(machine, dao).recover() }

        // Assert: every candidate, whole, in discovery order, with history excluded.
        val inconsistent = result as ActiveRunRecoveryResult.InconsistentActiveRuns
        assertEquals(listOf(earlierRun, laterRun), inconsistent.activeRuns)

        // Assert: no run was chosen and no owner was partially restored.
        assertEquals(RunSessionState.NO_SESSION, machine.state)

        // Assert: the evidence on disk is left exactly as it was for investigation.
        assertEquals(storageBefore, dao.inserted)
        assertEquals(3, runBlocking { dao.countRuns() })
    }

    /**
     * Proves an unreadable database is not reported as an empty one.
     */
    @Test
    fun `a discovery failure propagates and leaves the machine alone`() {

        // Arrange: storage that cannot answer the discovery query.
        val dao = FakeRunDao()
        runBlocking { dao.insert(runningRun()) }
        dao.failDiscoveryWith = IllegalStateException("disk unavailable")
        val machine = freshMachine()

        // Act and Assert: the exact failure reaches the caller, unwrapped.
        val thrown = assertThrows(IllegalStateException::class.java) {
            runBlocking { ActiveRunRecovery(machine, dao).recover() }
        }
        assertEquals("disk unavailable", thrown.message)

        // Assert: nothing was adopted on the strength of an answer never received.
        assertEquals(RunSessionState.NO_SESSION, machine.state)
    }

    /**
     * Proves a machine that already owns a run is refused before storage is asked.
     *
     * The precondition exists so a live run cannot be overwritten by whatever happens
     * to be on disk. Checking it first also means the refusal costs no query at all,
     * which is what the call count proves — without it, "queried and declined" would
     * look the same from outside.
     */
    @Test
    fun `recovery is refused before querying when the machine owns a session`() {

        // Arrange: a machine already carrying a live run, and a recoverable row on disk.
        val dao = FakeRunDao()
        runBlocking { dao.insert(runningRun()) }
        val machine = freshMachine().apply {
            beginCountdown()
            startRun()
        }

        // Act and Assert
        assertThrows(IllegalStateException::class.java) {
            runBlocking { ActiveRunRecovery(machine, dao).recover() }
        }

        // Assert: storage was never consulted.
        assertEquals(0, dao.discoveryQueryCalls)

        // Assert: the run this machine already owns is untouched.
        assertEquals(RunSessionState.RUNNING, machine.state)
    }

    /**
     * Proves a successful recovery changes memory only.
     *
     * Recovery reads a record of what already happened. Repairing, completing or
     * checkpointing that row would edit the evidence, and the run on disk is the only
     * account of the run the app has.
     */
    @Test
    fun `a successful recovery writes nothing to storage`() {

        // Arrange: a paused run with a real pause behind it, plus a finished run.
        val dao = FakeRunDao()
        runBlocking {
            dao.insert(runningRun())
            dao.pauseRun(RUN_ID, PAUSED_AT)

            dao.insert(runningRun(COMPLETED_RUN_ID, SECOND_START))
            dao.pauseRun(COMPLETED_RUN_ID, SECOND_START + 60_000L)
            dao.completeRun(COMPLETED_RUN_ID, SECOND_START + 120_000L)
        }

        // Arrange: exactly what storage looks like before recovery runs.
        val rowsBefore = dao.inserted.toList()
        val transitionsBefore = dao.transitions.toList()
        val countBefore = runBlocking { dao.countRuns() }

        // Act
        val result = runBlocking { ActiveRunRecovery(freshMachine(), dao).recover() }

        // Assert: it did recover, so this is a success path and not a silent no-op.
        val recovered = result as ActiveRunRecoveryResult.Recovered
        assertEquals(RUN_ID, recovered.session.runId)
        assertEquals(RunSessionState.PAUSED, recovered.session.state)

        // Assert: every stored row is byte-for-byte what it was.
        assertEquals(rowsBefore, dao.inserted)

        // Assert: the real pause is still the only event, unchanged and un-duplicated.
        assertEquals(transitionsBefore, dao.transitions)
        assertEquals(
            listOf("PAUSE@1"),
            runBlocking { dao.transitionsFor(RUN_ID) }
                .map { "${it.transitionType}@${it.sequenceNumber}" }
        )

        // Assert: no row was added or removed.
        assertEquals(countBefore, runBlocking { dao.countRuns() })

        // Assert: the snapshot handed back is the stored row, not a rebuilt copy.
        assertSame(
            dao.inserted.first { it.runId == RUN_ID },
            recovered.runAtRecovery
        )
    }
}
