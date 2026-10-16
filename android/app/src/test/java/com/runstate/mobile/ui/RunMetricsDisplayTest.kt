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
import com.runstate.mobile.data.local.RunMusicObservationEntity
import com.runstate.mobile.data.local.RunReflectionEntity
import com.runstate.mobile.data.local.RunTransitionEntity
import com.runstate.mobile.data.local.RunTransitionType
import com.runstate.mobile.data.local.StoredRunState
import com.runstate.mobile.data.local.TelemetryCoverage
import com.runstate.mobile.run.RunMetrics
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** Checks metric text without Compose, a database or an Android device. */
class RunMetricsDisplayTest {

    private val historyRunId = "123e4567-e89b-12d3-a456-426614174000"
    private val historyRequestId = "123e4567-e89b-12d3-a456-426614174001"
    private val historyStart = Instant.parse("2026-10-12T01:30:00Z").toEpochMilli()

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
            TelemetryCoverage.COMPLETE, true)
        assertEquals("Run saved. Fixture distance, 1 mile. Elapsed time, 1 hour, 1 minute, 1 second.",
            savedRunReceiptFor(run))
    }

    @Test
    fun `save receipt identifies partial gps and never invents unavailable distance`() {
        val gps = historyRun(historyStart + 60_000L, 2_000.0,
            TelemetryCoverage.PARTIAL, true).copy(metricSource = MetricSource.GPS,
            displayDistanceUnit = DistanceUnit.KILOMETERS)
        assertEquals("Run saved. Partial GPS distance, 2 kilometers. Elapsed time, 1 minute.",
            savedRunReceiptFor(gps))
        assertEquals("Run saved. Elapsed time, 1 minute.", savedRunReceiptFor(
            historyRun(historyStart + 60_000L, historyComplete = false)))
        assertEquals("Run saved.", savedRunReceiptFor(historyRun(null, historyComplete = false)))
    }

    @Test
    fun `tiny measured receipt does not round movement to zero`() {
        val run = historyRun(historyStart + 999L, 1.0, TelemetryCoverage.PARTIAL, true)
        assertEquals("Run saved. Fixture distance, less than 0.01 miles. Elapsed time, 0 seconds.",
            savedRunReceiptFor(run))
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
