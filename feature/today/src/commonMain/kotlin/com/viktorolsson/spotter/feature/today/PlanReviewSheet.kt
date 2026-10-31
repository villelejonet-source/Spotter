package com.viktorolsson.spotter.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.viktorolsson.spotter.core.data.repository.PlanReviewChoice
import com.viktorolsson.spotter.core.engine.PlanReview
import com.viktorolsson.spotter.core.engine.StalledLift
import com.viktorolsson.spotter.core.engine.SupportReason
import com.viktorolsson.spotter.core.model.RecommendationAction
import com.viktorolsson.spotter.core.model.WeightUnit
import com.viktorolsson.spotter.core.model.formatChange
import com.viktorolsson.spotter.core.model.isTimed
import com.viktorolsson.spotter.core.ui.component.CardTitle
import com.viktorolsson.spotter.core.ui.component.Delta
import com.viktorolsson.spotter.core.ui.formatSet
import com.viktorolsson.spotter.core.ui.formatShortDate
import com.viktorolsson.spotter.core.ui.theme.Dimens
import com.viktorolsson.spotter.core.ui.theme.Spacing
import com.viktorolsson.spotter.feature.today.resources.*
import com.viktorolsson.spotter.feature.today.resources.Res
import kotlinx.datetime.daysUntil
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/** The review on Today: how long it's been and what stalled, with Later and Review. */
@Composable
internal fun PlanReviewCard(review: PlanReview, today: kotlinx.datetime.LocalDate, onLater: () -> Unit, onOpen: () -> Unit) {
    val weeks = (review.since.daysUntil(today) / 7).coerceAtLeast(1)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Dimens.CardPadding), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Icon(Icons.Rounded.Insights, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(Res.string.review_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Text(
                stringResource(
                    Res.string.review_card_body,
                    pluralStringResource(Res.plurals.review_weeks, weeks, weeks),
                    review.stalled.joinToString(", ") { it.plateau.slot.exercise.name },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onLater) { Text(stringResource(Res.string.review_later)) }
                Button(onClick = onOpen, modifier = Modifier.padding(start = Spacing.sm)) { Text(stringResource(Res.string.review_open)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlanReviewSheet(
    review: PlanReview,
    unit: WeightUnit,
    onApply: (List<PlanReviewChoice>) -> Unit,
    onKeep: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        PlanReviewContent(review, unit, onApply, onKeep)
    }
}

/** The sheet's body, usable on its own (screenshot tests). Nothing is ticked to start with. */
@Composable
internal fun PlanReviewContent(
    review: PlanReview,
    unit: WeightUnit,
    onApply: (List<PlanReviewChoice>) -> Unit,
    onKeep: () -> Unit,
    initiallyPicked: Set<String> = emptySet(),
) {
    // Keyed by "stalled lift id|exercise id", so a variation and an accessory never clash.
    var picked by rememberSaveable { mutableStateOf(initiallyPicked) }
    val choices = review.stalled.flatMap { lift ->
        val id = lift.plateau.slot.exercise.id
        val name = lift.plateau.slot.exercise.name
        lift.supports.map { (_, action) -> "$id|${action.exerciseId}" to PlanReviewChoice(id, name, action) } +
            listOfNotNull(lift.variation?.let { "$id|${it.toExerciseId}" to PlanReviewChoice(id, name, it) })
    }.toMap()
    fun toggle(key: String) {
        picked = if (key in picked) picked - key else picked + key
    }

    Column(
        Modifier.padding(horizontal = Dimens.SheetPadding).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(stringResource(Res.string.review_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Text(stringResource(Res.string.review_sheet_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (review.trends.isNotEmpty()) {
            CardTitle(stringResource(Res.string.review_progress, formatShortDate(review.since)))
            review.trends.forEach { trend ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(trend.exercise.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    val percent = (trend.change * 100).roundToInt()
                    Delta(formatChange(percent), improving = percent > 0)
                }
            }
        }

        review.stalled.forEach { lift ->
            HorizontalDivider()
            StalledLiftSection(lift, unit, picked, ::toggle)
        }

        Row(
            Modifier.fillMaxWidth().padding(bottom = Spacing.lg),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onKeep) { Text(stringResource(Res.string.review_keep)) }
            Button(
                onClick = { onApply(picked.mapNotNull(choices::get)) },
                enabled = picked.isNotEmpty(),
                modifier = Modifier.padding(start = Spacing.sm),
            ) { Text(stringResource(Res.string.review_apply, picked.size)) }
        }
    }
}

@Composable
private fun StalledLiftSection(lift: StalledLift, unit: WeightUnit, picked: Set<String>, onToggle: (String) -> Unit) {
    val exercise = lift.plateau.slot.exercise
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(stringResource(Res.string.review_stalled, exercise.name), style = MaterialTheme.typography.titleMedium)
        lift.plateau.latest?.let { best ->
            Text(
                stringResource(Res.string.review_stalled_evidence, formatSet(best.weightKg, best.reps, unit, timed = exercise.isTimed), lift.plateau.sessions),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        lift.supports.forEach { (support, action) ->
            ChoiceRow(
                title = action.exerciseName,
                body = stringResource(Res.string.review_add_to, stringResource(support.reason.label), action.dayName),
                checked = "${exercise.id}|${action.exerciseId}" in picked,
                onToggle = { onToggle("${exercise.id}|${action.exerciseId}") },
            )
        }
        lift.variation?.let { swap: RecommendationAction.SwapVariation ->
            ChoiceRow(
                title = stringResource(Res.string.review_variation, swap.toName),
                body = stringResource(Res.string.review_variation_body, swap.weeks, swap.fromName),
                checked = "${exercise.id}|${swap.toExerciseId}" in picked,
                onToggle = { onToggle("${exercise.id}|${swap.toExerciseId}") },
            )
        }
    }
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
