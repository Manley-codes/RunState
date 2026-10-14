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
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runstate.mobile.R

internal const val ACTIVE_RUN_SCREEN_TAG = "activeRunScreen"
internal const val ACTIVE_RUN_CIRCLE_TAG = "activeRunCircle"
internal const val ACTIVE_RUN_ELAPSED_TAG = "activeRunElapsed"
internal const val ACTIVE_RUN_METRICS_TAG = "activeRunMetrics"

private val ActiveInk = Color(0xFF293326)
private val ActiveForest = Color(0xFF263222)
private val ActiveLime = Color(0xFFB8D442)
private val ActiveCream = Color(0xFFF9FAF5)

/**
 * The Start map becomes an elapsed-time instrument. It remains an illustration in every mode;
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
        val circleSize = (maxWidth - 48.dp).coerceIn(120.dp, 300.dp)
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
                Spacer(Modifier.height(22.dp))

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
                            text = "Elapsed",
                            color = ActiveInk.copy(alpha = 0.72f),
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.2.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        ActiveElapsedTime(metrics?.elapsed ?: "—")
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
                        text = "Active time and distance are paused. Elapsed includes breaks.",
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

                ActiveMetricsPanel(metrics, Modifier.padding(top = 20.dp))
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

/** Fit the whole saved-format timer to the circle without clipping digits or changing the text. */
@Composable
private fun ActiveElapsedTime(elapsed: String) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val measurer = rememberTextMeasurer()
        val style = TextStyle(fontFamily = CompletionNumberFont, fontWeight = FontWeight.Medium)
        val textSize = remember(elapsed, maxWidth, density, measurer) {
            val availableWidth = with(density) { maxWidth.toPx() }
            var low = 10f
            var high = 68f
            repeat(8) {
                val candidate = (low + high) / 2f
                val measured = measurer.measure(
                    text = elapsed, style = style.copy(fontSize = candidate.sp),
                    softWrap = false, maxLines = 1, constraints = Constraints()
                )
                if (measured.size.width <= availableWidth) low = candidate else high = candidate
            }
            low.sp
        }
        Text(
            text = elapsed,
            color = ActiveInk,
            style = style.copy(fontSize = textSize, lineHeight = (textSize.value * 1.12f).sp),
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().testTag(ACTIVE_RUN_ELAPSED_TAG)
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
            .padding(20.dp)
            .testTag(ACTIVE_RUN_METRICS_TAG)
    ) {
        val stacked = LocalDensity.current.fontScale > 1.25f || maxWidth < 280.dp
        Column(modifier = Modifier.fillMaxWidth()) {
            if (stacked) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ActiveMetric("Distance", metrics?.distance ?: "—")
                    ActiveMetric("Avg pace", metrics?.averagePace ?: "—")
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    ActiveMetric("Distance", metrics?.distance ?: "—", Modifier.weight(1f))
                    ActiveMetric("Avg pace", metrics?.averagePace ?: "—", Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(18.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(ActiveInk.copy(alpha = 0.09f)))
            Spacer(Modifier.height(14.dp))
            if (stacked) {
                ActiveMetric("Active", metrics?.active ?: "—", valueSize = 22)
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Active", color = ActiveInk.copy(alpha = 0.74f), fontSize = 12.sp)
                    Text(
                        text = metrics?.active ?: "—",
                        color = ActiveInk,
                        fontSize = 22.sp,
                        fontFamily = CompletionNumberFont,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
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
private fun ActiveMetric(label: String, value: String, modifier: Modifier = Modifier, valueSize: Int = 28) {
    Column(modifier = modifier) {
        Text(
            text = label,
            color = ActiveInk.copy(alpha = 0.7f),
            fontSize = 12.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = value,
            color = ActiveInk,
            fontFamily = CompletionNumberFont,
            fontSize = valueSize.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = (valueSize + 6).sp,
            modifier = Modifier.padding(top = 3.dp)
        )
    }
}
