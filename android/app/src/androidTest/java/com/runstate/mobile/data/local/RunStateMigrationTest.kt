package com.runstate.mobile.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves a real version-1 database becomes a version-2 database without losing or
 * inventing anything.
 *
 * The database here is built from the exported version-1 schema rather than from
 * today's entity classes. That is the whole point: a migration has to be tested against
 * what was actually written to phones, not against the code's current idea of a run.
 *
 * Room never finds a migration on its own, so `MIGRATION_1_2` is always handed over
 * explicitly. `runMigrationsAndValidate` then compares the migrated file against the
 * generated version-2 schema and fails on any difference — a missing column, a wrong
 * type, an absent index — so a passing test also proves the hand-written SQL and the
 * entity classes agree.
 *
 * Those four tests check the migration itself. The last test checks something different
 * and equally necessary: that the builder the app actually ships has the migration
 * registered. A correct migration nobody registered is still a crash on the first
 * launch after an update.
 */
@RunWith(AndroidJUnit4::class)
class RunStateMigrationTest {

    private companion object {
        const val TEST_DB = "runstate-migration-test.db"

        /** A separate file, so the direct-open test cannot inherit a migrated one. */
        const val DIRECT_OPEN_TEST_DB = "runstate-factory-open-test.db"

        const val OFFICIAL_START = 1_756_000_000_000L
        const val RUN_ID = "0f6a2c1e-9d43-4b7a-9c21-7b5e8a4d1f30"
        const val TIMEZONE_ID = "America/Chicago"
    }

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        RunStateDatabase::class.java
    )

    /** The same context the helper writes its database files under. */
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /**
     * Writes a run the only way version 1 could: raw SQL against the version-1 table,
     * with no finish column and no transition table in existence.
     */
    private fun SupportSQLiteDatabase.insertVersionOneRun(
        state: String,
        lastCheckpointEpochMillis: Long = OFFICIAL_START
    ) {
        execSQL(
            """
            INSERT INTO runs (
                run_id, state, official_start_epoch_millis,
                start_timezone_id, last_checkpoint_epoch_millis
            ) VALUES (?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any>(RUN_ID, state, OFFICIAL_START, TIMEZONE_ID, lastCheckpointEpochMillis)
        )
    }

    /** Creates the version-1 database, lets [fill] populate it, and closes it. */
    private fun createVersionOneDatabase(
        databaseName: String = TEST_DB,
        fill: SupportSQLiteDatabase.() -> Unit = {}
    ) {
        helper.createDatabase(databaseName, 1).apply {
            fill()
            close()
        }
    }

    /**
     * Runs the migration, validates the result against the exported version-2 schema,
     * and closes the raw handle so Room can open the same file afterwards.
     */
    private fun migrateToVersionTwo() {
        helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2).close()
    }

    /**
     * Opens a database file through the real production factory.
     *
     * Reading back through the DAO is stronger than reading raw columns: it proves the
     * migrated row can still be turned into a `RunEntity`, constructor guards and all.
     *
     * It goes through [buildRunStateDatabase] rather than a `Room.databaseBuilder` of
     * its own so these tests exercise the builder the app actually ships, including its
     * migration list. A local copy of that builder would keep passing while production
     * lost `.addMigrations(MIGRATION_1_2)`, which is the one omission most worth
     * catching here.
     */
    private fun openDatabaseThroughFactory(
        databaseName: String = TEST_DB
    ): RunStateDatabase {
        val database = buildRunStateDatabase(context, databaseName)

        helper.closeWhenFinished(database)
        return database
    }

    /**
     * Proves an unfinished, still-running version-1 run comes through untouched.
     */
    @Test
    fun runningRunSurvivesMigrationWithEveryOriginalFieldIntact() {

        // Arrange: a version-1 run that is still going.
        createVersionOneDatabase { insertVersionOneRun(state = "RUNNING") }

        // Act
        migrateToVersionTwo()

        // Assert: same identity, same timeline, same state — and a finish that is
        // honestly unknown rather than guessed from the checkpoint.
        val migrated = openDatabaseThroughFactory()
        val restored = runBlocking { migrated.runDao().findById(RUN_ID) }
        assertEquals(
            RunEntity(
                runId = RUN_ID,
                state = StoredRunState.RUNNING,
                officialStartEpochMillis = OFFICIAL_START,
                startTimezoneId = TIMEZONE_ID,
                lastCheckpointEpochMillis = OFFICIAL_START,
                finishEpochMillis = null
            ),
            restored
        )

        // Assert: no history was fabricated for a run that never recorded one.
        assertTrue(runBlocking { migrated.runDao().transitionsFor(RUN_ID) }.isEmpty())
    }

    /**
     * Proves a version-1 PAUSED run stays readable with no invented pause history.
     *
     * Version 1 recorded that a run was paused but never when, and a run paused once
     * looks exactly like a run paused six times. Manufacturing a PAUSE event here would
     * turn that unknown into a measurement the app would then present as fact.
     */
    @Test
    fun pausedRunRemainsReadableWithNoInventedTransitions() {

        // Arrange: paused ten minutes after it started, which is all version 1 knew.
        val pausedCheckpoint = OFFICIAL_START + 600_000L
        createVersionOneDatabase {
            insertVersionOneRun(state = "PAUSED", lastCheckpointEpochMillis = pausedCheckpoint)
        }

        // Act
        migrateToVersionTwo()

        // Assert
        val migrated = openDatabaseThroughFactory()
        val restored = runBlocking { migrated.runDao().findById(RUN_ID) }
        assertEquals(StoredRunState.PAUSED, restored?.state)
        assertEquals(pausedCheckpoint, restored?.lastCheckpointEpochMillis)
        assertNull(restored?.finishEpochMillis)
        assertTrue(runBlocking { migrated.runDao().transitionsFor(RUN_ID) }.isEmpty())
    }

    /**
     * Proves a version-1 COMPLETED run stays readable with a null finish.
     *
     * Version 1 technically allowed a row to be inserted as COMPLETED, and it had
     * nowhere to record when that run ended. Null is the honest answer, and the entity
     * has to accept it or the app would crash reading its own stored history.
     */
    @Test
    fun completedRunRemainsReadableWithNoInventedFinish() {

        // Arrange: a finished version-1 run whose end time was never storable.
        val finalCheckpoint = OFFICIAL_START + 1_800_000L
        createVersionOneDatabase {
            insertVersionOneRun(state = "COMPLETED", lastCheckpointEpochMillis = finalCheckpoint)
        }

        // Act
        migrateToVersionTwo()

        // Assert: still completed, still readable, and still not claiming a finish.
        val migrated = openDatabaseThroughFactory()
        val restored = runBlocking { migrated.runDao().findById(RUN_ID) }
        assertEquals(StoredRunState.COMPLETED, restored?.state)
        assertEquals(finalCheckpoint, restored?.lastCheckpointEpochMillis)
        assertNull(restored?.finishEpochMillis)
        assertTrue(runBlocking { migrated.runDao().transitionsFor(RUN_ID) }.isEmpty())
    }

    /**
     * Proves the hand-written migration produces exactly the schema Room expects.
     *
     * `runMigrationsAndValidate` is the assertion: it throws if the migrated file
     * differs from the exported version-2 schema in any way. The empty database keeps
     * this test about the shape alone, with no rows involved.
     */
    @Test
    fun migratedSchemaMatchesTheGeneratedVersionTwoSchema() {

        // Arrange: version 1 with nothing in it.
        createVersionOneDatabase()

        // Act + assert: validation failure would surface as a thrown exception here.
        migrateToVersionTwo()

        // Assert: the new table exists and starts empty, so nothing was seeded.
        val migrated = openDatabaseThroughFactory()
        assertTrue(runBlocking { migrated.runDao().transitionsFor(RUN_ID) }.isEmpty())
        assertEquals(0, runBlocking { migrated.runDao().countRuns() })
    }

    /**
     * Proves the production factory itself carries the migration.
     *
     * The four tests above prove `MIGRATION_1_2` is correct, but every one of them hands
     * it to Room by hand. None of them would notice if `.addMigrations(MIGRATION_1_2)`
     * disappeared from [buildRunStateDatabase], because by the time they open the file it
     * is already a version-2 database with nothing left to migrate. That gap is exactly
     * the shape of a real upgrade failure: correct migration, never registered, and the
     * app crashes on the first launch after the update on every phone holding old data.
     *
     * So this test does the one thing they do not. It builds a genuine version-1 file,
     * deliberately skips `runMigrationsAndValidate`, and hands the un-migrated file
     * straight to the production factory. The migration can only happen if the factory
     * registered it.
     *
     * The DAO read is not incidental. Room opens lazily, so `buildRunStateDatabase`
     * returns without touching the file at all; the version check, the migration and any
     * "A migration from 1 to 2 was required but not found" failure all happen on first
     * use. Without a query, an unregistered migration would go undetected.
     */
    @Test
    fun theProductionFactoryMigratesAVersionOneDatabaseOnFirstUse() {

        // Arrange: a real version-1 database, holding a real version-1 run, closed.
        createVersionOneDatabase(DIRECT_OPEN_TEST_DB) {
            insertVersionOneRun(state = "RUNNING")
        }

        // Act: open that version-1 file through the production factory alone. Nothing
        // has migrated it, and no migration is passed in here.
        val database = openDatabaseThroughFactory(DIRECT_OPEN_TEST_DB)

        // Act: force the open. This is where Room migrates, or refuses to.
        val restored = runBlocking { database.runDao().findById(RUN_ID) }

        // Assert: the original row survived, readable as a version-2 entity, with the
        // finish honestly unknown rather than invented by the upgrade.
        assertEquals(
            RunEntity(
                runId = RUN_ID,
                state = StoredRunState.RUNNING,
                officialStartEpochMillis = OFFICIAL_START,
                startTimezoneId = TIMEZONE_ID,
                lastCheckpointEpochMillis = OFFICIAL_START,
                finishEpochMillis = null
            ),
            restored
        )

        // Assert: the version-2 table arrived with the migration and was not seeded.
        assertTrue(runBlocking { database.runDao().transitionsFor(RUN_ID) }.isEmpty())
    }
}
