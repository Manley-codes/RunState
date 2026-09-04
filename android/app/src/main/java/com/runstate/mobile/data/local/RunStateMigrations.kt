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
