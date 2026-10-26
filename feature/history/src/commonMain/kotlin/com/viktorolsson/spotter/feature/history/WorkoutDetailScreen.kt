package com.viktorolsson.spotter.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.viktorolsson.spotter.core.model.EffortScale
import com.viktorolsson.spotter.core.model.PersonalRecord
import com.viktorolsson.spotter.core.model.SessionExercise
import com.viktorolsson.spotter.core.model.SetType
import com.viktorolsson.spotter.core.model.WeightUnit
import com.viktorolsson.spotter.core.model.WorkoutSummary
import com.viktorolsson.spotter.core.model.isTimed
import com.viktorolsson.spotter.core.ui.component.ErrorState
import com.viktorolsson.spotter.core.ui.component.LoadingState
import com.viktorolsson.spotter.core.ui.component.PrimaryButton
import com.viktorolsson.spotter.core.ui.component.RecordsCard
import com.viktorolsson.spotter.core.ui.component.SpotterTopBar
import com.viktorolsson.spotter.core.ui.component.StatTile
import com.viktorolsson.spotter.core.ui.formatClock
import com.viktorolsson.spotter.core.ui.formatEffort
import com.viktorolsson.spotter.core.ui.formatMediumDate
import com.viktorolsson.spotter.core.ui.formatSet
import com.viktorolsson.spotter.core.ui.formatTotal
import com.viktorolsson.spotter.core.ui.localDate
import com.viktorolsson.spotter.core.ui.theme.Dimens
import com.viktorolsson.spotter.core.ui.theme.Spacing
import com.viktorolsson.spotter.feature.history.resources.Res
import com.viktorolsson.spotter.feature.history.resources.detail_duration
import com.viktorolsson.spotter.feature.history.resources.detail_not_found_body
import com.viktorolsson.spotter.feature.history.resources.detail_not_found_title
import com.viktorolsson.spotter.feature.history.resources.detail_open_exercise
import com.viktorolsson.spotter.feature.history.resources.detail_prs
import com.viktorolsson.spotter.feature.history.resources.detail_record_set
import com.viktorolsson.spotter.feature.history.resources.detail_repeat
import com.viktorolsson.spotter.feature.history.resources.detail_sets
import com.viktorolsson.spotter.feature.history.resources.detail_swapped_from
import com.viktorolsson.spotter.feature.history.resources.detail_volume
import com.viktorolsson.spotter.feature.history.resources.detail_warmup
import com.viktorolsson.spotter.feature.history.resources.history_workout

@Composable
internal fun WorkoutDetailRoute(
    onBack: () -> Unit,
    onOpenExercise: (String) -> Unit,
    onOpenSession: (Long) -> Unit,
    viewModel: WorkoutDetailViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    WorkoutDetailScreen(
        state = state,
        onBack = onBack,
        onOpenExercise = onOpenExercise,
        onRepeat = { viewModel.repeat(onOpenSession) },
    )
}

@Composable
internal fun WorkoutDetailScreen(
    state: WorkoutDetailUiState,
    onBack: () -> Unit,
    onOpenExercise: (String) -> Unit,
    onRepeat: () -> Unit,
) {
    val session = state.session
    Scaffold(
        topBar = {
            SpotterTopBar(
                title = session?.planDayName ?: stringResource(Res.string.history_workout),
                subtitle = session?.let { formatMediumDate(it.startedAt.localDate()) },
                onBack = onBack,
            )
        },
        bottomBar = {
            if (session != null) {
                PrimaryButton(
                    text = stringResource(Res.string.detail_repeat),
                    onClick = onRepeat,
                    icon = Icons.Rounded.Replay,
                    modifier = Modifier.navigationBarsPadding().padding(Dimens.ScreenPadding),
                )
            }
        },
    ) { padding ->
        when {
            state.loading -> LoadingState(Modifier.padding(padding).fillMaxSize())
            session == null -> ErrorState(
                title = stringResource(Res.string.detail_not_found_title),
                body = stringResource(Res.string.detail_not_found_body),
                modifier = Modifier.padding(padding).fillMaxSize(),
            )
            else -> {
                val summary = remember(session) { WorkoutSummary.of(session) }
                val recordsByExercise = remember(state.records) { state.records.groupBy { it.exerciseId } }
                LazyColumn(
                    modifier = Modifier.padding(padding).fillMaxSize(),
                    contentPadding = PaddingValues(Dimens.ScreenPadding),
                    verticalArrangement = Arrangement.spacedBy(Dimens.ListGap),
                ) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            StatTile(stringResource(Res.string.detail_duration), formatClock(summary.duration), Modifier.weight(1f))
                            StatTile(stringResource(Res.string.detail_volume), formatTotal(summary.volumeKg, state.unit), Modifier.weight(1f))
                            StatTile(stringResource(Res.string.detail_sets), summary.completedSets.toString(), Modifier.weight(1f))
                        }
                    }
                    session.notes?.let { notes -> item { Text(notes, style = MaterialTheme.typography.bodyMedium) } }
                    if (state.records.isNotEmpty()) {
                        item { RecordsCard(stringResource(Res.string.detail_prs), state.records, state.unit) }
                    }
                    items(session.exercises, key = { it.id }) { exercise ->
                        ExerciseSetsCard(
                            exercise = exercise,
                            unit = state.unit,
                            effortScale = state.effortScale,
                            records = recordsByExercise[exercise.exercise.id].orEmpty(),
                            onOpen = { onOpenExercise(exercise.exercise.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExerciseSetsCard(
    exercise: SessionExercise,
    unit: WeightUnit,
    effortScale: EffortScale,
    records: List<PersonalRecord>,
    onOpen: () -> Unit,
) {
    // The record was set once: mark the first set that achieved it.
    val recordSetIds = remember(exercise, records) {
        records.mapNotNull { record ->
            exercise.sets.firstOrNull { it.weightKg == record.weightKg && it.reps == record.reps }?.id
        }.toSet()
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(start = Dimens.CardPadding, end = Spacing.sm, top = Spacing.sm, bottom = Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    exercise.exercise.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onOpen) {
                    Text(stringResource(Res.string.detail_open_exercise))
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
                }
            }
            exercise.substitutedFromName?.let {
                Text(stringResource(Res.string.detail_swapped_from, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
            }
            var working = 0
            exercise.sets.forEach { set ->
                if (set.setType != SetType.WARMUP) working++
                val isRecord = set.id in recordSetIds
                Row(Modifier.padding(vertical = Spacing.xxs), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (set.setType == SetType.WARMUP) stringResource(Res.string.detail_warmup) else working.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(SET_NUMBER_WIDTH),
                    )
                    Text(
                        formatSet(set.weightKg, set.reps, unit, exercise.exercise.isTimed),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    set.rir?.let {
                        Text(
                            formatEffort(it, effortScale),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = Spacing.sm),
                        )
                    }
                    if (isRecord) {
                        Icon(
                            Icons.Rounded.EmojiEvents,
                            contentDescription = stringResource(Res.string.detail_record_set),
                            tint = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
            }
            exercise.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

private val SET_NUMBER_WIDTH = 32.dp
