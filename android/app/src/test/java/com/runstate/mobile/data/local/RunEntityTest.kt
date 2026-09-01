package com.runstate.mobile.data.local

import org.junit.Assert.assertEquals
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
        startTimezoneId: String = "America/Chicago"
    ) = RunEntity(
        runId = runId,
        state = StoredRunState.RUNNING,
        officialStartEpochMillis = officialStart,
        startTimezoneId = startTimezoneId,
        lastCheckpointEpochMillis = officialStart
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
}
