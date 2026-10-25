package com.runstate.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.runstate.mobile.location.GpsMapEvidence
import com.runstate.mobile.location.GpsSignalStatus

internal const val ACTIVE_RUN_SCREEN_TAG = "activeRunScreen"
internal const val ACTIVE_RUN_CIRCLE_TAG = "activeRunCircle"
internal const val ACTIVE_RUN_DISTANCE_TAG = "activeRunDistance"
internal const val ACTIVE_RUN_ELAPSED_TAG = "activeRunElapsed"
internal const val ACTIVE_RUN_METRICS_TAG = "activeRunMetrics"
internal const val ACTIVE_RUN_PACE_TAG = "activeRunPace"
internal const val ACTIVE_RUN_ACTIVE_TAG = "activeRunActive"
internal const val ACTIVE_RUN_MAP_BUTTON_TAG = "activeRunMapButton"
internal const val ACTIVE_RUN_MAP_DIALOG_TAG = "activeRunMapDialog"

private val ActiveInk = Color(0xFF293326)
private val ActiveForest = Color(0xFF263222)
private val ActiveCream = Color(0xFFF9FAF5)

/** An unfinished short run remains safely paused until the runner makes an explicit choice. */
@Composable
internal fun ShortRunDecisionDialog(
    busy: Boolean,
    error: String?,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onKeepPaused: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onKeepPaused() },
        containerColor = ActiveCream,
        titleContentColor = ActiveInk,
        textContentColor = ActiveInk,
        title = { Text("Save this short run?") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("You recorded less than 30 seconds of active time. Save it to History, or discard it if you started by accident. Discard permanently removes this run.")
                if (error != null) Text(error, color = Color(0xFF8B392D))
            }
        },
        confirmButton = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onSave,
                    enabled = !busy,
                    colors = ButtonDefaults.buttonColors(containerColor = ActiveForest),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) { Text(if (busy) "Finishing…" else "Save run") }
                TextButton(
                    onClick = onDiscard,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) { Text("Discard run", color = if (busy) ActiveInk.copy(alpha = 0.38f) else Color(0xFF8B392D)) }
                TextButton(
                    onClick = onKeepPaused,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) { Text("Keep paused", color = ActiveInk.copy(alpha = if (busy) 0.38f else 0.85f)) }
            }
        }
    )
}

/** Standalone active presentation; the app uses the continuous countdown/run composition. */
@Composable
internal fun ActiveRunScreen(
    paused: Boolean,
    metrics: RunMetricsDisplay?,
    actionFailed: Boolean,
    actionsEnabled: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    isGpsRun: Boolean = false,
    gpsServicePresent: Boolean = false,
    gpsMessage: String? = null,
    onResumeGpsTracking: () -> Unit = {},
    modifier: Modifier = Modifier,
    gpsSignalStatus: GpsSignalStatus = GpsSignalStatus.ACQUIRING,
    onOpenRunMap: () -> Unit = {},
    liveMapBackground: Boolean = false,
    liveMapStatus: String? = null
) {
    RunAssemblyScreen(
        state = if (paused) RunUiState.ActivePaused else RunUiState.ActiveRunning,
        countdownDigit = null, metrics = metrics, day = java.time.LocalTime.now().hour in 7 until 19,
        actionsEnabled = actionsEnabled, actionFailed = actionFailed, isGpsRun = isGpsRun,
        gpsServicePresent = gpsServicePresent, gpsSignalStatus = gpsSignalStatus, gpsMessage = gpsMessage,
        onPause = onPause, onResume = onResume, onStop = onStop, onCancel = {}, onStartNow = {},
        onResumeGpsTracking = onResumeGpsTracking, onOpenRunMap = onOpenRunMap,
        onOpenSpotify = {}, modifier = modifier
    )
}

/** Optional street-map presentation; the existing run owner still owns every control. */
@Composable
internal fun ActiveRunMapDialog(
    evidence: GpsMapEvidence?,
    paused: Boolean,
    actionsEnabled: Boolean,
    actionFailed: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDismiss: () -> Unit,
    enableNativeMap: Boolean = false
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().padding(12.dp).clip(RoundedCornerShape(24.dp))
                .background(ActiveCream).padding(16.dp).testTag(ACTIVE_RUN_MAP_DIALOG_TAG)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (paused) "Recorded map" else "Live run map", color = ActiveInk,
                    fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Close") }
            }
            if (evidence == null) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Loading recorded GPS…", color = ActiveInk)
                }
            } else {
                RecordedRunMap(evidence, Modifier.weight(1f).fillMaxWidth(), enableNativeMap)
            }
            if (actionFailed) Text(ACTION_FAILED_MESSAGE, color = Color(0xFF8B392D),
                fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            Button(onClick = if (paused) onResume else onPause, enabled = actionsEnabled,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ActiveForest)) {
                Text(if (paused) "Resume run" else "Pause run")
            }
            Text("Close the map to finish your run. Map tiles from OpenFreeMap. Viewed map areas and your IP address go to the provider; your recorded route stays on this phone.",
                color = ActiveInk, fontSize = 11.sp, lineHeight = 16.sp,
                modifier = Modifier.padding(top = 8.dp))
        }
    }
}
