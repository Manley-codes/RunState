package com.runstate.mobile.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.runstate.mobile.ui.theme.RunStateTheme
import kotlinx.coroutines.launch

/** Tags the pressable surface, so a test can hold it. */
internal const val HOLD_TO_STOP_TAG = "holdToStop"

/** Tags the fill, so a test can read how far the hold has got. */
internal const val HOLD_TO_STOP_PROGRESS_TAG = "holdToStopProgress"

/** How long the press has to last. Ending a run should never be one stray touch. */
internal const val HOLD_TO_STOP_MILLIS = 1_500

/** The accepted circle uses a solid face and a progress ring only during the hold. */
internal data class HoldToStopCircleColors(val face: Color, val icon: Color, val progress: Color)

/**
 * A control that reports a stop only after one continuous 1.5-second press.
 *
 * It knows nothing about runs. It does not hold a session, call the coordinator or decide
 * whether stopping is allowed; it turns one kind of touch into one callback, and the caller
 * decides what that means. That is what lets it be previewed and tested on its own.
 *
 * ## The gesture
 *
 * One pointer coroutine per press. On the first down the fill starts moving linearly towards
 * full, and the press then waits for release with a timeout of the hold length:
 *
 * - **Released or cancelled first** — lifting early, sliding off, or the system taking the
 *   pointer. The fill snaps back to empty and nothing is reported.
 * - **The timeout wins** — the finger is still down after the full hold. The callback runs
 *   once, and the gesture then only waits for the finger to lift, so holding on longer can
 *   never report a second stop.
 *
 * The timeout and the fill animation both run on the composition's frame clock, which is
 * why a Compose test can drive them together with the test clock instead of real time.
 *
 * @param onHoldComplete called once per completed hold.
 * @param enabled false while the caller's previous request is still being saved. Disabling
 *   removes the gesture outright and empties the fill, so a hold in progress is abandoned.
 */
@Composable
internal fun HoldToStopButton(
    onHoldComplete: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    label: String = "Hold to stop",
    circleColors: HoldToStopCircleColors? = null
) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    // Read at completion time rather than captured when the gesture began, so a recomposed
    // caller's newest callback is the one invoked.
    val currentOnHoldComplete by rememberUpdatedState(onHoldComplete)

    LaunchedEffect(enabled) {
        if (!enabled) {
            progress.snapTo(0f)
        }
    }

    val gesture = if (enabled) {
        Modifier.pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown()

                val fill = scope.launch {
                    progress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(HOLD_TO_STOP_MILLIS, easing = LinearEasing)
                    )
                }

                try {
                    // Non-null only when the press ended before the hold did.
                    val endedEarly = withTimeoutOrNull(HOLD_TO_STOP_MILLIS.toLong()) {
                        waitForUpOrCancellation()
                        true
                    }
                    if (endedEarly == null) {
                        currentOnHoldComplete()
                        waitForUpOrCancellation()
                    }
                } finally {
                    // Also clean up if this control is disabled or removed mid-gesture.
                    fill.cancel()
                    scope.launch { progress.snapTo(0f) }
                }
            }
        }
    } else {
        Modifier
    }

    val control = Modifier
        .alpha(if (enabled) 1f else 0.38f)
        .testTag(HOLD_TO_STOP_TAG)
        .semantics {
            role = Role.Button
            contentDescription = "Hold to finish run"
            if (!enabled) disabled() else {
                // TalkBack exposes this intentional long-press action without requiring
                // the runner to reproduce a timed raw pointer gesture.
                onLongClick(label = "Finish run") {
                    currentOnHoldComplete()
                    true
                }
            }
        }
        .then(gesture)

    if (circleColors != null) {
        Box(modifier.then(control)) {
            Canvas(Modifier.fillMaxSize().testTag(HOLD_TO_STOP_PROGRESS_TAG).semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(progress.value, 0f..1f)
            }) {
                val diameter = size.minDimension
                drawCircle(color = circleColors.face, radius = diameter / 2f)
                val stopSide = diameter * 12f / 44f
                drawRoundRect(color = circleColors.icon,
                    topLeft = Offset((size.width - stopSide) / 2f, (size.height - stopSide) / 2f),
                    size = Size(stopSide, stopSide), cornerRadius = CornerRadius(stopSide / 6f))
                if (progress.value > 0f) {
                    val ringDiameter = diameter * 42f / 44f
                    drawArc(color = circleColors.progress, startAngle = -90f,
                        sweepAngle = 360f * progress.value, useCenter = false,
                        topLeft = Offset((size.width - ringDiameter) / 2f, (size.height - ringDiameter) / 2f),
                        size = Size(ringDiameter, ringDiameter),
                        style = Stroke(width = diameter * 1.7f / 44f, cap = StrokeCap.Round))
                }
            }
        }
        return
    }

    OutlinedCard(
        modifier = modifier.padding(top = 24.dp).widthIn(min = 200.dp).then(control)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = label, style = MaterialTheme.typography.labelLarge)

            LinearProgressIndicator(
                progress = { progress.value },
                modifier = Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth()
                    .testTag(HOLD_TO_STOP_PROGRESS_TAG)
            )
        }
    }
}

@Preview(showBackground = true, name = "Hold to stop")
@Composable
private fun HoldToStopButtonPreview() {
    RunStateTheme {
        HoldToStopButton(onHoldComplete = {}, enabled = true)
    }
}

@Preview(showBackground = true, name = "Hold to stop, disabled")
@Composable
private fun HoldToStopButtonDisabledPreview() {
    RunStateTheme {
        HoldToStopButton(onHoldComplete = {}, enabled = false)
    }
}
