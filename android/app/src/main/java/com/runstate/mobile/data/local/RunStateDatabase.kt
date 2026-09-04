package com.runstate.mobile.data.local

import androidx.room.Database
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
 * No singleton, callback or prepopulation lives here yet, and there is still no
 * production `Room.databaseBuilder` anywhere in the app. **When that builder is
 * written, it must call `.addMigrations(MIGRATION_1_2)`.** Room does not discover
 * migrations on its own: a builder without that call meets a version-1 database, finds
 * no route to version 2 and throws on open. The migration test passes it explicitly for
 * the same reason.
 */
@Database(
    entities = [RunEntity::class, RunTransitionEntity::class],
    version = 2,
    exportSchema = true
)
abstract class RunStateDatabase : RoomDatabase() {

    abstract fun runDao(): RunDao
}
