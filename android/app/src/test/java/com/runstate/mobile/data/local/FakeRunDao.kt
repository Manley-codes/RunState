package com.runstate.mobile.data.local

/**
 * An in-memory stand-in for the real Room DAO, for tests that run on the JVM.
 *
 * It exists so the save-before-RUNNING boundary can be checked without a phone, an
 * emulator or a database: tests can watch exactly when the insert happens, force it to
 * fail, and hold it open while another caller tries to start.
 *
 * It moved out of `RunSessionStarterTest` when [RunDao] became an abstract class with
 * transactional lifecycle methods. Implementing that class is now enough work that
 * repeating it in every test file would be its own maintenance problem.
 *
 * **It provides no transaction semantics.** The inherited `pauseRun`, `resumeRun` and
 * `completeRun` run their checks and writes against these lists with nothing wrapping
 * them, so a failure part-way through leaves whatever it had already changed. Rollback
 * is a property of the database, not of the DAO, and it is proved on a real database in
 * the instrumented tests rather than pretended at here.
 */
open class FakeRunDao : RunDao() {

    /** Every run row this DAO holds, in the order it first received them. */
    val inserted = mutableListOf<RunEntity>()

    /** Every pause or resume event recorded, in the order it received them. */
    val transitions = mutableListOf<RunTransitionEntity>()

    /** When set, [insert] throws this instead of storing anything. */
    var failWith: Exception? = null

    /** Runs at the start of [insert], before success or failure is decided. */
    var duringInsert: (suspend () -> Unit)? = null

    override suspend fun insert(run: RunEntity) {
        duringInsert?.invoke()
        failWith?.let { throw it }
        inserted += run
    }

    override suspend fun findById(runId: String): RunEntity? =
        inserted.lastOrNull { it.runId == runId }

    override suspend fun transitionsFor(runId: String): List<RunTransitionEntity> =
        transitions.filter { it.runId == runId }.sortedBy { it.sequenceNumber }

    override suspend fun countRuns(): Int = inserted.size

    override suspend fun applyStateChange(
        runId: String,
        expectedState: StoredRunState,
        newState: StoredRunState,
        occurredAtEpochMillis: Long
    ): Int = updateMatchingRun(runId, expectedState, occurredAtEpochMillis) { stored ->
        stored.copy(
            state = newState,
            lastCheckpointEpochMillis = occurredAtEpochMillis
        )
    }

    override suspend fun applyCompletion(
        runId: String,
        expectedState: StoredRunState,
        completedState: StoredRunState,
        finishEpochMillis: Long
    ): Int = updateMatchingRun(runId, expectedState, finishEpochMillis) { stored ->
        stored.copy(
            state = completedState,
            lastCheckpointEpochMillis = finishEpochMillis,
            finishEpochMillis = finishEpochMillis
        )
    }

    override suspend fun highestSequenceNumber(runId: String): Int? =
        transitions.filter { it.runId == runId }.maxOfOrNull { it.sequenceNumber }

    override suspend fun insertTransition(transition: RunTransitionEntity) {

        // Mirrors the real table's composite primary key. Without it the fake would
        // accept a duplicate sequence number that the database would refuse.
        val alreadyUsed = transitions.any {
            it.runId == transition.runId && it.sequenceNumber == transition.sequenceNumber
        }
        check(!alreadyUsed) {
            "Sequence ${transition.sequenceNumber} is already used by run ${transition.runId}."
        }

        transitions += transition
    }

    /**
     * Applies [change] to the one stored run that still matches the conditions the real
     * conditional UPDATE puts in its WHERE clause, and reports how many rows changed.
     */
    private fun updateMatchingRun(
        runId: String,
        expectedState: StoredRunState,
        occurredAtEpochMillis: Long,
        change: (RunEntity) -> RunEntity
    ): Int {
        val index = inserted.indexOfFirst {
            it.runId == runId &&
                it.state == expectedState &&
                it.lastCheckpointEpochMillis <= occurredAtEpochMillis
        }

        if (index < 0) {
            return 0
        }

        inserted[index] = change(inserted[index])
        return 1
    }
}
