package com.runstate.mobile.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/** Shared decorative agent: not a gauge or proof of music/analysis activity. */
@Composable
internal fun rememberRunAgentPhase(active: Boolean): State<Float> = if (active) {
    rememberInfiniteTransition(label = "agentSignal").animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(5464, easing = LinearEasing)),
        label = "agentSignalSweep"
    )
} else {
    remember { mutableFloatStateOf(0f) }
}

@Composable
internal fun RunAgentRing(phase: State<Float>, intakeTime: () -> Float?, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        // Fixed bar angles and a travelling three-lobed wave reproduce the prototype's
        // spinning illusion. The bar lengths, rather than the whole ring, move around it.
        val barWidth = (size.minDimension * 0.014f).coerceIn(1.dp.toPx(), 1.4.dp.toPx())
        if (size.minDimension <= barWidth) return@Canvas
        // Reserve the rounded caps as well as the receiving swell inside the measured drawing.
        val units = (size.minDimension - barWidth) / (2f * (264f + 79.8f * 1.8f))
        val radius = 264f * units
        val receiptFraction = intakeTime()?.let { (it - 1010f) / 620f }
        val swell = if (receiptFraction != null && receiptFraction in 0f..1f) {
            sin(PI * receiptFraction.toDouble().pow(0.62)).pow(1.4).toFloat()
        } else 0f
        repeat(76) { index ->
            val theta = index * 2.0 * PI / 76.0
            val angle = theta - PI / 2.0
            val direction = Offset(cos(angle).toFloat(), sin(angle).toFloat())
            val wave = (0.5 + 0.5 * sin(theta * 3.0 - phase.value)).toFloat()
            val receipt = swell * (0.62f + 0.38f * (0.5f + 0.5f * cos(theta - PI).toFloat()))
            val height = (9f + wave * 7.5f +
                (sin(index * 5.31).toFloat() * 0.5f + 0.5f) * 2.5f) * 4.2f * (1f + receipt * 0.8f)
            val color = when (index * 56 / 76) {
                in 0..17 -> Color(0xFF849B80)
                in 18..34 -> Color(0xFFA390B3)
                else -> Color(0xFFACA473)
            }
            drawLine(
                color = color.copy(alpha = (0.84f + wave * 0.12f + receipt * 0.04f).coerceAtMost(1f)),
                start = center + direction * radius,
                end = center + direction * (radius + height * units),
                strokeWidth = barWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

