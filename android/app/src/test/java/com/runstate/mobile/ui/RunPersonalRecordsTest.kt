package com.runstate.mobile.ui

import com.runstate.mobile.data.local.DistanceUnit
import com.runstate.mobile.data.local.MetricSource
import com.runstate.mobile.data.local.RunEntity
import com.runstate.mobile.data.local.RunTrackingMode
import com.runstate.mobile.data.local.StoredRunState
import com.runstate.mobile.data.local.TelemetryCoverage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RunPersonalRecordsTest {
    private val current = run(3, 30_000L, 40_000L, 5_000.0, finalized = false)
    private val earlier = run(1, 10_000L, 20_000L, 4_000.0)

    @Test fun `durable completion can recognize improvement before Save Activity finalization`() {
        assertEquals("Your longest recorded run.", longestRunAchievementFor(current, listOf(earlier)))
        assertEquals("Your longest recorded run.",
            longestRunAchievementFor(current.copy(activityFinalized = true), listOf(earlier)))
    }

    @Test fun `first eligible run and self-only history remain silent`() {
        assertNull(longestRunAchievementFor(current, emptyList()))
        assertNull(longestRunAchievementFor(current, listOf(current.copy(activityFinalized = true))))
        assertNull(longestRunAchievementFor(current, listOf(earlier.copy(runId = current.runId))))
    }

    @Test fun `ties and shorter runs do not announce and strongest prior wins in any order`() {
        val longest = run(2, 21_000L, 29_000L, 6_000.0)
        assertNull(longestRunAchievementFor(current.copy(finalDistanceMeters = 4_000.0), listOf(earlier)))
        assertNull(longestRunAchievementFor(current.copy(finalDistanceMeters = 3_000.0), listOf(earlier)))
        assertNull(longestRunAchievementFor(current, listOf(earlier, longest)))
        assertNull(longestRunAchievementFor(current, listOf(longest, earlier)))
    }

    @Test fun `comparison uses raw meters independent of display units or rounded distance`() {
        val prior = earlier.copy(finalDistanceMeters = 4_999.999, displayDistanceUnit = DistanceUnit.MILES)
        assertEquals("Your longest recorded run.", longestRunAchievementFor(
            current.copy(displayDistanceUnit = DistanceUnit.KILOMETERS), listOf(prior)))
        assertNull(longestRunAchievementFor(current.copy(finalDistanceMeters = 4_999.998), listOf(prior)))
    }

    @Test fun `unfinalized overlap and future runs cannot establish or defeat a prior baseline`() {
        val invalidPriors = listOf(
            earlier.copy(activityFinalized = false),
            run(4, 25_000L, 30_001L, 10_000.0),
            run(5, 40_000L, 50_000L, 10_000.0)
        )
        assertNull(longestRunAchievementFor(current, invalidPriors))
        assertEquals("Your longest recorded run.", longestRunAchievementFor(current, invalidPriors + earlier))
        assertEquals("Your longest recorded run.", longestRunAchievementFor(current,
            listOf(earlier.copy(finishEpochMillis = current.officialStartEpochMillis))))
    }

    @Test fun `sample unknown and mixed provenance never claim a record`() {
        for (candidate in unsupportedEvidence(current)) {
            assertNull("Unsupported current evidence: $candidate", longestRunAchievementFor(candidate, listOf(earlier)))
        }
    }

    @Test fun `sample partial unknown and zero distance history neither set nor block a baseline`() {
        val unsupported = unsupportedEvidence(earlier)
        assertNull(longestRunAchievementFor(current, unsupported))
        assertEquals("Your longest recorded run.", longestRunAchievementFor(current, unsupported + earlier))
    }

    @Test fun `live paused and legacy unfinished evidence never produces an achievement`() {
        val legacy = legacyRun(6)
        val running = legacy.copy(state = StoredRunState.RUNNING)
        val paused = legacy.copy(state = StoredRunState.PAUSED)
        for (candidate in listOf(legacy, running, paused)) {
            assertNull(longestRunAchievementFor(candidate, listOf(earlier)))
            assertNull(longestRunAchievementFor(current, listOf(candidate)))
        }
    }

    private fun unsupportedEvidence(run: RunEntity): List<RunEntity> = listOf(
        run.copy(trackingMode = RunTrackingMode.FIXTURE, metricSource = MetricSource.FIXTURE),
        run.copy(trackingMode = RunTrackingMode.FIXTURE),
        run.copy(metricSource = MetricSource.FIXTURE),
        run.copy(trackingMode = null),
        run.copy(telemetryCoverage = TelemetryCoverage.PARTIAL),
        run.copy(telemetryCoverage = TelemetryCoverage.UNAVAILABLE, finalDistanceMeters = null),
        run.copy(finalDistanceMeters = 0.0),
        run.copy(finalDistanceMeters = null, metricSource = null, telemetryCoverage = null,
            displayDistanceUnit = null)
    )

    private fun run(number: Int, start: Long, finish: Long, distance: Double, finalized: Boolean = true) = RunEntity(
        runId = "00000000-0000-0000-0000-${number.toString().padStart(12, '0')}",
        state = StoredRunState.COMPLETED,
        officialStartEpochMillis = start,
        startTimezoneId = "America/Chicago",
        lastCheckpointEpochMillis = finish,
        finishEpochMillis = finish,
        finalDistanceMeters = distance,
        metricSource = MetricSource.GPS,
        telemetryCoverage = TelemetryCoverage.COMPLETE,
        displayDistanceUnit = DistanceUnit.MILES,
        trackingMode = RunTrackingMode.GPS,
        activityFinalized = finalized
    )

    private fun legacyRun(number: Int) = RunEntity(
        runId = "00000000-0000-0000-0000-${number.toString().padStart(12, '0')}",
        state = StoredRunState.COMPLETED,
        officialStartEpochMillis = 1_000L,
        startTimezoneId = "America/Chicago",
        lastCheckpointEpochMillis = 1_000L
    )
}
