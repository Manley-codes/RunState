package com.runstate.mobile.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The on-phone store for runs.
 *
 * Version 1 is the initial baseline. Its exported schema under `android/app/schemas`
 * is the record of what version 1 looked like, and every future version increment
 * requires an explicitly written migration. Destructive migration is not configured
 * here and must not be added as a shortcut: rebuilding the database would erase
 * recorded runs, which contradicts local storage being the source of truth.
 *
 * No singleton, callback or prepopulation lives here yet. How the database instance is
 * created and shared is an application-wiring decision this slice does not make.
 */
@Database(
    entities = [RunEntity::class],
    version = 1,
    exportSchema = true
)
abstract class RunStateDatabase : RoomDatabase() {

    abstract fun runDao(): RunDao
}
