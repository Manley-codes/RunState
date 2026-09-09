package com.runstate.mobile.run

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.runstate.mobile.data.local.RunEntity
import com.runstate.mobile.data.local.RunStateDatabase
import com.runstate.mobile.data.local.StoredRunState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves a paused run can be picked up again from the database file alone.
 *
 * The JVM tests already cover every recovery decision against a fake. What they cannot
 * show is that the decision survives the journey through real storage: that a paused
 * row written by one Room instance is still a paused row when a different instance
 * opens the same file, and that the run remains usable afterwards rather than merely
 * readable.
 *
 * ## What closing and reopening Room does and does not prove
 *
 * Closing the database and reopening the same file forces reconstruction from disk with
 * fresh memory. Every in-memory cache Room held is gone, the second `RunStateDatabase`
 * shares nothing with the first, and the state machine handed to recovery is brand new
 * and has never seen this run. So what comes back can only have come from the file.
 *
 * That is emphatically **not** Android process death. The app process here never dies:
 * the test runner, the JVM, the class loader and every static in it are the same
 * throughout. A real process kill is the operating system reclaiming the app while a
 * run is live, and proving RunState survives that needs the app actually wired to
 * recover at startup — which does not exist yet. This proves the reconstruction step of
 * that story, and no more.
 *
 * Deliberately one journey and no rejection cases; the exhaustive contract lives in the
 * JVM tests, where it runs in a fraction of the time.
 */
@RunWith(AndroidJUnit4::class)
class ActiveRunRecoveryRoomTest {

    private companion object {
        const val DATABASE_NAME = "runstate-active-recovery-test.db"
        const val OFFICIAL_START = 1_756_000_000_000L

        /** The checkpoint the paused run was left at, one minute in. */
        const val PAUSED_AT = OFFICIAL_START + 60_000L

        /** A resume two minutes in, comfortably after the stored checkpoint. */
        const val RESUME = OFFICIAL_START + 120_000L

        const val RUN_ID = "0f6a2c1e-9d43-4b7a-9c21-7b5e8a4d1f30"
        const val TIMEZONE_ID = "America/Chicago"
    }

    private lateinit var context: Context
    private lateinit var database: RunStateDatabase

    /**
     * A paused recovery fixture on disk, with no transition children.
     *
     * Written directly as PAUSED rather than started and then paused, so the row has no
     * history at all. That makes the closing assertions unambiguous — any PAUSE event
     * found afterwards could only have been invented, because there was never one to
     * begin with. This represents a stored recovery shape; it does not claim the test
     * actually killed the phone or app process.
     */
    private val pausedRun = RunEntity(
        runId = RUN_ID,
        state = StoredRunState.PAUSED,
        officialStartEpochMillis = OFFICIAL_START,
        startTimezoneId = TIMEZONE_ID,
        lastCheckpointEpochMillis = PAUSED_AT
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()

        // Delete first so a leftover file from an earlier run cannot make this pass.
        context.deleteDatabase(DATABASE_NAME)
        database = openDatabase()
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(DATABASE_NAME)
    }

    /** Opens the one on-disk database file this test uses. */
    private fun openDatabase(): RunStateDatabase =
        Room.databaseBuilder(context, RunStateDatabase::class.java, DATABASE_NAME).build()

    /**
     * Proves a paused run survives a closed database and can be resumed afterwards.
     */
    @Test
    fun aPausedRunIsRecoveredFromAReopenedDatabaseAndResumes() {

        // Arrange: store the paused run, then let go of the database entirely.
        runBlocking { database.runDao().insert(pausedRun) }
        database.close()

        // Arrange: reopen the same file with a fresh Room instance that shares no
        // memory with the first, and a machine that has never seen this run.
        database = openDatabase()
        val dao = database.runDao()
        val machine = RunSessionStateMachine()
        assertEquals(RunSessionState.NO_SESSION, machine.state)

        // Act: recover from the file alone.
        val result = runBlocking { ActiveRunRecovery(machine, dao).recover() }

        // Assert: one run was found and adopted.
        val recovered = result as ActiveRunRecoveryResult.Recovered

        // Assert: the snapshot is the stored run, field for field, with nothing lost or
        // regenerated in the round trip through SQLite.
        val runAtRecovery = recovered.runAtRecovery
        assertEquals(RUN_ID, runAtRecovery.runId)
        assertEquals(StoredRunState.PAUSED, runAtRecovery.state)
        assertEquals(OFFICIAL_START, runAtRecovery.officialStartEpochMillis)
        assertEquals(TIMEZONE_ID, runAtRecovery.startTimezoneId)
        assertEquals(PAUSED_AT, runAtRecovery.lastCheckpointEpochMillis)
        assertEquals(null, runAtRecovery.finishEpochMillis)

        // Assert: the new machine and the returned owner both landed on PAUSED.
        assertEquals(RunSessionState.PAUSED, machine.state)
        assertEquals(RUN_ID, recovered.session.runId)
        assertEquals(RunSessionState.PAUSED, recovered.session.state)

        // Assert: recovery invented no history to explain the pause it found.
        assertTrue(
            "Recovery must not write transition events.",
            runBlocking { dao.transitionsFor(RUN_ID) }.isEmpty()
        )

        // Act: carry on with the run through the recovered owner.
        runBlocking { recovered.session.resume(RESUME) }

        // Assert: the same row moved on, rather than a second run appearing.
        assertEquals(1, runBlocking { dao.countRuns() })
        val afterResume = runBlocking { dao.findById(RUN_ID) }
            ?: throw AssertionError("The recovered run is no longer stored.")
        assertEquals(StoredRunState.RUNNING, afterResume.state)
        assertEquals(OFFICIAL_START, afterResume.officialStartEpochMillis)

        // Assert: the checkpoint advanced to the resume it was actually given.
        assertEquals(RESUME, afterResume.lastCheckpointEpochMillis)
        assertEquals(RunSessionState.RUNNING, recovered.session.state)

        // Assert: exactly one event exists, and it is the resume that really happened.
        // Sequence 1 is the proof that no historical PAUSE was fabricated ahead of it —
        // a manufactured pause would have taken this number and pushed the resume to 2.
        val history = runBlocking { dao.transitionsFor(RUN_ID) }
        assertEquals(
            listOf("RESUME@1"),
            history.map { "${it.transitionType}@${it.sequenceNumber}" }
        )
        assertEquals(RESUME, history.single().occurredAtEpochMillis)
    }
}
