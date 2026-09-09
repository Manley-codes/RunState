package com.runstate.mobile.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The on-phone store for runs.
 *
 * Version 2 adds the run's finish time and its pause/resume history. Its exported
 * schema under `android/app/schemas` sits beside the version-1 schema, which stays
 * unchanged as the record of what the previous version looked like. Every future
 * version increment requires an explicitly written migration; destructive migration is
 * not configured here and must not be added as a shortcut, because rebuilding the
 * database would erase recorded runs and local storage is the source of truth.
 *
 * ## One place builds this
 *
 * Production construction is centralized in [buildRunStateDatabase], which is the only
 * builder in the application. That centralization exists because Room never discovers a
 * migration on its own: a builder that omits `.addMigrations(MIGRATION_1_2)` meets a
 * version-1 database, finds no route to version 2 and throws when the file is opened.
 * With one factory, that list is written once and carried by every caller, instead of a
 * second builder appearing later and quietly shipping without it. Every future migration
 * has to be added there as well, and the factory carries them explicitly rather than
 * relying on anything automatic.
 *
 * The factory builds an instance; it does not decide how long one lives. Process-scoped
 * lifetime is a separate concern and belongs to the Application, which holds a single
 * lazy property over this factory.
 *
 * No callback, prepopulation or destructive-migration fallback is configured, and none
 * may be added here.
 */
@Database(
    entities = [RunEntity::class, RunTransitionEntity::class],
    version = 2,
    exportSchema = true
)
abstract class RunStateDatabase : RoomDatabase() {

    abstract fun runDao(): RunDao
}

/**
 * The file the real app stores its runs in.
 *
 * Stable on purpose: the name is how Android finds an existing database, so changing it
 * would not rename the old file, it would silently start an empty new one beside it and
 * abandon every recorded run.
 */
internal const val RUNSTATE_DATABASE_NAME = "runstate.db"

/**
 * Builds the run database with every migration this version knows about.
 *
 * [databaseName] defaults to the production file and exists so instrumented tests can
 * exercise this exact builder against their own file. A test that constructed its own
 * `Room.databaseBuilder` would be proving a builder production does not use, and the
 * omission that actually matters — a missing migration — would be exactly the thing it
 * could not catch.
 *
 * The context is reduced to [Context.getApplicationContext] before it reaches Room. A
 * database outlives any single Activity, so holding the Activity that happened to build
 * it would keep a destroyed screen and its whole view tree alive for the life of the
 * process.
 */
internal fun buildRunStateDatabase(
    context: Context,
    databaseName: String = RUNSTATE_DATABASE_NAME
): RunStateDatabase =
    Room.databaseBuilder(
        context.applicationContext,
        RunStateDatabase::class.java,
        databaseName
    )
        .addMigrations(MIGRATION_1_2)
        .build()
