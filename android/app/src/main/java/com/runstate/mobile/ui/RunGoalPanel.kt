package com.runstate.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.testTag
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
import kotlin.math.roundToLong

internal const val RUN_GOAL_SHEET_TAG = "runGoalSheet"
internal const val RUN_GOAL_INPUT_TAG = "runGoalInput"
internal const val RUN_GOAL_SAVE_TAG = "runGoalSave"
internal const val RUN_GOAL_REMOVE_TAG = "runGoalRemove"
internal const val RUN_GOAL_CLOSE_TAG = "runGoalClose"
internal const val RUN_GOAL_DISTANCE_TAG = "runGoalDistance"
internal const val RUN_GOAL_TIME_TAG = "runGoalTime"
internal const val RUN_GOAL_MILES_TAG = "runGoalMiles"
internal const val RUN_GOAL_KILOMETERS_TAG = "runGoalKilometers"

// Bound accidental enormous entries without tying goals to shoe mileage or coaching rules.
internal const val MAX_RUN_GOAL_METERS = 1_000_000.0
internal const val MAX_RUN_GOAL_MINUTES = 10_080.0
private val GoalInk = Color(0xFF293326)
private val GoalForest = Color(0xFF1D261B)
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

/** Input stays a draft until Save. Never construct a goal from a partial/invalid edit. */
internal fun runGoalFromInput(text: String, distance: Boolean, unit: DistanceUnit): RunGoal? {
    val clean = text.trim().replace(',', '.')
    if (!clean.matches(Regex("(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)"))) return null
    val value = clean.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 } ?: return null
    return if (distance) {
        (value * unit.metersPerUnit()).takeIf { it.isFinite() && it <= MAX_RUN_GOAL_METERS }
            ?.let { RunGoal.Distance(it) }
    } else {
        value.takeIf { it <= MAX_RUN_GOAL_MINUTES }?.times(60_000.0)?.roundToLong()
            ?.takeIf { it > 0L }?.let { RunGoal.Time(it) }
    }
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
        mutableStateOf((selectedGoal as? RunGoal.Distance)?.let { runGoalInput(it, distanceUnit) }.orEmpty())
    }
    var timeText by rememberSaveable(selectedGoal) {
        mutableStateOf((selectedGoal as? RunGoal.Time)?.let { runGoalInput(it, distanceUnit) }.orEmpty())
    }
    val input = if (distanceSelected) distanceText else timeText
    val parsed = runGoalFromInput(input, distanceSelected, unit)
    // Display rounding and switching units must not rewrite a previously chosen exact target.
    val candidate = if (selectedGoal != null &&
        distanceSelected == (selectedGoal is RunGoal.Distance) &&
        input.trim().replace(',', '.') == runGoalInput(selectedGoal, unit)) selectedGoal else parsed
    val invalid = input.isNotBlank() && candidate == null

    val chipColors = FilterChipDefaults.filterChipColors(
        containerColor = Color.White,
        labelColor = GoalInk,
        selectedContainerColor = GoalForest,
        selectedLabelColor = Color.White
    )

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

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilterChip(
                selected = distanceSelected,
                onClick = { distanceSelected = true },
                enabled = enabled,
                label = { Text("Distance") },
                colors = chipColors,
                modifier = Modifier.weight(1f).testTag(RUN_GOAL_DISTANCE_TAG)
            )

            FilterChip(
                selected = !distanceSelected,
                onClick = { distanceSelected = false },
                enabled = enabled,
                label = { Text("Time") },
                colors = chipColors,
                modifier = Modifier.weight(1f).testTag(RUN_GOAL_TIME_TAG)
            )
        }

        if (distanceSelected) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DistanceUnit.entries.forEach { choice ->
                    FilterChip(
                        selected = unit == choice,
                        onClick = {
                            if (unit != choice) {
                                val distanceGoal = candidate as? RunGoal.Distance
                                distanceText = distanceGoal?.let { runGoalInput(it, choice) }.orEmpty()
                                unit = choice
                                onDistanceUnitChanged(choice)
                            }
                        },
                        enabled = enabled,
                        label = { Text(if (choice == DistanceUnit.MILES) "Miles" else "Kilometers") },
                        colors = chipColors,
                        modifier = Modifier.weight(1f).testTag(if (choice == DistanceUnit.MILES)
                            RUN_GOAL_MILES_TAG else RUN_GOAL_KILOMETERS_TAG)
                    )
                }
            }
        }
        OutlinedTextField(
            value = input,
            onValueChange = { value ->
                if (value.length <= 24) {
                    if (distanceSelected) distanceText = value else timeText = value
                }
            },
            enabled = enabled,
            label = { Text(if (!distanceSelected) "Minutes" else if (unit == DistanceUnit.MILES) "Miles" else "Kilometers") },
            placeholder = { Text(if (!distanceSelected) "30" else if (unit == DistanceUnit.MILES) "2" else "5") },
            supportingText = {
                Text(if (invalid) {
                    if (distanceSelected) "Enter a distance above 0 and no more than 1,000 km."
                    else "Enter minutes above 0 and no more than 10,080."
                } else if (distanceSelected) "Choose how far you want to run."
                else "Active running time. Pauses do not count.")
            },
            isError = invalid,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GoalForest,
                focusedLabelColor = GoalForest, cursorColor = GoalForest,
                focusedTextColor = GoalInk, unfocusedTextColor = GoalInk),
            modifier = Modifier.fillMaxWidth().testTag(RUN_GOAL_INPUT_TAG)
        )
        Text(
            "Your run keeps going until you finish it.",
            style = MaterialTheme.typography.bodyMedium,
            color = GoalInk.copy(alpha = .75f)
        )
        Button(
            onClick = { candidate?.let(onSave) },
            enabled = enabled && candidate != null,
            colors = ButtonDefaults.buttonColors(containerColor = GoalForest, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(RUN_GOAL_SAVE_TAG)
        ) { Text(if (selectedGoal == null) "Set goal" else "Save goal") }
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
