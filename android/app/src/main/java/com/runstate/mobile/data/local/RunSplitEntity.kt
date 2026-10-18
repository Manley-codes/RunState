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
import com.runstate.mobile.run.RunSplit
import com.runstate.mobile.run.RunSplitCalculator
import kotlinx.coroutines.flow.Flow
import kotlin.math.abs

/** No row means splits were not requested, including every run saved before version 13. */
enum class RunSplitStatus { PENDING, READY, UNAVAILABLE }

@Entity(
    tableName = "run_split_snapshots",
    foreignKeys = [ForeignKey(
        entity = RunEntity::class,
        parentColumns = ["run_id"], childColumns = ["run_id"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class RunSplitSnapshotEntity(
    @PrimaryKey @ColumnInfo(name = "run_id") val runId: String,
    @ColumnInfo(name = "status") val status: RunSplitStatus,
    @ColumnInfo(name = "derivation_version") val derivationVersion: Int,
    @ColumnInfo(name = "display_unit") val displayUnit: DistanceUnit
)

@Entity(
    tableName = "run_splits",
    primaryKeys = ["run_id", "split_number"],
    foreignKeys = [ForeignKey(
        entity = RunSplitSnapshotEntity::class,
        parentColumns = ["run_id"], childColumns = ["run_id"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class RunSplitEntity(
    @ColumnInfo(name = "run_id") val runId: String,
    @ColumnInfo(name = "split_number") val splitNumber: Int,
    @ColumnInfo(name = "start_distance_meters") val startDistanceMeters: Double,
    @ColumnInfo(name = "end_distance_meters") val endDistanceMeters: Double,
    @ColumnInfo(name = "start_active_millis") val startActiveMillis: Long,
    @ColumnInfo(name = "end_active_millis") val endActiveMillis: Long,
    @ColumnInfo(name = "start_epoch_millis") val startEpochMillis: Long,
    @ColumnInfo(name = "end_epoch_millis") val endEpochMillis: Long,
    @ColumnInfo(name = "is_partial") val isPartial: Boolean,
    @ColumnInfo(name = "pace_seconds_per_meter") val paceSecondsPerMeter: Double
)

/** Publishing an optional derived result never changes the completed parent or a settled snapshot. */
@Dao
abstract class RunSplitDao {
    @Query("SELECT * FROM run_split_snapshots ORDER BY run_id")
    abstract fun observeAllSnapshots(): Flow<List<RunSplitSnapshotEntity>>

    @Query("SELECT * FROM run_split_snapshots WHERE run_id = :runId")
    abstract suspend fun snapshotFor(runId: String): RunSplitSnapshotEntity?

    @Query("SELECT * FROM run_splits WHERE run_id = :runId ORDER BY split_number")
    abstract suspend fun rowsFor(runId: String): List<RunSplitEntity>

    suspend fun pendingRunIds(): List<String> = pendingInStatus(RunSplitStatus.PENDING)

    @Query("SELECT run_id FROM run_split_snapshots WHERE status = :status ORDER BY run_id")
    protected abstract suspend fun pendingInStatus(status: RunSplitStatus): List<String>

    /** Null is an honest unavailable result; exceptions leave PENDING for a later retry. */
    @Transaction
    open suspend fun publishIfPending(runId: String, splits: List<RunSplit>?): Boolean {
        val snapshot = snapshotFor(runId) ?: return false
        if (snapshot.status != RunSplitStatus.PENDING) return false
        val run = findRun(runId) ?: return false
        check(run.state == StoredRunState.COMPLETED && run.trackingMode == RunTrackingMode.GPS &&
            run.metricSource == MetricSource.GPS && run.finishEpochMillis != null &&
            run.displayDistanceUnit == snapshot.displayUnit) {
            "A split snapshot must belong to its completed GPS run and frozen unit."
        }
        check(snapshot.derivationVersion == RunSplitCalculator.DERIVATION_VERSION) {
            "A pending split version cannot silently change during publication."
        }
        check(rowsFor(runId).isEmpty()) { "A pending snapshot cannot already contain split rows." }
        if (splits != null) {
            validateSplits(run, snapshot.displayUnit, splits)
            insertRows(splits.map { split -> RunSplitEntity(
                runId = runId,
                splitNumber = split.index,
                startDistanceMeters = split.startDistanceMeters,
                endDistanceMeters = split.endDistanceMeters,
                startActiveMillis = split.startActiveMillis,
                endActiveMillis = split.endActiveMillis,
                startEpochMillis = split.startEpochMillis,
                endEpochMillis = split.endEpochMillis,
                isPartial = split.isPartial,
                paceSecondsPerMeter = split.paceSecondsPerMeter
            ) })
        }
        check(settle(runId, RunSplitStatus.PENDING,
            if (splits == null) RunSplitStatus.UNAVAILABLE else RunSplitStatus.READY) == 1)
        return true
    }

    private fun validateSplits(run: RunEntity, unit: DistanceUnit, splits: List<RunSplit>) {
        require(splits.isNotEmpty()) { "A ready split snapshot must contain at least one split." }
        require(run.telemetryCoverage == TelemetryCoverage.COMPLETE) {
            "Incomplete GPS evidence cannot publish precise splits."
        }
        val finish = requireNotNull(run.finishEpochMillis)
        val distance = requireNotNull(run.finalDistanceMeters)
        val unitMeters = when (unit) {
            DistanceUnit.MILES -> 1_609.344
            DistanceUnit.KILOMETERS -> 1_000.0
        }
        var priorDistance = 0.0
        var priorActive = 0L
        var priorEpoch = run.officialStartEpochMillis
        splits.forEachIndexed { position, split ->
            require(split.index == position + 1) { "Split numbers must be one-based and consecutive." }
            require(split.startDistanceMeters.isFinite() && split.endDistanceMeters.isFinite() &&
                abs(split.startDistanceMeters - priorDistance) <= 0.000_001 &&
                split.endDistanceMeters > split.startDistanceMeters) { "Split distances must be contiguous and finite." }
            val length = split.endDistanceMeters - split.startDistanceMeters
            require(if (split.isPartial) position == splits.lastIndex && length < unitMeters
                else abs(length - unitMeters) <= 0.000_001) { "Only the final split may be shorter than its frozen unit." }
            require(split.startActiveMillis == priorActive && split.endActiveMillis > priorActive &&
                split.endActiveMillis <= finish - run.officialStartEpochMillis) { "Split active times must be contiguous and positive." }
            require(split.startEpochMillis == priorEpoch && split.endEpochMillis > split.startEpochMillis &&
                split.endEpochMillis <= finish &&
                split.endActiveMillis - split.startActiveMillis <= split.endEpochMillis - split.startEpochMillis) {
                "Split boundaries must fit the completed run timeline."
            }
            require(split.paceSecondsPerMeter.isFinite() && split.paceSecondsPerMeter > 0.0) {
                "A ready split needs a finite, positive pace."
            }
            priorDistance = split.endDistanceMeters
            priorActive = split.endActiveMillis
            priorEpoch = split.endEpochMillis
        }
        require(abs(priorDistance - distance) <= 0.000_001) {
            "Splits must end at the run's frozen measured distance."
        }
    }

    @Query("SELECT * FROM runs WHERE run_id = :runId")
    protected abstract suspend fun findRun(runId: String): RunEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertRows(rows: List<RunSplitEntity>)

    @Query("UPDATE run_split_snapshots SET status = :status WHERE run_id = :runId AND status = :pendingStatus")
    protected abstract suspend fun settle(runId: String, pendingStatus: RunSplitStatus, status: RunSplitStatus): Int
}
