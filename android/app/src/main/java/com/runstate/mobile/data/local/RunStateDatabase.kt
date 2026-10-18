package com.runstate.mobile.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The on-phone store for runs.
 *
 * Version 2 adds the run's finish time and pause/resume history. Version 3 adds
 * nullable completed-metric fields and explicit transition-history provenance.
 * Version 4 adds durable reflection attempts, selection and exact finalized text.
 * Version 5 stores optional pre-run energy without assigning a value to older runs.
 * Version 6 adds a distinct run tracking mode and durable GPS observations.
 * Version 7 adds explicit per-run music observation and its partial event stream.
 * Version 8 stores optional post-run Effort without inferring answers for older runs.
 * Version 9 starts the reflection selection window after the spoken save receipt.
 * Version 10 records separate music-reflection sharing consent, off for every older run.
 * Version 11 adds nullable measured timing evidence without changing older runs.
 * Version 12 adds run-owned optional weather attempts without backfilling older runs.
 * Version 13 adds immutable split snapshots and rows without recalculating older runs.
 * Version 14 adds one durable optional song-preparation claim per run, with no old-run backfill.
 * Each exported schema under `android/app/schemas` stays unchanged as the record of what
 * that version looked like. Every future version increment requires an explicitly
 * written migration; destructive migration is not configured here and must not be added
 * as a shortcut, because rebuilding the database would erase recorded runs and local
 * storage is the source of truth.
 *
 * ## One place builds this
 *
 * Production construction is centralized in [buildRunStateDatabase], which is the only
 * builder in the application. That centralization exists because Room never discovers a
 * migration on its own: a builder that omits either registered migration may meet an
 * older database, find no route to the current version and throw when the file opens.
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
    entities = [
        RunEntity::class,
        RunTransitionEntity::class,
        RunReflectionEntity::class,
        RunLocationPointEntity::class,
        RunMusicObservationEntity::class,
        RunWeatherEntity::class,
        RunSplitSnapshotEntity::class,
        RunSplitEntity::class,
        SongPreparationClaimEntity::class
    ],
    version = 14,
    exportSchema = true
)
abstract class RunStateDatabase : RoomDatabase() {

    abstract fun runDao(): RunDao

    abstract fun reflectionDao(): ReflectionDao

    abstract fun locationDao(): LocationDao

    abstract fun musicObservationDao(): MusicObservationDao

    abstract fun weatherDao(): WeatherDao

    abstract fun splitDao(): RunSplitDao
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
        .addMigrations(
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
            MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10,
            MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14
        )
        .build()
