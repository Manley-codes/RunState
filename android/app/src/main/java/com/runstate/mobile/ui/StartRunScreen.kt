package com.runstate.mobile.ui

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import com.runstate.mobile.R
import com.runstate.mobile.data.local.PreRunEnergy
import com.runstate.mobile.data.local.RunIntention
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt

internal const val START_RUN_SCREEN_TAG = "startRunScreen"
internal const val START_RUN_BUTTON_TAG = "startRunButton"
internal const val START_RUN_MAP_START_TAG = "startMapStart"
internal const val START_HISTORY_TAG = "startHistory"
internal const val START_ENERGY_LOW_TAG = "startEnergyLow"
internal const val START_ENERGY_MODERATE_TAG = "startEnergyModerate"
internal const val START_ENERGY_HIGH_TAG = "startEnergyHigh"
internal const val START_ENERGY_SELECTED_TAG = "startEnergySelected"
internal const val START_AGENT_VISUALIZER_TAG = "startAgentVisualizer"
internal const val START_ENERGY_INTAKE_TAG = "startEnergyIntake"
internal const val START_GPS_TOGGLE_TAG = "startGpsToggle"
internal const val START_MUSIC_TOGGLE_TAG = "startMusicToggle"
internal const val START_OPEN_SPOTIFY_TAG = "startOpenSpotify"
internal const val START_OPEN_SHOES_TAG = "startOpenShoes"
internal const val START_MUSIC_SHARING_TOGGLE_TAG = "startMusicSharingToggle"
internal const val START_REFLECTION_SETUP_TAG = "startReflectionSetup"
internal const val START_RUN_SETTINGS_TAG = "startRunSettings"
internal const val START_RUN_STATUS_TAG = "startRunStatus"
internal const val START_RUN_UPPER_CONTENT_TAG = "startRunUpperContent"
internal const val START_RUN_FALLBACK_SCROLL_TAG = "startRunFallbackScroll"
internal const val START_AGENT_CARD_TAG = "startAgentCard"
internal const val START_UTILITY_ACTIONS_TAG = "startUtilityActions"
internal const val START_ENERGY_CHOICES_TAG = "startEnergyChoices"
internal const val START_ENERGY_CUE_TAG = "startEnergyCue"
internal const val START_RUN_PLAN_TAG = "startRunPlan"
internal const val START_RUN_PLAN_SHEET_TAG = "startRunPlanSheet"
internal const val START_RUN_INTENTION_TAG_PREFIX = "startRunIntention:"
internal const val START_RUN_ROUTE_LABEL_TAG = "startRunRouteLabel"
internal const val START_RUN_PLAN_SAVE_TAG = "startRunPlanSave"
internal const val START_FOOTER_TAG = "startFooter"
internal const val START_MAP_STATUS_TAG = "startMapStatus"
internal const val START_MAP_LOCATION_ACCESS_TAG = "startMapLocationAccess"
internal const val REFLECTION_CONNECTION_DIALOG_TAG = "reflectionConnectionDialog"
internal const val REFLECTION_CONNECTION_ADDRESS_TAG = "reflectionConnectionAddress"
internal const val REFLECTION_CONNECTION_CODE_TAG = "reflectionConnectionCode"
internal const val REFLECTION_CONNECTION_SAVE_TAG = "reflectionConnectionSave"
internal const val REFLECTION_CONNECTION_CLEAR_TAG = "reflectionConnectionClear"
private val StartInk = Color(0xFF293326)
private val StartForest = Color(0xFF2D3827)
private val StartForestDeep = Color(0xFF1D261B)
private val StartLime = Color(0xFFB8D442)
private val StartCream = Color(0xFFF9FAF5)

/** A visual hand-off only: the actual draft choice is owned by the parent immediately on tap. */
private data class EnergyIntake(
    val energy: PreRunEnergy,
    val tiles: Map<PreRunEnergy, Rect>,
    val target: Offset
)

private val StartEnergyLevels = listOf(PreRunEnergy.LOW, PreRunEnergy.MODERATE, PreRunEnergy.HIGH)
internal const val START_ENERGY_ENTRY_DURATION_MS = 920
private val EnergyEntryEase = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
private val IntakeMarkEase = CubicBezierEasing(0.32f, 0.72f, 0.3f, 1f)
private val IntakeMergeEase = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
private val IntakeTravelEase = CubicBezierEasing(0.5f, 0.02f, 0.28f, 1f)
private val IntakeFadeEase = CubicBezierEasing(0.4f, 0f, 1f, 1f)

/**
 * The original illustrated Start foundation remains explicit. The journey owner opens the
 * separate live map only after its action is tapped; viewing a map never starts a recorded run.
 * Only choices backed by the current run flow receive click handlers.
 */
@Composable
internal fun StartRunScreen(
    selectedPreRunEnergy: PreRunEnergy?,
    onSelectPreRunEnergy: (PreRunEnergy?) -> Unit,
    onStart: () -> Unit,
    actionsEnabled: Boolean,
    onViewHistory: () -> Unit,
    showHistory: Boolean,
    gpsSelected: Boolean = true,
    gpsMessage: String? = null,
    musicSelected: Boolean = false,
    musicAccessGranted: Boolean = false,
    modifier: Modifier = Modifier,
    onOpenRunSettings: () -> Unit = {},
    gpsReady: Boolean = false,
    gpsStatusText: String? = null,
    setupLoading: Boolean = false,
    reflectionSetupRequired: Boolean = false,
    liveMapBackground: Boolean = false,
    liveMapStatus: String? = null,
    onRequestMapLocation: (() -> Unit)? = null,
    onOpenMap: (() -> Unit)? = null,
    onOpenSpotify: () -> Unit = {},
    selectedShoe: ShoePairDisplay? = null,
    onOpenShoes: () -> Unit = {},
    selectedRunIntention: RunIntention? = null,
    routeLabel: String? = null,
    onRunContextChanged: (RunIntention?, String?) -> Unit = { _, _ -> }
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val view = LocalView.current
    var lifecycleStarted by remember(lifecycle) {
        mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    var animationsEnabled by remember(view) { mutableStateOf(ValueAnimator.areAnimatorsEnabled()) }
    var rootBounds by remember { mutableStateOf<Rect?>(null) }
    var ringBounds by remember { mutableStateOf<Rect?>(null) }
    val tileBounds = remember { mutableStateMapOf<PreRunEnergy, Rect>() }
    var intake by remember { mutableStateOf<EnergyIntake?>(null) }
    var runPlanOpen by rememberSaveable { mutableStateOf(false) }
    val intakeTime = remember { Animatable(0f) }
    var energyEntryPlayed by rememberSaveable { mutableStateOf(false) }
    val energyEntryTime = remember {
        Animatable(if (energyEntryPlayed || selectedPreRunEnergy != null || !animationsEnabled)
            START_ENERGY_ENTRY_DURATION_MS.toFloat() else 0f)
    }
    DisposableEffect(lifecycle, view) {
        val observer = LifecycleEventObserver { _, event ->
            lifecycleStarted = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            animationsEnabled = ValueAnimator.areAnimatorsEnabled()
            if (event == Lifecycle.Event.ON_PAUSE || !lifecycleStarted) intake = null
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val energyEntryLaidOut = rootBounds?.let { it.width > 0f && it.height > 0f } == true
    // Scaffold/window insets can settle after the first Start frame. Wait for layout once,
    // but do not cancel an entrance already in flight when its rectangle/density changes.
    LaunchedEffect(lifecycleStarted, animationsEnabled, selectedPreRunEnergy, energyEntryLaidOut) {
        if (energyEntryTime.value >= START_ENERGY_ENTRY_DURATION_MS) return@LaunchedEffect
        if (!energyEntryLaidOut) return@LaunchedEffect
        // Returning from the background or restoring the screen must not replay the entrance.
        if (energyEntryPlayed || selectedPreRunEnergy != null || !animationsEnabled) {
            energyEntryTime.snapTo(START_ENERGY_ENTRY_DURATION_MS.toFloat())
        } else if (lifecycleStarted) {
            energyEntryPlayed = true
            energyEntryTime.animateTo(START_ENERGY_ENTRY_DURATION_MS.toFloat(),
                tween(START_ENERGY_ENTRY_DURATION_MS, easing = LinearEasing))
        }
    }
    LaunchedEffect(intake, selectedPreRunEnergy, lifecycleStarted, animationsEnabled) {
        val current = intake ?: return@LaunchedEffect
        if (!lifecycleStarted || !animationsEnabled || selectedPreRunEnergy != current.energy) {
            intake = null
            return@LaunchedEffect
        }
        intakeTime.snapTo(0f)
        intakeTime.animateTo(1630f, tween(1630, easing = LinearEasing))
        if (intake === current) intake = null
    }
    val ringVisible = rootBounds?.let { viewport -> ringBounds?.overlaps(viewport) } == true
    val ringPhase = rememberRunAgentPhase(lifecycleStarted && animationsEnabled && ringVisible)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag(START_RUN_SCREEN_TAG)
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInRoot()
                if (rootBounds != null && rootBounds != bounds) intake = null
                rootBounds = bounds
            }
    ) {
        val agentRingSize = if (maxWidth < 400.dp) 108.dp else 128.dp
        val energyEntryDistancePx = with(LocalDensity.current) { maxWidth.toPx() }
        // A very short landscape window cannot keep both controls and upper details visible.
        // In that case use one continuous flow; regular phone heights retain the anchored layout.
        val fallbackHeight = 400.dp + 60.dp * (LocalDensity.current.fontScale - 1f).coerceAtLeast(0f)
        val scrollWholeStack = maxHeight < fallbackHeight
        // On compact/large-text screens the optional plan scrolls with secondary actions,
        // leaving the Energy tiles and Start button anchored and reachable.
        val planInUpper = !scrollWholeStack &&
            (maxHeight < 580.dp || LocalDensity.current.fontScale > 1.4f)
        val wholeStackScroll = rememberScrollState()
        val upperDetailsScroll = rememberScrollState()
        if (!liveMapBackground) {
            Image(
                painter = painterResource(R.drawable.start_map_reference),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.08f),
                        0.55f to Color.White.copy(alpha = 0.02f),
                        1f to Color.White.copy(alpha = 0.52f)
                    ))
            )
        }

        // Energy and the measured footer own separate space, so neither can slide over the other.
        // Upper details handle ordinary overflow; only very short windows scroll the entire flow.
        Column(
            modifier = Modifier.fillMaxSize().then(if (scrollWholeStack) Modifier
                .testTag(START_RUN_FALLBACK_SCROLL_TAG).verticalScroll(wholeStackScroll) else Modifier),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .then(if (scrollWholeStack) Modifier else Modifier.weight(1f))
                    .fillMaxWidth()
                    .testTag(START_RUN_UPPER_CONTENT_TAG)
                    .then(if (scrollWholeStack) Modifier else Modifier.verticalScroll(upperDetailsScroll))
                    .padding(horizontal = 20.dp)
                    .padding(top = 26.dp, bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AgentReadyCard(
                    selectedEnergy = selectedPreRunEnergy,
                    gpsStatus = when {
                        setupLoading -> "Loading run preferences…"
                        !gpsSelected -> "Sample demo · simulated distance"
                        gpsStatusText.equals("GPS ready", ignoreCase = true) -> "GPS Ready"
                        gpsStatusText != null -> gpsStatusText
                        gpsReady -> "GPS connecting…"
                        else -> "Location setup on Start"
                    },
                    ringSize = agentRingSize,
                    phase = ringPhase,
                    intakeTime = { if (intake != null) intakeTime.value else null },
                    ringModifier = Modifier.onGloballyPositioned { coordinates ->
                        val bounds = coordinates.boundsInRoot()
                        if (ringBounds != null && ringBounds != bounds) intake = null
                        ringBounds = bounds
                    }
                )
                StartUtilityActions(
                    mapStatus = if (liveMapBackground) liveMapStatus else null,
                    onMapClick = if (liveMapBackground && onRequestMapLocation != null)
                        onRequestMapLocation else onOpenMap,
                    mapNeedsLocation = liveMapBackground && onRequestMapLocation != null,
                    onViewHistory = onViewHistory,
                    showHistory = showHistory,
                    onOpenRunSettings = onOpenRunSettings,
                    actionsEnabled = actionsEnabled,
                    setupLoading = setupLoading,
                    musicStatus = when {
                        !musicSelected -> "Music observation off"
                        !musicAccessGranted -> "Music observation needs access"
                        else -> "Music observation on"
                    },
                    musicAccessNeeded = musicSelected && !musicAccessGranted,
                    reflectionSetupRequired = reflectionSetupRequired,
                    gpsMessage = gpsMessage
                )
                if (planInUpper) {
                    RunPlanControl(selectedRunIntention, routeLabel, actionsEnabled && !setupLoading,
                        onOpen = { runPlanOpen = true })
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                .padding(top = 12.dp, bottom = 12.dp)) {
                PreRunEnergyChoices(
                    selected = selectedPreRunEnergy,
                    onSelect = selection@{ energy ->
                        // Keep the real value synchronous, even if Start is tapped before arrival.
                        if (energy != null && (selectedPreRunEnergy != null || intake != null)) {
                            return@selection
                        }
                        if (energy == null) {
                            intake = null
                        } else if (selectedPreRunEnergy == null && intake == null) {
                            val root = rootBounds
                            val ring = ringBounds
                            val allTiles = StartEnergyLevels.associateWith { tileBounds[it] }
                            // An early tap still answers immediately; never fly a copy from an
                            // unsettled entrance position or hold up the runner's Start action.
                            if (energyEntryTime.value >= START_ENERGY_ENTRY_DURATION_MS &&
                                lifecycleStarted && animationsEnabled && ringVisible && root != null &&
                                ring != null && allTiles.values.all { it != null && it.width > 0f && it.height > 0f }) {
                                intake = EnergyIntake(
                                    energy,
                                    allTiles.mapValues { (_, bounds) -> bounds!!.translate(-root.topLeft) },
                                    ring.center - root.topLeft
                                )
                            }
                        }
                        onSelectPreRunEnergy(energy)
                    },
                    enabled = actionsEnabled,
                    intake = intake,
                    intakeTime = { intakeTime.value },
                    entryTime = { energyEntryTime.value },
                    entryDistancePx = energyEntryDistancePx,
                    onTileBounds = { energy, bounds ->
                        if (tileBounds[energy] != null && tileBounds[energy] != bounds) intake = null
                        tileBounds[energy] = bounds
                    }
                )
                if (!planInUpper) {
                    RunPlanControl(selectedRunIntention, routeLabel, actionsEnabled && !setupLoading,
                        onOpen = { runPlanOpen = true }, modifier = Modifier.align(Alignment.CenterHorizontally))
                }
            }

            StartFooter(
                onStart = onStart,
                enabled = actionsEnabled && !setupLoading,
                gpsSelected = gpsSelected,
                energySelected = selectedPreRunEnergy != null,
                phase = ringPhase,
                onOpenSpotify = onOpenSpotify,
                musicEnabled = actionsEnabled,
                selectedShoe = selectedShoe,
                onOpenShoes = onOpenShoes
            )
        }

        intake?.let { current ->
            EnergyIntakeOverlay(current, time = { intakeTime.value })
        }
    }
    if (runPlanOpen) {
        RunPlanSheet(
            selectedIntention = selectedRunIntention,
            routeLabel = routeLabel,
            enabled = actionsEnabled && !setupLoading,
            onDismiss = { runPlanOpen = false },
            onSave = { intention, route ->
                onRunContextChanged(intention, route)
                runPlanOpen = false
            }
        )
    }
}

private fun RunIntention.startLabel(): String = when (this) {
    RunIntention.KEEP_EASY -> "Keep it easy"
    RunIntention.STEADY -> "Steady"
    RunIntention.HARD_EFFORT -> "Hard effort"
}

@Composable
private fun RunPlanControl(intention: RunIntention?, routeLabel: String?, enabled: Boolean,
    onOpen: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onOpen, enabled = enabled,
        colors = ButtonDefaults.textButtonColors(contentColor = StartForestDeep),
        modifier = modifier.heightIn(min = 48.dp).testTag(START_RUN_PLAN_TAG)) {
        Text(intention?.startLabel()?.let { "$it · Edit plan" }
            ?: if (routeLabel != null) "Route added · Edit plan" else "Plan this run",
            fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

/** This run's optional plan is an explicit choice, never inferred from pace or the map. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RunPlanSheet(
    selectedIntention: RunIntention?,
    routeLabel: String?,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onSave: (RunIntention?, String?) -> Unit
) {
    var intention by rememberSaveable { mutableStateOf(selectedIntention) }
    var route by rememberSaveable { mutableStateOf(routeLabel.orEmpty()) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = StartCream,
        modifier = Modifier.testTag(START_RUN_PLAN_SHEET_TAG)
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Plan this run", color = StartInk, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            Text("What kind of run?", color = StartForest,
                fontSize = 14.sp, lineHeight = 21.sp)
            (listOf<RunIntention?>(null) + RunIntention.entries).forEach { choice ->
                OutlinedButton(
                    onClick = { intention = choice },
                    enabled = enabled,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = StartInk,
                        containerColor = if (intention == choice) Color(0xFFE5EAD7) else Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .testTag(START_RUN_INTENTION_TAG_PREFIX + (choice?.name ?: "NONE"))
                        .semantics { selected = intention == choice }
                ) {
                    Text(choice?.startLabel() ?: "No target",
                        fontWeight = if (intention == choice) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
            OutlinedTextField(
                value = route,
                onValueChange = { if (it.length <= 80) route = it },
                enabled = enabled,
                label = { Text("Familiar route") },
                placeholder = { Text("e.g. Park loop") },
                supportingText = { Text("Use the same name for the same route.") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag(START_RUN_ROUTE_LABEL_TAG)
            )
            TextButton(onClick = { onSave(intention, route.trim().takeIf { it.isNotEmpty() }) },
                enabled = enabled,
                modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp)
                    .testTag(START_RUN_PLAN_SAVE_TAG)) { Text("Save plan", color = StartForestDeep) }
        }
    }
}

@Composable
private fun StartUtilityActions(
    mapStatus: String?,
    onMapClick: (() -> Unit)?,
    mapNeedsLocation: Boolean,
    onViewHistory: () -> Unit,
    showHistory: Boolean,
    onOpenRunSettings: () -> Unit,
    actionsEnabled: Boolean,
    setupLoading: Boolean,
    musicStatus: String,
    musicAccessNeeded: Boolean,
    reflectionSetupRequired: Boolean,
    gpsMessage: String?
) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.End) {
        val settingsState = buildList {
            add(musicStatus)
            if (setupLoading) add("Loading run preferences")
            if (reflectionSetupRequired) add("Reflection setup required")
        }.joinToString(". ")
        Column(
            modifier = Modifier.testTag(START_UTILITY_ACTIONS_TAG),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val actionModifier = Modifier.size(48.dp)
            if (onMapClick != null) {
                StartUtilityButton("Map", StartUtilityIcon.MAP, onMapClick,
                    enabled = actionsEnabled && (!mapNeedsLocation || !setupLoading),
                    modifier = actionModifier.testTag(if (mapNeedsLocation)
                        START_MAP_LOCATION_ACCESS_TAG else "startExpandMap"),
                    accessibilityLabel = if (mapNeedsLocation) "Enable location" else "Open live map",
                    actionState = if (mapNeedsLocation) "Location access needed" else mapStatus)
            }
            if (showHistory) {
                StartUtilityButton("History", StartUtilityIcon.HISTORY, onViewHistory,
                    enabled = actionsEnabled,
                    modifier = actionModifier.testTag(START_HISTORY_TAG),
                    accessibilityLabel = "Log History")
            }
            StartUtilityButton("Settings", StartUtilityIcon.SETTINGS, onOpenRunSettings,
                enabled = actionsEnabled && !setupLoading,
                modifier = actionModifier.testTag(START_RUN_SETTINGS_TAG),
                accessibilityLabel = "Run settings", actionState = settingsState)
        }
        if (!setupLoading) {
            // Keep exceptional setup actions reachable outside the clean Agent card.
            if (musicAccessNeeded) {
                StartSetupNotice("Music observation needs access", onOpenRunSettings, actionsEnabled)
            }
            if (reflectionSetupRequired) {
                StartSetupNotice("Set up reflections in Run settings", onOpenRunSettings, actionsEnabled)
            }
            if (!gpsMessage.isNullOrBlank()) {
                StartSetupNotice(gpsMessage, onOpenRunSettings, actionsEnabled)
            }
        }
    }
}

@Composable
private fun StartSetupNotice(text: String, onClick: () -> Unit, enabled: Boolean) {
    TextButton(onClick = onClick, enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(text, color = StartInk, fontSize = 11.sp, lineHeight = 16.sp,
            modifier = Modifier.fillMaxWidth())
    }
}

// Unmodified Google Material Design outlined 24px icon paths.
// Copyright 2014 Google Inc. All Rights Reserved.
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use these icon paths except in compliance with the License.
// You may obtain a copy at https://www.apache.org/licenses/LICENSE-2.0.
// Unless required by applicable law or agreed to in writing, these paths are
// distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing
// permissions and limitations under the License.
// https://github.com/google/material-design-icons/blob/master/src/maps/map/materialiconsoutlined/24px.svg
// https://github.com/google/material-design-icons/blob/master/src/action/history/materialiconsoutlined/24px.svg
// https://github.com/google/material-design-icons/blob/master/src/action/settings/materialiconsoutlined/24px.svg
private enum class StartUtilityIcon(val pathData: String) {
    MAP("M20.5 3l-.16.03L15 5.1 9 3 3.36 4.9c-.21.07-.36.25-.36.48V20.5c0 .28.22.5.5.5l.16-.03L9 18.9l6 2.1 5.64-1.9c.21-.07.36-.25.36-.48V3.5c0-.28-.22-.5-.5-.5zM10 5.47l4 1.4v11.66l-4-1.4V5.47zm-5 .99l3-1.01v11.7l-3 1.16V6.46zm14 11.08l-3 1.01V6.86l3-1.16v11.84z"),
    HISTORY("M13 3c-4.97 0-9 4.03-9 9H1l3.89 3.89.07.14L9 12H6c0-3.87 3.13-7 7-7s7 3.13 7 7-3.13 7-7 7c-1.93 0-3.68-.79-4.94-2.06l-1.42 1.42C8.27 19.99 10.51 21 13 21c4.97 0 9-4.03 9-9s-4.03-9-9-9zm-1 5v5l4.25 2.52.77-1.28-3.52-2.09V8z"),
    SETTINGS("M19.43 12.98c.04-.32.07-.64.07-.98 0-.34-.03-.66-.07-.98l2.11-1.65c.19-.15.24-.42.12-.64l-2-3.46c-.09-.16-.26-.25-.44-.25-.06 0-.12.01-.17.03l-2.49 1c-.52-.4-1.08-.73-1.69-.98l-.38-2.65C14.46 2.18 14.25 2 14 2h-4c-.25 0-.46.18-.49.42l-.38 2.65c-.61.25-1.17.59-1.69.98l-2.49-1c-.06-.02-.12-.03-.18-.03-.17 0-.34.09-.43.25l-2 3.46c-.13.22-.07.49.12.64l2.11 1.65c-.04.32-.07.65-.07.98 0 .33.03.66.07.98l-2.11 1.65c-.19.15-.24.42-.12.64l2 3.46c.09.16.26.25.44.25.06 0 .12-.01.17-.03l2.49-1c.52.4 1.08.73 1.69.98l.38 2.65c.03.24.24.42.49.42h4c.25 0 .46-.18.49-.42l.38-2.65c.61-.25 1.17-.59 1.69-.98l2.49 1c.06.02.12.03.18.03.17 0 .34-.09.43-.25l2-3.46c.12-.22.07-.49-.12-.64l-2.11-1.65zm-1.98-1.71c.04.31.05.52.05.73 0 .21-.02.43-.05.73l-.14 1.13.89.7 1.08.84-.7 1.21-1.27-.51-1.04-.42-.9.68c-.43.32-.84.56-1.25.73l-1.06.43-.16 1.13-.2 1.35h-1.4l-.19-1.35-.16-1.13-1.06-.43c-.43-.18-.83-.41-1.23-.71l-.91-.7-1.06.43-1.27.51-.7-1.21 1.08-.84.89-.7-.14-1.13c-.03-.31-.05-.54-.05-.74s.02-.43.05-.73l.14-1.13-.89-.7-1.08-.84.7-1.21 1.27.51 1.04.42.9-.68c.43-.32.84-.56 1.25-.73l1.06-.43.16-1.13.2-1.35h1.39l.19 1.35.16 1.13 1.06.43c.43.18.83.41 1.23.71l.91.7 1.06-.43 1.27-.51.7 1.21-1.07.85-.89.7.14 1.13zM12 8c-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4-1.79-4-4-4zm0 6c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2z")
}

@Composable
private fun StartUtilityButton(
    label: String,
    icon: StartUtilityIcon,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    accessibilityLabel: String = label,
    actionState: String? = null
) {
    val ink = StartInk.copy(alpha = if (enabled) 1f else 0.5f)
    val image = remember(icon) {
        ImageVector.Builder(name = "Material${icon.name}", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f)
            .addPath(pathData = PathParser().parsePathString(icon.pathData).toNodes(), fill = SolidColor(Color.Black))
            .build()
    }
    Box(modifier.widthIn(min = 48.dp).heightIn(min = 48.dp)
        .clip(RoundedCornerShape(8.dp))
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .semantics(mergeDescendants = true) {
            contentDescription = accessibilityLabel
            if (actionState != null) stateDescription = actionState
        }
        .padding(4.dp),
        contentAlignment = Alignment.Center) {
        Image(imageVector = image, contentDescription = null, colorFilter = ColorFilter.tint(ink),
            modifier = Modifier.size(25.dp))
    }
}

/** Original, decorative branch silhouettes; they contain no map or location information. */
@Composable
internal fun RunMapTreeShadows(modifier: Modifier = Modifier) {
    Canvas(modifier.blur(8.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
        .clearAndSetSemantics { }) {
        val branchSize = size.width.coerceAtMost(520.dp.toPx())
        val shadow = Color(0xFF55736B).copy(alpha = 0.26f)
        fun androidx.compose.ui.graphics.drawscope.DrawScope.branch() {
            val stem = Path().apply {
                moveTo(-0.08f * branchSize, 0.01f * branchSize)
                cubicTo(0.04f * branchSize, 0.07f * branchSize,
                    0.28f * branchSize, 0.11f * branchSize,
                    0.49f * branchSize, 0.29f * branchSize)
            }
            drawPath(stem, shadow.copy(alpha = 0.12f), style = Stroke(0.011f * branchSize,
                cap = StrokeCap.Round))
            val leaves = listOf(
                Triple(0.03f, 0.05f, -66f), Triple(0.09f, 0.08f, 28f),
                Triple(0.17f, 0.1f, -42f), Triple(0.22f, 0.13f, 48f),
                Triple(0.3f, 0.16f, -30f), Triple(0.36f, 0.2f, 62f),
                Triple(0.43f, 0.24f, -22f)
            )
            leaves.forEachIndexed { index, (x, y, angle) ->
                withTransform({
                    translate(x * branchSize, y * branchSize)
                    rotate(angle, pivot = Offset.Zero)
                }) {
                    val length = branchSize * (if (index % 2 == 0) 0.17f else 0.14f)
                    val breadth = length * 0.38f
                    val leaf = Path().apply {
                        moveTo(0f, 0f)
                        cubicTo(length * 0.28f, -breadth, length * 0.82f, -breadth * 0.55f,
                            length, 0f)
                        cubicTo(length * 0.7f, breadth * 0.8f, length * 0.25f, breadth * 0.7f,
                            0f, 0f)
                        close()
                    }
                    drawPath(leaf, shadow)
                }
            }
        }
        branch()
        withTransform({
            translate(size.width, size.height)
            rotate(180f, pivot = Offset.Zero)
        }) { branch() }
    }
}

/** The private access code stays out of saved UI state and the dialog disallows screenshots. */
@Composable
internal fun ReflectionConnectionDialog(
    initialEndpoint: String,
    hasSavedConnection: Boolean,
    saving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
    onClear: () -> Unit
) {
    var endpoint by remember(initialEndpoint) { mutableStateOf(initialEndpoint) }
    var accessCode by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        modifier = Modifier.testTag(REFLECTION_CONNECTION_DIALOG_TAG),
        containerColor = StartCream,
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        title = { Text("Reflection connection", color = StartInk) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Use your RunState service address and access code. Your Anthropic API key belongs on the server.",
                    color = StartInk, fontSize = 13.sp, lineHeight = 19.sp)
                OutlinedTextField(
                    value = endpoint, onValueChange = { endpoint = it },
                    label = { Text("Service address") },
                    singleLine = true, enabled = !saving,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth().testTag(REFLECTION_CONNECTION_ADDRESS_TAG)
                )
                OutlinedTextField(
                    value = accessCode, onValueChange = { accessCode = it },
                    label = { Text("RunState access code") },
                    singleLine = true, enabled = !saving,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth().testTag(REFLECTION_CONNECTION_CODE_TAG)
                )
                Text("Saving selects this address for new reflection requests and for retries you choose in Log History. " +
                    "Those requests send saved run facts and any music context you chose to share to this service. " +
                    "Use only a service you trust. Saving does not check the connection.",
                    color = StartInk.copy(alpha = 0.8f), fontSize = 12.sp, lineHeight = 18.sp)
                if (error != null) Text(error, color = Color(0xFF8B392D), fontSize = 12.sp)
                if (hasSavedConnection) {
                    TextButton(onClick = onClear, enabled = !saving,
                        modifier = Modifier.testTag(REFLECTION_CONNECTION_CLEAR_TAG)) {
                        Text("Clear saved connection", color = StartInk)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(endpoint, accessCode) },
                enabled = !saving && endpoint.isNotBlank() && accessCode.isNotBlank(),
                modifier = Modifier.testTag(REFLECTION_CONNECTION_SAVE_TAG)
            ) { Text(if (saving) "Saving…" else "Save", color = StartInk) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel", color = StartInk) }
        }
    )
}

@Composable
private fun AgentReadyCard(
    selectedEnergy: PreRunEnergy?,
    gpsStatus: String,
    ringSize: Dp,
    phase: State<Float>,
    intakeTime: () -> Float?,
    ringModifier: Modifier
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(START_AGENT_CARD_TAG)
            .shadow(10.dp, RoundedCornerShape(24.dp), clip = false,
                ambientColor = Color(0xFF49645C).copy(alpha = 0.16f),
                spotColor = Color(0xFF49645C).copy(alpha = 0.22f))
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFDDE7E2), RoundedCornerShape(24.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "RUN",
                color = StartLime,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp,
                lineHeight = 22.sp
            )
            Text(
                text = "STATE",
                color = StartInk,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                lineHeight = 32.sp
            )
            Text(
                text = buildAnnotatedString {
                    append("AGENT  ")
                    withStyle(SpanStyle(color = StartLime)) { append("•") }
                    withStyle(SpanStyle(color = StartInk.copy(alpha = 0.70f))) { append("  READY") }
                },
                color = StartInk,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.1.sp,
                lineHeight = 16.sp
            )
        }
        // Reserve the agent's size; side text can wrap instead of squeezing it at large font sizes.
        Box(ringModifier.size(ringSize).testTag(START_AGENT_VISUALIZER_TAG)) {
            RunAgentRing(
                phase = phase,
                intakeTime = intakeTime,
                modifier = Modifier.fillMaxSize().then(
                    if (selectedEnergy != null) Modifier
                        .testTag(START_ENERGY_SELECTED_TAG)
                        .semantics {
                            selected = true
                            contentDescription = "Run agent"
                            stateDescription = "Pre-run energy: ${selectedEnergy.name}"
                        }
                    else Modifier
                )
            )
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            Column(Modifier.width(IntrinsicSize.Max), horizontalAlignment = Alignment.End) {
                Text(
                    text = "READY",
                    color = StartInk.copy(alpha = 0.70f),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp,
                    lineHeight = 22.sp,
                    modifier = Modifier.align(Alignment.End),
                    textAlign = TextAlign.End
                )
                Text(
                    text = "TO RUN",
                    color = StartInk,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    lineHeight = 32.sp,
                    textAlign = TextAlign.End
                )
                Text(
                    text = gpsStatus,
                    color = StartInk.copy(alpha = 0.72f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.1.sp,
                    lineHeight = 16.sp,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth().testTag(START_RUN_STATUS_TAG)
                )
            }
        }
    }
}

@Composable
private fun PreRunEnergyChoices(
    selected: PreRunEnergy?,
    onSelect: (PreRunEnergy?) -> Unit,
    enabled: Boolean,
    intake: EnergyIntake?,
    intakeTime: () -> Float,
    entryTime: () -> Float,
    entryDistancePx: Float,
    onTileBounds: (PreRunEnergy, Rect) -> Unit
) {
    val tileHeight = 82.dp + 40.dp * (LocalDensity.current.fontScale - 1f).coerceAtLeast(0f)
    Column(modifier = Modifier.testTag(START_ENERGY_CHOICES_TAG),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Physical energy now · Optional",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.3.sp,
            modifier = Modifier
                .graphicsLayer {
                    alpha = when {
                        selected == null -> 1f
                        intake != null -> 1f - ((intakeTime() - 470f) / 200f).coerceIn(0f, 1f)
                        else -> 0f
                    }
                }
                .clip(RoundedCornerShape(12.dp))
                .background(StartForest.copy(alpha = 0.96f))
                .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .then(if (selected != null) Modifier.clearAndSetSemantics {} else Modifier)
        )
        Spacer(Modifier.height(10.dp))
        // Keep the reserved Energy space stable while the tiles leave for the agent.
        Box(Modifier.fillMaxWidth().height(tileHeight).testTag(START_ENERGY_CUE_TAG),
            contentAlignment = Alignment.Center) {
            if (selected == null || intake != null) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .graphicsLayer { alpha = if (selected == null) 1f else 0f }
                        .then(if (selected != null) Modifier.clearAndSetSemantics {} else Modifier),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StartEnergyLevels.forEachIndexed { index, energy ->
                        EnergyTile(
                            energy = energy,
                            onClick = { onSelect(energy) },
                            selected = false,
                            enabled = enabled && selected == null,
                            modifier = Modifier.weight(1f)
                                .onGloballyPositioned {
                                    onTileBounds(energy, it.boundsInRoot())
                                }
                                .graphicsLayer {
                                    // One shared clock: Low, Moderate, High enter 120ms apart.
                                    // Read the clock here so movement updates the layer, not the
                                    // layout or tile contents on every frame.
                                    val progress = ((entryTime() - index * 120f) / 680f).coerceIn(0f, 1f)
                                    val settled = EnergyEntryEase.transform(progress)
                                    translationX = entryDistancePx * (1f - settled)
                                    rotationZ = 4f * (1f - settled)
                                    alpha = (progress / 0.22f).coerceIn(0f, 1f)
                                },
                            tag = if (selected == null) when (energy) {
                                PreRunEnergy.LOW -> START_ENERGY_LOW_TAG
                                PreRunEnergy.MODERATE -> START_ENERGY_MODERATE_TAG
                                PreRunEnergy.HIGH -> START_ENERGY_HIGH_TAG
                            } else null,
                            tileHeight = tileHeight
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EnergyTile(
    energy: PreRunEnergy,
    onClick: () -> Unit,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    tag: String?,
    interactive: Boolean = true,
    labelAlpha: () -> Float = { 1f },
    tileHeight: Dp = 82.dp
) {
    val label = when (energy) {
        PreRunEnergy.LOW -> "LOW"
        PreRunEnergy.MODERATE -> "MODERATE"
        PreRunEnergy.HIGH -> "HIGH"
    }
    val accent = when (energy) {
        PreRunEnergy.LOW -> Color(0xFFCDB7EA)
        PreRunEnergy.MODERATE -> Color(0xFFB8D1B7)
        PreRunEnergy.HIGH -> Color(0xFFEED378)
    }
    Column(
        modifier = modifier
            .height(tileHeight)
            .clip(RoundedCornerShape(17.dp))
            .background(StartInk.copy(alpha = if (enabled) 0.89f else 0.95f))
            .then(if (interactive && enabled) Modifier.pointerInput(Unit) {
                // A swipe within a stationary tile is not an Energy answer. Consuming movement
                // only after touch slop cancels the selectable tap without changing its semantics.
                detectDragGestures { change, _ -> change.consume() }
            } else Modifier)
            .then(if (interactive) Modifier.selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick
            ) else Modifier)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Canvas(Modifier.size(18.dp).offset(y = 4.dp)) {
            when (energy) {
                PreRunEnergy.LOW -> {
                    drawCircle(accent, radius = size.minDimension * 0.42f)
                    drawCircle(StartInk, radius = size.minDimension * 0.39f,
                        center = center + Offset(size.width * 0.23f, -size.height * 0.16f))
                }
                PreRunEnergy.MODERATE -> repeat(3) { index ->
                    val y = size.height * (0.29f + index * 0.22f)
                    drawLine(accent, Offset(size.width * 0.2f, y), Offset(size.width * 0.8f, y),
                        strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                }
                PreRunEnergy.HIGH -> {
                    drawCircle(accent, radius = size.minDimension * 0.20f)
                    repeat(8) { index ->
                        val angle = index * PI / 4.0
                        val direction = Offset(cos(angle).toFloat(), sin(angle).toFloat())
                        drawLine(accent, center + direction * size.minDimension * 0.33f,
                            center + direction * size.minDimension * 0.48f,
                            strokeWidth = 1.8.dp.toPx(), cap = StrokeCap.Round)
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            color = Color.White,
            fontSize = if (energy == PreRunEnergy.MODERATE) 11.sp else 13.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.1.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 3.dp).graphicsLayer { alpha = labelAlpha() }
        )
        Text(
            text = when (energy) {
                PreRunEnergy.LOW -> "Running low"
                PreRunEnergy.MODERATE -> "Steady"
                PreRunEnergy.HIGH -> "Energized"
            },
            color = accent,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 3.dp).graphicsLayer { alpha = labelAlpha() }
        )
    }
}

@Composable
private fun EnergyIntakeOverlay(intake: EnergyIntake, time: () -> Float) {
    val density = LocalDensity.current
    val elapsed = time()
    val mark = IntakeMarkEase.transform((elapsed / 150f).coerceIn(0f, 1f))
    val merge = IntakeMergeEase.transform(((elapsed - 150f) / 320f).coerceIn(0f, 1f))
    val travel = IntakeTravelEase.transform(((elapsed - 470f) / 640f).coerceIn(0f, 1f))
    val chosenBounds = intake.tiles.getValue(intake.energy)
    // The chosen tile stays in front while the other two converge and disappear.
    (StartEnergyLevels.filter { it != intake.energy } + intake.energy).forEach { energy ->
        val chosen = intake.energy == energy
        if ((chosen && elapsed < 1110f) || (!chosen && elapsed < 470f)) {
            val bounds = intake.tiles.getValue(energy)
            val scale = when {
                chosen && elapsed < 150f -> 1f + mark * 0.015f
                chosen && elapsed < 470f -> 1.015f + merge * 0.02f
                chosen -> 1.035f + travel * (0.13f - 1.035f)
                else -> 1f - merge * 0.1f
            }
            val destination = if (chosen) {
                bounds.center + (intake.target - bounds.center) * travel
            } else {
                bounds.center + Offset((chosenBounds.center.x - bounds.center.x) * 0.62f * merge, 0f)
            }
            val opacity = if (chosen) {
                1f - IntakeFadeEase.transform(((elapsed - 870f) / 260f).coerceIn(0f, 1f))
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
                        shape = RoundedCornerShape(17.dp + 35.dp * travel)
                        clip = true
                    }
                    // A drawn copy has no selectable action or duplicate spoken label.
                    .clearAndSetSemantics { if (chosen) testTag = START_ENERGY_INTAKE_TAG }
            ) {
                EnergyTile(
                    energy = energy,
                    onClick = {},
                    selected = false,
                    enabled = true,
                    modifier = Modifier.fillMaxSize(),
                    tag = null,
                    interactive = false,
                    labelAlpha = { 1f - ((elapsed - 470f) / 200f).coerceIn(0f, 1f) }
                )
            }
        }
    }
}

@Composable
private fun StartFooter(
    onStart: () -> Unit,
    enabled: Boolean,
    gpsSelected: Boolean,
    energySelected: Boolean,
    phase: State<Float>,
    onOpenSpotify: () -> Unit,
    musicEnabled: Boolean,
    selectedShoe: ShoePairDisplay?,
    onOpenShoes: () -> Unit,
    modifier: Modifier = Modifier
) {
    val readyGlow by animateFloatAsState(
        targetValue = if (energySelected && enabled) 1f else 0f,
        animationSpec = tween(500, easing = IntakeMarkEase),
        label = "startReadyGlow"
    )
    Box(modifier = modifier.fillMaxWidth().heightIn(min = 184.dp).testTag(START_FOOTER_TAG)) {
        Canvas(Modifier.matchParentSize()) {
            val boundary = Path().apply {
                // Lift the side shoulders, not the controls: the music keeps its size and gains
                // clear space below the crest without changing the measured Energy/footer gap.
                moveTo(0f, size.height * 0.16f)
                cubicTo(
                    size.width * 0.21f, size.height * 0.24f,
                    size.width * 0.37f, size.height * 0.06f,
                    size.width * 0.5f, size.height * 0.06f
                )
                cubicTo(
                    size.width * 0.63f, size.height * 0.06f,
                    size.width * 0.82f, size.height * 0.23f,
                    size.width, size.height * 0.16f
                )
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(boundary, Brush.horizontalGradient(listOf(StartForest, StartForestDeep)))
            repeat(4) { index ->
                val curve = Path().apply {
                    moveTo(size.width * (0.68f + index * 0.09f), size.height)
                    cubicTo(
                        size.width * 0.76f,
                        size.height * (0.60f + index * 0.02f),
                        size.width * 0.92f,
                        size.height * (0.44f + index * 0.02f),
                        size.width,
                        size.height * (0.40f + index * 0.06f)
                    )
                }
                drawPath(curve, Color.White.copy(alpha = 0.07f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()))
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 20.dp)
                .padding(top = 18.dp, bottom = 13.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            ShoeFooterControl(selectedShoe, enabled, onOpenShoes)
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .clickable(enabled = enabled, role = Role.Button, onClick = onStart)
                    .testTag(START_RUN_BUTTON_TAG)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                StartDial(
                    modifier = Modifier.size(102.dp),
                    glow = { readyGlow * (0.9f + 0.1f * sin(phase.value)) }
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "Start",
                    color = if (enabled) Color.White else Color.White.copy(alpha = 0.48f),
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 3.sp
                )
                Text(
                    text = if (gpsSelected) "GPS run" else "Sample run",
                    color = Color.White.copy(alpha = if (enabled) 0.8f else 0.48f),
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            FooterLabel("MUSIC", "SPOTIFY", R.drawable.start_music_orb, artwork = true,
                modifier = Modifier
                    .clickable(enabled = musicEnabled, role = Role.Button,
                        onClickLabel = "Open Spotify", onClick = onOpenSpotify)
                    .testTag(START_OPEN_SPOTIFY_TAG))
        }
    }
}

@Composable
private fun ShoeFooterControl(shoe: ShoePairDisplay?, enabled: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(72.dp).padding(bottom = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = "Choose shoes", onClick = onClick)
            .semantics { stateDescription = shoe?.let { "${it.fullName}, ${it.mileageLabel}" } ?: "No shoes selected" }
            .testTag(START_OPEN_SHOES_TAG),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ShoeIllustration(Modifier.size(64.dp))
        Spacer(Modifier.height(7.dp))
        Text("SHOES", color = Color.White.copy(alpha = 0.78f), fontSize = 10.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        if (shoe == null) {
            Text("ADD PAIR", color = Color.White.copy(alpha = 0.6f), fontSize = 8.sp,
                fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
        } else {
            Text(shoe.displayName, color = Color.White.copy(alpha = 0.86f), fontSize = 10.sp,
                lineHeight = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center, modifier = Modifier.padding(top = 2.dp))
            Text(shoe.mileageLabel, color = Color.White.copy(alpha = 0.62f), fontSize = 9.sp,
                lineHeight = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1,
                modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun FooterLabel(
    label: String,
    sublabel: String,
    icon: Int,
    artwork: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(72.dp)
            .padding(bottom = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(if (artwork) 64.dp else 52.dp)
                .then(if (artwork) Modifier.clip(CircleShape) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            if (!artwork) {
                Canvas(Modifier.matchParentSize()) {
                    val w = size.width
                    val h = size.height
                    drawOval(
                        brush = Brush.radialGradient(
                            listOf(Color.Black.copy(alpha = 0.24f), Color.Transparent),
                            center = Offset(w * 0.5f, h * 0.72f), radius = w * 0.48f
                        ),
                        topLeft = Offset(w * 0.02f, h * 0.43f),
                        size = Size(w * 0.96f, h * 0.55f)
                    )
                    val pebble = Path().apply {
                        moveTo(w * 0.46f, h * 0.06f)
                        cubicTo(w * 0.68f, h * 0.01f, w * 0.91f, h * 0.17f, w * 0.92f, h * 0.40f)
                        cubicTo(w * 0.98f, h * 0.63f, w * 0.79f, h * 0.85f, w * 0.55f, h * 0.89f)
                        cubicTo(w * 0.32f, h * 0.97f, w * 0.08f, h * 0.79f, w * 0.07f, h * 0.55f)
                        cubicTo(w * 0.02f, h * 0.32f, w * 0.22f, h * 0.10f, w * 0.46f, h * 0.06f)
                        close()
                    }
                    drawPath(pebble, Brush.linearGradient(
                        0f to Color(0xFF4A5535),
                        0.48f to Color(0xFF303D24),
                        1f to Color(0xFF192416),
                        start = Offset(w * 0.2f, 0f), end = Offset(w * 0.72f, h)
                    ))
                    // Light catches only the upper shoulder; there is no enclosing outline.
                    val shoulder = Path().apply {
                        moveTo(w * 0.15f, h * 0.28f)
                        cubicTo(w * 0.31f, h * 0.06f, w * 0.65f, h * 0.02f, w * 0.82f, h * 0.22f)
                    }
                    drawPath(shoulder, Brush.horizontalGradient(listOf(
                        Color.Transparent, Color(0xFFDCE5BE).copy(alpha = 0.20f), Color.Transparent
                    )), style = Stroke(0.8.dp.toPx(), cap = StrokeCap.Round))
                }
            }
            Image(
                painter = painterResource(icon),
                contentDescription = null,
                colorFilter = if (artwork) null else ColorFilter.tint(Color(0xFFDCE5BE)),
                contentScale = ContentScale.Fit,
                modifier = if (artwork) Modifier.fillMaxSize() else Modifier.size(29.dp)
            )
        }
        Spacer(Modifier.height(7.dp))
        Text(label, color = Color.White.copy(alpha = 0.78f),
            fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text(sublabel, color = Color.White.copy(alpha = 0.5f),
            fontSize = 8.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
    }
}

@Composable
private fun StartDial(modifier: Modifier = Modifier, glow: () -> Float = { 0f }) {
    Canvas(modifier) {
        val outerRadius = size.minDimension * 0.49f
        val r = outerRadius * 0.9f
        val illumination = glow().coerceIn(0f, 1f)
        val shadowCenter = center + Offset(0f, 2.dp.toPx())
        val shadowRadius = outerRadius - 2.dp.toPx()
        drawCircle(
            brush = Brush.radialGradient(
                0f to Color.Black.copy(alpha = 0.30f),
                0.76f to Color.Black.copy(alpha = 0.20f),
                1f to Color.Transparent,
                center = shadowCenter, radius = shadowRadius
            ),
            radius = shadowRadius, center = shadowCenter
        )
        if (illumination > 0f) {
            drawCircle(
                brush = Brush.radialGradient(
                    0f to Color.Transparent,
                    0.72f to Color.Transparent,
                    0.9f to StartLime.copy(alpha = 0.36f * illumination),
                    1f to Color.Transparent,
                    center = center,
                    radius = outerRadius
                ),
                radius = outerRadius
            )
            drawCircle(StartLime.copy(alpha = 0.64f * illumination),
                radius = r + 0.7.dp.toPx(),
                style = androidx.compose.ui.graphics.drawscope.Stroke(1.4.dp.toPx()))
        }
        drawCircle(Brush.radialGradient(
            0f to Color(0xFF3B4733),
            0.52f to Color(0xFF232E1E),
            1f to Color(0xFF151E13),
            center = center + Offset(-r * 0.45f, -r * 0.7f), radius = r * 2.2f
        ), radius = r)
        drawArc(
            brush = Brush.linearGradient(
                listOf(Color.Transparent, Color(0xFFE4EBD7).copy(alpha = 0.20f), Color.Transparent),
                start = center - Offset(r, r), end = center + Offset(r, -r * 0.3f)
            ),
            startAngle = 205f, sweepAngle = 105f, useCenter = false,
            topLeft = center - Offset(r, r), size = Size(r * 2f, r * 2f),
            style = Stroke(1.dp.toPx(), cap = StrokeCap.Round)
        )
        repeat(12) { index ->
            val angle = index * PI / 6.0 - PI / 2.0
            val direction = Offset(cos(angle).toFloat(), sin(angle).toFloat())
            drawLine(
                Color.White.copy(alpha = if (index % 3 == 0) 0.78f else 0.42f),
                center + direction * r * 0.68f,
                center + direction * r * (if (index % 3 == 0) 0.88f else 0.83f),
                strokeWidth = if (index % 3 == 0) 1.8.dp.toPx() else 1.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
        val triangle = Path().apply {
            fillType = PathFillType.EvenOdd
            moveTo(center.x - r * 0.23f, center.y - r * 0.36f)
            lineTo(center.x + r * 0.42f, center.y)
            lineTo(center.x - r * 0.23f, center.y + r * 0.36f)
            close()
            // A real opening lets the shaded dial show through the play symbol.
            moveTo(center.x - r * 0.12f, center.y - r * 0.20f)
            lineTo(center.x + r * 0.24f, center.y)
            lineTo(center.x - r * 0.12f, center.y + r * 0.20f)
            close()
        }
        drawPath(triangle, Color.White)
        drawLine(Color(0xFFF5BE37),
            Offset(center.x - r * 0.05f, center.y),
            Offset(center.x + r * 0.12f, center.y),
            strokeWidth = 5.dp.toPx(), cap = StrokeCap.Square)
    }
}
