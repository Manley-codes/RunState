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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.runstate.mobile.location.GpsMapEvidence
import com.runstate.mobile.R
import com.runstate.mobile.location.GpsSignalStatus

internal const val ACTIVE_RUN_SCREEN_TAG = "activeRunScreen"
internal const val ACTIVE_RUN_CIRCLE_TAG = "activeRunCircle"
internal const val ACTIVE_RUN_DISTANCE_TAG = "activeRunDistance"
internal const val ACTIVE_RUN_ELAPSED_TAG = "activeRunElapsed"
internal const val ACTIVE_RUN_METRICS_TAG = "activeRunMetrics"
internal const val ACTIVE_RUN_PACE_TAG = "activeRunPace"
internal const val ACTIVE_RUN_ACTIVE_TAG = "activeRunActive"
internal const val ACTIVE_RUN_MAP_BUTTON_TAG = "activeRunMapButton"
internal const val ACTIVE_RUN_MAP_DIALOG_TAG = "activeRunMapDialog"

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
 * Measurements and GPS states come from the existing run owner. The illustrated instrument
 * stays explicit, while the map action reports an optional view request to the journey owner.
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
    modifier: Modifier = Modifier,
    gpsSignalStatus: GpsSignalStatus = GpsSignalStatus.ACQUIRING,
    onOpenRunMap: () -> Unit = {},
    liveMapBackground: Boolean = false,
    liveMapStatus: String? = null
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag(ACTIVE_RUN_SCREEN_TAG)
            .then(if (liveMapBackground) Modifier else Modifier.background(
                Brush.verticalGradient(listOf(ActiveCream, Color(0xFFF0F3EB)))))
    ) {
        val circleSize = (maxWidth - 48.dp).coerceIn(120.dp,
            if (liveMapBackground) 160.dp else 240.dp)
        val largeText = LocalDensity.current.fontScale > 1.3f
        val bodyScroll = rememberScrollState()
        LaunchedEffect(paused) { bodyScroll.scrollTo(0) }
        val mapStatus = if (liveMapBackground) liveMapStatus ?: when {
            !gpsServicePresent -> "GPS STOPPED"
            paused -> "GPS PAUSED · LAST RECORDED POSITION"
            else -> when (gpsSignalStatus) {
                GpsSignalStatus.ACQUIRING -> "WAITING FOR GPS"
                GpsSignalStatus.RECORDING -> "GPS RECORDING"
                GpsSignalStatus.WEAK -> "GPS WEAK SIGNAL"
                GpsSignalStatus.STALE -> "GPS SIGNAL UNAVAILABLE"
            }
        } else when {
            !isGpsRun -> "ILLUSTRATIVE MAP · LOCATION OFF"
            !gpsServicePresent -> "ILLUSTRATIVE MAP · GPS STOPPED"
            paused -> "ILLUSTRATIVE MAP · GPS PAUSED"
            else -> when (gpsSignalStatus) {
                GpsSignalStatus.ACQUIRING -> "ILLUSTRATIVE MAP · GPS WAITING"
                GpsSignalStatus.RECORDING -> "ILLUSTRATIVE MAP · GPS RECORDING"
                GpsSignalStatus.WEAK -> "ILLUSTRATIVE MAP · GPS WEAK SIGNAL"
                GpsSignalStatus.STALE -> "ILLUSTRATIVE MAP · GPS SIGNAL UNAVAILABLE"
            }
        }

        if (liveMapBackground) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                0f to ActiveCream.copy(alpha = 0.92f),
                0.25f to ActiveCream.copy(alpha = 0.75f),
                0.43f to ActiveCream.copy(alpha = 0.06f),
                0.7f to ActiveCream.copy(alpha = 0.1f),
                1f to ActiveCream.copy(alpha = 0.65f)
            )))
            RunMapTreeShadows(Modifier.fillMaxSize())
        }

        // Controls take their natural height. The body scrolls in the remaining space,
        // rather than relying on a fixed footer allowance that large text could exceed.
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(bodyScroll)
                    .padding(start = 24.dp, top = 22.dp, end = 24.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ActiveHeader(paused, largeText)
                if (!paused) Text(
                    text = "You have a run in progress",
                    color = ActiveInk.copy(alpha = 0.78f),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                )
                Spacer(Modifier.height(if (paused) 10.dp else 16.dp))

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
                    maximumSize = if (paused) 64 else 76,
                    compactDistanceUnit = true,
                    modifier = Modifier.padding(top = 4.dp).testTag(ACTIVE_RUN_DISTANCE_TAG)
                )
                Spacer(Modifier.height(if (paused) 10.dp else 14.dp))

                if (paused) {
                    PausedMetricsPanel(metrics)
                } else Box(
                    modifier = Modifier
                        .align(if (liveMapBackground) Alignment.End else Alignment.CenterHorizontally)
                        .size(circleSize)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = if (liveMapBackground) 0.72f else 1f))
                        .testTag(ACTIVE_RUN_CIRCLE_TAG),
                    contentAlignment = Alignment.Center
                ) {
                    if (!liveMapBackground) {
                        Image(
                            painter = painterResource(R.drawable.start_map_reference),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(Modifier.fillMaxSize().background(ActiveCream.copy(alpha = 0.8f)))
                    }
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal =
                            if (liveMapBackground) 16.dp else 28.dp),
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
                            maximumSize = if (liveMapBackground) 48 else 68,
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
                if (isGpsRun) {
                    TextButton(
                        onClick = onOpenRunMap,
                        modifier = Modifier.heightIn(min = 48.dp).testTag(ACTIVE_RUN_MAP_BUTTON_TAG)
                    ) {
                        Text(if (liveMapBackground) "Expand map"
                            else if (paused) "View recorded map" else "View live map", color = ActiveForest,
                            fontWeight = FontWeight.Bold)
                    }
                }
                if (paused) {
                    Text(
                        text = "Active time and distance are paused.",
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

                if (!paused) ActiveMetricsPanel(metrics, Modifier.padding(top = 16.dp))
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
                    .padding(horizontal = 24.dp, vertical = if (paused) 12.dp else 20.dp),
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
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                        .heightIn(min = if (paused) 50.dp else 58.dp),
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

/** Optional street-map presentation; the existing run owner still owns every control. */
@Composable
internal fun ActiveRunMapDialog(
    evidence: GpsMapEvidence?,
    paused: Boolean,
    actionsEnabled: Boolean,
    actionFailed: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDismiss: () -> Unit,
    enableNativeMap: Boolean = false
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().padding(12.dp).clip(RoundedCornerShape(24.dp))
                .background(ActiveCream).padding(16.dp).testTag(ACTIVE_RUN_MAP_DIALOG_TAG)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (paused) "Recorded map" else "Live run map", color = ActiveInk,
                    fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Close") }
            }
            if (evidence == null) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Loading recorded GPS…", color = ActiveInk)
                }
            } else {
                RecordedRunMap(evidence, Modifier.weight(1f).fillMaxWidth(), enableNativeMap)
            }
            if (actionFailed) Text(ACTION_FAILED_MESSAGE, color = Color(0xFF8B392D),
                fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            Button(onClick = if (paused) onResume else onPause, enabled = actionsEnabled,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ActiveForest)) {
                Text(if (paused) "Resume run" else "Pause run")
            }
            Text("Close the map to finish your run. Map tiles from OpenFreeMap. Viewed map areas and your IP address go to the provider; your recorded route stays on this phone.",
                color = ActiveInk, fontSize = 11.sp, lineHeight = 16.sp,
                modifier = Modifier.padding(top = 8.dp))
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
private fun PausedMetricsPanel(metrics: RunMetricsDisplay?) {
    BoxWithConstraints(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.94f)).padding(16.dp)
            .testTag(ACTIVE_RUN_METRICS_TAG)
    ) {
        // Keep the frozen timer and pace before the optional map/status details. They are
        // immediately visible on ordinary portrait screens; large text can still scroll.
        val stacked = LocalDensity.current.fontScale > 1.3f || maxWidth < 260.dp
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (stacked) {
                ActiveMetric("Active time", metrics?.active ?: "—", ACTIVE_RUN_ACTIVE_TAG)
                ActiveMetric("Avg pace", metrics?.averagePace ?: "—", ACTIVE_RUN_PACE_TAG)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ActiveMetric("Active time", metrics?.active ?: "—", ACTIVE_RUN_ACTIVE_TAG,
                        Modifier.weight(1f))
                    ActiveMetric("Avg pace", metrics?.averagePace ?: "—", ACTIVE_RUN_PACE_TAG,
                        Modifier.weight(1f))
                }
            }
            Text(metrics?.sourceLabel ?: "Metrics unavailable", color = ActiveInk.copy(alpha = 0.74f),
                fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
        }
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
        Column(modifier = Modifier.fillMaxWidth()) {
            ActiveMetric("Avg pace", metrics?.averagePace ?: "—", ACTIVE_RUN_PACE_TAG)
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
