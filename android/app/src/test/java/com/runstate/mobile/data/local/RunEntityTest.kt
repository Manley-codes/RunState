package com.runstate.mobile.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Checks the guards a run row must pass before it can exist.
 *
 * These run on the JVM with no database involved: the rules live in the entity's
 * constructor, so they can be proved without a phone or an emulator.
 */
class RunEntityTest {

    private val officialStart = 1_756_000_000_000L
    private val canonicalUuid = "0f6a2c1e-9d43-4b7a-9c21-7b5e8a4d1f30"

    /** Builds a valid row, overriding only the field under test. */
    private fun runWith(
        runId: String = canonicalUuid,
        startTimezoneId: String = "America/Chicago",
        state: StoredRunState = StoredRunState.RUNNING,
        finishEpochMillis: Long? = null
    ) = RunEntity(
        runId = runId,
        state = state,
        officialStartEpochMillis = officialStart,
        startTimezoneId = startTimezoneId,
        lastCheckpointEpochMillis = officialStart,
        finishEpochMillis = finishEpochMillis
    )

    /**
     * Proves the spelling the app is meant to produce is accepted and kept verbatim.
     */
    @Test
    fun `canonical lowercase uuid is accepted and stored unchanged`() {

        // Act
        val run = runWith(runId = canonicalUuid)

        // Assert: accepted, and not silently rewritten on the way in.
        assertEquals(canonicalUuid, run.runId)
    }

    /**
     * Proves the rule does not pin a UUID version, only the spelling.
     *
     * The `1` in the third group marks a version-1 UUID rather than the version-4 the
     * app is expected to generate. Which generator is used is a separate decision.
     */
    @Test
    fun `a canonical uuid of another version is still accepted`() {

        // Arrange + act
        val versionOne = "f81d4fae-7dec-11d0-a765-00a0c91e6bf6"
        val run = runWith(runId = versionOne)

        // Assert
        assertEquals(versionOne, run.runId)
    }

    /**
     * Proves uppercase text is refused.
     *
     * SQLite compares `run_id` as TEXT, so an uppercase spelling of the same UUID
     * would be a second, separate run rather than the same one.
     */
    @Test
    fun `uppercase uuid is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            runWith(runId = canonicalUuid.uppercase())
        }
    }

    /**
     * Proves the dashless 32-character form is refused.
     */
    @Test
    fun `uuid without dashes is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            runWith(runId = canonicalUuid.replace("-", ""))
        }
    }

    /**
     * Proves shortened groups are refused.
     *
     * `UUID.fromString` parses this and pads it back out to a full UUID, so parsing
     * alone would let it through. The round-trip comparison is what catches it.
     */
    @Test
    fun `shortened uuid groups are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            runWith(runId = "f81d4fae-7dec-11d0-a765-1")
        }
    }

    /**
     * Proves non-hexadecimal characters are refused.
     */
    @Test
    fun `non hexadecimal uuid text is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            runWith(runId = "zzzzzzzz-9d43-4b7a-9c21-7b5e8a4d1f30")
        }
    }

    /**
     * Proves an empty identity is refused.
     */
    @Test
    fun `empty uuid text is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            runWith(runId = "")
        }
    }

    /**
     * Proves an unresolvable zone is refused as a bad argument.
     *
     * The type matters as much as the rejection: this must surface the same
     * `IllegalArgumentException` a bad UUID does, not a raw `DateTimeException`.
     */
    @Test
    fun `unparseable timezone is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            runWith(startTimezoneId = "Mars/Olympus_Mons")
        }
    }

    /**
     * Proves an empty zone is refused too, rather than quietly meaning "the default".
     */
    @Test
    fun `empty timezone is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            runWith(startTimezoneId = "")
        }
    }

    /**
     * Proves a run that has not ended simply has no finish time.
     *
     * The default matters as much as the null: every version-1 call site constructs a
     * row without mentioning a finish, and those call sites must keep meaning exactly
     * what they meant before the column existed.
     */
    @Test
    fun `a run with no finish defaults to an unknown finish`() {
        assertNull(runWith().finishEpochMillis)
    }

    /**
     * Proves a COMPLETED row with no finish time is readable rather than rejected.
     *
     * This is the migration compatibility case, not an allowance for new code. Version 1
     * had no finish column, so a row stored as COMPLETED under version 1 genuinely has
     * no finish to recover and the migration is forbidden to invent one. Refusing that
     * row here would crash the app while reading its own history. New completions get
     * their non-null finish from the DAO's completion transaction instead.
     */
    @Test
    fun `a completed run with no finish is accepted for migrated rows`() {

        // Act
        val migratedCompletedRun = runWith(state = StoredRunState.COMPLETED)

        // Assert: readable, and honest about what it does not know.
        assertEquals(StoredRunState.COMPLETED, migratedCompletedRun.state)
        assertNull(migratedCompletedRun.finishEpochMillis)
    }

    /**
     * Proves a finish before the official start is refused.
     *
     * A run that ended before it began is not a recoverable record, and no migrated row
     * can look like this: the new column is created empty and only new completions
     * ever fill it.
     */
    @Test
    fun `a finish before the official start is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            runWith(
                state = StoredRunState.COMPLETED,
                finishEpochMillis = officialStart - 1L
            )
        }
    }

    /**
     * Proves an unfinished run cannot carry a finish time.
     *
     * Null on a COMPLETED row is a version-1 gap the app has to tolerate. A finish on a
     * run that has not ended is the opposite: not an unknown, but a contradiction, since
     * the row would claim the run is still going and that it already ended.
     */
    @Test
    fun `a finish on a run that has not ended is rejected`() {
        listOf(StoredRunState.RUNNING, StoredRunState.PAUSED).forEach { unfinishedState ->
            assertThrows(
                "$unfinishedState must not accept a finish time",
                IllegalArgumentException::class.java
            ) {
                runWith(state = unfinishedState, finishEpochMillis = officialStart + 60_000L)
            }
        }
    }

    /**
     * Proves a run that ends in the same millisecond it began is still a valid record.
     *
     * The rule is about impossible ordering, not about a minimum duration. An
     * immediately abandoned run is a real thing that must be storable.
     */
    @Test
    fun `a finish equal to the official start is accepted`() {

        // Act
        val instantRun = runWith(
            state = StoredRunState.COMPLETED,
            finishEpochMillis = officialStart
        )

        // Assert
        assertEquals(officialStart, instantRun.finishEpochMillis)
    }
}
