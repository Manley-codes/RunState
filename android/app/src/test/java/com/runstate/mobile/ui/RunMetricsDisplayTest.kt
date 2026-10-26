package com.runstate.mobile.ui

import com.runstate.mobile.data.local.DistanceUnit
import com.runstate.mobile.data.local.MetricSource
import com.runstate.mobile.data.local.MusicObservationKind
import com.runstate.mobile.data.local.MusicPlaybackState
import com.runstate.mobile.data.local.PostRunEffort
import com.runstate.mobile.data.local.PreRunEnergy
import com.runstate.mobile.data.local.ReflectionBranch
import com.runstate.mobile.data.local.ReflectionStatus
import com.runstate.mobile.data.local.RunEntity
import com.runstate.mobile.data.local.RunIntention
import com.runstate.mobile.data.local.RunMusicObservationEntity
import com.runstate.mobile.data.local.RunReflectionEntity
import com.runstate.mobile.data.local.RunTransitionEntity
import com.runstate.mobile.data.local.RunTransitionType
import com.runstate.mobile.data.local.RunTrackingMode
import com.runstate.mobile.data.local.RunSplitEntity
import com.runstate.mobile.data.local.RunSplitSnapshotEntity
import com.runstate.mobile.data.local.RunSplitStatus
import com.runstate.mobile.data.local.StoredRunState
import com.runstate.mobile.data.local.TelemetryCoverage
import com.runstate.mobile.data.local.RunWeatherEntity
import com.runstate.mobile.data.local.RunWeatherStatus
import com.runstate.mobile.weather.RunWeatherSnapshot
import com.runstate.mobile.run.RunMetrics
import com.runstate.mobile.run.RunSplitCalculator
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Checks metric text without Compose, a database or an Android device. */
class RunMetricsDisplayTest {

    private val historyRunId = "123e4567-e89b-12d3-a456-426614174000"
    private val historyRequestId = "123e4567-e89b-12d3-a456-426614174001"
    private val historyStart = Instant.parse("2026-10-12T01:30:00Z").toEpochMilli()

    @Test
    fun `history passes typed starting energy and metric source independently of display strings`() {
        val run = historyRun(historyStart + 600_000L, 2_000.0,
            TelemetryCoverage.PARTIAL, true).copy(preRunEnergy = PreRunEnergy.MODERATE,
            metricSource = MetricSource.GPS, trackingMode = RunTrackingMode.GPS)
        val item = historyItemFor(run, emptyList(), null)
        assertEquals(PreRunEnergy.MODERATE, item.preRunEnergy)
        assertEquals(MetricSource.GPS, item.metricSource)
        assertEquals("Moderate", item.preRunEnergyLabel)
        assertTrue(item.isGpsRun)
        assertTrue(item.insightEligible)

        val old = historyItemFor(historyRun(null, historyComplete = false), emptyList(), null)
        assertNull(old.preRunEnergy)
        assertNull(old.metricSource)
        assertNull(old.energy)
    }

    @Test
    fun `history keeps raw question provenance and context when an answer was edited`() {
        val finished = historyStart + 600_000L
        val firstAnswer = finished + 20_000L
        val laterEdit = finished + 600_000L
        val run = historyRun(finished, 2_000.0, TelemetryCoverage.COMPLETE, true).copy(
            trackingMode = RunTrackingMode.GPS, metricSource = MetricSource.GPS,
            preRunEnergy = PreRunEnergy.LOW, energyQuestionVersion = 2,
            preRunEnergyRecordedAtEpochMillis = historyStart - 8_000L,
            runIntention = RunIntention.KEEP_EASY, routeLabel = "Park Loop",
            postRunEffort = PostRunEffort.HEAVY, postRunEffortRating = null,
            postRunEffortQuestionVersion = 1, firstPostRunEffortRating = 7,
            postRunEffortRecordedAtEpochMillis = firstAnswer,
            postRunEffortUpdatedAtEpochMillis = laterEdit,
            firstPostRunEnergy = ReflectionBranch.NO_SELECTION, firstPostRunEnergyQuestionVersion = 2,
            postRunEnergyQuestionVersion = 2, postRunEnergyRecordedAtEpochMillis = firstAnswer,
            postRunEnergyUpdatedAtEpochMillis = laterEdit)
        val reflection = musicSnapshotReflection("{}").copy(postRunEnergy = ReflectionBranch.FEELING_GOOD)
        val item = historyItemFor(run, emptyList(), reflection)
        assertEquals(historyStart, item.officialStartEpochMillis)
        assertEquals("America/Chicago", item.startTimezoneId)
        assertEquals(finished, item.finishEpochMillis)
        assertEquals(2_000.0, item.distanceMeters!!, 0.0)
        assertEquals(RunIntention.KEEP_EASY, item.runIntention)
        assertEquals("Park Loop", item.routeLabel)
        assertEquals(2, item.energyQuestionVersion)
        assertEquals(historyStart - 8_000L, item.preRunEnergyRecordedAtEpochMillis)
        assertEquals(ReflectionBranch.NO_SELECTION, item.firstPostRunEnergy)
        assertEquals(ReflectionBranch.FEELING_GOOD, item.energy)
        assertEquals(2, item.firstPostRunEnergyQuestionVersion)
        assertEquals(2, item.postRunEnergyQuestionVersion)
        assertEquals(firstAnswer, item.postRunEnergyRecordedAtEpochMillis)
        assertEquals(laterEdit, item.postRunEnergyUpdatedAtEpochMillis)
        assertEquals(PostRunEffort.HEAVY, item.effort)
        assertNull(item.postRunEffortRating)
        assertEquals(7, item.firstPostRunEffortRating)
        assertEquals(firstAnswer, item.postRunEffortRecordedAtEpochMillis)
        assertEquals(laterEdit, item.postRunEffortUpdatedAtEpochMillis)
        assertEquals("Not recorded", historyEffortLabel(item))
    }

    @Test
    fun `unsaved completed activity never becomes energy insight evidence`() {
        val run = historyRun(historyStart + 600_000L, 2_000.0, TelemetryCoverage.COMPLETE, true)
            .copy(trackingMode = RunTrackingMode.GPS, metricSource = MetricSource.GPS,
                preRunEnergy = PreRunEnergy.LOW, energyQuestionVersion = 2, activityFinalized = false)
        val item = historyItemFor(run, emptyList(), musicSnapshotReflection("{}")
            .copy(postRunEnergy = ReflectionBranch.FEELING_GOOD))
        assertFalse(item.insightEligible)
        assertTrue(runnerEnergyFoundationFor(listOf(item)).runs.isEmpty())
    }

    @Test
    fun `reported energy contributes while reflection is pending or failed`() {
        val run = historyRun(historyStart + 600_000L, 1_000.0,
            TelemetryCoverage.COMPLETE, true).copy(preRunEnergy = PreRunEnergy.LOW,
            metricSource = MetricSource.GPS, trackingMode = RunTrackingMode.GPS)
        for (status in listOf(ReflectionStatus.PENDING, ReflectionStatus.FAILED)) {
            val reflection = musicSnapshotReflection("{}").copy(status = status,
                postRunEnergy = ReflectionBranch.FEELING_GOOD, isTestData = true)
            val item = historyItemFor(run, emptyList(), reflection)
            assertFalse(item.isTestData)
            assertTrue(item.insightEligible)
            assertEquals(PreRunEnergy.LOW, item.preRunEnergy)
            assertEquals(ReflectionBranch.FEELING_GOOD, item.energy)

            val genuine = historyItemFor(run, emptyList(), reflection.copy(isTestData = false))
            assertTrue(genuine.insightEligible)
            assertFalse(genuine.isTestData)
            assertNotNull(runnerStateInsightFor((1..3).map { number ->
                item.copy(runId = "223e4567-e89b-12d3-a456-${number.toString().padStart(12, '0')}")
            }))
        }
    }

    @Test
    fun `a confirmed fake reflection remains excluded from personal energy learning`() {
        val run = historyRun(historyStart + 600_000L, 1_000.0,
            TelemetryCoverage.COMPLETE, true).copy(preRunEnergy = PreRunEnergy.LOW,
            metricSource = MetricSource.GPS, trackingMode = RunTrackingMode.GPS)
        val fake = musicSnapshotReflection("{}").copy(status = ReflectionStatus.READY,
            selectedBranch = ReflectionBranch.FEELING_GOOD,
            postRunEnergy = ReflectionBranch.FEELING_GOOD,
            text = "Fixture reflection.", finalizedAtEpochMillis = historyStart + 601_000L,
            isTestData = true)
        val item = historyItemFor(run, emptyList(), fake)
        assertTrue(item.isTestData)
        assertFalse(item.insightEligible)
        assertNull(runnerStateInsightFor((1..3).map { number ->
            item.copy(runId = "223e4567-e89b-12d3-a456-${number.toString().padStart(12, '0')}")
        }))
    }

    @Test
    fun `split snapshot absence pending and unavailable stay distinct without exposing stale rows`() {
        val row = splitRow()
        assertEquals(HistorySplitDisplay.NotRecorded, historySplitsFor(null, listOf(row)))
        assertEquals(HistorySplitDisplay.Pending,
            historySplitsFor(splitSnapshot(RunSplitStatus.PENDING), listOf(row)))
        assertEquals(HistorySplitDisplay.Unavailable,
            historySplitsFor(splitSnapshot(RunSplitStatus.UNAVAILABLE), listOf(row)))
    }

    @Test
    fun `mile split table sorts frozen rows and labels a final quarter mile with its actual time`() {
        val mile = splitRow(endDistance = 1_609.344, endActive = 600_000L)
        val partial = splitRow(number = 2, startDistance = 1_609.344, endDistance = 2_011.68,
            startActive = 600_000L, endActive = 750_000L, partial = true)
        assertEquals(HistorySplitDisplay.Ready(listOf(
            RunSplitDisplayRow("1 mi", "10:00", "10:00/mi"),
            RunSplitDisplayRow("Final 0.25 mi", "2:30", "10:00/mi")
        ), "mi"), historySplitsFor(splitSnapshot(), listOf(partial, mile)))
    }

    @Test
    fun `kilometer split table uses the snapshot unit and rounds pace without discarding hours`() {
        val row = splitRow(endDistance = 1_000.0, endActive = 3_661_000L)
        assertEquals(HistorySplitDisplay.Ready(listOf(
            RunSplitDisplayRow("1 km", "1:01:01", "1:01:01/km")
        ), "km"), historySplitsFor(splitSnapshot(unit = DistanceUnit.KILOMETERS), listOf(row)))
    }

    @Test
    fun `tiny final split never rounds its distance or time to zero`() {
        val row = splitRow(endDistance = 1.0, endActive = 500L, partial = true)
        assertEquals(HistorySplitDisplay.Ready(listOf(
            RunSplitDisplayRow("Final <0.01 km", "<0:01", "8:20/km")
        ), "km"), historySplitsFor(splitSnapshot(unit = DistanceUnit.KILOMETERS), listOf(row)))
    }

    @Test
    fun `empty ready snapshots and unknown derivation versions fail without pretending no splits exist`() {
        assertEquals(HistorySplitDisplay.Failed, historySplitsFor(splitSnapshot(), emptyList()))
        assertEquals(HistorySplitDisplay.Failed, historySplitsFor(
            splitSnapshot().copy(derivationVersion = RunSplitCalculator.DERIVATION_VERSION + 1),
            listOf(splitRow())))
    }

    @Test
    fun `foreign identity bad indices and nonfinite or nonpositive split facts fail safely`() {
        val valid = splitRow()
        val invalid = listOf(
            valid.copy(runId = historyRequestId),
            valid.copy(splitNumber = 0),
            valid.copy(splitNumber = 2),
            valid.copy(startDistanceMeters = Double.NaN),
            valid.copy(endDistanceMeters = Double.POSITIVE_INFINITY),
            valid.copy(endDistanceMeters = 0.0),
            valid.copy(startActiveMillis = 1L),
            valid.copy(endActiveMillis = 0L),
            valid.copy(paceSecondsPerMeter = Double.NaN),
            valid.copy(paceSecondsPerMeter = Double.POSITIVE_INFINITY),
            valid.copy(paceSecondsPerMeter = 0.0),
            valid.copy(paceSecondsPerMeter = -1.0))
        invalid.forEachIndexed { index, row ->
            assertEquals("Malformed split $index", HistorySplitDisplay.Failed,
                historySplitsFor(splitSnapshot(), listOf(row)))
        }
    }

    @Test
    fun `split gaps duplicate numbers and a partial row before the last one fail safely`() {
        val first = splitRow()
        val second = splitRow(number = 2, startDistance = first.endDistanceMeters,
            endDistance = first.endDistanceMeters * 2, startActive = first.endActiveMillis,
            endActive = first.endActiveMillis * 2)
        val invalid = listOf(
            listOf(first, second.copy(splitNumber = 1)),
            listOf(first, second.copy(startDistanceMeters = first.endDistanceMeters + 1.0)),
            listOf(first, second.copy(startActiveMillis = first.endActiveMillis + 1)),
            listOf(first.copy(isPartial = true), second))
        invalid.forEach { rows ->
            assertEquals(HistorySplitDisplay.Failed, historySplitsFor(splitSnapshot(), rows))
        }
    }

    @Test
    fun `an unrepresentable derived pace remains unknown instead of overflowing the display`() {
        val row = splitRow().copy(paceSecondsPerMeter = Double.MAX_VALUE)
        val display = historySplitsFor(splitSnapshot(), listOf(row)) as HistorySplitDisplay.Ready
        assertEquals("—", display.rows.single().pace)
        assertEquals("10:00", display.rows.single().time)
    }

    private fun splitSnapshot(
        status: RunSplitStatus = RunSplitStatus.READY,
        unit: DistanceUnit = DistanceUnit.MILES
    ) = RunSplitSnapshotEntity(historyRunId, status, RunSplitCalculator.DERIVATION_VERSION, unit)

    private fun splitRow(
        number: Int = 1,
        startDistance: Double = 0.0,
        endDistance: Double = 1_609.344,
        startActive: Long = 0L,
        endActive: Long = 600_000L,
        partial: Boolean = false
    ) = RunSplitEntity(historyRunId, number, startDistance, endDistance, startActive, endActive,
        historyStart + startActive, historyStart + endActive, partial,
        (endActive - startActive) / 1_000.0 / (endDistance - startDistance))

    @Test
    fun `saved weather uses the provider hour and run timezone with honest units`() {
        val run = sharingHistoryRun()
        val hour = historyStart / 3_600_000L * 3_600_000L
        val snapshot = RunWeatherSnapshot(targetEpochMillis = historyStart,
            validFromEpochMillis = hour, validToEpochMillis = hour + 3_600_000L,
            fetchedAtEpochMillis = historyStart + 5_000L, temperatureCelsius = 20.0,
            apparentTemperatureCelsius = 21.0, relativeHumidityPercent = 60.0,
            windSpeedKmh = 16.09344, weatherCode = 0)
        val record = RunWeatherEntity(run.runId, RunWeatherStatus.READY, snapshot.toJson())
        assertEquals("68°F · Clear sky\nNear start · 8:00 PM hourly estimate\n" +
            "Feels like 70°F · Humidity 60% · Wind 10 mph", weatherSummaryFor(run, record))
        assertTrue(requireNotNull(weatherSummaryFor(run.copy(startTimezoneId = "UTC"), record))
            .contains("1:00 AM hourly estimate"))
        assertTrue(requireNotNull(weatherSummaryFor(run.copy(displayDistanceUnit = DistanceUnit.KILOMETERS,
            metricSource = MetricSource.FIXTURE, telemetryCoverage = TelemetryCoverage.PARTIAL,
            finalDistanceMeters = 1_000.0), record))
            .contains("20°C · Clear sky"))
    }

    @Test
    fun `old disabled pending and damaged weather never invent weather values`() {
        val run = sharingHistoryRun()
        assertNull(weatherSummaryFor(run, null))
        assertNull(weatherSummaryFor(run, RunWeatherEntity(run.runId, RunWeatherStatus.DISABLED)))
        assertEquals("Weather near start · Unavailable",
            weatherSummaryFor(run, RunWeatherEntity(run.runId, RunWeatherStatus.PENDING)))
        assertEquals("Weather near start · Unavailable",
            weatherSummaryFor(run, RunWeatherEntity(run.runId, RunWeatherStatus.READY, "not json")))
        assertEquals("Weather near start · Unavailable",
            weatherSummaryFor(run, RunWeatherEntity(run.runId, RunWeatherStatus.UNAVAILABLE)))
    }

    @Test
    fun `optional music read failure preserves saved run and reflection history`() = runBlocking {
        val run = sharingHistoryRun()
        val reflection = musicSnapshotReflection("{\"state\":\"UNKNOWN\"}").copy(
            status = ReflectionStatus.READY,
            selectedBranch = ReflectionBranch.FEELING_GOOD,
            postRunEnergy = ReflectionBranch.FEELING_GOOD,
            text = "Your saved reflection.\nExactly as delivered.",
            finalizedAtEpochMillis = historyStart + 607_000L,
            isTestData = false
        )
        val expected = historyItemFor(run, emptyList(), reflection)
        val item = historyItemWithOptionalMusic(run, emptyList(), reflection) {
            throw IllegalStateException("Unavailable music store")
        }

        assertEquals(expected.copy(musicObservationsUnavailable = true), item)
        assertTrue(item.musicObservationsUnavailable)
        assertNull(item.lastObservedMedia)
    }

    @Test
    fun `empty music result is unknown evidence rather than a loading failure`() = runBlocking {
        val item = historyItemWithOptionalMusic(sharingHistoryRun(), emptyList(), null) { emptyList() }
        assertFalse(item.musicObservationsUnavailable)
        assertEquals(0, item.musicObservationCount)
    }

    @Test
    fun `history does not query optional music for an opted out run`() = runBlocking {
        val run = historyRun(finish = historyStart + 600_000L, historyComplete = false)
        val item = historyItemWithOptionalMusic(run, emptyList(), null) {
            throw AssertionError("Opted-out music must not be read")
        }
        assertEquals(historyItemFor(run, emptyList(), null), item)
    }

    @Test(expected = CancellationException::class)
    fun `leaving history cancels its music read instead of showing unavailable data`() = runBlocking {
        historyItemWithOptionalMusic(sharingHistoryRun(), emptyList(), null) {
            throw CancellationException("History closed")
        }
        Unit
    }

    @Test
    fun `completion date uses saved finish and timezone across the utc date boundary`() {
        val finish = Instant.parse("2026-10-13T00:05:00Z").toEpochMilli()
        val run = historyRun(finish = finish, historyComplete = true)

        assertEquals("Mon, Oct 12 · 7:05 PM", completionDateLabelFor(run))
        assertEquals("Tue, Oct 13 · 12:05 AM",
            completionDateLabelFor(run.copy(startTimezoneId = "UTC")))
    }

    @Test
    fun `missing completion finish stays absent without a start or current time fallback`() {
        val run = historyRun(finish = null, historyComplete = false)

        assertNull(completionDateLabelFor(run))
    }

    @Test
    fun `unfinished running and paused rows never receive a completion date`() {
        val unfinished = historyRun(finish = null, historyComplete = true)

        listOf(StoredRunState.RUNNING, StoredRunState.PAUSED).forEach { state ->
            assertNull(completionDateLabelFor(unfinished.copy(state = state)))
        }
    }

    @Test
    fun `invalid saved timezone stays absent without a device zone fallback`() {
        val run = historyRun(finish = historyStart + 600_000L, historyComplete = true)
        // Constructors reject invalid zones. Corrupt only this fixture to exercise the
        // formatter's defensive boundary without weakening the production entity guard.
        RunEntity::class.java.getDeclaredField("startTimezoneId").apply {
            isAccessible = true
            set(run, "Not/A_Real_Zone")
        }

        assertNull(completionDateLabelFor(run))
    }

    @Test
    fun `save receipt uses saved fixture facts and spoken duration units`() {
        val run = historyRun(historyStart + 3_661_000L, 1_609.344,
            TelemetryCoverage.PARTIAL, true)
        val transitions = listOf(RunTransitionEntity(historyRunId, 1, RunTransitionType.PAUSE,
            historyStart + 3_661_000L))
        assertEquals("Run saved. Fixture distance, 1 mile. Active time, 1 hour, 1 minute, 1 second.",
            savedRunReceiptFor(run, transitions))
    }

    @Test
    fun `save receipt identifies partial gps and never invents unavailable distance`() {
        val gps = historyRun(historyStart + 60_000L, 2_000.0,
            TelemetryCoverage.PARTIAL, true).copy(metricSource = MetricSource.GPS,
            displayDistanceUnit = DistanceUnit.KILOMETERS)
        assertEquals("Run saved. Partial GPS distance, 2 kilometers.",
            savedRunReceiptFor(gps))
        assertEquals("Run saved.", savedRunReceiptFor(
            historyRun(historyStart + 60_000L, historyComplete = false)))
        assertEquals("Run saved.", savedRunReceiptFor(historyRun(null, historyComplete = false)))
    }

    @Test
    fun `tiny measured receipt does not round movement to zero`() {
        val run = historyRun(historyStart + 999L, 1.0, TelemetryCoverage.PARTIAL, true)
        val transitions = listOf(RunTransitionEntity(historyRunId, 1, RunTransitionType.PAUSE,
            historyStart + 999L))
        assertEquals("Run saved. Fixture distance, less than 0.01 miles. Active time, 0 seconds.",
            savedRunReceiptFor(run, transitions))
    }

    @Test
    fun `save receipt speaks trusted saved average mile pace using active rather than elapsed time`() {
        val run = historyRun(historyStart + 600_000L, 1_609.344,
            TelemetryCoverage.COMPLETE, true).copy(metricSource = MetricSource.GPS,
            trackingMode = RunTrackingMode.GPS)
        val transitions = listOf(RunTransitionEntity(historyRunId, 1, RunTransitionType.PAUSE,
            historyStart + 500_000L))

        assertEquals("Run saved. 1 mile. Active time, 8 minutes, 20 seconds. " +
            "Average pace, 8 minutes, 20 seconds per mile.", savedRunReceiptFor(run, transitions))
        // Without the actual saved pause rows, neither active duration nor pace is known.
        assertEquals("Run saved. 1 mile.", savedRunReceiptFor(run))
    }

    @Test
    fun `save receipt uses stored kilometer unit and spoken pace rounding and singular units`() {
        val cases = listOf(
            Triple(330_000L, 1_000.0, "5 minutes, 30 seconds"),
            Triple(61_000L, 1_000.0, "1 minute, 1 second"),
            Triple(119_600L, 1_000.0, "2 minutes")
        )
        cases.forEach { (activeMillis, meters, expectedPace) ->
            val run = historyRun(historyStart + activeMillis, meters,
                TelemetryCoverage.COMPLETE, true).copy(metricSource = MetricSource.GPS,
                displayDistanceUnit = DistanceUnit.KILOMETERS)
            val transitions = listOf(RunTransitionEntity(historyRunId, 1, RunTransitionType.PAUSE,
                historyStart + activeMillis))
            val receipt = savedRunReceiptFor(run, transitions)
            assertTrue("Saved pace for $activeMillis active milliseconds: $receipt",
                receipt.endsWith("Average pace, $expectedPace per kilometer."))
        }
    }

    @Test
    fun `save receipt omits average pace for partial unavailable or incomplete saved evidence`() {
        val complete = historyRun(historyStart + 600_000L, 1_609.344,
            TelemetryCoverage.COMPLETE, true).copy(metricSource = MetricSource.GPS)
        val unavailable = complete.copy(finalDistanceMeters = null, telemetryCoverage = TelemetryCoverage.UNAVAILABLE)
        val legacy = historyRun(historyStart + 600_000L, historyComplete = false)
        val partialFixture = complete.copy(metricSource = MetricSource.FIXTURE,
            telemetryCoverage = TelemetryCoverage.PARTIAL)
        listOf(complete.copy(telemetryCoverage = TelemetryCoverage.PARTIAL), unavailable, legacy,
            partialFixture, complete.copy(transitionHistoryComplete = false),
            complete.copy(transitionHistoryComplete = null), complete.copy(finalDistanceMeters = 0.0))
            .forEach { run ->
                val receipt = savedRunReceiptFor(run, emptyList())
                assertFalse(receipt, receipt.contains("pace", ignoreCase = true))
                assertFalse(receipt, receipt.contains("Elapsed time", ignoreCase = true))
                if (run.transitionHistoryComplete != true ||
                    run.metricSource == MetricSource.GPS && run.telemetryCoverage != TelemetryCoverage.COMPLETE) {
                    assertFalse("Unknown active time must never fall back to total: $receipt",
                        receipt.contains("Active time", ignoreCase = true))
                }
            }
    }

    @Test
    fun `save receipt cannot invent pace from foreign malformed or zero active saved timelines`() {
        val run = historyRun(historyStart + 600_000L, 1_609.344,
            TelemetryCoverage.COMPLETE, true).copy(metricSource = MetricSource.GPS)
        val pause = RunTransitionEntity(historyRunId, 1, RunTransitionType.PAUSE, historyStart + 300_000L)
        listOf(
            listOf(pause.copy(runId = "cccccccc-cccc-4ccc-8ccc-cccccccccccc")),
            listOf(pause.copy(sequenceNumber = 2)),
            listOf(pause.copy(occurredAtEpochMillis = historyStart + 700_000L))
        ).forEach { transitions ->
            assertEquals("Run saved. 1 mile.", savedRunReceiptFor(run, transitions))
        }
        assertEquals("Run saved. 1 mile. Active time, 0 seconds.",
            savedRunReceiptFor(run, listOf(pause.copy(occurredAtEpochMillis = historyStart))))
    }

    @Test
    fun `save receipt stays factual when the run carries energy effort and music choices`() {
        val run = historyRun(historyStart + 600_000L, 1_609.344,
            TelemetryCoverage.COMPLETE, true).copy(metricSource = MetricSource.GPS)
        val factual = "Run saved. 1 mile. Active time, 10 minutes. Average pace, 10 minutes per mile."
        val transitions = listOf(RunTransitionEntity(historyRunId, 1, RunTransitionType.PAUSE,
            historyStart + 600_000L))
        PreRunEnergy.entries.forEach { energy ->
            PostRunEffort.entries.forEach { effort ->
                assertEquals("Receipt must stay factual for $energy and $effort", factual,
                    savedRunReceiptFor(run.copy(preRunEnergy = energy, postRunEffort = effort,
                        musicObservationRequested = true, musicReflectionSharingAllowed = true), transitions))
            }
        }
    }

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
        val spotify = historyItemFor(run, emptyList(), null,
            listOf(second.copy(sourcePackage = "com.spotify.music")))
        assertEquals("Later title — Artist · Spotify", spotify.lastObservedMedia)
        assertEquals("Music observations kept on this phone", item.reflectionMusicSummary)
        val noObservation = historyItemFor(run, emptyList(), null)
        assertEquals(0, noObservation.musicObservationCount)
        assertNull(noObservation.lastObservedMedia)
    }

    @Test
    fun `history music context summary reads frozen evidence rather than latest local report`() {
        val run = sharingHistoryRun()
        val reflection = musicSnapshotReflection("""{"state":"MUSIC","trackTitle":"Original title",
            "artistName":"Original artist","artistDescription":null,"provenance":"MEDIA_SESSION_PARTIAL"}""")
            .copy(status = ReflectionStatus.FAILED)
        val later = RunMusicObservationEntity(
            runId = historyRunId, sequenceNumber = 3, observationKey = "later-local-only",
            observedAtEpochMillis = historyStart + 2_000L, elapsedRealtimeNanos = 2_000_000_000L,
            sourcePackage = "com.example.player", title = "Later title", artist = "Later artist",
            playbackState = MusicPlaybackState.PAUSED, eventKind = MusicObservationKind.METADATA_CHANGED
        )

        val item = historyItemFor(run, emptyList(), reflection, listOf(later))
        val summary = requireNotNull(item.reflectionMusicSummary)
        assertEquals("Later title — Later artist · com.example.player", item.lastObservedMedia)
        assertEquals("One Spotify report included in reflection context · partial", summary)
        assertFalse(summary.contains("Original title"))
        assertFalse(summary.contains("Later title"))
        assertEquals(summary,
            historyItemFor(run, emptyList(), reflection, emptyList()).reflectionMusicSummary)
    }

    @Test
    fun `history frozen unknown context stays unknown despite local metadata`() {
        val run = sharingHistoryRun()
        val reflection = musicSnapshotReflection("""{"state":"UNKNOWN","trackTitle":null,
            "artistName":null,"artistDescription":null,"provenance":null}""")
        val observed = RunMusicObservationEntity(
            runId = historyRunId, sequenceNumber = 1, observationKey = "local-title",
            observedAtEpochMillis = historyStart + 1_000L, elapsedRealtimeNanos = null,
            sourcePackage = "com.spotify.music", title = "Local title", artist = "Local artist",
            playbackState = MusicPlaybackState.PLAYING, eventKind = MusicObservationKind.SESSION_SNAPSHOT
        )

        assertEquals("No Spotify report included in reflection context",
            historyItemFor(run, emptyList(), reflection, listOf(observed)).reflectionMusicSummary)
    }

    @Test
    fun `history missing or malformed music snapshot states only saved permission`() {
        val run = sharingHistoryRun()
        val incompleteSnapshots = listOf(
            "not json", "{}", "{\"musicContext\":null}", "{\"musicContext\":[]}",
            "{\"musicContext\":{\"state\":\"MUSIC\",\"provenance\":\"MEDIA_SESSION_PARTIAL\",\"trackTitle\":\"\",\"artistName\":\"Artist\"}}",
            "{\"musicContext\":{\"state\":\"MUSIC\",\"provenance\":\"MEDIA_SESSION_PARTIAL\",\"trackTitle\":5,\"artistName\":\"Artist\"}}",
            "{\"musicContext\":{\"state\":\"UNKNOWN\",\"trackTitle\":\"Unexpected title\"}}",
            "{\"musicContext\":{\"state\":\"MUSIC\",\"provenance\":\"CONTROLLED\",\"trackTitle\":\"Title\",\"artistName\":\"Artist\"}}",
            "{\"musicContext\":{\"state\":\"UNKNOWN\"}} trailing"
        )
        assertEquals("Music sharing allowed for this run",
            historyItemFor(run, emptyList(), null).reflectionMusicSummary)
        incompleteSnapshots.forEach { snapshot ->
            val reflection = musicSnapshotReflection("{}").copy(evidenceJson = snapshot)
            assertEquals("Music sharing allowed for this run",
                historyItemFor(run, emptyList(), reflection).reflectionMusicSummary)
        }
        assertEquals("Music observations kept on this phone", historyItemFor(
            run.copy(musicReflectionSharingAllowed = false), emptyList(), null
        ).reflectionMusicSummary)
        assertNull(historyItemFor(
            run.copy(musicReflectionSharingAllowed = false, musicObservationRequested = false), emptyList(), null
        ).reflectionMusicSummary)
    }

    @Test
    fun `history preserves saved reflection and shows frozen metrics in run timezone`() {
        val finish = historyStart + 600_000L
        val run = historyRun(
            finish = finish,
            distanceMeters = 1_609.344,
            coverage = TelemetryCoverage.COMPLETE,
            historyComplete = true
        ).copy(preRunEnergy = PreRunEnergy.LOW, postRunEffort = PostRunEffort.HEAVY)
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
        assertEquals(PostRunEffort.HEAVY, item.effort)
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
        assertNull(item.effort)
        assertEquals(false, item.isTestData)
    }

    @Test
    fun `history retains frozen gps coverage without inferring it from distance`() {
        for (coverage in listOf(TelemetryCoverage.COMPLETE, TelemetryCoverage.PARTIAL, null)) {
            val run = historyRun(
                finish = historyStart + 600_000L,
                distanceMeters = if (coverage == null) null else 100.0,
                coverage = coverage,
                historyComplete = true
            ).copy(
                metricSource = if (coverage == null) null else MetricSource.GPS,
                trackingMode = RunTrackingMode.GPS
            )
            val item = historyItemFor(run, listOf(
                RunTransitionEntity(historyRunId, 1, RunTransitionType.PAUSE, historyStart + 600_000L)
            ), null)

            assertEquals(coverage, item.telemetryCoverage)
            assertEquals(true, item.isGpsRun)
            if (coverage == TelemetryCoverage.COMPLETE) {
                assertEquals(6.0, item.paceSecondsPerMeter!!, 0.0)
            } else {
                assertNull(item.paceSecondsPerMeter)
            }
        }
    }

    @Test
    fun `history ranking pace excludes pauses and is independent of displayed unit`() {
        val run = historyRun(historyStart + 600_000L, 1_000.0, TelemetryCoverage.COMPLETE, true)
            .copy(metricSource = MetricSource.GPS, trackingMode = RunTrackingMode.GPS)
        val transitions = listOf(
            RunTransitionEntity(historyRunId, 1, RunTransitionType.PAUSE, historyStart + 300_000L),
            RunTransitionEntity(historyRunId, 2, RunTransitionType.RESUME, historyStart + 420_000L),
            RunTransitionEntity(historyRunId, 3, RunTransitionType.PAUSE, historyStart + 600_000L)
        )
        val miles = historyItemFor(run.copy(displayDistanceUnit = DistanceUnit.MILES), transitions, null)
        val kilometers = historyItemFor(run.copy(displayDistanceUnit = DistanceUnit.KILOMETERS), transitions, null)
        assertEquals(0.48, miles.paceSecondsPerMeter!!, 0.0)
        assertEquals(miles.paceSecondsPerMeter, kilometers.paceSecondsPerMeter)
        assertEquals("8:00 /km", kilometers.metrics?.averagePace)
        assertEquals("12:52 /mi", miles.metrics?.averagePace)
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

    private fun sharingHistoryRun() = historyRun(
        finish = historyStart + 600_000L, historyComplete = false
    ).copy(musicObservationRequested = true, musicReflectionSharingAllowed = true)

    private fun musicSnapshotReflection(musicJson: String) = RunReflectionEntity(
        runId = historyRunId, requestId = historyRequestId,
        evidenceJson = "{\"musicContext\":$musicJson}",
        selectionDeadlineEpochMillis = historyStart + 606_000L
    )

    @Test
    fun `live current mile is formatted separately and unavailable remains a dash`() {
        val gps = RunMetrics(600_000, 600_000, 1_800.0, 0.4, DistanceUnit.MILES,
            MetricSource.GPS, TelemetryCoverage.COMPLETE, currentMilePaceSecondsPerMeter = 480.0 / 1_609.344)
        assertEquals("8:00 /mi", runMetricsDisplayFor(gps).currentMilePace)
        assertEquals("10:44 /mi", runMetricsDisplayFor(gps).averagePace)
        assertEquals("8:00 /mi", runMetricsDisplayFor(gps.copy(displayUnit = DistanceUnit.KILOMETERS))
            .currentMilePace)
        assertEquals("—", runMetricsDisplayFor(gps.copy(currentMilePaceSecondsPerMeter = null))
            .currentMilePace)
    }

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
