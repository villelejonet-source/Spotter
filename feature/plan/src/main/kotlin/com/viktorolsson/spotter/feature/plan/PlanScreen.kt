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
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.viktorolsson.spotter.core.engine.PlanGenerator
import com.viktorolsson.spotter.core.model.EffortScale
import com.viktorolsson.spotter.core.model.PlanDay
import com.viktorolsson.spotter.core.model.PlanExercise
import com.viktorolsson.spotter.core.model.isTimed
import com.viktorolsson.spotter.core.ui.component.ConfirmDialog
import com.viktorolsson.spotter.core.ui.component.EmptyState
import com.viktorolsson.spotter.core.ui.component.Eyebrow
import com.viktorolsson.spotter.core.ui.component.LoadingState
import com.viktorolsson.spotter.core.ui.component.SectionHeader
import com.viktorolsson.spotter.core.ui.component.SpotterTopBar
import com.viktorolsson.spotter.core.ui.component.SupersetBadge
import com.viktorolsson.spotter.core.ui.formatEffort
import com.viktorolsson.spotter.core.ui.formatExerciseCount
import com.viktorolsson.spotter.core.ui.formatTarget
import com.viktorolsson.spotter.core.ui.theme.Dimens
import com.viktorolsson.spotter.core.ui.theme.Spacing

@Composable
internal fun PlanRoute(
    onBack: () -> Unit,
    onRebuild: () -> Unit,
    onOpenSession: (Long) -> Unit,
    viewModel: PlanViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PlanScreen(
        state = state,
        onBack = onBack,
        onRebuild = onRebuild,
        onStartDay = { viewModel.startDay(it, onOpenSession) },
    )
}

@Composable
internal fun PlanScreen(
    state: PlanUiState,
    onBack: () -> Unit,
    onRebuild: () -> Unit,
    onStartDay: (planDayId: Long) -> Unit,
) {
    var confirmRebuild by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = {
            SpotterTopBar(
                title = stringResource(R.string.plan_title),
                onBack = onBack,
                actions = {
                    if (state.plan != null) {
                        IconButton(onClick = { confirmRebuild = true }) { Icon(Icons.Rounded.Refresh, stringResource(R.string.plan_rebuild)) }
                    }
                },
            )
        },
    ) { padding ->
        val plan = state.plan
        when {
            state.loading -> LoadingState(Modifier.padding(padding).fillMaxSize())
            plan == null -> EmptyState(
                icon = Icons.AutoMirrored.Rounded.EventNote,
                title = stringResource(R.string.plan_none_title),
                body = stringResource(R.string.plan_none_body),
                actionLabel = stringResource(R.string.plan_none_action),
                onAction = onRebuild,
                modifier = Modifier.padding(padding).fillMaxSize(),
            )
            else -> LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(Dimens.ScreenPadding),
                verticalArrangement = Arrangement.spacedBy(Dimens.ListGap),
            ) {
                item { SectionHeader(plan.name) }
                items(plan.days, key = { it.id }) { day ->
                    DayCard(day, isNext = day.id == state.nextDayId, effortScale = state.effortScale) { onStartDay(day.id) }
                }
            }
        }
    }
    if (confirmRebuild) {
        ConfirmDialog(
            title = stringResource(R.string.plan_rebuild_title),
            text = stringResource(R.string.plan_rebuild_body),
            confirmText = stringResource(R.string.plan_rebuild),
            onConfirm = { confirmRebuild = false; onRebuild() },
            onDismiss = { confirmRebuild = false },
        )
    }
}

@Composable
private fun DayCard(day: PlanDay, isNext: Boolean, effortScale: EffortScale, onStart: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isNext) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Dimens.CardPadding), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    if (isNext) Eyebrow(stringResource(R.string.plan_next))
                    Text(day.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        stringResource(R.string.plan_day_meta, PlanGenerator.estimateMinutes(day.exercises), formatExerciseCount(day.exercises.size)),
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
            day.exercises.forEach { PlanExerciseRow(it, effortScale) }
        }
    }
}

@Composable
internal fun PlanExerciseRow(exercise: PlanExercise, effortScale: EffortScale) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            exercise.exercise.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (exercise.supersetGroup != null) SupersetBadge()
        val target = formatTarget(exercise.sets, exercise.repMin, exercise.repMax, exercise.exercise.isTimed)
        // Reps in reserve don't apply to a hold.
        val rir = exercise.targetRir?.takeUnless { exercise.exercise.isTimed }
        Text(
            if (rir != null) stringResource(R.string.plan_target_rir, target, formatEffort(rir, effortScale)) else target,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

