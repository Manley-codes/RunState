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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Joins the two halves against a real database, once, end to end.
 *
 * Everything below is already proved in isolation: `RunStateDatabaseTest` covers the DAO
 * contract on real SQLite, and `ActiveRunSessionTest` covers the owner's ordering and
 * failure behavior against a fake. What neither can show is that the pieces fit — that
 * a countdown really becomes a stored, owned run whose pauses, resume and completion all
 * land on one row through [ActiveRunSession].
 *
 * So this is deliberately one journey and no rejection cases. Repeating the DAO's
 * exhaustive contract here would only make the same assertions slower to run.
 */
@RunWith(AndroidJUnit4::class)
class ActiveRunSessionRoomTest {

    private companion object {
        const val DATABASE_NAME = "runstate-active-session-test.db"
        const val OFFICIAL_START = 1_756_000_000_000L

        /** Pause at one minute, resume at two, pause at three, end at four. */
        const val FIRST_PAUSE = OFFICIAL_START + 60_000L
        const val RESUME = OFFICIAL_START + 120_000L
        const val SECOND_PAUSE = OFFICIAL_START + 180_000L
        const val FINISH = OFFICIAL_START + 240_000L

        const val RUN_ID = "0f6a2c1e-9d43-4b7a-9c21-7b5e8a4d1f30"
    }

    private lateinit var context: Context
    private lateinit var database: RunStateDatabase

    /** The row a real countdown hands over: RUNNING, checkpoint at the official start. */
    private val preparedRun = RunEntity(
        runId = RUN_ID,
        state = StoredRunState.RUNNING,
        officialStartEpochMillis = OFFICIAL_START,
        startTimezoneId = "America/Chicago",
        lastCheckpointEpochMillis = OFFICIAL_START
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()

        // Delete first so a leftover file from an earlier run cannot make this pass.
        context.deleteDatabase(DATABASE_NAME)
        database = Room.databaseBuilder(context, RunStateDatabase::class.java, DATABASE_NAME)
            .build()
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(DATABASE_NAME)
    }

    /**
     * Proves a countdown becomes one owned run that reaches COMPLETED in real storage.
     */
    @Test
    fun oneOwnerCarriesARealRunFromCountdownToCompleted() {

        // Arrange: a real machine at the only stage a run may start from.
        val machine = RunSessionStateMachine().apply { beginCountdown() }
        val dao = database.runDao()

        // Act: cross the start boundary, then run the journey through the owner alone.
        val owner = runBlocking {
            val session = RunSessionStarter(machine, dao).start(preparedRun)

            session.pause(FIRST_PAUSE)
            session.resume(RESUME)
            session.pause(SECOND_PAUSE)
            session.complete(FINISH)

            session
        }

        // Assert: one run, and it is the run the countdown prepared.
        assertEquals(1, runBlocking { dao.countRuns() })
        val stored = runBlocking { dao.findById(RUN_ID) }
            ?: throw AssertionError("The run under test is no longer stored.")
        assertEquals(RUN_ID, owner.runId)

        // Assert: ended, at the finish it was given, with the start it began with.
        assertEquals(StoredRunState.COMPLETED, stored.state)
        assertEquals(OFFICIAL_START, stored.officialStartEpochMillis)
        assertEquals(FINISH, stored.finishEpochMillis)
        assertEquals(FINISH, stored.lastCheckpointEpochMillis)

        // Assert: the pauses and the resume are all there, in the order they happened.
        val history = runBlocking { dao.transitionsFor(RUN_ID) }
        assertEquals(
            listOf("PAUSE@1", "RESUME@2", "PAUSE@3"),
            history.map { "${it.transitionType}@${it.sequenceNumber}" }
        )
        assertEquals(
            listOf(FIRST_PAUSE, RESUME, SECOND_PAUSE),
            history.map { it.occurredAtEpochMillis }
        )

        // Assert: memory ended up saying what storage says.
        assertEquals(RunSessionState.COMPLETED, owner.state)
    }
}
