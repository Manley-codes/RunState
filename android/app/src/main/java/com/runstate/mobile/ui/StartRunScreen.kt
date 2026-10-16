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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
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
import androidx.compose.ui.text.style.TextAlign
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt

internal const val START_RUN_SCREEN_TAG = "startRunScreen"
internal const val START_RUN_BUTTON_TAG = "startRunButton"
internal const val START_HISTORY_TAG = "startHistory"
internal const val START_ENERGY_LOW_TAG = "startEnergyLow"
internal const val START_ENERGY_MODERATE_TAG = "startEnergyModerate"
internal const val START_ENERGY_HIGH_TAG = "startEnergyHigh"
internal const val START_ENERGY_SELECTED_TAG = "startEnergySelected"
internal const val START_ENERGY_UNDO_TAG = "startEnergyUndo"
internal const val START_AGENT_VISUALIZER_TAG = "startAgentVisualizer"
internal const val START_ENERGY_INTAKE_TAG = "startEnergyIntake"
internal const val START_GPS_TOGGLE_TAG = "startGpsToggle"
internal const val START_MUSIC_TOGGLE_TAG = "startMusicToggle"
internal const val START_MUSIC_SHARING_TOGGLE_TAG = "startMusicSharingToggle"
internal const val START_REFLECTION_SETUP_TAG = "startReflectionSetup"
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
private val IntakeMarkEase = CubicBezierEasing(0.32f, 0.72f, 0.3f, 1f)
private val IntakeMergeEase = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
private val IntakeTravelEase = CubicBezierEasing(0.5f, 0.02f, 0.28f, 1f)
private val IntakeFadeEase = CubicBezierEasing(0.4f, 0f, 1f, 1f)

/**
 * A native version of the Start prototype's visual foundation. The map is a static illustration
 * taken from the user's prototype. It never implies that location or route tracking is active.
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
    gpsSelected: Boolean = false,
    onToggleGps: () -> Unit = {},
    gpsMessage: String? = null,
    musicSelected: Boolean = false,
    musicAccessGranted: Boolean = false,
    onToggleMusic: () -> Unit = {},
    musicReflectionSharingAvailable: Boolean = false,
    musicReflectionSharingSelected: Boolean = false,
    onToggleMusicReflectionSharing: () -> Unit = {},
    reflectionConnectionLabel: String? = null,
    onOpenReflectionSetup: () -> Unit = {},
    reflectionSetupEnabled: Boolean = true,
    modifier: Modifier = Modifier
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
        val screenHeight = maxHeight
        val agentRingSize = if (maxWidth < 400.dp) 84.dp else 100.dp
        Image(
            painter = painterResource(R.drawable.start_map_reference),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.08f),
                        0.55f to Color.White.copy(alpha = 0.02f),
                        1f to Color.White.copy(alpha = 0.52f)
                    )
                )
        )

        // The main action stays fixed. On a short viewport the card and energy choices can scroll.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 26.dp, bottom = 184.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AgentReadyCard(
                selectedEnergy = selectedPreRunEnergy,
                ringSize = agentRingSize,
                phase = ringPhase,
                intakeTime = { if (intake != null) intakeTime.value else null },
                ringModifier = Modifier.onGloballyPositioned { coordinates ->
                    val bounds = coordinates.boundsInRoot()
                    if (ringBounds != null && ringBounds != bounds) intake = null
                    ringBounds = bounds
                }
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (gpsSelected) "ILLUSTRATIVE MAP · GPS READY"
                        else "ILLUSTRATIVE MAP · LOCATION OFF",
                    color = StartInk,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(StartCream.copy(alpha = 0.89f))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                )
                if (showHistory) {
                    TextButton(
                        onClick = onViewHistory,
                        enabled = actionsEnabled,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag(START_HISTORY_TAG)
                    ) {
                        Text(
                            "Log History",
                            color = StartInk,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
            if (reflectionConnectionLabel != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(reflectionConnectionLabel, color = StartInk, fontSize = 11.sp,
                        modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = onOpenReflectionSetup,
                        enabled = actionsEnabled && reflectionSetupEnabled,
                        modifier = Modifier.heightIn(min = 48.dp).testTag(START_REFLECTION_SETUP_TAG)
                    ) {
                        Text("Reflection setup", color = StartInk, fontSize = 12.sp)
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(StartCream.copy(alpha = 0.9f))
                    .padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (gpsSelected) "Precise GPS will record this run"
                        else "Sample run · GPS off",
                    color = StartInk,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(
                    onClick = onToggleGps,
                    enabled = actionsEnabled,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag(START_GPS_TOGGLE_TAG)
                ) {
                    Text(
                        if (gpsSelected) "Turn GPS off" else "Turn GPS on",
                        color = StartInk,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            if (gpsMessage != null) {
                Text(
                    text = gpsMessage,
                    color = StartInk,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 5.dp)
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(StartCream.copy(alpha = 0.9f))
                    .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Observe music",
                        color = StartInk,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (musicAccessGranted)
                            "Saves media-session title, artist & app while Running. Other audio may appear."
                        else
                            "Android grants broad notification access. RunState reads media sessions only during opted-in runs.",
                        color = StartInk.copy(alpha = 0.78f),
                        fontSize = 10.sp,
                        lineHeight = 13.sp
                    )
                }
                TextButton(
                    onClick = onToggleMusic,
                    enabled = actionsEnabled,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag(START_MUSIC_TOGGLE_TAG)
                ) {
                    Text(
                        if (musicSelected) "Turn off" else "Turn on",
                        color = StartInk,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            val showMusicSharing = musicReflectionSharingAvailable && musicSelected && musicAccessGranted
            if (showMusicSharing) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(StartCream.copy(alpha = 0.9f))
                        .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Music in reflection",
                            color = StartInk,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            (if (musicReflectionSharingSelected) "On for this run. " else "Optional. ") +
                                "Shares one reported Spotify title and artist with RunState’s backend and Anthropic. " +
                                "Partial observation; no audio.",
                            color = StartInk.copy(alpha = 0.78f),
                            fontSize = 10.sp,
                            lineHeight = 13.sp
                        )
                    }
                    TextButton(
                        onClick = onToggleMusicReflectionSharing,
                        enabled = actionsEnabled,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag(START_MUSIC_SHARING_TOGGLE_TAG)
                    ) {
                        Text(
                            if (musicReflectionSharingSelected) "Turn off" else "Include",
                            color = StartInk,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            val energySpacerBase = (if (showMusicSharing) 750.dp else 670.dp) +
                (if (reflectionConnectionLabel != null) 48.dp else 0.dp)
            Spacer(modifier = Modifier.height((screenHeight - energySpacerBase).coerceAtLeast(20.dp)))
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
                        if (lifecycleStarted && animationsEnabled && ringVisible && root != null &&
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
                onTileBounds = { energy, bounds ->
                    if (tileBounds[energy] != null && tileBounds[energy] != bounds) intake = null
                    tileBounds[energy] = bounds
                }
            )
        }

        StartFooter(
            onStart = onStart,
            enabled = actionsEnabled,
            gpsSelected = gpsSelected,
            energySelected = selectedPreRunEnergy != null,
            phase = ringPhase,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        intake?.let { current ->
            EnergyIntakeOverlay(current, time = { intakeTime.value })
        }
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
    ringSize: Dp,
    phase: State<Float>,
    intakeTime: () -> Float?,
    ringModifier: Modifier
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.96f))
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "RUN",
                color = StartLime,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp
            )
            Text(
                text = "STATE",
                color = StartInk,
                fontSize = 23.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                lineHeight = 26.sp
            )
            Text(
                text = "AGENT  •  READY",
                color = StartInk,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.4.sp
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
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            Text(
                text = "READY",
                color = StartInk,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )
            Text(
                text = "TO RUN",
                color = StartInk,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )
            Text(
                text = "Ready when you are",
                color = StartInk.copy(alpha = 0.72f),
                fontSize = 9.sp,
                maxLines = 2,
                textAlign = TextAlign.End
            )
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
    onTileBounds: (PreRunEnergy, Rect) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "PRE-RUN ENERGY",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 2.sp,
            modifier = Modifier
                .graphicsLayer {
                    alpha = when {
                        selected == null -> 1f
                        intake != null -> 1f - ((intakeTime() - 470f) / 200f).coerceIn(0f, 1f)
                        else -> 0f
                    }
                }
                .clip(RoundedCornerShape(12.dp))
                .background(StartForest.copy(alpha = 0.75f))
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .then(if (selected != null) Modifier.clearAndSetSemantics {} else Modifier)
        )
        Spacer(Modifier.height(10.dp))
        // Preserve the existing row height and Undo position while the tiles leave for the agent.
        Box(Modifier.fillMaxWidth().height(82.dp), contentAlignment = Alignment.Center) {
            if (selected == null || intake != null) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .graphicsLayer { alpha = if (selected == null) 1f else 0f }
                        .then(if (selected != null) Modifier.clearAndSetSemantics {} else Modifier),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StartEnergyLevels.forEach { energy ->
                        EnergyTile(
                            energy = energy,
                            onClick = { onSelect(energy) },
                            selected = false,
                            enabled = enabled && selected == null,
                            modifier = Modifier.weight(1f).onGloballyPositioned {
                                onTileBounds(energy, it.boundsInRoot())
                            },
                            tag = if (selected == null) when (energy) {
                                PreRunEnergy.LOW -> START_ENERGY_LOW_TAG
                                PreRunEnergy.MODERATE -> START_ENERGY_MODERATE_TAG
                                PreRunEnergy.HIGH -> START_ENERGY_HIGH_TAG
                            } else null
                        )
                    }
                }
            }
            if (selected != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Spacer(Modifier.width(106.dp).height(82.dp))
                    TextButton(
                        onClick = { onSelect(null) },
                        enabled = enabled,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag(START_ENERGY_UNDO_TAG)
                    ) {
                        Text("Undo", color = StartInk, fontWeight = FontWeight.Bold)
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
    labelAlpha: () -> Float = { 1f }
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
            .height(82.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(StartInk.copy(alpha = if (enabled) 0.89f else 0.95f))
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
        Canvas(Modifier.size(18.dp)) {
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
            modifier = Modifier.graphicsLayer { alpha = labelAlpha() }
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
    modifier: Modifier = Modifier
) {
    val readyGlow by animateFloatAsState(
        targetValue = if (energySelected && enabled) 1f else 0f,
        animationSpec = tween(500, easing = IntakeMarkEase),
        label = "startReadyGlow"
    )
    Box(modifier = modifier.fillMaxWidth().height(184.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val boundary = Path().apply {
                moveTo(0f, size.height * 0.23f)
                cubicTo(
                    size.width * 0.21f, size.height * 0.35f,
                    size.width * 0.37f, size.height * 0.02f,
                    size.width * 0.5f, size.height * 0.08f
                )
                cubicTo(
                    size.width * 0.68f, 0f,
                    size.width * 0.82f, size.height * 0.36f,
                    size.width, size.height * 0.23f
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
                .padding(horizontal = 20.dp, vertical = 13.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            FooterLabel("SHOES", "LATER", R.drawable.ic_shoe_sneaker)
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
            FooterLabel("MUSIC", "LATER", R.drawable.start_music_orb, artwork = true)
        }
    }
}

@Composable
private fun FooterLabel(label: String, sublabel: String, icon: Int, artwork: Boolean = false) {
    Column(
        modifier = Modifier
            .width(72.dp)
            .padding(bottom = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(if (artwork) 64.dp else 52.dp)
                .clip(CircleShape)
                .background(Color(0xFF172115).copy(alpha = 0.82f))
                .then(if (artwork) Modifier else Modifier
                    .border(1.dp, Color(0xFF9DAF74).copy(alpha = 0.45f), CircleShape)),
            contentAlignment = Alignment.Center
        ) {
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
        val dialInk = Color(0xFF182116)
        drawCircle(dialInk, radius = r)
        drawCircle(Color.White.copy(alpha = 0.10f), radius = r, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
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
            moveTo(center.x - r * 0.23f, center.y - r * 0.36f)
            lineTo(center.x + r * 0.42f, center.y)
            lineTo(center.x - r * 0.23f, center.y + r * 0.36f)
            close()
        }
        drawPath(triangle, Color.White)
        val inset = Path().apply {
            moveTo(center.x - r * 0.12f, center.y - r * 0.20f)
            lineTo(center.x + r * 0.24f, center.y)
            lineTo(center.x - r * 0.12f, center.y + r * 0.20f)
            close()
        }
        drawPath(inset, dialInk)
        drawLine(Color(0xFFF5BE37),
            Offset(center.x - r * 0.05f, center.y),
            Offset(center.x + r * 0.12f, center.y),
            strokeWidth = 5.dp.toPx(), cap = StrokeCap.Square)
    }
}
