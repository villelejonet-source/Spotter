package com.viktorolsson.spotter.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.viktorolsson.spotter.core.engine.PlanGenerator
import com.viktorolsson.spotter.core.model.PlanDay
import com.viktorolsson.spotter.core.model.ScheduledWorkout
import com.viktorolsson.spotter.core.model.isTimed
import com.viktorolsson.spotter.core.ui.component.CardTitle
import com.viktorolsson.spotter.core.ui.component.Eyebrow
import com.viktorolsson.spotter.core.ui.component.LoadingState
import com.viktorolsson.spotter.core.ui.component.PrimaryButton
import com.viktorolsson.spotter.core.ui.component.SecondaryButton
import com.viktorolsson.spotter.core.ui.component.SpotterTopBar
import com.viktorolsson.spotter.core.ui.formatExerciseCount
import com.viktorolsson.spotter.core.ui.formatShortDate
import com.viktorolsson.spotter.core.ui.formatTarget
import com.viktorolsson.spotter.core.ui.theme.Dimens
import com.viktorolsson.spotter.core.ui.theme.Spacing
import com.viktorolsson.spotter.feature.today.resources.Res
import com.viktorolsson.spotter.feature.today.resources.today_coming_up
import com.viktorolsson.spotter.feature.today.resources.today_day_meta
import com.viktorolsson.spotter.feature.today.resources.today_deload_body
import com.viktorolsson.spotter.feature.today.resources.today_deload_title
import com.viktorolsson.spotter.feature.today.resources.today_empty_workout
import com.viktorolsson.spotter.feature.today.resources.today_free_workout
import com.viktorolsson.spotter.feature.today.resources.today_free_workout_body
import com.viktorolsson.spotter.feature.today.resources.today_more_exercises
import com.viktorolsson.spotter.feature.today.resources.today_next_label
import com.viktorolsson.spotter.feature.today.resources.today_no_plan_action
import com.viktorolsson.spotter.feature.today.resources.today_no_plan_body
import com.viktorolsson.spotter.feature.today.resources.today_no_plan_title
import com.viktorolsson.spotter.feature.today.resources.today_resume_workout
import com.viktorolsson.spotter.feature.today.resources.today_scheduled_done
import com.viktorolsson.spotter.feature.today.resources.today_scheduled_label
import com.viktorolsson.spotter.feature.today.resources.today_start_workout
import com.viktorolsson.spotter.feature.today.resources.today_title
import com.viktorolsson.spotter.feature.today.resources.today_view_plan

@Composable
internal fun TodayRoute(
    onOpenSession: (Long) -> Unit,
    onOpenPlan: () -> Unit,
    onBuildPlan: () -> Unit,
    viewModel: TodayViewModel = koinViewModel(),
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

@Composable
internal fun TodayScreen(
    uiState: TodayUiState,
    onResume: () -> Unit,
    onStartNext: () -> Unit,
    onEmptyWorkout: () -> Unit,
    onOpenPlan: () -> Unit,
    onBuildPlan: () -> Unit,
) {
    Scaffold(topBar = { SpotterTopBar(title = stringResource(Res.string.today_title)) }) { padding ->
        if (uiState.loading) {
            LoadingState(Modifier.padding(padding).fillMaxSize())
            return@Scaffold
        }
        Column(
            modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(Dimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.SectionGap),
        ) {
            val next = uiState.nextDay
            uiState.deloadUntil?.let { until -> DeloadCard(until) }
            val scheduled = uiState.scheduledToday
            if (uiState.scheduledTodayDone) DoneCard()
            when {
                scheduled != null && uiState.scheduledDay != null ->
                    WorkoutCard(uiState.scheduledDay, Res.string.today_scheduled_label, scheduled.notes, onOpenPlan)
                scheduled != null -> FreeWorkoutCard(scheduled.notes)
                next != null -> WorkoutCard(next, Res.string.today_next_label, notes = null, onOpenPlan)
                !uiState.hasPlan -> NoPlanCard(onBuildPlan)
            }
            when {
                uiState.activeSessionId != null ->
                    PrimaryButton(stringResource(Res.string.today_resume_workout), onResume, icon = Icons.Rounded.PlayArrow)
                scheduled != null || next != null ->
                    PrimaryButton(stringResource(Res.string.today_start_workout), onStartNext, icon = Icons.Rounded.PlayArrow)
            }
            if (uiState.activeSessionId == null) {
                SecondaryButton(stringResource(Res.string.today_empty_workout), onEmptyWorkout, icon = Icons.Rounded.Add)
            }
            if (uiState.upcoming.isNotEmpty()) UpcomingCard(uiState.upcoming)
        }
    }
}

@Composable
private fun DeloadCard(until: kotlinx.datetime.LocalDate) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Dimens.CardPadding)) {
            Text(
                stringResource(Res.string.today_deload_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Text(
                stringResource(Res.string.today_deload_body, formatShortDate(until)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}

@Composable
private fun WorkoutCard(day: PlanDay, label: StringResource, notes: String?, onOpenPlan: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Dimens.CardPadding), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Eyebrow(stringResource(label))
            Text(day.name, style = MaterialTheme.typography.headlineSmall)
            notes?.let { Text(it, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic) }
            Text(
                stringResource(Res.string.today_day_meta, PlanGenerator.estimateMinutes(day.exercises), formatExerciseCount(day.exercises.size)),
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
                val hidden = day.exercises.size - PREVIEW_COUNT
                if (hidden > 0) {
                    Text(
                        pluralStringResource(Res.plurals.today_more_exercises, hidden, hidden),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                TextButton(onClick = onOpenPlan) { Text(stringResource(Res.string.today_view_plan)) }
            }
        }
    }
}

/** A free workout planned for today (no plan day). */
@Composable
private fun FreeWorkoutCard(notes: String?) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Dimens.CardPadding), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Eyebrow(stringResource(Res.string.today_scheduled_label))
            Text(stringResource(Res.string.today_free_workout), style = MaterialTheme.typography.headlineSmall)
            Text(notes ?: stringResource(Res.string.today_free_workout_body), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun DoneCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(Dimens.CardPadding), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
            Text(
                stringResource(Res.string.today_scheduled_done),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.padding(start = Spacing.md),
            )
        }
    }
}

/** The rest of the week's planned workouts. */
@Composable
private fun UpcomingCard(upcoming: List<ScheduledWorkout>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Dimens.CardPadding), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            CardTitle(stringResource(Res.string.today_coming_up))
            upcoming.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        formatShortDate(item.date),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(DATE_COLUMN_WIDTH),
                    )
                    Text(
                        item.planDayName ?: stringResource(Res.string.today_free_workout),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun NoPlanCard(onBuildPlan: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Dimens.CardPadding), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(stringResource(Res.string.today_no_plan_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(Res.string.today_no_plan_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onBuildPlan) { Text(stringResource(Res.string.today_no_plan_action)) }
        }
    }
}

private const val PREVIEW_COUNT = 5
private val DATE_COLUMN_WIDTH = 104.dp
