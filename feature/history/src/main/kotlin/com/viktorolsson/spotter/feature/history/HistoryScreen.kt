package com.viktorolsson.spotter.feature.history

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.viktorolsson.spotter.core.model.WeightUnit
import com.viktorolsson.spotter.core.model.WorkoutSession
import com.viktorolsson.spotter.core.model.WorkoutSummary
import com.viktorolsson.spotter.core.ui.component.EmptyState
import com.viktorolsson.spotter.core.ui.formatClock
import com.viktorolsson.spotter.core.ui.formatShortDate
import com.viktorolsson.spotter.core.ui.formatTotal
import com.viktorolsson.spotter.core.ui.localDate
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryRoute(onOpenWorkout: (Long) -> Unit, viewModel: HistoryViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.history_title)) }) }) { padding ->
        when {
            state.loading -> Unit
            state.sessions.isEmpty() -> EmptyState(
                icon = Icons.Rounded.CalendarMonth,
                title = stringResource(R.string.history_empty_title),
                body = stringResource(R.string.history_empty_body),
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    MonthCalendar(
                        month = state.month,
                        sessions = state.sessions,
                        onPrevious = { viewModel.shiftMonth(-1) },
                        onNext = { viewModel.shiftMonth(1) },
                    )
                }
                if (state.monthSessions.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.history_month_empty),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp),
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
 * biggest session (the heat-map).
 */
@Composable
private fun MonthCalendar(
    month: YearMonth,
    sessions: List<WorkoutSession>,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val volumeByDay = sessions
        .filter { YearMonth.from(it.startedAt.localDate()) == month }
        .groupBy { it.startedAt.localDate() }
        .mapValues { (_, list) -> list.sumOf { WorkoutSummary.of(it).volumeKg }.coerceAtLeast(1.0) }
    val maxVolume = volumeByDay.values.maxOrNull() ?: 1.0
    val firstDay = WeekFields.of(Locale.getDefault()).firstDayOfWeek
    val leading = (month.atDay(1).dayOfWeek.value - firstDay.value + 7) % 7
    val today = LocalDate.now()

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrevious) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(R.string.history_prev_month))
                }
                Text(
                    month.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onNext, enabled = month < YearMonth.now()) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(R.string.history_next_month))
                }
            }
            Row {
                (0 until 7).map { DayOfWeek.of((firstDay.value - 1 + it) % 7 + 1) }.forEach { day ->
                    Text(
                        day.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            val cells = List(leading) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
            cells.chunked(7).forEach { week ->
                Row {
                    (0 until 7).forEach { i ->
                        val date = week.getOrNull(i)
                        Box(Modifier.weight(1f).aspectRatio(1f).padding(3.dp), contentAlignment = Alignment.Center) {
                            if (date != null) {
                                val volume = volumeByDay[date]
                                val trained = volume != null
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (trained) {
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f + 0.7f * (volume / maxVolume).toFloat())
                                            } else {
                                                MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
                                            },
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "${date.dayOfMonth}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (date == today) FontWeight.Bold else FontWeight.Normal,
                                        color = if (trained) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionCard(session: WorkoutSession, unit: WeightUnit, onClick: () -> Unit) {
    val summary = WorkoutSummary.of(session)
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    session.planDayName ?: stringResource(R.string.history_workout),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    formatShortDate(session.startedAt.localDate()),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                stringResource(R.string.history_session_meta, formatClock(summary.duration), summary.completedSets, formatTotal(summary.volumeKg, unit)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                session.exercises.joinToString(" · ") { it.exercise.name },
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
