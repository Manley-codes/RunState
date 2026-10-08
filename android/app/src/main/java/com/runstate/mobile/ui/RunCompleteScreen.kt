package com.runstate.mobile.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

internal const val RUN_COMPLETE_SCREEN_TAG = "runCompleteScreen"
internal const val RUN_COMPLETE_DISTANCE_TAG = "runCompleteDistance"
internal const val RUN_COMPLETE_VISUALIZER_TAG = "runCompleteVisualizer"
internal const val RUN_COMPLETE_REFLECTION_TAG = "runCompleteReflection"
internal const val RUN_COMPLETE_REFLECTION_TEXT_TAG = "runCompleteReflectionText"
internal const val RUN_COMPLETE_REFLECTION_RETRY_TAG = "runCompleteReflectionRetry"

private val CompletionInk = Color(0xFF4A5049)
private val CompletionStrongInk = Color(0xFF353A34)
private val CompletionMutedInk = Color(0xFF8A8F87)
private val CompletionGreen = Color(0xFF6A7F60)
private val CompletionBlue = Color(0xFFAFCEDA)
private val CompletionSurfaceTop = Color(0xFFFCFCFA)
private val CompletionSurfaceBottom = Color(0xFFF4F5F1)

/** The only reflection information the completion UI is allowed to know. */
internal sealed interface RunCompleteReflectionDisplay {
    data object Preparing : RunCompleteReflectionDisplay
    data class Ready(val text: String) : RunCompleteReflectionDisplay
    data object Failed : RunCompleteReflectionDisplay
}

/**
 * Android ships a condensed sans family. Prefer it for instrument-like numbers and fall
 * back to the ordinary system sans if an OEM does not provide it under the AOSP name.
 */
private val CompletionNumberFont = FontFamily(
    Font(DeviceFontFamilyName("sans-serif-condensed"), weight = FontWeight.Normal),
    Font(DeviceFontFamilyName("sans-serif-condensed"), weight = FontWeight.Medium),
    Font(DeviceFontFamilyName("sans-serif"), weight = FontWeight.Normal),
    Font(DeviceFontFamilyName("sans-serif"), weight = FontWeight.Medium)
)

/**
 * The saved-run presentation, translated from the working Run Complete reference.
 *
 * The layout deliberately consumes only facts already supplied by the lifecycle foundation.
 * The visualizer is a static completed-state mark: it does not claim music, reflection or live
 * analysis. A missing read remains an em dash, and the fixed action stays reachable while the
 * factual content scrolls behind it on short screens or with large system text.
 */
@Composable
internal fun RunCompleteScreen(
    metrics: RunMetricsDisplay?,
    reflection: RunCompleteReflectionDisplay?,
    onRetryReflection: () -> Unit,
    onStartAnother: () -> Unit,
    actionsEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val distance = splitMetric(metrics?.distance ?: "—")
    val pace = splitMetric(metrics?.averagePace ?: "—")
    val sourceLabel = metrics?.sourceLabel ?: "Metrics unavailable"

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag(RUN_COMPLETE_SCREEN_TAG)
            .background(
                Brush.verticalGradient(
                    colors = listOf(CompletionSurfaceTop, CompletionSurfaceBottom)
                )
            )
    ) {
        CompletionAtmosphere(Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 26.dp, top = 24.dp, end = 22.dp, bottom = 126.dp)
        ) {
            SavedStatus(sourceLabel)
            Spacer(modifier = Modifier.height(24.dp))
            CompletionHero(
                distance = distance,
                elapsed = metrics?.elapsed ?: "—",
                pace = pace,
                active = metrics?.active ?: "—"
            )
            if (reflection != null) {
                Spacer(modifier = Modifier.height(14.dp))
                ReflectionPanel(
                    reflection = reflection,
                    onRetry = onRetryReflection,
                    retryEnabled = actionsEnabled
                )
            }
        }

        Button(
            onClick = onStartAnother,
            enabled = actionsEnabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = CompletionStrongInk,
                contentColor = Color.White,
                disabledContainerColor = CompletionStrongInk.copy(alpha = 0.38f),
                disabledContentColor = Color.White.copy(alpha = 0.7f)
            ),
            shape = RoundedCornerShape(20.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp)
                .heightIn(min = 56.dp)
        ) {
            Text(
                text = "Start another run",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.2.sp
            )
        }
    }
}

/**
 * Temporary text-only reflection treatment while Energy selection is not implemented.
 * The disclosure stays attached in every state so a fake reply cannot resemble runner data.
 */
@Composable
private fun ReflectionPanel(
    reflection: RunCompleteReflectionDisplay,
    onRetry: () -> Unit,
    retryEnabled: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(RUN_COMPLETE_REFLECTION_TAG)
            .background(
                color = Color.White.copy(alpha = 0.62f),
                shape = RoundedCornerShape(22.dp)
            )
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Text(
            text = "REFLECTION",
            color = CompletionMutedInk,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.9.sp
        )
        Text(
            text = "FAKE BACKEND · TEST DATA",
            color = CompletionGreen,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.25.sp,
            modifier = Modifier.padding(top = 6.dp)
        )

        when (reflection) {
            RunCompleteReflectionDisplay.Preparing -> {
                Text(
                    text = "Preparing your reflection…",
                    color = CompletionStrongInk,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 27.sp,
                    modifier = Modifier.padding(top = 18.dp)
                )
                Text(
                    text = "Your run is already saved.",
                    color = CompletionMutedInk,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            is RunCompleteReflectionDisplay.Ready -> Text(
                text = reflection.text,
                color = CompletionStrongInk,
                fontSize = 21.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 29.sp,
                modifier = Modifier
                    .padding(top = 18.dp)
                    .testTag(RUN_COMPLETE_REFLECTION_TEXT_TAG)
            )

            RunCompleteReflectionDisplay.Failed -> {
                Text(
                    text = "Reflection couldn’t be prepared.",
                    color = CompletionStrongInk,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 26.sp,
                    modifier = Modifier.padding(top = 18.dp)
                )
                Text(
                    text = "Your saved run is unchanged. Start the local fake backend, then retry.",
                    color = CompletionMutedInk,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
                OutlinedButton(
                    onClick = onRetry,
                    enabled = retryEnabled,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .padding(top = 14.dp)
                        .testTag(RUN_COMPLETE_REFLECTION_RETRY_TAG)
                ) {
                    Text("Retry reflection")
                }
            }
        }
    }
}

/** The reference uses one quiet line rather than a badge or a second page title. */
@Composable
private fun SavedStatus(sourceLabel: String) {
    val largeText = LocalDensity.current.fontScale > 1.35f

    if (largeText) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "RUN SAVED",
                color = CompletionGreen,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.2.sp
            )
            Text(
                text = sourceLabel.uppercase(),
                color = CompletionMutedInk,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.8.sp
            )
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "RUN SAVED",
                color = CompletionGreen,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.2.sp
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = 10.dp)
                    .size(5.dp)
                    .background(CompletionBlue, RoundedCornerShape(50))
            )
            Text(
                text = sourceLabel.uppercase(),
                color = CompletionMutedInk,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.7.sp
            )
        }
    }
}

@Composable
private fun CompletionHero(
    distance: MetricParts,
    elapsed: String,
    pace: MetricParts,
    active: String
) {
    val largeText = LocalDensity.current.fontScale > 1.35f

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (!largeText && maxWidth >= 320.dp) {
            OffsetCompletionHero(
                distance = distance,
                elapsed = elapsed,
                pace = pace,
                active = active
            )
        } else {
            AccessibleCompletionHero(
                distance = distance,
                elapsed = elapsed,
                pace = pace,
                active = active
            )
        }
    }
}

/** The normal-size composition mirrors the reference's deliberate overlap and offset. */
@Composable
private fun OffsetCompletionHero(
    distance: MetricParts,
    elapsed: String,
    pace: MetricParts,
    active: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(246.dp)
    ) {
        DistanceInstrument(
            distance = distance,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 18.dp)
        )

        CompletionVisualizer(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(x = 16.dp, y = 18.dp)
                .size(80.dp)
        )

        CompactMetrics(
            elapsed = elapsed,
            pace = pace,
            active = active,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 35.dp)
                .width(145.dp)
        )
    }
}

/** Large text gets a linear reading order instead of being forced through the overlap. */
@Composable
private fun AccessibleCompletionHero(
    distance: MetricParts,
    elapsed: String,
    pace: MetricParts,
    active: String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        DistanceInstrument(distance = distance)
        CompletionVisualizer(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(vertical = 18.dp)
                .size(116.dp)
        )
        CompactMetrics(
            elapsed = elapsed,
            pace = pace,
            active = active,
            modifier = Modifier.fillMaxWidth(),
            generousSpacing = true
        )
    }
}

@Composable
private fun DistanceInstrument(
    distance: MetricParts,
    modifier: Modifier = Modifier
) {
    val numberSize = when {
        distance.value.length >= 5 -> 72.sp
        distance.value.length == 4 -> 82.sp
        else -> 98.sp
    }
    val numberLineHeight = when {
        distance.value.length >= 5 -> 70.sp
        distance.value.length == 4 -> 80.sp
        else -> 92.sp
    }

    Column(modifier = modifier.testTag(RUN_COMPLETE_DISTANCE_TAG)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = distance.value,
                color = CompletionInk,
                fontFamily = CompletionNumberFont,
                fontSize = numberSize,
                fontWeight = FontWeight.Normal,
                lineHeight = numberLineHeight,
                letterSpacing = (-1.4).sp,
                maxLines = 1
            )
            if (distance.unit.isNotEmpty()) {
                Text(
                    text = distance.unit.uppercase(),
                    color = CompletionMutedInk.copy(alpha = 0.72f),
                    fontFamily = CompletionNumberFont,
                    fontSize = if (distance.value.length >= 4) 24.sp else 28.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 6.dp, bottom = 10.dp)
                )
            }
        }
        DistanceRuler(
            modifier = Modifier
                .padding(top = 8.dp)
                .width(174.dp)
                .height(14.dp)
        )
    }
}

@Composable
private fun DistanceRuler(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val tickCount = 29
        val spacing = size.width / (tickCount - 1)
        repeat(tickCount) { index ->
            val strong = index == tickCount / 2
            val height = when {
                strong -> size.height
                index % 5 == 0 -> size.height * 0.68f
                else -> size.height * 0.43f
            }
            drawLine(
                color = if (strong) CompletionGreen else CompletionMutedInk.copy(alpha = 0.2f),
                start = Offset(index * spacing, size.height - height),
                end = Offset(index * spacing, size.height),
                strokeWidth = if (strong) 1.5.dp.toPx() else 1.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun CompactMetrics(
    elapsed: String,
    pace: MetricParts,
    active: String,
    modifier: Modifier = Modifier,
    generousSpacing: Boolean = false
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(if (generousSpacing) 14.dp else 10.dp)
    ) {
        CompactMetric(value = elapsed, label = "TIME", valueSize = 22.sp)
        CompactMetric(value = pace.value, unit = pace.unit, label = "PACE", valueSize = 29.sp)
        CompactMetric(value = active, label = "ACTIVE", valueSize = 22.sp)
    }
}

@Composable
private fun CompactMetric(
    value: String,
    label: String,
    valueSize: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier,
    unit: String = ""
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = value,
            color = CompletionInk,
            fontFamily = CompletionNumberFont,
            fontSize = valueSize,
            fontWeight = FontWeight.Medium,
            lineHeight = valueSize,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip
        )
        if (unit.isNotEmpty()) {
            Text(
                text = unit.uppercase(),
                color = CompletionMutedInk,
                fontFamily = CompletionNumberFont,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 2.dp, bottom = 2.dp)
            )
        }
        Text(
            text = label,
            color = CompletionMutedInk.copy(alpha = 0.72f),
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.5.sp,
            modifier = Modifier.padding(start = 8.dp, bottom = 3.dp)
        )
    }
}

/** A neutral completion mark; static because reflection text does not imply live analysis. */
@Composable
private fun CompletionVisualizer(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.testTag(RUN_COMPLETE_VISUALIZER_TAG)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val outerRadius = min(size.width, size.height) * 0.48f
        val innerRadius = outerRadius * 0.78f
        val tickCount = 64

        repeat(tickCount) { index ->
            val angle = (index.toFloat() / tickCount.toFloat()) * 2f * PI.toFloat() - PI.toFloat() / 2f
            val color = when (index) {
                in 18..28 -> Color(0xFFC7D7A5)
                in 29..39 -> Color(0xFFC5C8E7)
                in 40..49 -> Color(0xFFAFCEDD)
                else -> CompletionMutedInk.copy(alpha = 0.28f)
            }
            val start = Offset(
                x = center.x + cos(angle) * innerRadius,
                y = center.y + sin(angle) * innerRadius
            )
            val end = Offset(
                x = center.x + cos(angle) * outerRadius,
                y = center.y + sin(angle) * outerRadius
            )
            drawLine(
                color = color,
                start = start,
                end = end,
                strokeWidth = 1.2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        drawCircle(
            color = CompletionSurfaceTop.copy(alpha = 0.78f),
            radius = innerRadius * 0.82f,
            center = center
        )
    }
}

/** Fine structure and pale facets recreate the reference's layered porcelain field. */
@Composable
private fun CompletionAtmosphere(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFE9EEE4).copy(alpha = 0.55f), Color.Transparent),
                center = Offset(size.width * 0.82f, size.height * 0.18f),
                radius = size.width * 0.78f
            )
        )

        val gridColor = CompletionInk.copy(alpha = 0.026f)
        val gridStep = 32.dp.toPx()
        var x = 0f
        while (x <= size.width) {
            drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
            x += gridStep
        }
        var y = 0f
        while (y <= size.height) {
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            y += gridStep
        }

        val topFacet = Path().apply {
            moveTo(size.width * 0.67f, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width, size.height * 0.17f)
            lineTo(size.width * 0.55f, size.height * 0.22f)
            close()
        }
        drawPath(topFacet, Color(0xFFDDE5D8).copy(alpha = 0.55f))

        val topHighlight = Path().apply {
            moveTo(size.width * 0.78f, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width, size.height * 0.12f)
            close()
        }
        drawPath(topHighlight, Color.White.copy(alpha = 0.58f))

        val middleFacet = Path().apply {
            moveTo(size.width * 0.58f, size.height * 0.39f)
            lineTo(size.width, size.height * 0.47f)
            lineTo(size.width * 0.77f, size.height * 0.68f)
            close()
        }
        drawPath(middleFacet, Color(0xFFEAE8D7).copy(alpha = 0.44f))

        val lowerFacet = Path().apply {
            moveTo(size.width * 0.72f, size.height * 0.56f)
            lineTo(size.width, size.height * 0.61f)
            lineTo(size.width * 0.88f, size.height * 0.78f)
            close()
        }
        drawPath(lowerFacet, Color(0xFFE1E7E1).copy(alpha = 0.32f))
    }
}

private data class MetricParts(val value: String, val unit: String)

/** Keeps the formatter's honest em dash intact while allowing a real unit to sit quietly beside it. */
private fun splitMetric(formattedMetric: String): MetricParts {
    val separator = formattedMetric.lastIndexOf(' ')
    return if (separator > 0 && separator < formattedMetric.lastIndex) {
        MetricParts(
            value = formattedMetric.substring(0, separator),
            unit = formattedMetric.substring(separator + 1)
        )
    } else {
        MetricParts(value = formattedMetric, unit = "")
    }
}
