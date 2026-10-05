package com.viktorolsson.spotter.feature.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.viktorolsson.spotter.core.engine.PlanGenerator
import com.viktorolsson.spotter.core.model.PlanDay
import com.viktorolsson.spotter.core.model.PlanExercise
import com.viktorolsson.spotter.core.model.isTimed
import com.viktorolsson.spotter.core.ui.component.EmptyState
import com.viktorolsson.spotter.core.ui.formatTarget

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlanRoute(
    onBack: () -> Unit,
    onRebuild: () -> Unit,
    onOpenSession: (Long) -> Unit,
    viewModel: PlanViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.plan_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.plan_back)) }
                },
                actions = {
                    if (state.plan != null) {
                        IconButton(onClick = onRebuild) { Icon(Icons.Rounded.Refresh, stringResource(R.string.plan_rebuild)) }
                    }
                },
            )
        },
    ) { padding ->
        val plan = state.plan
        when {
            state.loading -> Unit
            plan == null -> Column(Modifier.padding(padding).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                EmptyState(
                    icon = Icons.AutoMirrored.Rounded.EventNote,
                    title = stringResource(R.string.plan_none_title),
                    body = stringResource(R.string.plan_none_body),
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = onRebuild, modifier = Modifier.padding(32.dp)) { Text(stringResource(R.string.plan_none_action)) }
            }
            else -> LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { Text(plan.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary) }
                items(plan.days, key = { it.id }) { day ->
                    DayCard(day, isNext = day.id == state.nextDayId) { viewModel.startDay(day.id, onOpenSession) }
                }
            }
        }
    }
}

@Composable
private fun DayCard(day: PlanDay, isNext: Boolean, onStart: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isNext) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    if (isNext) {
                        Text(
                            stringResource(R.string.plan_next).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(day.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        stringResource(R.string.plan_day_meta, PlanGenerator.estimateMinutes(day.exercises), day.exercises.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (isNext) {
                    Button(onClick = onStart) { Text(stringResource(R.string.plan_start_day)) }
                } else {
                    FilledTonalButton(onClick = onStart) { Text(stringResource(R.string.plan_start_day)) }
                }
            }
            day.exercises.forEach { PlanExerciseRow(it) }
        }
    }
}

@Composable
internal fun PlanExerciseRow(exercise: PlanExercise) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            exercise.exercise.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (exercise.supersetGroup != null) {
            Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.small) {
                Text(
                    stringResource(R.string.plan_superset),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Text(
            formatTarget(exercise.sets, exercise.repMin, exercise.repMax, exercise.exercise.isTimed).let { target ->
                // Reps in reserve don't apply to a hold.
                exercise.targetRir?.takeUnless { exercise.exercise.isTimed }?.let { stringResource(R.string.plan_target_rir, target, it) } ?: target
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
