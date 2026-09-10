package com.runstate.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.runstate.mobile.ui.theme.RunStateTheme

/**
 * Draws one [RunUiState] and reports taps. It decides nothing.
 *
 * Stateless on purpose, and the purpose is testability as much as tidiness: every screen
 * below can be rendered on an emulator by handing this function a value, with no
 * Application, no coordinator and no database anywhere near it. A screen that reached for
 * the real coordinator to decide what to draw could only be tested by arranging a real
 * database into each of seven situations.
 *
 * It also has no opinion about what a tap means. `onStart` does not begin a countdown here;
 * it tells the caller a button was pressed, and the caller asks the coordinator. That is
 * what keeps the coordinator the single place where a run's rules live.
 *
 * Intentionally plain. This is the first truthful journey, not the Start V7 visual design.
 *
 * @param actionsEnabled false while an action the caller started is still running, which
 *   is how a double-tapped Start or Cancel is stopped from becoming two requests. It is
 *   presentation only — the coordinator refuses an illegal second request regardless.
 */
@Composable
internal fun RunStateScreen(
    uiState: RunUiState,
    onStart: () -> Unit,
    onCancelCountdown: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    actionsEnabled: Boolean = true
) {

    // Back out of a countdown the same way the Cancel button does, because to the runner
    // they are one gesture with two spellings. It is enabled only during COUNTDOWN, so
    // back keeps its ordinary meaning — leaving the app — on every other screen, and it is
    // routed through the same callback so neither path can drift from the other.
    BackHandler(enabled = uiState == RunUiState.Countdown && actionsEnabled) {
        onCancelCountdown()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = messageFor(uiState),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )

        // One action at most, and only where one is honest. Initializing has nothing to
        // offer yet; a live or paused run has no controls in this slice; and a blocked
        // database has nothing the runner can usefully press.
        when (uiState) {
            RunUiState.ReadyToStart -> ScreenAction(
                label = "Start",
                enabled = actionsEnabled,
                onClick = onStart
            )

            RunUiState.Countdown -> ScreenAction(
                label = "Cancel",
                enabled = actionsEnabled,
                onClick = onCancelCountdown
            )

            RunUiState.InitializationFailed -> ScreenAction(
                label = "Try again",
                enabled = actionsEnabled,
                onClick = onRetry
            )

            RunUiState.Initializing,
            RunUiState.ActiveRunning,
            RunUiState.ActivePaused,
            RunUiState.StorageInconsistent -> Unit
        }
    }
}

/** The single button shape every acting screen uses, so they cannot drift apart. */
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

/**
 * What the runner reads on each screen.
 *
 * Deliberately free of UUIDs, exception text, database words and candidate counts. The
 * inconsistent-storage line is the one that most needed writing carefully: it says a run
 * was found that never finished and that RunState will not guess, because that is both
 * true and something the runner can understand, whereas "two active rows" is a sentence
 * about a table.
 */
private fun messageFor(uiState: RunUiState): String = when (uiState) {
    RunUiState.Initializing -> "Getting things ready"

    RunUiState.ReadyToStart -> "Ready when you are"

    RunUiState.Countdown -> "Getting ready to run"

    RunUiState.ActiveRunning -> "You have a run in progress"

    RunUiState.ActivePaused -> "You have a paused run"

    RunUiState.InitializationFailed -> "RunState couldn't get ready."

    RunUiState.StorageInconsistent ->
        "RunState found more than one run that never finished, and won't guess which " +
            "one is yours. Starting a new run is paused until this is sorted out."
}

/**
 * Previews for every screen, built from a plain enum value.
 *
 * None of them touch the Application, the coordinator or the database — which is only
 * possible because [RunStateScreen] takes a value rather than fetching one.
 */
@Preview(showBackground = true, name = "Initializing")
@Composable
private fun InitializingPreview() = PreviewScreen(RunUiState.Initializing)

@Preview(showBackground = true, name = "Ready to start")
@Composable
private fun ReadyToStartPreview() = PreviewScreen(RunUiState.ReadyToStart)

@Preview(showBackground = true, name = "Countdown")
@Composable
private fun CountdownPreview() = PreviewScreen(RunUiState.Countdown)

@Preview(showBackground = true, name = "Active running")
@Composable
private fun ActiveRunningPreview() = PreviewScreen(RunUiState.ActiveRunning)

@Preview(showBackground = true, name = "Active paused")
@Composable
private fun ActivePausedPreview() = PreviewScreen(RunUiState.ActivePaused)

@Preview(showBackground = true, name = "Initialization failed")
@Composable
private fun InitializationFailedPreview() = PreviewScreen(RunUiState.InitializationFailed)

@Preview(showBackground = true, name = "Storage inconsistent")
@Composable
private fun StorageInconsistentPreview() = PreviewScreen(RunUiState.StorageInconsistent)

/** The shared preview wrapper, so each preview above is one line. */
@Composable
private fun PreviewScreen(uiState: RunUiState) {
    RunStateTheme {
        RunStateScreen(
            uiState = uiState,
            onStart = {},
            onCancelCountdown = {},
            onRetry = {}
        )
    }
}
