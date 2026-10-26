package com.runstate.mobile.ui

import com.runstate.mobile.data.local.MetricSource
import com.runstate.mobile.data.local.TelemetryCoverage

internal enum class HistoryFilter(val label: String) {
    ALL("All runs"),
    LONGEST("Longest"),
    FASTEST("Fastest pace"),
    GPS("GPS"),
    SAMPLES("Samples")
}

private val historyChronologicalOrder =
    compareByDescending<RunHistoryItem> { it.officialStartEpochMillis }
        .thenBy { it.runId }

/**
 * Selects saved records without modifying their facts or using rounded display text to rank them.
 * A test reflection is independent of whether the underlying run was recorded with real GPS.
 */
internal fun historyItemsFor(
    items: List<RunHistoryItem>,
    filter: HistoryFilter,
    showRecordingSources: Boolean = false
): List<RunHistoryItem> {
    val available = if (showRecordingSources) items else items.filterNot(::isKnownFixture)
    return when (filter) {
        HistoryFilter.ALL -> available.sortedWith(historyChronologicalOrder)
        HistoryFilter.GPS -> available.filter { it.isGpsRun }.sortedWith(historyChronologicalOrder)
        HistoryFilter.SAMPLES -> available.filter(::isKnownFixture).sortedWith(historyChronologicalOrder)
        HistoryFilter.LONGEST -> available.filter {
            it.metricSource == MetricSource.GPS && !isKnownFixture(it) &&
                it.distanceMeters.isPositiveFinite()
        }.sortedWith(compareByDescending<RunHistoryItem> { it.distanceMeters }
            .then(historyChronologicalOrder))
        HistoryFilter.FASTEST -> available.filter {
            it.metricSource == MetricSource.GPS && !isKnownFixture(it) &&
                it.telemetryCoverage == TelemetryCoverage.COMPLETE &&
                it.distanceMeters.isPositiveFinite() && it.paceSecondsPerMeter.isPositiveFinite()
        }.sortedWith(compareBy<RunHistoryItem> { it.paceSecondsPerMeter }
            .then(historyChronologicalOrder))
    }
}

private fun isKnownFixture(item: RunHistoryItem): Boolean =
    item.metricSource == MetricSource.FIXTURE || item.metrics?.sourceLabel == "Fixture metrics"

private fun Double?.isPositiveFinite(): Boolean = this != null && isFinite() && this > 0.0
