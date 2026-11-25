package com.runstate.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runstate.mobile.data.local.DistanceUnit
import com.runstate.mobile.run.RunGoal
import com.runstate.mobile.ui.theme.RunStateTheme
import java.math.BigDecimal
import java.math.MathContext

internal const val RUN_GOAL_SHEET_TAG = "runGoalSheet"
internal const val RUN_GOAL_INPUT_TAG = "runGoalInput"
internal const val RUN_GOAL_SAVE_TAG = "runGoalSave"
internal const val RUN_GOAL_REMOVE_TAG = "runGoalRemove"
internal const val RUN_GOAL_CLOSE_TAG = "runGoalClose"
internal const val RUN_GOAL_DISTANCE_TAG = "runGoalDistance"
internal const val RUN_GOAL_TIME_TAG = "runGoalTime"
internal const val RUN_GOAL_UNIT_TAG = "runGoalUnit"
internal const val RUN_GOAL_MILES_TAG = "runGoalMiles"
internal const val RUN_GOAL_KILOMETERS_TAG = "runGoalKilometers"

// Bound accidental enormous entries without tying goals to shoe mileage or coaching rules.
internal const val MAX_RUN_GOAL_METERS = 1_000_000.0
internal const val MAX_RUN_GOAL_MINUTES = 10_080.0
private val GoalInk = Color(0xFF293326)
private val GoalCream = Color(0xFFF9FAF5)

private fun DistanceUnit.metersPerUnit() = if (this == DistanceUnit.MILES) 1_609.344 else 1_000.0

internal fun runGoalNumber(value: Double): String = BigDecimal.valueOf(value)
    .round(MathContext(12)).stripTrailingZeros().toPlainString()

internal fun runGoalInput(goal: RunGoal, unit: DistanceUnit): String = runGoalNumber(when (goal) {
    is RunGoal.Distance -> goal.meters / unit.metersPerUnit()
    is RunGoal.Time -> goal.activeMillis / 60_000.0
})

internal fun runGoalLabel(goal: RunGoal, unit: DistanceUnit): String = runGoalInput(goal, unit) + when (goal) {
    is RunGoal.Distance -> if (unit == DistanceUnit.MILES) " mi" else " km"
    is RunGoal.Time -> " min"
}

/** Show seconds/milliseconds only when the saved goal needs that precision. */
internal fun runGoalTimeInput(goal: RunGoal.Time): String {
    val minutes = goal.activeMillis / 60_000L
    val clock = (minutes / 60L).toString().padStart(2, '0') + ":" +
        (minutes % 60L).toString().padStart(2, '0')
    if (goal.activeMillis % 60_000L == 0L) return clock
    val seconds = ((goal.activeMillis / 1_000L) % 60L).toString().padStart(2, '0')
    val millis = goal.activeMillis % 1_000L
    return "$clock:$seconds" + if (millis == 0L) "" else "." + millis.toString().padStart(3, '0')
}

/** Input stays a draft until Save. Never construct a goal from a partial/invalid edit. */
internal fun runGoalFromInput(text: String, distance: Boolean, unit: DistanceUnit): RunGoal? {
    if (!distance) {
        val clock = Regex("([0-9]{1,3}):([0-9]{2})(?::([0-9]{2})(?:\\.([0-9]{1,3}))?)?")
            .matchEntire(text.trim()) ?: return null
        val hours = clock.groupValues[1].toLong()
        val minutes = clock.groupValues[2].toLong()
        val seconds = clock.groupValues[3].ifEmpty { "0" }.toLong()
        val millis = clock.groupValues[4].padEnd(3, '0').toLong()
        if (minutes > 59L || seconds > 59L) return null
        val totalMillis = (hours * 60L + minutes) * 60_000L + seconds * 1_000L + millis
        return totalMillis.takeIf { it > 0L && it <= MAX_RUN_GOAL_MINUTES * 60_000L }
            ?.let { RunGoal.Time(it) }
    }
    val clean = text.trim().replace(',', '.')
    if (!clean.matches(Regex("(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)"))) return null
    val value = clean.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 } ?: return null
    return (value * unit.metersPerUnit()).takeIf { it.isFinite() && it <= MAX_RUN_GOAL_METERS }
        ?.let { RunGoal.Distance(it) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RunGoalSheet(
    selectedGoal: RunGoal?,
    distanceUnit: DistanceUnit,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onSave: (RunGoal?) -> Unit,
    onDistanceUnitChanged: (DistanceUnit) -> Unit = {}
) {
    val boundary = remember {
        object : NestedScrollConnection {
            override suspend fun onPostFling(consumed: Velocity, available: Velocity) =
                Velocity(0f, minOf(0f, available.y))
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = GoalCream,
        modifier = Modifier.testTag(RUN_GOAL_SHEET_TAG)
    ) {
        RunGoalPanel(
            modifier = Modifier.heightIn(max = LocalConfiguration.current.screenHeightDp.dp * .85f)
                .imePadding().nestedScroll(boundary).verticalScroll(rememberScrollState()),
            selectedGoal = selectedGoal, distanceUnit = distanceUnit, enabled = enabled,
            onDismiss = onDismiss, onSave = onSave, onDistanceUnitChanged = onDistanceUnitChanged
        )
    }
}

@Composable
internal fun RunGoalPanel(
    modifier: Modifier = Modifier,
    selectedGoal: RunGoal? = null,
    distanceUnit: DistanceUnit = DistanceUnit.MILES,
    enabled: Boolean = true,
    onDismiss: () -> Unit = {},
    onSave: (RunGoal?) -> Unit = {},
    onDistanceUnitChanged: (DistanceUnit) -> Unit = {}
) {
    var distanceSelected by rememberSaveable(selectedGoal) {
        mutableStateOf(selectedGoal !is RunGoal.Time)
    }
    var unit by rememberSaveable(selectedGoal) { mutableStateOf(distanceUnit) }
    var distanceText by rememberSaveable(selectedGoal) {
        mutableStateOf(runGoalInput(selectedGoal as? RunGoal.Distance
            ?: RunGoal.Distance(3.0 * 1_609.344), distanceUnit))
    }
    var timeText by rememberSaveable(selectedGoal) {
        mutableStateOf((selectedGoal as? RunGoal.Time)?.let(::runGoalTimeInput) ?: "00:30")
    }
    var unitMenuOpen by remember { mutableStateOf(false) }
    val input = if (distanceSelected) distanceText else timeText
    val parsed = runGoalFromInput(input, distanceSelected, unit)
    // Display rounding and switching units must not rewrite a previously chosen exact target.
    val originalInput = when (selectedGoal) {
        is RunGoal.Distance -> runGoalInput(selectedGoal, unit)
        is RunGoal.Time -> runGoalTimeInput(selectedGoal)
        null -> null
    }
    val candidate = if (selectedGoal != null &&
        distanceSelected == (selectedGoal is RunGoal.Distance) &&
        input.trim().replace(',', '.') == originalInput) selectedGoal else parsed
    val invalid = input.isNotBlank() && candidate == null
    val controlHeight = 64.dp * maxOf(1f, LocalDensity.current.fontScale)
    val controlShape = RoundedCornerShape(16.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(GoalCream)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Set a goal", fontSize = 24.sp, lineHeight = 29.sp,
                fontWeight = FontWeight.SemiBold, color = GoalInk, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp).testTag(RUN_GOAL_CLOSE_TAG)) {
                Text("Close", color = GoalInk)
            }
        }

        listOf(true to "Distance", false to "Time").forEach { (isDistance, label) ->
            val chosen = distanceSelected == isDistance
            Button(
                onClick = { distanceSelected = isDistance; unitMenuOpen = false },
                enabled = enabled,
                shape = controlShape,
                border = if (chosen) null else BorderStroke(1.dp, Color.Black.copy(alpha = .16f)),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (chosen) Color.Black else Color.White,
                    contentColor = if (chosen) Color.White else Color.Black),
                modifier = Modifier.fillMaxWidth().height(controlHeight)
                    .testTag(if (isDistance) RUN_GOAL_DISTANCE_TAG else RUN_GOAL_TIME_TAG)
                    .semantics { selected = chosen }
            ) { Text(label, fontSize = 18.sp, fontWeight = FontWeight.Medium) }
        }
        OutlinedTextField(
            value = input,
            onValueChange = { value ->
                if (value.length <= 24) {
                    if (distanceSelected) distanceText = value else timeText = value
                }
            },
            enabled = enabled,
            trailingIcon = {
                if (distanceSelected) Box {
                    TextButton(onClick = { unitMenuOpen = true }, enabled = enabled,
                        modifier = Modifier.testTag(RUN_GOAL_UNIT_TAG)
                            .semantics { contentDescription = "Distance unit" }) {
                        Text(if (unit == DistanceUnit.MILES) "mi ▾" else "km ▾", color = Color.Black)
                    }
                    DropdownMenu(expanded = unitMenuOpen, onDismissRequest = { unitMenuOpen = false }) {
                        DistanceUnit.entries.forEach { choice ->
                            DropdownMenuItem(
                                text = { Text(if (choice == DistanceUnit.MILES) "Miles" else "Kilometers") },
                                onClick = {
                                    if (unit != choice) {
                                        distanceText = (candidate as? RunGoal.Distance)
                                            ?.let { runGoalInput(it, choice) }.orEmpty()
                                        unit = choice
                                        onDistanceUnitChanged(choice)
                                    }
                                    unitMenuOpen = false
                                },
                                enabled = enabled,
                                modifier = Modifier.testTag(if (choice == DistanceUnit.MILES)
                                    RUN_GOAL_MILES_TAG else RUN_GOAL_KILOMETERS_TAG)
                                    .semantics { selected = unit == choice }
                            )
                        }
                    }
                } else Text(if (input.count { it == ':' } >= 2) "h:mm:ss" else "h:mm",
                    color = GoalInk, modifier = Modifier.padding(end = 16.dp))
            },
            isError = invalid,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 20.sp),
            shape = controlShape,
            keyboardOptions = KeyboardOptions(keyboardType = if (distanceSelected)
                KeyboardType.Decimal else KeyboardType.Ascii),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Black,
                cursorColor = Color.Black,
                focusedContainerColor = Color.White, unfocusedContainerColor = Color.White,
                unfocusedBorderColor = Color.Black.copy(alpha = .16f),
                focusedTextColor = GoalInk, unfocusedTextColor = GoalInk),
            modifier = Modifier.fillMaxWidth().height(controlHeight).testTag(RUN_GOAL_INPUT_TAG)
                .semantics {
                    contentDescription = if (distanceSelected) "Distance goal" else "Active time goal in hours and minutes"
                    if (invalid) error(if (distanceSelected) "Enter a positive distance up to 1,000 kilometers."
                        else "Enter hours and minutes, such as 00:30, up to 168:00.")
                }
        )
        Button(
            onClick = { candidate?.let(onSave) },
            enabled = enabled && candidate != null,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(RUN_GOAL_SAVE_TAG)
        ) { Text("Save goal") }
        if (selectedGoal != null) {
            TextButton(onClick = { onSave(null) }, enabled = enabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(RUN_GOAL_REMOVE_TAG)) {
                Text("Remove goal", color = GoalInk)
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun RunGoalPanelPreview() {
    RunStateTheme(darkTheme = false, dynamicColor = false) {
        RunGoalPanel()
    }
}
