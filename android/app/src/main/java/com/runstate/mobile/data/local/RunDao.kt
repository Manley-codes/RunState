package com.runstate.mobile.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/**
 * The queries this slice is allowed to make against the run tables.
 *
 * Deliberately narrow: creating the initial run row, reading one run back by its
 * permanent UUID, durably moving that same run through pause, resume and completion,
 * and reading back every run that has not ended. General listing, History queries and
 * synchronization are separate contracts and are not opened here.
 *
 * Discovery is a read. [findActiveRuns] reports what storage holds; deciding what to do
 * about it — adopting one run, or refusing to guess when there is more than one — is
 * recovery, and recovery is a separate slice that does not exist yet.
 *
 * This is an abstract class because the lifecycle operations below are not single
 * statements. Each one reads, checks and then writes two tables inside one
 * `@Transaction`. An abstract class lets that shared transactional logic live in the
 * same type as the query methods Room generates, with the private helpers it needs, so
 * one lifecycle operation reads as one thing.
 */
@Dao
abstract class RunDao {

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
    abstract suspend fun insert(run: RunEntity)

    /** Reads one run by its permanent UUID, or null when no such run is stored. */
    @Query("SELECT * FROM runs WHERE run_id = :runId")
    abstract suspend fun findById(runId: String): RunEntity?

    /**
     * Reads one run's pause and resume history in the order the events happened.
     *
     * Ordered by sequence rather than by timestamp because sequence is the authoritative
     * ordering: two events may legitimately share a millisecond, and ordering on an
     * equal value has no defined answer.
     */
    @Query(
        """
        SELECT * FROM run_transitions
        WHERE run_id = :runId
        ORDER BY sequence_number ASC
        """
    )
    abstract suspend fun transitionsFor(runId: String): List<RunTransitionEntity>

    /**
     * How many runs are stored.
     *
     * Exists so completion can be proved to change the run it was given rather than
     * add a second one. A completed run is the same run, later — never a new record.
     */
    @Query("SELECT COUNT(*) FROM runs")
    abstract suspend fun countRuns(): Int

    /**
     * Every stored run that has not ended, oldest start first.
     *
     * A run is active when storage holds it as RUNNING or PAUSED. Both are returned,
     * because a paused run is just as unfinished as a running one — the runner stopped
     * at a crossing, the app was killed, and that run still needs recovering. COMPLETED
     * rows are excluded: they are history, and history is never a recovery candidate.
     *
     * ## Why a list, and not one row
     *
     * A `LIMIT 1` or a single nullable result would be the convenient shape, and it
     * would be wrong. It cannot tell "one active run" apart from "several active runs,
     * and here is one of them" — so the single situation that most needs attention, a
     * durable state that should be impossible, would arrive looking exactly like the
     * healthy case. A list keeps the three answers distinct: empty means nothing to
     * recover, one means one candidate, and more than one means the stored state is
     * inconsistent and must be surfaced rather than quietly resolved.
     *
     * ## What the ordering is and is not
     *
     * Rows come back oldest official start first, with canonical UUID text breaking
     * ties so the order is total rather than merely mostly-defined. Two runs can share
     * a start millisecond, and SQLite has no defined answer for equal sort keys, so
     * without the second column the same data could come back in a different order on
     * a different day and a failing report would be unreproducible.
     *
     * That ordering exists for stable evidence and diagnostics. It is emphatically not
     * a ranking, and being first here confers nothing: it does not make a row the one
     * to adopt, and recovery must not read "first" as "chosen". When this returns more
     * than one row, the answer is to refuse and report, not to take the oldest.
     *
     * ## Read-only
     *
     * This selects and returns. It does not repair, delete, complete, deduplicate or
     * mark anything, and it must not grow the ability to. Interpreting the result
     * belongs to the later recovery slice.
     */
    suspend fun findActiveRuns(): List<RunEntity> =
        selectRunsInStates(
            runningState = StoredRunState.RUNNING,
            pausedState = StoredRunState.PAUSED
        )

    /**
     * Durably pauses a run that storage still believes is RUNNING.
     *
     * @throws IllegalStateException if no run is stored under [runId], if the stored run
     *   is not RUNNING, or if [pausedAtEpochMillis] is before the stored checkpoint.
     *   Neither table changes in any of those cases.
     */
    @Transaction
    open suspend fun pauseRun(runId: String, pausedAtEpochMillis: Long) {
        moveState(
            runId = runId,
            expectedState = StoredRunState.RUNNING,
            newState = StoredRunState.PAUSED,
            transitionType = RunTransitionType.PAUSE,
            occurredAtEpochMillis = pausedAtEpochMillis
        )
    }

    /**
     * Durably resumes a run that storage still believes is PAUSED.
     *
     * @throws IllegalStateException if no run is stored under [runId], if the stored run
     *   is not PAUSED, or if [resumedAtEpochMillis] is before the stored checkpoint.
     *   Neither table changes in any of those cases.
     */
    @Transaction
    open suspend fun resumeRun(runId: String, resumedAtEpochMillis: Long) {
        moveState(
            runId = runId,
            expectedState = StoredRunState.PAUSED,
            newState = StoredRunState.RUNNING,
            transitionType = RunTransitionType.RESUME,
            occurredAtEpochMillis = resumedAtEpochMillis
        )
    }

    /**
     * Durably completes a run that storage still believes is PAUSED.
     *
     * No child event is written. Completion is not something that happened *during* the
     * run; it is the run's end, and the parent's COMPLETED state plus its finish time
     * already say so completely.
     *
     * Unlike the entity, which tolerates a null finish on a row inherited from version
     * 1, this path always writes one. Storage may contain an old unknown; it may not
     * produce a new one.
     *
     * @throws IllegalStateException if no run is stored under [runId], if the stored run
     *   is not PAUSED, or if [finishEpochMillis] is before the stored checkpoint.
     *   Nothing changes in any of those cases.
     */
    @Transaction
    open suspend fun completeRun(runId: String, finishEpochMillis: Long) {
        requireUpdatableRun(runId, StoredRunState.PAUSED, finishEpochMillis)

        val rowsUpdated = applyCompletion(
            runId = runId,
            expectedState = StoredRunState.PAUSED,
            completedState = StoredRunState.COMPLETED,
            finishEpochMillis = finishEpochMillis
        )

        requireSingleRowUpdated(rowsUpdated, runId)
    }

    /**
     * The shared body of pause and resume: check, allocate, update the run, record the
     * event. Every step runs inside the caller's transaction.
     */
    private suspend fun moveState(
        runId: String,
        expectedState: StoredRunState,
        newState: StoredRunState,
        transitionType: RunTransitionType,
        occurredAtEpochMillis: Long
    ) {
        requireUpdatableRun(runId, expectedState, occurredAtEpochMillis)

        // Allocated from what is stored, not from a counter held in memory. A counter
        // would be wrong after a process death, and wrong again for a second session
        // object; the table itself always knows how far this run has got.
        val nextSequenceNumber = (highestSequenceNumber(runId) ?: 0) + 1

        val rowsUpdated = applyStateChange(
            runId = runId,
            expectedState = expectedState,
            newState = newState,
            occurredAtEpochMillis = occurredAtEpochMillis
        )

        requireSingleRowUpdated(rowsUpdated, runId)

        // Last, so a rejected parent update never leaves a lone orphan event behind.
        // If this insert fails, the parent update above rolls back with it.
        insertTransition(
            RunTransitionEntity(
                runId = runId,
                sequenceNumber = nextSequenceNumber,
                transitionType = transitionType,
                occurredAtEpochMillis = occurredAtEpochMillis
            )
        )
    }

    /**
     * Reads the run and refuses the operation unless storage agrees it is legal.
     *
     * The read is what makes the refusal explainable — the conditional UPDATE below can
     * only report that it matched nothing, while this can say which of the three
     * reasons it was.
     */
    private suspend fun requireUpdatableRun(
        runId: String,
        expectedState: StoredRunState,
        occurredAtEpochMillis: Long
    ): RunEntity {
        val storedRun = findById(runId)
            ?: error("No run is stored under $runId, so it cannot be updated.")

        // What memory believes is not evidence. Storage is the source of truth, so the
        // stored state is what decides whether this transition is legal.
        check(storedRun.state == expectedState) {
            "Run $runId is stored as ${storedRun.state}, not $expectedState."
        }

        // Equal is allowed: two lifecycle events can land in the same millisecond.
        // Earlier is not, because the checkpoint is the promise that everything through
        // that instant is durable, and moving it backwards would withdraw the promise.
        check(occurredAtEpochMillis >= storedRun.lastCheckpointEpochMillis) {
            "Run $runId already has a checkpoint at " +
                "${storedRun.lastCheckpointEpochMillis}, after $occurredAtEpochMillis."
        }

        return storedRun
    }

    /**
     * Fails unless the conditional write matched exactly the one intended run.
     *
     * Zero means another operation committed between the read above and this write, and
     * the row no longer matches the state this operation was built on. Throwing rolls
     * the whole transaction back, so a stale operation cannot overwrite newer truth or
     * leave a transition event describing a change that never landed.
     */
    private fun requireSingleRowUpdated(rowsUpdated: Int, runId: String) {
        check(rowsUpdated == 1) {
            "Expected to update exactly one row for run $runId, but updated $rowsUpdated."
        }
    }

    /**
     * Moves a run between RUNNING and PAUSED and advances its checkpoint.
     *
     * The state and checkpoint conditions are repeated in the WHERE clause rather than
     * trusted from the read. Re-checking them inside the write is what makes the check
     * and the write one indivisible decision instead of two steps with a gap between.
     */
    @Query(
        """
        UPDATE runs
        SET state = :newState,
            last_checkpoint_epoch_millis = :occurredAtEpochMillis
        WHERE run_id = :runId
          AND state = :expectedState
          AND last_checkpoint_epoch_millis <= :occurredAtEpochMillis
        """
    )
    protected abstract suspend fun applyStateChange(
        runId: String,
        expectedState: StoredRunState,
        newState: StoredRunState,
        occurredAtEpochMillis: Long
    ): Int

    /**
     * Ends a run, writing its finish and moving its checkpoint to the same instant.
     *
     * `completedState` is passed in rather than written as a literal so the stored text
     * always comes from the enum itself, and cannot drift from it.
     */
    @Query(
        """
        UPDATE runs
        SET state = :completedState,
            finish_epoch_millis = :finishEpochMillis,
            last_checkpoint_epoch_millis = :finishEpochMillis
        WHERE run_id = :runId
          AND state = :expectedState
          AND last_checkpoint_epoch_millis <= :finishEpochMillis
        """
    )
    protected abstract suspend fun applyCompletion(
        runId: String,
        expectedState: StoredRunState,
        completedState: StoredRunState,
        finishEpochMillis: Long
    ): Int

    /**
     * Reads every run stored in either of the two states it is given.
     *
     * The states arrive as bound parameters rather than as literal text in the SQL, for
     * the same reason [applyCompletion] takes its completed state as one: the stored
     * spelling then always comes from [StoredRunState] itself and cannot drift from it.
     * Writing `state = 'RUNNING'` here would put a copy of that name in a string the
     * compiler never checks, and renaming the constant would leave the query silently
     * matching nothing.
     *
     * Two scalar parameters rather than a list, because scalar enum binding is already
     * compiled and proven throughout this DAO, and this needs exactly two.
     *
     * It is protected because the answer to "which states count as active" belongs to
     * this DAO and not to its callers. [findActiveRuns] supplies both constants, so a
     * caller cannot ask for COMPLETED rows through this door and cannot accidentally
     * narrow discovery to only the running half.
     */
    @Query(
        """
        SELECT * FROM runs
        WHERE state = :runningState OR state = :pausedState
        ORDER BY official_start_epoch_millis ASC, run_id ASC
        """
    )
    protected abstract suspend fun selectRunsInStates(
        runningState: StoredRunState,
        pausedState: StoredRunState
    ): List<RunEntity>

    /** The highest sequence number this run has used, or null if it has no events. */
    @Query("SELECT MAX(sequence_number) FROM run_transitions WHERE run_id = :runId")
    protected abstract suspend fun highestSequenceNumber(runId: String): Int?

    /**
     * Writes one pause or resume event.
     *
     * ABORT for the same reason the run insert uses it: a repeated sequence number is a
     * defect in allocation, and it must surface as a failure that rolls the transaction
     * back rather than quietly replacing a real recorded event.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertTransition(transition: RunTransitionEntity)
}
