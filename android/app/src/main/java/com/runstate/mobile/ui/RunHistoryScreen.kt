package com.runstate.mobile.ui

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runstate.mobile.data.local.PostRunEffort

internal const val RUN_HISTORY_SCREEN_TAG = "runHistoryScreen"
internal const val RUN_HISTORY_ROW_TAG_PREFIX = "runHistoryRow:"
internal const val RUN_HISTORY_REFLECTION_TAG_PREFIX = "runHistoryReflection:"
internal const val RUN_HISTORY_RETRY_TAG_PREFIX = "runHistoryRetry:"
internal const val RUN_HISTORY_EFFORT_TAG_PREFIX = "runHistoryEffort:"

/** Kept injectable so screen behavior can be checked without a device voice engine. */
internal interface ReflectionSpeaker {
    fun speak(text: String)
    fun stop()
    fun close()
}

/** Android owns the voice; the saved text remains the source for both reading and speech. */
internal class AndroidReflectionSpeaker(context: Context) : ReflectionSpeaker {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var engine: TextToSpeech? = null
    private var initialized = false
    private var pendingText: String? = null
    private var closed = false

    init {
        try {
            engine = TextToSpeech(context.applicationContext) { result ->
                // Some engines report readiness on a binder thread. Keep state on the UI thread.
                mainHandler.post {
                    if (!closed) {
                        initialized = result == TextToSpeech.SUCCESS
                        if (initialized) speakPending() else pendingText = null
                    }
                }
            }
        } catch (_: RuntimeException) {
            // The exact saved text remains on screen if no speech engine is available.
        }
    }

    override fun speak(text: String) {
        if (closed) return
        pendingText = text
        if (initialized) speakPending()
    }

    override fun stop() {
        pendingText = null
        try {
            engine?.stop()
        } catch (_: RuntimeException) {
            // A disconnected engine must not disturb History navigation.
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        stop()
        try {
            engine?.shutdown()
        } catch (_: RuntimeException) {
            // The screen is already leaving; the visible run is still intact.
        }
        engine = null
    }

    private fun speakPending() {
        val text = pendingText ?: return
        val activeEngine = engine ?: return
        pendingText = null
        if (text.length > TextToSpeech.getMaxSpeechInputLength()) return
        try {
            activeEngine.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), "runstate-history-reflection")
        } catch (_: RuntimeException) {
            // Speech failure is independent of the saved reflection.
        }
    }
}

/** Display facts read from local storage; opening a row never starts generation. */
internal data class RunHistoryItem(
    val runId: String,
    val dateLabel: String,
    val metrics: RunMetricsDisplay?,
    val reflectionStatus: String?,
    val reflectionText: String?,
    val energyLabel: String?,
    val isTestData: Boolean,
    val preRunEnergyLabel: String? = null,
    val musicObservationRequested: Boolean = false,
    val musicObservationCount: Int = 0,
    val lastObservedMedia: String? = null,
    val effort: PostRunEffort? = null
)

/** A single expanded saved record keeps exact reflection text readable on a phone. */
@Composable
internal fun RunHistoryScreen(
    items: List<RunHistoryItem>,
    onBack: () -> Unit,
    onRetryReflection: (String) -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    error: String? = null,
    speakerFactory: (Context) -> ReflectionSpeaker = { AndroidReflectionSpeaker(it) },
    onSelectEffort: (String, PostRunEffort?) -> Unit = { _, _ -> },
    effortSavingRunId: String? = null,
    effortError: Pair<String, String>? = null
) {
    var expandedRunId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingEffortRunId by rememberSaveable { mutableStateOf<String?>(null) }
    var speaker by remember { mutableStateOf<ReflectionSpeaker?>(null) }
    val context = LocalContext.current

    DisposableEffect(Unit) {
        // Read the current speaker on exit, including one created by the last tap before recomposition.
        onDispose { speaker?.close() }
    }
    LaunchedEffect(items, expandedRunId) {
        val openItem = items.firstOrNull { it.runId == expandedRunId }
        if (expandedRunId != null &&
            (openItem?.reflectionStatus != "READY" || openItem?.reflectionText.isNullOrBlank())
        ) {
            speaker?.stop()
        }
    }
    LaunchedEffect(items, editingEffortRunId) {
        if (editingEffortRunId != null && items.none { it.runId == editingEffortRunId }) {
            editingEffortRunId = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag(RUN_HISTORY_SCREEN_TAG)
            .background(Brush.verticalGradient(listOf(CompletionSurfaceTop, CompletionSurfaceBottom)))
    ) {
        CompletionAtmosphere(Modifier.fillMaxSize())
        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
                TextButton(
                    onClick = {
                        speaker?.stop()
                        onBack()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = CompletionStrongInk),
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 12.dp),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Back to run")
                }
                Text(
                    text = "LOG HISTORY",
                    color = CompletionStrongInk,
                    fontSize = 28.sp,
                    fontFamily = CompletionNumberFont,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.1.sp,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    text = "Your saved runs",
                    color = CompletionMutedInk,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 5.dp)
                )
            }

            LazyColumn(
                contentPadding = PaddingValues(start = 24.dp, top = 10.dp, end = 24.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) {
                if (isLoading) {
                    item(key = "loading") {
                        HistoryMessage("Loading your saved runs…")
                    }
                }
                if (error != null) {
                    item(key = "error") {
                        HistoryMessage(error)
                    }
                }
                if (!isLoading && error == null && items.isEmpty()) {
                    item(key = "empty") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White.copy(alpha = 0.65f), RoundedCornerShape(22.dp))
                                .padding(22.dp)
                        ) {
                            Text(
                                text = "Your first run starts the story.",
                                color = CompletionStrongInk,
                                fontSize = 23.sp,
                                lineHeight = 30.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Complete a run and it will appear here with its saved details and reflection.",
                                color = CompletionMutedInk,
                                fontSize = 14.sp,
                                lineHeight = 21.sp,
                                modifier = Modifier.padding(top = 10.dp)
                            )
                        }
                    }
                }
                items(items = items, key = { it.runId }) { item ->
                    HistoryRow(
                        item = item,
                        expanded = expandedRunId == item.runId,
                        onToggle = {
                            speaker?.stop()
                            if (expandedRunId == item.runId) {
                                expandedRunId = null
                            } else {
                                expandedRunId = item.runId
                                val exactText = item.reflectionText
                                if (item.reflectionStatus == "READY" && !exactText.isNullOrBlank()) {
                                    val activeSpeaker = speaker ?: speakerFactory(context).also { speaker = it }
                                    activeSpeaker.speak(exactText)
                                }
                            }
                        },
                        onRetryReflection = { onRetryReflection(item.runId) },
                        onEditEffort = { editingEffortRunId = item.runId },
                        effortSaving = effortSavingRunId == item.runId,
                        effortError = effortError?.takeIf { it.first == item.runId }?.second
                    )
                }
            }
        }
        val editingItem = items.firstOrNull { it.runId == editingEffortRunId }
        if (editingItem != null) {
            EffortChoiceSheet(
                selectedEffort = editingItem.effort,
                enabled = effortSavingRunId != editingItem.runId,
                onDismiss = { editingEffortRunId = null },
                onSelect = { effort ->
                    editingEffortRunId = null
                    onSelectEffort(editingItem.runId, effort)
                }
            )
        }
    }
}

@Composable
private fun HistoryMessage(text: String) {
    Text(
        text = text,
        color = CompletionMutedInk,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp)
    )
}

@Composable
private fun HistoryRow(
    item: RunHistoryItem,
    expanded: Boolean,
    onToggle: () -> Unit,
    onRetryReflection: () -> Unit,
    onEditEffort: () -> Unit,
    effortSaving: Boolean,
    effortError: String?
) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White.copy(alpha = if (expanded) 0.88f else 0.62f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(RUN_HISTORY_ROW_TAG_PREFIX + item.runId)
                .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
                .clickable(
                    role = Role.Button,
                    onClickLabel = if (expanded) "Close run details" else "Open run details",
                    onClick = onToggle
                )
                .padding(20.dp)
        ) {
            Text(
                text = item.dateLabel,
                color = CompletionMutedInk,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 18.sp
            )
            Text(
                text = item.metrics?.distance ?: "—",
                color = CompletionStrongInk,
                fontFamily = CompletionNumberFont,
                fontSize = 38.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 44.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                text = "${item.metrics?.elapsed ?: "—"} time · ${item.metrics?.averagePace ?: "—"} pace",
                color = CompletionInk,
                fontSize = 13.sp,
                lineHeight = 20.sp
            )
            Text(
                text = item.metrics?.sourceLabel ?: "Metrics unavailable",
                color = CompletionMutedInk,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when (item.reflectionStatus) {
                        "READY" -> "Reflection saved"
                        "PENDING" -> "Reflection pending"
                        "FAILED" -> "Reflection unavailable"
                        else -> "Saved run"
                    },
                    color = CompletionGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f).padding(end = 10.dp)
                )
                Text(
                    text = if (expanded) "CLOSE −" else "OPEN +",
                    color = CompletionMutedInk,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.7.sp
                )
            }
        }
        if (expanded) {
            Column(modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 22.dp)) {
                Text(
                    text = "Active time · ${item.metrics?.active ?: "—"}",
                    color = CompletionMutedInk,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
                if (item.preRunEnergyLabel != null) {
                    Text(
                        text = "Before run · ${item.preRunEnergyLabel}",
                        color = CompletionStrongInk,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
                if (item.energyLabel != null) {
                    Text(
                        text = "After run · ${item.energyLabel}",
                        color = CompletionStrongInk,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
                TextButton(
                    onClick = onEditEffort,
                    enabled = !effortSaving,
                    colors = ButtonDefaults.textButtonColors(contentColor = CompletionMutedInk),
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 10.dp),
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .heightIn(min = 48.dp)
                        .testTag(RUN_HISTORY_EFFORT_TAG_PREFIX + item.runId)
                ) {
                    Text(
                        text = item.effort?.let { "EFFORT · ${it.displayLabel().uppercase()}" } ?: "EFFORT +",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.1.sp
                    )
                }
                if (effortSaving) {
                    Text("Saving Effort…", color = CompletionMutedInk, fontSize = 12.sp)
                }
                if (effortError != null) {
                    Text(effortError, color = CompletionStrongInk, fontSize = 12.sp, lineHeight = 18.sp)
                }
                if (item.musicObservationRequested) {
                    Text(
                        text = "OBSERVED MEDIA · PARTIAL",
                        color = CompletionGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.1.sp,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                    Text(
                        text = if (item.musicObservationCount == 0) {
                            "No media session was observed. Music remains unknown."
                        } else {
                            "Last observed · ${item.lastObservedMedia ?: "Unknown media"}"
                        },
                        color = CompletionStrongInk,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(top = 5.dp)
                    )
                    if (item.musicObservationCount > 0) {
                        Text(
                            text = "${item.musicObservationCount} media-session updates saved. This may not be the full soundtrack.",
                            color = CompletionMutedInk,
                            fontSize = 11.sp,
                            lineHeight = 17.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                if (item.isTestData) {
                    Text(
                        text = "FAKE BACKEND · TEST DATA",
                        color = CompletionGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.1.sp,
                        modifier = Modifier.padding(top = 18.dp)
                    )
                }
                Text(
                    text = when {
                        item.reflectionStatus == "READY" && !item.reflectionText.isNullOrBlank() -> item.reflectionText
                        item.reflectionStatus == "PENDING" -> "Your run is saved. Its reflection is still being prepared."
                        item.reflectionStatus == "FAILED" -> "Your run is saved. The reflection couldn’t be prepared."
                        item.reflectionStatus == null -> "No reflection saved for this run."
                        else -> "Reflection unavailable. Your run is saved."
                    },
                    color = CompletionStrongInk,
                    fontSize = 19.sp,
                    lineHeight = 28.sp,
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .testTag(RUN_HISTORY_REFLECTION_TAG_PREFIX + item.runId)
                )
                if (item.reflectionStatus == "FAILED") {
                    OutlinedButton(
                        onClick = onRetryReflection,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CompletionStrongInk),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .padding(top = 16.dp)
                            .heightIn(min = 48.dp)
                            .testTag(RUN_HISTORY_RETRY_TAG_PREFIX + item.runId)
                    ) {
                        Text("Retry reflection")
                    }
                }
            }
        }
    }
}
