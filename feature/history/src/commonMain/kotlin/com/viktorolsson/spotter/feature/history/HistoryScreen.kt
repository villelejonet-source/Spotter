package com.viktorolsson.spotter.feature.history

import com.viktorolsson.spotter.core.ui.uppercaseLocal
import com.viktorolsson.spotter.core.ui.theme.SpotterTheme
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.viktorolsson.spotter.core.model.WeightUnit
import com.viktorolsson.spotter.core.model.WorkoutSession
import com.viktorolsson.spotter.core.model.WorkoutSummary
import com.viktorolsson.spotter.core.ui.component.EmptyState
import com.viktorolsson.spotter.core.ui.component.LoadingState
import com.viktorolsson.spotter.core.ui.component.SpotterTopBar
import com.viktorolsson.spotter.core.ui.firstDayOfWeek
import com.viktorolsson.spotter.core.ui.formatClock
import com.viktorolsson.spotter.core.ui.formatMonthYear
import com.viktorolsson.spotter.core.ui.formatSetCount
import com.viktorolsson.spotter.core.ui.formatShortDate
import com.viktorolsson.spotter.core.ui.formatTotal
import com.viktorolsson.spotter.core.ui.localDate
import com.viktorolsson.spotter.core.ui.narrowDayName
import com.viktorolsson.spotter.core.ui.theme.Dimens
import com.viktorolsson.spotter.core.ui.theme.Spacing
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.onDay
import kotlinx.datetime.todayIn
import kotlinx.datetime.yearMonth
import kotlin.time.Clock
import com.viktorolsson.spotter.feature.history.resources.Res
import com.viktorolsson.spotter.feature.history.resources.history_day_rest
import com.viktorolsson.spotter.feature.history.resources.history_day_trained
import com.viktorolsson.spotter.feature.history.resources.history_empty_body
import com.viktorolsson.spotter.feature.history.resources.history_empty_title
import com.viktorolsson.spotter.feature.history.resources.history_month_empty
import com.viktorolsson.spotter.feature.history.resources.history_next_month
import com.viktorolsson.spotter.feature.history.resources.history_prev_month
import com.viktorolsson.spotter.feature.history.resources.history_session_meta
import com.viktorolsson.spotter.feature.history.resources.history_title
import com.viktorolsson.spotter.feature.history.resources.history_workout

@Composable
internal fun HistoryRoute(onOpenWorkout: (Long) -> Unit, viewModel: HistoryViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HistoryScreen(
        state = state,
        onOpenWorkout = onOpenWorkout,
        onPreviousMonth = { viewModel.shiftMonth(-1) },
        onNextMonth = { viewModel.shiftMonth(1) },
    )
}

@Composable
internal fun HistoryScreen(
    state: HistoryUiState,
    onOpenWorkout: (Long) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    today: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault()),
) {
    Scaffold(topBar = { SpotterTopBar(title = stringResource(Res.string.history_title)) }) { padding ->
        when {
            state.loading -> LoadingState(Modifier.padding(padding).fillMaxSize())
            !state.hasAnySession -> EmptyState(
                icon = Icons.Rounded.CalendarMonth,
                title = stringResource(Res.string.history_empty_title),
                body = stringResource(Res.string.history_empty_body),
                modifier = Modifier.padding(padding).fillMaxSize(),
            )
            else -> LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(Dimens.ScreenPadding),
                verticalArrangement = Arrangement.spacedBy(Dimens.ListGap),
            ) {
                item {
                    MonthCalendar(
                        month = state.month,
                        volumeByDay = state.volumeByDay,
                        unit = state.unit,
                        today = today,
                        onPrevious = onPreviousMonth,
                        onNext = onNextMonth,
                    )
                }
                if (state.monthSessions.isEmpty()) {
                    item {
                        Text(
                            stringResource(Res.string.history_month_empty),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(Spacing.sm),
                        )
                    }
                }
                items(state.monthSessions, key = { it.id }) { session ->
                    SessionCard(session, state.unit) { onOpenWorkout(session.id) }
                }
            }
        }
    }
}

/**
 * Month grid; each training day is tinted by its volume relative to the month's
 * biggest session (the heat-map), from the primary container up to primary.
 */
@Composable
private fun MonthCalendar(
    month: YearMonth,
    volumeByDay: Map<LocalDate, Double>,
    unit: WeightUnit,
    today: LocalDate,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val maxVolume = remember(volumeByDay) { volumeByDay.values.maxOrNull() ?: 1.0 }
    val firstDay = firstDayOfWeek()
    val cells = remember(month, firstDay) {
        val leading = (month.firstDay.dayOfWeek.isoDayNumber - firstDay.isoDayNumber + 7) % 7
        (List(leading) { null } + (1..month.numberOfDays).map { month.onDay(it) }).chunked(7)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(Dimens.Hairline, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrevious) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(Res.string.history_prev_month))
                }
                Text(
                    formatMonthYear(month),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onNext, enabled = month < today.yearMonth) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(Res.string.history_next_month))
                }
            }
            Row {
                (0 until 7).map { DayOfWeek((firstDay.isoDayNumber - 1 + it) % 7 + 1) }.forEach { day ->
                    Text(
                        narrowDayName(day),
                        style = SpotterTheme.type.label,
                        color = SpotterTheme.colors.faint,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            cells.forEach { week ->
                Row {
                    (0 until 7).forEach { i ->
                        val date = week.getOrNull(i)
                        Box(Modifier.weight(1f).aspectRatio(1f).padding(Spacing.xs / 2), contentAlignment = Alignment.Center) {
                            if (date != null) DayCell(date, volumeByDay[date], maxVolume, unit, isToday = date == today)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, volumeKg: Double?, maxVolume: Double, unit: WeightUnit, isToday: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val trained = volumeKg != null
    // Heat steps run from the container to primary itself, so text stays readable at every step.
    val intensity = if (volumeKg != null) (volumeKg / maxVolume).toFloat().coerceIn(0f, 1f) else 0f
    val background = if (trained) lerp(scheme.primaryContainer, scheme.primary, intensity) else scheme.surfaceContainerHighest
    val foreground = when {
        !trained -> scheme.onSurfaceVariant
        intensity > 0.5f -> scheme.onPrimary
        else -> scheme.onPrimaryContainer
    }
    val label = formatShortDate(date)
    val description = if (volumeKg != null) {
        stringResource(Res.string.history_day_trained, label, formatTotal(volumeKg, unit))
    } else {
        stringResource(Res.string.history_day_rest, label)
    }
    Box(
        Modifier
            .fillMaxSize()
            .clip(MaterialTheme.shapes.small)
            .background(background)
            .then(if (isToday) Modifier.border(TODAY_RING, scheme.primary, MaterialTheme.shapes.small) else Modifier)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            date.day.toString(),
            style = SpotterTheme.type.numberSmall,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            color = foreground,
        )
    }
}

@Composable
private fun SessionCard(session: WorkoutSession, unit: WeightUnit, onClick: () -> Unit) {
    val summary = remember(session) { WorkoutSummary.of(session) }
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(Dimens.Hairline, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = Dimens.CardPadding, vertical = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    session.planDayName ?: stringResource(Res.string.history_workout),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    formatShortDate(session.startedAt.localDate()).uppercaseLocal(),
                    style = SpotterTheme.type.label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                stringResource(
                    Res.string.history_session_meta,
                    formatClock(summary.duration),
                    formatSetCount(summary.completedSets),
                    formatTotal(summary.volumeKg, unit),
                ),
                style = SpotterTheme.type.numberSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                session.exercises.joinToString(" · ") { it.exercise.name },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private val TODAY_RING = 1.5.dp
