package com.runstate.mobile.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runstate.mobile.R
import com.runstate.mobile.data.local.PreRunEnergy
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal const val START_RUN_SCREEN_TAG = "startRunScreen"
internal const val START_RUN_BUTTON_TAG = "startRunButton"
internal const val START_HISTORY_TAG = "startHistory"
internal const val START_ENERGY_LOW_TAG = "startEnergyLow"
internal const val START_ENERGY_MODERATE_TAG = "startEnergyModerate"
internal const val START_ENERGY_HIGH_TAG = "startEnergyHigh"
internal const val START_ENERGY_SELECTED_TAG = "startEnergySelected"
internal const val START_ENERGY_UNDO_TAG = "startEnergyUndo"
internal const val START_GPS_TOGGLE_TAG = "startGpsToggle"
internal const val START_MUSIC_TOGGLE_TAG = "startMusicToggle"

private val StartInk = Color(0xFF293326)
private val StartForest = Color(0xFF2D3827)
private val StartForestDeep = Color(0xFF1D261B)
private val StartLime = Color(0xFFB8D442)
private val StartCream = Color(0xFFF9FAF5)

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
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag(START_RUN_SCREEN_TAG)
    ) {
        val screenHeight = maxHeight
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
            AgentReadyCard()
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
                        else "Fixture run · GPS optional",
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
                        if (gpsSelected) "Use fixture" else "Use GPS",
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
            Spacer(modifier = Modifier.height((screenHeight - 670.dp).coerceAtLeast(20.dp)))
            PreRunEnergyChoices(
                selected = selectedPreRunEnergy,
                onSelect = onSelectPreRunEnergy,
                enabled = actionsEnabled
            )
        }

        StartFooter(
            onStart = onStart,
            enabled = actionsEnabled,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun AgentReadyCard() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Color.White.copy(alpha = 0.96f))
            .padding(horizontal = 20.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
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
        AgentRing(Modifier.size(69.dp))
        Column(horizontalAlignment = Alignment.End) {
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
private fun AgentRing(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val radius = size.minDimension * 0.43f
        repeat(56) { index ->
            val angle = index * 2.0 * PI / 56.0
            val direction = Offset(cos(angle).toFloat(), sin(angle).toFloat())
            val color = when (index) {
                in 0..17 -> Color(0xFFA4B7A2)
                in 18..34 -> Color(0xFFC1B1CC)
                else -> Color(0xFFD6D2A8)
            }
            drawLine(
                color = color,
                start = center + direction * radius * 0.79f,
                end = center + direction * radius,
                strokeWidth = 1.8.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun PreRunEnergyChoices(
    selected: PreRunEnergy?,
    onSelect: (PreRunEnergy?) -> Unit,
    enabled: Boolean
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "PRE-RUN ENERGY",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 2.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(StartForest.copy(alpha = 0.75f))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Spacer(Modifier.height(10.dp))
        if (selected == null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(PreRunEnergy.LOW, PreRunEnergy.MODERATE, PreRunEnergy.HIGH).forEach { energy ->
                    EnergyTile(
                        energy = energy,
                        onClick = { onSelect(energy) },
                        selected = false,
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                        tag = when (energy) {
                            PreRunEnergy.LOW -> START_ENERGY_LOW_TAG
                            PreRunEnergy.MODERATE -> START_ENERGY_MODERATE_TAG
                            PreRunEnergy.HIGH -> START_ENERGY_HIGH_TAG
                        }
                    )
                }
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                EnergyTile(
                    energy = selected,
                    onClick = {},
                    selected = true,
                    enabled = false,
                    modifier = Modifier.width(106.dp),
                    tag = START_ENERGY_SELECTED_TAG
                )
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

@Composable
private fun EnergyTile(
    energy: PreRunEnergy,
    onClick: () -> Unit,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    tag: String
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
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick
            )
            .testTag(tag),
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
            letterSpacing = 1.1.sp
        )
    }
}

@Composable
private fun StartFooter(
    onStart: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
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
            FooterLabel("SHOES", "LATER")
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .clickable(enabled = enabled, role = Role.Button, onClick = onStart)
                    .testTag(START_RUN_BUTTON_TAG)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                StartDial(Modifier.size(102.dp))
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "Start",
                    color = if (enabled) Color.White else Color.White.copy(alpha = 0.48f),
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 3.sp
                )
            }
            FooterLabel("MUSIC", "OPTIONAL")
        }
    }
}

@Composable
private fun FooterLabel(label: String, sublabel: String) {
    Column(
        modifier = Modifier
            .width(72.dp)
            .padding(bottom = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = Color.White.copy(alpha = 0.78f),
            fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text(sublabel, color = Color.White.copy(alpha = 0.5f),
            fontSize = 8.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
    }
}

@Composable
private fun StartDial(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val r = size.minDimension * 0.49f
        drawCircle(Color(0xFF182116), radius = r)
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
        drawLine(Color(0xFFF5BE37),
            Offset(center.x - r * 0.05f, center.y),
            Offset(center.x + r * 0.12f, center.y),
            strokeWidth = 5.dp.toPx(), cap = StrokeCap.Square)
    }
}
