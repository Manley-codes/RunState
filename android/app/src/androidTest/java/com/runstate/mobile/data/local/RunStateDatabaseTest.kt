package com.runstate.mobile.data.local

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the real Room database on a device or emulator.
 *
 * The database is file-backed and named rather than in-memory on purpose: an in-memory
 * database disappears when it is closed, so it could not show that a run survives the
 * database instance being thrown away and rebuilt.
 */
@RunWith(AndroidJUnit4::class)
class RunStateDatabaseTest {

    private companion object {
        const val DATABASE_NAME = "runstate-persistence-test.db"
        const val OFFICIAL_START = 1_756_000_000_000L

        /** A plausible lifecycle: ten minutes in, five paused, ending at half an hour. */
        const val PAUSED_AT = OFFICIAL_START + 600_000L
        const val RESUMED_AT = OFFICIAL_START + 900_000L
        const val FINISHED_AT = OFFICIAL_START + 1_800_000L
    }

    private lateinit var context: Context
    private lateinit var database: RunStateDatabase

    /** A representative run row: official, just started, checkpoint at the start. */
    private val runningRun = RunEntity(
        runId = "0f6a2c1e-9d43-4b7a-9c21-7b5e8a4d1f30",
        state = StoredRunState.RUNNING,
        officialStartEpochMillis = OFFICIAL_START,
        startTimezoneId = "America/Chicago",
        lastCheckpointEpochMillis = OFFICIAL_START
    )

    private fun openDatabase(): RunStateDatabase =
        Room.databaseBuilder(context, RunStateDatabase::class.java, DATABASE_NAME).build()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()

        // Delete first so a leftover file from an earlier run cannot make a test pass.
        context.deleteDatabase(DATABASE_NAME)
        database = openDatabase()
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(DATABASE_NAME)
    }

    /**
     * Proves a saved run is still there after the database instance is discarded.
     *
     * Closing and reopening under the same name proves the row reached the database
     * file on disk and is read back from it by a freshly built instance. It does NOT
     * prove full process-death recovery: the app process, its Room classes and this
     * test all stay alive throughout. Recovery after the process is actually killed is
     * separate behavior and is not covered here.
     */
    @Test
    fun runSurvivesClosingAndRebuildingTheDatabase() {

        // Arrange + act: save the run through the first database instance.
        runBlocking { database.runDao().insert(runningRun) }

        // Act: throw that instance away entirely and build a new one on the same file.
        database.close()
        database = openDatabase()

        // Assert: the same run comes back, field for field, under the same UUID.
        val restored = runBlocking { database.runDao().findById(runningRun.runId) }
        assertEquals(runningRun, restored)
    }

    /**
     * Proves a repeated UUID is refused instead of quietly overwriting the stored run.
     */
    @Test
    fun duplicateRunIdIsRejectedAndOriginalRowSurvives() {

        // Arrange: one stored run.
        runBlocking { database.runDao().insert(runningRun) }

        // Arrange: a different row wearing the same UUID.
        val duplicate = runningRun.copy(
            officialStartEpochMillis = OFFICIAL_START + 600_000L,
            startTimezoneId = "America/New_York",
            lastCheckpointEpochMillis = OFFICIAL_START + 600_000L
        )

        // Act + assert: ABORT surfaces the collision rather than replacing anything.
        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { database.runDao().insert(duplicate) }
        }

        // Assert: the original run is untouched, not the newer values.
        val stored = runBlocking { database.runDao().findById(runningRun.runId) }
        assertEquals(runningRun, stored)
    }

    // ---------------------------------------------------------------------------
    // Durable lifecycle updates: pause, resume and completion.
    //
    // These run against a real SQLite file rather than a fake, because the behavior
    // under test belongs to the database. Only a real transaction can roll a rejected
    // operation back, and only a real conditional write can lose a race it should lose.
    // ---------------------------------------------------------------------------

    /** Saves the running run so a lifecycle test starts from something real. */
    private fun storeRunningRun() {
        runBlocking { database.runDao().insert(runningRun) }
    }

    /** Reads the stored run, failing loudly rather than asserting against null. */
    private fun storedRun(): RunEntity =
        runBlocking { database.runDao().findById(runningRun.runId) }
            ?: throw AssertionError("The run under test is no longer stored.")

    /** Reads the stored run's pause/resume history in sequence order. */
    private fun storedTransitions(): List<RunTransitionEntity> =
        runBlocking { database.runDao().transitionsFor(runningRun.runId) }

    /** The `type@sequence` shape of the stored history, for readable comparisons. */
    private fun transitionOutline(): List<String> =
        storedTransitions().map { "${it.transitionType}@${it.sequenceNumber}" }

    /**
     * Proves pausing changes the run that already exists and records the moment.
     */
    @Test
    fun pauseUpdatesTheSameRunAndRecordsPauseSequenceOne() {

        // Arrange
        storeRunningRun()

        // Act
        runBlocking { database.runDao().pauseRun(runningRun.runId, PAUSED_AT) }

        // Assert: the same row, moved on — not a second row describing a paused run.
        val paused = storedRun()
        assertEquals(1, runBlocking { database.runDao().countRuns() })
        assertEquals(StoredRunState.PAUSED, paused.state)
        assertEquals(PAUSED_AT, paused.lastCheckpointEpochMillis)
        assertEquals(OFFICIAL_START, paused.officialStartEpochMillis)
        assertNull(paused.finishEpochMillis)

        // Assert: exactly one event, numbered from the start of this run's history.
        val event = storedTransitions().single()
        assertEquals(RunTransitionType.PAUSE, event.transitionType)
        assertEquals(1, event.sequenceNumber)
        assertEquals(PAUSED_AT, event.occurredAtEpochMillis)
    }

    /**
     * Proves resuming continues the same run and takes the next sequence number.
     */
    @Test
    fun resumeUpdatesTheSameRunAndRecordsResumeSequenceTwo() {

        // Arrange: a run that has already been paused.
        storeRunningRun()
        runBlocking { database.runDao().pauseRun(runningRun.runId, PAUSED_AT) }

        // Act
        runBlocking { database.runDao().resumeRun(runningRun.runId, RESUMED_AT) }

        // Assert
        val resumed = storedRun()
        assertEquals(1, runBlocking { database.runDao().countRuns() })
        assertEquals(StoredRunState.RUNNING, resumed.state)
        assertEquals(RESUMED_AT, resumed.lastCheckpointEpochMillis)

        // Assert: the pause is still there and the resume follows it.
        assertEquals(listOf("PAUSE@1", "RESUME@2"), transitionOutline())
        assertEquals(RESUMED_AT, storedTransitions().last().occurredAtEpochMillis)
    }

    /**
     * Proves a run that stops and starts repeatedly keeps its exact order.
     *
     * This is what the parent row alone could never answer: a run showing PAUSED could
     * have paused once or four times, and those are different runs with different
     * active durations.
     */
    @Test
    fun repeatedPauseAndResumeCyclesKeepTheirExactOrder() {

        // Arrange
        storeRunningRun()
        val dao = database.runDao()

        // Act: two full cycles, each a minute after the last event.
        runBlocking {
            dao.pauseRun(runningRun.runId, OFFICIAL_START + 60_000L)
            dao.resumeRun(runningRun.runId, OFFICIAL_START + 120_000L)
            dao.pauseRun(runningRun.runId, OFFICIAL_START + 180_000L)
            dao.resumeRun(runningRun.runId, OFFICIAL_START + 240_000L)
        }

        // Assert: four events, alternating, numbered without a gap or a repeat.
        assertEquals(
            listOf("PAUSE@1", "RESUME@2", "PAUSE@3", "RESUME@4"),
            transitionOutline()
        )
        assertEquals(
            listOf(
                OFFICIAL_START + 60_000L,
                OFFICIAL_START + 120_000L,
                OFFICIAL_START + 180_000L,
                OFFICIAL_START + 240_000L
            ),
            storedTransitions().map { it.occurredAtEpochMillis }
        )
        assertEquals(StoredRunState.RUNNING, storedRun().state)
    }

    /**
     * Proves two events may share a millisecond.
     *
     * A pause and an immediate resume can genuinely land in the same tick, so equality
     * has to be legal. The sequence number, not the clock, keeps them in order.
     */
    @Test
    fun aTimestampEqualToTheStoredCheckpointIsAccepted() {

        // Arrange: a pause, leaving the checkpoint exactly at PAUSED_AT.
        storeRunningRun()
        runBlocking { database.runDao().pauseRun(runningRun.runId, PAUSED_AT) }

        // Act: resume in the very same millisecond.
        runBlocking { database.runDao().resumeRun(runningRun.runId, PAUSED_AT) }

        // Assert: accepted, ordered by sequence rather than by an equal timestamp.
        assertEquals(listOf("PAUSE@1", "RESUME@2"), transitionOutline())
        assertEquals(StoredRunState.RUNNING, storedRun().state)
        assertEquals(PAUSED_AT, storedRun().lastCheckpointEpochMillis)
    }

    /**
     * Proves a timestamp before the stored checkpoint is refused, and refused whole.
     *
     * The checkpoint is a promise that everything through that instant is durable.
     * Accepting an earlier one would withdraw the promise, so the operation fails and
     * neither table keeps any part of it.
     */
    @Test
    fun anEarlierTimestampIsRejectedWithNoPartialChange() {

        // Arrange
        storeRunningRun()
        runBlocking { database.runDao().pauseRun(runningRun.runId, PAUSED_AT) }

        // Act + assert: a resume dated one millisecond before the pause.
        assertThrows(IllegalStateException::class.java) {
            runBlocking { database.runDao().resumeRun(runningRun.runId, PAUSED_AT - 1L) }
        }

        // Assert: the run is exactly where the pause left it.
        val unchanged = storedRun()
        assertEquals(StoredRunState.PAUSED, unchanged.state)
        assertEquals(PAUSED_AT, unchanged.lastCheckpointEpochMillis)

        // Assert: no half-written RESUME event was left behind.
        assertEquals(listOf("PAUSE@1"), transitionOutline())
    }

    /**
     * Proves an operation aimed at a run that is not stored changes nothing.
     */
    @Test
    fun aMissingRunIdChangesNothing() {

        // Arrange: one stored run, and a UUID belonging to no run at all.
        storeRunningRun()
        val unknownRunId = "9c8b7a65-4321-4def-8abc-0123456789ab"

        // Act + assert
        assertThrows(IllegalStateException::class.java) {
            runBlocking { database.runDao().pauseRun(unknownRunId, PAUSED_AT) }
        }

        // Assert: no run invented for the unknown id, and the real run untouched.
        assertEquals(1, runBlocking { database.runDao().countRuns() })
        assertEquals(runningRun, storedRun())
        assertTrue(storedTransitions().isEmpty())
        assertTrue(runBlocking { database.runDao().transitionsFor(unknownRunId) }.isEmpty())
    }

    /**
     * Proves storage, not the caller, decides whether a transition is legal.
     *
     * Resuming a run that storage still holds as RUNNING is a caller working from a
     * stale idea of the session. The stored state is the source of truth, so it wins.
     */
    @Test
    fun aPersistedStateMismatchChangesNothing() {

        // Arrange: a run that is running, not paused.
        storeRunningRun()

        // Act + assert
        assertThrows(IllegalStateException::class.java) {
            runBlocking { database.runDao().resumeRun(runningRun.runId, RESUMED_AT) }
        }

        // Assert
        assertEquals(runningRun, storedRun())
        assertTrue(storedTransitions().isEmpty())
    }

    /**
     * Proves completing ends the run that exists rather than saving a second one.
     */
    @Test
    fun completionEndsTheSameRunAndRecordsNoTransitionEvent() {

        // Arrange: a paused run, which is the only state a run may complete from.
        storeRunningRun()
        runBlocking { database.runDao().pauseRun(runningRun.runId, PAUSED_AT) }

        // Act
        runBlocking { database.runDao().completeRun(runningRun.runId, FINISHED_AT) }

        // Assert: still one run, now ended, with a real finish rather than a null one.
        assertEquals(1, runBlocking { database.runDao().countRuns() })
        val completed = storedRun()
        assertEquals(StoredRunState.COMPLETED, completed.state)
        assertEquals(FINISHED_AT, completed.finishEpochMillis)
        assertEquals(FINISHED_AT, completed.lastCheckpointEpochMillis)
        assertEquals(runningRun.runId, completed.runId)
        assertEquals(OFFICIAL_START, completed.officialStartEpochMillis)

        // Assert: completion is the run's end, not an event inside it, so the history
        // still holds only the pause.
        assertEquals(listOf("PAUSE@1"), transitionOutline())
    }

    /**
     * Proves a failed child insert takes the parent update back down with it.
     *
     * The other rejection tests all fail before anything has been written, so they would
     * still pass with no transaction anywhere. This one fails *after* the parent UPDATE
     * has already landed, which is the only situation where a rollback can actually be
     * observed: without `@Transaction`, the run would be left permanently PAUSED with no
     * record of when it paused.
     *
     * A trigger forces the failure rather than a fake DAO, because the write has to be
     * real enough to leave something behind if nothing undoes it.
     */
    @Test
    fun aFailedTransitionInsertRollsBackTheParentUpdate() {

        // Arrange
        storeRunningRun()
        val blockingTrigger = "block_run_transition_inserts"

        try {

            // Arrange: SQLite itself will now refuse every transition insert.
            database.openHelper.writableDatabase.execSQL(
                """
                CREATE TRIGGER $blockingTrigger
                BEFORE INSERT ON run_transitions
                BEGIN
                    SELECT RAISE(ABORT, 'transition insert blocked by test');
                END
                """.trimIndent()
            )

            // Act + assert: the pause reaches storage, updates the run, then fails.
            assertThrows(SQLiteException::class.java) {
                runBlocking { database.runDao().pauseRun(runningRun.runId, PAUSED_AT) }
            }
        } finally {

            // Dropped in `finally` so a failed assertion cannot leave the trigger armed
            // for the assertions below or for a later test.
            database.openHelper.writableDatabase.execSQL(
                "DROP TRIGGER IF EXISTS $blockingTrigger"
            )
        }

        // Assert: the successful parent UPDATE was undone, field for field.
        val unchanged = storedRun()
        assertEquals(runningRun, unchanged)
        assertEquals(StoredRunState.RUNNING, unchanged.state)
        assertEquals(OFFICIAL_START, unchanged.lastCheckpointEpochMillis)
        assertNull(unchanged.finishEpochMillis)

        // Assert: and no transition row survived the rollback either.
        assertTrue(storedTransitions().isEmpty())
    }

    /**
     * Proves a whole lifecycle is still there after the database instance is discarded.
     *
     * The earlier persistence test covered a single inserted row. This covers the part
     * that is genuinely new: updates and child rows written across several separate
     * transactions all reached the same file and come back as one coherent run.
     */
    @Test
    fun aCompletedLifecycleSurvivesClosingAndRebuildingTheDatabase() {

        // Arrange + act: the full journey through one database instance.
        storeRunningRun()
        runBlocking {
            database.runDao().pauseRun(runningRun.runId, PAUSED_AT)
            database.runDao().resumeRun(runningRun.runId, RESUMED_AT)
            database.runDao().pauseRun(runningRun.runId, FINISHED_AT - 1_000L)
            database.runDao().completeRun(runningRun.runId, FINISHED_AT)
        }

        // Act: throw that instance away and build a new one on the same file.
        database.close()
        database = openDatabase()

        // Assert: state, finish and checkpoint all survived.
        val restored = storedRun()
        assertEquals(StoredRunState.COMPLETED, restored.state)
        assertEquals(FINISHED_AT, restored.finishEpochMillis)
        assertEquals(FINISHED_AT, restored.lastCheckpointEpochMillis)

        // Assert: so did the ordered history that explains the run's active time.
        assertEquals(listOf("PAUSE@1", "RESUME@2", "PAUSE@3"), transitionOutline())
        assertEquals(
            listOf(PAUSED_AT, RESUMED_AT, FINISHED_AT - 1_000L),
            storedTransitions().map { it.occurredAtEpochMillis }
        )
    }

    /**
     * Proves overlapping operations cannot both act on the same source state.
     *
     * Eight coroutines try to pause the same running run at once. Nothing in the DAO
     * holds a lock, on purpose: the protection under test is the database's own, not a
     * mutex the callers happen to share.
     *
     * Each attempt reads the run, then writes with the source state repeated in its
     * WHERE clause. Whichever transaction commits first turns the row PAUSED, so every
     * later attempt matches nothing, updates zero rows, and throws — which rolls its
     * transaction back before any transition event can survive. One pause happened, so
     * exactly one PAUSE event exists.
     *
     * Scope: this proves one winning transition through this Room database instance. It
     * says nothing about two processes competing for the same file, which is separate
     * behavior and is not tested here.
     */
    @Test
    fun overlappingOperationsProduceOnlyOneTransitionFromOneSourceState() {

        // Arrange
        storeRunningRun()
        val attempts = 8

        // Act: all eight aim at the same run, the same source state, the same instant.
        val outcomes = runBlocking {
            (1..attempts)
                .map {
                    async(Dispatchers.IO) {
                        runCatching { database.runDao().pauseRun(runningRun.runId, PAUSED_AT) }
                    }
                }
                .awaitAll()
        }

        // Assert: one winner, and every loser refused rather than silently ignored.
        assertEquals(1, outcomes.count { it.isSuccess })
        assertEquals(attempts - 1, outcomes.count { it.isFailure })
        assertTrue(
            "Every loser must fail with IllegalStateException: $outcomes",
            outcomes.filter { it.isFailure }
                .all { it.exceptionOrNull() is IllegalStateException }
        )

        // Assert: one pause happened, so the durable record shows exactly one.
        assertEquals(listOf("PAUSE@1"), transitionOutline())
        assertEquals(StoredRunState.PAUSED, storedRun().state)
        assertEquals(1, runBlocking { database.runDao().countRuns() })
    }
}
