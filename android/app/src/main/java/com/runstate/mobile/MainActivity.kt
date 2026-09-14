package com.runstate.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.runstate.mobile.run.RunSessionCoordinator
import com.runstate.mobile.ui.RunStateScreen
import com.runstate.mobile.ui.RunUiState
import com.runstate.mobile.ui.runUiStateFor
import com.runstate.mobile.ui.theme.RunStateTheme
import kotlinx.coroutines.launch

/**
 * The app's one screen, wired to the process-scoped coordinator.
 *
 * The coordinator is taken from [RunStateApplication] rather than built here. An Activity
 * is recreated on every rotation and can exist more than once, so a coordinator owned by
 * one would be a new gate each time — and a second gate guards nothing. Taking the
 * process-scoped one is what makes a countdown survive rotation: the Activity's memory is
 * thrown away, the coordinator's is not.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        // The existing process-scoped instance. Nothing new is constructed, and no second
        // database is opened.
        val coordinator = (application as RunStateApplication).runSessionCoordinator

        setContent {
            RunStateTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    RunJourneyRoot(
                        coordinator = coordinator,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

/**
 * Asks the coordinator what is true, and hands the answer to a screen that only draws.
 *
 * This is the stateful half of the split. It owns the coroutine work and the presentation
 * state; [RunStateScreen] owns neither and decides nothing. What it deliberately does not
 * own is the truth about the run — every answer below comes from the coordinator, and the
 * only thing kept here is the most recent one.
 *
 * ## Why nothing is saved across recreation
 *
 * There is no `rememberSaveable` here, and there must not be. A saved [RunUiState] would
 * be a copy of a decision the coordinator made earlier, restored without asking whether it
 * is still true — so a run completed by something else, or an inconsistency that appeared
 * since, would be painted over with a stale screen. On recreation this starts at
 * [RunUiState.Initializing] and asks again. That costs nothing, because [
 * RunSessionCoordinator.initialize] returns immediately after a completed attempt without
 * touching Room.
 *
 * ## Which read each path uses
 *
 * First load and Try Again call [RunSessionCoordinator.initialize], because both genuinely
 * mean "establish what storage holds" — and after a failure, retrying is the point. After
 * Start or Cancel, recovery is already settled, so the screen uses
 * [RunSessionCoordinator.journeySnapshot], which only reads. Either way the screen receives
 * one snapshot taken under one lock, never a status and an admission read separately.
 */
@Composable
private fun RunJourneyRoot(
    coordinator: RunSessionCoordinator,
    modifier: Modifier = Modifier
) {

    // The last answer the coordinator gave, and nothing more. `remember` rather than
    // `rememberSaveable` for the reason above.
    var uiState by remember { mutableStateOf(RunUiState.Initializing) }

    // Presentation-only, and not a run state. It exists so a double-tapped Start or a
    // Cancel raced with the back gesture cannot become two requests. The coordinator would
    // refuse the second one anyway; this stops the screen from asking.
    var actionInFlight by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    // Keyed on the coordinator, so this runs once per composition of this screen and
    // re-runs only if it were ever given a different one. Cancellation when the Activity
    // goes away propagates normally; nothing here is made uncancellable.
    LaunchedEffect(coordinator) {
        uiState = runUiStateFor(coordinator.initialize())
    }

    // Every action follows the same shape: refuse to overlap, do the coordinator call,
    // then re-read. The `finally` releases the guard even if the call throws or the
    // composition is cancelled mid-flight.
    val runGuarded: (suspend () -> Unit) -> Unit = { action ->
        if (!actionInFlight) {
            actionInFlight = true
            scope.launch {
                try {
                    action()
                } finally {
                    actionInFlight = false
                }
            }
        }
    }

    RunStateScreen(
        uiState = uiState,
        onStart = {
            runGuarded {

                // Entering the countdown is the whole of Start in this slice. No run is
                // made official: no UUID, no timestamps, no row, and no call to
                // `coordinator.requestStart`.
                coordinator.beginCountdown()
                uiState = runUiStateFor(coordinator.journeySnapshot())
            }
        },
        onCancelCountdown = {
            runGuarded {
                coordinator.cancelCountdown()
                uiState = runUiStateFor(coordinator.journeySnapshot())
            }
        },
        onRetry = {

            // Moved back to Initializing before the work starts, so the retry button is
            // gone the instant it is pressed rather than sitting there through the
            // attempt looking unpressed.
            uiState = RunUiState.Initializing
            runGuarded {
                uiState = runUiStateFor(coordinator.initialize())
            }
        },
        actionsEnabled = !actionInFlight,
        modifier = modifier
    )
}
