package com.runstate.mobile.data.local

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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
}
