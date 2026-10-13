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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runstate.mobile.R

internal const val ACTIVE_RUN_SCREEN_TAG = "activeRunScreen"
internal const val ACTIVE_RUN_CIRCLE_TAG = "activeRunCircle"

private val ActiveInk = Color(0xFF293326)
private val ActiveForest = Color(0xFF263222)
private val ActiveLime = Color(0xFFB8D442)

/**
 * First native Active visual pass. The map is decorative until GPS evidence exists; the
 * numbers come only from the session coordinator, and its source remains visible.
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
            .background(Brush.verticalGradient(listOf(Color(0xFFF9FAF5), Color(0xFFF0F3EB))))
    ) {
        val circleSize = minOf(maxWidth - 40.dp, maxHeight * 0.43f, 340.dp)
        val footerSpace = if (paused) 222.dp else 128.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, top = 22.dp, end = 24.dp, bottom = footerSpace),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RUNSTATE",
                    color = ActiveInk,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp
                )
                Text(
                    text = if (paused) "PAUSED" else "RUNNING",
                    color = if (paused) ActiveInk else ActiveForest,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (paused) Color(0xFFE1E7DC) else ActiveLime.copy(alpha = 0.55f))
                        .padding(horizontal = 13.dp, vertical = 9.dp)
                )
            }

            Text(
                text = if (paused) "You have a paused run" else "You have a run in progress",
                color = ActiveInk.copy(alpha = 0.78f),
                fontSize = 14.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            )
            Spacer(Modifier.height(18.dp))

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
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = 0.72f))
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = when {
                            !isGpsRun -> "ILLUSTRATIVE MAP · LOCATION OFF"
                            gpsServicePresent && paused -> "ILLUSTRATIVE MAP · GPS PAUSED"
                            gpsServicePresent -> "ILLUSTRATIVE MAP · GPS RECORDING"
                            else -> "ILLUSTRATIVE MAP · GPS OFF"
                        },
                        color = ActiveInk.copy(alpha = 0.72f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.7.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    CircleMetric("Elapsed", metrics?.elapsed ?: "—", prominent = true)
                    Spacer(Modifier.height(12.dp))
                    CircleMetric("Distance", metrics?.distance ?: "—")
                    Spacer(Modifier.height(8.dp))
                    CircleMetric("Avg pace", metrics?.averagePace ?: "—")
                }
            }

            Text(
                text = metrics?.sourceLabel ?: "Metrics unavailable",
                color = ActiveInk.copy(alpha = 0.75f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(top = 18.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.9f))
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Active", color = ActiveInk.copy(alpha = 0.74f), fontSize = 14.sp)
                Text(
                    metrics?.active ?: "—",
                    color = ActiveInk,
                    fontSize = 20.sp,
                    fontFamily = CompletionNumberFont,
                    fontWeight = FontWeight.Bold
                )
            }
            if (isGpsRun && !gpsServicePresent) {
                Text(
                    text = gpsMessage ?: "GPS is not recording. Distance may be incomplete.",
                    color = Color(0xFF8B392D),
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp,
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
            if (actionFailed) {
                Text(
                    text = ACTION_FAILED_MESSAGE,
                    color = Color(0xFFA13832),
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 14.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp))
                .background(Brush.horizontalGradient(listOf(ActiveForest, Color(0xFF1B2519))))
                .padding(horizontal = 24.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (paused) {
                Text(
                    text = "RUN PAUSED",
                    color = ActiveLime,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.4.sp
                )
                Button(
                    onClick = onResume,
                    enabled = actionsEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .heightIn(min = 54.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ActiveLime,
                        contentColor = ActiveInk
                    )
                ) {
                    Text("Resume", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
                HoldToStopButton(
                    onHoldComplete = onStop,
                    enabled = actionsEnabled,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text(
                    text = "YOUR RUN IS ACTIVE",
                    color = ActiveLime,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.4.sp
                )
                Button(
                    onClick = onPause,
                    enabled = actionsEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 9.dp)
                        .heightIn(min = 58.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = ActiveInk
                    )
                ) {
                    Text("Pause", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun CircleMetric(label: String, value: String, prominent: Boolean = false) {
    Text(
        text = label,
        color = ActiveInk.copy(alpha = 0.7f),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp
    )
    Text(
        text = value,
        color = ActiveInk,
        fontFamily = CompletionNumberFont,
        fontSize = if (prominent) 33.sp else 24.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = if (prominent) 38.sp else 28.sp
    )
}
