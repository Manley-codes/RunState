package com.runstate.mobile.data.local

import com.runstate.mobile.run.RunTimingMark
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

    @Test
    fun `energy foundation leaves old reports unknown and validates independent new answers`() {
        val old = runWith(state = StoredRunState.COMPLETED, postRunEffort = PostRunEffort.HEAVY)
        assertNull(old.energyQuestionVersion)
        assertNull(old.postRunEffortRating)
        assertNull(old.postRunEffortRecordedAtEpochMillis)
        assertNull(old.postRunEnergyRecordedAtEpochMillis)
        assertNull(old.firstPostRunEnergy)
        assertEquals(PostRunEffort.HEAVY, old.postRunEffort)
        listOf(0, 11).forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) { old.copy(postRunEffortRating = invalid) }
        }
        assertThrows(IllegalArgumentException::class.java) { runWith().copy(postRunEffortRating = 5) }
        assertThrows(IllegalArgumentException::class.java) { old.copy(routeLabel = " loop ") }
        assertThrows(IllegalArgumentException::class.java) { old.copy(energyQuestionVersion = 0) }
        assertEquals(1, old.copy(postRunEffortRating = 1).postRunEffortRating)
        assertEquals(10, old.copy(postRunEffortRating = 10).postRunEffortRating)
    }

    @Test
    fun `older rows remain finalized and note validation preserves optional completed only semantics`() {
        val active = runWith()
        assertEquals(true, active.activityFinalized)
        assertNull(active.postRunNote)
        val completed = active.copy(state = StoredRunState.COMPLETED,
            finishEpochMillis = officialStart, activityFinalized = false)
        assertEquals("x".repeat(280), completed.copy(postRunNote = "x".repeat(280)).postRunNote)
        listOf("", " ", "x".repeat(281)).forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) { completed.copy(postRunNote = invalid) }
        }
        assertThrows(IllegalArgumentException::class.java) { active.copy(postRunNote = "Tired") }
    }

    @Test
    fun `timing marks allow zero and require a canonical process identity`() {
        assertEquals(0L, RunTimingMark(0L, canonicalUuid).elapsedRealtimeMillis)
        assertThrows(IllegalArgumentException::class.java) { RunTimingMark(-1L, canonicalUuid) }
        listOf("", " ", "process", canonicalUuid.uppercase(), "1-1-1-1-1").forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) { RunTimingMark(1L, invalid) }
        }
    }

    @Test
    fun `start timing is either absent or one validated pair`() {
        val original = runWith()
        assertNull(original.startElapsedRealtimeMillis)
        assertNull(original.timingSessionId)
        val marked = original.copy(startElapsedRealtimeMillis = 42L, timingSessionId = canonicalUuid)
        assertEquals(42L, marked.startElapsedRealtimeMillis)
        assertEquals(canonicalUuid, marked.timingSessionId)
        assertThrows(IllegalArgumentException::class.java) { original.copy(startElapsedRealtimeMillis = 42L) }
        assertThrows(IllegalArgumentException::class.java) { original.copy(timingSessionId = canonicalUuid) }
        assertThrows(IllegalArgumentException::class.java) { marked.copy(startElapsedRealtimeMillis = -1L) }
        assertThrows(IllegalArgumentException::class.java) { marked.copy(timingSessionId = " ") }
    }

    @Test
    fun `transition timing preserves unknown and rejects half or invalid pairs`() {
        val original = RunTransitionEntity(canonicalUuid, 1, RunTransitionType.PAUSE, officialStart)
        assertNull(original.elapsedRealtimeMillis)
        assertNull(original.timingSessionId)
        val marked = original.copy(elapsedRealtimeMillis = 42L, timingSessionId = canonicalUuid)
        assertEquals(42L, marked.elapsedRealtimeMillis)
        assertEquals(canonicalUuid, marked.timingSessionId)
        assertThrows(IllegalArgumentException::class.java) { original.copy(elapsedRealtimeMillis = 42L) }
        assertThrows(IllegalArgumentException::class.java) { original.copy(timingSessionId = canonicalUuid) }
        assertThrows(IllegalArgumentException::class.java) { marked.copy(elapsedRealtimeMillis = -1L) }
        assertThrows(IllegalArgumentException::class.java) { marked.copy(timingSessionId = "bad-session") }
    }

    /** Builds a valid row, overriding only the field under test. */
    private fun runWith(
        runId: String = canonicalUuid,
        startTimezoneId: String = "America/Chicago",
        state: StoredRunState = StoredRunState.RUNNING,
        finishEpochMillis: Long? = null,
        finalDistanceMeters: Double? = null,
        metricSource: MetricSource? = null,
        telemetryCoverage: TelemetryCoverage? = null,
        displayDistanceUnit: DistanceUnit? = null,
        transitionHistoryComplete: Boolean? = null,
        postRunEffort: PostRunEffort? = null,
        musicObservationRequested: Boolean = false,
        musicReflectionSharingAllowed: Boolean = false
    ) = RunEntity(
        runId = runId,
        state = state,
        officialStartEpochMillis = officialStart,
        startTimezoneId = startTimezoneId,
        lastCheckpointEpochMillis = officialStart,
        finishEpochMillis = finishEpochMillis,
        finalDistanceMeters = finalDistanceMeters,
        metricSource = metricSource,
        telemetryCoverage = telemetryCoverage,
        displayDistanceUnit = displayDistanceUnit,
        transitionHistoryComplete = transitionHistoryComplete,
        postRunEffort = postRunEffort,
        musicObservationRequested = musicObservationRequested,
        musicReflectionSharingAllowed = musicReflectionSharingAllowed
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

    @Test
    fun `music sharing is separate consent and requires local observation`() {
        assertEquals(false, runWith().musicReflectionSharingAllowed)
        assertEquals(false, runWith(musicObservationRequested = true).musicReflectionSharingAllowed)
        assertEquals(true, runWith(
            musicObservationRequested = true, musicReflectionSharingAllowed = true
        ).musicReflectionSharingAllowed)
        assertThrows(IllegalArgumentException::class.java) {
            runWith(musicReflectionSharingAllowed = true)
        }
    }

    @Test
    fun `effort is unknown by default and only completed runs may carry a choice`() {
        assertNull(runWith().postRunEffort)
        assertEquals(
            PostRunEffort.HEAVY,
            runWith(state = StoredRunState.COMPLETED, postRunEffort = PostRunEffort.HEAVY)
                .postRunEffort
        )
        for (unfinishedState in listOf(StoredRunState.RUNNING, StoredRunState.PAUSED)) {
            assertThrows(IllegalArgumentException::class.java) {
                runWith(state = unfinishedState, postRunEffort = PostRunEffort.HEAVY)
            }
        }
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

    /** A freshly created live row may certify that its transition history starts here. */
    @Test
    fun `a live run may carry complete transition history provenance`() {
        assertEquals(true, runWith(transitionHistoryComplete = true).transitionHistoryComplete)
    }

    /** A completed row accepts one coherent finalized fixture-metric group. */
    @Test
    fun `completed fixture metrics are accepted as one group`() {
        val completed = runWith(
            state = StoredRunState.COMPLETED,
            finishEpochMillis = officialStart + 60_000L,
            finalDistanceMeters = 180.0,
            metricSource = MetricSource.FIXTURE,
            telemetryCoverage = TelemetryCoverage.COMPLETE,
            displayDistanceUnit = DistanceUnit.MILES,
            transitionHistoryComplete = true
        )

        assertEquals(180.0, completed.finalDistanceMeters!!, 0.0)
        assertEquals(MetricSource.FIXTURE, completed.metricSource)
        assertEquals(TelemetryCoverage.COMPLETE, completed.telemetryCoverage)
        assertEquals(DistanceUnit.MILES, completed.displayDistanceUnit)
    }

    /** Old completed rows remain readable with every version-3 addition unknown. */
    @Test
    fun `a migrated completed run accepts all version three fields as null`() {
        val migrated = runWith(state = StoredRunState.COMPLETED)

        assertNull(migrated.finalDistanceMeters)
        assertNull(migrated.metricSource)
        assertNull(migrated.telemetryCoverage)
        assertNull(migrated.displayDistanceUnit)
        assertNull(migrated.transitionHistoryComplete)
    }

    /** A source without its coverage and display unit is not a finalized metric group. */
    @Test
    fun `partial metric metadata is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            runWith(
                state = StoredRunState.COMPLETED,
                metricSource = MetricSource.FIXTURE
            )
        }
    }

    /** Final metrics describe a completed run and cannot appear on a live row. */
    @Test
    fun `final metrics on a live run are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            runWith(
                finalDistanceMeters = 180.0,
                metricSource = MetricSource.FIXTURE,
                telemetryCoverage = TelemetryCoverage.COMPLETE,
                displayDistanceUnit = DistanceUnit.MILES
            )
        }
    }

    /** Metrics cannot be final when the same row cannot say when the run finished. */
    @Test
    fun `final metrics without a durable finish are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            runWith(
                state = StoredRunState.COMPLETED,
                finalDistanceMeters = 180.0,
                metricSource = MetricSource.FIXTURE,
                telemetryCoverage = TelemetryCoverage.COMPLETE,
                displayDistanceUnit = DistanceUnit.MILES
            )
        }
    }

    /** SQLite REAL values used as distance must stay finite and non-negative. */
    @Test
    fun `invalid final distances are rejected`() {
        listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach { invalidDistance ->
            assertThrows(IllegalArgumentException::class.java) {
                runWith(
                    state = StoredRunState.COMPLETED,
                    finalDistanceMeters = invalidDistance,
                    metricSource = MetricSource.FIXTURE,
                    telemetryCoverage = TelemetryCoverage.COMPLETE,
                    displayDistanceUnit = DistanceUnit.MILES
                )
            }
        }
    }

    /** Unavailable is an honest absence, never another spelling of zero or a value. */
    @Test
    fun `unavailable telemetry rejects a claimed distance`() {
        assertThrows(IllegalArgumentException::class.java) {
            FinalRunMetrics(
                distanceMeters = 1.0,
                source = MetricSource.FIXTURE,
                coverage = TelemetryCoverage.UNAVAILABLE,
                displayUnit = DistanceUnit.MILES
            )
        }
    }
}
