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
    companion object {
        /** Durable policy marker: an answer or Save Activity, never a timer, selects this reply. */
        const val UNTIMED_SELECTION_DEADLINE = Long.MAX_VALUE
    }

    @Query("SELECT * FROM run_reflections WHERE run_id = :runId")
    abstract suspend fun findByRunId(runId: String): RunReflectionEntity?

    @Query("SELECT * FROM run_reflections ORDER BY run_id ASC")
    abstract fun observeAll(): Flow<List<RunReflectionEntity>>

    @Query("SELECT * FROM run_reflections ORDER BY run_id ASC")
    abstract suspend fun all(): List<RunReflectionEntity>

    /** Idempotent creation: reopening cannot replace evidence or reset an existing window. */
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

    /** The first deliberate answer selects an untimed reply; later answers update feedback only. */
    @Transaction
    open suspend fun recordEnergy(
        runId: String,
        branch: ReflectionBranch,
        now: Long,
        selectionHeld: Boolean = false,
        questionVersion: Int? = null
    ): RunReflectionEntity {
        require(branch != ReflectionBranch.NO_SELECTION) { "Energy must name an Energy branch." }
        return recordExplicitAnswer(runId, branch, now, selectionHeld, questionVersion)
    }

    /** A deliberate unknown is distinct from an automatic timeout, without inventing Energy. */
    @Transaction
    open suspend fun recordNotSure(
        runId: String, now: Long, selectionHeld: Boolean = false, questionVersion: Int? = null
    ): RunReflectionEntity =
        recordExplicitAnswer(runId, ReflectionBranch.NO_SELECTION, now, selectionHeld, questionVersion)

    private suspend fun recordExplicitAnswer(
        runId: String, branch: ReflectionBranch, now: Long, selectionHeld: Boolean, questionVersion: Int?
    ): RunReflectionEntity {
        require(questionVersion == null || questionVersion > 0)
        val stored = required(runId)
        val selected = stored.selectedBranch ?: when {
            // Save Activity is already durable even if its coordinator callback has not run.
            stored.selectionDeadlineEpochMillis == UNTIMED_SELECTION_DEADLINE &&
                parentFinalized(runId, StoredRunState.COMPLETED) == true -> ReflectionBranch.NO_SELECTION
            stored.selectionDeadlineEpochMillis == UNTIMED_SELECTION_DEADLINE ||
                !stored.selectionWindowStarted || selectionHeld ||
                now < stored.selectionDeadlineEpochMillis -> branch
            else -> ReflectionBranch.NO_SELECTION
        }
        val updated = stored.copy(postRunEnergy = branch, selectedBranch = selected)
        check(recordEnergyProvenance(runId, StoredRunState.COMPLETED, stored.postRunEnergy,
            branch, now, questionVersion) == 1) { "Energy requires a completed saved run." }
        update(updated)
        return updated
    }

    /** Old original answers retain unknown wording/time instead of becoming version-2 evidence. */
    @Query("""
        UPDATE runs SET
            first_post_run_energy = CASE WHEN first_post_run_energy IS NULL
                THEN COALESCE(:previousAnswer, :answer) ELSE first_post_run_energy END,
            first_post_run_energy_question_version = CASE WHEN first_post_run_energy IS NULL
                THEN CASE WHEN :previousAnswer IS NULL THEN :questionVersion ELSE NULL END
                ELSE first_post_run_energy_question_version END,
            post_run_energy_recorded_at_epoch_millis = CASE
                WHEN first_post_run_energy IS NULL AND :previousAnswer IS NULL THEN :now
                ELSE post_run_energy_recorded_at_epoch_millis END,
            post_run_energy_updated_at_epoch_millis = :now,
            post_run_energy_question_version = :questionVersion
        WHERE run_id = :runId AND state = :completedState
    """)
    protected abstract suspend fun recordEnergyProvenance(
        runId: String, completedState: StoredRunState, previousAnswer: ReflectionBranch?,
        answer: ReflectionBranch, now: Long, questionVersion: Int?
    ): Int

    @Transaction
    open suspend fun selectNoSelectionIfDue(runId: String, now: Long): RunReflectionEntity {
        val stored = required(runId)
        if (!stored.selectionWindowStarted || stored.selectedBranch != null ||
            stored.selectionDeadlineEpochMillis == UNTIMED_SELECTION_DEADLINE ||
            now < stored.selectionDeadlineEpochMillis) return stored
        val updated = stored.copy(selectedBranch = ReflectionBranch.NO_SELECTION)
        update(updated)
        return updated
    }

    /** Upgrade an unfinished, unanswered legacy completion without rewriting any frozen choice. */
    @Transaction
    open suspend fun waitForEnergyWithoutDeadline(runId: String): RunReflectionEntity {
        val stored = required(runId)
        if (stored.status != ReflectionStatus.PENDING || stored.selectedBranch != null ||
            stored.postRunEnergy != null || stored.selectionDeadlineEpochMillis == UNTIMED_SELECTION_DEADLINE ||
            parentFinalized(runId, StoredRunState.COMPLETED) != false) return stored
        val updated = stored.copy(selectionDeadlineEpochMillis = UNTIMED_SELECTION_DEADLINE)
        update(updated)
        return updated
    }

    /** Save may close unanswered Energy, but it never invents an explicit Not sure answer. */
    @Transaction
    open suspend fun selectForSavedActivity(runId: String): RunReflectionEntity {
        val stored = required(runId)
        if (stored.status == ReflectionStatus.READY ||
            stored.selectionDeadlineEpochMillis != UNTIMED_SELECTION_DEADLINE ||
            parentFinalized(runId, StoredRunState.COMPLETED) != true) return stored
        val updated = stored.copy(
            selectedBranch = stored.selectedBranch ?: ReflectionBranch.NO_SELECTION,
            selectionWindowStarted = true
        )
        if (updated != stored) update(updated)
        return updated
    }

    /** Receipt completion releases delivery once; an untimed check-in stays untimed. */
    @Transaction
    open suspend fun beginSelectionWindow(
        runId: String,
        deadline: Long
    ): RunReflectionEntity {
        val stored = required(runId)
        if (stored.selectionWindowStarted) return stored
        val updated = stored.copy(
            selectionWindowStarted = true,
            selectionDeadlineEpochMillis = if (stored.selectionDeadlineEpochMillis == UNTIMED_SELECTION_DEADLINE)
                UNTIMED_SELECTION_DEADLINE else deadline
        )
        update(updated)
        return updated
    }

    /** The first complete family wins; duplicate or stale callbacks never overwrite it. */
    @Transaction
    open suspend fun saveCandidates(
        runId: String,
        requestId: String,
        json: String,
        isTestData: Boolean,
        selectedEvidenceJson: String? = null
    ): Boolean {
        require(json.isNotBlank())
        val stored = findByRunId(runId) ?: return false
        if (!stored.accepts(requestId) || stored.candidatesJson != null) return false
        require(selectedEvidenceJson == null || selectedEvidenceJson.isNotBlank())
        update(stored.copy(candidatesJson = json, isTestData = isTestData,
            evidenceJson = selectedEvidenceJson ?: stored.evidenceJson))
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
        if (!stored.accepts(requestId) || !stored.selectionWindowStarted ||
            stored.selectedBranch != branch ||
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

    @Query("SELECT activity_finalized FROM runs WHERE run_id = :runId AND state = :completedState")
    protected abstract suspend fun parentFinalized(runId: String, completedState: StoredRunState): Boolean?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insert(entity: RunReflectionEntity)

    @Update
    protected abstract suspend fun update(entity: RunReflectionEntity)
}
