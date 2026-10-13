package com.runstate.mobile.data.local

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Version 1 to version 2: durable pause, resume and completion.
 *
 * Written by hand rather than auto-generated, and it only ever adds. Every statement
 * below creates something new; nothing drops, rewrites or reinterprets a version-1 row.
 * Destructive migration is deliberately not an option anywhere in this project — the
 * phone is the source of truth for a run, so rebuilding the database to dodge a
 * migration would delete real runs that exist nowhere else.
 *
 * Two things it pointedly does not do:
 *
 * It does not fill in a finish time. A version-1 database could contain a COMPLETED row,
 * and version 1 had nowhere to record when that run ended. The honest result is null.
 * Deriving one from the checkpoint would look like a repair while actually inventing a
 * fact the app would then present to the runner as a real finish time.
 *
 * It does not manufacture pause or resume events. A version-1 PAUSED row proves the run
 * was paused, not when, and a run that pauses once and a run that pauses six times both
 * look identical in version 1. A fabricated history would make an unknown look measured.
 *
 * All three version-1 states therefore survive as they are: RUNNING and PAUSED rows keep
 * an empty transition history, and a COMPLETED row keeps a null finish.
 *
 * The SQL below has to produce the same structure the exported version-2 schema
 * describes. Room compares the structure, not the text: on open it reads the actual
 * tables, columns, types, indices and foreign keys out of the database and rejects any
 * difference from the schema — so formatting is free, but a missing index or a
 * differently-typed column fails there.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {

    override fun migrate(connection: SQLiteConnection) {

        // Nullable and with no default, so existing rows get null rather than a
        // stand-in value that later code could mistake for a recorded finish.
        connection.execSQL(
            "ALTER TABLE `runs` ADD COLUMN `finish_epoch_millis` INTEGER"
        )

        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `run_transitions` (
                `run_id` TEXT NOT NULL,
                `sequence_number` INTEGER NOT NULL,
                `transition_type` TEXT NOT NULL,
                `occurred_at_epoch_millis` INTEGER NOT NULL,
                PRIMARY KEY(`run_id`, `sequence_number`),
                FOREIGN KEY(`run_id`) REFERENCES `runs`(`run_id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )

        // Named exactly as Room names it, since Room compares index names during
        // validation and treats a differently-named equivalent index as a mismatch.
        connection.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_run_transitions_run_id`
                ON `run_transitions` (`run_id`)
            """.trimIndent()
        )
    }
}

/**
 * Version 2 to version 3: completed fixture metrics and timeline provenance.
 *
 * Every addition is nullable and has no SQLite default. Existing rows therefore keep
 * null rather than receiving a guessed distance, source, display unit or claim that
 * their pause/resume history is complete. New version-3 runs write provenance when
 * they are created and finalize metrics only when completion succeeds.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {

    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE `runs` ADD COLUMN `final_distance_meters` REAL"
        )
        connection.execSQL(
            "ALTER TABLE `runs` ADD COLUMN `metric_source` TEXT"
        )
        connection.execSQL(
            "ALTER TABLE `runs` ADD COLUMN `telemetry_coverage` TEXT"
        )
        connection.execSQL(
            "ALTER TABLE `runs` ADD COLUMN `display_distance_unit` TEXT"
        )
        connection.execSQL(
            "ALTER TABLE `runs` ADD COLUMN `transition_history_complete` INTEGER"
        )
    }
}

/** Version 4 adds reflection storage without inventing replies for existing runs. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `run_reflections` (
                `run_id` TEXT NOT NULL,
                `request_id` TEXT NOT NULL,
                `evidence_json` TEXT NOT NULL,
                `selection_deadline_epoch_millis` INTEGER NOT NULL,
                `status` TEXT NOT NULL,
                `selected_branch` TEXT,
                `post_run_energy` TEXT,
                `candidates_json` TEXT,
                `text` TEXT,
                `finalized_at_epoch_millis` INTEGER,
                `is_test_data` INTEGER NOT NULL DEFAULT 1,
                PRIMARY KEY(`run_id`),
                FOREIGN KEY(`run_id`) REFERENCES `runs`(`run_id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
    }
}

/** Version 5 records only a choice the runner actually made before the run. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(connection: SQLiteConnection) {
        // Earlier versions never recorded this choice. NULL is unknown, not Moderate.
        connection.execSQL("ALTER TABLE `runs` ADD COLUMN `pre_run_energy` TEXT")
    }
}

/** Version 6 separates a run's chosen tracking mode and adds its owned GPS stream. */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(connection: SQLiteConnection) {
        // Older runs were not created with a stored mode. Their truth stays unknown.
        connection.execSQL("ALTER TABLE `runs` ADD COLUMN `tracking_mode` TEXT")
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `run_location_points` (
                `run_id` TEXT NOT NULL,
                `sequence_number` INTEGER NOT NULL,
                `observation_key` TEXT NOT NULL,
                `observed_at_epoch_millis` INTEGER NOT NULL,
                `elapsed_realtime_nanos` INTEGER,
                `latitude` REAL NOT NULL,
                `longitude` REAL NOT NULL,
                `horizontal_accuracy_meters` REAL,
                `altitude_meters` REAL,
                `speed_meters_per_second` REAL,
                `bearing_degrees` REAL,
                `accepted_for_distance` INTEGER NOT NULL,
                `rejection_reason` TEXT,
                `segment_distance_meters` REAL,
                `cumulative_distance_meters` REAL,
                PRIMARY KEY(`run_id`, `sequence_number`),
                FOREIGN KEY(`run_id`) REFERENCES `runs`(`run_id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_run_location_points_run_id` ON `run_location_points` (`run_id`)"
        )
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_run_location_points_run_id_observation_key` ON `run_location_points` (`run_id`, `observation_key`)"
        )
    }
}

/** Version 7 adds optional media-session evidence without assigning history to old runs. */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE `runs` ADD COLUMN `music_observation_requested` INTEGER NOT NULL DEFAULT 0"
        )
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `run_music_observations` (
                `run_id` TEXT NOT NULL,
                `sequence_number` INTEGER NOT NULL,
                `observation_key` TEXT NOT NULL,
                `observed_at_epoch_millis` INTEGER NOT NULL,
                `elapsed_realtime_nanos` INTEGER,
                `source_package` TEXT NOT NULL,
                `title` TEXT,
                `artist` TEXT,
                `playback_state` TEXT NOT NULL,
                `event_kind` TEXT NOT NULL,
                PRIMARY KEY(`run_id`, `sequence_number`),
                FOREIGN KEY(`run_id`) REFERENCES `runs`(`run_id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_run_music_observations_run_id` ON `run_music_observations` (`run_id`)"
        )
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_run_music_observations_run_id_observation_key` ON `run_music_observations` (`run_id`, `observation_key`)"
        )
    }
}

/** Version 8 keeps Effort unknown for runs that never had a chance to report it. */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `runs` ADD COLUMN `post_run_effort` TEXT")
    }
}
