package com.runstate.mobile.data.local

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks that [FakeRunDao]'s discovery answers what the real Room query would answer.
 *
 * A fake is only useful while it stays honest. Every JVM test that reasons about
 * unfinished runs is really reasoning about this implementation, so if its filtering or
 * its ordering drifted from the SQL, those tests would keep passing while describing
 * behavior the phone does not have.
 *
 * The fixture is therefore the same shape as the real-database ordering test in
 * `RunStateDatabaseTest`: a tie on official start, the higher UUID inserted first, and
 * a completed row that must not come back. This file is about that fidelity and nothing
 * else — the DAO's lifecycle behavior is covered elsewhere, against a real database.
 */
class FakeRunDaoTest {

    private companion object {
        const val OFFICIAL_START = 1_756_000_000_000L

        /** Ten minutes in, shared by two rows so the UUID tie-break has to decide. */
        const val SHARED_LATER_START = OFFICIAL_START + 600_000L
        const val FINISHED_AT = OFFICIAL_START + 1_800_000L
    }

    /** Paused, earliest start, and deliberately the highest UUID of the three. */
    private val earliestRun = RunEntity(
        runId = "f0a1b2c3-d4e5-4f60-8a1b-2c3d4e5f6071",
        state = StoredRunState.PAUSED,
        officialStartEpochMillis = OFFICIAL_START,
        startTimezoneId = "America/Chicago",
        lastCheckpointEpochMillis = OFFICIAL_START
    )

    /** Running, tied on start, higher UUID — inserted before its lower-UUID twin. */
    private val higherUuidRun = earliestRun.copy(
        runId = "e1d2c3b4-a596-4877-8b9a-0c1d2e3f4a5b",
        state = StoredRunState.RUNNING,
        officialStartEpochMillis = SHARED_LATER_START,
        lastCheckpointEpochMillis = SHARED_LATER_START
    )

    /** Paused, tied on start, lower UUID — must come back ahead of its twin. */
    private val lowerUuidRun = earliestRun.copy(
        runId = "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
        state = StoredRunState.PAUSED,
        officialStartEpochMillis = SHARED_LATER_START,
        lastCheckpointEpochMillis = SHARED_LATER_START
    )

    /** Finished business. It must never appear as a recovery candidate. */
    private val completedRun = earliestRun.copy(
        runId = "7b3d9e10-2c4f-4a86-9d05-6e8f1a2b3c4d",
        state = StoredRunState.COMPLETED,
        officialStartEpochMillis = OFFICIAL_START + 30_000L,
        lastCheckpointEpochMillis = FINISHED_AT,
        finishEpochMillis = FINISHED_AT
    )

    /**
     * Proves the fake filters and orders exactly as the real discovery query does.
     */
    @Test
    fun `discovery returns active rows in start then uuid order without touching storage`() {

        // Arrange: inserted so that insertion order is wrong on both sort columns.
        val dao = FakeRunDao()
        val insertionOrder = listOf(higherUuidRun, lowerUuidRun, completedRun, earliestRun)
        runBlocking { insertionOrder.forEach { dao.insert(it) } }

        // Act: through the inherited public wrapper, so the fake is asked the same
        // question production asks, with the same two states supplied by the DAO.
        val discovered = runBlocking { dao.findActiveRuns() }

        // Assert: both unfinished states came back, oldest start first, with the tied
        // pair separated by canonical UUID text rather than by when they arrived.
        assertEquals(
            listOf(earliestRun, lowerUuidRun, higherUuidRun),
            discovered
        )

        // Assert: history stayed out of the answer.
        assertTrue(
            "A COMPLETED run must never be offered for recovery: $discovered",
            discovered.none { it.runId == completedRun.runId }
        )

        // Assert: reading did not reorder or otherwise disturb the fake's own storage.
        // A discovery that rearranged what it read would be a write.
        assertEquals(insertionOrder, dao.inserted)
    }
}
