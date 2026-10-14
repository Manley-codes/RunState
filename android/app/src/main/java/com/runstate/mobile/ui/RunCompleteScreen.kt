package com.runstate.mobile.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runstate.mobile.data.local.ReflectionBranch
import com.runstate.mobile.data.local.PostRunEffort
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

internal const val RUN_COMPLETE_SCREEN_TAG = "runCompleteScreen"
internal const val RUN_COMPLETE_DISTANCE_TAG = "runCompleteDistance"
internal const val RUN_COMPLETE_VISUALIZER_TAG = "runCompleteVisualizer"
internal const val RUN_COMPLETE_METRICS_TAG = "runCompleteMetrics"
internal const val RUN_COMPLETE_REFLECTION_TAG = "runCompleteReflection"
internal const val RUN_COMPLETE_REFLECTION_TEXT_TAG = "runCompleteReflectionText"
internal const val RUN_COMPLETE_REFLECTION_RETRY_TAG = "runCompleteReflectionRetry"
internal const val RUN_COMPLETE_ENERGY_TAG = "runCompleteEnergy"
internal const val RUN_COMPLETE_EFFORT_TAG = "runCompleteEffort"
internal const val RUN_COMPLETE_EFFORT_SHEET_TAG = "runCompleteEffortSheet"
internal const val RUN_COMPLETE_EFFORT_CHOICE_TAG_PREFIX = "runCompleteEffortChoice:"
internal const val RUN_COMPLETE_HISTORY_TAG = "runCompleteHistory"
internal const val RUN_COMPLETE_AUDIO_STOP_TAG = "runCompleteAudioStop"

internal val CompletionInk = Color(0xFF4A5049)
internal val CompletionStrongInk = Color(0xFF353A34)
internal val CompletionMutedInk = Color(0xFF61685E)
internal val CompletionGreen = Color(0xFF55704C)
private val CompletionBlue = Color(0xFFAFCEDA)
internal val CompletionSurfaceTop = Color(0xFFFCFCFA)
internal val CompletionSurfaceBottom = Color(0xFFF4F5F1)

/** The only reflection information the completion UI is allowed to know. */
internal sealed interface RunCompleteReflectionDisplay {
    data object Preparing : RunCompleteReflectionDisplay
    data class Ready(val text: String) : RunCompleteReflectionDisplay
    data object Failed : RunCompleteReflectionDisplay
    data object ConnectionNeeded : RunCompleteReflectionDisplay
}

/**
 * Android ships a condensed sans family. Prefer it for instrument-like numbers and fall
 * back to the ordinary system sans if an OEM does not provide it under the AOSP name.
 */
internal val CompletionNumberFont = FontFamily(
    Font(DeviceFontFamilyName("sans-serif-condensed"), weight = FontWeight.Normal),
    Font(DeviceFontFamilyName("sans-serif-condensed"), weight = FontWeight.Medium),
    Font(DeviceFontFamilyName("sans-serif"), weight = FontWeight.Normal),
    Font(DeviceFontFamilyName("sans-serif"), weight = FontWeight.Medium)
)

/**
 * The saved-run presentation, translated from the working Run Complete reference.
 *
 * The layout deliberately consumes only facts already supplied by the lifecycle foundation.
 * The visualizer is a static completed-state mark: it does not claim music, reflection or live
 * analysis. A missing read remains an em dash, and the fixed action stays reachable while the
 * factual content scrolls behind it on short screens or with large system text.
 */
@Composable
internal fun RunCompleteScreen(
    metrics: RunMetricsDisplay?,
    reflection: RunCompleteReflectionDisplay?,
    onRetryReflection: () -> Unit,
    onStartAnother: () -> Unit,
    actionsEnabled: Boolean,
    modifier: Modifier = Modifier,
    selectedEnergy: ReflectionBranch? = null,
    onSelectEnergy: (ReflectionBranch) -> Unit = {},
    energyEnabled: Boolean = false,
    isTestData: Boolean = true,
    onViewHistory: () -> Unit = {},
    showHistory: Boolean = false,
    selectedEffort: PostRunEffort? = null,
    onSelectEffort: (PostRunEffort?) -> Unit = {},
    effortEnabled: Boolean = false,
    effortSaving: Boolean = false,
    effortError: String? = null,
    onEffortSheetOpenChange: (Boolean) -> Unit = {},
    reflectionAudioActive: Boolean = false,
    onStopReflectionAudio: () -> Unit = {}
) {
    val distance = splitMetric(metrics?.distance ?: "—")
    val pace = splitMetric(metrics?.averagePace ?: "—")
    val sourceLabel = metrics?.sourceLabel ?: "Metrics unavailable"
    var effortSheetOpen by rememberSaveable { mutableStateOf(false) }
    val currentSheetOpenChange by rememberUpdatedState(onEffortSheetOpenChange)

    // Restored sheets continue holding the quiet no-Energy window after rotation.
    LaunchedEffect(effortSheetOpen) { currentSheetOpenChange(effortSheetOpen) }
    DisposableEffect(Unit) {
        onDispose { currentSheetOpenChange(false) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag(RUN_COMPLETE_SCREEN_TAG)
            .background(
                Brush.verticalGradient(
                    colors = listOf(CompletionSurfaceTop, CompletionSurfaceBottom)
                )
            )
    ) {
        CompletionAtmosphere(Modifier.fillMaxSize())

        Column(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 26.dp, top = 24.dp, end = 22.dp, bottom = 24.dp)
            ) {
                SavedStatus(sourceLabel)
                Spacer(modifier = Modifier.height(24.dp))
                CompletionHero(
                    distance = distance,
                    elapsed = metrics?.elapsed ?: "—",
                    pace = pace,
                    active = metrics?.active ?: "—"
                )
                if (energyEnabled) {
                    EnergyChoices(
                        selectedEnergy = selectedEnergy,
                        onSelectEnergy = onSelectEnergy,
                        enabled = actionsEnabled,
                        reflectionFinal = reflection is RunCompleteReflectionDisplay.Ready
                    )
                }
                if (effortEnabled) {
                    EffortControl(
                        selectedEffort = selectedEffort,
                        saving = effortSaving,
                        error = effortError,
                        enabled = actionsEnabled,
                        onOpen = { effortSheetOpen = true }
                    )
                }
                if (reflection != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    ReflectionPanel(
                        reflection = reflection,
                        onRetry = onRetryReflection,
                        retryEnabled = actionsEnabled,
                        isTestData = isTestData
                    )
                    if (reflectionAudioActive) {
                        Text(
                            "Voice, then a short RunState instrumental (Eleven Music)",
                            color = CompletionMutedInk,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                        TextButton(
                            onClick = onStopReflectionAudio,
                            colors = ButtonDefaults.textButtonColors(contentColor = CompletionStrongInk),
                            modifier = Modifier.heightIn(min = 48.dp).testTag(RUN_COMPLETE_AUDIO_STOP_TAG)
                        ) { Text("Stop audio") }
                    }
                }
                if (showHistory) {
                    TextButton(
                        onClick = onViewHistory,
                        enabled = actionsEnabled,
                        colors = ButtonDefaults.textButtonColors(contentColor = CompletionStrongInk),
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .align(Alignment.CenterHorizontally)
                            .heightIn(min = 48.dp)
                            .testTag(RUN_COMPLETE_HISTORY_TAG)
                    ) {
                        Text("View in Log History")
                    }
                }
            }

            Button(
                onClick = onStartAnother,
                enabled = actionsEnabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CompletionStrongInk,
                    contentColor = Color.White,
                    disabledContainerColor = CompletionStrongInk.copy(alpha = 0.38f),
                    disabledContentColor = Color.White.copy(alpha = 0.7f)
                ),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp)
                    .heightIn(min = 56.dp)
            ) {
                Text(
                    text = "Start another run",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.2.sp
                )
            }
        }

        if (effortSheetOpen && effortEnabled) {
            EffortChoiceSheet(
                selectedEffort = selectedEffort,
                enabled = actionsEnabled && !effortSaving,
                onDismiss = { effortSheetOpen = false },
                onSelect = { effort ->
                    effortSheetOpen = false
                    onSelectEffort(effort)
                }
            )
        }
    }
}

internal fun PostRunEffort.displayLabel(): String = when (this) {
    PostRunEffort.SMOOTH -> "Smooth"
    PostRunEffort.WORKING -> "Working"
    PostRunEffort.HEAVY -> "Heavy"
    PostRunEffort.EMPTY_TANK -> "Empty Tank"
}

/** Effort stays smaller than Energy and never implies that an answer is required. */
@Composable
private fun EffortControl(
    selectedEffort: PostRunEffort?,
    saving: Boolean,
    error: String?,
    enabled: Boolean,
    onOpen: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
        TextButton(
            onClick = onOpen,
            enabled = enabled && !saving,
            colors = ButtonDefaults.textButtonColors(contentColor = CompletionMutedInk),
            modifier = Modifier.heightIn(min = 48.dp).testTag(RUN_COMPLETE_EFFORT_TAG)
        ) {
            Text(
                text = selectedEffort?.let { "EFFORT · ${it.displayLabel().uppercase()}" } ?: "EFFORT +",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.2.sp
            )
        }
        if (saving) {
            Text("Saving Effort…", color = CompletionMutedInk, fontSize = 12.sp)
        }
        if (error != null) {
            Text(error, color = CompletionStrongInk, fontSize = 12.sp, lineHeight = 18.sp)
        }
    }
}

/** Shared by completion and expanded History; selecting only reports a value to the owner. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EffortChoiceSheet(
    selectedEffort: PostRunEffort?,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onSelect: (PostRunEffort?) -> Unit,
    modifier: Modifier = Modifier
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = CompletionSurfaceTop,
        modifier = modifier.testTag(RUN_COMPLETE_EFFORT_SHEET_TAG)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "How did that run land?",
                color = CompletionStrongInk,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            PostRunEffort.entries.forEach { effort ->
                OutlinedButton(
                    onClick = { onSelect(effort) },
                    enabled = enabled,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CompletionStrongInk),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag(RUN_COMPLETE_EFFORT_CHOICE_TAG_PREFIX + effort.name)
                ) {
                    Text(
                        text = effort.displayLabel(),
                        fontWeight = if (effort == selectedEffort) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
            if (selectedEffort != null) {
                TextButton(
                    onClick = { onSelect(null) },
                    enabled = enabled,
                    colors = ButtonDefaults.textButtonColors(contentColor = CompletionMutedInk),
                    modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = 48.dp)
                ) {
                    Text("Clear Effort")
                }
            }
        }
    }
}

/**
 * Selected text is supplied by the saved-run owner. Fake-mode disclosure remains attached
 * in every state, while ordinary generation uses the same honest save/failure treatment.
 */
@Composable
private fun ReflectionPanel(
    reflection: RunCompleteReflectionDisplay,
    onRetry: () -> Unit,
    retryEnabled: Boolean,
    isTestData: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(RUN_COMPLETE_REFLECTION_TAG)
            .background(
                color = Color.White.copy(alpha = 0.62f),
                shape = RoundedCornerShape(22.dp)
            )
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Text(
            text = "REFLECTION",
            color = CompletionMutedInk,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.9.sp
        )
        if (isTestData) {
            Text(
                text = "FAKE BACKEND · TEST DATA",
                color = CompletionGreen,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.25.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        when (reflection) {
            RunCompleteReflectionDisplay.Preparing -> {
                Text(
                    text = "Preparing your reflection…",
                    color = CompletionStrongInk,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 27.sp,
                    modifier = Modifier.padding(top = 18.dp)
                )
                Text(
                    text = "Your run is already saved.",
                    color = CompletionMutedInk,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            is RunCompleteReflectionDisplay.Ready -> Text(
                text = reflection.text,
                color = CompletionStrongInk,
                fontSize = 21.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 29.sp,
                modifier = Modifier
                    .padding(top = 18.dp)
                    .testTag(RUN_COMPLETE_REFLECTION_TEXT_TAG)
            )

            RunCompleteReflectionDisplay.ConnectionNeeded -> {
                Text("Your run is saved on this phone.", color = CompletionStrongInk,
                    fontSize = 19.sp, lineHeight = 26.sp, modifier = Modifier.padding(top = 18.dp))
                Text("Energy and Effort can still be recorded here. Set up reflections on Start, then retry this run in Log History.",
                    color = CompletionMutedInk, fontSize = 13.sp, lineHeight = 19.sp,
                    modifier = Modifier.padding(top = 6.dp))
            }
            RunCompleteReflectionDisplay.Failed -> {
                Text(
                    text = "Reflection couldn’t be prepared.",
                    color = CompletionStrongInk,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 26.sp,
                    modifier = Modifier.padding(top = 18.dp)
                )
                Text(
                    text = "Your run is saved. You can retry the reflection when a connection is available.",
                    color = CompletionMutedInk,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
                OutlinedButton(
                    onClick = onRetry,
                    enabled = retryEnabled,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .padding(top = 14.dp)
                        .testTag(RUN_COMPLETE_REFLECTION_RETRY_TAG)
                ) {
                    Text("Retry reflection")
                }
            }
        }
    }
}

/** Energy is optional and remains editable after the reflection has become permanent. */
@Composable
private fun EnergyChoices(
    selectedEnergy: ReflectionBranch?,
    onSelectEnergy: (ReflectionBranch) -> Unit,
    enabled: Boolean,
    reflectionFinal: Boolean
) {
    val choices = listOf(
        ReflectionBranch.SPENT to "SPENT",
        ReflectionBranch.FEELING_GOOD to "FEELING GOOD",
        ReflectionBranch.POWERED_UP to "POWERED UP"
    )
    val largeText = LocalDensity.current.fontScale > 1.3f

    Column(modifier = Modifier.fillMaxWidth().testTag(RUN_COMPLETE_ENERGY_TAG)) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            if (largeText || maxWidth < 300.dp) {
                Column(
                    modifier = Modifier.fillMaxWidth().selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    choices.forEach { (branch, label) ->
                        EnergyChoice(
                            label = label,
                            selected = selectedEnergy == branch,
                            enabled = enabled,
                            onClick = { onSelectEnergy(branch) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    choices.forEach { (branch, label) ->
                        EnergyChoice(
                            label = label,
                            selected = selectedEnergy == branch,
                            enabled = enabled,
                            onClick = { onSelectEnergy(branch) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
        Text(
            text = if (reflectionFinal) {
                "You can update Energy. Your reflection stays the same."
            } else {
                "Optional · How you feel now"
            },
            color = CompletionMutedInk,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
        )
    }
}

@Composable
private fun EnergyChoice(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(if (selected) CompletionStrongInk else Color.White.copy(alpha = 0.76f), shape)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 76.dp)
            .padding(horizontal = 12.dp, vertical = 15.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else CompletionStrongInk,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 17.sp,
            letterSpacing = 0.6.sp
        )
    }
}

/** The reference uses one quiet line rather than a badge or a second page title. */
@Composable
private fun SavedStatus(sourceLabel: String) {
    val largeText = LocalDensity.current.fontScale > 1.35f

    if (largeText) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "RUN SAVED",
                color = CompletionGreen,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.2.sp
            )
            Text(
                text = sourceLabel.uppercase(),
                color = CompletionMutedInk,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.8.sp
            )
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "RUN SAVED",
                color = CompletionGreen,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.2.sp
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = 10.dp)
                    .size(5.dp)
                    .background(CompletionBlue, RoundedCornerShape(50))
            )
            Text(
                text = sourceLabel.uppercase(),
                color = CompletionMutedInk,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.7.sp
            )
        }
    }
}

@Composable
private fun CompletionHero(
    distance: MetricParts,
    elapsed: String,
    pace: MetricParts,
    active: String
) {
    val largeText = LocalDensity.current.fontScale > 1.35f

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (!largeText && maxWidth >= 320.dp) {
            OffsetCompletionHero(
                distance = distance,
                elapsed = elapsed,
                pace = pace,
                active = active
            )
        } else {
            AccessibleCompletionHero(
                distance = distance,
                elapsed = elapsed,
                pace = pace,
                active = active
            )
        }
    }
}

/** Preserve the offset composition when its measured text leaves room for the completion mark. */
@Composable
private fun OffsetCompletionHero(
    distance: MetricParts,
    elapsed: String,
    pace: MetricParts,
    active: String
) {
    Layout(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 246.dp),
        content = {
            DistanceInstrument(distance = distance)
            CompactMetrics(
                elapsed = elapsed,
                pace = pace,
                active = active
            )
            CompletionVisualizer(modifier = Modifier.size(80.dp))
        }
    ) { measurables, constraints ->
        val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val distancePlaceable = measurables[0].measure(looseConstraints)
        // Intrinsic widths round each text run independently; leave a small amount of
        // actual room for OEM glyph/letter-spacing rounding before measuring the rows.
        val metricsWidth = (
            measurables[1].maxIntrinsicWidth(looseConstraints.maxHeight) + 4.dp.roundToPx()
        ).coerceAtMost(constraints.maxWidth)
        val metricsPlaceable = measurables[1].measure(
            looseConstraints.copy(minWidth = metricsWidth, maxWidth = metricsWidth)
        )
        val markPlaceable = measurables[2].measure(looseConstraints)
        val width = constraints.maxWidth
        val gap = 12.dp.roundToPx()
        val distanceY = 18.dp.roundToPx()
        val metricsX = width - metricsPlaceable.width
        val metricsY = if (distancePlaceable.width + gap <= metricsX) {
            35.dp.roundToPx()
        } else {
            distanceY + distancePlaceable.height + gap
        }

        // OEM fonts and timer digits can consume the original fixed central gap.
        // Put decoration below the facts when it cannot fit without covering text.
        val markFitsBetween = distancePlaceable.width + gap + markPlaceable.width + gap <= metricsX
        val preferredMarkX = (width - markPlaceable.width) / 2 + 16.dp.roundToPx()
        val markX = if (markFitsBetween) {
            preferredMarkX.coerceIn(
                distancePlaceable.width + gap,
                metricsX - gap - markPlaceable.width
            )
        } else {
            (width - markPlaceable.width) / 2
        }
        val factsBottom = maxOf(
            distanceY + distancePlaceable.height,
            metricsY + metricsPlaceable.height
        )
        val markY = if (markFitsBetween) distanceY else factsBottom + gap
        val height = constraints.constrainHeight(maxOf(factsBottom, markY + markPlaceable.height))

        layout(width, height) {
            distancePlaceable.placeRelative(0, distanceY)
            metricsPlaceable.placeRelative(metricsX, metricsY)
            markPlaceable.placeRelative(markX, markY)
        }
    }
}

/** Large text gets a linear reading order instead of being forced through the overlap. */
@Composable
private fun AccessibleCompletionHero(
    distance: MetricParts,
    elapsed: String,
    pace: MetricParts,
    active: String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        DistanceInstrument(distance = distance)
        CompletionVisualizer(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(vertical = 18.dp)
                .size(116.dp)
        )
        CompactMetrics(
            elapsed = elapsed,
            pace = pace,
            active = active,
            modifier = Modifier.fillMaxWidth(),
            generousSpacing = true
        )
    }
}

@Composable
private fun DistanceInstrument(
    distance: MetricParts,
    modifier: Modifier = Modifier
) {
    val numberSize = when {
        distance.value.length >= 5 -> 72.sp
        distance.value.length == 4 -> 82.sp
        else -> 98.sp
    }
    val numberLineHeight = when {
        distance.value.length >= 5 -> 70.sp
        distance.value.length == 4 -> 80.sp
        else -> 92.sp
    }

    Column(modifier = modifier.testTag(RUN_COMPLETE_DISTANCE_TAG)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = distance.value,
                color = CompletionInk,
                fontFamily = CompletionNumberFont,
                fontSize = numberSize,
                fontWeight = FontWeight.Normal,
                lineHeight = numberLineHeight,
                letterSpacing = (-1.4).sp,
                maxLines = 1
            )
            if (distance.unit.isNotEmpty()) {
                Text(
                    text = distance.unit.uppercase(),
                    color = CompletionMutedInk,
                    fontFamily = CompletionNumberFont,
                    fontSize = if (distance.value.length >= 4) 24.sp else 28.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 6.dp, bottom = 10.dp)
                )
            }
        }
        DistanceRuler(
            modifier = Modifier
                .padding(top = 8.dp)
                .width(174.dp)
                .height(14.dp)
        )
    }
}

@Composable
private fun DistanceRuler(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val tickCount = 29
        val spacing = size.width / (tickCount - 1)
        repeat(tickCount) { index ->
            val strong = index == tickCount / 2
            val height = when {
                strong -> size.height
                index % 5 == 0 -> size.height * 0.68f
                else -> size.height * 0.43f
            }
            drawLine(
                color = if (strong) CompletionGreen else CompletionMutedInk.copy(alpha = 0.2f),
                start = Offset(index * spacing, size.height - height),
                end = Offset(index * spacing, size.height),
                strokeWidth = if (strong) 1.5.dp.toPx() else 1.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun CompactMetrics(
    elapsed: String,
    pace: MetricParts,
    active: String,
    modifier: Modifier = Modifier,
    generousSpacing: Boolean = false
) {
    Column(
        modifier = modifier.testTag(RUN_COMPLETE_METRICS_TAG),
        verticalArrangement = Arrangement.spacedBy(if (generousSpacing) 14.dp else 10.dp)
    ) {
        CompactMetric(value = elapsed, label = "TIME", valueSize = 22.sp)
        CompactMetric(value = pace.value, unit = pace.unit, label = "PACE", valueSize = 29.sp)
        CompactMetric(value = active, label = "ACTIVE", valueSize = 22.sp)
    }
}

@Composable
private fun CompactMetric(
    value: String,
    label: String,
    valueSize: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier,
    unit: String = ""
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = value,
            color = CompletionInk,
            fontFamily = CompletionNumberFont,
            fontSize = valueSize,
            fontWeight = FontWeight.Medium,
            lineHeight = valueSize,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip
        )
        if (unit.isNotEmpty()) {
            Text(
                text = unit.uppercase(),
                color = CompletionMutedInk,
                fontFamily = CompletionNumberFont,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(start = 2.dp, bottom = 2.dp)
            )
        }
        Text(
            text = label,
            color = CompletionMutedInk,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.5.sp,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(start = 8.dp, bottom = 3.dp)
        )
    }
}

/** A neutral completion mark; static because reflection text does not imply live analysis. */
@Composable
private fun CompletionVisualizer(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.testTag(RUN_COMPLETE_VISUALIZER_TAG)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val outerRadius = min(size.width, size.height) * 0.48f
        val innerRadius = outerRadius * 0.78f
        val tickCount = 64

        repeat(tickCount) { index ->
            val angle = (index.toFloat() / tickCount.toFloat()) * 2f * PI.toFloat() - PI.toFloat() / 2f
            val color = when (index) {
                in 18..28 -> Color(0xFFC7D7A5)
                in 29..39 -> Color(0xFFC5C8E7)
                in 40..49 -> Color(0xFFAFCEDD)
                else -> CompletionMutedInk.copy(alpha = 0.28f)
            }
            val start = Offset(
                x = center.x + cos(angle) * innerRadius,
                y = center.y + sin(angle) * innerRadius
            )
            val end = Offset(
                x = center.x + cos(angle) * outerRadius,
                y = center.y + sin(angle) * outerRadius
            )
            drawLine(
                color = color,
                start = start,
                end = end,
                strokeWidth = 1.2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        drawCircle(
            color = CompletionSurfaceTop.copy(alpha = 0.78f),
            radius = innerRadius * 0.82f,
            center = center
        )
    }
}

/** Fine structure and pale facets recreate the reference's layered porcelain field. */
@Composable
internal fun CompletionAtmosphere(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFE9EEE4).copy(alpha = 0.55f), Color.Transparent),
                center = Offset(size.width * 0.82f, size.height * 0.18f),
                radius = size.width * 0.78f
            )
        )

        val gridColor = CompletionInk.copy(alpha = 0.026f)
        val gridStep = 32.dp.toPx()
        var x = 0f
        while (x <= size.width) {
            drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
            x += gridStep
        }
        var y = 0f
        while (y <= size.height) {
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            y += gridStep
        }

        val topFacet = Path().apply {
            moveTo(size.width * 0.67f, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width, size.height * 0.17f)
            lineTo(size.width * 0.55f, size.height * 0.22f)
            close()
        }
        drawPath(topFacet, Color(0xFFDDE5D8).copy(alpha = 0.55f))

        val topHighlight = Path().apply {
            moveTo(size.width * 0.78f, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width, size.height * 0.12f)
            close()
        }
        drawPath(topHighlight, Color.White.copy(alpha = 0.58f))

        val middleFacet = Path().apply {
            moveTo(size.width * 0.58f, size.height * 0.39f)
            lineTo(size.width, size.height * 0.47f)
            lineTo(size.width * 0.77f, size.height * 0.68f)
            close()
        }
        drawPath(middleFacet, Color(0xFFEAE8D7).copy(alpha = 0.44f))

        val lowerFacet = Path().apply {
            moveTo(size.width * 0.72f, size.height * 0.56f)
            lineTo(size.width, size.height * 0.61f)
            lineTo(size.width * 0.88f, size.height * 0.78f)
            close()
        }
        drawPath(lowerFacet, Color(0xFFE1E7E1).copy(alpha = 0.32f))
    }
}

private data class MetricParts(val value: String, val unit: String)

/** Keeps the formatter's honest em dash intact while allowing a real unit to sit quietly beside it. */
private fun splitMetric(formattedMetric: String): MetricParts {
    val separator = formattedMetric.lastIndexOf(' ')
    return if (separator > 0 && separator < formattedMetric.lastIndex) {
        MetricParts(
            value = formattedMetric.substring(0, separator),
            unit = formattedMetric.substring(separator + 1)
        )
    } else {
        MetricParts(value = formattedMetric, unit = "")
    }
}
