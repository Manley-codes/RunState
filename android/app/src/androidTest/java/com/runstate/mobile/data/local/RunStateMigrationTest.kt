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
 * Proves real version-1, version-2 and version-3 databases reach the current version without
 * losing or inventing anything.
 *
 * The database here is built from the exported version-1 schema rather than from
 * today's entity classes. That is the whole point: a migration has to be tested against
 * what was actually written to phones, not against the code's current idea of a run.
 *
 * Room never finds a migration on its own, so each step is handed over explicitly.
 * `runMigrationsAndValidate` then compares the migrated file against the exported target
 * schema and fails on any difference — a missing column, a wrong type, an absent index —
 * so a passing test also proves the hand-written SQL and the entity classes agree.
 *
 * The direct migration tests check each step itself. The factory tests check something
 * different and equally necessary: that the builder the app actually ships registers
 * the complete chain. A correct migration nobody registered is still a crash on the
 * first launch after an update.
 */
@RunWith(AndroidJUnit4::class)
class RunStateMigrationTest {

    private companion object {
        const val TEST_DB = "runstate-migration-test.db"

        /** A separate file, so the direct-open test cannot inherit a migrated one. */
        const val DIRECT_OPEN_TEST_DB = "runstate-factory-open-test.db"

        const val VERSION_TWO_ACTIVE_DB = "runstate-v2-active-migration-test.db"
        const val VERSION_TWO_COMPLETED_DB = "runstate-v2-completed-migration-test.db"
        const val VERSION_TWO_SHAPE_DB = "runstate-v2-shape-migration-test.db"
        const val VERSION_TWO_FACTORY_DB = "runstate-v2-factory-open-test.db"

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

    @Test
    fun versionFourteenMigrationPreservesAllFieldsAndEveryPreparationState() {
        val name = "runstate-v14-multi-song-migration-test.db"
        val old = helper.createDatabase(name, 14)
        val identities = listOf(
            RUN_ID to "a8ddf13c-316b-44c4-bfa0-740012266a82",
            "03689ffc-65d9-4907-ae77-7a17b3ad77a6" to "3fc37cf9-3d3a-4d80-a08e-f9d5c14bbdf5",
            "8c405ab0-4a80-4be9-9bfb-19f2517ec2ba" to "b5bec493-b0cd-44a4-a2f6-cd3e43489819"
        )
        identities.forEachIndexed { index, (runId, preparationId) ->
            old.insertVersionTwoRun(runId = runId, state = "RUNNING",
                lastCheckpointEpochMillis = OFFICIAL_START + 30_000, finishEpochMillis = null)
            old.execSQL("UPDATE runs SET transition_history_complete=1, music_observation_requested=1, " +
                "music_reflection_sharing_allowed=1 WHERE run_id=?", arrayOf(runId))
            old.execSQL("""
                INSERT INTO run_song_preparations (run_id,preparation_id,track_title,artist_name,
                    observation_sequence_number,destination_fingerprint,claimed_at_epoch_millis,status)
                VALUES (?,?,?,'Exact artist',1,?,?,?)
            """.trimIndent(), arrayOf<Any>(runId, preparationId, "Exact title — $index", "a".repeat(64),
                OFFICIAL_START + 30_000, listOf("CLAIMED", "READY", "UNAVAILABLE")[index]))
        }
        old.execSQL("""
            INSERT INTO run_music_observations (run_id,sequence_number,observation_key,
                observed_at_epoch_millis,elapsed_realtime_nanos,source_package,title,artist,playback_state,event_kind)
            VALUES (?,1,'old-song',?,1000000,'com.spotify.music','Exact title — 0','Exact artist','PLAYING','SESSION_SNAPSHOT')
        """.trimIndent(), arrayOf<Any>(RUN_ID, OFFICIAL_START + 1_000))
        val tables = listOf("runs", "run_transitions", "run_location_points", "run_music_observations",
            "run_reflections", "run_weather", "run_split_snapshots", "run_splits", "run_song_preparations")
        val columns = tables.associateWith { table ->
            old.query("SELECT * FROM `$table` LIMIT 0").use { it.columnNames.toList() }
        }
        fun rows(db: SupportSQLiteDatabase, table: String): List<List<String?>> =
            db.query("SELECT * FROM `$table` ORDER BY 1,2").use { cursor -> buildList {
                while (cursor.moveToNext()) add(columns.getValue(table).indices.map { index ->
                    if (cursor.isNull(index)) null else cursor.getString(index)
                })
            } }
        val before = tables.associateWith { rows(old, it) }
        old.close()
        helper.runMigrationsAndValidate(name, 15, true, MIGRATION_14_15).use { migrated ->
            tables.forEach { table ->
                migrated.query("SELECT * FROM `$table` LIMIT 0").use {
                    assertEquals(table, columns.getValue(table), it.columnNames.toList())
                }
                assertEquals(table, before.getValue(table), rows(migrated, table))
            }
        }
        val reopened = openDatabaseThroughFactory(name)
        runBlocking {
            val dao = reopened.musicObservationDao()
            identities.forEachIndexed { index, (runId, preparationId) ->
                val claim = dao.preparationsFor(runId).single()
                assertEquals(preparationId, claim.preparationId)
                assertEquals(listOf(SongPreparationClaimStatus.CLAIMED, SongPreparationClaimStatus.READY,
                    SongPreparationClaimStatus.UNAVAILABLE)[index], claim.status)
            }
            val observation = dao.recordObservation(RunMusicObservation(RUN_ID, "new-song",
                OFFICIAL_START + 31_000, null, "com.spotify.music", "New song", "New artist",
                MusicPlaybackState.PLAYING, MusicObservationKind.METADATA_CHANGED))
            val claim = dao.claimPreparation(RUN_ID, "789f76bc-4cd7-4891-b6ce-d956417ebc88",
                observation.sequenceNumber, "a".repeat(64), OFFICIAL_START + 32_000)
            assertEquals("New song", claim?.trackTitle)
            assertEquals(2, dao.preparationsFor(RUN_ID).size)
            assertEquals(identities.first().second, dao.preparationFor(RUN_ID)?.preparationId)
        }
    }

    @Test
    fun versionThirteenMigrationPreservesEveryOldFieldAndCreatesNoPreparationClaims() {
        val name = "runstate-v13-song-preparation-migration-test.db"
        val old = helper.createDatabase(name, 13)
        val finish = OFFICIAL_START + 60_000
        old.insertVersionTwoRun(state = "COMPLETED", lastCheckpointEpochMillis = finish, finishEpochMillis = finish)
        old.execSQL("""
            UPDATE runs SET transition_history_complete=1, music_observation_requested=1,
                music_reflection_sharing_allowed=1, pre_run_energy='LOW', post_run_effort='SMOOTH'
            WHERE run_id=?
        """.trimIndent(), arrayOf(RUN_ID))
        old.execSQL("""
            INSERT INTO run_music_observations (run_id,sequence_number,observation_key,
                observed_at_epoch_millis,elapsed_realtime_nanos,source_package,title,artist,playback_state,event_kind)
            VALUES (?,1,'old-observation',?,1000000,'com.spotify.music','Exact title','Exact artist','PLAYING','SESSION_SNAPSHOT')
        """.trimIndent(), arrayOf<Any>(RUN_ID, OFFICIAL_START + 1_000))
        old.execSQL("""
            INSERT INTO run_reflections (run_id,request_id,evidence_json,selection_deadline_epoch_millis,
                status,selected_branch,post_run_energy,candidates_json,text,finalized_at_epoch_millis,is_test_data,selection_window_started)
            VALUES (?,?,'{"preserve":"evidence"}',?,'READY','SPENT','SPENT',NULL,?,?,0,1)
        """.trimIndent(), arrayOf<Any>(RUN_ID, "03689ffc-65d9-4907-ae77-7a17b3ad77a6", finish + 6_000,
            "  Exact old reflection.\nUnicode — 🏃  ", finish + 7_000))
        val tables = listOf("runs", "run_transitions", "run_location_points", "run_music_observations",
            "run_reflections", "run_weather", "run_split_snapshots", "run_splits")
        val columns = tables.associateWith { table ->
            old.query("SELECT * FROM `$table` LIMIT 0").use { it.columnNames.toList() }
        }
        fun rows(db: SupportSQLiteDatabase, table: String): List<List<String?>> =
            db.query("SELECT * FROM `$table` ORDER BY 1").use { cursor -> buildList {
                while (cursor.moveToNext()) add(columns.getValue(table).indices.map { index ->
                    if (cursor.isNull(index)) null else cursor.getString(index)
                })
            } }
        val before = tables.associateWith { rows(old, it) }
        old.close()
        helper.runMigrationsAndValidate(name, 14, true, MIGRATION_13_14).use { migrated ->
            tables.forEach { table ->
                migrated.query("SELECT * FROM `$table` LIMIT 0").use {
                    assertEquals(table, columns.getValue(table), it.columnNames.toList())
                }
                assertEquals(table, before.getValue(table), rows(migrated, table))
            }
            migrated.query("SELECT COUNT(*) FROM run_song_preparations").use {
                assertTrue(it.moveToFirst())
                assertEquals("Older runs must not gain an early preparation attempt.", 0, it.getInt(0))
            }
        }
        val reopened = openDatabaseThroughFactory(name)
        runBlocking {
            assertNull(reopened.musicObservationDao().preparationFor(RUN_ID))
            assertEquals("Exact title", reopened.musicObservationDao().observationsFor(RUN_ID).single().title)
            assertEquals("  Exact old reflection.\nUnicode — 🏃  ", reopened.reflectionDao().findByRunId(RUN_ID)?.text)
        }
    }

    @Test
    fun versionTwelveMigrationPreservesEveryAppFieldWithoutBackfillingSplits() {
        val name = "runstate-v12-split-migration-test.db"
        val activeId = "03689ffc-65d9-4907-ae77-7a17b3ad77a6"
        val sampleId = "8c405ab0-4a80-4be9-9bfb-19f2517ec2ba"
        val finish = OFFICIAL_START + 60_000L
        val old = helper.createDatabase(name, 12)
        old.apply {
            insertVersionTwoRun(state = "COMPLETED", lastCheckpointEpochMillis = finish, finishEpochMillis = finish)
            insertVersionTwoRun(runId = activeId, state = "PAUSED",
                lastCheckpointEpochMillis = OFFICIAL_START + 30_000L, finishEpochMillis = null)
            insertVersionTwoRun(runId = sampleId, state = "COMPLETED",
                lastCheckpointEpochMillis = finish, finishEpochMillis = finish)
            execSQL("""
                UPDATE runs SET final_distance_meters=150.5, metric_source='GPS',
                    telemetry_coverage='COMPLETE', display_distance_unit='MILES',
                    transition_history_complete=1, pre_run_energy='HIGH', tracking_mode='GPS',
                    music_observation_requested=1, post_run_effort='SMOOTH',
                    music_reflection_sharing_allowed=1, start_elapsed_realtime_millis=123000,
                    timing_session_id='2280842d-649a-4f15-aa80-6c7fe621a52e' WHERE run_id=?
            """.trimIndent(), arrayOf(RUN_ID))
            execSQL("UPDATE runs SET tracking_mode='FIXTURE' WHERE run_id=?", arrayOf(sampleId))
            execSQL("""
                INSERT INTO run_transitions (run_id,sequence_number,transition_type,
                    occurred_at_epoch_millis,elapsed_realtime_millis,timing_session_id)
                VALUES (?,1,'PAUSE',?,153000,'2280842d-649a-4f15-aa80-6c7fe621a52e')
            """.trimIndent(), arrayOf<Any>(RUN_ID, OFFICIAL_START + 30_000))
            execSQL("""
                INSERT INTO run_location_points (run_id,sequence_number,observation_key,
                    observed_at_epoch_millis,elapsed_realtime_nanos,latitude,longitude,
                    horizontal_accuracy_meters,accepted_for_distance,segment_distance_meters,cumulative_distance_meters)
                VALUES (?,1,'preserved-location',?,123000000000,40.0,-75.0,3.0,1,150.5,150.5)
            """.trimIndent(), arrayOf<Any>(RUN_ID, OFFICIAL_START + 1_000))
            execSQL("""
                INSERT INTO run_music_observations (run_id,sequence_number,observation_key,
                    observed_at_epoch_millis,elapsed_realtime_nanos,source_package,title,artist,playback_state,event_kind)
                VALUES (?,1,'preserved-music',?,123000000000,'test.player','Stored title','Stored artist','PLAYING','SESSION_SNAPSHOT')
            """.trimIndent(), arrayOf<Any>(RUN_ID, OFFICIAL_START + 1_000))
            execSQL("""
                INSERT INTO run_reflections (run_id,request_id,evidence_json,selection_deadline_epoch_millis,
                    status,selected_branch,post_run_energy,candidates_json,text,finalized_at_epoch_millis,
                    is_test_data,selection_window_started)
                VALUES (?,?,'{"keep":"evidence"}',?,'READY','FEELING_GOOD','FEELING_GOOD',
                    '{"keep":"candidates"}',?,?,0,1)
            """.trimIndent(), arrayOf<Any>(RUN_ID, activeId, finish + 6_000, "  Exact saved text.\nUnicode — 🏃  ", finish + 7_000))
            execSQL("INSERT INTO run_weather (run_id,status,snapshot_json) VALUES (?,'READY',?)",
                arrayOf<Any>(RUN_ID, "{\"keep\":\"weather payload\"}"))
        }
        val tables = listOf("runs", "run_transitions", "run_location_points", "run_music_observations", "run_reflections", "run_weather")
        val columns = tables.associateWith { table ->
            old.query("SELECT * FROM `$table` LIMIT 0").use { it.columnNames.toList() }
        }
        fun rows(db: SupportSQLiteDatabase, table: String): List<List<String?>> =
            db.query("SELECT * FROM `$table` ORDER BY 1, 2").use { cursor -> buildList {
                while (cursor.moveToNext()) add(columns.getValue(table).indices.map { index ->
                    if (cursor.isNull(index)) null else cursor.getString(index)
                })
            } }
        val before = tables.associateWith { rows(old, it) }
        old.close()
        helper.runMigrationsAndValidate(name, 13, true, MIGRATION_12_13).use { migrated ->
            tables.forEach { table ->
                migrated.query("SELECT * FROM `$table` LIMIT 0").use {
                    assertEquals(table, columns.getValue(table), it.columnNames.toList())
                }
                assertEquals(table, before.getValue(table), rows(migrated, table))
            }
            listOf("run_split_snapshots", "run_splits").forEach { table ->
                migrated.query("SELECT COUNT(*) FROM `$table`").use {
                    assertTrue(it.moveToFirst())
                    assertEquals("No historical run may gain split evidence.", 0, it.getInt(0))
                }
            }
        }
        val upgraded = openDatabaseThroughFactory(name)
        runBlocking {
            assertEquals(3, upgraded.runDao().countRuns())
            val saved = requireNotNull(upgraded.runDao().findById(RUN_ID))
            assertEquals(150.5, requireNotNull(saved.finalDistanceMeters), 0.0)
            assertEquals("2280842d-649a-4f15-aa80-6c7fe621a52e", saved.timingSessionId)
            assertTrue(upgraded.splitDao().pendingRunIds().isEmpty())
            assertNull(upgraded.splitDao().snapshotFor(RUN_ID))
        }
    }

    @Test
    fun versionElevenMigrationPreservesEveryStoredFieldAndStartsWithNoWeather() {
        val databaseName = "runstate-v11-weather-migration-test.db"
        val activeId = "03689ffc-65d9-4907-ae77-7a17b3ad77a6"
        val pausedId = "8c405ab0-4a80-4be9-9bfb-19f2517ec2ba"
        val finish = OFFICIAL_START + 60_000L
        val exactReply = "  Exact saved reply.\nUnicode — 🌤 stays.  "
        val oldDatabase = helper.createDatabase(databaseName, 11)
        oldDatabase.apply {
            insertVersionTwoRun(state = "COMPLETED", lastCheckpointEpochMillis = finish, finishEpochMillis = finish)
            insertVersionTwoRun(runId = activeId, state = "RUNNING",
                lastCheckpointEpochMillis = OFFICIAL_START, finishEpochMillis = null)
            insertVersionTwoRun(runId = pausedId, state = "PAUSED",
                lastCheckpointEpochMillis = OFFICIAL_START + 30_000L, finishEpochMillis = null)
            execSQL("""
                UPDATE runs SET final_distance_meters=12.34, metric_source='GPS',
                    telemetry_coverage='PARTIAL', display_distance_unit='MILES',
                    transition_history_complete=1, pre_run_energy='LOW', tracking_mode='GPS',
                    music_observation_requested=1, post_run_effort='WORKING',
                    music_reflection_sharing_allowed=1, start_elapsed_realtime_millis=123000,
                    timing_session_id='original-clock' WHERE run_id=?
            """.trimIndent(), arrayOf(RUN_ID))
            execSQL("""
                UPDATE runs SET tracking_mode='GPS', transition_history_complete=1,
                    pre_run_energy='HIGH', start_elapsed_realtime_millis=456000,
                    timing_session_id='active-clock' WHERE run_id=?
            """.trimIndent(), arrayOf(activeId))
            execSQL("""
                INSERT INTO run_transitions (run_id,sequence_number,transition_type,
                    occurred_at_epoch_millis,elapsed_realtime_millis,timing_session_id)
                VALUES (?,1,'PAUSE',?,153000,'original-clock')
            """.trimIndent(), arrayOf<Any>(RUN_ID, OFFICIAL_START + 30_000L))
            execSQL("""
                INSERT INTO run_location_points (run_id,sequence_number,observation_key,
                    observed_at_epoch_millis,elapsed_realtime_nanos,latitude,longitude,horizontal_accuracy_meters,
                    altitude_meters,speed_meters_per_second,bearing_degrees,
                    accepted_for_distance,rejection_reason,segment_distance_meters,cumulative_distance_meters)
                VALUES (?,1,'anchor',?,1000000000,40.0,-75.0,3.0,25.0,1.5,90.0,1,NULL,NULL,NULL),
                       (?,2,'rejected',?,2000000000,40.0001,-75.0,99.0,NULL,NULL,NULL,0,'POOR_ACCURACY',NULL,NULL)
            """.trimIndent(), arrayOf<Any>(RUN_ID, OFFICIAL_START + 1_000L, RUN_ID, OFFICIAL_START + 2_000L))
            execSQL("""
                INSERT INTO run_music_observations (run_id,sequence_number,observation_key,
                    observed_at_epoch_millis,elapsed_realtime_nanos,source_package,title,artist,playback_state,event_kind)
                VALUES (?,1,'music',?,2000000000,'test.player','Stored track','Stored artist','PLAYING','SESSION_SNAPSHOT')
            """.trimIndent(), arrayOf<Any>(RUN_ID, OFFICIAL_START + 2_000L))
            execSQL("""
                INSERT INTO run_reflections (run_id,request_id,evidence_json,selection_deadline_epoch_millis,
                    status,selected_branch,post_run_energy,candidates_json,text,finalized_at_epoch_millis,
                    is_test_data,selection_window_started)
                VALUES (?,?,'{"preserve":"exact evidence"}',?,'READY','SPENT','SPENT',
                    '{"preserve":"exact candidates"}',?,?,0,1)
            """.trimIndent(), arrayOf<Any>(RUN_ID, pausedId, finish + 6_000L, exactReply, finish + 7_000L))
        }
        val tableNames = listOf("runs", "run_transitions", "run_location_points", "run_music_observations", "run_reflections")
        fun rows(database: SupportSQLiteDatabase, table: String, columns: List<String>): List<List<String?>> =
            database.query("SELECT ${columns.joinToString(",") { "`$it`" }} FROM `$table` ORDER BY 1, 2").use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(columns.indices.map { index -> if (cursor.isNull(index)) null else cursor.getString(index) })
                    }
                }
            }
        val oldColumns = tableNames.associateWith { table ->
            oldDatabase.query("SELECT * FROM `$table` LIMIT 0").use { it.columnNames.toList() }
        }
        val before = tableNames.associateWith { rows(oldDatabase, it, oldColumns.getValue(it)) }
        oldDatabase.close()

        helper.runMigrationsAndValidate(databaseName, 12, true, MIGRATION_11_12).use { migrated ->
            tableNames.forEach { table ->
                assertEquals(table, before.getValue(table), rows(migrated, table, oldColumns.getValue(table)))
            }
            migrated.query("SELECT COUNT(*) FROM run_weather").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }

            // A weather row follows only its own run through deletion.
            migrated.execSQL("PRAGMA foreign_keys=ON")
            migrated.execSQL("INSERT INTO run_weather (run_id,status,snapshot_json) VALUES (?,'READY','{}')",
                arrayOf(activeId))
            migrated.execSQL("DELETE FROM runs WHERE run_id=?", arrayOf(activeId))
            migrated.query("SELECT COUNT(*) FROM run_weather").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
            migrated.query("SELECT COUNT(*) FROM runs").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(2, cursor.getInt(0))
            }
        }
    }

    @Test
    fun productionFactoryMigratesVersionElevenAndWeatherAttemptsSettleOnlyOnce() {
        val databaseName = "runstate-v11-weather-factory-test.db"
        val completedId = "03689ffc-65d9-4907-ae77-7a17b3ad77a6"
        val disabledId = "8c405ab0-4a80-4be9-9bfb-19f2517ec2ba"
        val abandonedId = "17b32e6c-8ec5-4f58-ad11-360816ab4729"
        val fixtureId = "8ba990b9-51f8-4449-9d2f-a38460af0ba0"
        val pausedId = "cd6fde6d-3f7a-4c3b-938b-dd38e79a255f"
        helper.createDatabase(databaseName, 11).apply {
            listOf(RUN_ID, disabledId, abandonedId, fixtureId).forEach { id ->
                insertVersionTwoRun(runId = id, state = "RUNNING",
                    lastCheckpointEpochMillis = OFFICIAL_START, finishEpochMillis = null)
            }
            insertVersionTwoRun(runId = pausedId, state = "PAUSED",
                lastCheckpointEpochMillis = OFFICIAL_START, finishEpochMillis = null)
            insertVersionTwoRun(runId = completedId, state = "COMPLETED",
                lastCheckpointEpochMillis = OFFICIAL_START + 60_000L,
                finishEpochMillis = OFFICIAL_START + 60_000L)
            execSQL("UPDATE runs SET tracking_mode='GPS', start_elapsed_realtime_millis=123000, timing_session_id='2280842d-649a-4f15-aa80-6c7fe621a52e'")
            execSQL("UPDATE runs SET tracking_mode='FIXTURE' WHERE run_id=?", arrayOf(fixtureId))
            close()
        }

        val migrated = openDatabaseThroughFactory(databaseName)
        runBlocking {
            val restored = requireNotNull(migrated.runDao().findById(RUN_ID))
            assertEquals(OFFICIAL_START, restored.officialStartEpochMillis)
            assertEquals(123000L, restored.startElapsedRealtimeMillis)
            assertEquals("2280842d-649a-4f15-aa80-6c7fe621a52e", restored.timingSessionId)
            val weather = migrated.weatherDao()
            assertNull(weather.find(RUN_ID))
            assertEquals(false, weather.claim(completedId, allowed = true))
            assertNull(weather.find(completedId))
            assertEquals(false, weather.claim(fixtureId, allowed = true))
            assertNull(weather.find(fixtureId))
            assertEquals(false, weather.claim(pausedId, allowed = true))
            assertNull(weather.find(pausedId))
            assertEquals(false, weather.claim(disabledId, allowed = false))
            assertEquals(false, weather.claim(disabledId, allowed = true))
            weather.finish(disabledId, "{}")
            assertEquals(RunWeatherStatus.DISABLED, weather.find(disabledId)?.status)

            assertTrue(weather.claim(RUN_ID, allowed = true))
            assertEquals(RunWeatherStatus.PENDING, weather.find(RUN_ID)?.status)
            weather.finish(RUN_ID, "{\"preserve\":\"first snapshot\"}")
            weather.finish(RUN_ID, null)
            assertEquals(false, weather.claim(RUN_ID, allowed = true))
            assertEquals(RunWeatherEntity(RUN_ID, RunWeatherStatus.READY,
                "{\"preserve\":\"first snapshot\"}"), weather.find(RUN_ID))
            assertNull(weather.find(RUN_ID)?.readySnapshot())

            assertTrue(weather.claim(abandonedId, allowed = true))
            assertEquals(false, weather.claim(abandonedId, allowed = true))
            weather.finish(abandonedId, "{\"too\":\"late\"}")
            assertEquals(RunWeatherEntity(abandonedId, RunWeatherStatus.UNAVAILABLE), weather.find(abandonedId))
        }
    }

    @Test
    fun versionTenMigrationPreservesEveryStoredFieldAndLeavesTimingUnknown() {
        val databaseName = "runstate-v10-timing-migration-test.db"
        val activeId = "03689ffc-65d9-4907-ae77-7a17b3ad77a6"
        val pausedId = "8c405ab0-4a80-4be9-9bfb-19f2517ec2ba"
        val finish = OFFICIAL_START + 60_000L
        val exactReply = "  Exact saved reply.\nUnicode — 🌤 stays.  "
        val oldDatabase = helper.createDatabase(databaseName, 10)
        oldDatabase.apply {
            insertVersionTwoRun(state = "COMPLETED", lastCheckpointEpochMillis = finish, finishEpochMillis = finish)
            insertVersionTwoRun(runId = activeId, state = "RUNNING",
                lastCheckpointEpochMillis = OFFICIAL_START, finishEpochMillis = null)
            insertVersionTwoRun(runId = pausedId, state = "PAUSED",
                lastCheckpointEpochMillis = OFFICIAL_START + 30_000L, finishEpochMillis = null)
            execSQL("""
                UPDATE runs SET final_distance_meters=12.34, metric_source='GPS',
                    telemetry_coverage='PARTIAL', display_distance_unit='MILES',
                    transition_history_complete=1, pre_run_energy='LOW', tracking_mode='GPS',
                    music_observation_requested=1, post_run_effort='WORKING',
                    music_reflection_sharing_allowed=1 WHERE run_id=?
            """.trimIndent(), arrayOf(RUN_ID))
            execSQL("INSERT INTO run_transitions (run_id,sequence_number,transition_type,occurred_at_epoch_millis) VALUES (?,1,'PAUSE',?)",
                arrayOf<Any>(RUN_ID, OFFICIAL_START + 30_000L))
            execSQL("""
                INSERT INTO run_location_points (run_id,sequence_number,observation_key,
                    observed_at_epoch_millis,elapsed_realtime_nanos,latitude,longitude,horizontal_accuracy_meters,
                    altitude_meters,speed_meters_per_second,bearing_degrees,
                    accepted_for_distance,rejection_reason,segment_distance_meters,cumulative_distance_meters)
                VALUES (?,1,'anchor',?,1000000000,40.0,-75.0,3.0,25.0,1.5,90.0,1,NULL,NULL,NULL),
                       (?,2,'rejected',?,2000000000,40.0001,-75.0,99.0,NULL,NULL,NULL,0,'POOR_ACCURACY',NULL,NULL)
            """.trimIndent(), arrayOf<Any>(RUN_ID, OFFICIAL_START + 1_000L, RUN_ID, OFFICIAL_START + 2_000L))
            execSQL("""
                INSERT INTO run_music_observations (run_id,sequence_number,observation_key,
                    observed_at_epoch_millis,elapsed_realtime_nanos,source_package,title,artist,playback_state,event_kind)
                VALUES (?,1,'music',?,2000000000,'test.player','Stored track','Stored artist','PLAYING','SESSION_SNAPSHOT')
            """.trimIndent(), arrayOf<Any>(RUN_ID, OFFICIAL_START + 2_000L))
            execSQL("""
                INSERT INTO run_reflections (run_id,request_id,evidence_json,selection_deadline_epoch_millis,
                    status,selected_branch,post_run_energy,text,finalized_at_epoch_millis,is_test_data,selection_window_started)
                VALUES (?,?,'{"preserve":"exact evidence"}',?,'READY','SPENT','SPENT',?,?,0,1)
            """.trimIndent(), arrayOf<Any>(RUN_ID, pausedId, finish + 6_000L, exactReply, finish + 7_000L))
        }
        val tableNames = listOf("runs", "run_transitions", "run_location_points", "run_music_observations", "run_reflections")
        fun rows(database: SupportSQLiteDatabase, table: String, columns: List<String>): List<List<String?>> =
            database.query("SELECT ${columns.joinToString(",") { "`$it`" }} FROM `$table` ORDER BY 1, 2").use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(columns.indices.map { index -> if (cursor.isNull(index)) null else cursor.getString(index) })
                    }
                }
            }
        val oldColumns = tableNames.associateWith { table ->
            oldDatabase.query("SELECT * FROM `$table` LIMIT 0").use { it.columnNames.toList() }
        }
        val before = tableNames.associateWith { rows(oldDatabase, it, oldColumns.getValue(it)) }
        oldDatabase.close()

        helper.runMigrationsAndValidate(databaseName, 11, true, MIGRATION_10_11).use { migrated ->
            tableNames.forEach { table -> assertEquals(table, before.getValue(table), rows(migrated, table, oldColumns.getValue(table))) }
            migrated.query("SELECT start_elapsed_realtime_millis,timing_session_id FROM runs").use { cursor ->
                assertEquals(3, cursor.count)
                while (cursor.moveToNext()) { assertTrue(cursor.isNull(0)); assertTrue(cursor.isNull(1)) }
            }
            migrated.query("SELECT elapsed_realtime_millis,timing_session_id FROM run_transitions").use { cursor ->
                assertEquals(1, cursor.count)
                assertTrue(cursor.moveToFirst())
                assertTrue(cursor.isNull(0))
                assertTrue(cursor.isNull(1))
            }
        }
        // Also prove the production builder registers 10 -> 11 rather than only the test helper.
        val directName = "runstate-v10-timing-factory-test.db"
        helper.createDatabase(directName, 10).apply {
            insertVersionTwoRun(state = "RUNNING", lastCheckpointEpochMillis = OFFICIAL_START, finishEpochMillis = null)
            close()
        }
        val migrated = openDatabaseThroughFactory(directName)
        val restored = runBlocking { requireNotNull(migrated.runDao().findById(RUN_ID)) }
        assertNull(restored.startElapsedRealtimeMillis)
        assertNull(restored.timingSessionId)
        assertEquals(OFFICIAL_START, restored.officialStartEpochMillis)
    }

    @Test
    fun versionThreeMigrationPreservesRunsMetricsAndTransitionsWithoutInventingReflections() {
        val databaseName = "runstate-v3-reflection-migration-test.db"
        val finishedAt = OFFICIAL_START + 60_000
        helper.createDatabase(databaseName, 3).apply {
            insertVersionTwoRun(
                state = "COMPLETED",
                lastCheckpointEpochMillis = finishedAt,
                finishEpochMillis = finishedAt
            )
            execSQL(
                """
                UPDATE runs SET final_distance_meters = 123.456,
                    metric_source = 'FIXTURE', telemetry_coverage = 'PARTIAL',
                    display_distance_unit = 'MILES', transition_history_complete = 1
                WHERE run_id = ?
                """.trimIndent(),
                arrayOf(RUN_ID)
            )
            execSQL(
                "INSERT INTO run_transitions (run_id, sequence_number, transition_type, occurred_at_epoch_millis) VALUES (?, 1, 'PAUSE', ?)",
                arrayOf<Any>(RUN_ID, finishedAt)
            )
            close()
        }
        helper.runMigrationsAndValidate(databaseName, 4, true, MIGRATION_3_4).close()
        val migrated = openDatabaseThroughFactory(databaseName)
        runBlocking {
            assertEquals(
                RunEntity(
                    runId = RUN_ID,
                    state = StoredRunState.COMPLETED,
                    officialStartEpochMillis = OFFICIAL_START,
                    startTimezoneId = TIMEZONE_ID,
                    lastCheckpointEpochMillis = finishedAt,
                    finishEpochMillis = finishedAt,
                    finalDistanceMeters = 123.456,
                    metricSource = MetricSource.FIXTURE,
                    telemetryCoverage = TelemetryCoverage.PARTIAL,
                    displayDistanceUnit = DistanceUnit.MILES,
                    transitionHistoryComplete = true
                ),
                migrated.runDao().findById(RUN_ID)
            )
            assertEquals(
                listOf(RunTransitionEntity(RUN_ID, 1, RunTransitionType.PAUSE, finishedAt)),
                migrated.runDao().transitionsFor(RUN_ID)
            )
            assertTrue(migrated.reflectionDao().all().isEmpty())
            val pending = RunReflectionEntity(
                runId = RUN_ID,
                requestId = "03689ffc-65d9-4907-ae77-7a17b3ad77a6",
                evidenceJson = "{}",
                selectionDeadlineEpochMillis = finishedAt + 6_000
            )
            assertEquals(pending, migrated.reflectionDao().ensurePending(pending))
        }
    }

    @Test
    fun versionFourMigrationLeavesUnknownPreRunEnergyUnknown() {
        val databaseName = "runstate-v4-energy-migration-test.db"
        helper.createDatabase(databaseName, 4).apply {
            insertVersionTwoRun(
                state = "RUNNING",
                lastCheckpointEpochMillis = OFFICIAL_START,
                finishEpochMillis = null
            )
            close()
        }

        helper.runMigrationsAndValidate(databaseName, 5, true, MIGRATION_4_5).close()

        val migrated = openDatabaseThroughFactory(databaseName)
        val restored = runBlocking { migrated.runDao().findById(RUN_ID) }
            ?: throw AssertionError("The version-4 run was lost.")
        assertEquals(RUN_ID, restored.runId)
        assertNull(restored.preRunEnergy)
    }

    @Test
    fun versionFiveMigrationKeepsOldTrackingModeUnknownAndAddsNoGpsPoints() {
        val databaseName = "runstate-v5-gps-migration-test.db"
        helper.createDatabase(databaseName, 5).apply {
            insertVersionTwoRun(
                state = "RUNNING",
                lastCheckpointEpochMillis = OFFICIAL_START,
                finishEpochMillis = null
            )
            execSQL("UPDATE runs SET pre_run_energy = 'LOW' WHERE run_id = ?", arrayOf(RUN_ID))
            close()
        }

        helper.runMigrationsAndValidate(databaseName, 6, true, MIGRATION_5_6).close()

        val migrated = openDatabaseThroughFactory(databaseName)
        val restored = runBlocking { migrated.runDao().findById(RUN_ID) }
            ?: throw AssertionError("The version-5 run was lost.")
        assertEquals(PreRunEnergy.LOW, restored.preRunEnergy)
        assertNull(restored.trackingMode)
        assertTrue(runBlocking { migrated.locationDao().pointsFor(RUN_ID) }.isEmpty())
    }

    @Test
    fun versionSixMigrationDefaultsMusicObservationOffWithoutInventingEvents() {
        val databaseName = "runstate-v6-music-migration-test.db"
        helper.createDatabase(databaseName, 6).apply {
            insertVersionTwoRun(
                state = "RUNNING",
                lastCheckpointEpochMillis = OFFICIAL_START,
                finishEpochMillis = null
            )
            close()
        }

        helper.runMigrationsAndValidate(databaseName, 7, true, MIGRATION_6_7).close()

        val migrated = openDatabaseThroughFactory(databaseName)
        val restored = runBlocking { migrated.runDao().findById(RUN_ID) }
            ?: throw AssertionError("The version-6 run was lost.")
        assertEquals(false, restored.musicObservationRequested)
        assertTrue(runBlocking { migrated.musicObservationDao().observationsFor(RUN_ID) }.isEmpty())
    }

    @Test
    fun versionSevenMigrationKeepsEffortUnknownOnExistingCompletedRun() {
        val databaseName = "runstate-v7-effort-migration-test.db"
        val finishedAt = OFFICIAL_START + 900_000L
        helper.createDatabase(databaseName, 7).apply {
            insertVersionTwoRun(
                state = "COMPLETED",
                lastCheckpointEpochMillis = finishedAt,
                finishEpochMillis = finishedAt
            )
            execSQL(
                "UPDATE runs SET pre_run_energy = 'HIGH', music_observation_requested = 1 WHERE run_id = ?",
                arrayOf(RUN_ID)
            )
            close()
        }

        helper.runMigrationsAndValidate(databaseName, 8, true, MIGRATION_7_8).close()

        val migrated = openDatabaseThroughFactory(databaseName)
        val restored = runBlocking { migrated.runDao().findById(RUN_ID) }
            ?: throw AssertionError("The version-7 completed run was lost.")
        assertEquals(StoredRunState.COMPLETED, restored.state)
        assertEquals(finishedAt, restored.finishEpochMillis)
        assertEquals(PreRunEnergy.HIGH, restored.preRunEnergy)
        assertEquals(true, restored.musicObservationRequested)
        assertNull(restored.postRunEffort)
    }

    @Test
    fun versionEightMigrationKeepsExistingReflectionWindowsAndExactReadyText() {
        val databaseName = "runstate-v8-receipt-migration-test.db"
        val secondRunId = "03689ffc-65d9-4907-ae77-7a17b3ad77a6"
        val requestId = "8c405ab0-4a80-4be9-9bfb-19f2517ec2ba"
        val deadline = OFFICIAL_START + 66_000L
        val exactReply = "  Exact saved reply.\nWith Unicode — 🌤  "
        helper.createDatabase(databaseName, 8).apply {
            listOf(RUN_ID, secondRunId).forEach { runId ->
                insertVersionTwoRun(
                    runId = runId, state = "COMPLETED",
                    lastCheckpointEpochMillis = OFFICIAL_START + 60_000L,
                    finishEpochMillis = OFFICIAL_START + 60_000L
                )
            }
            execSQL(
                "INSERT INTO run_reflections (run_id, request_id, evidence_json, selection_deadline_epoch_millis, status) VALUES (?, ?, '{}', ?, 'PENDING')",
                arrayOf<Any>(RUN_ID, requestId, deadline)
            )
            execSQL(
                """
                INSERT INTO run_reflections (
                    run_id, request_id, evidence_json, selection_deadline_epoch_millis,
                    status, selected_branch, text, finalized_at_epoch_millis, is_test_data
                ) VALUES (?, ?, '{}', ?, 'READY', 'SPENT', ?, ?, 0)
                """.trimIndent(),
                arrayOf<Any>(secondRunId, requestId, deadline, exactReply, deadline)
            )
            close()
        }
        helper.runMigrationsAndValidate(databaseName, 9, true, MIGRATION_8_9).close()
        val migrated = openDatabaseThroughFactory(databaseName)
        runBlocking {
            val pending = requireNotNull(migrated.reflectionDao().findByRunId(RUN_ID))
            assertTrue(pending.selectionWindowStarted)
            assertEquals(deadline, pending.selectionDeadlineEpochMillis)
            assertEquals(ReflectionStatus.PENDING, pending.status)
            val ready = requireNotNull(migrated.reflectionDao().findByRunId(secondRunId))
            assertTrue(ready.selectionWindowStarted)
            assertEquals(deadline, ready.selectionDeadlineEpochMillis)
            assertEquals(exactReply, ready.text)
            assertEquals(ReflectionStatus.READY, ready.status)
            assertEquals(false, ready.isTestData)
            assertEquals(2, migrated.runDao().countRuns())
        }
    }

    @Test
    fun versionNineMigrationPreservesRecordedDataWithoutGrantingMusicSharing() {
        val databaseName = "runstate-v9-sharing-migration-test.db"
        val secondRunId = "03689ffc-65d9-4907-ae77-7a17b3ad77a6"
        val requestId = "8c405ab0-4a80-4be9-9bfb-19f2517ec2ba"
        val finish = OFFICIAL_START + 60_000L
        val exactReply = "  Exact prior reply.\nUnicode — 🌤 stays.  "
        helper.createDatabase(databaseName, 9).apply {
            insertVersionTwoRun(state = "COMPLETED", lastCheckpointEpochMillis = finish, finishEpochMillis = finish)
            insertVersionTwoRun(runId = secondRunId, state = "RUNNING",
                lastCheckpointEpochMillis = OFFICIAL_START, finishEpochMillis = null)
            execSQL("""
                UPDATE runs SET final_distance_meters=12.34, metric_source='GPS',
                    telemetry_coverage='PARTIAL', display_distance_unit='MILES',
                    transition_history_complete=1, pre_run_energy='LOW', tracking_mode='GPS',
                    music_observation_requested=1, post_run_effort='WORKING' WHERE run_id=?
            """.trimIndent(), arrayOf(RUN_ID))
            execSQL("INSERT INTO run_transitions (run_id,sequence_number,transition_type,occurred_at_epoch_millis) VALUES (?,1,'PAUSE',?)",
                arrayOf<Any>(RUN_ID, OFFICIAL_START + 30_000L))
            execSQL("""
                INSERT INTO run_location_points (run_id,sequence_number,observation_key,
                    observed_at_epoch_millis,latitude,longitude,horizontal_accuracy_meters,
                    accepted_for_distance,segment_distance_meters,cumulative_distance_meters)
                VALUES (?,1,'anchor',?,40.0,-75.0,3.0,1,NULL,NULL),
                       (?,2,'segment',?,40.0001,-75.0,3.0,1,12.34,12.34)
            """.trimIndent(), arrayOf<Any>(RUN_ID, OFFICIAL_START + 1_000L, RUN_ID, OFFICIAL_START + 2_000L))
            execSQL("""
                INSERT INTO run_music_observations (run_id,sequence_number,observation_key,
                    observed_at_epoch_millis,source_package,title,artist,playback_state,event_kind)
                VALUES (?,1,'music',?,'test.player','Stored track','Stored artist','PLAYING','SESSION_SNAPSHOT')
            """.trimIndent(), arrayOf<Any>(RUN_ID, OFFICIAL_START + 2_000L))
            execSQL("""
                INSERT INTO run_reflections (run_id,request_id,evidence_json,selection_deadline_epoch_millis,
                    status,selected_branch,text,finalized_at_epoch_millis,is_test_data,selection_window_started)
                VALUES (?,?,'{}',?,'READY','SPENT',?,?,0,1)
            """.trimIndent(), arrayOf<Any>(RUN_ID, requestId, finish + 6_000L, exactReply, finish + 7_000L))
            close()
        }
        helper.runMigrationsAndValidate(databaseName, 10, true, MIGRATION_9_10).close()
        val migrated = openDatabaseThroughFactory(databaseName)
        runBlocking {
            val restored = requireNotNull(migrated.runDao().findById(RUN_ID))
            val other = requireNotNull(migrated.runDao().findById(secondRunId))
            assertEquals(false, restored.musicReflectionSharingAllowed)
            assertEquals(false, other.musicReflectionSharingAllowed)
            assertEquals(true, restored.musicObservationRequested)
            assertEquals(false, other.musicObservationRequested)
            assertEquals(12.34, requireNotNull(restored.finalDistanceMeters), 0.0)
            assertEquals(RunTrackingMode.GPS, restored.trackingMode)
            assertEquals(TelemetryCoverage.PARTIAL, restored.telemetryCoverage)
            assertEquals(PreRunEnergy.LOW, restored.preRunEnergy)
            assertEquals(PostRunEffort.WORKING, restored.postRunEffort)
            assertEquals(finish, restored.finishEpochMillis)
            assertEquals(listOf(RunTransitionEntity(RUN_ID, 1, RunTransitionType.PAUSE, OFFICIAL_START + 30_000L)),
                migrated.runDao().transitionsFor(RUN_ID))
            val points = migrated.locationDao().pointsFor(RUN_ID)
            assertEquals(2, points.size)
            assertNull(points[0].segmentDistanceMeters)
            assertEquals(12.34, requireNotNull(points[1].segmentDistanceMeters), 0.0)
            assertEquals(12.34, requireNotNull(points[1].cumulativeDistanceMeters), 0.0)
            val observations = migrated.musicObservationDao().observationsFor(RUN_ID)
            assertEquals(1, observations.size)
            assertEquals("Stored track", observations.single().title)
            assertEquals("Stored artist", observations.single().artist)
            assertEquals(MusicPlaybackState.PLAYING, observations.single().playbackState)
            val reply = requireNotNull(migrated.reflectionDao().findByRunId(RUN_ID))
            assertEquals(requestId, reply.requestId)
            assertEquals(exactReply, reply.text)
            assertEquals(ReflectionStatus.READY, reply.status)
            assertEquals(false, reply.isTestData)
            assertEquals(true, reply.selectionWindowStarted)
            assertEquals(2, migrated.runDao().countRuns())
        }
    }

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

    /** Creates a genuine version-2 file from the preserved exported schema. */
    private fun createVersionTwoDatabase(
        databaseName: String,
        fill: SupportSQLiteDatabase.() -> Unit = {}
    ) {
        helper.createDatabase(databaseName, 2).apply {
            fill()
            close()
        }
    }

    /** Writes the exact parent-row shape available in version 2. */
    private fun SupportSQLiteDatabase.insertVersionTwoRun(
        runId: String = RUN_ID,
        state: String,
        lastCheckpointEpochMillis: Long,
        finishEpochMillis: Long?
    ) {
        execSQL(
            """
            INSERT INTO runs (
                run_id, state, official_start_epoch_millis,
                start_timezone_id, last_checkpoint_epoch_millis, finish_epoch_millis
            ) VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any?>(
                runId,
                state,
                OFFICIAL_START,
                TIMEZONE_ID,
                lastCheckpointEpochMillis,
                finishEpochMillis
            )
        )
    }

    /** Runs only the new additive step and validates it against schema version 3. */
    private fun migrateVersionTwoToThree(databaseName: String) {
        helper.runMigrationsAndValidate(databaseName, 3, true, MIGRATION_2_3).close()
    }

    /** Every version-3 addition must remain unknown on a migrated version-2 row. */
    private fun assertVersionThreeFieldsWereNotInvented(run: RunEntity) {
        assertNull(run.finalDistanceMeters)
        assertNull(run.metricSource)
        assertNull(run.telemetryCoverage)
        assertNull(run.displayDistanceUnit)
        assertNull(run.transitionHistoryComplete)
        assertEquals(false, run.musicReflectionSharingAllowed)
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
     * lost one of its `.addMigrations(...)` entries, which is the omission these factory
     * tests exist to catch.
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

        // Assert: the original row survived the full chain, readable as a current entity,
        // with the finish and version-3 fields honestly unknown rather than invented.
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

        // Assert: the transition table arrived through the migration chain and was not seeded.
        assertTrue(runBlocking { database.runDao().transitionsFor(RUN_ID) }.isEmpty())
    }

    /** A live version-2 row survives with no invented metric or history provenance. */
    @Test
    fun activeVersionTwoRunMigratesToVersionThreeWithNewFieldsUnknown() {
        createVersionTwoDatabase(VERSION_TWO_ACTIVE_DB) {
            insertVersionTwoRun(
                state = "RUNNING",
                lastCheckpointEpochMillis = OFFICIAL_START,
                finishEpochMillis = null
            )
        }

        migrateVersionTwoToThree(VERSION_TWO_ACTIVE_DB)

        val database = openDatabaseThroughFactory(VERSION_TWO_ACTIVE_DB)
        val restored = runBlocking { database.runDao().findById(RUN_ID) }
            ?: throw AssertionError("The migrated active run is missing.")
        assertEquals(StoredRunState.RUNNING, restored.state)
        assertEquals(OFFICIAL_START, restored.officialStartEpochMillis)
        assertEquals(OFFICIAL_START, restored.lastCheckpointEpochMillis)
        assertNull(restored.finishEpochMillis)
        assertVersionThreeFieldsWereNotInvented(restored)
    }

    /** A completed version-2 row keeps its finish but gains no fabricated metrics. */
    @Test
    fun completedVersionTwoRunMigratesToVersionThreeWithNewFieldsUnknown() {
        val finish = OFFICIAL_START + 1_800_000L
        createVersionTwoDatabase(VERSION_TWO_COMPLETED_DB) {
            insertVersionTwoRun(
                state = "COMPLETED",
                lastCheckpointEpochMillis = finish,
                finishEpochMillis = finish
            )
        }

        migrateVersionTwoToThree(VERSION_TWO_COMPLETED_DB)

        val database = openDatabaseThroughFactory(VERSION_TWO_COMPLETED_DB)
        val restored = runBlocking { database.runDao().findById(RUN_ID) }
            ?: throw AssertionError("The migrated completed run is missing.")
        assertEquals(StoredRunState.COMPLETED, restored.state)
        assertEquals(finish, restored.finishEpochMillis)
        assertEquals(finish, restored.lastCheckpointEpochMillis)
        assertVersionThreeFieldsWereNotInvented(restored)
    }

    /** The five ALTER statements produce exactly the current Room schema. */
    @Test
    fun versionTwoToThreeMigrationMatchesTheGeneratedSchema() {
        createVersionTwoDatabase(VERSION_TWO_SHAPE_DB)
        migrateVersionTwoToThree(VERSION_TWO_SHAPE_DB)

        val database = openDatabaseThroughFactory(VERSION_TWO_SHAPE_DB)
        assertEquals(0, runBlocking { database.runDao().countRuns() })
    }

    /** Production registers the new migration rather than relying on test-only wiring. */
    @Test
    fun productionFactoryMigratesVersionTwoOnFirstUse() {
        createVersionTwoDatabase(VERSION_TWO_FACTORY_DB) {
            insertVersionTwoRun(
                state = "RUNNING",
                lastCheckpointEpochMillis = OFFICIAL_START,
                finishEpochMillis = null
            )
        }

        val database = openDatabaseThroughFactory(VERSION_TWO_FACTORY_DB)
        val restored = runBlocking { database.runDao().findById(RUN_ID) }
            ?: throw AssertionError("The production migration lost the version-2 run.")
        assertVersionThreeFieldsWereNotInvented(restored)
    }
}
