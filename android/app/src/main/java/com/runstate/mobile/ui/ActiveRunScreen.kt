package com.runstate.mobile.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.runstate.mobile.R

internal const val ACTIVE_RUN_SCREEN_TAG = "activeRunScreen"
internal const val ACTIVE_RUN_CIRCLE_TAG = "activeRunCircle"
internal const val ACTIVE_RUN_DISTANCE_TAG = "activeRunDistance"
internal const val ACTIVE_RUN_ELAPSED_TAG = "activeRunElapsed"
internal const val ACTIVE_RUN_METRICS_TAG = "activeRunMetrics"
internal const val ACTIVE_RUN_PACE_TAG = "activeRunPace"
internal const val ACTIVE_RUN_ACTIVE_TAG = "activeRunActive"

private val ActiveInk = Color(0xFF293326)
private val ActiveForest = Color(0xFF263222)
private val ActiveLime = Color(0xFFB8D442)
private val ActiveCream = Color(0xFFF9FAF5)

/** An unfinished short run remains safely paused until the runner makes an explicit choice. */
@Composable
internal fun ShortRunDecisionDialog(
    busy: Boolean,
    error: String?,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onKeepPaused: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onKeepPaused() },
        containerColor = ActiveCream,
        titleContentColor = ActiveInk,
        textContentColor = ActiveInk,
        title = { Text("Save this short run?") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("You recorded less than 30 seconds of active time. Save it to History, or discard it if you started by accident. Discard permanently removes this run.")
                if (error != null) Text(error, color = Color(0xFF8B392D))
            }
        },
        confirmButton = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onSave,
                    enabled = !busy,
                    colors = ButtonDefaults.buttonColors(containerColor = ActiveForest),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) { Text(if (busy) "Finishing…" else "Save run") }
                TextButton(
                    onClick = onDiscard,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) { Text("Discard run", color = if (busy) ActiveInk.copy(alpha = 0.38f) else Color(0xFF8B392D)) }
                TextButton(
                    onClick = onKeepPaused,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) { Text("Keep paused", color = ActiveInk.copy(alpha = if (busy) 0.38f else 0.85f)) }
            }
        }
    )
}

/**
 * The Start map becomes an active-time instrument. It remains an illustration in every mode;
 * all displayed measurements and GPS states come from the existing run owner.
 */
@Composable
internal fun ActiveRunScreen(
    paused: Boolean,
    metrics: RunMetricsDisplay?,
    actionFailed: Boolean,
    actionsEnabled: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    isGpsRun: Boolean = false,
    gpsServicePresent: Boolean = false,
    gpsMessage: String? = null,
    onResumeGpsTracking: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag(ACTIVE_RUN_SCREEN_TAG)
            .background(Brush.verticalGradient(listOf(ActiveCream, Color(0xFFF0F3EB))))
    ) {
        val circleSize = (maxWidth - 48.dp).coerceIn(120.dp, 240.dp)
        val largeText = LocalDensity.current.fontScale > 1.3f
        val mapStatus = when {
            !isGpsRun -> "ILLUSTRATIVE MAP · LOCATION OFF"
            !gpsServicePresent -> "ILLUSTRATIVE MAP · GPS STOPPED"
            paused -> "ILLUSTRATIVE MAP · GPS PAUSED"
            metrics?.gpsDistanceAvailable != true -> "ILLUSTRATIVE MAP · GPS WAITING"
            else -> "ILLUSTRATIVE MAP · GPS RECORDING"
        }

        // Controls take their natural height. The body scrolls in the remaining space,
        // rather than relying on a fixed footer allowance that large text could exceed.
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, top = 22.dp, end = 24.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ActiveHeader(paused, largeText)
                Text(
                    text = if (paused) "You have a paused run" else "You have a run in progress",
                    color = ActiveInk.copy(alpha = 0.78f),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                )
                Spacer(Modifier.height(16.dp))

                Text(
                    text = if (isGpsRun) "GPS RUN" else "SAMPLE RUN · SIMULATED DISTANCE",
                    color = ActiveForest,
                    fontSize = 11.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.8.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isGpsRun) Color.White else ActiveLime.copy(alpha = 0.26f))
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                )
                if (!isGpsRun) {
                    Text(
                        text = "Sample distance increases automatically, even when you are still.",
                        color = ActiveInk.copy(alpha = 0.78f),
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Distance",
                    color = ActiveInk.copy(alpha = 0.72f),
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.2.sp
                )
                ActiveFittedValue(
                    value = metrics?.distance ?: "—",
                    maximumSize = 76,
                    compactDistanceUnit = true,
                    modifier = Modifier.padding(top = 4.dp).testTag(ACTIVE_RUN_DISTANCE_TAG)
                )
                Spacer(Modifier.height(14.dp))

                Box(
                    modifier = Modifier
                        .size(circleSize)
                        .clip(CircleShape)
                        .background(Color.White)
                        .testTag(ACTIVE_RUN_CIRCLE_TAG),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.start_map_reference),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(Modifier.fillMaxSize().background(ActiveCream.copy(alpha = 0.8f)))
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Active time",
                            color = ActiveInk.copy(alpha = 0.72f),
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.2.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        ActiveFittedValue(
                            value = metrics?.active ?: "—",
                            maximumSize = 68,
                            modifier = Modifier.testTag(ACTIVE_RUN_ACTIVE_TAG)
                        )
                    }
                }

                Text(
                    text = mapStatus,
                    color = ActiveInk.copy(alpha = 0.78f),
                    fontSize = 10.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.7.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.7f))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
                if (paused) {
                    Text(
                        text = "Active time and distance are paused. Total time includes breaks.",
                        color = ActiveInk.copy(alpha = 0.78f),
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                    )
                }
                if (isGpsRun && gpsServicePresent && !paused && metrics?.gpsDistanceAvailable != true) {
                    Text(
                        text = "Waiting for usable GPS movement. Distance will appear once it is recorded.",
                        color = ActiveInk.copy(alpha = 0.75f),
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }

                ActiveMetricsPanel(metrics, Modifier.padding(top = 16.dp))
                if (isGpsRun && !gpsServicePresent) {
                    Text(
                        text = gpsMessage ?: "GPS is not recording. Distance may be incomplete.",
                        color = Color(0xFF8B392D),
                        textAlign = TextAlign.Center,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(top = 14.dp)
                    )
                    Button(
                        onClick = onResumeGpsTracking,
                        enabled = actionsEnabled,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ActiveForest,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.padding(top = 8.dp).heightIn(min = 48.dp)
                    ) {
                        Text("Restart GPS tracking")
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                    .background(Brush.horizontalGradient(listOf(ActiveForest, Color(0xFF1B2618))))
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (actionFailed) {
                    Text(
                        text = ACTION_FAILED_MESSAGE,
                        color = ActiveCream,
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
                Text(
                    text = if (paused) "RUN PAUSED" else "YOUR RUN IS ACTIVE",
                    color = ActiveLime,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.4.sp,
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = if (paused) onResume else onPause,
                    enabled = actionsEnabled,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp).heightIn(min = 58.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (paused) ActiveLime else Color.White,
                        contentColor = ActiveInk
                    )
                ) {
                    Text(
                        text = if (paused) "Resume" else "Pause",
                        fontSize = 18.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (paused) {
                    HoldToStopButton(
                        onHoldComplete = onStop,
                        enabled = actionsEnabled,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveHeader(paused: Boolean, stacked: Boolean) {
    if (stacked) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ActiveBrand()
            ActiveStateBadge(paused)
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ActiveBrand()
            ActiveStateBadge(paused)
        }
    }
}

@Composable
private fun ActiveBrand() {
    Text(
        text = "RUNSTATE",
        color = ActiveInk,
        fontSize = 18.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 1.5.sp
    )
}

@Composable
private fun ActiveStateBadge(paused: Boolean) {
    Text(
        text = if (paused) "PAUSED" else "RUNNING",
        color = ActiveForest,
        fontSize = 12.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 1.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (paused) Color(0xFFE1E7DC) else ActiveLime.copy(alpha = 0.55f))
            .padding(horizontal = 13.dp, vertical = 9.dp)
    )
}

/** Fit an exact formatted value, including its unit, without clipping or changing the text. */
@Composable
private fun ActiveFittedValue(
    value: String,
    maximumSize: Int,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Center,
    compactDistanceUnit: Boolean = false
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val measurer = rememberTextMeasurer()
        val style = TextStyle(fontFamily = CompletionNumberFont, fontWeight = FontWeight.Medium)
        // The formatter still owns the complete value; only an existing mi/km suffix changes size.
        val displayedValue = remember(value, compactDistanceUnit) {
            if (compactDistanceUnit && (value.endsWith(" mi") || value.endsWith(" km"))) {
                buildAnnotatedString {
                    append(value.dropLast(3))
                    withStyle(SpanStyle(fontSize = 0.32.em)) { append(value.takeLast(3)) }
                }
            } else AnnotatedString(value)
        }
        val textSize = remember(displayedValue, maximumSize, maxWidth, density, measurer) {
            val availableWidth = with(density) { maxWidth.toPx() }
            var low = 10f
            var high = maximumSize.toFloat()
            repeat(8) {
                val candidate = (low + high) / 2f
                val measured = measurer.measure(
                    text = displayedValue, style = style.copy(fontSize = candidate.sp),
                    softWrap = false, maxLines = 1, constraints = Constraints()
                )
                if (measured.size.width <= availableWidth) low = candidate else high = candidate
            }
            low.sp
        }
        Text(
            text = displayedValue,
            color = ActiveInk,
            style = style.copy(fontSize = textSize, lineHeight = (textSize.value * 1.12f).sp),
            maxLines = 1,
            softWrap = false,
            textAlign = textAlign,
            modifier = modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ActiveMetricsPanel(metrics: RunMetricsDisplay?, modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.9f))
            .padding(16.dp)
            .testTag(ACTIVE_RUN_METRICS_TAG)
    ) {
        val stacked = LocalDensity.current.fontScale > 1.25f || maxWidth < 280.dp
        Column(modifier = Modifier.fillMaxWidth()) {
            if (stacked) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ActiveMetric("Avg pace", metrics?.averagePace ?: "—", ACTIVE_RUN_PACE_TAG)
                    ActiveMetric("Total incl. breaks", metrics?.elapsed ?: "—", ACTIVE_RUN_ELAPSED_TAG)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    ActiveMetric("Avg pace", metrics?.averagePace ?: "—", ACTIVE_RUN_PACE_TAG,
                        Modifier.weight(1f))
                    ActiveMetric("Total incl. breaks", metrics?.elapsed ?: "—", ACTIVE_RUN_ELAPSED_TAG,
                        Modifier.weight(1f))
                }
            }
            Text(
                text = metrics?.sourceLabel ?: "Metrics unavailable",
                color = ActiveInk.copy(alpha = 0.74f),
                fontSize = 12.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

@Composable
private fun ActiveMetric(label: String, value: String, valueTag: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label,
            color = ActiveInk.copy(alpha = 0.7f),
            fontSize = 12.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
        ActiveFittedValue(
            value = value,
            maximumSize = 28,
            textAlign = TextAlign.Start,
            modifier = Modifier.padding(top = 3.dp).testTag(valueTag)
        )
    }
}
