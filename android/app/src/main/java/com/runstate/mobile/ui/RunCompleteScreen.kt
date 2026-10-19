package com.runstate.mobile.ui

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.runstate.mobile.data.local.ReflectionBranch
import com.runstate.mobile.R
import com.runstate.mobile.data.local.PostRunEffort
import com.runstate.mobile.data.local.PreRunEnergy
import kotlin.math.roundToInt

internal const val RUN_COMPLETE_SCREEN_TAG = "runCompleteScreen"
internal const val RUN_COMPLETE_DISTANCE_TAG = "runCompleteDistance"
internal const val RUN_COMPLETE_VISUALIZER_TAG = "runCompleteVisualizer"
internal const val RUN_COMPLETE_METRICS_TAG = "runCompleteMetrics"
internal const val RUN_COMPLETE_REFLECTION_TAG = "runCompleteReflection"
internal const val RUN_COMPLETE_REFLECTION_TEXT_TAG = "runCompleteReflectionText"
internal const val RUN_COMPLETE_REFLECTION_RETRY_TAG = "runCompleteReflectionRetry"
internal const val RUN_COMPLETE_ENERGY_TAG = "runCompleteEnergy"
internal const val RUN_COMPLETE_ENERGY_INTAKE_TAG = "runCompleteEnergyIntake"
internal const val RUN_COMPLETE_EFFORT_TAG = "runCompleteEffort"
internal const val RUN_COMPLETE_EFFORT_SHEET_TAG = "runCompleteEffortSheet"
internal const val RUN_COMPLETE_EFFORT_CHOICE_TAG_PREFIX = "runCompleteEffortChoice:"
internal const val RUN_COMPLETE_HISTORY_TAG = "runCompleteHistory"
internal const val RUN_COMPLETE_AUDIO_STOP_TAG = "runCompleteAudioStop"
internal const val RUN_COMPLETE_SAVE_ACTIVITY_TAG = "runCompleteSaveActivity"
internal const val RUN_COMPLETE_NOTE_TAG = "runCompleteNote"
internal const val RUN_COMPLETE_NOTE_SAVE_TAG = "runCompleteNoteSave"
internal const val RUN_ACTIVITY_RECAP_TAG = "runActivityRecap"

internal val CompletionInk = Color(0xFF4A5049)
internal val CompletionStrongInk = Color(0xFF2D3832)
internal val CompletionMutedInk = Color(0xFF61685E)
internal val CompletionGreen = Color(0xFF55704C)
private val CompletionBlue = Color(0xFFAFCEDA)
private val CompletionWarmAccent = Color(0xFFA47B44)
internal val CompletionSurfaceTop = Color(0xFFFCFDFC)
internal val CompletionSurfaceBottom = Color(0xFFF0F4F4)

/** A tap-only visual snapshot, separate from the owner's saved Energy and reply state. */
private class CompletionEnergyIntake(
    val branch: ReflectionBranch,
    val tiles: Map<ReflectionBranch, Rect>,
    val target: Offset
)

private val CompletionEnergyBranches = listOf(
    ReflectionBranch.SPENT, ReflectionBranch.FEELING_GOOD, ReflectionBranch.POWERED_UP
)
private val CompletionIntakeMarkEase = CubicBezierEasing(0.32f, 0.72f, 0.3f, 1f)
private val CompletionIntakeMergeEase = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
private val CompletionIntakeTravelEase = CubicBezierEasing(0.5f, 0.02f, 0.28f, 1f)
private val CompletionIntakeFadeEase = CubicBezierEasing(0.4f, 0f, 1f, 1f)

/** The only reflection information the completion UI is allowed to know. */
internal sealed interface RunCompleteReflectionDisplay {
    data object Preparing : RunCompleteReflectionDisplay
    data class Ready(val text: String) : RunCompleteReflectionDisplay
    data object Failed : RunCompleteReflectionDisplay
    data object ConnectionNeeded : RunCompleteReflectionDisplay
}

/**
 * Bundle the prototype's open-licensed numeral face so OEM font aliases cannot change
 * the distance and timing hierarchy. Both font files and their license ship with the app.
 */
internal val CompletionNumberFont = FontFamily(
    Font(R.font.barlow_condensed_regular, weight = FontWeight.Normal),
    Font(R.font.barlow_condensed_medium, weight = FontWeight.Medium)
)

/**
 * The saved-run presentation, translated from the working Run Complete reference.
 *
 * The layout deliberately consumes only facts already supplied by the lifecycle foundation.
 * The visualizer shares Start's decorative agent wave and receives tapped Energy choices;
 * its motion does not claim music playback or live analysis.
 * A missing read remains an em dash, and the fixed action stays reachable while the
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
    onStopReflectionAudio: () -> Unit = {},
    savedPreRunEnergy: PreRunEnergy? = null,
    completionDateLabel: String? = null,
    onReflectionPresentationHoldChange: (Boolean) -> Unit = {},
    weatherSummary: String? = null,
    reflectionSongLabel: String? = null,
    onSaveActivity: (() -> Unit)? = null,
    activitySaving: Boolean = false,
    activitySaveError: String? = null,
    localNote: String? = null,
    onSaveLocalNote: ((String?) -> Unit)? = null,
    noteSaving: Boolean = false,
    noteError: String? = null
) {
    val distance = splitMetric(metrics?.distance ?: "—")
    val pace = splitMetric(metrics?.averagePace ?: "—")
    val sourceLabel = metrics?.sourceLabel ?: "Metrics unavailable"
    var effortFollowupOpen by rememberSaveable { mutableStateOf(false) }
    var effortFollowupPending by rememberSaveable { mutableStateOf(false) }
    var noteOpen by rememberSaveable { mutableStateOf(false) }
    var noteDraft by rememberSaveable(localNote) { mutableStateOf(localNote.orEmpty()) }
    var energyChoicesOpen by rememberSaveable { mutableStateOf(false) }
    var energySubmitted by rememberSaveable { mutableStateOf(false) }
    val reflectionFinal = reflection is RunCompleteReflectionDisplay.Ready
    var previousReflectionFinal by rememberSaveable { mutableStateOf(reflectionFinal) }
    val currentSheetOpenChange by rememberUpdatedState(onEffortSheetOpenChange)
    val currentPresentationHoldChange by rememberUpdatedState(onReflectionPresentationHoldChange)
    var initialPresentationHeld by remember { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val view = LocalView.current
    var lifecycleStarted by remember(lifecycle) {
        mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    var animationsEnabled by remember(view) { mutableStateOf(ValueAnimator.areAnimatorsEnabled()) }
    var rootBounds by remember { mutableStateOf<Rect?>(null) }
    var ringBounds by remember { mutableStateOf<Rect?>(null) }
    val tileBounds = remember { mutableStateMapOf<ReflectionBranch, Rect>() }
    var intake by remember { mutableStateOf<CompletionEnergyIntake?>(null) }
    val intakeTime = remember { Animatable(0f) }

    DisposableEffect(lifecycle, view) {
        val observer = LifecycleEventObserver { _, event ->
            lifecycleStarted = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            animationsEnabled = ValueAnimator.areAnimatorsEnabled()
            if (event == Lifecycle.Event.ON_PAUSE || !lifecycleStarted) intake = null
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(intake, lifecycleStarted, animationsEnabled) {
        val current = intake
        if (current == null) {
            if (initialPresentationHeld) {
                initialPresentationHeld = false
                currentPresentationHoldChange(false)
            }
            return@LaunchedEffect
        }
        if (!lifecycleStarted || !animationsEnabled) {
            intake = null
            return@LaunchedEffect
        }
        intakeTime.snapTo(0f)
        intakeTime.animateTo(1630f, tween(1630, easing = LinearEasing))
        if (intake === current) intake = null
    }
    val ringVisible = rootBounds?.let { viewport -> ringBounds?.overlaps(viewport) } == true
    val ringPhase = rememberRunAgentPhase(lifecycleStarted && animationsEnabled && ringVisible)
    val ringModifier = Modifier.onGloballyPositioned { coordinates ->
        val bounds = coordinates.boundsInRoot()
        if (ringBounds != null && ringBounds != bounds) intake = null
        ringBounds = bounds
    }

    // The optional inline follow-up never holds or delays the reflection.
    LaunchedEffect(Unit) { currentSheetOpenChange(false) }
    LaunchedEffect(intake, effortFollowupPending, effortEnabled) {
        if (intake == null && effortFollowupPending && effortEnabled) {
            effortFollowupPending = false
            effortFollowupOpen = selectedEffort == null
        }
    }
    LaunchedEffect(effortError) {
        if (effortError != null) effortFollowupOpen = true
    }
    // This changes presentation only. The owner has already selected and saved the reply.
    LaunchedEffect(reflectionFinal) {
        if (reflectionFinal && !previousReflectionFinal) energyChoicesOpen = false
        previousReflectionFinal = reflectionFinal
    }
    DisposableEffect(Unit) {
        onDispose {
            currentSheetOpenChange(false)
            currentPresentationHoldChange(false)
        }
    }

    // A recorded choice and a finished provider response are separate states. Waiting
    // for the latter must never cause the answered initial choices to return.
    val hasEnergyAnswer = energySubmitted || selectedEnergy != null

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag(RUN_COMPLETE_SCREEN_TAG)
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInRoot()
                if (rootBounds != null && rootBounds != bounds) intake = null
                rootBounds = bounds
            }
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
                    .padding(start = 26.dp, top = 22.dp, end = 24.dp, bottom = 24.dp)
            ) {
                SavedStatus(sourceLabel, completionDateLabel, isDraft = onSaveActivity != null)
                Spacer(modifier = Modifier.height(18.dp))
                CompletionHero(
                    distance = distance,
                    pace = pace,
                    active = metrics?.active ?: "—",
                    phase = ringPhase,
                    intakeTime = { if (intake != null) intakeTime.value else null },
                    ringModifier = ringModifier
                )
                weatherSummary?.let { summary ->
                    Text(text = summary, color = CompletionMutedInk, fontSize = 11.sp,
                        lineHeight = 17.sp,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp)
                            .testTag("runCompleteWeather"))
                    RunWeatherCredit(color = CompletionMutedInk,
                        modifier = Modifier.padding(bottom = 6.dp))
                }
                CompletionEnergySummary(
                    before = savedPreRunEnergy,
                    after = selectedEnergy,
                    showAfter = hasEnergyAnswer && !initialPresentationHeld,
                    canEdit = energyEnabled && hasEnergyAnswer && !initialPresentationHeld,
                    editEnabled = actionsEnabled,
                    choicesOpen = energyChoicesOpen,
                    onEdit = { energyChoicesOpen = !energyChoicesOpen }
                )
                if (energyEnabled && (!hasEnergyAnswer || energyChoicesOpen)) {
                    EnergyChoices(
                        selectedEnergy = selectedEnergy,
                        onSelectEnergy = { branch ->
                            // Replace the visual flight on a new tap, never the real save callback.
                            intake = null
                            energySubmitted = true
                            energyChoicesOpen = false
                            effortFollowupPending = effortEnabled && selectedEffort == null
                            animationsEnabled = ValueAnimator.areAnimatorsEnabled()
                            val root = rootBounds
                            val ring = ringBounds
                            val allTiles = CompletionEnergyBranches.associateWith { tileBounds[it] }
                            if (lifecycleStarted && animationsEnabled && ringVisible && root != null &&
                                ring != null && allTiles.values.all { it != null && it.width > 0f && it.height > 0f }) {
                                intake = CompletionEnergyIntake(
                                    branch,
                                    allTiles.mapValues { (_, bounds) -> bounds!!.translate(-root.topLeft) },
                                    ring.center - root.topLeft
                                )
                                if (!hasEnergyAnswer) {
                                    // Register before the owner can synchronously publish READY.
                                    initialPresentationHeld = true
                                    currentPresentationHoldChange(true)
                                }
                            }
                            // Save immediately; only initial text/voice presentation waits for intake.
                            onSelectEnergy(branch)
                        },
                        enabled = actionsEnabled,
                        reflectionFinal = reflectionFinal,
                        intake = intake,
                        onTileBounds = { branch, bounds ->
                            if (tileBounds[branch] != null && tileBounds[branch] != bounds) intake = null
                            tileBounds[branch] = bounds
                        }
                    )
                    TextButton(
                        onClick = {
                            energySubmitted = true
                            energyChoicesOpen = false
                            onSelectEnergy(ReflectionBranch.NO_SELECTION)
                        },
                        enabled = actionsEnabled,
                        colors = ButtonDefaults.textButtonColors(contentColor = CompletionMutedInk),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) { Text("Not sure") }
                }
                if (effortEnabled && !initialPresentationHeld &&
                    (effortFollowupOpen || effortSaving)) {
                    InlineEffortChoices(effortSaving, effortError, actionsEnabled,
                        onSelect = { effort ->
                            effortFollowupOpen = false
                            onSelectEffort(effort)
                        },
                        onSkip = { effortFollowupOpen = false })
                }
                if (reflection != null && hasEnergyAnswer && !initialPresentationHeld) {
                    Spacer(modifier = Modifier.height(14.dp))
                    ReflectionPanel(
                        reflection = reflection,
                        onRetry = onRetryReflection,
                        retryEnabled = actionsEnabled,
                        isTestData = isTestData,
                        isDraft = onSaveActivity != null
                    )
                    if (reflectionAudioActive) {
                        Text(
                            reflectionSongLabel?.let { "Voice, then a Spotify passage when available · $it" }
                                ?: "Voice, then a short RunState instrumental (Eleven Music)",
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
                if (onSaveLocalNote != null) {
                    TextButton(
                        onClick = { noteOpen = !noteOpen },
                        enabled = actionsEnabled && !noteSaving,
                        colors = ButtonDefaults.textButtonColors(contentColor = CompletionMutedInk),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) { Text(if (noteOpen) "Close note" else if (localNote.isNullOrBlank()) "Something else? Add a note" else "Edit your note") }
                    if (noteOpen) {
                        OutlinedTextField(
                            value = noteDraft,
                            onValueChange = { noteDraft = it.take(280) },
                            enabled = actionsEnabled && !noteSaving,
                            label = { Text("How did you feel?") },
                            supportingText = { Text("Optional · saved on this phone · ${noteDraft.length}/280") },
                            minLines = 2,
                            maxLines = 5,
                            modifier = Modifier.fillMaxWidth().testTag(RUN_COMPLETE_NOTE_TAG)
                        )
                        TextButton(
                            onClick = { onSaveLocalNote(noteDraft.trim().ifBlank { null }) },
                            enabled = actionsEnabled && !noteSaving && noteDraft.trim() != localNote.orEmpty(),
                            modifier = Modifier.heightIn(min = 48.dp).testTag(RUN_COMPLETE_NOTE_SAVE_TAG)
                        ) { Text(if (noteSaving) "Saving note…" else "Save note") }
                        TextButton(onClick = {
                            noteDraft = localNote.orEmpty()
                            noteOpen = false
                        }, enabled = actionsEnabled && !noteSaving,
                            modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("Cancel note changes")
                        }
                        if (noteError != null) Text(noteError, color = CompletionStrongInk,
                            fontSize = 12.sp, lineHeight = 18.sp)
                    }
                }
                if (showHistory && onSaveActivity == null) {
                    TextButton(
                        onClick = onViewHistory,
                        enabled = actionsEnabled,
                        colors = ButtonDefaults.textButtonColors(contentColor = CompletionStrongInk),
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .align(Alignment.Start)
                            .heightIn(min = 48.dp)
                            .testTag(RUN_COMPLETE_HISTORY_TAG)
                    ) {
                        Text("View in Log History  →", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            if (activitySaveError != null) {
                Text(activitySaveError, color = CompletionStrongInk, fontSize = 13.sp,
                    lineHeight = 19.sp, modifier = Modifier.padding(horizontal = 24.dp))
            }
            if (onSaveActivity != null && noteDraft.trim() != localNote.orEmpty()) {
                Text("Save your note before finishing the activity.", color = CompletionMutedInk,
                    fontSize = 12.sp, modifier = Modifier.padding(horizontal = 24.dp))
            }
            Button(
                onClick = onSaveActivity ?: onStartAnother,
                enabled = actionsEnabled && !activitySaving && !noteSaving &&
                    (onSaveActivity == null || noteDraft.trim() == localNote.orEmpty()),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CompletionStrongInk,
                    contentColor = Color.White,
                    disabledContainerColor = CompletionStrongInk.copy(alpha = 0.38f),
                    disabledContentColor = Color.White.copy(alpha = 0.7f)
                ),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp)
                    .heightIn(min = 56.dp)
                    .testTag(RUN_COMPLETE_SAVE_ACTIVITY_TAG)
            ) {
                Text(
                    text = if (activitySaving) "Saving activity…" else if (onSaveActivity != null) "Save Activity" else "Start another run",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.2.sp
                )
            }
        }

        // This survives an immediate Ready reply removing the real choice column.
        // Drawn copies do not expose duplicate actions or intercept the fixed footer.
        intake?.let { current -> CompletionEnergyIntakeOverlay(current, time = { intakeTime.value }) }

    }
}

/** A read-only recap of the finalized activity. It neither generates nor replays a reflection. */
@Composable
internal fun RunActivityRecapScreen(
    item: RunHistoryItem?,
    localNote: String?,
    onViewHistory: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    route: HistoryRouteDisplay? = null,
    splits: HistorySplitDisplay? = null,
    onRetrySplits: () -> Unit = {},
    loadError: String? = null,
    actionsEnabled: Boolean = true
) {
    Column(modifier.fillMaxSize().background(CompletionSurfaceTop).testTag(RUN_ACTIVITY_RECAP_TAG)) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("ACTIVITY SAVED", color = CompletionGreen, fontSize = 12.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            if (item == null) {
                Text(loadError ?: "Your activity is saved. Its details are still loading.", color = CompletionStrongInk,
                    fontSize = 18.sp, lineHeight = 25.sp)
            } else {
                if (loadError != null) Text(loadError, color = CompletionStrongInk,
                    fontSize = 13.sp, lineHeight = 19.sp)
                Text(item.dateLabel, color = CompletionMutedInk, fontSize = 13.sp)
                DistanceInstrument(splitMetric(item.metrics?.distance ?: "—"))
                CompactMetrics(pace = splitMetric(item.metrics?.averagePace ?: "—"),
                    active = item.metrics?.active ?: "—", modifier = Modifier.fillMaxWidth(),
                    generousSpacing = true)
                Text(item.metrics?.sourceLabel ?: "Metrics unavailable", color = CompletionMutedInk,
                    fontSize = 12.sp)
                CompletionEnergySummary(item.preRunEnergy, item.energy, true, false, false, false, {})
                item.effort?.let { Text("Effort · ${it.displayLabel()}", color = CompletionStrongInk,
                    fontSize = 14.sp) }
                if (!localNote.isNullOrBlank()) {
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("YOUR NOTE", color = CompletionMutedInk, fontSize = 10.sp,
                            fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                        Text(localNote, color = CompletionStrongInk, fontSize = 16.sp, lineHeight = 23.sp)
                    }
                }
                item.weatherSummary?.let { Text(it, color = CompletionMutedInk, fontSize = 13.sp,
                    lineHeight = 19.sp) }
                if (item.isGpsRun) {
                    when (route) {
                        is HistoryRouteDisplay.Ready -> GpsRoutePreview(route.data, coverage = item.telemetryCoverage)
                        HistoryRouteDisplay.Failed -> Text("The recorded path couldn’t load. Your activity is saved.",
                            color = CompletionMutedInk, fontSize = 13.sp, lineHeight = 19.sp)
                        HistoryRouteDisplay.Loading, null -> Text("Loading your recorded path…",
                            color = CompletionMutedInk, fontSize = 13.sp)
                    }
                    Box(Modifier.fillMaxWidth().background(CompletionStrongInk, RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 4.dp)) {
                        HistorySplitsPanel(splits, onRetrySplits)
                    }
                }
                item.reflectionText?.takeIf { it.isNotBlank() }?.let { exactText ->
                    ReflectionPanel(RunCompleteReflectionDisplay.Ready(exactText), {}, false, item.isTestData)
                }
                if (item.reflectionText.isNullOrBlank()) {
                    Text("Your activity is saved. You can check its reflection in Log History.",
                        color = CompletionMutedInk, fontSize = 13.sp, lineHeight = 19.sp)
                }
                item.reflectionMusicSummary?.let { Text(it, color = CompletionMutedInk,
                    fontSize = 12.sp, lineHeight = 18.sp) }
            }
            TextButton(onClick = onViewHistory, enabled = actionsEnabled,
                modifier = Modifier.heightIn(min = 48.dp).testTag(RUN_COMPLETE_HISTORY_TAG)) {
                Text("View in Log History")
            }
        }
        Button(onClick = onDone, enabled = actionsEnabled,
            colors = ButtonDefaults.buttonColors(containerColor = CompletionStrongInk),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp)
                .heightIn(min = 56.dp), shape = RoundedCornerShape(16.dp)) {
            Text("Done", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

internal fun PostRunEffort.displayLabel(): String = when (this) {
    PostRunEffort.SMOOTH -> "Smooth"
    PostRunEffort.WORKING -> "Working"
    PostRunEffort.HEAVY -> "Heavy"
    PostRunEffort.EMPTY_TANK -> "Empty Tank"
}

/** A saved answer is shown as a fact; an absent answer is never inferred from the run. */
@Composable
private fun CompletionEnergySummary(
    before: PreRunEnergy?,
    after: ReflectionBranch?,
    showAfter: Boolean,
    canEdit: Boolean,
    editEnabled: Boolean,
    choicesOpen: Boolean,
    onEdit: () -> Unit
) {
    val beforeLabel = when (before) {
        PreRunEnergy.LOW -> "Low"
        PreRunEnergy.MODERATE -> "Moderate"
        PreRunEnergy.HIGH -> "High"
        null -> "Unknown"
    }
    val afterLabel = when (after) {
        ReflectionBranch.SPENT -> "Spent"
        ReflectionBranch.FEELING_GOOD -> "Feeling Good"
        ReflectionBranch.POWERED_UP -> "Powered Up"
        ReflectionBranch.NO_SELECTION -> "Not sure"
        null -> "Not selected"
    }
    val largeText = LocalDensity.current.fontScale > 1.3f
    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp)) {
        if (largeText) {
            EnergySummaryFact(beforeLabel, "PRE-RUN STATE", false)
            if (showAfter) {
                Spacer(Modifier.height(12.dp))
                EnergySummaryFact(afterLabel, "POST-RUN STATE", after != null && after != ReflectionBranch.NO_SELECTION)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                EnergySummaryFact(beforeLabel, "PRE-RUN STATE", false, Modifier.weight(1f))
                if (showAfter) {
                    Spacer(Modifier.width(16.dp))
                    EnergySummaryFact(afterLabel, "POST-RUN STATE", after != null && after != ReflectionBranch.NO_SELECTION,
                        Modifier.weight(1f))
                }
            }
        }
        if (canEdit) {
            TextButton(
                onClick = onEdit,
                enabled = editEnabled,
                colors = ButtonDefaults.textButtonColors(contentColor = CompletionMutedInk),
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(if (choicesOpen) "Close Energy choices" else "Energy +",
                    fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
            }
        }
    }
}

@Composable
private fun EnergySummaryFact(
    label: String,
    caption: String,
    filled: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(8.dp)) {
                if (filled) drawCircle(CompletionGreen)
                else drawCircle(CompletionGreen, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
            }
            Text(label, color = CompletionStrongInk, fontSize = 15.sp, lineHeight = 21.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 7.dp))
        }
        Text(caption, color = CompletionMutedInk, fontSize = 9.sp, letterSpacing = 1.5.sp,
            lineHeight = 14.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

/** A compact, skippable follow-up after Energy; reflection rendering proceeds independently. */
@Composable
private fun InlineEffortChoices(
    saving: Boolean,
    error: String?,
    enabled: Boolean,
    onSelect: (PostRunEffort) -> Unit,
    onSkip: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp).testTag(RUN_COMPLETE_EFFORT_TAG),
        verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("How hard was that?", color = CompletionStrongInk, fontSize = 16.sp,
            fontWeight = FontWeight.Medium)
        Text("Optional · how much effort the run took", color = CompletionMutedInk,
            fontSize = 12.sp, lineHeight = 18.sp)
        if (!saving) {
            PostRunEffort.entries.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { effort ->
                        OutlinedButton(onClick = { onSelect(effort) }, enabled = enabled,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CompletionStrongInk),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                .testTag(RUN_COMPLETE_EFFORT_CHOICE_TAG_PREFIX + effort.name)) {
                            Text(effort.displayLabel(), fontSize = 13.sp)
                        }
                    }
                }
            }
            TextButton(onClick = onSkip, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Skip", color = CompletionMutedInk)
            }
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
    isTestData: Boolean,
    isDraft: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(RUN_COMPLETE_REFLECTION_TAG)
            .shadow(5.dp, RoundedCornerShape(7.dp), ambientColor = CompletionInk.copy(alpha = 0.08f),
                spotColor = CompletionInk.copy(alpha = 0.1f))
            .background(
                color = Color.White.copy(alpha = 0.86f),
                shape = RoundedCornerShape(7.dp)
            )
            .completionBrackets(CompletionWarmAccent.copy(alpha = 0.75f))
            .padding(horizontal = 20.dp, vertical = 20.dp)
    ) {
        Text(
            text = "RUNSTATE · REFLECTION",
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
                    text = if (isDraft) "Your run is protected on this phone." else "Your run is already saved.",
                    color = CompletionMutedInk,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            is RunCompleteReflectionDisplay.Ready -> Text(
                text = reflection.text,
                color = CompletionStrongInk,
                fontSize = 19.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 28.sp,
                modifier = Modifier
                    .padding(top = 18.dp)
                    .testTag(RUN_COMPLETE_REFLECTION_TEXT_TAG)
            )

            RunCompleteReflectionDisplay.ConnectionNeeded -> {
                Text(if (isDraft) "Your run is protected on this phone." else "Your run is saved on this phone.", color = CompletionStrongInk,
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
                    text = if (isDraft) "Your run is protected on this phone. You can retry the reflection when a connection is available."
                        else "Your run is saved. You can retry the reflection when a connection is available.",
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
    reflectionFinal: Boolean,
    intake: CompletionEnergyIntake?,
    onTileBounds: (ReflectionBranch, Rect) -> Unit
) {
    val choices = listOf(
        ReflectionBranch.SPENT to "SPENT",
        ReflectionBranch.FEELING_GOOD to "FEELING GOOD",
        ReflectionBranch.POWERED_UP to "POWERED UP"
    )
    val largeText = LocalDensity.current.fontScale > 1.3f

    Column(modifier = Modifier.fillMaxWidth().testTag(RUN_COMPLETE_ENERGY_TAG).padding(top = 10.dp)) {
        Text("POST RUN ENERGY", color = CompletionMutedInk, fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold, letterSpacing = 1.7.sp,
            modifier = Modifier.padding(bottom = 12.dp))
        Column(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            choices.forEach { (branch, label) ->
                EnergyChoice(
                    label = label,
                    branch = branch,
                    selected = selectedEnergy == branch,
                    enabled = enabled,
                    onClick = { onSelectEnergy(branch) },
                    modifier = (if (largeText) Modifier.fillMaxWidth() else Modifier.width(124.dp))
                        .onGloballyPositioned { onTileBounds(branch, it.boundsInRoot()) }
                        .graphicsLayer { alpha = if (intake != null) 0f else 1f }
                )
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
    branch: ReflectionBranch,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
    labelAlpha: Float = 1f
) {
    val shape = RoundedCornerShape(3.dp)
    val surface = when (branch) {
        ReflectionBranch.SPENT -> Color(0xFF6A7977)
        ReflectionBranch.FEELING_GOOD -> Color(0xFF4C6258)
        ReflectionBranch.POWERED_UP -> Color(0xFFE0BA78)
        ReflectionBranch.NO_SELECTION -> CompletionSurfaceBottom
    }
    val ink = if (branch == ReflectionBranch.POWERED_UP) CompletionStrongInk else Color.White
    Column(
        modifier = modifier
            .shadow(if (selected) 6.dp else 3.dp, shape,
                ambientColor = CompletionInk.copy(alpha = 0.13f), spotColor = CompletionInk.copy(alpha = 0.14f))
            .clip(shape)
            .background(surface.copy(alpha = if (enabled) 1f else 0.55f), shape)
            .completionBrackets(ink.copy(alpha = if (selected) 0.9f else 0.4f))
            .then(if (interactive) Modifier.selectable(
                selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick
            ) else Modifier)
            .heightIn(min = 98.dp)
            .padding(horizontal = 10.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            color = ink.copy(alpha = (if (enabled) 1f else 0.7f) * labelAlpha),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 17.sp,
            letterSpacing = 0.1.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

/** Only visual copies fly; the selected value has already reached the saved-run owner. */
@Composable
private fun CompletionEnergyIntakeOverlay(intake: CompletionEnergyIntake, time: () -> Float) {
    val density = LocalDensity.current
    val elapsed = time()
    val mark = CompletionIntakeMarkEase.transform((elapsed / 150f).coerceIn(0f, 1f))
    val merge = CompletionIntakeMergeEase.transform(((elapsed - 150f) / 320f).coerceIn(0f, 1f))
    val travel = CompletionIntakeTravelEase.transform(((elapsed - 470f) / 640f).coerceIn(0f, 1f))
    val chosenBounds = intake.tiles.getValue(intake.branch)
    (CompletionEnergyBranches.filter { it != intake.branch } + intake.branch).forEach { branch ->
        val chosen = branch == intake.branch
        if ((chosen && elapsed < 1110f) || (!chosen && elapsed < 470f)) {
            val bounds = intake.tiles.getValue(branch)
            val scale = when {
                chosen && elapsed < 150f -> 1f + mark * 0.015f
                chosen && elapsed < 470f -> 1.015f + merge * 0.02f
                chosen -> 1.035f + travel * (0.13f - 1.035f)
                else -> 1f - merge * 0.1f
            }
            val destination = if (chosen) {
                bounds.center + (intake.target - bounds.center) * travel
            } else {
                bounds.center + (chosenBounds.center - bounds.center) * (0.62f * merge)
            }
            val opacity = if (chosen) {
                1f - CompletionIntakeFadeEase.transform(((elapsed - 870f) / 260f).coerceIn(0f, 1f))
            } else {
                1f - ((elapsed - 150f) / 260f).coerceIn(0f, 1f)
            }
            Box(
                modifier = Modifier
                    .offset { IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()) }
                    .size(with(density) { bounds.width.toDp() }, with(density) { bounds.height.toDp() })
                    .graphicsLayer {
                        translationX = destination.x - bounds.center.x
                        translationY = destination.y - bounds.center.y
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin.Center
                        alpha = opacity
                        shape = RoundedCornerShape(3.dp + 35.dp * travel)
                        clip = true
                    }
                    .clearAndSetSemantics { if (chosen) testTag = RUN_COMPLETE_ENERGY_INTAKE_TAG }
            ) {
                EnergyChoice(
                    label = when (branch) {
                        ReflectionBranch.SPENT -> "SPENT"
                        ReflectionBranch.FEELING_GOOD -> "FEELING GOOD"
                        ReflectionBranch.POWERED_UP -> "POWERED UP"
                        ReflectionBranch.NO_SELECTION -> ""
                    },
                    branch = branch,
                    selected = chosen,
                    enabled = true,
                    onClick = {},
                    modifier = Modifier.fillMaxSize(),
                    interactive = false,
                    labelAlpha = 1f - ((elapsed - 470f) / 200f).coerceIn(0f, 1f)
                )
            }
        }
    }
}

/** Fine registration corners echo the reference without outlining every surface. */
private fun Modifier.completionBrackets(color: Color): Modifier = drawBehind {
    val inset = 8.dp.toPx()
    val reach = 9.dp.toPx()
    val stroke = 0.7.dp.toPx()
    val left = inset
    val right = size.width - inset
    val top = inset
    val bottom = size.height - inset
    drawLine(color, Offset(left, top + reach), Offset(left, top), stroke)
    drawLine(color, Offset(left, top), Offset(left + reach, top), stroke)
    drawLine(color, Offset(right - reach, top), Offset(right, top), stroke)
    drawLine(color, Offset(right, top), Offset(right, top + reach), stroke)
    drawLine(color, Offset(left, bottom - reach), Offset(left, bottom), stroke)
    drawLine(color, Offset(left, bottom), Offset(left + reach, bottom), stroke)
    drawLine(color, Offset(right - reach, bottom), Offset(right, bottom), stroke)
    drawLine(color, Offset(right, bottom), Offset(right, bottom - reach), stroke)
}

/** The reference uses one quiet line rather than a badge or a second page title. */
@Composable
private fun SavedStatus(sourceLabel: String, completionDateLabel: String?, isDraft: Boolean = false) {
    val largeText = LocalDensity.current.fontScale > 1.35f

    if (largeText) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = if (isDraft) "RUN COMPLETE" else "RUN SAVED",
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
                text = if (isDraft) "RUN COMPLETE" else "RUN SAVED",
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
    if (!completionDateLabel.isNullOrBlank()) {
        Text(completionDateLabel.uppercase(), color = CompletionMutedInk, fontSize = 9.sp,
            fontWeight = FontWeight.Medium, letterSpacing = 1.3.sp, lineHeight = 15.sp,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun CompletionHero(
    distance: MetricParts,
    pace: MetricParts,
    active: String,
    phase: State<Float>,
    intakeTime: () -> Float?,
    ringModifier: Modifier
) {
    val largeText = LocalDensity.current.fontScale > 1.35f

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (!largeText && maxWidth >= 320.dp) {
            OffsetCompletionHero(
                distance = distance,
                pace = pace,
                active = active,
                phase = phase,
                intakeTime = intakeTime,
                ringModifier = ringModifier
            )
        } else {
            AccessibleCompletionHero(
                distance = distance,
                pace = pace,
                active = active,
                phase = phase,
                intakeTime = intakeTime,
                ringModifier = ringModifier
            )
        }
    }
}

/** Preserve the offset composition when its measured text leaves room for the completion mark. */
@Composable
private fun OffsetCompletionHero(
    distance: MetricParts,
    pace: MetricParts,
    active: String,
    phase: State<Float>,
    intakeTime: () -> Float?,
    ringModifier: Modifier
) {
    Layout(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 156.dp),
        content = {
            DistanceInstrument(distance = distance, compact = true)
            CompactMetrics(
                pace = pace,
                active = active,
                compact = true
            )
            CompletionVisualizer(phase, intakeTime, modifier = Modifier.size(104.dp).then(ringModifier))
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
        val gap = 8.dp.roundToPx()
        val distanceY = 8.dp.roundToPx()
        val metricsX = width - metricsPlaceable.width
        val metricsY = if (distancePlaceable.width + gap <= metricsX) {
            12.dp.roundToPx()
        } else {
            distanceY + distancePlaceable.height + gap
        }

        // OEM fonts and timer digits can consume the original fixed central gap.
        // Put decoration below the facts when it cannot fit without covering text.
        val markFitsBetween = distancePlaceable.width + gap + markPlaceable.width + gap <= metricsX
        val preferredMarkX = (width - markPlaceable.width) / 2 + 8.dp.roundToPx()
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
        val reservedMarkY = if (markFitsBetween) 22.dp.roundToPx() else factsBottom + gap
        // Lift only the mark; keep its original reserved height so the rest of Completion
        // stays put. The stacked fallback must never move back over the measured facts.
        val markY = (reservedMarkY - 8.dp.roundToPx())
            .coerceAtLeast(if (markFitsBetween) 0 else factsBottom)
        val height = constraints.constrainHeight(maxOf(factsBottom, reservedMarkY + markPlaceable.height))

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
    pace: MetricParts,
    active: String,
    phase: State<Float>,
    intakeTime: () -> Float?,
    ringModifier: Modifier
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        DistanceInstrument(distance = distance)
        CompletionVisualizer(
            phase = phase,
            intakeTime = intakeTime,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 10.dp, bottom = 26.dp)
                .size(116.dp)
                .then(ringModifier)
        )
        CompactMetrics(
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
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val numberSize = when {
        compact && distance.value.length >= 5 -> 60.sp
        compact -> 72.sp
        distance.value.length >= 5 -> 72.sp
        distance.value.length == 4 -> 82.sp
        else -> 98.sp
    }
    val numberLineHeight = when {
        compact && distance.value.length >= 5 -> 66.sp
        compact -> 78.sp
        distance.value.length >= 5 -> 70.sp
        distance.value.length == 4 -> 80.sp
        else -> 92.sp
    }

    Column(modifier = modifier.testTag(RUN_COMPLETE_DISTANCE_TAG)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = distance.value,
                color = CompletionStrongInk,
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
                    fontSize = if (compact) 21.sp else if (distance.value.length >= 4) 24.sp else 28.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = if (compact) 4.dp else 6.dp, bottom = 10.dp)
                )
            }
        }
        DistanceRuler(
            modifier = Modifier
                .padding(top = 8.dp)
                .width(if (compact) 142.dp else 174.dp)
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
    pace: MetricParts,
    active: String,
    modifier: Modifier = Modifier,
    generousSpacing: Boolean = false,
    compact: Boolean = false
) {
    Column(
        modifier = modifier.testTag(RUN_COMPLETE_METRICS_TAG),
        verticalArrangement = Arrangement.spacedBy(if (generousSpacing) 14.dp else 10.dp)
    ) {
        CompactMetric(value = active, label = "TIME", valueSize = if (compact) 20.sp else 22.sp,
            compact = compact)
        CompactMetric(value = pace.value, unit = pace.unit, label = "PACE", valueSize = if (compact) 24.sp else 29.sp,
            compact = compact)
    }
}

@Composable
private fun CompactMetric(
    value: String,
    label: String,
    valueSize: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier,
    unit: String = "",
    compact: Boolean = false
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = value,
            color = CompletionStrongInk,
            fontFamily = CompletionNumberFont,
            fontSize = valueSize,
            fontWeight = FontWeight.Medium,
            lineHeight = valueSize * 1.12f,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip
        )
        if (unit.isNotEmpty()) {
            Text(
                text = unit.uppercase(),
                color = CompletionMutedInk,
                fontFamily = CompletionNumberFont,
                fontSize = if (compact) 12.sp else 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(start = 2.dp, bottom = 2.dp)
            )
        }
        Text(
            text = label,
            color = CompletionMutedInk,
            fontSize = if (compact) 8.sp else 9.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = if (compact) 1.sp else 1.5.sp,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(start = 8.dp, bottom = 3.dp)
        )
    }
}

/** Start and Completion use the same decorative agent; only a tap starts an intake swell. */
@Composable
private fun CompletionVisualizer(
    phase: State<Float>,
    intakeTime: () -> Float?,
    modifier: Modifier = Modifier
) = RunAgentRing(phase, intakeTime, modifier.testTag(RUN_COMPLETE_VISUALIZER_TAG))

/** Fine structure and pale facets recreate the reference's layered porcelain field. */
@Composable
internal fun CompletionAtmosphere(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFE6EFF0).copy(alpha = 0.55f), Color.Transparent),
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
        drawPath(topFacet, Color(0xFFDCE8E9).copy(alpha = 0.55f))

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
        drawPath(middleFacet, Color(0xFFD8DFE1).copy(alpha = 0.44f))

        val lowerFacet = Path().apply {
            moveTo(size.width * 0.72f, size.height * 0.56f)
            lineTo(size.width, size.height * 0.61f)
            lineTo(size.width * 0.88f, size.height * 0.78f)
            close()
        }
        drawPath(lowerFacet, Color(0xFFE1E7E1).copy(alpha = 0.32f))
    }
}

/** Keep the provider and data licence linked beside both displays of optional weather. */
@Composable
internal fun RunWeatherCredit(color: Color, modifier: Modifier = Modifier) {
    val links = remember {
        val style = TextLinkStyles(style = SpanStyle(textDecoration = TextDecoration.Underline))
        buildAnnotatedString {
            withLink(LinkAnnotation.Url("https://open-meteo.com/", styles = style)) {
                append("Open-Meteo")
            }
            append(" · ")
            withLink(LinkAnnotation.Url("https://creativecommons.org/licenses/by/4.0/", styles = style)) {
                append("CC BY 4.0")
            }
        }
    }
    Text(text = links, color = color, fontSize = 10.sp, lineHeight = 16.sp,
        modifier = modifier)
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
