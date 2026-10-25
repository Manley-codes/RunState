package com.runstate.mobile.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.runstate.mobile.location.GpsSignalStatus

internal const val COUNTDOWN_SCREEN_TAG = "countdownScreen"
internal const val COUNTDOWN_DIGIT_TAG = "countdownDigit"

/** Isolated countdown entry for previews/tests; the app keeps one shared assembly composition. */
@Composable
internal fun CountdownScreen(
    digit: Int?,
    actionsEnabled: Boolean,
    onStartNow: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    transitioning: Boolean = false
) = RunAssemblyScreen(
    state = if (transitioning) RunUiState.StartingRun else RunUiState.Countdown,
    countdownDigit = digit, metrics = null, day = java.time.LocalTime.now().hour in 7 until 19,
    actionsEnabled = actionsEnabled, actionFailed = false, isGpsRun = true,
    gpsServicePresent = false, gpsSignalStatus = GpsSignalStatus.ACQUIRING, gpsMessage = null,
    onPause = {}, onResume = {}, onStop = {}, onCancel = onCancel, onStartNow = onStartNow,
    onResumeGpsTracking = {}, onOpenRunMap = {}, onOpenSpotify = {}, modifier = modifier
)
