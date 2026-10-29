package com.runstate.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.runstate.mobile.data.local.EffortRatingScale
import com.runstate.mobile.data.local.ReflectionBranch
import com.runstate.mobile.location.GpsSignalStatus
import com.runstate.mobile.settings.RunMusicPlayerLayout

internal const val MUSIC_REFLECTION_DEMO_SCREEN_TAG = "musicReflectionDemoScreen"
internal const val MUSIC_REFLECTION_DEMO_ERROR_TAG = "musicReflectionDemoError"
internal const val MUSIC_REFLECTION_DEMO_RETRY_TAG = "musicReflectionDemoRetry"

/** Presentation-only journey: these callbacks never receive a real run, database or preferences. */
@Composable
internal fun MusicReflectionDemoScreen(
    audio: MusicReflectionDemoAudio,
    day: Boolean,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    var stage by rememberSaveable { mutableStateOf(RunUiState.ActiveRunning) }
    var energy by rememberSaveable { mutableStateOf<ReflectionBranch?>(null) }
    var effort by rememberSaveable { mutableStateOf<Int?>(null) }
    var musicRevealed by rememberSaveable { mutableStateOf(false) }
    var exitRequested by remember { mutableStateOf(false) }
    val audioState by audio.state.collectAsState()
    val player by audio.player.collectAsState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentExit by rememberUpdatedState(onExit)

    fun exit() {
        if (exitRequested) return
        exitRequested = true
        audio.stop()
        currentExit()
    }

    LaunchedEffect(audio) { audio.prepare() }
    DisposableEffect(audio, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) audio.stop()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            audio.stop()
        }
    }
    BackHandler { exit() }

    val lightSurface = day || stage == RunUiState.Saved
    val ink = if (lightSurface) CompletionStrongInk else Color(0xFFF5F5F7)
    val surface = if (lightSurface) CompletionSurfaceTop else Color(0xFF101113)
    Column(modifier.fillMaxSize().testTag(MUSIC_REFLECTION_DEMO_SCREEN_TAG)) {
        RunStateScreen(
            model = RunUiModel(stage),
            metrics = MusicReflectionDemoData.metrics,
            countdownDigit = null,
            onStart = {},
            onCancelCountdown = ::exit,
            onRetryInitialization = {},
            onRetryStart = {},
            onPause = { stage = RunUiState.ActivePaused },
            onResume = { stage = RunUiState.ActiveRunning },
            onStop = {
                if (stage == RunUiState.ActivePaused) {
                    audio.finishRun()
                    stage = RunUiState.Saved
                }
            },
            onStartAnother = ::exit,
            reflection = audioState.reflection,
            onRetryReflection = audio::retry,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            selectedEnergy = energy,
            onSelectEnergy = {
                energy = it
                audio.selectEnergy(it)
            },
            energyEnabled = true,
            isTestData = false,
            savedPreRunEnergy = MusicReflectionDemoData.preRunEnergy,
            selectedEffortRating = effort,
            effortRatingScale = EffortRatingScale.FOSTER,
            onSelectEffortRating = { effort = it },
            effortEnabled = true,
            onReflectionPresentationHoldChange = audio::setPresentationHeld,
            reflectionAudioActive = audioState.active,
            reflectionSongLabel = "Good as Hell · Lizzo",
            onStopReflectionAudio = audio::stop,
            onSaveActivity = ::exit,
            completionActionLabel = "Done",
            isGpsRun = true,
            gpsServicePresent = true,
            gpsSignalStatus = GpsSignalStatus.RECORDING,
            runAppearanceDay = day,
            countdownCadenceActive = false,
            musicPlayer = player,
            musicPlayerLayout = RunMusicPlayerLayout.ALBUM,
            onPreviousTrack = audio::restartMusic,
            onNextTrack = audio::restartMusic,
            onToggleMusicPlayback = audio::toggleMusic,
            onConnectMusic = audio::startMusic,
            onOpenSpotify = audio::startMusic,
            onOpenMusicTrack = { audio.startMusic() },
            onMusicVisibilityChange = { open ->
                if (open && !musicRevealed) {
                    musicRevealed = true
                    audio.startMusic()
                }
            }
        )
        audioState.error?.let { error ->
            Row(
                Modifier.fillMaxWidth().background(surface)
                    .padding(horizontal = 20.dp).testTag(MUSIC_REFLECTION_DEMO_ERROR_TAG),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(error, color = ink, fontSize = 13.sp, lineHeight = 18.sp,
                    modifier = Modifier.weight(1f))
                TextButton(onClick = audio::retry,
                    modifier = Modifier.testTag(MUSIC_REFLECTION_DEMO_RETRY_TAG)) {
                    Text("Retry", color = ink)
                }
            }
        }
    }
}
