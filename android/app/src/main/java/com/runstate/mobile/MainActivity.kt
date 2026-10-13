package com.runstate.mobile

import android.Manifest
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.runstate.mobile.reflection.RunReflectionCoordinator
import com.runstate.mobile.reflection.RunReflectionState
import com.runstate.mobile.data.local.ReflectionBranch
import com.runstate.mobile.data.local.PreRunEnergy
import com.runstate.mobile.data.local.PostRunEffort
import com.runstate.mobile.data.local.RunTrackingMode
import com.runstate.mobile.data.local.RunStateDatabase
import com.runstate.mobile.run.RunActionKind
import com.runstate.mobile.run.RunAdmission
import com.runstate.mobile.run.RunSessionCoordinator
import com.runstate.mobile.location.RunTrackingService
import com.runstate.mobile.location.RunTrackingServiceState
import com.runstate.mobile.music.RunMusicNotificationListener
import com.runstate.mobile.ui.RunMetricsDisplay
import com.runstate.mobile.ui.RunMetricsTicker
import com.runstate.mobile.ui.RunCompleteReflectionDisplay
import com.runstate.mobile.ui.AndroidReflectionSpeaker
import com.runstate.mobile.ui.ReflectionSpeaker
import com.runstate.mobile.ui.RunHistoryItem
import com.runstate.mobile.ui.RunHistoryScreen
import com.runstate.mobile.ui.RunStateScreen
import com.runstate.mobile.ui.RunUiModel
import com.runstate.mobile.ui.RunUiState
import com.runstate.mobile.ui.rememberLifecycleCountdown
import com.runstate.mobile.ui.runMetricsDisplayFor
import com.runstate.mobile.ui.historyItemFor
import com.runstate.mobile.ui.savedRunReceiptFor
import com.runstate.mobile.ui.gpsRoutePreviewFor
import com.runstate.mobile.ui.runUiModelFor
import com.runstate.mobile.ui.theme.RunStateTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.StateFlow

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
        val database = runStateApplication.database

        setContent {
            RunStateTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    RunJourneyRoot(
                        coordinator = coordinator,
                        reflectionCoordinator = reflectionCoordinator,
                        database = database,
                        trackingFlow = runStateApplication.trackingServiceState,

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
 * The [RunUiModel] is never saved with `rememberSaveable`. Saving that model would keep
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
    database: RunStateDatabase,
    trackingFlow: StateFlow<RunTrackingServiceState>,
    lifecycle: Lifecycle,
    modifier: Modifier = Modifier,
    completionSpeakerFactory: (Context) -> ReflectionSpeaker = { AndroidReflectionSpeaker(it) }
) {

    // The last answer the coordinator gave, and nothing more. `remember` rather than
    // `rememberSaveable` for the reason above.
    var model by remember { mutableStateOf(RunUiModel(RunUiState.Initializing)) }

    // A formatted answer, not a timer or a run owner. Rotation discards it and asks the
    // process coordinator again, just as it does for the journey model.
    var metrics by remember { mutableStateOf<RunMetricsDisplay?>(null) }

    // Kept only in this stateful adapter. The stateless screen never receives a run UUID.
    var completedRunId by remember { mutableStateOf<String?>(null) }
    var activeRunId by remember { mutableStateOf<String?>(null) }
    var activeTrackingMode by remember { mutableStateOf<RunTrackingMode?>(null) }

    // Navigation survives rotation; every saved value still comes from Room.
    var historyVisible by rememberSaveable { mutableStateOf(false) }
    // Only a completion performed in this UI (or its explicit retry) earns arrival speech.
    // Reopening an older Saved run never speaks merely because Room restored READY text.
    var completionSpeechEligibleRunId by rememberSaveable { mutableStateOf<String?>(null) }
    var completionSpeechSpokenRunId by rememberSaveable { mutableStateOf<String?>(null) }
    var completionReceiptRunId by rememberSaveable { mutableStateOf<String?>(null) }
    var completionReceiptText by rememberSaveable { mutableStateOf<String?>(null) }
    var completionReceiptStartedRunId by rememberSaveable { mutableStateOf<String?>(null) }
    var completionReceiptFinishedRunId by rememberSaveable { mutableStateOf<String?>(null) }
    // Retain the sheet's run ID so disposal can release its reflection hold even after
    // navigation has changed completedRunId to null or a different run.
    var effortSheetRunId by remember { mutableStateOf<String?>(null) }
    var effortSavingRunId by remember { mutableStateOf<String?>(null) }
    var effortError by remember { mutableStateOf<Pair<String, String>?>(null) }
    var energySavingRunId by remember { mutableStateOf<String?>(null) }
    var energyError by remember { mutableStateOf<Pair<String, String>?>(null) }
    // This is still a draft choice until the official start writes it on the run row.
    var preRunEnergy by rememberSaveable { mutableStateOf<PreRunEnergy?>(null) }
    var gpsSelected by rememberSaveable { mutableStateOf(false) }
    var gpsMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingGpsResumeId by rememberSaveable { mutableStateOf<String?>(null) }
    var musicSelected by rememberSaveable { mutableStateOf(false) }
    var musicReflectionSharingSelected by rememberSaveable { mutableStateOf(false) }
    var pendingMusicAccess by rememberSaveable { mutableStateOf(false) }
    var historyItems by remember { mutableStateOf<List<RunHistoryItem>>(emptyList()) }
    var historyError by remember { mutableStateOf<String?>(null) }
    BackHandler(historyVisible) { historyVisible = false }

    val completedRuns by remember(database) { database.runDao().observeCompletedRuns() }
        .collectAsState(initial = null)
    val storedReflections by remember(database) { database.reflectionDao().observeAll() }
        .collectAsState(initial = null)

    LaunchedEffect(historyVisible, completedRuns, storedReflections, reflectionCoordinator) {
        if (!historyVisible || completedRuns == null || storedReflections == null) return@LaunchedEffect
        try {
            // Repair older pending rows locally even when Run Complete is no longer visible.
            // This never starts a new provider request after a process restart.
            reflectionCoordinator?.initialize()
            val reflectionsByRun = storedReflections.orEmpty().associateBy { it.runId }
            historyItems = completedRuns.orEmpty().map { run ->
                historyItemFor(
                    run,
                    database.runDao().transitionsFor(run.runId),
                    reflectionsByRun[run.runId],
                    database.musicObservationDao().observationsFor(run.runId)
                )
            }
            historyError = null
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            historyItems = emptyList()
            historyError = "Log History couldn't load. Try opening it again."
        }
    }

    val reflectionState = reflectionCoordinator?.let { activeCoordinator ->
        activeCoordinator.state.collectAsState().value
    }
    val trackingState by trackingFlow.collectAsState()
    val context = LocalContext.current
    var voiceForeground by remember(lifecycle) {
        mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }

    var musicAccessGranted by remember(context) { mutableStateOf(hasMusicAccess(context)) }

    // Notification access is granted in Android Settings, outside this Activity. Recheck
    // on return and do not retain an opt-in after the runner revokes that access.
    DisposableEffect(lifecycle, context) {
        val observer = LifecycleEventObserver { _, event ->
            voiceForeground = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            if (event == Lifecycle.Event.ON_PAUSE) {
                effortSheetRunId?.let { reflectionCoordinator?.setEffortSheetOpen(it, false) }
            }
            if (event == Lifecycle.Event.ON_RESUME) {
                effortSheetRunId?.let { reflectionCoordinator?.setEffortSheetOpen(it, true) }
            }
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = hasMusicAccess(context)
                musicAccessGranted = granted
                if (pendingMusicAccess) {
                    musicSelected = granted
                    pendingMusicAccess = false
                } else if (!granted) {
                    musicSelected = false
                }
                if (!granted) musicReflectionSharingSelected = false
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    var permissionRunId by rememberSaveable { mutableStateOf<String?>(null) }
    var permissionForRetry by rememberSaveable { mutableStateOf(false) }
    val pendingPermissionRunId by rememberUpdatedState(permissionRunId)
    val pendingPermissionForRetry by rememberUpdatedState(permissionForRetry)

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
        pendingPermissionRunId?.let { runId ->
            if (granted) {
                if (pendingPermissionForRetry) reflectionCoordinator?.retry(runId)
                else reflectionCoordinator?.onSavedVisible(runId, bridgeReady = true)
            } else if (!pendingPermissionForRetry) {
                reflectionCoordinator?.onBridgeUnavailable(runId)
            }
        }
        permissionRunId = null
        permissionForRetry = false
    }

    fun preciseLocationReady(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED &&
            context.getSystemService(LocationManager::class.java)
                ?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true

    val gpsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (preciseLocationReady()) {
            val resumeId = pendingGpsResumeId
            if (resumeId == null) {
                gpsSelected = true
                gpsMessage = null
            } else {
                try {
                    RunTrackingService.resumeExisting(context, resumeId)
                    gpsMessage = null
                } catch (_: RuntimeException) {
                    gpsMessage = "Android could not restart GPS tracking. Try again while the app is open."
                }
            }
        } else {
            gpsSelected = false
            gpsMessage = "Precise GPS is unavailable. Enable precise location and phone Location, then try again."
        }
        pendingGpsResumeId = null
    }

    fun requestGpsPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= 33) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }
        gpsPermissionLauncher.launch(permissions.toTypedArray())
    }

    fun retryReflection(runId: String) {
        if (reflectionCoordinator == null) return
        if (!historyVisible && model.state == RunUiState.Saved && completedRunId == runId) {
            completionSpeechEligibleRunId = runId
        }
        if (localBridgePermissionGranted()) reflectionCoordinator.retry(runId)
        else {
            permissionRunId = runId
            permissionForRetry = true
            localNetworkPermissionLauncher.launch(LOCAL_NETWORK_PERMISSION)
        }
    }

    // Presentation-only, and not a run state. It exists so a double tap, or a Cancel raced
    // with the countdown reaching zero, cannot become two requests from this screen. The
    // coordinator remains the final backstop against duplicates.
    var actionInFlight by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    fun saveEffort(runId: String, effort: PostRunEffort?) {
        if (effortSavingRunId != null) return
        effortSavingRunId = runId
        effortError = null
        scope.launch {
            try {
                if (!database.runDao().setPostRunEffort(runId, effort)) {
                    effortError = runId to "Effort couldn't save. Try again."
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                effortError = runId to "Effort couldn't save. Try again."
            } finally {
                effortSavingRunId = null
            }
        }
    }

    fun saveHistoryEnergy(runId: String, branch: ReflectionBranch) {
        val owner = reflectionCoordinator
        if (energySavingRunId != null) return
        energySavingRunId = runId
        energyError = null
        scope.launch {
            try {
                if (owner != null) owner.saveEnergy(runId, branch)
                else database.reflectionDao().recordEnergy(runId, branch, System.currentTimeMillis())
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { energyError = runId to "Energy couldn't save. Try again." }
            finally { energySavingRunId = null }
        }
    }

    suspend fun refresh() {
        val snapshot = coordinator.journeySnapshot()
        model = runUiModelFor(snapshot)
        activeRunId = (snapshot.admission as? RunAdmission.RunInProgress)?.session?.runId
        completedRunId = (snapshot.admission as? RunAdmission.RunCompleted)?.session?.runId
        activeTrackingMode = coordinator.trackingMode()
        metrics = coordinator.runMetrics()?.let(::runMetricsDisplayFor)
    }

    // Keyed on the coordinator, so this runs once per composition of this screen and
    // re-runs only if it were ever given a different one. Cancellation when the Activity
    // goes away propagates normally; nothing here is made uncancellable.
    LaunchedEffect(coordinator) {
        val snapshot = coordinator.initialize()
        model = runUiModelFor(snapshot)
        activeRunId = (snapshot.admission as? RunAdmission.RunInProgress)?.session?.runId
        completedRunId = (snapshot.admission as? RunAdmission.RunCompleted)?.session?.runId
        activeTrackingMode = coordinator.trackingMode()
        metrics = coordinator.runMetrics()?.let(::runMetricsDisplayFor)
    }

    // Service progress survives Activity rotation. Its result prompts a fresh coordinator
    // read; the service state itself never stands in for the durable run row.
    LaunchedEffect(trackingState) {
        when (val service = trackingState) {
            RunTrackingServiceState.Idle,
            RunTrackingServiceState.Starting -> Unit

            is RunTrackingServiceState.Tracking,
            is RunTrackingServiceState.Paused -> {
                gpsMessage = null
                refresh()
            }

            is RunTrackingServiceState.Failed -> {
                gpsMessage = service.reason
                refresh()
                val snapshot = coordinator.journeySnapshot()
                if (snapshot.admission == RunAdmission.CountdownInProgress &&
                    snapshot.startAttemptPhase == com.runstate.mobile.run.StartAttemptPhase.None) {
                    coordinator.cancelCountdown()
                    refresh()
                }
            }
        }
    }

    // This reports visibility; the process coordinator owns the delay, Room read and call.
    LaunchedEffect(model.state, completedRunId, reflectionCoordinator) {
        val activeReflectionCoordinator = reflectionCoordinator ?: return@LaunchedEffect
        if (model.state == RunUiState.Saved) {
            completedRunId?.let { runId ->
                val bridgeReady = localBridgePermissionGranted()
                activeReflectionCoordinator.onSavedVisible(runId, bridgeReady,
                    awaitingReceipt = completionReceiptRunId == runId &&
                        completionReceiptFinishedRunId != runId)
                if (!bridgeReady) {
                    permissionRunId = runId
                    permissionForRetry = false
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
    suspend fun awaitDurable(
        onSuccess: suspend () -> Unit = {},
        request: suspend () -> Deferred<*>
    ) {
        try {
            val work = request()
            refresh()
            work.await()
            onSuccess()
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
            val observeMusicForRun = musicSelected && hasMusicAccess(context)
            if (musicSelected && !observeMusicForRun) {
                musicSelected = false
                musicAccessGranted = false
                musicReflectionSharingSelected = false
            }
            val shareMusicForRun = observeMusicForRun && reflectionCoordinator != null &&
                musicReflectionSharingSelected
            if (gpsSelected) {
                if (!preciseLocationReady()) {
                    gpsMessage = "Precise GPS is unavailable. Enable phone Location before starting."
                    runGuarded {
                        coordinator.cancelCountdown()
                        refresh()
                    }
                } else {
                    try {
                        // The visible Activity launches the foreground service before any
                        // asynchronous Room insert; that service owns this GPS start.
                        RunTrackingService.startNew(context, preRunEnergy, observeMusicForRun,
                            shareMusicForRun)
                    } catch (_: RuntimeException) {
                        gpsMessage = "Android could not start GPS tracking. Try again while the app is open."
                        runGuarded {
                            coordinator.cancelCountdown()
                            refresh()
                        }
                    }
                }
            } else {
                runGuarded {
                    awaitDurable {
                        coordinator.requestStart(
                            preRunEnergy,
                            musicObservationRequested = observeMusicForRun,
                            musicReflectionSharingAllowed = shareMusicForRun
                        )
                    }
                }
            }
        }
    )

    val requestAction: (RunActionKind) -> Unit = { kind ->
        runGuarded {
            if (kind == RunActionKind.COMPLETE) {
                activeRunId?.let { id ->
                    // Reserve audible delivery before even a fast completion can expose Saved.
                    // The receipt itself still waits for the durable COMPLETED row.
                    completionSpeechEligibleRunId = id
                    completionReceiptRunId = id
                    completionReceiptText = null
                    completionReceiptStartedRunId = null
                    completionReceiptFinishedRunId = null
                }
            }
            awaitDurable(onSuccess = {
                if (kind == RunActionKind.COMPLETE) {
                    val saved = coordinator.journeySnapshot().admission as? RunAdmission.RunCompleted
                    saved?.session?.runId?.let { id ->
                        // Mark receipt ownership before refreshing Saved and launching reflection.
                        completionSpeechEligibleRunId = id
                        completionReceiptRunId = id
                        completionReceiptText = try {
                            database.runDao().findById(id)?.let(::savedRunReceiptFor)
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { null } ?: "Run saved."
                    }
                }
            }) { coordinator.requestAction(kind) }
        }
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

    val completionVisible = model.state == RunUiState.Saved &&
        !historyVisible && voiceForeground
    RunCompleteReceiptEffect(
        completedRunId = completedRunId,
        receiptRunId = completionReceiptRunId,
        receiptText = completionReceiptText ?: completedRuns?.firstOrNull {
            it.runId == completionReceiptRunId
        }?.let(::savedRunReceiptFor),
        startedRunId = completionReceiptStartedRunId,
        finishedRunId = completionReceiptFinishedRunId,
        completionVisible = completionVisible,
        speakerFactory = completionSpeakerFactory,
        onStarted = { completionReceiptStartedRunId = it },
        onFinished = { id ->
            if (completionReceiptRunId == id) completionReceiptFinishedRunId = id
            reflectionCoordinator?.finishReceipt(id)
        }
    )
    RunCompleteSpeechEffect(
        completedRunId = completedRunId,
        eligibleRunId = completionSpeechEligibleRunId,
        spokenRunId = completionSpeechSpokenRunId,
        reflectionState = reflectionState,
        completionVisible = completionVisible && (completionReceiptRunId != completedRunId ||
            completionReceiptFinishedRunId == completedRunId),
        speakerFactory = completionSpeakerFactory,
        onSpoken = { completionSpeechSpokenRunId = it }
    )

    if (historyVisible) {
        RunHistoryScreen(
            items = historyItems,
            onBack = { historyVisible = false },
            onRetryReflection = ::retryReflection,
            onSelectEffort = ::saveEffort,
            effortSavingRunId = effortSavingRunId,
            effortError = effortError,
            onSelectEnergy = ::saveHistoryEnergy,
            energySavingRunId = energySavingRunId,
            energyError = energyError,
            routeLoader = { runId ->
                val points = database.locationDao().pointsFor(runId)
                withContext(Dispatchers.Default) { gpsRoutePreviewFor(runId, points) }
            },
            isLoading = completedRuns == null || storedReflections == null,
            error = historyError,
            modifier = modifier
        )
        return
    }

    val activeReflection = reflectionState?.takeIf { current ->
        when (current) {
            is RunReflectionState.Preparing -> current.runId == completedRunId
            is RunReflectionState.Ready -> current.runId == completedRunId
            is RunReflectionState.Failed -> current.runId == completedRunId
            RunReflectionState.Idle -> false
        }
    }
    val selectedEnergy: ReflectionBranch? = when (activeReflection) {
        is RunReflectionState.Preparing -> activeReflection.energy
        is RunReflectionState.Ready -> activeReflection.energy
        is RunReflectionState.Failed -> activeReflection.energy
        else -> null
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
            val observeMusicForRun = musicSelected && hasMusicAccess(context)
            val shareMusicForRun = observeMusicForRun && reflectionCoordinator != null &&
                musicReflectionSharingSelected
            if (gpsSelected) {
                if (preciseLocationReady()) {
                    try {
                        RunTrackingService.startNew(
                            context,
                            preRunEnergy,
                            observeMusicForRun,
                            shareMusicForRun
                        )
                    } catch (_: RuntimeException) {
                        gpsMessage = "Android could not restart GPS. Cancel and try again."
                    }
                } else {
                    gpsMessage = "Precise GPS is unavailable. Cancel to return to Start."
                }
            } else {
                runGuarded {
                    awaitDurable {
                        coordinator.requestStart(
                            preRunEnergy,
                            musicObservationRequested = observeMusicForRun,
                            musicReflectionSharingAllowed = shareMusicForRun
                        )
                    }
                }
            }
        },
        onPause = { requestAction(RunActionKind.PAUSE) },
        onResume = { requestAction(RunActionKind.RESUME) },
        onStop = { requestAction(RunActionKind.COMPLETE) },
        onStartAnother = {
            runGuarded {

                // Reset per-run answers; keep the explicit GPS choice for this app session.
                coordinator.prepareAnotherRun()
                preRunEnergy = null
                gpsMessage = null
                musicSelected = false
                musicReflectionSharingSelected = false
                refresh()
            }
        },
        reflection = reflectionDisplay,
        onRetryReflection = {
            completedRunId?.let(::retryReflection)
        },
        selectedEnergy = selectedEnergy,
        selectedPreRunEnergy = preRunEnergy,
        onSelectPreRunEnergy = { preRunEnergy = it },
        gpsSelected = gpsSelected,
        onToggleGps = {
            if (gpsSelected) {
                gpsSelected = false
                gpsMessage = null
            } else if (preciseLocationReady()) {
                gpsSelected = true
                gpsMessage = null
            } else {
                requestGpsPermissions()
            }
        },
        gpsMessage = gpsMessage,
        musicSelected = musicSelected,
        musicAccessGranted = musicAccessGranted,
        musicReflectionSharingAvailable = reflectionCoordinator != null,
        musicReflectionSharingSelected = musicReflectionSharingSelected,
        onToggleMusicReflectionSharing = {
            musicReflectionSharingSelected = musicSelected && hasMusicAccess(context) &&
                reflectionCoordinator != null && !musicReflectionSharingSelected
        },
        onToggleMusic = {
            if (musicSelected) {
                musicSelected = false
                musicReflectionSharingSelected = false
            } else if (hasMusicAccess(context)) {
                musicAccessGranted = true
                musicSelected = true
            } else {
                musicAccessGranted = false
                musicReflectionSharingSelected = false
                pendingMusicAccess = openMusicAccessSettings(context)
            }
        },
        isGpsRun = activeTrackingMode == RunTrackingMode.GPS,
        gpsServicePresent = when (val service = trackingState) {
            is RunTrackingServiceState.Tracking -> service.runId == activeRunId
            is RunTrackingServiceState.Paused -> service.runId == activeRunId
            else -> false
        },
        onResumeGpsTracking = {
            activeRunId?.let { id ->
                if (preciseLocationReady()) {
                    try {
                        RunTrackingService.resumeExisting(context, id)
                        gpsMessage = null
                    } catch (_: RuntimeException) {
                        gpsMessage = "Android could not restart GPS tracking. Try again while the app is open."
                    }
                } else {
                    pendingGpsResumeId = id
                    requestGpsPermissions()
                }
            }
        },
        onSelectEnergy = { branch ->
            completedRunId?.let { reflectionCoordinator?.selectEnergy(it, branch) }
        },
        selectedEffort = completedRuns?.firstOrNull { it.runId == completedRunId }?.postRunEffort,
        onSelectEffort = { effort -> completedRunId?.let { saveEffort(it, effort) } },
        effortEnabled = completedRunId != null,
        effortSaving = effortSavingRunId == completedRunId,
        effortError = effortError?.takeIf { it.first == completedRunId }?.second,
        onEffortSheetOpenChange = { open ->
            if (open) {
                completedRunId?.let { runId ->
                    effortSheetRunId = runId
                    reflectionCoordinator?.setEffortSheetOpen(runId, true)
                }
            } else {
                val heldRunId = effortSheetRunId ?: completedRunId
                effortSheetRunId = null
                heldRunId?.let { reflectionCoordinator?.setEffortSheetOpen(it, false) }
            }
        },
        energyEnabled = reflectionCoordinator != null,
        isTestData = (activeReflection as? RunReflectionState.Ready)?.isTestData == true,
        onViewHistory = { historyVisible = true },
        showHistory = true,
        actionsEnabled = !actionInFlight,
        modifier = modifier
    )
}

/** Consume the saved receipt once; an interrupted receipt releases delivery without replay. */
@Composable
internal fun RunCompleteReceiptEffect(
    completedRunId: String?,
    receiptRunId: String?,
    receiptText: String?,
    startedRunId: String?,
    finishedRunId: String?,
    completionVisible: Boolean,
    speakerFactory: (Context) -> ReflectionSpeaker,
    onStarted: (String) -> Unit,
    onFinished: (String) -> Unit
) {
    val context = LocalContext.current
    var speaker by remember { mutableStateOf<ReflectionSpeaker?>(null) }
    var activeReceiptRunId by remember { mutableStateOf<String?>(null) }
    var locallyFinishedRunId by remember { mutableStateOf<String?>(null) }
    val finishCallback by rememberUpdatedState(onFinished)

    fun finish(id: String) {
        if (locallyFinishedRunId == id) return
        locallyFinishedRunId = id
        activeReceiptRunId = null
        speaker?.close()
        speaker = null
        finishCallback(id)
    }

    DisposableEffect(Unit) {
        onDispose {
            val interruptedId = activeReceiptRunId
            speaker?.close()
            interruptedId?.let(::finish)
        }
    }
    LaunchedEffect(completedRunId, receiptRunId, receiptText, finishedRunId, completionVisible) {
        activeReceiptRunId?.takeIf { it != completedRunId }?.let(::finish)
        val pendingId = receiptRunId?.takeIf { it != finishedRunId }
        if (pendingId == null || completedRunId != pendingId) {
            speaker?.stop()
            return@LaunchedEffect
        }
        if (!completionVisible) {
            speaker?.stop()
            finish(pendingId)
            return@LaunchedEffect
        }
        if (startedRunId == pendingId) {
            // A restored consumed receipt was interrupted by recreation; never repeat it.
            if (activeReceiptRunId == null) finish(pendingId)
            return@LaunchedEffect
        }
        val text = receiptText ?: return@LaunchedEffect
        activeReceiptRunId = pendingId
        onStarted(pendingId)
        try {
            val activeSpeaker = speakerFactory(context).also { speaker = it }
            activeSpeaker.speakReceipt(text) {
                if (activeReceiptRunId == pendingId) finish(pendingId)
            }
        } catch (_: RuntimeException) {
            finish(pendingId)
        }
    }
}

/** Keep speech outside the stateless screen; leaving, backgrounding or rotation closes its engine. */
@Composable
internal fun RunCompleteSpeechEffect(
    completedRunId: String?,
    eligibleRunId: String?,
    spokenRunId: String?,
    reflectionState: RunReflectionState?,
    completionVisible: Boolean,
    speakerFactory: (Context) -> ReflectionSpeaker,
    onSpoken: (String) -> Unit
) {
    val context = LocalContext.current
    var speaker by remember { mutableStateOf<ReflectionSpeaker?>(null) }

    DisposableEffect(Unit) {
        onDispose { speaker?.close() }
    }
    LaunchedEffect(completedRunId, eligibleRunId, spokenRunId, reflectionState, completionVisible) {
        if (!completionVisible) {
            speaker?.stop()
            return@LaunchedEffect
        }
        val exactText = readyCompletionSpeechText(
            completedRunId, eligibleRunId, spokenRunId, reflectionState
        ) ?: return@LaunchedEffect
        val activeSpeaker = speaker ?: speakerFactory(context).also { speaker = it }
        activeSpeaker.speak(exactText)
        onSpoken(requireNotNull(completedRunId))
    }
}

/** Speak only a newly completed run's exact selected text, once, after it becomes READY. */
internal fun readyCompletionSpeechText(
    completedRunId: String?,
    eligibleRunId: String?,
    spokenRunId: String?,
    reflectionState: RunReflectionState?
): String? {
    val ready = reflectionState as? RunReflectionState.Ready ?: return null
    return ready.text.takeIf {
        completedRunId != null && completedRunId == eligibleRunId &&
            ready.runId == completedRunId && spokenRunId != completedRunId
    }
}

private const val LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

private fun musicListenerComponent(context: Context): ComponentName =
    ComponentName(context, RunMusicNotificationListener::class.java)

private fun hasMusicAccess(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= 27) {
        context.getSystemService(NotificationManager::class.java)
            ?.isNotificationListenerAccessGranted(musicListenerComponent(context)) == true
    } else {
        NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)
    }

private fun openMusicAccessSettings(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= 30) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                    .putExtra(
                        Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                        musicListenerComponent(context).flattenToString()
                    )
            )
            return true
        } catch (_: ActivityNotFoundException) {
            // Some Android builds omit the per-app page; use the general list below.
        } catch (_: SecurityException) {
            // The general list is still worth trying.
        }
    }
    return try {
        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
