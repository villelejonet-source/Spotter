package com.viktorolsson.spotter.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.viktorolsson.spotter.core.model.SplitType
import com.viktorolsson.spotter.core.model.isTimed
import com.viktorolsson.spotter.core.ui.component.LoadingState
import com.viktorolsson.spotter.core.ui.component.SectionHeader
import com.viktorolsson.spotter.core.ui.formatExerciseCount
import com.viktorolsson.spotter.core.ui.formatTarget
import com.viktorolsson.spotter.core.ui.labelRes
import com.viktorolsson.spotter.core.ui.theme.Dimens
import com.viktorolsson.spotter.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SummaryStep(state: OnboardingUiState, onSplit: (SplitType?) -> Unit) {
    val generated = state.generated
    Title(R.string.onb_summary_title)
    if (generated == null) {
        LoadingState(Modifier.fillMaxWidth())
        return
    }
    SectionHeader(generated.plan.name)

    Text(stringResource(R.string.onb_summary_split), style = MaterialTheme.typography.titleSmall)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        items(
            listOf(
                null to R.string.onb_split_auto,
                SplitType.FULL_BODY to R.string.onb_split_full,
                SplitType.UPPER_LOWER to R.string.onb_split_upper_lower,
                SplitType.PUSH_PULL_LEGS to R.string.onb_split_ppl,
            ).filter { (split, _) -> split != SplitType.PUSH_PULL_LEGS || (state.answers.days ?: 0) >= 3 },
        ) { (split, label) ->
            FilterChip(selected = state.splitOverride == split, onClick = { onSplit(split) }, label = { Text(stringResource(label)) })
        }
    }

    if (generated.unfilledPatterns.isNotEmpty()) {
        val names = generated.unfilledPatterns.map { stringResource(it.labelRes).lowercase() }.joinToString(", ")
        // A heads-up, not a failure: tertiary, not the error tones.
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
            Text(
                stringResource(R.string.onb_summary_missing, names),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.padding(Dimens.CardPadding),
            )
        }
    }

    generated.plan.days.forEachIndexed { i, day ->
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(Dimens.CardPadding), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(day.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(
                        stringResource(R.string.onb_summary_day_meta, generated.estimatedMinutes[i], formatExerciseCount(day.exercises.size)),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                day.exercises.forEach { ex ->
                    Row {
                        Text(
                            ex.exercise.name,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            formatTarget(ex.sets, ex.repMin, ex.repMax, ex.exercise.isTimed),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
    Text(
        stringResource(R.string.onb_summary_calibration),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
