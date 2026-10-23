package com.runstate.mobile.ui

import android.animation.ValueAnimator
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.StartOffsetType
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.runstate.mobile.R
import kotlin.math.min

internal const val COUNTDOWN_SCREEN_TAG = "countdownScreen"
internal const val COUNTDOWN_DIGIT_TAG = "countdownDigit"
private val CountdownInk = Color(0xFF263329)
private val CountdownFont = FontFamily(Font(R.font.anton_regular))
private val DriftEase = CubicBezierEasing(.42f, 0f, .58f, 1f)
private val PulseEase = CubicBezierEasing(0f, 0f, .58f, 1f)

private data class CountdownOrb(
    val resource: Int,
    val x: Float,
    val y: Float,
    val width: Float,
    val driftX: Float,
    val driftY: Float,
    val durationMillis: Int,
    val phaseMillis: Int,
    val tilt: Float
)

// The accepted Frosted Drift arrangement, including the smaller orbs and clearer trail.
private val CountdownOrbs = listOf(
    CountdownOrb(R.drawable.countdown_shoe, .20f, .16f, .392f, 7f, 10f, 9_000, 2_000, -3f),
    CountdownOrb(R.drawable.countdown_bottle, .81f, .20f, .328f, -8f, 12f, 10_000, 5_000, 3f),
    CountdownOrb(R.drawable.countdown_headphones, .11f, .36f, .272f, 6f, -10f, 8_000, 1_000, 3f),
    CountdownOrb(R.drawable.countdown_runner, .89f, .46f, .376f, -7f, -9f, 11_000, 4_000, -2f),
    CountdownOrb(R.drawable.countdown_shades, .11f, .57f, .312f, -6f, 11f, 10_000, 7_000, -3f),
    CountdownOrb(R.drawable.countdown_fern, .82f, .68f, .272f, 10f, -9f, 9_000, 3_000, 4f),
    CountdownOrb(R.drawable.countdown_trail, .28f, .71f, .26f, -7f, -9f, 11_000, 3_000, 3f)
)

/**
 * Presentation only. The run coordinator owns the countdown, spoken cues and recording start.
 * The parent provides the safe content bounds and fades this surface into the active run.
 */
@Composable
internal fun CountdownScreen(
    digit: Int?,
    actionsEnabled: Boolean,
    onStartNow: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    transitioning: Boolean = false
) {
    val motionEnabled = rememberCountdownMotionEnabled()
    val visibleDigit = digit?.takeIf { it > 0 && !transitioning }
    val canStart = actionsEnabled && visibleDigit != null
    val canCancel = actionsEnabled && !transitioning
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(visibleDigit, motionEnabled) {
        if (visibleDigit == null || !motionEnabled) {
            pulse.snapTo(1f)
        } else {
            pulse.snapTo(0f)
            pulse.animateTo(1f, tween(1_000, easing = LinearEasing))
        }
    }

    BoxWithConstraints(
        modifier.fillMaxSize().clipToBounds().background(Color(0xFFEDF4F0))
            .testTag(COUNTDOWN_SCREEN_TAG)
    ) {
        val density = LocalDensity.current
        val compact = maxHeight < 480.dp
        val visualScale = min(maxWidth.value / 393f, maxHeight.value / 740f).coerceIn(.55f, 1.35f)
        Image(
            painter = painterResource(R.drawable.countdown_frost),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        CountdownOrbs.forEach { orb ->
            FloatingCountdownOrb(orb, maxWidth, maxHeight, motionEnabled, visualScale)
        }

        Text(
            text = "RUNSTATE",
            color = Color(0xFF8E9B92),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 5.sp,
            modifier = Modifier.align(Alignment.TopCenter).offset(y = maxHeight * .065f)
        )

        if (visibleDigit != null) {
            val finalCounts = visibleDigit <= 3
            val nominalSize = if (visibleDigit >= 10) 154f else 188f
            // Keep this single, already oversized numeral inside the composition at large font
            // settings; labels and controls still honor the user's ordinary text scaling.
            val numeralSize = with(density) { (nominalSize * visualScale).dp.toSp() }
            Text(
                text = visibleDigit.toString(),
                color = CountdownInk,
                fontFamily = CountdownFont,
                fontSize = numeralSize,
                lineHeight = numeralSize,
                letterSpacing = (-3 * visualScale).sp,
                textAlign = TextAlign.Center,
                style = androidx.compose.ui.text.TextStyle(
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                ),
                modifier = Modifier.align(Alignment.Center)
                    .offset(y = maxHeight * -.069f)
                    .graphicsLayer {
                        val progress = pulse.value
                        scaleX = if (!motionEnabled) .87f else pulseValue(
                            progress,
                            if (finalCounts) .59f else .64f,
                            if (finalCounts) .97f else .90f,
                            if (finalCounts) .90f else .87f,
                            .79f, finalCounts
                        )
                        scaleY = if (!motionEnabled) 1f else pulseValue(
                            progress,
                            if (finalCounts) .68f else .73f,
                            if (finalCounts) 1.10f else 1.03f,
                            if (finalCounts) 1.03f else 1f,
                            .91f, finalCounts
                        )
                        alpha = if (!motionEnabled) 1f else pulseValue(
                            progress, .55f, 1f, 1f, if (finalCounts) .80f else .75f, finalCounts
                        )
                    }.testTag(COUNTDOWN_DIGIT_TAG)
            )
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter)
                .padding(bottom = (maxHeight * .026f).coerceIn(10.dp, 28.dp)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.clip(RoundedCornerShape(44.dp))
                    .clickable(enabled = canStart, role = Role.Button, onClick = onStartNow)
                    .testTag(COUNTDOWN_START_NOW_TAG)
                    .padding(horizontal = 14.dp, vertical = 3.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Canvas(Modifier.size(if (compact) 56.dp else 78.dp)) {
                    drawCircle(Color(0xFF2B422D))
                    drawCircle(Brush.radialGradient(
                        listOf(Color(0xFFD2EA8E).copy(alpha = .13f), Color.Transparent),
                        center = Offset(size.width * .5f, 0f), radius = size.height
                    ))
                    val play = Path().apply {
                        moveTo(size.width * .43f, size.height * .34f)
                        lineTo(size.width * .65f, size.height * .5f)
                        lineTo(size.width * .43f, size.height * .66f)
                        close()
                    }
                    drawPath(play, Color.White)
                }
                Spacer(Modifier.height(7.dp))
                Text("Start now", color = Color(0xFF243326), fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold, letterSpacing = (-.5).sp)
            }
            if (!transitioning) Box(
                modifier = Modifier.widthIn(min = 88.dp).heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .clickable(enabled = canCancel, role = Role.Button, onClick = onCancel),
                contentAlignment = Alignment.Center
            ) {
                Text("Cancel", color = Color(0xFF7A887F), fontSize = 12.sp)
            } else Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
private fun FloatingCountdownOrb(
    orb: CountdownOrb,
    width: Dp,
    height: Dp,
    motionEnabled: Boolean,
    visualScale: Float
) {
    val progress: State<Float> = if (motionEnabled) {
        rememberInfiniteTransition(label = "countdownOrbDrift").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(orb.durationMillis, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
                initialStartOffset = StartOffset(orb.phaseMillis, StartOffsetType.FastForward)
            ),
            label = "countdownOrbPosition"
        )
    } else remember { mutableStateOf(0f) }
    val diameter = width * orb.width
    Image(
        painter = painterResource(orb.resource),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .offset {
                IntOffset((width * orb.x - diameter / 2).roundToPx(),
                    (height * orb.y - diameter / 2).roundToPx())
            }
            .size(diameter)
            .graphicsLayer {
                if (motionEnabled) {
                    val time = progress.value
                    val section = if (time < .45f) DriftEase.transform(time / .45f)
                        else DriftEase.transform((time - .45f) / .55f)
                    translationX = interpolate(
                        if (time < .45f) -.6f else 1f,
                        if (time < .45f) 1f else -.25f, section
                    ) * orb.driftX.dp.toPx() * visualScale
                    translationY = interpolate(
                        if (time < .45f) -.6f else -.2f,
                        if (time < .45f) -.2f else 1f, section
                    ) * orb.driftY.dp.toPx() * visualScale
                    rotationZ = interpolate(
                        if (time < .45f) -.5f else 1f,
                        if (time < .45f) 1f else -.3f, section
                    ) * orb.tilt
                }
                shape = CircleShape
                clip = true
            }
    )
}

private fun pulseValue(
    progress: Float,
    start: Float,
    peak: Float,
    settle: Float,
    end: Float,
    finalCounts: Boolean
): Float {
    val peakTime = if (finalCounts) .24f else .28f
    val settleTime = if (finalCounts) .60f else .62f
    return when {
        progress < peakTime -> interpolate(start, peak, PulseEase.transform(progress / peakTime))
        progress < settleTime -> interpolate(peak, settle,
            PulseEase.transform((progress - peakTime) / (settleTime - peakTime)))
        else -> interpolate(settle, end,
            PulseEase.transform(((progress - settleTime) / (1f - settleTime)).coerceIn(0f, 1f)))
    }
}

private fun interpolate(start: Float, end: Float, progress: Float) = start + (end - start) * progress

@Composable
private fun rememberCountdownMotionEnabled(): Boolean {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var systemMotion by remember { mutableStateOf(ValueAnimator.areAnimatorsEnabled()) }
    var foreground by remember(lifecycle) {
        mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(context, lifecycle) {
        val settingsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                systemMotion = ValueAnimator.areAnimatorsEnabled()
            }
        }
        val lifecycleObserver = LifecycleEventObserver { _, _ ->
            foreground = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, settingsObserver
        )
        lifecycle.addObserver(lifecycleObserver)
        onDispose {
            context.contentResolver.unregisterContentObserver(settingsObserver)
            lifecycle.removeObserver(lifecycleObserver)
        }
    }
    return systemMotion && foreground
}
