package com.runstate.mobile.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * The queries this slice is allowed to make against the `runs` table.
 *
 * Deliberately narrow: creating the initial run row, and reading one run back by its
 * permanent UUID. Update, delete, listing, recovery, active-run discovery and
 * synchronization are separate contracts and are not opened here.
 */
@Dao
interface RunDao {

    /**
     * Writes one run row.
     *
     * ABORT rather than REPLACE is the point. A repeated UUID means something has gone
     * wrong upstream, and REPLACE would quietly overwrite the original run's official
     * start and checkpoint with the newer row's values. ABORT fails the insert and
     * leaves the stored run untouched, so the caller learns about the collision instead
     * of losing the record it already had.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(run: RunEntity)

    /** Reads one run by its permanent UUID, or null when no such run is stored. */
    @Query("SELECT * FROM runs WHERE run_id = :runId")
    suspend fun findById(runId: String): RunEntity?
}
