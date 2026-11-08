package com.viktorolsson.spotter.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.viktorolsson.spotter.core.engine.LiftOption
import com.viktorolsson.spotter.core.engine.StalledLift
import com.viktorolsson.spotter.core.engine.SupportReason
import com.viktorolsson.spotter.core.model.RecommendationAction
import com.viktorolsson.spotter.core.model.WeightUnit
import com.viktorolsson.spotter.core.model.isTimed
import com.viktorolsson.spotter.core.ui.formatSet
import com.viktorolsson.spotter.core.ui.resources.*
import com.viktorolsson.spotter.core.ui.resources.Res
import com.viktorolsson.spotter.core.ui.theme.Dimens
import com.viktorolsson.spotter.core.ui.theme.Spacing
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Identifies [option] among everything on screen, by its lift and what it does. */
fun optionKey(lift: StalledLift, option: LiftOption): String = "${lift.plateau.slot.exercise.id}|${option.key}"

/**
 * A stalled lift, why it's flagged, and its options as rows to tick. [picked] holds
 * [optionKey]s, so several lifts can share one selection (the plan review).
 */
@Composable
fun StalledLiftOptions(lift: StalledLift, unit: WeightUnit, picked: Set<String>, onToggle: (String) -> Unit) {
    val exercise = lift.plateau.slot.exercise
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(stringResource(Res.string.lift_stalled, exercise.name), style = MaterialTheme.typography.titleMedium)
        lift.plateau.latest?.let { best ->
            Text(
                stringResource(Res.string.lift_stalled_evidence, formatSet(best.weightKg, best.reps, unit, timed = exercise.isTimed), lift.plateau.sessions),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        lift.options.forEach { option ->
            val key = optionKey(lift, option)
            val (title, body) = describe(option)
            ChoiceRow(title, body, checked = key in picked, onToggle = { onToggle(key) })
        }
    }
}

/** A stalled lift on its own (Progress, exercise history): tick options, then apply or set it aside. */
@Composable
fun StalledLiftCard(
    lift: StalledLift,
    unit: WeightUnit,
    onApply: (List<LiftOption>) -> Unit,
    onNotNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var picked by rememberSaveable(lift.plateau.slot.exercise.id) { mutableStateOf(emptySet<String>()) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Dimens.CardPadding), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            StalledLiftOptions(lift, unit, picked) { key -> picked = if (key in picked) picked - key else picked + key }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onNotNow) { Text(stringResource(Res.string.lift_not_now)) }
                // Only what's still offered: the list is rebuilt as the plan changes.
                val chosen = lift.options.filter { optionKey(lift, it) in picked }
                Button(
                    onClick = { onApply(chosen) },
                    enabled = chosen.isNotEmpty(),
                    modifier = Modifier.padding(start = Spacing.sm),
                ) { Text(stringResource(Res.string.lift_apply, chosen.size)) }
            }
        }
    }
}

@Composable
private fun describe(option: LiftOption): Pair<String, String> = when (val a = option.action) {
    is RecommendationAction.AddExercise ->
        a.exerciseName to stringResource(Res.string.lift_add_to, option.reason?.let { stringResource(it.label) }.orEmpty(), a.dayName)
    is RecommendationAction.SwapVariation ->
        stringResource(Res.string.lift_variation, a.toName) to stringResource(Res.string.lift_variation_body, a.weeks, a.fromName)
    is RecommendationAction.ChangeRepRange ->
        stringResource(Res.string.lift_range, a.sets, a.repMin, a.repMax) to stringResource(Res.string.lift_range_body, a.fromSets, a.fromRepMin, a.fromRepMax)
    is RecommendationAction.AddSets ->
        stringResource(Res.string.lift_more_sets) to stringResource(Res.string.lift_more_sets_body, a.exerciseNames.distinct().joinToString(", "))
    // A lighter week is its own card, never an option for one lift.
    is RecommendationAction.Deload -> "" to ""
}

@Composable
private fun ChoiceRow(title: String, body: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() }),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // The row is the toggle; the box only shows its state.
        Checkbox(checked = checked, onCheckedChange = null)
    }
}

private val SupportReason.label: StringResource
    get() = when (this) {
        SupportReason.UPPER_CHEST -> Res.string.support_upper_chest
        SupportReason.TRICEPS -> Res.string.support_triceps
        SupportReason.SHOULDERS -> Res.string.support_shoulders
        SupportReason.SIDE_DELTS -> Res.string.support_side_delts
        SupportReason.UPPER_BACK -> Res.string.support_upper_back
        SupportReason.REAR_DELTS -> Res.string.support_rear_delts
        SupportReason.LATS -> Res.string.support_lats
        SupportReason.BICEPS -> Res.string.support_biceps
        SupportReason.QUADS -> Res.string.support_quads
        SupportReason.HAMSTRINGS -> Res.string.support_hamstrings
        SupportReason.GLUTES -> Res.string.support_glutes
        SupportReason.LOWER_BACK -> Res.string.support_lower_back
        SupportReason.CORE -> Res.string.support_core
    }
