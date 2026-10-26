package com.runstate.mobile.ui

import com.runstate.mobile.data.local.MetricSource
import com.runstate.mobile.data.local.TelemetryCoverage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RunHistorySelectionTest {
    @Test
    fun `all runs excludes known samples but preserves unknown legacy records`() {
        val real = run("real", start = 200L)
        val unknown = run("legacy", start = 100L).copy(
            metricSource = null, isGpsRun = false, metrics = null,
            distanceMeters = null, paceSecondsPerMeter = null, telemetryCoverage = null
        )
        val typedSample = run("sample").copy(metricSource = MetricSource.FIXTURE)
        val legacySample = run("old-sample").copy(metricSource = null, isGpsRun = false,
            metrics = display(source = "Fixture metrics"))

        assertEquals(listOf("real", "legacy"), ids(historyItemsFor(
            listOf(legacySample, unknown, typedSample, real), HistoryFilter.ALL
        )))
    }

    @Test
    fun `chronology uses raw start with missing dates last and deterministic identity ties`() {
        val items = listOf(run("z", start = null), run("b", start = 200L),
            run("old", start = 100L), run("a", start = 200L), run("a-null", start = null))
        assertEquals(listOf("a", "b", "old", "a-null", "z"),
            ids(historyItemsFor(items, HistoryFilter.ALL)))
    }

    @Test
    fun `longest ranks raw meters across different display units and rounded values`() {
        val longer = run("longer", start = 100L, distance = 1_609.344)
            .copy(metrics = display(distance = "1.00 mi"))
        val shorter = run("shorter", start = 200L, distance = 1_500.0)
            .copy(metrics = display(distance = "1.50 km"))
        val fractionLonger = run("fraction-longer", start = 100L, distance = 2_000.4)
            .copy(metrics = display(distance = "2.00 km"))
        val fractionShorter = run("fraction-shorter", start = 200L, distance = 2_000.1)
            .copy(metrics = display(distance = "2.00 km"))

        assertEquals(listOf("fraction-longer", "fraction-shorter", "longer", "shorter"),
            ids(historyItemsFor(listOf(shorter, fractionShorter, longer, fractionLonger),
                HistoryFilter.LONGEST)))
    }

    @Test
    fun `fastest ranks raw seconds per meter rather than mixed unit or rounded labels`() {
        val faster = run("faster", start = 100L, pace = .30)
            .copy(metrics = display(pace = "8:03 /mi"))
        val slower = run("slower", start = 200L, pace = .40)
            .copy(metrics = display(pace = "6:40 /km"))
        val fractionFaster = run("fraction-faster", start = 100L, pace = .2998)
            .copy(metrics = display(pace = "5:00 /km"))
        val fractionSlower = run("fraction-slower", start = 200L, pace = .2999)
            .copy(metrics = display(pace = "5:00 /km"))

        assertEquals(listOf("fraction-faster", "fraction-slower", "faster", "slower"),
            ids(historyItemsFor(listOf(slower, fractionSlower, faster, fractionFaster),
                HistoryFilter.FASTEST)))
    }

    @Test
    fun `partial GPS is ordered by recorded distance but cannot compete on pace`() {
        val partial = run("partial", distance = 4_000.0, pace = .10)
            .copy(telemetryCoverage = TelemetryCoverage.PARTIAL,
                metrics = display(source = "GPS metrics · partial", pace = "—"))
        val complete = run("complete", distance = 2_000.0, pace = .35)

        val longest = historyItemsFor(listOf(complete, partial), HistoryFilter.LONGEST)
        assertEquals(listOf("partial", "complete"), ids(longest))
        assertEquals(partial, longest.first())
        assertEquals(listOf("complete"), ids(historyItemsFor(listOf(partial, complete),
            HistoryFilter.FASTEST)))
    }

    @Test
    fun `missing zero negative and nonfinite distance never enter rankings`() {
        val invalid = listOf<Double?>(null, 0.0, -1.0, Double.NaN,
            Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).mapIndexed { index, distance ->
            run("invalid-$index", distance = distance)
        }
        for (filter in listOf(HistoryFilter.LONGEST, HistoryFilter.FASTEST)) {
            assertEquals(listOf("valid"), ids(historyItemsFor(invalid + run("valid"), filter)))
        }
        assertEquals(invalid.size + 1, historyItemsFor(invalid + run("valid"), HistoryFilter.ALL).size)
    }

    @Test
    fun `missing invalid or unsupported pace never enters fastest`() {
        val invalidPaces = listOf<Double?>(null, 0.0, -1.0, Double.NaN,
            Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).mapIndexed { index, pace ->
            run("invalid-$index", pace = pace)
        }
        val unsupported = listOf(run("partial").copy(telemetryCoverage = TelemetryCoverage.PARTIAL),
            run("unavailable").copy(telemetryCoverage = TelemetryCoverage.UNAVAILABLE),
            run("unknown-coverage").copy(telemetryCoverage = null),
            run("unknown-source").copy(metricSource = null))

        assertEquals(listOf("valid"), ids(historyItemsFor(
            invalidPaces + unsupported + run("valid"), HistoryFilter.FASTEST)))
    }

    @Test
    fun `unknown provenance cannot become GPS ranking evidence from display strings or flags`() {
        val unknown = run("unknown", distance = 90_000.0, pace = .01)
            .copy(metricSource = null)
        assertEquals(listOf("real"), ids(historyItemsFor(listOf(unknown, run("real")),
            HistoryFilter.LONGEST)))
        assertEquals(listOf("real"), ids(historyItemsFor(listOf(unknown, run("real")),
            HistoryFilter.FASTEST)))
    }

    @Test
    fun `real GPS with a test reflection keeps its physical ranking`() {
        val real = run("real-with-fake-voice").copy(isTestData = true)
        for (filter in listOf(HistoryFilter.ALL, HistoryFilter.LONGEST, HistoryFilter.FASTEST)) {
            assertEquals(listOf(real), historyItemsFor(listOf(real), filter))
        }
    }

    @Test
    fun `samples cannot outrank real GPS even with recording sources enabled`() {
        val typedSample = run("sample", distance = 90_000.0, pace = .01)
            .copy(metricSource = MetricSource.FIXTURE, isGpsRun = false)
        val legacySample = run("legacy-sample", distance = 80_000.0, pace = .02)
            .copy(metrics = display(source = "Fixture metrics"))
        val items = listOf(typedSample, legacySample, run("real"))
        for (showSources in listOf(false, true)) {
            for (filter in listOf(HistoryFilter.LONGEST, HistoryFilter.FASTEST)) {
                assertEquals(listOf("real"), ids(historyItemsFor(items, filter, showSources)))
            }
        }
    }

    @Test
    fun `equal ranks use chronological then identity order regardless of feedback`() {
        val items = listOf(run("b", start = 200L), run("old", start = 100L),
            run("a", start = 200L), run("undated", start = null))
        for (filter in listOf(HistoryFilter.LONGEST, HistoryFilter.FASTEST)) {
            assertEquals(listOf("a", "b", "old", "undated"), ids(historyItemsFor(items, filter)))
            assertEquals(listOf("a", "b", "old", "undated"), ids(historyItemsFor(
                items.reversed().map { it.copy(postRunNote = "Edited later", energyLabel = "Spent") }, filter)))
        }
    }

    @Test
    fun `secondary source views retain sample access and old GPS mode behavior`() {
        val real = run("real", start = 200L)
        val noMetricsGps = run("gps-unknown", start = 100L)
            .copy(metricSource = null, metrics = null, distanceMeters = null, paceSecondsPerMeter = null)
        val sample = run("sample", start = 300L)
            .copy(metricSource = MetricSource.FIXTURE, isGpsRun = false)
        val legacySample = run("legacy-sample", start = 250L)
            .copy(metricSource = null, isGpsRun = false, metrics = display(source = "Fixture metrics"))
        val unknown = run("legacy", start = null).copy(metricSource = null, isGpsRun = false, metrics = null)
        val items = listOf(unknown, noMetricsGps, legacySample, real, sample)

        assertEquals(listOf("sample", "legacy-sample", "real", "gps-unknown", "legacy"),
            ids(historyItemsFor(items, HistoryFilter.ALL, showRecordingSources = true)))
        assertEquals(listOf("real", "gps-unknown"),
            ids(historyItemsFor(items, HistoryFilter.GPS, showRecordingSources = true)))
        assertEquals(listOf("sample", "legacy-sample"),
            ids(historyItemsFor(items, HistoryFilter.SAMPLES, showRecordingSources = true)))
        assertTrue(historyItemsFor(items, HistoryFilter.SAMPLES).isEmpty())
    }

    private fun ids(items: List<RunHistoryItem>) = items.map { it.runId }

    private fun run(
        id: String,
        start: Long? = 100L,
        distance: Double? = 1_000.0,
        pace: Double? = .35
    ) = RunHistoryItem(
        runId = id, dateLabel = "Display date", metrics = display(), reflectionStatus = null,
        reflectionText = null, energyLabel = null, isTestData = false, isGpsRun = true,
        metricSource = MetricSource.GPS, telemetryCoverage = TelemetryCoverage.COMPLETE,
        officialStartEpochMillis = start, distanceMeters = distance, paceSecondsPerMeter = pace
    )

    private fun display(
        distance: String = "1.00 km",
        pace: String = "5:50 /km",
        source: String = "GPS metrics"
    ) = RunMetricsDisplay(elapsed = "6:00", active = "5:50", distance = distance,
        averagePace = pace, sourceLabel = source)
}
