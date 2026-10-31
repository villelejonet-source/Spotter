package com.viktorolsson.spotter.feature.today

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
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
import com.viktorolsson.spotter.core.model.formatChange
import com.viktorolsson.spotter.core.model.isTimed
import com.viktorolsson.spotter.core.ui.component.ContentCard
import com.viktorolsson.spotter.core.ui.component.DashboardCard
import com.viktorolsson.spotter.core.ui.component.DashboardStat
import com.viktorolsson.spotter.core.ui.component.Delta
import com.viktorolsson.spotter.core.ui.component.Eyebrow
import com.viktorolsson.spotter.core.ui.component.HeatmapLegend
import com.viktorolsson.spotter.core.ui.component.LoadingState
import com.viktorolsson.spotter.core.ui.component.PrimaryButton
import com.viktorolsson.spotter.core.ui.component.ProgressRing
import com.viktorolsson.spotter.core.ui.component.SecondaryButton
import com.viktorolsson.spotter.core.ui.component.SpotterTopBar
import com.viktorolsson.spotter.core.ui.component.TrainingHeatmap
import com.viktorolsson.spotter.core.ui.formatExerciseCount
import com.viktorolsson.spotter.core.ui.formatCount
import com.viktorolsson.spotter.core.ui.formatShortDate
import com.viktorolsson.spotter.core.ui.formatTarget
import com.viktorolsson.spotter.core.ui.formatTotal
import com.viktorolsson.spotter.core.ui.theme.Dimens
import com.viktorolsson.spotter.core.ui.theme.Spacing
import com.viktorolsson.spotter.core.ui.theme.SpotterTheme
import com.viktorolsson.spotter.feature.today.resources.Res
import com.viktorolsson.spotter.feature.today.resources.today_heatmap_more
import com.viktorolsson.spotter.feature.today.resources.today_heatmap_less
import com.viktorolsson.spotter.feature.today.resources.today_heatmap_summary
import com.viktorolsson.spotter.feature.today.resources.today_heatmap_title
import com.viktorolsson.spotter.feature.today.resources.today_stat_total_hint
import com.viktorolsson.spotter.feature.today.resources.today_stat_total
import com.viktorolsson.spotter.feature.today.resources.today_stat_streak_hint
import com.viktorolsson.spotter.feature.today.resources.today_stat_streak
import com.viktorolsson.spotter.feature.today.resources.today_stat_volume_hint
import com.viktorolsson.spotter.feature.today.resources.today_stat_volume
import com.viktorolsson.spotter.feature.today.resources.today_stat_workouts_hint
import com.viktorolsson.spotter.feature.today.resources.today_stat_week_goal_hint
import com.viktorolsson.spotter.feature.today.resources.today_stat_week
import com.viktorolsson.spotter.feature.today.resources.today_ring_description
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
    val next = uiState.nextDay
    val scheduled = uiState.scheduledToday
    Scaffold(
        topBar = {
            SpotterTopBar(
                title = stringResource(Res.string.today_title),
                subtitle = uiState.today?.let { formatShortDate(it) },
            )
        },
        // The main action sits at the bottom, in reach of the thumb, and stays put while the
        // dashboard scrolls under it.
        bottomBar = {
            if (!uiState.loading) {
                StartBar(
                    resume = uiState.activeSessionId != null,
                    hasNext = scheduled != null || next != null,
                    onResume = onResume,
                    onStartNext = onStartNext,
                    onEmptyWorkout = onEmptyWorkout,
                )
            }
        },
    ) { padding ->
        if (uiState.loading) {
            LoadingState(Modifier.padding(padding).fillMaxSize())
            return@Scaffold
        }
        Column(
            modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(Dimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.SectionGap),
        ) {
            val dashboard = uiState.dashboard
            uiState.deloadUntil?.let { until -> DeloadCard(until) }
            if (uiState.scheduledTodayDone) DoneCard()
            when {
                scheduled != null && uiState.scheduledDay != null ->
                    WorkoutCard(uiState.scheduledDay, Res.string.today_scheduled_label, scheduled.notes, dashboard, onOpenPlan)
                scheduled != null -> FreeWorkoutCard(scheduled.notes, dashboard)
                next != null -> WorkoutCard(next, Res.string.today_next_label, notes = null, dashboard, onOpenPlan)
                !uiState.hasPlan -> NoPlanCard(onBuildPlan)
            }
            if (dashboard != null) {
                Stats(dashboard)
                TrainingDays(dashboard)
            }
            if (uiState.upcoming.isNotEmpty()) UpcomingCard(uiState.upcoming)
        }
    }
}

/** Start (or resume) and an empty workout, pinned above the tab bar. */
@Composable
private fun StartBar(
    resume: Boolean,
    hasNext: Boolean,
    onResume: () -> Unit,
    onStartNext: () -> Unit,
    onEmptyWorkout: () -> Unit,
) {
    val hairline = MaterialTheme.colorScheme.outlineVariant
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .drawBehind { drawLine(hairline, Offset.Zero, Offset(size.width, 0f), Dimens.Hairline.toPx()) }
            .padding(horizontal = Dimens.ScreenPadding, vertical = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val empty = stringResource(Res.string.today_empty_workout)
        when {
            resume -> PrimaryButton(stringResource(Res.string.today_resume_workout), onResume, Modifier.weight(1f), icon = Icons.Rounded.PlayArrow)
            hasNext -> {
                PrimaryButton(stringResource(Res.string.today_start_workout), onStartNext, Modifier.weight(1f), icon = Icons.Rounded.PlayArrow)
                OutlinedIconButton(
                    onClick = onEmptyWorkout,
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(Dimens.Hairline, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.size(Dimens.ButtonHeight),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = empty)
                }
            }
            else -> SecondaryButton(empty, onEmptyWorkout, Modifier.weight(1f), icon = Icons.Rounded.Add)
        }
    }
}

/** This week, volume, streak and all-time workouts, two to a row. */
@Composable
internal fun Stats(dashboard: Dashboard) {
    val weeks = dashboard.statWeeks
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.ListGap)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.ListGap), modifier = Modifier.height(IntrinsicSize.Max)) {
            val goal = dashboard.weeklyGoal
            val done = dashboard.thisWeek.workouts
            DashboardStat(
                label = stringResource(Res.string.today_stat_week),
                value = if (goal != null) "$done / $goal" else done.toString(),
                hint = if (goal != null) stringResource(Res.string.today_stat_week_goal_hint) else pluralStringResource(Res.plurals.today_stat_workouts_hint, done),
                spark = weeks.map { it.workouts.toDouble() },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            val change = dashboard.volumeChange
            DashboardStat(
                label = stringResource(Res.string.today_stat_volume),
                value = formatTotal(dashboard.fourWeekVolumeKg, dashboard.unit),
                hint = stringResource(Res.string.today_stat_volume_hint),
                delta = change?.let { { Delta(formatChange(it), improving = it >= 0) } },
                spark = weeks.map { it.volumeKg },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.ListGap), modifier = Modifier.height(IntrinsicSize.Max)) {
            val streak = dashboard.streak
            DashboardStat(
                label = stringResource(Res.string.today_stat_streak),
                value = if (streak >= Dashboard.HEATMAP_WEEKS) "${Dashboard.HEATMAP_WEEKS}+" else streak.toString(),
                hint = pluralStringResource(Res.plurals.today_stat_streak_hint, streak),
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            DashboardStat(
                label = stringResource(Res.string.today_stat_total),
                value = formatCount(dashboard.totalWorkouts),
                hint = stringResource(Res.string.today_stat_total_hint),
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}

@Composable
internal fun TrainingDays(dashboard: Dashboard) {
    val count = dashboard.weeks.sumOf { it.workouts }
    val summary = pluralStringResource(Res.plurals.today_heatmap_summary, count, count, Dashboard.HEATMAP_WEEKS)
    DashboardCard(stringResource(Res.string.today_heatmap_title)) {
        TrainingHeatmap(dashboard.workoutsByDay, dashboard.today, Dashboard.HEATMAP_WEEKS, description = summary)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            HeatmapLegend(stringResource(Res.string.today_heatmap_less), stringResource(Res.string.today_heatmap_more))
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
private fun WorkoutCard(day: PlanDay, label: StringResource, notes: String?, dashboard: Dashboard?, onOpenPlan: () -> Unit) {
    ContentCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Eyebrow(stringResource(label))
                Text(day.name, style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(Res.string.today_day_meta, PlanGenerator.estimateMinutes(day.exercises), formatExerciseCount(day.exercises.size)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            WeekRing(dashboard)
        }
        notes?.let { Text(it, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic) }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = Spacing.xs))
        day.exercises.take(PREVIEW_COUNT).forEach { ex ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = EXERCISE_ROW_HEIGHT)) {
                Text(
                    ex.exercise.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    formatTarget(ex.sets, ex.repMin, ex.repMax, ex.exercise.isTimed),
                    style = SpotterTheme.type.numberSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            val hidden = day.exercises.size - PREVIEW_COUNT
            if (hidden > 0) {
                Text(
                    pluralStringResource(Res.plurals.today_more_exercises, hidden, hidden),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            TextButton(onClick = onOpenPlan) { Text(stringResource(Res.string.today_view_plan)) }
        }
    }
}

/** This week's workouts against the weekly goal, when the profile has one. */
@Composable
private fun WeekRing(dashboard: Dashboard?) {
    val goal = dashboard?.weeklyGoal ?: return
    val done = dashboard.thisWeek.workouts
    ProgressRing(done, goal, stringResource(Res.string.today_ring_description, done, goal), Modifier.padding(start = Spacing.md))
}

/** A free workout planned for today (no plan day). */
@Composable
private fun FreeWorkoutCard(notes: String?, dashboard: Dashboard?) {
    ContentCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Eyebrow(stringResource(Res.string.today_scheduled_label))
                Text(stringResource(Res.string.today_free_workout), style = MaterialTheme.typography.headlineSmall)
            }
            WeekRing(dashboard)
        }
        Text(
            notes ?: stringResource(Res.string.today_free_workout_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
    DashboardCard(stringResource(Res.string.today_coming_up), Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            upcoming.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        formatShortDate(item.date),
                        style = SpotterTheme.type.numberSmall,
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
    ContentCard(Modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.today_no_plan_title), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(Res.string.today_no_plan_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PrimaryButton(stringResource(Res.string.today_no_plan_action), onBuildPlan)
    }
}

private const val PREVIEW_COUNT = 5
private val EXERCISE_ROW_HEIGHT = 28.dp
private val DATE_COLUMN_WIDTH = 104.dp
