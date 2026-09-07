package com.runstate.mobile.run

import com.runstate.mobile.data.local.FakeRunDao
import com.runstate.mobile.data.local.RunEntity
import com.runstate.mobile.data.local.StoredRunState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks that one [ActiveRunSession] keeps storage and memory telling the same story.
 *
 * Two things are under test here and nothing else. First, the ordering: Room is written
 * before the in-memory machine moves, so a refused write never becomes a believed
 * transition. Second, the binding: the owner only ever touches the UUID it was given.
 *
 * The DAO contract itself — what Room accepts, what it rejects and what it rolls back —
 * belongs to `RunStateDatabaseTest` against a real database. [FakeRunDao] is used here
 * because these tests need to watch the moment a durable write begins and to force one
 * to fail, which is about the owner's ordering rather than about SQLite.
 */
class ActiveRunSessionTest {

    private companion object {
        const val OFFICIAL_START = 1_756_000_000_000L

        /** A plausible lifecycle: pause at one minute, resume, pause again, end at four. */
        const val FIRST_PAUSE = OFFICIAL_START + 60_000L
        const val RESUME = OFFICIAL_START + 120_000L
        const val SECOND_PAUSE = OFFICIAL_START + 180_000L
        const val FINISH = OFFICIAL_START + 240_000L

        const val RUN_ID = "0f6a2c1e-9d43-4b7a-9c21-7b5e8a4d1f30"
        const val DECOY_RUN_ID = "c4e1b8a2-7d35-4f61-8b0c-2a9e6d4f13b7"
    }

    /** The row a real countdown hands over: RUNNING, checkpoint at the official start. */
    private fun preparedRun(runId: String = RUN_ID): RunEntity = RunEntity(
        runId = runId,
        state = StoredRunState.RUNNING,
        officialStartEpochMillis = OFFICIAL_START,
        startTimezoneId = "America/Chicago",
        lastCheckpointEpochMillis = OFFICIAL_START
    )

    /**
     * Produces an owner the way production will: through the start boundary.
     *
     * Going through [RunSessionStarter] rather than constructing the owner directly
     * means every test below starts from a run that is genuinely stored and genuinely
     * RUNNING, which is the only situation an owner is allowed to exist in.
     */
    private fun startedOwner(dao: FakeRunDao): ActiveRunSession {
        val machine = RunSessionStateMachine().apply { beginCountdown() }
        return runBlocking { RunSessionStarter(machine, dao).start(preparedRun()) }
    }

    /** Reads the run under test back, failing loudly rather than asserting on null. */
    private fun storedRun(dao: FakeRunDao): RunEntity =
        runBlocking { dao.findById(RUN_ID) }
            ?: throw AssertionError("The run under test is no longer stored.")

    /** The `type@sequence` shape of one run's stored history, for readable comparison. */
    private fun transitionOutline(dao: FakeRunDao, runId: String = RUN_ID): List<String> =
        runBlocking { dao.transitionsFor(runId) }
            .map { "${it.transitionType}@${it.sequenceNumber}" }

    /**
     * Proves one owner carries one run from RUNNING to COMPLETED, storage first.
     *
     * The observations inside the DAO hooks are the ordering evidence: at the instant
     * each durable write begins, the owner still reports the state it had before that
     * write. Memory follows storage, so it cannot have moved yet.
     */
    @Test
    fun `a full journey ends completed in storage and memory`() {

        // Arrange: a second run that this owner has nothing to do with.
        val dao = FakeRunDao()
        runBlocking { dao.insert(preparedRun(DECOY_RUN_ID)) }
        val decoyBefore = runBlocking { dao.findById(DECOY_RUN_ID) }

        // Arrange: the owner, and a record of what memory says mid-write.
        val owner = startedOwner(dao)
        val stateWhenWriteBegan = mutableListOf<RunSessionState>()
        dao.duringStateChange = { stateWhenWriteBegan += owner.state }
        dao.duringCompletion = { stateWhenWriteBegan += owner.state }

        // Act: the whole journey, through the owner alone.
        runBlocking {
            owner.pause(FIRST_PAUSE)
            owner.resume(RESUME)
            owner.pause(SECOND_PAUSE)
            owner.complete(FINISH)
        }

        // Assert: every durable write began while memory still held its prior state.
        assertEquals(
            listOf(
                RunSessionState.RUNNING,
                RunSessionState.PAUSED,
                RunSessionState.RUNNING,
                RunSessionState.PAUSED
            ),
            stateWhenWriteBegan
        )

        // Assert: still one run of ours, ended, with the exact finish supplied.
        val completed = storedRun(dao)
        assertEquals(RUN_ID, completed.runId)
        assertEquals(StoredRunState.COMPLETED, completed.state)
        assertEquals(FINISH, completed.finishEpochMillis)
        assertEquals(FINISH, completed.lastCheckpointEpochMillis)
        assertEquals(OFFICIAL_START, completed.officialStartEpochMillis)

        // Assert: memory agrees with storage.
        assertEquals(RunSessionState.COMPLETED, owner.state)

        // Assert: the history is the owner's own, at the timestamps it was given.
        assertEquals(listOf("PAUSE@1", "RESUME@2", "PAUSE@3"), transitionOutline(dao))
        val history = runBlocking { dao.transitionsFor(RUN_ID) }
        assertTrue(history.all { it.runId == owner.runId })
        assertEquals(
            listOf(FIRST_PAUSE, RESUME, SECOND_PAUSE),
            history.map { it.occurredAtEpochMillis }
        )

        // Assert: the decoy run never heard from this owner.
        assertEquals(decoyBefore, runBlocking { dao.findById(DECOY_RUN_ID) })
        assertTrue(transitionOutline(dao, DECOY_RUN_ID).isEmpty())

        // Assert: a completed run is over. Nothing else may be done through the owner.
        assertThrows(IllegalStateException::class.java) {
            runBlocking { owner.pause(FINISH + 1_000L) }
        }
        assertEquals(RunSessionState.COMPLETED, owner.state)
    }

    /**
     * Proves a pause storage refuses is not a pause the session believes in.
     */
    @Test
    fun `a pause storage failure propagates and leaves memory running`() {

        // Arrange: a DAO that cannot write the pause.
        val dao = FakeRunDao()
        val owner = startedOwner(dao)
        dao.failStateChangeWith = IllegalStateException("disk unavailable")

        // Act + assert: the failure reaches the caller unchanged.
        val thrown = assertThrows(IllegalStateException::class.java) {
            runBlocking { owner.pause(FIRST_PAUSE) }
        }
        assertEquals("disk unavailable", thrown.message)

        // Assert: memory never moved, and the stored run is untouched.
        assertEquals(RunSessionState.RUNNING, owner.state)
        assertEquals(StoredRunState.RUNNING, storedRun(dao).state)
        assertEquals(OFFICIAL_START, storedRun(dao).lastCheckpointEpochMillis)
        assertTrue(transitionOutline(dao).isEmpty())
    }

    /**
     * Proves a refused resume leaves the session paused rather than half-resumed.
     */
    @Test
    fun `a resume storage failure propagates and leaves memory paused`() {

        // Arrange: a genuinely paused run, then a DAO that cannot write the resume.
        val dao = FakeRunDao()
        val owner = startedOwner(dao)
        runBlocking { owner.pause(FIRST_PAUSE) }
        dao.failStateChangeWith = IllegalStateException("disk unavailable")

        // Act + assert
        val thrown = assertThrows(IllegalStateException::class.java) {
            runBlocking { owner.resume(RESUME) }
        }
        assertEquals("disk unavailable", thrown.message)

        // Assert: the run is exactly where the successful pause left it.
        assertEquals(RunSessionState.PAUSED, owner.state)
        assertEquals(StoredRunState.PAUSED, storedRun(dao).state)
        assertEquals(FIRST_PAUSE, storedRun(dao).lastCheckpointEpochMillis)
        assertEquals(listOf("PAUSE@1"), transitionOutline(dao))
    }

    /**
     * Proves a run whose ending was never stored is not treated as ended.
     *
     * This is the one that matters most to the runner: RunState may only say the run
     * is saved once storage says so.
     */
    @Test
    fun `a completion storage failure propagates and leaves memory paused`() {

        // Arrange
        val dao = FakeRunDao()
        val owner = startedOwner(dao)
        runBlocking { owner.pause(FIRST_PAUSE) }
        dao.failCompletionWith = IllegalStateException("disk unavailable")

        // Act + assert
        val thrown = assertThrows(IllegalStateException::class.java) {
            runBlocking { owner.complete(FINISH) }
        }
        assertEquals("disk unavailable", thrown.message)

        // Assert: still a paused run, with no finish invented for it.
        assertEquals(RunSessionState.PAUSED, owner.state)
        val stored = storedRun(dao)
        assertEquals(StoredRunState.PAUSED, stored.state)
        assertEquals(FIRST_PAUSE, stored.lastCheckpointEpochMillis)
        assertEquals(null, stored.finishEpochMillis)
    }

    /**
     * A [FakeRunDao] that also records how often each lifecycle method was called.
     *
     * Counting lives in the test rather than in production code: the owner has no
     * reason to expose call counts, and adding one only so a test could read it would
     * be the test changing the design it is meant to be checking.
     */
    private class CallCountingRunDao : FakeRunDao() {
        var pauseCalls = 0
        var resumeCalls = 0
        var completeCalls = 0

        override suspend fun pauseRun(runId: String, pausedAtEpochMillis: Long) {
            pauseCalls++
            super.pauseRun(runId, pausedAtEpochMillis)
        }

        override suspend fun resumeRun(runId: String, resumedAtEpochMillis: Long) {
            resumeCalls++
            super.resumeRun(runId, resumedAtEpochMillis)
        }

        override suspend fun completeRun(runId: String, finishEpochMillis: Long) {
            completeCalls++
            super.completeRun(runId, finishEpochMillis)
        }
    }

    /**
     * Proves an action memory already knows is illegal never reaches storage.
     *
     * Storage would refuse both of these anyway. Checking memory first means the app
     * does not spend a database round trip discovering something it already knew, and
     * it is why a rejection can name the session's own state in its message.
     */
    @Test
    fun `an action illegal in memory is refused before the dao is called`() {

        // Arrange: a running session, which can be neither resumed nor completed.
        val dao = CallCountingRunDao()
        val owner = startedOwner(dao)

        // Act + assert: resuming a run that is not paused.
        assertThrows(IllegalStateException::class.java) {
            runBlocking { owner.resume(RESUME) }
        }

        // Act + assert: completing a run that is not paused.
        assertThrows(IllegalStateException::class.java) {
            runBlocking { owner.complete(FINISH) }
        }

        // Assert: storage was never asked either question.
        assertEquals(0, dao.resumeCalls)
        assertEquals(0, dao.completeCalls)

        // Assert: nothing changed anywhere.
        assertEquals(RunSessionState.RUNNING, owner.state)
        assertEquals(StoredRunState.RUNNING, storedRun(dao).state)
        assertTrue(transitionOutline(dao).isEmpty())
    }

    /**
     * Proves storage, not this owner, has the final say on a transition.
     *
     * The stored row is moved behind the owner's back, so memory believes a pause is
     * legal while storage knows the run is already paused. Memory losing that argument
     * is the whole point of writing durably first.
     */
    @Test
    fun `storage refuses an action memory permits and nothing changes`() {

        // Arrange: an owner that still believes the run is RUNNING...
        val dao = FakeRunDao()
        val owner = startedOwner(dao)

        // ...while something else has already paused the stored run.
        runBlocking { dao.pauseRun(RUN_ID, FIRST_PAUSE) }
        assertEquals(RunSessionState.RUNNING, owner.state)

        // Act + assert: the owner's pause passes its own check, then storage refuses.
        assertThrows(IllegalStateException::class.java) {
            runBlocking { owner.pause(SECOND_PAUSE) }
        }

        // Assert: memory did not move on a write that never landed.
        assertEquals(RunSessionState.RUNNING, owner.state)

        // Assert: the stored row is exactly as the earlier pause left it.
        val stored = storedRun(dao)
        assertEquals(StoredRunState.PAUSED, stored.state)
        assertEquals(FIRST_PAUSE, stored.lastCheckpointEpochMillis)
        assertEquals(listOf("PAUSE@1"), transitionOutline(dao))
    }

    /**
     * Proves two overlapping pauses through one owner never both reach storage.
     *
     * This is a double-tapped pause button, and it is worth being precise about which
     * defense does what. Room already prevents a duplicate durable transition on its
     * own: once the first pause has landed, the conditional update no longer matches a
     * RUNNING row, so a second one is refused and the stored history could not hold two
     * PAUSE events either way. Counting stored events therefore proves less than it
     * looks like it does.
     *
     * What the owner's mutex protects is the plain in-memory state, which has no such
     * defense. Without it, both callers could read the same stale RUNNING and both
     * enter the DAO. With it, the loser waits, reads the PAUSED state the winner left
     * behind, and is refused before storage is ever asked — so the DAO sees exactly one
     * pause call. That count is the assertion that actually distinguishes the two.
     *
     * The coroutines are coordinated, not timed. CompletableDeferred parks the first
     * durable write until the test releases it, and the second call is launched
     * UNDISPATCHED so it runs immediately on this thread until it can go no further —
     * which, with the lock held, is the lock itself. Nothing here depends on a delay.
     */
    @Test
    fun `overlapping pauses record one pause and the loser is refused`() {

        // Arrange: one owner, shared by both callers, over a DAO that counts its calls.
        val dao = CallCountingRunDao()
        val owner = startedOwner(dao)

        // Arrange: handles for parking and releasing the first durable write.
        val firstWriteReached = CompletableDeferred<Unit>()
        val releaseFirstWrite = CompletableDeferred<Unit>()
        dao.duringStateChange = {
            firstWriteReached.complete(Unit)
            releaseFirstWrite.await()
        }

        var loserFailure: Throwable? = null

        runBlocking {

            // Act: the first caller runs until it is parked inside the pause write.
            val first = launch { owner.pause(FIRST_PAUSE) }
            firstWriteReached.await()

            // Act: a second pause arrives while the first is still unfinished.
            val second = launch(start = CoroutineStart.UNDISPATCHED) {
                loserFailure = runCatching { owner.pause(SECOND_PAUSE) }.exceptionOrNull()
            }

            // Act: let the first caller finish, then wait for both.
            releaseFirstWrite.complete(Unit)
            first.join()
            second.join()
        }

        // Assert: the loser was refused because it saw PAUSED, not a stale RUNNING.
        assertTrue(
            "Expected IllegalStateException, got $loserFailure",
            loserFailure is IllegalStateException
        )

        // Assert: the loser never reached storage at all. Room would have refused it
        // anyway, so this is what shows the mutex — not Room — turned it back.
        assertEquals(1, dao.pauseCalls)

        // Assert: one pause event, at the winner's timestamp.
        assertEquals(listOf("PAUSE@1"), transitionOutline(dao))
        assertEquals(
            FIRST_PAUSE,
            runBlocking { dao.transitionsFor(RUN_ID) }.single().occurredAtEpochMillis
        )

        // Assert: storage and memory agree on the one pause that happened.
        val stored = storedRun(dao)
        assertEquals(StoredRunState.PAUSED, stored.state)
        assertEquals(FIRST_PAUSE, stored.lastCheckpointEpochMillis)
        assertEquals(RunSessionState.PAUSED, owner.state)
    }
}
