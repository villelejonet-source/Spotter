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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.viktorolsson.spotter.core.model.SetType
import com.viktorolsson.spotter.core.model.format
import com.viktorolsson.spotter.core.model.fromKg
import com.viktorolsson.spotter.core.model.isTimed
import com.viktorolsson.spotter.core.model.label
import com.viktorolsson.spotter.core.ui.component.ChartPoint
import com.viktorolsson.spotter.core.ui.component.RecommendationCard
import com.viktorolsson.spotter.core.ui.component.TrendChart
import com.viktorolsson.spotter.core.ui.formatSet
import com.viktorolsson.spotter.core.ui.formatShortDate
import com.viktorolsson.spotter.core.ui.formatTotal
import com.viktorolsson.spotter.core.ui.localDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExerciseHistoryRoute(onBack: () -> Unit, viewModel: ExerciseHistoryViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val unit = state.unit
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.exercise?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.history_back)) }
                },
            )
        },
    ) { padding ->
        val logs = state.logs
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (logs.isEmpty()) {
                item { Text(stringResource(R.string.exercise_none), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                return@LazyColumn
            }
            items(state.recommendations, key = { "rec-${it.id}" }) { rec ->
                RecommendationCard(
                    recommendation = rec,
                    unit = unit,
                    onApply = { viewModel.apply(rec.id) },
                    onDismiss = { viewModel.dismiss(rec.id) },
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        ExerciseMetric.ONE_REP_MAX to R.string.exercise_metric_1rm,
                        ExerciseMetric.BEST_SET to R.string.exercise_metric_best,
                        ExerciseMetric.VOLUME to R.string.exercise_metric_volume,
                    ).forEach { (metric, label) ->
                        FilterChip(selected = state.metric == metric, onClick = { viewModel.setMetric(metric) }, label = { Text(stringResource(label)) })
                    }
                }
            }
            item {
                val points = logs.mapNotNull { log ->
                    val value = when (state.metric) {
                        ExerciseMetric.ONE_REP_MAX -> log.estimatedOneRepMax
                        ExerciseMetric.BEST_SET -> log.bestSet?.weightKg
                        ExerciseMetric.VOLUME -> log.volumeKg.takeIf { it > 0 }
                    }
                    value?.let { ChartPoint(log.date.localDate(), unit.fromKg(it)) }
                }
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    TrendChart(
                        primary = points,
                        evenlySpaced = true,
                        formatValue = { "%.0f".format(it) },
                        emptyText = stringResource(R.string.exercise_chart_empty),
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
            item {
                val best1rm = logs.mapNotNull { it.estimatedOneRepMax }.maxOrNull()
                val heaviest = logs.mapNotNull { it.bestSet?.weightKg }.maxOrNull()
                val bestVolume = logs.maxOf { it.volumeKg }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stat(
                        stringResource(R.string.exercise_best_1rm),
                        best1rm?.let { "${unit.format(it).substringBefore('.')} ${unit.label}" } ?: "—",
                        Modifier.weight(1f),
                    )
                    Stat(stringResource(R.string.exercise_heaviest), heaviest?.let { "${unit.format(it)} ${unit.label}" } ?: "—", Modifier.weight(1f))
                    Stat(stringResource(R.string.exercise_best_volume), formatTotal(bestVolume, unit), Modifier.weight(1f))
                }
            }
            if (state.repRecords.isNotEmpty()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.exercise_rep_records), style = MaterialTheme.typography.titleSmall)
                            state.repRecords.take(8).forEach { (weight, reps) ->
                                Row {
                                    Text("${unit.format(weight)} ${unit.label}", modifier = Modifier.weight(1f))
                                    Text(stringResource(if (state.exercise?.isTimed == true) R.string.exercise_hold_record_row else R.string.exercise_rep_record_row, reps))
                                }
                            }
                        }
                    }
                }
            }
            item { Text(stringResource(R.string.exercise_log), style = MaterialTheme.typography.titleSmall) }
            items(logs.reversed(), key = { it.sessionId }) { log ->
                Column {
                    Text(formatShortDate(log.date.localDate()), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(
                        log.sets.filter { it.setType != SetType.WARMUP }.joinToString("  ·  ") { formatSet(it.weightKg, it.reps, unit, state.exercise?.isTimed == true) },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
