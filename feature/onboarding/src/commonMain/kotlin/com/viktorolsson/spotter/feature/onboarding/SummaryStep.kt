package com.viktorolsson.spotter.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.viktorolsson.spotter.core.model.Program
import com.viktorolsson.spotter.core.ui.component.LoadingState
import com.viktorolsson.spotter.core.ui.component.OptionCard
import com.viktorolsson.spotter.core.ui.component.ProgramCard
import com.viktorolsson.spotter.core.ui.component.SectionHeader
import com.viktorolsson.spotter.core.ui.formatExerciseCount
import com.viktorolsson.spotter.core.ui.formatPlanTarget
import com.viktorolsson.spotter.core.ui.labelRes
import com.viktorolsson.spotter.core.ui.theme.Dimens
import com.viktorolsson.spotter.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import com.viktorolsson.spotter.feature.onboarding.resources.Res
import com.viktorolsson.spotter.feature.onboarding.resources.onb_split_auto
import com.viktorolsson.spotter.feature.onboarding.resources.onb_split_auto_body
import com.viktorolsson.spotter.feature.onboarding.resources.onb_summary_calibration
import com.viktorolsson.spotter.feature.onboarding.resources.onb_summary_day_meta
import com.viktorolsson.spotter.feature.onboarding.resources.onb_summary_missing
import com.viktorolsson.spotter.feature.onboarding.resources.onb_summary_split
import com.viktorolsson.spotter.feature.onboarding.resources.onb_summary_title

@Composable
internal fun SummaryStep(state: OnboardingUiState, onProgram: (Program?) -> Unit) {
    val generated = state.generated
    Title(Res.string.onb_summary_title)
    if (generated == null) {
        LoadingState(Modifier.fillMaxWidth())
        return
    }
    SectionHeader(generated.plan.name)

    Text(stringResource(Res.string.onb_summary_split), style = MaterialTheme.typography.titleSmall)
    OptionCard(
        title = stringResource(Res.string.onb_split_auto),
        selected = state.program == null,
        onClick = { onProgram(null) },
        subtitle = stringResource(Res.string.onb_split_auto_body),
    )
    Program.entries.forEach { program ->
        val blocker = program.blocker(state.answers.equipment, state.answers.days ?: 0)
        val selected = state.program == program
        ProgramCard(
            program = program,
            daysPerWeek = if (program == Program.FIVE_THREE_ONE) Program.FIVE_THREE_ONE_DAYS else state.answers.days,
            // A blocked card stays, saying why, but can't be picked.
            onClick = { if (blocker == null) onProgram(program) },
            suits = program in state.suited,
            blocker = blocker,
            selected = selected,
            expanded = selected,
        )
    }

    if (generated.unfilledPatterns.isNotEmpty()) {
        val names = generated.unfilledPatterns.map { stringResource(it.labelRes).lowercase() }.joinToString(", ")
        // A heads-up, not a failure: tertiary, not the error tones.
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
            Text(
                stringResource(Res.string.onb_summary_missing, names),
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
                        stringResource(Res.string.onb_summary_day_meta, generated.estimatedMinutes[i], formatExerciseCount(day.exercises.size)),
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
                            formatPlanTarget(ex),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
    Text(
        stringResource(Res.string.onb_summary_calibration),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
