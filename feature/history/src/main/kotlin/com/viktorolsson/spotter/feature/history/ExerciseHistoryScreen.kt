package com.viktorolsson.spotter.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.viktorolsson.spotter.core.model.SetType
import com.viktorolsson.spotter.core.model.fromKg
import com.viktorolsson.spotter.core.model.isTimed
import com.viktorolsson.spotter.core.ui.component.ChartPoint
import com.viktorolsson.spotter.core.ui.component.EmptyState
import com.viktorolsson.spotter.core.ui.component.LoadingState
import com.viktorolsson.spotter.core.ui.component.RecommendationCard
import com.viktorolsson.spotter.core.ui.component.SectionHeader
import com.viktorolsson.spotter.core.ui.component.SpotterTopBar
import com.viktorolsson.spotter.core.ui.component.StatTile
import com.viktorolsson.spotter.core.ui.component.TrendChart
import com.viktorolsson.spotter.core.ui.formatOneRepMax
import com.viktorolsson.spotter.core.ui.formatSet
import com.viktorolsson.spotter.core.ui.formatShortDate
import com.viktorolsson.spotter.core.ui.formatTotal
import com.viktorolsson.spotter.core.ui.formatWeight
import com.viktorolsson.spotter.core.ui.localDate
import com.viktorolsson.spotter.core.ui.theme.Dimens
import com.viktorolsson.spotter.core.ui.theme.Spacing

@Composable
internal fun ExerciseHistoryRoute(onBack: () -> Unit, viewModel: ExerciseHistoryViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ExerciseHistoryScreen(
        state = state,
        onBack = onBack,
        onMetric = viewModel::setMetric,
        onApply = viewModel::apply,
        onDismiss = viewModel::dismiss,
    )
}

@Composable
internal fun ExerciseHistoryScreen(
    state: ExerciseHistoryUiState,
    onBack: () -> Unit,
    onMetric: (ExerciseMetric) -> Unit,
    onApply: (Long) -> Unit,
    onDismiss: (Long) -> Unit,
) {
    val unit = state.unit
    val timed = state.exercise?.isTimed == true
    Scaffold(topBar = { SpotterTopBar(title = state.exercise?.name.orEmpty(), onBack = onBack) }) { padding ->
        val logs = state.logs
        when {
            state.loading -> LoadingState(Modifier.padding(padding).fillMaxSize())
            logs.isEmpty() -> EmptyState(
                icon = Icons.Rounded.Insights,
                title = stringResource(R.string.exercise_none_title),
                body = stringResource(R.string.exercise_none),
                modifier = Modifier.padding(padding).fillMaxSize(),
            )
            else -> LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(Dimens.ScreenPadding),
                verticalArrangement = Arrangement.spacedBy(Dimens.ListGap),
            ) {
                items(state.recommendations, key = { "rec-${it.id}" }) { rec ->
                    RecommendationCard(
                        recommendation = rec,
                        unit = unit,
                        onApply = { onApply(rec.id) },
                        onDismiss = { onDismiss(rec.id) },
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        listOf(
                            ExerciseMetric.ONE_REP_MAX to R.string.exercise_metric_1rm,
                            ExerciseMetric.BEST_SET to R.string.exercise_metric_best,
                            ExerciseMetric.VOLUME to R.string.exercise_metric_volume,
                        ).forEach { (metric, label) ->
                            FilterChip(selected = state.metric == metric, onClick = { onMetric(metric) }, label = { Text(stringResource(label)) })
                        }
                    }
                }
                item {
                    val points = remember(logs, state.metric, unit) {
                        logs.mapNotNull { log ->
                            val value = when (state.metric) {
                                ExerciseMetric.ONE_REP_MAX -> log.estimatedOneRepMax
                                ExerciseMetric.BEST_SET -> log.bestSet?.weightKg
                                ExerciseMetric.VOLUME -> log.volumeKg.takeIf { it > 0 }
                            }
                            value?.let { ChartPoint(log.date.localDate(), unit.fromKg(it)) }
                        }
                    }
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        TrendChart(
                            primary = points,
                            evenlySpaced = true,
                            formatValue = { "%.0f".format(it) },
                            emptyText = stringResource(R.string.exercise_chart_empty),
                            modifier = Modifier.padding(Spacing.md),
                        )
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        StatTile(
                            stringResource(R.string.exercise_best_1rm),
                            state.bestOneRepMaxKg?.let { formatOneRepMax(it, unit) } ?: stringResource(R.string.exercise_no_value),
                            Modifier.weight(1f),
                        )
                        StatTile(
                            stringResource(R.string.exercise_heaviest),
                            state.heaviestKg?.let { formatWeight(it, unit) } ?: stringResource(R.string.exercise_no_value),
                            Modifier.weight(1f),
                        )
                        StatTile(stringResource(R.string.exercise_best_volume), formatTotal(state.bestVolumeKg, unit), Modifier.weight(1f))
                    }
                }
                if (state.repRecords.isNotEmpty()) {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                            Column(Modifier.padding(Dimens.CardPadding), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                SectionHeader(stringResource(R.string.exercise_rep_records))
                                state.repRecords.take(REP_RECORD_ROWS).forEach { (weight, reps) ->
                                    Row {
                                        Text(formatWeight(weight, unit), modifier = Modifier.weight(1f))
                                        Text(
                                            if (timed) stringResource(com.viktorolsson.spotter.core.ui.R.string.seconds_short, reps)
                                            else pluralStringResource(com.viktorolsson.spotter.core.ui.R.plurals.reps_count, reps, reps),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                item { SectionHeader(stringResource(R.string.exercise_log)) }
                items(logs.asReversed(), key = { it.sessionId }) { log ->
                    Column {
                        Text(formatShortDate(log.date.localDate()), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text(
                            log.sets.filter { it.setType != SetType.WARMUP }.map { formatSet(it.weightKg, it.reps, unit, timed) }.joinToString("  ·  "),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

private const val REP_RECORD_ROWS = 8
