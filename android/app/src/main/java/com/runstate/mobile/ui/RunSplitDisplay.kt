package com.runstate.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runstate.mobile.data.local.DistanceUnit
import com.runstate.mobile.data.local.RunSplitEntity
import com.runstate.mobile.data.local.RunSplitSnapshotEntity
import com.runstate.mobile.data.local.RunSplitStatus
import com.runstate.mobile.run.RunSplitCalculator
import java.util.Locale
import kotlin.math.roundToLong

internal sealed interface HistorySplitDisplay {
    data object Loading : HistorySplitDisplay
    data object NotRecorded : HistorySplitDisplay
    data object Pending : HistorySplitDisplay
    data object Unavailable : HistorySplitDisplay
    data object Failed : HistorySplitDisplay
    data class Ready(val rows: List<RunSplitDisplayRow>, val unitLabel: String) : HistorySplitDisplay
}

internal data class RunSplitDisplayRow(val label: String, val time: String, val pace: String)

/** The optional table cannot stop History from displaying the completed run. */
internal fun historySplitsFor(
    snapshot: RunSplitSnapshotEntity?,
    rows: List<RunSplitEntity>
): HistorySplitDisplay {
    snapshot ?: return HistorySplitDisplay.NotRecorded
    return when (snapshot.status) {
        RunSplitStatus.PENDING -> HistorySplitDisplay.Pending
        RunSplitStatus.UNAVAILABLE -> HistorySplitDisplay.Unavailable
        RunSplitStatus.READY -> {
            if (snapshot.derivationVersion != RunSplitCalculator.DERIVATION_VERSION || rows.isEmpty()) {
                return HistorySplitDisplay.Failed
            }
            val ordered = rows.sortedBy { it.splitNumber }
            var priorDistance = 0.0
            var priorActive = 0L
            if (ordered.withIndex().any { (index, row) ->
                    val bad = row.runId != snapshot.runId || row.splitNumber != index + 1 ||
                        !row.startDistanceMeters.isFinite() || !row.endDistanceMeters.isFinite() ||
                        kotlin.math.abs(row.startDistanceMeters - priorDistance) > 0.000001 ||
                        row.endDistanceMeters <= row.startDistanceMeters ||
                        row.startActiveMillis != priorActive || row.endActiveMillis <= priorActive ||
                        !row.paceSecondsPerMeter.isFinite() || row.paceSecondsPerMeter <= 0.0 ||
                        (row.isPartial && index != ordered.lastIndex)
                    priorDistance = row.endDistanceMeters
                    priorActive = row.endActiveMillis
                    bad
                }) return HistorySplitDisplay.Failed
            val meters = if (snapshot.displayUnit == DistanceUnit.MILES) 1_609.344 else 1_000.0
            val unit = if (snapshot.displayUnit == DistanceUnit.MILES) "mi" else "km"
            HistorySplitDisplay.Ready(ordered.map { row ->
                val distance = (row.endDistanceMeters - row.startDistanceMeters) / meters
                val fraction = if (distance < 0.01) "<0.01" else String.format(Locale.US, "%.2f", distance)
                val label = if (row.isPartial) "Final $fraction $unit" else "${row.splitNumber} $unit"
                val pace = row.paceSecondsPerMeter * meters
                RunSplitDisplayRow(label, splitTime(row.endActiveMillis - row.startActiveMillis),
                    if (pace.isFinite() && pace < Long.MAX_VALUE / 1_000.0) {
                        splitTime((pace * 1_000.0).roundToLong()) + "/$unit"
                    } else "—")
            }, unit)
        }
    }
}

private fun splitTime(millis: Long): String {
    if (millis < 1_000L) return "<0:01"
    val seconds = (millis / 1_000.0).roundToLong()
    val hours = seconds / 3_600L
    return if (hours > 0) String.format(Locale.US, "%d:%02d:%02d", hours, seconds / 60L % 60L, seconds % 60L)
    else String.format(Locale.US, "%d:%02d", seconds / 60L, seconds % 60L)
}

@Composable
internal fun HistorySplitsPanel(
    display: HistorySplitDisplay?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ink = Color(0xFFF4F5ED)
    val muted = Color(0xFFCAD0C5)
    Column(modifier.fillMaxWidth().testTag("historySplits").padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("SPLITS", color = Color(0xFFD6BC72), fontSize = 10.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp,
            modifier = Modifier.semantics { heading() })
        when (display) {
            is HistorySplitDisplay.Ready -> {
                Row(Modifier.fillMaxWidth()) {
                    Text("Distance", Modifier.weight(1.2f), color = muted, fontSize = 11.sp)
                    Text("Time", Modifier.weight(0.8f), color = muted, fontSize = 11.sp)
                    Text("Pace", Modifier.weight(1f), color = muted, fontSize = 11.sp)
                }
                display.rows.forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(row.label, Modifier.weight(1.2f), color = ink, fontSize = 13.sp)
                        Text(row.time, Modifier.weight(0.8f), color = ink, fontSize = 13.sp)
                        Text(row.pace, Modifier.weight(1f), color = ink, fontSize = 13.sp)
                    }
                }
                Text("Estimated from recorded GPS. Times exclude pauses.", color = muted,
                    fontSize = 11.sp, lineHeight = 17.sp)
            }
            HistorySplitDisplay.NotRecorded -> Text("Splits weren’t recorded for this run.",
                color = muted, fontSize = 12.sp, lineHeight = 18.sp)
            HistorySplitDisplay.Unavailable -> Text("The saved GPS or timing wasn’t complete enough for splits.",
                color = muted, fontSize = 12.sp, lineHeight = 18.sp)
            HistorySplitDisplay.Pending, HistorySplitDisplay.Failed -> {
                Text(if (display == HistorySplitDisplay.Pending) "Splits aren’t ready yet. Your run is saved."
                    else "Split details couldn’t load. Your run is saved.",
                    color = muted, fontSize = 12.sp, lineHeight = 18.sp)
                TextButton(onClick = onRetry, modifier = Modifier.testTag("historySplitsRetry")) {
                    Text("Retry splits", color = ink)
                }
            }
            HistorySplitDisplay.Loading, null -> Text("Loading splits…", color = muted, fontSize = 12.sp)
        }
    }
}
