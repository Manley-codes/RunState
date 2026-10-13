package com.runstate.mobile.ui

import com.runstate.mobile.data.local.DistanceUnit
import com.runstate.mobile.data.local.MetricSource
import com.runstate.mobile.data.local.MusicObservationKind
import com.runstate.mobile.data.local.MusicPlaybackState
import com.runstate.mobile.data.local.PreRunEnergy
import com.runstate.mobile.data.local.ReflectionBranch
import com.runstate.mobile.data.local.ReflectionStatus
import com.runstate.mobile.data.local.RunEntity
import com.runstate.mobile.data.local.RunMusicObservationEntity
import com.runstate.mobile.data.local.RunReflectionEntity
import com.runstate.mobile.data.local.RunTransitionEntity
import com.runstate.mobile.data.local.RunTransitionType
import com.runstate.mobile.data.local.StoredRunState
import com.runstate.mobile.data.local.TelemetryCoverage
import com.runstate.mobile.run.RunMetrics
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Checks metric text without Compose, a database or an Android device. */
class RunMetricsDisplayTest {

    private val historyRunId = "123e4567-e89b-12d3-a456-426614174000"
    private val historyRequestId = "123e4567-e89b-12d3-a456-426614174001"
    private val historyStart = Instant.parse("2026-10-12T01:30:00Z").toEpochMilli()

    @Test
    fun `history labels music as partial observations under the saved run`() {
        val run = historyRun(
            finish = historyStart + 600_000L,
            historyComplete = true
        ).copy(musicObservationRequested = true)
        val first = RunMusicObservationEntity(
            runId = historyRunId,
            sequenceNumber = 1,
            observationKey = "first",
            observedAtEpochMillis = historyStart + 1_000L,
            elapsedRealtimeNanos = 1_000_000_000L,
            sourcePackage = "com.example.player",
            title = "First title",
            artist = "Artist",
            playbackState = MusicPlaybackState.PLAYING,
            eventKind = MusicObservationKind.SESSION_SNAPSHOT
        )
        val second = first.copy(
            sequenceNumber = 2,
            observationKey = "second",
            title = "Later title"
        )

        val item = historyItemFor(run, emptyList(), null, listOf(second, first))

        assertEquals(true, item.musicObservationRequested)
        assertEquals(2, item.musicObservationCount)
        assertEquals("Later title — Artist · com.example.player", item.lastObservedMedia)
        val noObservation = historyItemFor(run, emptyList(), null)
        assertEquals(0, noObservation.musicObservationCount)
        assertNull(noObservation.lastObservedMedia)
    }

    @Test
    fun `history preserves saved reflection and shows frozen metrics in run timezone`() {
        val finish = historyStart + 600_000L
        val run = historyRun(
            finish = finish,
            distanceMeters = 1_609.344,
            coverage = TelemetryCoverage.COMPLETE,
            historyComplete = true
        ).copy(preRunEnergy = PreRunEnergy.LOW)
        val transitions = listOf(
            RunTransitionEntity(historyRunId, 1, RunTransitionType.PAUSE, historyStart + 300_000L)
        )
        val exactText = "The first line.\nThe second line."
        val reflection = RunReflectionEntity(
            runId = historyRunId,
            requestId = historyRequestId,
            evidenceJson = "{}",
            selectionDeadlineEpochMillis = finish + 6_000L,
            status = ReflectionStatus.READY,
            selectedBranch = ReflectionBranch.FEELING_GOOD,
            postRunEnergy = ReflectionBranch.FEELING_GOOD,
            text = exactText,
            finalizedAtEpochMillis = finish + 7_000L
        )

        val item = historyItemFor(run, transitions, reflection)

        assertEquals("Oct 11, 2026 · 8:30 PM", item.dateLabel)
        assertEquals("00:10:00", item.metrics?.elapsed)
        assertEquals("00:05:00", item.metrics?.active)
        assertEquals("1.00 mi", item.metrics?.distance)
        assertEquals("5:00 /mi", item.metrics?.averagePace)
        assertEquals("Feeling Good", item.energyLabel)
        assertEquals("Low", item.preRunEnergyLabel)
        assertEquals("READY", item.reflectionStatus)
        assertEquals(exactText, item.reflectionText)
        assertEquals(true, item.isTestData)
    }

    @Test
    fun `legacy finished run never invents elapsed or active time`() {
        val item = historyItemFor(
            historyRun(finish = null, historyComplete = false),
            emptyList(),
            null
        )

        assertNull(item.metrics)
        assertNull(item.reflectionStatus)
        assertNull(item.energyLabel)
        assertNull(item.preRunEnergyLabel)
        assertEquals(false, item.isTestData)
    }

    @Test
    fun `pending reflection does not claim confirmed fake output`() {
        val item = historyItemFor(
            historyRun(finish = historyStart + 600_000L, historyComplete = false),
            emptyList(),
            RunReflectionEntity(
                runId = historyRunId,
                requestId = historyRequestId,
                evidenceJson = "{}",
                selectionDeadlineEpochMillis = historyStart + 606_000L
            )
        )

        assertEquals("PENDING", item.reflectionStatus)
        assertNull(item.reflectionText)
        assertEquals(false, item.isTestData)
    }

    @Test
    fun `incomplete transition history retains known elapsed time but leaves active unknown`() {
        val item = historyItemFor(
            historyRun(finish = historyStart + 600_000L, historyComplete = false),
            emptyList(),
            null
        )

        assertEquals("00:10:00", item.metrics?.elapsed)
        assertEquals("—", item.metrics?.active)
        assertEquals("—", item.metrics?.distance)
        assertEquals("—", item.metrics?.averagePace)
    }

    @Test
    fun `partial gps history does not invent active time after a possible service gap`() {
        val item = historyItemFor(
            historyRun(
                finish = historyStart + 600_000L,
                distanceMeters = 100.0,
                coverage = TelemetryCoverage.PARTIAL,
                historyComplete = true
            ).copy(metricSource = MetricSource.GPS),
            listOf(
                RunTransitionEntity(
                    historyRunId,
                    1,
                    RunTransitionType.PAUSE,
                    historyStart + 300_000L
                )
            ),
            null
        )

        assertEquals("00:10:00", item.metrics?.elapsed)
        assertEquals("—", item.metrics?.active)
        assertEquals("0.06 mi", item.metrics?.distance)
        assertEquals("—", item.metrics?.averagePace)
    }

    private fun historyRun(
        finish: Long?,
        distanceMeters: Double? = null,
        coverage: TelemetryCoverage? = null,
        historyComplete: Boolean
    ): RunEntity = RunEntity(
        runId = historyRunId,
        state = StoredRunState.COMPLETED,
        officialStartEpochMillis = historyStart,
        startTimezoneId = "America/Chicago",
        lastCheckpointEpochMillis = finish ?: historyStart,
        finishEpochMillis = finish,
        finalDistanceMeters = distanceMeters,
        metricSource = if (coverage == null) null else MetricSource.FIXTURE,
        telemetryCoverage = coverage,
        displayDistanceUnit = if (coverage == null) null else DistanceUnit.MILES,
        transitionHistoryComplete = historyComplete
    )

    @Test
    fun `mile metrics use stable duration distance and pace text`() {
        val display = runMetricsDisplayFor(
            RunMetrics(
                elapsedMillis = 3_661_999L,
                activeMillis = 60_000L,
                distanceMeters = 1_609.344,
                paceSecondsPerMeter = 360.0 / 1_609.344,
                displayUnit = DistanceUnit.MILES,
                source = MetricSource.FIXTURE,
                coverage = TelemetryCoverage.COMPLETE
            )
        )

        assertEquals("01:01:01", display.elapsed)
        assertEquals("00:01:00", display.active)
        assertEquals("1.00 mi", display.distance)
        assertEquals("6:00 /mi", display.averagePace)
        assertEquals("Fixture metrics", display.sourceLabel)
    }

    @Test
    fun `kilometer metrics use the stored display unit`() {
        val display = runMetricsDisplayFor(
            RunMetrics(
                elapsedMillis = 300_000L,
                activeMillis = 300_000L,
                distanceMeters = 1_000.0,
                paceSecondsPerMeter = 0.3,
                displayUnit = DistanceUnit.KILOMETERS,
                source = MetricSource.GPS,
                coverage = TelemetryCoverage.COMPLETE
            )
        )

        assertEquals("1.00 km", display.distance)
        assertEquals("5:00 /km", display.averagePace)
        assertEquals("GPS metrics", display.sourceLabel)
    }

    @Test
    fun `partial and unavailable gps evidence are labeled honestly`() {
        val partial = runMetricsDisplayFor(
            RunMetrics(
                elapsedMillis = 60_000L,
                activeMillis = 60_000L,
                distanceMeters = 100.0,
                paceSecondsPerMeter = 0.6,
                displayUnit = DistanceUnit.MILES,
                source = MetricSource.GPS,
                coverage = TelemetryCoverage.PARTIAL
            )
        )
        val unavailable = runMetricsDisplayFor(
            RunMetrics(
                elapsedMillis = 60_000L,
                activeMillis = 60_000L,
                distanceMeters = null,
                paceSecondsPerMeter = null,
                displayUnit = DistanceUnit.MILES,
                source = MetricSource.GPS,
                coverage = TelemetryCoverage.UNAVAILABLE
            )
        )

        assertEquals("GPS metrics · partial", partial.sourceLabel)
        assertEquals("—", partial.averagePace)
        assertEquals("GPS distance unavailable", unavailable.sourceLabel)
        assertEquals("—", unavailable.distance)
    }

    @Test
    fun `unavailable values remain visibly unknown rather than becoming zero`() {
        val display = runMetricsDisplayFor(
            RunMetrics(
                elapsedMillis = 90_000L,
                activeMillis = null,
                distanceMeters = null,
                paceSecondsPerMeter = null,
                displayUnit = DistanceUnit.MILES,
                source = null,
                coverage = TelemetryCoverage.UNAVAILABLE
            )
        )

        assertEquals("00:01:30", display.elapsed)
        assertEquals("—", display.active)
        assertEquals("—", display.distance)
        assertEquals("—", display.averagePace)
        assertEquals("Metrics unavailable", display.sourceLabel)
    }

    @Test
    fun `partial milliseconds are not rounded into time that has not happened`() {
        val display = runMetricsDisplayFor(
            RunMetrics(
                elapsedMillis = 999L,
                activeMillis = 999L,
                distanceMeters = 2.997,
                paceSecondsPerMeter = 1.0 / 3.0,
                displayUnit = DistanceUnit.MILES,
                source = MetricSource.FIXTURE,
                coverage = TelemetryCoverage.COMPLETE
            )
        )

        assertEquals("00:00:00", display.elapsed)
        assertEquals("00:00:00", display.active)
    }
}
