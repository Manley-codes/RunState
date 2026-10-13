package com.runstate.mobile.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** All read/check/write operations are transactions, including selection versus response races. */
@Dao
abstract class ReflectionDao {
    @Query("SELECT * FROM run_reflections WHERE run_id = :runId")
    abstract suspend fun findByRunId(runId: String): RunReflectionEntity?

    @Query("SELECT * FROM run_reflections ORDER BY run_id ASC")
    abstract fun observeAll(): Flow<List<RunReflectionEntity>>

    @Query("SELECT * FROM run_reflections ORDER BY run_id ASC")
    abstract suspend fun all(): List<RunReflectionEntity>

    /** Idempotent creation: reopening a run cannot replace its evidence or its deadline. */
    @Transaction
    open suspend fun ensurePending(entity: RunReflectionEntity): RunReflectionEntity {
        findByRunId(entity.runId)?.let { return it }
        require(entity.status == ReflectionStatus.PENDING && entity.selectedBranch == null &&
            entity.postRunEnergy == null && entity.candidatesJson == null) {
            "A new reflection must begin pending and unselected."
        }
        check(parentState(entity.runId) == StoredRunState.COMPLETED) {
            "A reflection requires a durably completed run."
        }
        insert(entity)
        return entity
    }

    /** First on-time Energy selects the reply; subsequent answers only update feedback. */
    @Transaction
    open suspend fun recordEnergy(
        runId: String,
        branch: ReflectionBranch,
        now: Long
    ): RunReflectionEntity {
        require(branch != ReflectionBranch.NO_SELECTION) { "Energy must name an Energy branch." }
        val stored = required(runId)
        val selected = stored.selectedBranch ?: if (now < stored.selectionDeadlineEpochMillis) {
            branch
        } else {
            ReflectionBranch.NO_SELECTION
        }
        val updated = stored.copy(postRunEnergy = branch, selectedBranch = selected)
        update(updated)
        return updated
    }

    @Transaction
    open suspend fun selectNoSelectionIfDue(runId: String, now: Long): RunReflectionEntity {
        val stored = required(runId)
        if (stored.selectedBranch != null || now < stored.selectionDeadlineEpochMillis) return stored
        val updated = stored.copy(selectedBranch = ReflectionBranch.NO_SELECTION)
        update(updated)
        return updated
    }

    /** The first complete family wins; duplicate or stale callbacks never overwrite it. */
    @Transaction
    open suspend fun saveCandidates(
        runId: String,
        requestId: String,
        json: String,
        isTestData: Boolean
    ): Boolean {
        require(json.isNotBlank())
        val stored = findByRunId(runId) ?: return false
        if (!stored.accepts(requestId) || stored.candidatesJson != null) return false
        update(stored.copy(candidatesJson = json, isTestData = isTestData))
        return true
    }

    /** Store the exact validated selected candidate and discard the unselected family. */
    @Transaction
    open suspend fun finalizeReady(
        runId: String,
        requestId: String,
        branch: ReflectionBranch,
        exactText: String,
        now: Long
    ): Boolean {
        require(exactText.isNotBlank())
        val stored = findByRunId(runId) ?: return false
        if (!stored.accepts(requestId) || stored.selectedBranch != branch ||
            stored.candidatesJson == null) return false
        update(stored.copy(
            status = ReflectionStatus.READY,
            text = exactText,
            finalizedAtEpochMillis = now,
            candidatesJson = null
        ))
        return true
    }

    @Transaction
    open suspend fun markFailed(runId: String, requestId: String): Boolean {
        val stored = findByRunId(runId) ?: return false
        if (!stored.accepts(requestId)) return false
        update(stored.copy(status = ReflectionStatus.FAILED, candidatesJson = null))
        return true
    }

    @Transaction
    open suspend fun retry(runId: String, newRequestId: String): RunReflectionEntity? {
        val stored = findByRunId(runId) ?: return null
        if (stored.status != ReflectionStatus.FAILED) return null
        require(newRequestId != stored.requestId) { "Retry requires a new request identity." }
        val updated = stored.copy(
            status = ReflectionStatus.PENDING,
            requestId = newRequestId,
            candidatesJson = null
        )
        update(updated)
        return updated
    }

    private suspend fun required(runId: String): RunReflectionEntity =
        findByRunId(runId) ?: error("No reflection exists for this saved run.")

    private fun RunReflectionEntity.accepts(requestId: String): Boolean =
        status == ReflectionStatus.PENDING && this.requestId == requestId

    @Query("SELECT state FROM runs WHERE run_id = :runId")
    protected abstract suspend fun parentState(runId: String): StoredRunState?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insert(entity: RunReflectionEntity)

    @Update
    protected abstract suspend fun update(entity: RunReflectionEntity)
}
