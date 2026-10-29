package com.runstate.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.runstate.mobile.data.local.ReflectionBranch
import com.runstate.mobile.data.local.PostRunEffort
import com.runstate.mobile.data.local.PreRunEnergy
import com.runstate.mobile.data.local.RunIntention
import com.runstate.mobile.data.local.EffortRatingScale
import com.runstate.mobile.location.GpsSignalStatus
import com.runstate.mobile.location.GpsMapEvidence
import com.runstate.mobile.music.SpotifyLivePlayerState
import com.runstate.mobile.settings.RunMusicPlayerLayout
import com.runstate.mobile.ui.theme.RunStateTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** The line shown when a lifecycle write is refused. */
internal const val ACTION_FAILED_MESSAGE = "That didn't save. Try again."

/** How long each countdown digit stays on screen. */
internal const val COUNTDOWN_STEP_MILLIS = 1_000L
internal const val COUNTDOWN_SECONDS = 10
internal const val COUNTDOWN_START_NOW_TAG = "countdownStartNow"

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
 * Ready, Countdown, Active and Saved delegate to their dedicated visual screens.
 * Lifecycle ownership and error states remain with the existing coordinator.
 *
 * @param countdownDigit the digit to show during [RunUiState.Countdown], or null for none.
 *   Passed separately from the model because it is composition-local timing, not an answer
 *   from the coordinator.
 * @param metrics formatted values for Running, Paused or Saved; null renders an honest
 *   unavailable state rather than substituting zero.
 * @param actionsEnabled false while a request the caller started is still running, which
 *   is how a double tap is stopped from becoming two requests. It is presentation only — the
 *   coordinator refuses or merges a second request regardless.
 */
@Composable
internal fun RunStateScreen(
    model: RunUiModel,
    metrics: RunMetricsDisplay? = null,
    countdownDigit: Int?,
    onStart: () -> Unit,
    onCancelCountdown: () -> Unit,
    onRetryInitialization: () -> Unit,
    onRetryStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onStartAnother: () -> Unit,
    reflection: RunCompleteReflectionDisplay? = null,
    onRetryReflection: () -> Unit = {},
    modifier: Modifier = Modifier,
    actionsEnabled: Boolean = true,
    selectedEnergy: ReflectionBranch? = null,
    onSelectEnergy: (ReflectionBranch) -> Unit = {},
    energyEnabled: Boolean = false,
    isTestData: Boolean = true,
    onViewHistory: () -> Unit = {},
    showHistory: Boolean = false,
    selectedPreRunEnergy: PreRunEnergy? = null,
    onSelectPreRunEnergy: (PreRunEnergy?) -> Unit = {},
    gpsSelected: Boolean = true,
    gpsMessage: String? = null,
    musicSelected: Boolean = false,
    musicAccessGranted: Boolean = false,
    isGpsRun: Boolean = false,
    gpsServicePresent: Boolean = false,
    onResumeGpsTracking: () -> Unit = {},
    selectedEffort: PostRunEffort? = null,
    onSelectEffort: (PostRunEffort?) -> Unit = {},
    effortEnabled: Boolean = false,
    effortSaving: Boolean = false,
    effortError: String? = null,
    onEffortSheetOpenChange: (Boolean) -> Unit = {},
    reflectionAudioActive: Boolean = false,
    onStopReflectionAudio: () -> Unit = {},
    savedPreRunEnergy: PreRunEnergy? = null,
    completionDateLabel: String? = null,
    onReflectionPresentationHoldChange: (Boolean) -> Unit = {},
    onOpenRunSettings: () -> Unit = {},
    gpsReady: Boolean = false,
    gpsStatusText: String? = null,
    setupLoading: Boolean = false,
    reflectionSetupRequired: Boolean = false,
    gpsSignalStatus: GpsSignalStatus = GpsSignalStatus.ACQUIRING,
    onOpenRunMap: () -> Unit = {},
    liveMapEnabled: Boolean = false,
    mapEvidence: GpsMapEvidence? = null,
    nativeMapsEnabled: Boolean = false,
    loadStreetTiles: Boolean = true,
    previewMapStatus: String? = null,
    onRequestMapLocation: (() -> Unit)? = null,
    mapExpanded: Boolean = false,
    onCloseRunMap: () -> Unit = {},
    onOpenSpotify: () -> Unit = {},
    weatherSummary: String? = null,
    reflectionSongLabel: String? = null,
    onStartNow: () -> Unit = {},
    onSaveActivity: (() -> Unit)? = null,
    activitySaving: Boolean = false,
    activitySaveError: String? = null,
    localNote: String? = null,
    onSaveLocalNote: ((String?) -> Unit)? = null,
    noteSaving: Boolean = false,
    noteError: String? = null,
    selectedShoe: ShoePairDisplay? = null,
    onOpenShoes: () -> Unit = {},
    selectedRunIntention: RunIntention? = null,
    routeLabel: String? = null,
    onRunContextChanged: (RunIntention?, String?) -> Unit = { _, _ -> },
    selectedEffortRating: Int? = null,
    effortRatingScale: EffortRatingScale? = EffortRatingScale.FOSTER,
    onSelectEffortRating: (Int?) -> Unit = {},
    runAppearanceDay: Boolean = false,
    countdownTickKey: Long = 0L,
    countdownCadenceActive: Boolean = true,
    musicPlayer: SpotifyLivePlayerState = SpotifyLivePlayerState(),
    musicPlayerLayout: RunMusicPlayerLayout = RunMusicPlayerLayout.HEADPHONES,
    onPreviousTrack: () -> Unit = {},
    onNextTrack: () -> Unit = {},
    onToggleMusicPlayback: () -> Unit = {},
    onConnectMusic: () -> Unit = {},
    onOpenMusicTrack: (String?) -> Unit = {},
    onMusicVisibilityChange: (Boolean) -> Unit = {},
    completionActionLabel: String? = null
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

    // Completion has its own full-screen hierarchy. It still receives only the same formatted
    // saved metrics and the same callback; this branch changes presentation, not ownership.
    if (state == RunUiState.Saved) {
        RunCompleteScreen(
            metrics = metrics,
            reflection = reflection,
            onRetryReflection = onRetryReflection,
            onStartAnother = onStartAnother,
            actionsEnabled = actionsEnabled,
            modifier = modifier,
            selectedEnergy = selectedEnergy,
            onSelectEnergy = onSelectEnergy,
            energyEnabled = energyEnabled,
            isTestData = isTestData,
            onViewHistory = onViewHistory,
            showHistory = showHistory,
            selectedEffort = selectedEffort,
            onSelectEffort = onSelectEffort,
            effortEnabled = effortEnabled,
            effortSaving = effortSaving,
            effortError = effortError,
            onEffortSheetOpenChange = onEffortSheetOpenChange,
            reflectionAudioActive = reflectionAudioActive,
            reflectionSongLabel = reflectionSongLabel,
            onStopReflectionAudio = onStopReflectionAudio,
            savedPreRunEnergy = savedPreRunEnergy,
            completionDateLabel = completionDateLabel,
            weatherSummary = weatherSummary,
            onReflectionPresentationHoldChange = onReflectionPresentationHoldChange,
            onSaveActivity = onSaveActivity,
            activitySaving = activitySaving,
            activitySaveError = activitySaveError,
            localNote = localNote,
            onSaveLocalNote = onSaveLocalNote,
            noteSaving = noteSaving,
            noteError = noteError,
            selectedEffortRating = selectedEffortRating,
            effortRatingScale = effortRatingScale,
            onSelectEffortRating = onSelectEffortRating,
            completionActionLabel = completionActionLabel
        )
        return
    }

    val mapAvailable = liveMapEnabled && when (state) {
        RunUiState.ReadyToStart -> gpsSelected
        RunUiState.ActiveRunning, RunUiState.ActivePaused -> isGpsRun
        else -> false
    }
    val showLiveMap = mapAvailable && mapExpanded
    var mapAvailability by remember(showLiveMap) { mutableStateOf<String?>(null) }
    var mapHeaderHeight by remember { mutableIntStateOf(0) }
    var mapFooterHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val mapStatus = mapAvailability ?: if (state == RunUiState.ReadyToStart || state == RunUiState.Countdown)
        previewMapStatus else mapEvidence?.let(::recordedMapStatus)
    BackHandler(showLiveMap, onBack = onCloseRunMap)

    // The map exists only after an explicit open. Closing restores the original illustrated
    // screens and disposes tile rendering; recording itself stays with the run coordinator.
    Column(modifier.fillMaxSize()) {
    Box(Modifier.weight(1f).fillMaxWidth()) {
        val countdownVisible = state == RunUiState.Countdown || state == RunUiState.StartingRun
        // One composition lets the countdown geometry assemble into real readings. The
        // coordinator starts recording first; this decoration never delays or starts a run.
        val assemblyVisible = countdownVisible || state == RunUiState.ActiveRunning || state == RunUiState.ActivePaused
        if (assemblyVisible && !showLiveMap) {
            RunAssemblyScreen(
                state = state,
                countdownDigit = countdownDigit,
                metrics = metrics,
                day = runAppearanceDay,
                actionsEnabled = if (countdownVisible) actionsEnabled && state == RunUiState.Countdown else runControlsEnabled,
                actionFailed = model.actionFailed,
                isGpsRun = if (countdownVisible) gpsSelected else isGpsRun,
                gpsServicePresent = gpsServicePresent,
                gpsSignalStatus = gpsSignalStatus,
                gpsMessage = gpsMessage,
                onPause = onPause,
                onResume = onResume,
                onStop = onStop,
                onCancel = onCancelCountdown,
                onStartNow = onStartNow,
                onResumeGpsTracking = onResumeGpsTracking,
                onOpenRunMap = onOpenRunMap,
                onOpenSpotify = onOpenSpotify,
                countdownTickKey = countdownTickKey,
                countdownCadenceActive = countdownCadenceActive,
                musicPlayer = musicPlayer,
                musicPlayerLayout = musicPlayerLayout,
                onPreviousTrack = onPreviousTrack,
                onNextTrack = onNextTrack,
                onToggleMusicPlayback = onToggleMusicPlayback,
                onConnectMusic = onConnectMusic,
                onOpenMusicTrack = onOpenMusicTrack,
                onMusicVisibilityChange = onMusicVisibilityChange
            )
            return@Box
        }
        if (showLiveMap) {
            RecordedRunMapBackground(
                evidence = mapEvidence,
                modifier = Modifier.fillMaxSize(),
                enableNativeMap = nativeMapsEnabled,
                loadStreetTiles = loadStreetTiles,
                showControls = true,
                controlsPadding = with(density) {
                    PaddingValues(top = mapHeaderHeight.toDp(), bottom = mapFooterHeight.toDp())
                },
                onStatusChange = { mapAvailability = it }
            )
        }
        if (showLiveMap) {
            Column(Modifier.fillMaxSize().testTag(ACTIVE_RUN_MAP_DIALOG_TAG)) {
                Column(Modifier.fillMaxWidth().onSizeChanged { mapHeaderHeight = it.height }) {
                Row(Modifier.fillMaxWidth().background(Color(0xEDF9FAF5)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(when (state) {
                        RunUiState.ReadyToStart -> "Location preview"
                        RunUiState.ActivePaused -> "Recorded run map"
                        else -> "Live run map"
                    },
                        modifier = Modifier.weight(1f))
                    TextButton(onClick = onCloseRunMap) { Text("Close map") }
                }
                Text(mapStatus ?: "Waiting for a trustworthy GPS location.",
                    modifier = Modifier.fillMaxWidth().background(Color(0xEDF9FAF5)).padding(12.dp))
                }
                Box(Modifier.weight(1f))
                Column(Modifier.fillMaxWidth().onSizeChanged { mapFooterHeight = it.height }
                    .background(Color(0xEDF9FAF5)).padding(12.dp)) {
                    if (model.actionFailed) Text(ACTION_FAILED_MESSAGE)
                    if (state == RunUiState.ReadyToStart) {
                        if (onRequestMapLocation != null) {
                            TextButton(onClick = onRequestMapLocation, enabled = actionsEnabled,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                    .testTag(START_MAP_LOCATION_ACCESS_TAG)) {
                                Text("Enable location")
                            }
                        }
                        Button(onClick = onStart, enabled = actionsEnabled && !setupLoading,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .testTag(START_RUN_MAP_START_TAG)) {
                            Text("Start")
                        }
                    } else if (state == RunUiState.ActiveRunning || state == RunUiState.ActivePaused) {
                        Button(onClick = if (state == RunUiState.ActivePaused) onResume else onPause,
                            enabled = runControlsEnabled, modifier = Modifier.fillMaxWidth()) {
                            Text(if (state == RunUiState.ActivePaused) "Resume" else "Pause")
                        }
                    }
                    Text("Map tiles from OpenFreeMap. Viewed map areas and your IP address go to the provider; your recorded route stays on this phone.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            return@Box
        }
    if (state == RunUiState.ReadyToStart) {
        StartRunScreen(
            selectedPreRunEnergy = selectedPreRunEnergy,
            onSelectPreRunEnergy = onSelectPreRunEnergy,
            onStart = onStart,
            actionsEnabled = actionsEnabled,
            onViewHistory = onViewHistory,
            showHistory = showHistory,
            gpsSelected = gpsSelected,
            gpsMessage = gpsMessage,
            musicSelected = musicSelected,
            musicAccessGranted = musicAccessGranted,
            onOpenSpotify = onOpenSpotify,
            selectedShoe = selectedShoe,
            onOpenShoes = onOpenShoes,
            selectedRunIntention = selectedRunIntention,
            routeLabel = routeLabel,
            onRunContextChanged = onRunContextChanged,
            onOpenRunSettings = onOpenRunSettings,
            gpsReady = gpsReady,
            gpsStatusText = gpsStatusText,
            setupLoading = setupLoading,
            reflectionSetupRequired = reflectionSetupRequired,
            modifier = Modifier,
            liveMapBackground = false,
            onOpenMap = if (mapAvailable) onOpenRunMap else null
        )
        return@Box
    }


    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xBFF9FAF5))
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
            RunUiState.ReadyToStart -> Unit // Drawn above as the dedicated Start surface.

            RunUiState.Countdown -> Unit // Drawn above; its exit overlays the active screen.

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

            RunUiState.ActiveRunning,
            RunUiState.ActivePaused -> Unit // Drawn above as the dedicated Active surface.

            RunUiState.Saved -> Unit // Drawn above as the dedicated Run Complete surface.

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
    if (showLiveMap) RecordedMapAttribution(Modifier.fillMaxWidth().background(Color(0xFFF9FAF5)))
    }
}

/** The primary button shape every acting screen uses, so they cannot drift apart. */
@Composable
private fun ScreenAction(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.padding(top = 24.dp)
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
 * Runs a ten-second preparation countdown only while [lifecycle] is at least STARTED.
 *
 * Returns the digit to show, or null when none should be. [onFinished] is called once,
 * either after the last second or an explicit Start now request.
 * This is gated on the lifecycle rather than merely on composition. A
 * composition keeps running while the app is in the background; a countdown must not,
 * because a durable start the runner never saw happen is exactly what this prevents.
 *
 * - Dropping below STARTED cancels the countdown and clears the digit.
 * - Returning to STARTED begins again at ten. A countdown is a visual preparation, so there is
 *   nothing meaningful to resume part-way through.
 * - The journey owner cancels unsent GPS intent on background/recreation. This helper never
 *   stores permission to start a run across compositions.
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
    onFinished: () -> Unit,
    startNowRequested: Boolean = false,
    onCountdownCue: (Int?) -> Unit = {},
    onIntroduction: suspend () -> Unit = {}
): Int? {
    var digit by remember { mutableStateOf<Int?>(null) }
    var finished by remember(active, lifecycle) { mutableStateOf(false) }
    val currentOnFinished by rememberUpdatedState(onFinished)
    val currentOnCue by rememberUpdatedState(onCountdownCue)
    val currentIntroduction by rememberUpdatedState(onIntroduction)

    // Both ways to finish share the same main-thread flag. A tap during the final second
    // cannot ask the run owner to start again when the timer's delay returns.
    LaunchedEffect(active, startNowRequested, lifecycle) {
        if (active && startNowRequested && !finished &&
            lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            finished = true
            digit = null
            currentOnCue(null)
            currentOnFinished()
        }
    }

    LaunchedEffect(active, lifecycle) {
        digit = null
        if (!active) {
            currentOnCue(null)
            return@LaunchedEffect
        }

        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if (finished) return@repeatOnLifecycle

            try {
                // Hold the opening number through the spoken introduction; timing starts after it.
                digit = COUNTDOWN_SECONDS
                kotlinx.coroutines.withTimeoutOrNull(5_000L) { currentIntroduction() }
                for (step in COUNTDOWN_SECONDS downTo 1) {
                    if (finished) return@repeatOnLifecycle
                    digit = step
                    if (step == COUNTDOWN_SECONDS || step <= 3) currentOnCue(step)
                    delay(COUNTDOWN_STEP_MILLIS)
                }
            } catch (cancellation: CancellationException) {
                digit = null
                currentOnCue(null)
                throw cancellation
            }

            if (!finished && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                finished = true
                digit = null
                currentOnCue(null)
                currentOnFinished()
            }
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
private fun CountdownPreview() = PreviewScreen(RunUiModel(RunUiState.Countdown), digit = COUNTDOWN_SECONDS)

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
