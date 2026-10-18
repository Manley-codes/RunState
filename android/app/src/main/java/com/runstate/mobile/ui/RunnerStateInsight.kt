package com.runstate.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runstate.mobile.data.local.MetricSource
import com.runstate.mobile.data.local.PreRunEnergy
import com.runstate.mobile.data.local.ReflectionBranch
import java.util.UUID

internal const val RUNNER_STATE_INSIGHT_TAG = "runnerStateInsight"
private const val RECENT_GPS_RUN_LIMIT = 20
private const val MINIMUM_PAIRED_RUNS = 3

/** A local description of reported check-ins, not a comparison between two Energy scales. */
internal data class RunnerStateInsight(
    val startingEnergy: PreRunEnergy,
    val spentRuns: Int,
    val feelingGoodRuns: Int,
    val poweredUpRuns: Int,
    val recentGpsRunCount: Int,
    val pairedGpsRunCount: Int
) {
    val matchingRunCount: Int get() = spentRuns + feelingGoodRuns + poweredUpRuns

    val heading: String get() = "After starting ${startingEnergy.displayLabel()}"

    val description: String get() {
        val counts = listOf("Spent" to spentRuns, "Feeling Good" to feelingGoodRuns,
            "Powered Up" to poweredUpRuns).filter { it.second > 0 }.sortedByDescending { it.second }
        val majority = counts.firstOrNull()?.takeIf { it.second * 2 > matchingRunCount }
        return if (majority != null) {
            "You finished ${majority.first} in ${majority.second} of $matchingRunCount runs " +
                "you logged with ${startingEnergy.displayLabel()} starting Energy."
        } else {
            // A tie or plurality must not be presented as a usual or expected finish.
            "Your finishes varied: " + counts.joinToString("; ") { (label, count) ->
                "$label in $count of $matchingRunCount runs"
            } + "."
        }
    }

    val coverageDescription: String get() =
        "Based on your Energy check-ins. $pairedGpsRunCount of your latest $recentGpsRunCount " +
            "GPS runs have both answers."
}

/**
 * History supplies completed runs in newest-first order. Missing answers still occupy the
 * recent-run window; otherwise a few old answers could masquerade as a current observation.
 * Recompute from saved answers on every change, with no insight persistence or provider call.
 */
internal fun runnerStateInsightFor(items: List<RunHistoryItem>): RunnerStateInsight? {
    val identified = items.mapNotNull { item ->
        canonicalRunId(item.runId)?.let { it to item }
    }
    // Ambiguous duplicate rows are all omitted, rather than choosing whichever answer came first.
    val identityCounts = identified.groupingBy { it.first }.eachCount()
    val recent = identified.asSequence().filter { (id, item) ->
        identityCounts[id] == 1 && item.isGpsRun && item.metricSource == MetricSource.GPS &&
            !item.isTestData && item.insightEligible
    }.map { it.second }.take(RECENT_GPS_RUN_LIMIT).toList()
    val paired = recent.filter { it.preRunEnergy != null && it.energy.isReportedEnergy() }
    // A newer starting-Energy group gets the one contextual slot when it has enough evidence.
    val group = paired.groupBy { it.preRunEnergy }.entries.firstOrNull {
        it.value.size >= MINIMUM_PAIRED_RUNS
    } ?: return null
    return RunnerStateInsight(
        startingEnergy = requireNotNull(group.key),
        spentRuns = group.value.count { it.energy == ReflectionBranch.SPENT },
        feelingGoodRuns = group.value.count { it.energy == ReflectionBranch.FEELING_GOOD },
        poweredUpRuns = group.value.count { it.energy == ReflectionBranch.POWERED_UP },
        recentGpsRunCount = recent.size,
        pairedGpsRunCount = paired.size
    )
}

private fun canonicalRunId(raw: String): String? = try {
    UUID.fromString(raw).toString().takeIf { it.equals(raw, ignoreCase = true) }
} catch (_: IllegalArgumentException) {
    null
}

private fun ReflectionBranch?.isReportedEnergy(): Boolean = when (this) {
    ReflectionBranch.SPENT, ReflectionBranch.FEELING_GOOD, ReflectionBranch.POWERED_UP -> true
    ReflectionBranch.NO_SELECTION, null -> false
}

private fun PreRunEnergy.displayLabel(): String = when (this) {
    PreRunEnergy.LOW -> "Low"
    PreRunEnergy.MODERATE -> "Moderate"
    PreRunEnergy.HIGH -> "High"
}

@Composable
internal fun RunnerStateInsightPanel(insight: RunnerStateInsight, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier.fillMaxWidth().testTag(RUNNER_STATE_INSIGHT_TAG)
            .background(Color(0xFFF4F5ED).copy(alpha = 0.97f), shape)
            .border(1.dp, Color(0xFFD5DED1), shape)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("YOUR ENERGY", color = Color(0xFF526348), fontSize = 10.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
        Text(insight.heading, color = Color(0xFF263029), fontSize = 20.sp,
            lineHeight = 25.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.semantics { heading() })
        Text(insight.description, color = Color(0xFF263029), fontSize = 14.sp,
            lineHeight = 21.sp)
        Text(insight.coverageDescription, color = Color(0xFF526348), fontSize = 12.sp,
            lineHeight = 18.sp)
    }
}
