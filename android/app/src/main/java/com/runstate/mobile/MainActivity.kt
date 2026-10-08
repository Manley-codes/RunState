package com.runstate.mobile

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import com.runstate.mobile.reflection.RunReflectionCoordinator
import com.runstate.mobile.reflection.RunReflectionState
import com.runstate.mobile.run.RunActionKind
import com.runstate.mobile.run.RunAdmission
import com.runstate.mobile.run.RunSessionCoordinator
import com.runstate.mobile.ui.RunMetricsDisplay
import com.runstate.mobile.ui.RunMetricsTicker
import com.runstate.mobile.ui.RunCompleteReflectionDisplay
import com.runstate.mobile.ui.RunStateScreen
import com.runstate.mobile.ui.RunUiModel
import com.runstate.mobile.ui.RunUiState
import com.runstate.mobile.ui.rememberLifecycleCountdown
import com.runstate.mobile.ui.runMetricsDisplayFor
import com.runstate.mobile.ui.runUiModelFor
import com.runstate.mobile.ui.theme.RunStateTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.launch

/**
 * The app's one screen, wired to the process-scoped coordinator.
 *
 * The coordinator is taken from [RunStateApplication] rather than built here. An Activity
 * is recreated on every rotation and can exist more than once, so a coordinator owned by
 * one would be a new gate each time — and a second gate guards nothing. Taking the
 * process-scoped one is what makes a run survive rotation: the Activity's memory is
 * thrown away, the coordinator's is not.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        // The existing process-scoped instance. Nothing new is constructed, and no second
        // database is opened.
        val runStateApplication = application as RunStateApplication
        val coordinator = runStateApplication.runSessionCoordinator
        val reflectionCoordinator = runStateApplication.runReflectionCoordinator

        setContent {
            RunStateTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    RunJourneyRoot(
                        coordinator = coordinator,
                        reflectionCoordinator = reflectionCoordinator,

                        // The Activity's own lifecycle gates the countdown, so nothing counts
                        // down — and no run is started — while the app is in the background.
                        lifecycle = lifecycle,
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
 * own is the truth about the run — every answer below comes from the coordinator — nor any
 * durable write: starts and lifecycle actions run in the application scope, and this only
 * waits for them.
 *
 * ## Why nothing is saved across recreation
 *
 * There is no `rememberSaveable` here, and there must not be. A saved [RunUiModel] would be
 * a copy of a decision the coordinator made earlier, restored without asking whether it is
 * still true. On recreation this starts at [RunUiState.Initializing] and asks again. That
 * costs nothing, because [RunSessionCoordinator.initialize] returns immediately after a
 * completed attempt without touching Room.
 *
 * ## Waiting without owning
 *
 * Every durable request follows one shape: ask, refresh at once so the busy state is
 * visible, await the returned work, then refresh again. The await belongs to this
 * composition and is cancelled with it; the work does not and is not. If this composition
 * arrives while work is already underway — a rotation mid-save — it waits for that work
 * through [RunSessionCoordinator.awaitReservedWork] rather than asking for it again.
 */
@Composable
internal fun RunJourneyRoot(
    coordinator: RunSessionCoordinator,
    reflectionCoordinator: RunReflectionCoordinator?,
    lifecycle: Lifecycle,
    modifier: Modifier = Modifier
) {

    // The last answer the coordinator gave, and nothing more. `remember` rather than
    // `rememberSaveable` for the reason above.
    var model by remember { mutableStateOf(RunUiModel(RunUiState.Initializing)) }

    // A formatted answer, not a timer or a run owner. Rotation discards it and asks the
    // process coordinator again, just as it does for the journey model.
    var metrics by remember { mutableStateOf<RunMetricsDisplay?>(null) }

    // Kept only in this stateful adapter. The stateless screen never receives a run UUID.
    var completedRunId by remember { mutableStateOf<String?>(null) }

    val reflectionState = reflectionCoordinator?.let { activeCoordinator ->
        activeCoordinator.state.collectAsState().value
    }
    val context = LocalContext.current
    val latestCompletedRunId by rememberUpdatedState(completedRunId)

    fun localBridgePermissionGranted(): Boolean =
        !BuildConfig.DEBUG ||
            Build.VERSION.SDK_INT < 37 ||
            ContextCompat.checkSelfPermission(
                context,
                LOCAL_NETWORK_PERMISSION
            ) == PackageManager.PERMISSION_GRANTED

    val localNetworkPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        latestCompletedRunId?.let { runId ->
            if (granted) {
                reflectionCoordinator?.startWhenBridgeReady(runId)
            } else {
                reflectionCoordinator?.onBridgeUnavailable(runId)
            }
        }
    }

    // Presentation-only, and not a run state. It exists so a double tap, or a Cancel raced
    // with the countdown reaching zero, cannot become two requests from this screen. The
    // coordinator remains the final backstop against duplicates.
    var actionInFlight by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        val snapshot = coordinator.journeySnapshot()
        model = runUiModelFor(snapshot)
        completedRunId = (snapshot.admission as? RunAdmission.RunCompleted)?.session?.runId
        metrics = coordinator.runMetrics()?.let(::runMetricsDisplayFor)
    }

    // Keyed on the coordinator, so this runs once per composition of this screen and
    // re-runs only if it were ever given a different one. Cancellation when the Activity
    // goes away propagates normally; nothing here is made uncancellable.
    LaunchedEffect(coordinator) {
        val snapshot = coordinator.initialize()
        model = runUiModelFor(snapshot)
        completedRunId = (snapshot.admission as? RunAdmission.RunCompleted)?.session?.runId
        metrics = coordinator.runMetrics()?.let(::runMetricsDisplayFor)
    }

    // This reports visibility; the process coordinator owns the delay, Room read and call.
    LaunchedEffect(model.state, completedRunId, reflectionCoordinator) {
        val activeReflectionCoordinator = reflectionCoordinator ?: return@LaunchedEffect
        if (model.state == RunUiState.Saved) {
            completedRunId?.let { runId ->
                val bridgeReady = localBridgePermissionGranted()
                activeReflectionCoordinator.onSavedVisible(runId, bridgeReady)
                if (!bridgeReady) {
                    localNetworkPermissionLauncher.launch(LOCAL_NETWORK_PERMISSION)
                }
            }
        }
    }

    // Reattaches to durable work this composition did not ask for, such as a start that was
    // still saving when the phone rotated. Harmless when this composition did ask: both
    // waiters simply refresh once the work reports.
    val waitingOnReservedWork = model.state == RunUiState.StartingRun || model.actionInProgress
    LaunchedEffect(waitingOnReservedWork) {
        if (waitingOnReservedWork) {
            coordinator.awaitReservedWork()
            refresh()
        }
    }

    // Live fixture values refresh once per second only while this screen is visible and a
    // run state is settled. During Paused, elapsed keeps growing while active time and
    // distance stay frozen. Saved no longer ticks, and a pending action freezes the last
    // truthful display until storage reports back.
    val metricsTicking = (
        model.state == RunUiState.ActiveRunning || model.state == RunUiState.ActivePaused
    ) && !model.actionInProgress
    RunMetricsTicker(active = metricsTicking, lifecycle = lifecycle) {
        metrics = coordinator.runMetrics()?.let(::runMetricsDisplayFor)
    }

    // Refuse to overlap, run the request, and always release the guard — even if the call
    // throws or this composition is cancelled mid-flight.
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

    // The one shape every durable request takes. Ordinary failures are not thrown on,
    // because the coordinator already records them and the refreshed snapshot shows them;
    // cancellation is rethrown, because it is this composition going away, not a result.
    suspend fun awaitDurable(request: suspend () -> Deferred<*>) {
        try {
            val work = request()
            refresh()
            work.await()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (reportedBySnapshot: Exception) {
            // Deliberately not rethrown: the refresh below shows what the coordinator recorded.
        }
        refresh()
    }

    val countdownDigit = rememberLifecycleCountdown(
        active = model.state == RunUiState.Countdown,
        lifecycle = lifecycle,
        onFinished = {

            // The countdown only reaches here while the Activity is STARTED. If a Cancel is
            // already in flight, the guard drops this start, and the cancel wins.
            runGuarded { awaitDurable { coordinator.requestStart() } }
        }
    )

    val requestAction: (RunActionKind) -> Unit = { kind ->
        runGuarded { awaitDurable { coordinator.requestAction(kind) } }
    }

    val reflectionDisplay = when {
        reflectionCoordinator == null -> null

        model.state != RunUiState.Saved || completedRunId == null ->
            RunCompleteReflectionDisplay.Preparing

        else -> when (val current = reflectionState) {
            is RunReflectionState.Ready -> if (current.runId == completedRunId) {
                RunCompleteReflectionDisplay.Ready(current.text)
            } else {
                RunCompleteReflectionDisplay.Preparing
            }

            is RunReflectionState.Failed -> if (current.runId == completedRunId) {
                RunCompleteReflectionDisplay.Failed
            } else {
                RunCompleteReflectionDisplay.Preparing
            }

            null,
            RunReflectionState.Idle,
            is RunReflectionState.Preparing -> RunCompleteReflectionDisplay.Preparing
        }
    }

    RunStateScreen(
        model = model,
        metrics = metrics,
        countdownDigit = countdownDigit,
        onStart = {
            runGuarded {
                coordinator.beginCountdown()
                refresh()
            }
        },
        onCancelCountdown = {
            runGuarded {
                coordinator.cancelCountdown()
                refresh()
            }
        },
        onRetryInitialization = {

            // Moved back to Initializing before the work starts, so the retry button is
            // gone the instant it is pressed rather than sitting there through the
            // attempt looking unpressed.
            model = RunUiModel(RunUiState.Initializing)
            metrics = null
            runGuarded {
                model = runUiModelFor(coordinator.initialize())
                metrics = coordinator.runMetrics()?.let(::runMetricsDisplayFor)
            }
        },
        onRetryStart = {

            // The same prepared run: the coordinator reuses its UUID and official start.
            runGuarded { awaitDurable { coordinator.requestStart() } }
        },
        onPause = { requestAction(RunActionKind.PAUSE) },
        onResume = { requestAction(RunActionKind.RESUME) },
        onStop = { requestAction(RunActionKind.COMPLETE) },
        onStartAnother = {
            runGuarded {

                // Retires the completed cycle and opens a fresh countdown.
                coordinator.beginCountdown()
                refresh()
            }
        },
        reflection = reflectionDisplay,
        onRetryReflection = {
            completedRunId?.let { runId ->
                if (localBridgePermissionGranted()) {
                    reflectionCoordinator?.retry(runId)
                } else {
                    localNetworkPermissionLauncher.launch(LOCAL_NETWORK_PERMISSION)
                }
            }
        },
        actionsEnabled = !actionInFlight,
        modifier = modifier
    )
}

private const val LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"
