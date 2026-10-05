package com.viktorolsson.spotter.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.viktorolsson.spotter.core.model.isTimed
import com.viktorolsson.spotter.core.ui.formatTarget

@Composable
internal fun TodayRoute(
    onOpenSession: (Long) -> Unit,
    onOpenPlan: () -> Unit,
    onBuildPlan: () -> Unit,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    TodayScreen(
        uiState = uiState,
        onResume = { uiState.activeSessionId?.let(onOpenSession) },
        onStartNext = { viewModel.startNext(onOpenSession) },
        onEmptyWorkout = { viewModel.startEmptyWorkout(onOpenSession) },
        onOpenPlan = onOpenPlan,
        onBuildPlan = onBuildPlan,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TodayScreen(
    uiState: TodayUiState,
    onResume: () -> Unit,
    onStartNext: () -> Unit,
    onEmptyWorkout: () -> Unit,
    onOpenPlan: () -> Unit,
    onBuildPlan: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.today_title)) }) },
    ) { padding ->
        if (uiState.loading) return@Scaffold
        Column(
            modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val next = uiState.nextDay
            uiState.deloadUntil?.let { until ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.today_deload_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        Text(
                            stringResource(R.string.today_deload_body, until.format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM"))),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
            }
            when {
                next != null -> NextWorkoutCard(next, onOpenPlan)
                !uiState.hasPlan -> NoPlanCard(onBuildPlan)
            }
            when {
                uiState.activeSessionId != null -> BigButton(R.string.today_resume_workout, onResume)
                next != null -> BigButton(R.string.today_start_workout, onStartNext)
            }
            if (uiState.activeSessionId == null) {
                OutlinedButton(onClick = onEmptyWorkout, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Text(stringResource(R.string.today_empty_workout), modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
                }
            }
        }
    }
}

@Composable
private fun BigButton(label: Int, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(64.dp)) {
        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
        Text(
            stringResource(label),
            modifier = Modifier.padding(start = ButtonDefaults.IconSpacing),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun NextWorkoutCard(day: PlanDay, onOpenPlan: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.today_next_label).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(day.name, style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.today_day_meta, PlanGenerator.estimateMinutes(day.exercises), day.exercises.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            val shown = day.exercises.take(PREVIEW_COUNT)
            shown.forEach { ex ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        ex.exercise.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(formatTarget(ex.sets, ex.repMin, ex.repMax, ex.exercise.isTimed), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (day.exercises.size > PREVIEW_COUNT) {
                    Text(
                        stringResource(R.string.today_more_exercises, day.exercises.size - PREVIEW_COUNT),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                TextButton(onClick = onOpenPlan) { Text(stringResource(R.string.today_view_plan)) }
            }
        }
    }
}

@Composable
private fun NoPlanCard(onBuildPlan: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.today_no_plan_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.today_no_plan_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onBuildPlan) { Text(stringResource(R.string.today_no_plan_action)) }
        }
    }
}

private const val PREVIEW_COUNT = 5
