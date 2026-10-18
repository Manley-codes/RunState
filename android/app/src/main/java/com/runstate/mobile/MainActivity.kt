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
import android.widget.Toast
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.runstate.mobile.reflection.RunReflectionCoordinator
import com.runstate.mobile.reflection.RunReflectionState
import com.runstate.mobile.reflection.ReflectionVoicePreparation
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
import com.runstate.mobile.location.GpsSignalStatus
import com.runstate.mobile.location.GpsMapEvidence
import com.runstate.mobile.location.rememberForegroundGpsPreview
import com.runstate.mobile.location.ForegroundGpsPreviewStatus
import com.runstate.mobile.location.ForegroundGpsPreviewSource
import com.runstate.mobile.music.RunMusicNotificationListener
import com.runstate.mobile.ui.RunMetricsDisplay
import com.runstate.mobile.ui.RunMetricsTicker
import com.runstate.mobile.ui.RunCompleteReflectionDisplay
import com.runstate.mobile.ui.AndroidReflectionSpeaker
import com.runstate.mobile.ui.ReflectionSpeaker
import com.runstate.mobile.ui.RunHistoryItem
import com.runstate.mobile.ui.RunHistoryScreen
import com.runstate.mobile.ui.RunStateScreen
import com.runstate.mobile.ui.ShortRunDecisionDialog
import com.runstate.mobile.ui.RunSettingsDialog
import com.runstate.mobile.settings.RunPreferences
import com.runstate.mobile.ui.ReflectionConnectionDialog
import com.runstate.mobile.ui.RunUiModel
import com.runstate.mobile.ui.RunUiState
import com.runstate.mobile.ui.rememberLifecycleCountdown
import com.runstate.mobile.ui.runMetricsDisplayFor
import com.runstate.mobile.ui.historyItemWithOptionalMusic
import com.runstate.mobile.ui.weatherSummaryFor
import com.runstate.mobile.ui.completionDateLabelFor
import com.runstate.mobile.ui.savedRunReceiptFor
import com.runstate.mobile.ui.gpsRoutePreviewFor
import com.runstate.mobile.ui.historySplitsFor
import com.runstate.mobile.ui.runUiModelFor
import com.runstate.mobile.ui.theme.RunStateTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch

/**
 * The app's one screen, wired to the process-scoped coordinator.
 *
 * The coordinator is taken from [RunStateApplication] rather than built here. An Activity
 * is recreated on every rotation and can exist more than once, so a coordinator owned by
 * one would be a new gate each time — and a second gate guards nothing. Taking the
 * process-scoped one is what makes a run survive rotation: the Activity's memory is
 * thrown away, the coordinator's is not.
 */
private fun preciseGpsLocationReady(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED &&
        context.getSystemService(LocationManager::class.java)
            ?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        // The existing process-scoped instance. Nothing new is constructed, and no second
        // database is opened.
        val runStateApplication = application as RunStateApplication
        val coordinator = runStateApplication.runSessionCoordinator
        val database = runStateApplication.database
        runStateApplication.initializeReflectionConnection()
        runStateApplication.initializeRunPreferences()
        runStateApplication.initializeRunSplits()

        setContent {
            RunStateTheme {
                val connection by runStateApplication.reflectionConnection.collectAsState()
                val connectionSaving by runStateApplication.reflectionConnectionSaving.collectAsState()
                val runPreferences by runStateApplication.runPreferences.collectAsState()
                val preferencesSaving by runStateApplication.runPreferencesSaving.collectAsState()
                val preferencesError by runStateApplication.runPreferencesError.collectAsState()
                val reflectionCoordinator = runStateApplication.runReflectionCoordinator
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    RunJourneyRoot(
                        coordinator = coordinator,
                        reflectionCoordinator = reflectionCoordinator,
                        database = database,
                        trackingFlow = runStateApplication.trackingServiceState,
                        reflectionConnection = connection,
                        reflectionConnectionSaving = connectionSaving,
                        onSaveReflectionConnection = runStateApplication::saveReflectionConnection,
                        onClearReflectionConnection = { runStateApplication.clearReflectionConnection() },
                        runPreferences = runPreferences,
                        runPreferencesSaving = preferencesSaving,
                        runPreferencesError = preferencesError,
                        onSaveRunPreferences = runStateApplication::saveRunPreferences,
                        onRetryRunPreferences = runStateApplication::initializeRunPreferences,
                        runSettingsSessionId = runStateApplication.runSettingsSessionId,
                        nativeMapsEnabled = true,
                        onRetrySplits = runStateApplication::retryRunSplits,

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
    completionSpeakerFactory: (Context) -> ReflectionSpeaker = { AndroidReflectionSpeaker(it) },
    reflectionConnection: ReflectionConnectionState = ReflectionConnectionState.Loading,
    reflectionConnectionSaving: Boolean = false,
    onSaveReflectionConnection: suspend (String, String) -> Unit = { _, _ -> },
    onClearReflectionConnection: suspend () -> Unit = {},
    runPreferences: RunPreferences? = RunPreferences(musicSetupComplete = true),
    runPreferencesSaving: Boolean = false,
    runPreferencesError: String? = null,
    onSaveRunPreferences: suspend (RunPreferences) -> Unit = {},
    onRetryRunPreferences: () -> Unit = {},
    runSettingsSessionId: String = "isolated-preview",
    musicAccessProvider: (Context) -> Boolean = ::hasMusicAccess,
    nativeMapsEnabled: Boolean = false,
    onRetrySplits: suspend (String) -> Unit = {},
    gpsPreviewSourceOverride: ForegroundGpsPreviewSource? = null,
    gpsLocationReady: (Context) -> Boolean = ::preciseGpsLocationReady,
    startGpsRun: (Context, PreRunEnergy?, Boolean, Boolean) -> Unit = RunTrackingService::startNew
) {

    val reflectionRequestsAvailable = reflectionConnection is ReflectionConnectionState.Hosted ||
        reflectionConnection == ReflectionConnectionState.Local

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
    var mapRunId by rememberSaveable { mutableStateOf<String?>(null) }
    var mapEvidence by remember { mutableStateOf<GpsMapEvidence?>(null) }

    // Navigation survives rotation; every saved value still comes from Room.
    var historyVisible by rememberSaveable { mutableStateOf(false) }
    var shortRunDecisionId by rememberSaveable { mutableStateOf<String?>(null) }
    var shortRunDecisionError by rememberSaveable { mutableStateOf<String?>(null) }
    // Only a completion performed in this UI (or its explicit retry) earns arrival speech.
    // Reopening an older Saved run never speaks merely because Room restored READY text.
    var completionSpeechEligibleRunId by rememberSaveable { mutableStateOf<String?>(null) }
    var completionSpeechSpokenRunId by rememberSaveable { mutableStateOf<String?>(null) }
    var completionAudioActive by remember { mutableStateOf(false) }
    var completionPresentationHeldRunId by remember { mutableStateOf<String?>(null) }
    var stopCompletionAudio by remember { mutableStateOf<(() -> Unit)?>(null) }
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
    // GPS is the normal path. An explicit Sample demo is a temporary draft, never a saved default.
    val gpsModeSaver = remember(runSettingsSessionId) {
        Saver<Boolean, Any>(
            save = { listOf(runSettingsSessionId, it) },
            restore = { raw ->
                val draft = raw as? List<*>
                if (draft?.getOrNull(0) == runSettingsSessionId) draft.getOrNull(1) as? Boolean ?: true
                else true
            }
        )
    }
    var gpsSelected by rememberSaveable(runSettingsSessionId, stateSaver = gpsModeSaver) {
        mutableStateOf(true)
    }
    var gpsMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingGpsResumeId by rememberSaveable { mutableStateOf<String?>(null) }
    // An unsaved, foreground-only intention. Recreation must never restore permission to start.
    var gpsStartRequested by remember { mutableStateOf(false) }
    // This remembers only an already dispatched service handoff across Activity recreation.
    // It cannot authorize a new start, and a different application process discards it.
    val gpsDispatchSaver = remember(runSettingsSessionId) {
        Saver<Boolean, Any>(save = { listOf(runSettingsSessionId, it) }, restore = { raw ->
            val saved = raw as? List<*>
            if (saved?.getOrNull(0) == runSettingsSessionId) saved.getOrNull(1) as? Boolean ?: false
            else false
        })
    }
    var gpsStartDispatched by rememberSaveable(runSettingsSessionId, stateSaver = gpsDispatchSaver) {
        mutableStateOf(false)
    }
    var gpsStartAttempt by remember { mutableStateOf(0L) }
    var gpsPreviewRestart by remember { mutableStateOf(0L) }
    val musicSelected = runPreferences?.observeMusic == true
    val musicReflectionSharingSelected = runPreferences?.shareMusicInReflection == true
    var runSettingsVisible by rememberSaveable { mutableStateOf(false) }
    var preferenceWritePending by remember { mutableStateOf(false) }
    var preferenceWriteError by remember { mutableStateOf<String?>(null) }
    var historyItems by remember { mutableStateOf<List<RunHistoryItem>>(emptyList()) }
    var historyError by remember { mutableStateOf<String?>(null) }
    var connectionDialogVisible by remember { mutableStateOf(false) }
    var connectionSaving by remember { mutableStateOf(false) }
    var connectionError by remember { mutableStateOf<String?>(null) }
    BackHandler(historyVisible) { historyVisible = false }

    val completedRuns by remember(database) { database.runDao().observeCompletedRuns() }
        .collectAsState(initial = null)
    val storedReflections by remember(database) { database.reflectionDao().observeAll() }
        .collectAsState(initial = null)
    val storedWeather by remember(database) {
        database.weatherDao().observeAll().catch { emit(emptyList()) }
    }.collectAsState(initial = emptyList())
    val weatherByRun = remember(storedWeather) { storedWeather.associateBy { it.runId } }
    val storedSplits by remember(database) {
        database.splitDao().observeAllSnapshots().catch { emit(emptyList()) }
    }.collectAsState(initial = emptyList())
    val splitsByRun = remember(storedSplits) { storedSplits.associateBy { it.runId } }

    LaunchedEffect(historyVisible, completedRuns, storedReflections, storedWeather, storedSplits, reflectionCoordinator) {
        if (!historyVisible || completedRuns == null || storedReflections == null) return@LaunchedEffect
        try {
            // Repair older pending rows locally even when Run Complete is no longer visible.
            // This never starts a new provider request after a process restart.
            reflectionCoordinator?.initialize()
            val reflectionsByRun = storedReflections.orEmpty().associateBy { it.runId }
            historyItems = completedRuns.orEmpty().map { run ->
                historyItemWithOptionalMusic(
                    run,
                    database.runDao().transitionsFor(run.runId),
                    reflectionsByRun[run.runId]
                ) { database.musicObservationDao().observationsFor(run.runId) }
                    .copy(weatherSummary = weatherSummaryFor(run, weatherByRun[run.runId]),
                        splitStatus = splitsByRun[run.runId]?.status)
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
    val voicePreparation = reflectionCoordinator?.let { activeCoordinator ->
        activeCoordinator.voicePreparation.collectAsState().value
    }
    val trackingState by trackingFlow.collectAsState()
    val activeMapAvailable = !historyVisible && activeTrackingMode == RunTrackingMode.GPS &&
        (model.state == RunUiState.ActiveRunning || model.state == RunUiState.ActivePaused)
    val mapVisible = mapRunId != null && (mapRunId == activeRunId && activeMapAvailable ||
        mapRunId == "foreground-gps-preview" && !historyVisible && gpsSelected &&
        model.state == RunUiState.ReadyToStart)
    val activeMapVisible = mapVisible && activeMapAvailable
    val mapSignal = (trackingState as? RunTrackingServiceState.Tracking)
        ?.takeIf { it.runId == activeRunId }?.signalStatus
    LaunchedEffect(activeMapVisible, activeRunId, model.state, mapSignal) {
        if (!activeMapVisible) mapEvidence = null
        else {
            val requestedId = activeRunId
            mapEvidence = coordinator.runMapEvidence(mapSignal)?.takeIf { it.runId == requestedId }
        }
    }
    val previewEnabled = gpsSelected && !gpsStartDispatched && activeRunId == null &&
        !historyVisible && !runSettingsVisible && !connectionDialogVisible &&
        (model.state == RunUiState.ReadyToStart || model.state == RunUiState.Countdown)
    // Warm GPS while Start is visible, independent of map tiles. Retain this same listener
    // through countdown/cancel; the service remains the only durable run recorder.
    val previewController = rememberForegroundGpsPreview(previewEnabled, lifecycle,
        gpsPreviewRestart, gpsPreviewSourceOverride)
    val gpsPreview by previewController
    val context = LocalContext.current
    fun preciseLocationReady(): Boolean = gpsLocationReady(context)
    var gpsReady by remember(context) { mutableStateOf(preciseLocationReady()) }
    var voiceForeground by remember(lifecycle) {
        mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }

    var musicAccessGranted by remember(context) { mutableStateOf(musicAccessProvider(context)) }

    // Permissions are separate from saved choices. Recheck on return; effective capture
    // still requires current access at the official start, without rewriting consent.
    DisposableEffect(lifecycle, context) {
        val observer = LifecycleEventObserver { _, event ->
            voiceForeground = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            if (event == Lifecycle.Event.ON_PAUSE) {
                stopCompletionAudio?.invoke()
                effortSheetRunId?.let { reflectionCoordinator?.setEffortSheetOpen(it, false) }
            }
            if (event == Lifecycle.Event.ON_RESUME) {
                effortSheetRunId?.let { reflectionCoordinator?.setEffortSheetOpen(it, true) }
            }
            if (event == Lifecycle.Event.ON_RESUME) {
                musicAccessGranted = musicAccessProvider(context)
                gpsReady = preciseLocationReady()
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
        reflectionConnection != ReflectionConnectionState.Local ||
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

    val gpsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        gpsReady = preciseLocationReady()
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
            gpsMessage = "Precise GPS is unavailable. Enable precise location and phone Location, then try again."
        }
        pendingGpsResumeId = null
    }

    fun requestGpsPermissions() {
        val fineLocationGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fineLocationGranted && !preciseLocationReady()) {
            try {
                context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            } catch (_: RuntimeException) {
                gpsMessage = "Enable phone Location in Android Settings, then return to Start."
            }
            return
        }
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
        if (!reflectionRequestsAvailable) {
            historyError = "Set up reflections on Start, then reopen this run to retry."
            return
        }
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

    fun savePreferences(next: RunPreferences, afterSave: () -> Unit = {}) {
        if (preferenceWritePending || runPreferencesSaving) return
        preferenceWritePending = true
        preferenceWriteError = null
        scope.launch {
            try {
                onSaveRunPreferences(next)
                afterSave()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                preferenceWriteError = "Preferences couldn't save. Try again before starting a run."
            } finally { preferenceWritePending = false }
        }
    }

    LaunchedEffect(model.state, runPreferences?.musicSetupComplete) {
        if (model.state == RunUiState.ReadyToStart && runPreferences?.musicSetupComplete == false) {
            runSettingsVisible = true
        }
    }

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
                gpsStartRequested = false
                gpsStartDispatched = false
                previewController.stopBeforeRun()
                gpsPreviewRestart++
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
    LaunchedEffect(model.state, completedRunId, reflectionCoordinator, reflectionConnection) {
        val activeReflectionCoordinator = reflectionCoordinator ?: return@LaunchedEffect
        if (reflectionConnection == ReflectionConnectionState.Loading) return@LaunchedEffect
        if (model.state == RunUiState.Saved) {
            completedRunId?.let { runId ->
                val bridgeReady = reflectionRequestsAvailable && localBridgePermissionGranted()
                activeReflectionCoordinator.onSavedVisible(runId, bridgeReady,
                    awaitingReceipt = completionReceiptRunId == runId &&
                        completionReceiptFinishedRunId != runId)
                if (!reflectionRequestsAvailable) {
                    activeReflectionCoordinator.onBridgeUnavailable(runId)
                } else if (!bridgeReady) {
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
        if (activeMapVisible) {
            val requestedId = activeRunId
            mapEvidence = coordinator.runMapEvidence(mapSignal)?.takeIf { it.runId == requestedId }
        }
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

    // Cancel an unsaved GPS start on backgrounding. Returning or recreating the Activity asks
    // for a new tap instead of starting a run the runner did not see begin.
    DisposableEffect(lifecycle, coordinator, previewController) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE && gpsStartRequested) {
                gpsStartRequested = false
                // The RESUMED preview lifecycle independently releases its listener here.
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(model.state, gpsStartRequested, gpsStartDispatched, gpsSelected, actionInFlight, trackingState) {
        if (gpsSelected && !gpsStartRequested && !gpsStartDispatched &&
            trackingState != RunTrackingServiceState.Starting &&
            model.state == RunUiState.Countdown && !actionInFlight) {
            runGuarded {
                val current = coordinator.journeySnapshot()
                if (current.admission == RunAdmission.CountdownInProgress &&
                    current.startAttemptPhase == com.runstate.mobile.run.StartAttemptPhase.None) {
                    coordinator.cancelCountdown()
                }
                refresh()
            }
        }
    }
    LaunchedEffect(model.state) {
        if (model.state != RunUiState.Initializing && model.state != RunUiState.Countdown &&
            (model.state != RunUiState.ReadyToStart || !gpsStartRequested)) {
            gpsStartRequested = false
            gpsStartDispatched = false
        }
    }
    LaunchedEffect(gpsStartRequested, model.state, actionInFlight, voiceForeground) {
        if (gpsStartRequested && model.state == RunUiState.ReadyToStart && !actionInFlight &&
            lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            runGuarded {
                val attempt = gpsStartAttempt
                if (gpsStartRequested && preciseLocationReady() &&
                    lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    coordinator.beginCountdown()
                    // A lifecycle interruption can arrive while coordinator admission suspends.
                    if (!gpsStartRequested || gpsStartAttempt != attempt ||
                        !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                        coordinator.cancelCountdown()
                    }
                    refresh()
                } else if (gpsStartRequested && !preciseLocationReady()) {
                    // Permission/provider can change between the tap and admission.
                    // Return control to Start instead of leaving an invisible pending request.
                    gpsStartRequested = false
                    gpsReady = false
                    gpsMessage = "Precise GPS is unavailable. Enable phone Location before starting."
                }
            }
        }
    }

    val countdownDigit = rememberLifecycleCountdown(
        active = model.state == RunUiState.Countdown &&
            (!gpsSelected || gpsStartRequested && !gpsStartDispatched),
        lifecycle = lifecycle,
        onFinished = {

            // The countdown only reaches here while the Activity is STARTED. If a Cancel is
            // already in flight, the guard drops this start, and the cancel wins.
            val observeMusicForRun = musicSelected && musicAccessProvider(context)
            if (musicSelected && !observeMusicForRun) {
                musicAccessGranted = false
            }
            val shareMusicForRun = observeMusicForRun && reflectionRequestsAvailable &&
                musicReflectionSharingSelected
            if (gpsSelected) {
                if (!gpsStartRequested || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    runGuarded {
                        coordinator.cancelCountdown()
                        refresh()
                    }
                } else if (!preciseLocationReady()) {
                    gpsStartRequested = false
                    gpsMessage = "Precise GPS is unavailable. Enable phone Location before starting."
                    runGuarded {
                        coordinator.cancelCountdown()
                        refresh()
                    }
                } else {
                    try {
                        // The visible Activity launches the foreground service before any
                        // asynchronous Room insert; that service owns this GPS start.
                        previewController.stopBeforeRun()
                        gpsStartDispatched = true
                        gpsStartRequested = false
                        startGpsRun(context, preRunEnergy, observeMusicForRun,
                            shareMusicForRun)
                    } catch (_: RuntimeException) {
                        gpsStartDispatched = false
                        gpsPreviewRestart++ // Retry even if dispatch failed within one frame.
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

    suspend fun performAction(kind: RunActionKind) {
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
                            database.runDao().findById(id)?.let { row ->
                                val transitions = try { database.runDao().transitionsFor(id) }
                                catch (cancelled: CancellationException) { throw cancelled }
                                catch (_: Exception) { null }
                                savedRunReceiptFor(row, transitions)
                            }
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { null } ?: "Run saved."
                    }
                }
            }) { coordinator.requestAction(kind) }
    }
    val requestAction: (RunActionKind) -> Unit = { kind -> runGuarded { performAction(kind) } }

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
                if (reflectionRequestsAvailable) RunCompleteReflectionDisplay.Failed
                else RunCompleteReflectionDisplay.ConnectionNeeded
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
    // Changing destination closes the old player's pending audio before a new one can prepare.
    key(reflectionConnection, completedRunId) {
        RunCompleteSpeechEffect(
        completedRunId = completedRunId,
        eligibleRunId = completionSpeechEligibleRunId,
        spokenRunId = completionSpeechSpokenRunId,
        reflectionState = reflectionState,
        voicePreparation = voicePreparation,
        preparationVisible = completionVisible,
        completionVisible = completionVisible && completionPresentationHeldRunId != completedRunId &&
            (completionReceiptRunId != completedRunId ||
            completionReceiptFinishedRunId == completedRunId),
        speakerFactory = completionSpeakerFactory,
        onSpoken = { completionSpeechSpokenRunId = it },
        onAudioActiveChange = { completionAudioActive = it },
        onStopReady = { stopCompletionAudio = it }
        )
    }

    if (connectionDialogVisible && model.state == RunUiState.ReadyToStart) {
        ReflectionConnectionDialog(
            initialEndpoint = (reflectionConnection as? ReflectionConnectionState.Hosted)?.config?.endpoint.orEmpty(),
            hasSavedConnection = reflectionConnection is ReflectionConnectionState.Hosted ||
                reflectionConnection == ReflectionConnectionState.Unavailable,
            saving = connectionSaving || reflectionConnectionSaving,
            error = connectionError,
            onDismiss = { if (!connectionSaving) connectionDialogVisible = false },
            onSave = { endpoint, accessCode ->
                if (!connectionSaving) {
                    connectionSaving = true
                    connectionError = null
                    // A service choice starts with fresh per-run music-sharing consent.
                    scope.launch {
                        try {
                            runPreferences?.takeIf { it.shareMusicInReflection }?.let {
                                onSaveRunPreferences(it.copy(shareMusicInReflection = false))
                            }
                            onSaveReflectionConnection(endpoint, accessCode)
                            connectionDialogVisible = false
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: IllegalArgumentException) {
                            connectionError = "Use an HTTPS reflection address and a valid RunState access code."
                        } catch (_: Exception) {
                            connectionError = "Connection couldn't save. Try again."
                        } finally { connectionSaving = false }
                    }
                }
            },
            onClear = {
                if (!connectionSaving) {
                    connectionSaving = true
                    connectionError = null
                    // This draft reset must survive rotation even when the caller's wait is cancelled.
                    scope.launch {
                        try {
                            runPreferences?.takeIf { it.shareMusicInReflection }?.let {
                                onSaveRunPreferences(it.copy(shareMusicInReflection = false))
                            }
                            onClearReflectionConnection()
                            connectionDialogVisible = false
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { connectionError = "Connection couldn't clear. Try again." }
                        finally { connectionSaving = false }
                    }
                }
            }
        )
    }

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
            splitLoader = { runId ->
                val snapshot = database.splitDao().snapshotFor(runId)
                val rows = database.splitDao().rowsFor(runId)
                withContext(Dispatchers.Default) { historySplitsFor(snapshot, rows) }
            },
            onRetrySplits = onRetrySplits,
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

    val completedRun = completedRuns?.firstOrNull { it.runId == completedRunId }
    val presentedRunId = completedRunId
    RunStateScreen(
        model = model,
        metrics = metrics,
        countdownDigit = countdownDigit,
        onStart = {
            when {
                runPreferences == null || runPreferencesError != null || preferenceWriteError != null ||
                    runPreferences.musicSetupComplete == false -> runSettingsVisible = true
                runPreferencesSaving || preferenceWritePending -> Unit
                gpsSelected && !preciseLocationReady() -> {
                    gpsReady = false
                    requestGpsPermissions()
                }
                gpsSelected -> {
                    // Start goes straight to countdown regardless of signal strength.
                    // The already-warm listener stays until the service takes sole ownership.
                    mapRunId = null
                    gpsMessage = null
                    gpsStartAttempt++
                    gpsStartDispatched = false
                    gpsStartRequested = true
                }
                else -> runGuarded {
                    previewController.stopBeforeRun()
                    mapRunId = null
                    coordinator.beginCountdown()
                    refresh()
                }
            }
        },
        onCancelCountdown = {
            gpsStartRequested = false
            runGuarded {
                coordinator.cancelCountdown()
                gpsSelected = true
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
            val observeMusicForRun = musicSelected && musicAccessProvider(context)
            val shareMusicForRun = observeMusicForRun && reflectionRequestsAvailable &&
                musicReflectionSharingSelected
            if (gpsSelected) {
                if (preciseLocationReady()) {
                    try {
                        previewController.stopBeforeRun()
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
        onStop = {
            runGuarded {
                val id = activeRunId
                if (id != null && coordinator.canDiscardShortRun(id)) {
                    shortRunDecisionId = id
                    shortRunDecisionError = null
                } else performAction(RunActionKind.COMPLETE)
            }
        },
        onStartAnother = {
            stopCompletionAudio?.invoke()
            runGuarded {

                // Only Energy is a per-run draft; music choices are saved preferences.
                coordinator.prepareAnotherRun()
                preRunEnergy = null
                gpsMessage = null
                gpsSelected = true
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
        gpsMessage = gpsMessage,
        musicSelected = musicSelected,
        musicAccessGranted = musicAccessGranted,
        onOpenSpotify = {
            if (!openSpotify(context)) {
                Toast.makeText(context,
                    "Spotify couldn't open. Check that it is installed on this phone.",
                    Toast.LENGTH_LONG).show()
            }
        },
        isGpsRun = activeTrackingMode == RunTrackingMode.GPS,
        onOpenRunMap = { mapRunId = activeRunId ?: "foreground-gps-preview" },
        liveMapEnabled = true,
        mapEvidence = if (activeMapVisible) mapEvidence?.takeIf { it.runId == activeRunId }
            else if (previewEnabled) gpsPreview.toMapEvidence() else null,
        nativeMapsEnabled = nativeMapsEnabled,
        previewMapStatus = gpsPreview.explanation,
        onRequestMapLocation = if (gpsPreview.status == ForegroundGpsPreviewStatus.PERMISSION_NEEDED ||
            gpsPreview.status == ForegroundGpsPreviewStatus.LOCATION_OFF) ({ requestGpsPermissions() }) else null,
        mapExpanded = mapVisible,
        onCloseRunMap = {
            mapRunId = null
        },
        gpsServicePresent = when (val service = trackingState) {
            is RunTrackingServiceState.Tracking -> service.runId == activeRunId
            is RunTrackingServiceState.Paused -> service.runId == activeRunId
            else -> false
        },
        gpsSignalStatus = (trackingState as? RunTrackingServiceState.Tracking)
            ?.takeIf { it.runId == activeRunId }?.signalStatus ?: GpsSignalStatus.ACQUIRING,
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
        selectedEffort = completedRun?.postRunEffort,
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
        onViewHistory = {
            gpsStartRequested = false
            stopCompletionAudio?.invoke()
            previewController.stopBeforeRun()
            mapRunId = null
            historyVisible = true
        },
        reflectionAudioActive = completionAudioActive,
        reflectionSongLabel = (reflectionState as? RunReflectionState.Ready)
            ?.takeIf { it.runId == completedRunId }?.passage
            ?.let { "${it.trackTitle} · ${it.artistName}" },
        onStopReflectionAudio = { stopCompletionAudio?.invoke() },
        showHistory = true,
        actionsEnabled = !actionInFlight && !reflectionConnectionSaving && !gpsStartDispatched &&
            !(gpsStartRequested && model.state == RunUiState.ReadyToStart),
        modifier = modifier,
        savedPreRunEnergy = completedRun?.preRunEnergy,
        completionDateLabel = completedRun?.let(::completionDateLabelFor),
        weatherSummary = completedRun?.let { weatherSummaryFor(it, weatherByRun[it.runId]) },
        onReflectionPresentationHoldChange = { held ->
            if (held) completionPresentationHeldRunId = presentedRunId
            else if (completionPresentationHeldRunId == presentedRunId) completionPresentationHeldRunId = null
        },
        onOpenRunSettings = {
            gpsStartRequested = false
            previewController.stopBeforeRun()
            runSettingsVisible = true
        },
        gpsReady = gpsReady,
        gpsStatusText = gpsPreview.startStatusText,
        setupLoading = runPreferences == null && runPreferencesError == null,
        reflectionSetupRequired = reflectionConnection == ReflectionConnectionState.NotConfigured ||
            reflectionConnection == ReflectionConnectionState.Unavailable
    )

    val settingsBusy = preferenceWritePending || runPreferencesSaving || connectionSaving ||
        reflectionConnectionSaving
    val settingsFailure = preferenceWriteError ?: runPreferencesError
    if (runSettingsVisible && model.state == RunUiState.ReadyToStart && !historyVisible &&
        !connectionDialogVisible) {
        RunSettingsDialog(
            gpsReady = gpsReady,
            gpsMessage = gpsMessage,
            musicObservationEnabled = musicSelected,
            musicAccessGranted = musicAccessGranted,
            musicReflectionSharingEnabled = musicReflectionSharingSelected,
            musicReflectionSharingAvailable = reflectionRequestsAvailable,
            weatherEnabled = runPreferences?.captureWeather == true,
            onSetWeather = { enabled ->
                runPreferences?.let { saved -> savePreferences(saved.copy(captureWeather = enabled)) }
            },
            reflectionConnectionLabel = when (reflectionConnection) {
                ReflectionConnectionState.Loading -> "Loading reflection connection…"
                ReflectionConnectionState.Local -> "Computer connection selected"
                ReflectionConnectionState.NotConfigured -> "Set up reflections"
                ReflectionConnectionState.Unavailable -> "Reflection connection needs setup"
                is ReflectionConnectionState.Hosted -> "Online service selected"
            },
            demoMode = !gpsSelected,
            settingsLoading = settingsBusy || runPreferences == null && runPreferencesError == null,
            settingsError = settingsFailure,
            preferencesAvailable = runPreferences != null,
            onRetryPreferences = onRetryRunPreferences,
            actionsEnabled = !actionInFlight,
            onDismiss = {
                val saved = runPreferences
                if (!settingsBusy && saved != null) {
                    // Always confirm storage again after a failed write before permitting Start.
                    if (!saved.musicSetupComplete || settingsFailure != null) {
                        savePreferences(saved.copy(musicSetupComplete = true)) {
                            runSettingsVisible = false
                        }
                    } else runSettingsVisible = false
                } else if (!settingsBusy) runSettingsVisible = false
            },
            onRequestLocationSetup = { requestGpsPermissions() },
            onSetMusicObservation = { enabled ->
                runPreferences?.let { saved ->
                    savePreferences(saved.copy(observeMusic = enabled,
                        shareMusicInReflection = enabled && saved.shareMusicInReflection))
                }
            },
            onRequestMusicAccess = {
                if (!openMusicAccessSettings(context)) {
                    preferenceWriteError = "Music access settings couldn't open. Try again."
                }
            },
            onSetMusicReflectionSharing = { enabled ->
                runPreferences?.let { saved ->
                    savePreferences(saved.copy(shareMusicInReflection = enabled && saved.observeMusic))
                }
            },
            onSetDemoMode = { demo ->
                previewController.stopBeforeRun()
                mapRunId = null
                gpsSelected = !demo
                gpsMessage = null
            },
            onOpenReflectionSetup = {
                connectionError = null
                connectionDialogVisible = true
            }
        )
    }

    val decisionRunId = shortRunDecisionId
    if (decisionRunId != null && decisionRunId == activeRunId &&
        model.state == RunUiState.ActivePaused) {
        ShortRunDecisionDialog(
            busy = actionInFlight || model.actionInProgress,
            error = shortRunDecisionError,
            onKeepPaused = {
                shortRunDecisionId = null
                shortRunDecisionError = null
            },
            onSave = {
                runGuarded {
                    val owned = coordinator.journeySnapshot().admission as? RunAdmission.RunInProgress
                    if (owned?.session?.runId == decisionRunId) {
                        performAction(RunActionKind.COMPLETE)
                        if (model.state == RunUiState.Saved) {
                            shortRunDecisionId = null
                            shortRunDecisionError = null
                        } else shortRunDecisionError = "Your run couldn't save. It is still paused. Try again."
                    } else {
                        shortRunDecisionId = null
                        refresh()
                    }
                }
            },
            onDiscard = {
                runGuarded {
                    shortRunDecisionError = null
                    awaitDurable(onSuccess = {
                        shortRunDecisionId = null
                        preRunEnergy = null
                        gpsMessage = null
                        gpsSelected = true
                    }) { coordinator.requestDiscard(decisionRunId) }
                    if (shortRunDecisionId == decisionRunId) {
                        shortRunDecisionError = "Your run wasn't discarded. It is still paused. Try again or save it."
                    }
                }
            }
        )
    }
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
        val finishedSpeaker = speaker
        speaker = null
        try { finishedSpeaker?.close() } catch (_: RuntimeException) {
            // The exact saved run remains usable when the platform speech service fails.
        } finally { finishCallback(id) }
    }

    DisposableEffect(Unit) {
        onDispose {
            val interruptedId = activeReceiptRunId
            if (interruptedId != null) finish(interruptedId)
            else try { speaker?.close() } catch (_: RuntimeException) { }
        }
    }
    LaunchedEffect(completedRunId, receiptRunId, receiptText, finishedRunId, completionVisible) {
        activeReceiptRunId?.takeIf { it != completedRunId }?.let(::finish)
        val pendingId = receiptRunId?.takeIf { it != finishedRunId }
        if (pendingId == null || completedRunId != pendingId) {
            try { speaker?.stop() } catch (_: RuntimeException) { }
            return@LaunchedEffect
        }
        if (!completionVisible) {
            try { speaker?.stop() } catch (_: RuntimeException) { }
            finally { finish(pendingId) }
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
    onSpoken: (String) -> Unit,
    onAudioActiveChange: (Boolean) -> Unit = {},
    onStopReady: ((() -> Unit)?) -> Unit = {},
    voicePreparation: ReflectionVoicePreparation? = null,
    preparationVisible: Boolean = completionVisible
) {
    val context = LocalContext.current
    var speaker by remember { mutableStateOf<ReflectionSpeaker?>(null) }
    var speakingRunId by remember { mutableStateOf<String?>(null) }
    var preparationRequestId by remember { mutableStateOf<String?>(null) }
    var activeAttempt by remember { mutableStateOf<Any?>(null) }
    val currentRunId by rememberUpdatedState(completedRunId)
    val currentVisible by rememberUpdatedState(completionVisible)
    val audioCallback by rememberUpdatedState(onAudioActiveChange)
    val stopReadyCallback by rememberUpdatedState(onStopReady)

    fun stopAudio() {
        // Invalidate first: stop may synchronously deliver an engine completion callback.
        activeAttempt = null
        audioCallback(false)
        try { speaker?.stop() } catch (_: RuntimeException) { }
    }

    DisposableEffect(Unit) {
        stopReadyCallback(::stopAudio)
        onDispose {
            activeAttempt = null
            audioCallback(false)
            stopReadyCallback(null)
            try { speaker?.close() } catch (_: RuntimeException) { }
        }
    }
    LaunchedEffect(completedRunId, eligibleRunId, spokenRunId, voicePreparation, preparationVisible) {
        val preparation = eligibleCompletionVoicePreparation(
            completedRunId, eligibleRunId, spokenRunId, voicePreparation
        )?.takeIf { preparationVisible }
        try {
            if (preparation == null) {
                speaker?.cancelPreparation()
                preparationRequestId = null
            } else {
                val activeSpeaker = speaker ?: speakerFactory(context).also { speaker = it }
                if (preparationRequestId != preparation.requestId) activeSpeaker.cancelPreparation()
                preparationRequestId = preparation.requestId
                activeSpeaker.prepare(preparation.text)
            }
        } catch (_: RuntimeException) {
            // Optional preparation must not consume arrival speech or hide the exact saved text.
            preparationRequestId = null
            try { speaker?.cancelPreparation() } catch (_: RuntimeException) { }
        }
    }
    LaunchedEffect(completedRunId, eligibleRunId, spokenRunId, reflectionState, completionVisible) {
        if (speakingRunId != null && speakingRunId != completedRunId) {
            stopAudio()
            val oldSpeaker = speaker
            speaker = null
            speakingRunId = null
            try { oldSpeaker?.close() } catch (_: RuntimeException) { }
        }
        if (!completionVisible) {
            // Facts and intake may still own audible delivery while voice prepares silently.
            if (!preparationVisible || activeAttempt != null) stopAudio()
            return@LaunchedEffect
        }
        val exactText = readyCompletionSpeechText(
            completedRunId, eligibleRunId, spokenRunId, reflectionState
        ) ?: return@LaunchedEffect
        onSpoken(requireNotNull(completedRunId))
        speakingRunId = completedRunId
        val attempt = Any()
        activeAttempt = attempt
        audioCallback(true)
        try {
            val activeSpeaker = speaker ?: speakerFactory(context).also { speaker = it }
            val runId = completedRunId
            val canPlay = {
                activeAttempt === attempt && speaker === activeSpeaker &&
                    currentVisible && currentRunId == runId
            }
            val finished = {
                if (activeAttempt === attempt) {
                    activeAttempt = null
                    audioCallback(false)
                }
            }
            val passage = (reflectionState as? RunReflectionState.Ready)?.passage
            if (passage != null) {
                activeSpeaker.speakWithSongPassage(exactText, passage, canPlay, finished)
            } else {
                activeSpeaker.speakWithInstrumental(exactText, canPlay, finished)
            }
        } catch (_: RuntimeException) {
            stopAudio()
            // Consume this arrival attempt once; a missing engine never replays on resume.
        }
    }
}

/** Preparing a voice never makes an old History row or another run eligible to speak. */
internal fun eligibleCompletionVoicePreparation(
    completedRunId: String?,
    eligibleRunId: String?,
    spokenRunId: String?,
    preparation: ReflectionVoicePreparation?
): ReflectionVoicePreparation? = preparation?.takeIf {
    completedRunId != null && completedRunId == eligibleRunId &&
        it.runId == completedRunId && spokenRunId != completedRunId
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

/** Opens Spotify only on a deliberate tap; no account access or playback command is sent. */
private fun openSpotify(context: Context): Boolean = try {
    context.startActivity(
        Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setPackage("com.spotify.music")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
    true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
}

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
