package com.viktorolsson.spotter.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.viktorolsson.spotter.core.model.PersonalRecord
import com.viktorolsson.spotter.core.model.SessionExercise
import com.viktorolsson.spotter.core.model.SetType
import com.viktorolsson.spotter.core.model.WeightUnit
import com.viktorolsson.spotter.core.model.WorkoutSummary
import com.viktorolsson.spotter.core.model.isTimed
import com.viktorolsson.spotter.core.ui.formatClock
import com.viktorolsson.spotter.core.ui.formatMediumDate
import com.viktorolsson.spotter.core.ui.formatRecordValue
import com.viktorolsson.spotter.core.ui.formatSet
import com.viktorolsson.spotter.core.ui.formatTotal
import com.viktorolsson.spotter.core.ui.labelRes
import com.viktorolsson.spotter.core.ui.localDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorkoutDetailRoute(
    onBack: () -> Unit,
    onOpenExercise: (String) -> Unit,
    onOpenSession: (Long) -> Unit,
    viewModel: WorkoutDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val session = state.session
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(session?.planDayName ?: stringResource(R.string.history_workout))
                        session?.let {
                            Text(
                                formatMediumDate(it.startedAt.localDate()),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.history_back)) }
                },
            )
        },
        bottomBar = {
            if (session != null) {
                Button(
                    onClick = { viewModel.repeat(onOpenSession) },
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).height(56.dp),
                ) {
                    Icon(Icons.Rounded.Replay, contentDescription = null)
                    Text(stringResource(R.string.detail_repeat), modifier = Modifier.padding(start = 8.dp))
                }
            }
        },
    ) { padding ->
        if (session == null) return@Scaffold
        val summary = WorkoutSummary.of(session)
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stat(stringResource(R.string.detail_duration), formatClock(summary.duration), Modifier.weight(1f))
                    Stat(stringResource(R.string.detail_volume), formatTotal(summary.volumeKg, state.unit), Modifier.weight(1f))
                    Stat(stringResource(R.string.detail_sets), "${summary.completedSets}", Modifier.weight(1f))
                }
            }
            session.notes?.let { notes -> item { Text(notes, style = MaterialTheme.typography.bodyMedium) } }
            if (state.records.isNotEmpty()) {
                item { RecordsCard(state.records, state.unit) }
            }
            items(session.exercises, key = { it.id }) { exercise ->
                ExerciseSetsCard(
                    exercise = exercise,
                    unit = state.unit,
                    recordReps = state.records.filter { it.exerciseId == exercise.exercise.id },
                    onOpen = { onOpenExercise(exercise.exercise.id) },
                )
            }
        }
    }
}

@Composable
internal fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun RecordsCard(records: List<PersonalRecord>, unit: WeightUnit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.EmojiEvents, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                Text(
                    stringResource(R.string.detail_prs),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            records.forEach { record ->
                Row {
                    Text(
                        "${record.exerciseName} · ${stringResource(record.type.labelRes)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        formatRecordValue(record, unit),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExerciseSetsCard(
    exercise: SessionExercise,
    unit: WeightUnit,
    recordReps: List<PersonalRecord>,
    onOpen: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    exercise.exercise.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onOpen) {
                    Text(stringResource(R.string.detail_open_exercise))
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
                }
            }
            exercise.substitutedFromName?.let {
                Text("↔ $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
            }
            // The record was set once: mark the first set that achieved it.
            val recordSetIds = recordReps.mapNotNull { record ->
                exercise.sets.firstOrNull { it.weightKg == record.weightKg && it.reps == record.reps }?.id
            }.toSet()
            var working = 0
            exercise.sets.forEach { set ->
                if (set.setType != SetType.WARMUP) working++
                val isRepRecord = set.id in recordSetIds
                Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (set.setType == SetType.WARMUP) stringResource(R.string.detail_warmup) else "$working",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(32.dp),
                    )
                    Text(formatSet(set.weightKg, set.reps, unit, exercise.exercise.isTimed), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    set.rir?.let {
                        Text(
                            stringResource(R.string.detail_rir, it),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                    if (isRepRecord) Icon(Icons.Rounded.EmojiEvents, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                }
            }
            exercise.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
