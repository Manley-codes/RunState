package com.runstate.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.runstate.mobile.ui.theme.RunStateTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** The line shown when a lifecycle write is refused. */
internal const val ACTION_FAILED_MESSAGE = "That didn't save. Try again."

/** How long each countdown digit stays on screen. */
internal const val COUNTDOWN_STEP_MILLIS = 1_000L

/**
 * Draws one [RunUiModel] and reports taps. It decides nothing.
 *
 * Stateless on purpose, and the purpose is testability as much as tidiness: every screen
 * below can be rendered on an emulator by handing this function a value, with no
 * Application, no coordinator and no database anywhere near it.
 *
 * It also has no opinion about what a tap means. `onPause` does not pause anything here; it
 * tells the caller a button was pressed, and the caller asks the coordinator. That is what
 * keeps the coordinator the single place where a run's rules live.
 *
 * Intentionally plain. This is the lifecycle foundation, not the Start V7 visual design.
 *
 * @param countdownDigit the digit to show during [RunUiState.Countdown], or null for none.
 *   Passed separately from the model because it is composition-local timing, not an answer
 *   from the coordinator.
 * @param actionsEnabled false while a request the caller started is still running, which
 *   is how a double tap is stopped from becoming two requests. It is presentation only — the
 *   coordinator refuses or merges a second request regardless.
 */
@Composable
internal fun RunStateScreen(
    model: RunUiModel,
    countdownDigit: Int?,
    onStart: () -> Unit,
    onCancelCountdown: () -> Unit,
    onRetryInitialization: () -> Unit,
    onRetryStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onStartAnother: () -> Unit,
    modifier: Modifier = Modifier,
    actionsEnabled: Boolean = true
) {
    val state = model.state

    // Back leaves a countdown, or a start that failed to save, the same way Cancel does,
    // because to the runner they are one gesture with two spellings. Everywhere else —
    // including while a start is still writing, and on Saved — back keeps its ordinary
    // Android meaning.
    BackHandler(
        enabled = actionsEnabled &&
            (state == RunUiState.Countdown || state == RunUiState.StartFailed)
    ) {
        onCancelCountdown()
    }

    // Live-run controls are off while the caller is still asking, and while the coordinator
    // reports a write still underway — the latter survives a rotation, the former does not.
    val runControlsEnabled = actionsEnabled && !model.actionInProgress

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = messageFor(state),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )

        when (state) {
            RunUiState.ReadyToStart -> ScreenAction(
                label = "Start",
                enabled = actionsEnabled,
                onClick = onStart
            )

            RunUiState.Countdown -> {
                if (countdownDigit != null) {
                    Text(
                        text = countdownDigit.toString(),
                        style = MaterialTheme.typography.displayLarge,
                        modifier = Modifier.padding(top = 24.dp)
                    )
                }

                ScreenAction(
                    label = "Cancel",
                    enabled = actionsEnabled,
                    onClick = onCancelCountdown
                )
            }

            RunUiState.StartFailed -> {
                ScreenAction(
                    label = "Try again",
                    enabled = actionsEnabled,
                    onClick = onRetryStart
                )
                SecondaryAction(
                    label = "Cancel",
                    enabled = actionsEnabled,
                    onClick = onCancelCountdown
                )
            }

            RunUiState.ActiveRunning -> {
                ActionFailure(model.actionFailed)
                ScreenAction(
                    label = "Pause",
                    enabled = runControlsEnabled,
                    onClick = onPause
                )
            }

            RunUiState.ActivePaused -> {
                ActionFailure(model.actionFailed)
                ScreenAction(
                    label = "Resume",
                    enabled = runControlsEnabled,
                    onClick = onResume
                )
                HoldToStopButton(
                    onHoldComplete = onStop,
                    enabled = runControlsEnabled
                )
            }

            RunUiState.Saved -> ScreenAction(
                label = "Start another run",
                enabled = actionsEnabled,
                onClick = onStartAnother
            )

            RunUiState.InitializationFailed -> ScreenAction(
                label = "Try again",
                enabled = actionsEnabled,
                onClick = onRetryInitialization
            )

            // Nothing to press: still getting ready, a start still writing, or a database
            // nothing the runner could press would resolve.
            RunUiState.Initializing,
            RunUiState.StartingRun,
            RunUiState.StorageInconsistent -> Unit
        }
    }
}

/** The primary button shape every acting screen uses, so they cannot drift apart. */
@Composable
private fun ScreenAction(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.padding(top = 24.dp)
    ) {
        Text(text = label)
    }
}

/** A quieter button, for the way out beside a primary action. */
@Composable
private fun SecondaryAction(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.padding(top = 12.dp)
    ) {
        Text(text = label)
    }
}

/** The retry line, shown only when the last lifecycle write was refused. */
@Composable
private fun ActionFailure(failed: Boolean) {
    if (failed) {
        Text(
            text = ACTION_FAILED_MESSAGE,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}

/**
 * What the runner reads on each screen.
 *
 * Deliberately free of UUIDs, exception text, database words and candidate counts.
 */
private fun messageFor(state: RunUiState): String = when (state) {
    RunUiState.Initializing -> "Getting things ready"

    RunUiState.ReadyToStart -> "Ready when you are"

    RunUiState.Countdown -> "Getting ready to run"

    RunUiState.StartingRun -> "Starting your run — saving its start"

    RunUiState.StartFailed -> "RunState couldn't save the start of your run."

    RunUiState.ActiveRunning -> "You have a run in progress"

    RunUiState.ActivePaused -> "You have a paused run"

    RunUiState.Saved -> "Run saved."

    RunUiState.InitializationFailed -> "RunState couldn't get ready."

    RunUiState.StorageInconsistent ->
        "RunState found more than one run that never finished, and won't guess which " +
            "one is yours. Starting a new run is paused until this is sorted out."
}

/**
 * Runs the visible 3, 2, 1 countdown only while [lifecycle] is at least STARTED.
 *
 * Returns the digit to show, or null when none should be. When the last second has passed,
 * [onFinished] is called once, and that is the only moment an official start may be asked
 * for — which is why this is gated on the lifecycle rather than merely on composition. A
 * composition keeps running while the app is in the background; a countdown must not,
 * because a durable start the runner never saw happen is exactly what this prevents.
 *
 * - Dropping below STARTED cancels the countdown and clears the digit.
 * - Returning to STARTED begins again at 3. A countdown is a visual preparation, so there is
 *   nothing meaningful to resume part-way through.
 * - A recreated Activity has a new composition, so it too begins again at 3.
 *
 * [lifecycle] is passed in by the Activity instead of read from `LocalLifecycleOwner`, so no
 * extra lifecycle-compose dependency is needed and a test can hand in a lifecycle it drives.
 *
 * @param active true while the screen is showing an ordinary, unreserved countdown.
 */
@Composable
internal fun rememberLifecycleCountdown(
    active: Boolean,
    lifecycle: Lifecycle,
    onFinished: () -> Unit
): Int? {
    var digit by remember { mutableStateOf<Int?>(null) }
    val currentOnFinished by rememberUpdatedState(onFinished)

    LaunchedEffect(active, lifecycle) {
        digit = null
        if (!active) return@LaunchedEffect

        // Local to this countdown. Once it has finished, returning to STARTED before the
        // screen has moved on must not count down — or ask for a start — a second time.
        var finished = false

        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if (finished) return@repeatOnLifecycle

            try {
                for (step in 3 downTo 1) {
                    digit = step
                    delay(COUNTDOWN_STEP_MILLIS)
                }
            } catch (cancellation: CancellationException) {
                digit = null
                throw cancellation
            }

            finished = true
            currentOnFinished()
        }
    }

    return digit
}

/**
 * Previews for every screen, built from plain values.
 *
 * None of them touch the Application, the coordinator or the database — which is only
 * possible because [RunStateScreen] takes a value rather than fetching one.
 */
@Preview(showBackground = true, name = "Initializing")
@Composable
private fun InitializingPreview() = PreviewScreen(RunUiModel(RunUiState.Initializing))

@Preview(showBackground = true, name = "Ready to start")
@Composable
private fun ReadyToStartPreview() = PreviewScreen(RunUiModel(RunUiState.ReadyToStart))

@Preview(showBackground = true, name = "Countdown")
@Composable
private fun CountdownPreview() = PreviewScreen(RunUiModel(RunUiState.Countdown), digit = 3)

@Preview(showBackground = true, name = "Starting run")
@Composable
private fun StartingRunPreview() = PreviewScreen(RunUiModel(RunUiState.StartingRun))

@Preview(showBackground = true, name = "Start failed")
@Composable
private fun StartFailedPreview() = PreviewScreen(RunUiModel(RunUiState.StartFailed))

@Preview(showBackground = true, name = "Active running")
@Composable
private fun ActiveRunningPreview() = PreviewScreen(RunUiModel(RunUiState.ActiveRunning))

@Preview(showBackground = true, name = "Active paused")
@Composable
private fun ActivePausedPreview() = PreviewScreen(RunUiModel(RunUiState.ActivePaused))

@Preview(showBackground = true, name = "Pause saving")
@Composable
private fun PauseSavingPreview() =
    PreviewScreen(RunUiModel(RunUiState.ActiveRunning, actionInProgress = true))

@Preview(showBackground = true, name = "Action failed")
@Composable
private fun ActionFailedPreview() =
    PreviewScreen(RunUiModel(RunUiState.ActivePaused, actionFailed = true))

@Preview(showBackground = true, name = "Saved")
@Composable
private fun SavedPreview() = PreviewScreen(RunUiModel(RunUiState.Saved))

@Preview(showBackground = true, name = "Initialization failed")
@Composable
private fun InitializationFailedPreview() =
    PreviewScreen(RunUiModel(RunUiState.InitializationFailed))

@Preview(showBackground = true, name = "Storage inconsistent")
@Composable
private fun StorageInconsistentPreview() =
    PreviewScreen(RunUiModel(RunUiState.StorageInconsistent))

/** The shared preview wrapper, so each preview above is one line. */
@Composable
private fun PreviewScreen(model: RunUiModel, digit: Int? = null) {
    RunStateTheme {
        RunStateScreen(
            model = model,
            countdownDigit = digit,
            onStart = {},
            onCancelCountdown = {},
            onRetryInitialization = {},
            onRetryStart = {},
            onPause = {},
            onResume = {},
            onStop = {},
            onStartAnother = {}
        )
    }
}
