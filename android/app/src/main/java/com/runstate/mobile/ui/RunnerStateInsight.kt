package com.runstate.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.runstate.mobile.data.local.RunIntention
import com.runstate.mobile.data.local.TelemetryCoverage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

internal const val RUNNER_STATE_INSIGHT_TAG = "runnerStateInsight"
internal const val RUNNER_ENERGY_EVIDENCE_TAG = "runnerEnergyEvidence"
private const val RECENT_GPS_RUN_LIMIT = 20
private const val MINIMUM_PAIRED_RUNS = 3
private const val CURRENT_ENERGY_VERSION = 2
private const val NUMERIC_EFFORT_VERSION = 1
// A visible product grouping rule, not validated physiological equivalence.
private const val SIMILAR_LENGTH_FRACTION = 0.20

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
    val recent = recentGenuineGpsRuns(items)
    // Earlier answers retain their original meaning. A newly clarified answer on an old run
    // does not retrospectively clarify its starting question.
    val legacy = recent.filter { it.energyQuestionVersion == null && it.postRunEnergyQuestionVersion == null }
    return energyCategoryInsight(legacy)?.copy(recentGpsRunCount = recent.size)
}

private fun recentGenuineGpsRuns(items: List<RunHistoryItem>): List<RunHistoryItem> {
    val identified = items.mapNotNull { item ->
        canonicalRunId(item.runId)?.let { it to item }
    }
    // Ambiguous duplicate rows are all omitted, rather than choosing whichever answer came first.
    val identityCounts = identified.groupingBy { it.first }.eachCount()
    return identified.asSequence().filter { (id, item) ->
        identityCounts[id] == 1 && item.isGpsRun && item.metricSource == MetricSource.GPS &&
            !item.isTestData && item.insightEligible
    }.map { it.second }.take(RECENT_GPS_RUN_LIMIT).toList()
}

private fun energyCategoryInsight(recent: List<RunHistoryItem>): RunnerStateInsight? {
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

/** One inspectable cohort; all rows are saved genuine GPS activities, including missing answers. */
internal data class RunnerEnergyFoundation(
    val recentGpsRunCount: Int,
    val runs: List<RunHistoryItem>,
    val earlierQuestionRunCount: Int,
    val pairedRuns: List<RunHistoryItem>,
    val categoryInsight: RunnerStateInsight?,
    val easyComparison: EasyRunComparison?,
    val earlierInsight: RunnerStateInsight?
) {
    val numericEffortCount: Int get() = runs.count { it.numericEffort() != null }
    val coverageDescription: String get() = if (runs.isEmpty())
        "No runs with the current questions yet. Your earlier answers stay as recorded."
    else "${pairedRuns.size} of ${runs.size} recent runs with the " +
        "current questions have both Energy answers; $numericEffortCount have Effort."
}

/** Similar length/time on the same runner-named route, not a route-match or improvement claim. */
internal data class EasyRunComparison(
    val anchor: RunHistoryItem,
    val runs: List<RunHistoryItem>
) {
    val hasEnoughRuns: Boolean get() = runs.size >= MINIMUM_PAIRED_RUNS
    val pairedCount: Int get() = runs.count { it.hasCurrentEnergyPair() }
    val effortRatings: List<Int> get() = runs.mapNotNull { it.numericEffort() }
    val effortDescription: String get() {
        if (effortRatings.isEmpty()) return "No numeric Effort answers in this group yet."
        val counts = effortRatings.groupingBy { it }.eachCount().toSortedMap()
        return "Reported Effort: " + counts.entries.joinToString("; ") { (rating, count) ->
            "$rating/10 on $count ${if (count == 1) "run" else "runs"}"
        } + ". ${effortRatings.size} of ${runs.size} runs answered."
    }
}

internal fun runnerEnergyFoundationFor(items: List<RunHistoryItem>): RunnerEnergyFoundation {
    val recent = recentGenuineGpsRuns(items)
    val current = recent.filter { it.energyQuestionVersion == CURRENT_ENERGY_VERSION }
    val paired = current.filter { it.hasCurrentEnergyPair() }
    // Missing and differently worded post answers remain in coverage, but not in counts.
    val categoryRows = current.map { row ->
        if (row.postRunEnergyQuestionVersion == CURRENT_ENERGY_VERSION) row else row.copy(energy = null)
    }
    return RunnerEnergyFoundation(recent.size, current,
        recent.count { it.energyQuestionVersion == null }, paired,
        energyCategoryInsight(categoryRows), easyRunComparisonFor(current), runnerStateInsightFor(items))
}

internal fun easyRunComparisonFor(currentRuns: List<RunHistoryItem>): EasyRunComparison? {
    fun validMetrics(row: RunHistoryItem): Boolean =
        row.telemetryCoverage == TelemetryCoverage.COMPLETE &&
            row.distanceMeters?.let { it.isFinite() && it > 0.0 } == true &&
            row.activeDurationMillis?.let { it > 0L } == true
    val eligible = recentGenuineGpsRuns(currentRuns).filter { row ->
        row.energyQuestionVersion == CURRENT_ENERGY_VERSION && row.runIntention == RunIntention.KEEP_EASY &&
            normalizedRouteLabel(row.routeLabel) != null && validMetrics(row)
    }
    val anchor = eligible.firstOrNull() ?: return null
    val distance = requireNotNull(anchor.distanceMeters)
    val duration = requireNotNull(anchor.activeDurationMillis).toDouble()
    val route = normalizedRouteLabel(anchor.routeLabel)
    val matches = eligible.filter { row ->
        normalizedRouteLabel(row.routeLabel) == route &&
            abs(requireNotNull(row.distanceMeters) / distance - 1.0) <= SIMILAR_LENGTH_FRACTION + 1e-9 &&
            abs(requireNotNull(row.activeDurationMillis).toDouble() / duration - 1.0) <= SIMILAR_LENGTH_FRACTION + 1e-9
    }
    return EasyRunComparison(anchor, matches)
}

private fun normalizedRouteLabel(label: String?): String? = label?.trim()?.takeIf { it.isNotEmpty() }
    ?.replace(Regex("\\s+"), " ")?.lowercase(Locale.ROOT)

private fun RunHistoryItem.hasCurrentEnergyPair(): Boolean = energyQuestionVersion == CURRENT_ENERGY_VERSION &&
    postRunEnergyQuestionVersion == CURRENT_ENERGY_VERSION && preRunEnergy != null && energy.isReportedEnergy()

private fun RunHistoryItem.numericEffort(): Int? = postRunEffortRating?.takeIf {
    postRunEffortQuestionVersion == NUMERIC_EFFORT_VERSION && it in 1..10
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

private fun ReflectionBranch?.answerLabel(): String = when (this) {
    ReflectionBranch.SPENT -> "Spent"
    ReflectionBranch.FEELING_GOOD -> "Feeling Good"
    ReflectionBranch.POWERED_UP -> "Powered Up"
    ReflectionBranch.NO_SELECTION -> "Not sure"
    null -> "Not recorded"
}

internal fun historyEffortLabel(item: RunHistoryItem): String = when {
    item.postRunEffortQuestionVersion == NUMERIC_EFFORT_VERSION ->
        item.numericEffort()?.let { "$it / 10" } ?: "Not recorded"
    item.effort != null -> item.effort.displayLabel()
    else -> "Not recorded"
}

private fun RunIntention?.insightLabel(): String = when (this) {
    RunIntention.KEEP_EASY -> "Keep it easy"
    RunIntention.STEADY -> "Steady"
    RunIntention.HARD_EFFORT -> "Hard effort"
    null -> "Not recorded"
}

private fun dateSpan(runs: List<RunHistoryItem>): String {
    val dated = runs.filter { it.officialStartEpochMillis != null }.sortedBy { it.officialStartEpochMillis }
    if (dated.isEmpty()) return "Saved dates shown with each run"
    fun date(row: RunHistoryItem): String = try {
        DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US).format(
            Instant.ofEpochMilli(requireNotNull(row.officialStartEpochMillis))
                .atZone(ZoneId.of(row.startTimezoneId ?: "UTC")))
    } catch (_: Exception) { row.dateLabel }
    val first = date(dated.first())
    val last = date(dated.last())
    return if (first == last) first else "$first – $last"
}

/** Answer provenance does not infer an original time for migrated feedback. */
internal fun energyAnswerTimingDescription(item: RunHistoryItem): String {
    val first = item.postRunEnergyRecordedAtEpochMillis
    val updated = item.postRunEnergyUpdatedAtEpochMillis
    val edited = updated != null && (first == null || updated > first)
    val firstLabel = when {
        first == null -> "Original answer time unknown"
        item.finishEpochMillis == null || first < item.finishEpochMillis -> "Answer time recorded"
        first - item.finishEpochMillis < 60_000L -> "Answered within a minute of finishing"
        else -> "Answered ${(first - item.finishEpochMillis) / 60_000L} min after finishing"
    }
    return if (edited) "$firstLabel · edited later" else firstLabel
}

@Composable
internal fun RunnerEnergyFoundationPanel(
    foundation: RunnerEnergyFoundation,
    onInspect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(18.dp)
    Column(modifier.fillMaxWidth().testTag(RUNNER_STATE_INSIGHT_TAG)
        .background(Color(0xFFF4F5ED).copy(alpha = 0.97f), shape)
        .border(1.dp, Color(0xFFD5DED1), shape).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("YOUR ENERGY", color = Color(0xFF526348), fontSize = 10.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
        Text(foundation.categoryInsight?.heading ?: "Your check-ins are taking shape",
            color = Color(0xFF263029), fontSize = 20.sp, lineHeight = 25.sp,
            fontWeight = FontWeight.Medium, modifier = Modifier.semantics { heading() })
        foundation.categoryInsight?.let { insight ->
            Text(insight.description, color = Color(0xFF263029), fontSize = 14.sp, lineHeight = 21.sp)
        }
        Text(foundation.coverageDescription, color = Color(0xFF526348), fontSize = 12.sp, lineHeight = 18.sp)
        Text(if (foundation.categoryInsight == null) "Collecting check-ins" else "Early observations",
            color = Color(0xFF526348), fontSize = 12.sp, lineHeight = 18.sp)
        if (foundation.runs.isEmpty()) foundation.earlierInsight?.let { earlier ->
            Text("Earlier check-ins · original questions", color = Color(0xFF526348), fontSize = 11.sp,
                modifier = Modifier.padding(top = 8.dp))
            Text(earlier.heading, color = Color(0xFF263029), fontSize = 16.sp,
                fontWeight = FontWeight.Medium)
            Text(earlier.description, color = Color(0xFF263029), fontSize = 13.sp, lineHeight = 19.sp)
            Text(earlier.coverageDescription, color = Color(0xFF526348), fontSize = 12.sp, lineHeight = 18.sp)
        }
        TextButton(onClick = onInspect, modifier = Modifier.heightIn(min = 48.dp)
            .testTag("runnerEnergyInspect")) { Text("View check-ins", color = Color(0xFF263029)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RunnerEnergyEvidenceSheet(foundation: RunnerEnergyFoundation, onDismiss: () -> Unit) {
    var comparing by rememberSaveable { mutableStateOf(false) }
    val comparison = foundation.easyComparison
    val rows = if (comparing) comparison?.runs.orEmpty() else foundation.runs
    ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFFF4F5ED), modifier = Modifier.testTag(RUNNER_ENERGY_EVIDENCE_TAG)) {
        LazyColumn(modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Text("Your Energy", color = Color(0xFF263029), fontSize = 25.sp,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { comparing = false }, modifier = Modifier.heightIn(min = 48.dp)
                        .testTag("runnerEnergyAllCheckIns")) {
                        Text("Check-ins", fontWeight = if (!comparing) FontWeight.Bold else FontWeight.Normal)
                    }
                    TextButton(onClick = { comparing = true }, modifier = Modifier.heightIn(min = 48.dp)
                        .testTag("runnerEnergyEasyRuns")) {
                        Text("Similar easy runs", fontWeight = if (comparing) FontWeight.Bold else FontWeight.Normal)
                    }
                }
                if (!comparing) {
                    Text(foundation.coverageDescription, fontSize = 14.sp, lineHeight = 21.sp)
                    Text("From your latest ${foundation.recentGpsRunCount} GPS runs.",
                        fontSize = 12.sp, lineHeight = 18.sp)
                    if (foundation.earlierQuestionRunCount > 0) {
                        Text("${foundation.earlierQuestionRunCount} earlier ${if (foundation.earlierQuestionRunCount == 1) "run is" else "runs are"} " +
                            "kept in History. Those check-ins used different questions.", fontSize = 12.sp, lineHeight = 18.sp)
                    }
                    if (foundation.runs.isEmpty()) {
                        Text("Your next saved check-in will appear here.",
                            modifier = Modifier.padding(top = 10.dp), fontSize = 14.sp, lineHeight = 21.sp)
                    }
                } else if (comparison == null) {
                    Text("A comparison begins with a saved Keep it easy run, a route name and complete GPS distance and active time.",
                        fontSize = 14.sp, lineHeight = 21.sp)
                } else {
                    Text("Keep it easy · ${comparison.anchor.routeLabel}", fontSize = 18.sp,
                        fontWeight = FontWeight.Medium, lineHeight = 23.sp)
                    Text("Same route name; distance and active time each within 20% of your newest matching run " +
                        "(${comparison.anchor.dateLabel}). Route names are yours, not verified GPS matches.",
                        fontSize = 12.sp, lineHeight = 18.sp)
                    Text(if (comparison.hasEnoughRuns) "${comparison.runs.size} similar-length runs · early observations"
                        else "${comparison.runs.size} of 3 comparable runs",
                        fontSize = 14.sp, lineHeight = 21.sp)
                    if (comparison.hasEnoughRuns) {
                        Text(comparison.effortDescription, fontSize = 14.sp, lineHeight = 21.sp)
                        Text("${comparison.pairedCount} of ${comparison.runs.size} have both Energy answers.",
                            fontSize = 12.sp, lineHeight = 18.sp)
                    }
                }
                if (rows.isNotEmpty()) Text(dateSpan(rows), modifier = Modifier.padding(top = 10.dp),
                    fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
            items(rows, key = { it.runId }) { row -> EnergyEvidenceRow(row) }
            item {
                Text("From your saved check-ins", fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 28.dp))
            }
        }
    }
}

@Composable
private fun EnergyEvidenceRow(row: RunHistoryItem) {
    Column(Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.84f), RoundedCornerShape(14.dp))
        .padding(14.dp).testTag("runnerEnergyEvidenceRun:" + row.runId),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(row.dateLabel, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        row.metrics?.let { Text("${it.distance} · ${it.active} active · ${it.averagePace}", fontSize = 12.sp) }
        Text("Start · ${row.preRunEnergy?.displayLabel() ?: "Not recorded"}    Finish · ${row.energy.answerLabel()}",
            fontSize = 13.sp, lineHeight = 19.sp)
        Text("Effort · ${historyEffortLabel(row)}", fontSize = 13.sp)
        if (row.energy != null) {
            Text(energyAnswerTimingDescription(row), fontSize = 11.sp, lineHeight = 16.sp)
            if (row.firstPostRunEnergy != null && row.firstPostRunEnergy != row.energy) {
                Text("Originally reported · ${row.firstPostRunEnergy.answerLabel()}", fontSize = 11.sp)
            }
            if (row.postRunEnergyQuestionVersion != CURRENT_ENERGY_VERSION) {
                Text("Finish answer uses earlier wording · excluded from current Energy counts", fontSize = 11.sp, lineHeight = 16.sp)
            }
        }
        val effortFirst = row.postRunEffortRecordedAtEpochMillis
        val effortUpdated = row.postRunEffortUpdatedAtEpochMillis
        if (effortFirst != null && row.finishEpochMillis != null && effortFirst >= row.finishEpochMillis) {
            val minutes = (effortFirst - row.finishEpochMillis) / 60_000L
            Text(if (minutes == 0L) "Effort answered within a minute of finishing"
                else "Effort answered $minutes min after finishing", fontSize = 11.sp, lineHeight = 16.sp)
        }
        if (effortUpdated != null && (effortFirst == null || effortUpdated > effortFirst)) {
            Text("Effort edited later" + (row.firstPostRunEffortRating?.let { " · originally $it / 10" } ?: ""),
                fontSize = 11.sp, lineHeight = 16.sp)
        }
        Text("Intention · ${row.runIntention.insightLabel()}\nRoute · ${row.routeLabel ?: "Not recorded"}\n" +
            "Shoes · ${row.shoeLabel ?: "Not recorded"}", fontSize = 12.sp, lineHeight = 18.sp)
        Text(row.weatherSummary ?: "Weather · Not recorded", fontSize = 12.sp, lineHeight = 18.sp)
    }
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
        Text("Earlier check-ins · original questions", color = Color(0xFF526348), fontSize = 11.sp)
        Text(insight.heading, color = Color(0xFF263029), fontSize = 20.sp,
            lineHeight = 25.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.semantics { heading() })
        Text(insight.description, color = Color(0xFF263029), fontSize = 14.sp,
            lineHeight = 21.sp)
        Text(insight.coverageDescription, color = Color(0xFF526348), fontSize = 12.sp,
            lineHeight = 18.sp)
    }
}
