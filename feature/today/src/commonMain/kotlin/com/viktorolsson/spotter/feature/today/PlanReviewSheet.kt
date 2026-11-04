package com.viktorolsson.spotter.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.viktorolsson.spotter.core.engine.LiftOption
import com.viktorolsson.spotter.core.engine.StalledLift
import com.viktorolsson.spotter.core.engine.PlanReview
import com.viktorolsson.spotter.core.model.WeightUnit
import com.viktorolsson.spotter.core.model.formatChange
import com.viktorolsson.spotter.core.ui.component.CardTitle
import com.viktorolsson.spotter.core.ui.component.Delta
import com.viktorolsson.spotter.core.ui.component.StalledLiftOptions
import com.viktorolsson.spotter.core.ui.component.optionKey
import com.viktorolsson.spotter.core.ui.formatShortDate
import com.viktorolsson.spotter.core.ui.theme.Dimens
import com.viktorolsson.spotter.core.ui.theme.Spacing
import com.viktorolsson.spotter.feature.today.resources.*
import com.viktorolsson.spotter.feature.today.resources.Res
import kotlinx.datetime.daysUntil
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
    onApply: (List<Pair<StalledLift, LiftOption>>) -> Unit,
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
    onApply: (List<Pair<StalledLift, LiftOption>>) -> Unit,
    onKeep: () -> Unit,
    initiallyPicked: Set<String> = emptySet(),
) {
    var picked by rememberSaveable { mutableStateOf(initiallyPicked) }
    val choices = review.stalled.flatMap { lift -> lift.options.map { optionKey(lift, it) to (lift to it) } }.toMap()
    // Only what's still offered: the review is rebuilt as the plan changes.
    val chosen = picked.mapNotNull(choices::get)
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
            StalledLiftOptions(lift, unit, picked, ::toggle)
        }

        Row(
            Modifier.fillMaxWidth().padding(bottom = Spacing.lg),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onKeep) { Text(stringResource(Res.string.review_keep)) }
            Button(
                onClick = { onApply(chosen) },
                enabled = chosen.isNotEmpty(),
                modifier = Modifier.padding(start = Spacing.sm),
            ) { Text(stringResource(Res.string.review_apply, chosen.size)) }
        }
    }
}
