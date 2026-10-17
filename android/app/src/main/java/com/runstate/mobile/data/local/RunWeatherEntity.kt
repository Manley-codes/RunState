package com.runstate.mobile.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import com.runstate.mobile.weather.RunWeatherSnapshot
import kotlinx.coroutines.flow.Flow

/** A single weather attempt belongs to one run; no row means weather was never attempted. */
enum class RunWeatherStatus { DISABLED, PENDING, READY, UNAVAILABLE }

@Entity(
    tableName = "run_weather",
    foreignKeys = [
        ForeignKey(
            entity = RunEntity::class,
            parentColumns = ["run_id"],
            childColumns = ["run_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class RunWeatherEntity(
    @PrimaryKey @ColumnInfo(name = "run_id") val runId: String,
    @ColumnInfo(name = "status") val status: RunWeatherStatus,
    @ColumnInfo(name = "snapshot_json") val snapshotJson: String? = null
) {
    /** A damaged optional snapshot cannot stop the run or its saved reflection from loading. */
    fun readySnapshot(): RunWeatherSnapshot? {
        if (status != RunWeatherStatus.READY) return null
        val json = snapshotJson ?: return null
        return try {
            RunWeatherSnapshot.fromJson(json)
        } catch (_: Exception) {
            null
        }
    }
}

/** The collector only needs one claim, one completion and a read of its durable result. */
interface RunWeatherStore {
    suspend fun find(runId: String): RunWeatherEntity?
    suspend fun claim(runId: String, allowed: Boolean): Boolean
    suspend fun finish(runId: String, snapshotJson: String?)
}

@Dao
abstract class WeatherDao : RunWeatherStore {
    @Query("SELECT * FROM run_weather ORDER BY run_id")
    abstract fun observeAll(): Flow<List<RunWeatherEntity>>

    @Query("SELECT * FROM run_weather WHERE run_id = :runId")
    abstract override suspend fun find(runId: String): RunWeatherEntity?

    /**
     * Only the first claim for an official GPS run can begin a lookup. A later process
     * closes an abandoned PENDING attempt instead of retrying it against later weather.
     * The process collector deduplicates its own calls before reaching this boundary.
     */
    @Transaction
    open override suspend fun claim(runId: String, allowed: Boolean): Boolean {
        find(runId)?.let { existing ->
            if (existing.status == RunWeatherStatus.PENDING) finish(runId, null)
            return false
        }
        val run = findRun(runId) ?: return false
        if (run.state != StoredRunState.RUNNING || run.trackingMode != RunTrackingMode.GPS) {
            return false
        }
        val status = if (allowed) RunWeatherStatus.PENDING else RunWeatherStatus.DISABLED
        return insertFirst(RunWeatherEntity(runId, status)) != -1L && allowed
    }

    /** A late or repeated result cannot replace a settled snapshot or create historical weather. */
    override suspend fun finish(runId: String, snapshotJson: String?) {
        finishPending(
            runId,
            if (snapshotJson == null) RunWeatherStatus.UNAVAILABLE else RunWeatherStatus.READY,
            snapshotJson
        )
    }

    @Query("SELECT * FROM runs WHERE run_id = :runId")
    protected abstract suspend fun findRun(runId: String): RunEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertFirst(row: RunWeatherEntity): Long

    @Query("""
        UPDATE run_weather SET status = :status, snapshot_json = :snapshotJson
        WHERE run_id = :runId AND status = 'PENDING'
    """)
    protected abstract suspend fun finishPending(
        runId: String,
        status: RunWeatherStatus,
        snapshotJson: String?
    ): Int
}
