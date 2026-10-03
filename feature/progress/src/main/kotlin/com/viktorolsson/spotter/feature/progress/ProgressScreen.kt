package com.viktorolsson.spotter.feature.progress

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.viktorolsson.spotter.core.model.BodyWeightPoint
import com.viktorolsson.spotter.core.model.MuscleVolume
import com.viktorolsson.spotter.core.model.VolumeStatus
import com.viktorolsson.spotter.core.model.WeightUnit
import com.viktorolsson.spotter.core.model.format
import com.viktorolsson.spotter.core.model.fromKg
import com.viktorolsson.spotter.core.model.label
import com.viktorolsson.spotter.core.ui.component.ChartPoint
import com.viktorolsson.spotter.core.ui.component.EmptyState
import com.viktorolsson.spotter.core.ui.component.RecommendationCard
import com.viktorolsson.spotter.core.ui.component.TrendChart
import com.viktorolsson.spotter.core.ui.formatRecordValue
import com.viktorolsson.spotter.core.ui.formatShortDate
import com.viktorolsson.spotter.core.ui.labelRes
import com.viktorolsson.spotter.core.ui.localDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProgressRoute(onOpenExercise: (String) -> Unit, viewModel: ProgressViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var loggingWeight by rememberSaveable { mutableStateOf(false) }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.progress_title)) }) }) { padding ->
        if (state.loading) return@Scaffold
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.recommendations.isNotEmpty()) {
                item { Text(stringResource(R.string.progress_recommendations), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() }) }
                items(state.recommendations, key = { "rec-${it.id}" }) { rec ->
                    RecommendationCard(
                        recommendation = rec,
                        unit = state.unit,
                        onApply = { viewModel.apply(rec.id) },
                        onDismiss = { viewModel.dismiss(rec.id) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
            if (state.isEmpty) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.Insights,
                        title = stringResource(R.string.progress_empty_title),
                        body = stringResource(R.string.progress_empty_body),
                        modifier = Modifier.height(320.dp),
                    )
                }
            } else {
                item { RecordsCard(state) }
                item { BalanceCard(state.volume) }
            }
            item { BodyWeightCard(state.bodyWeight, state.unit) { loggingWeight = true } }
            if (state.lifts.isNotEmpty()) {
                item { Text(stringResource(R.string.progress_lifts), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() }) }
                items(state.lifts, key = { it.exercise.id }) { lift ->
                    Column(
                        Modifier.fillMaxWidth().clickable { onOpenExercise(lift.exercise.id) }.padding(vertical = 8.dp),
                    ) {
                        Text(lift.exercise.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.progress_lift_meta, lift.sessionCount, formatShortDate(lift.lastDone.localDate())),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
    if (loggingWeight) {
        LogWeightDialog(
            unit = state.unit,
            initial = state.bodyWeight.lastOrNull()?.weightKg?.let(state.unit::format).orEmpty(),
            onSave = { viewModel.logWeight(it); loggingWeight = false },
            onDismiss = { loggingWeight = false },
        )
    }
}

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
private fun RecordsCard(state: ProgressUiState) = SectionCard {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.EmojiEvents, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
        Text(stringResource(R.string.progress_records), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp).semantics { heading() })
    }
    if (state.records.isEmpty()) {
        Text(stringResource(R.string.progress_records_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    state.records.forEach { record ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(record.exerciseName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${stringResource(record.type.labelRes)} · ${formatShortDate(record.achievedAt.localDate())}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(formatRecordValue(record, state.unit), style = MaterialTheme.typography.titleSmall)
        }
    }
}

/** Sets per muscle group against the goal's weekly range; under-trained groups stand out. */
@Composable
private fun BalanceCard(volume: List<MuscleVolume>) = SectionCard {
    Text(stringResource(R.string.progress_balance), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
    volume.firstOrNull()?.let {
        Text(
            stringResource(R.string.progress_balance_hint, it.targetLow, it.targetHigh),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    volume.forEach { group ->
        val color = when (group.status) {
            VolumeStatus.UNDER -> MaterialTheme.colorScheme.error
            VolumeStatus.IN_RANGE -> MaterialTheme.colorScheme.primary
            VolumeStatus.OVER -> MaterialTheme.colorScheme.tertiary
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(group.group.labelRes), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(92.dp))
            LinearProgressIndicator(
                progress = { (group.sets / group.targetHigh).toFloat().coerceIn(0f, 1f) },
                color = color,
                strokeCap = StrokeCap.Round,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.progress_balance_row, formatSets(group.sets), group.targetLow, group.targetHigh),
                style = MaterialTheme.typography.labelMedium,
                color = color,
                modifier = Modifier.width(72.dp),
            )
        }
    }
}

private fun formatSets(sets: Double) = if (sets % 1.0 == 0.0) "${sets.toInt()}" else "%.1f".format(sets)

@Composable
private fun BodyWeightCard(points: List<BodyWeightPoint>, unit: WeightUnit, onLog: () -> Unit) = SectionCard {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.progress_body_weight), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            points.lastOrNull()?.let { latest ->
                Text("${unit.format(latest.weightKg)} ${unit.label}", style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(R.string.progress_weight_average, "${unit.format(latest.sevenDayAverageKg).take(5)} ${unit.label}"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
        FilledTonalButton(onClick = onLog) { Text(stringResource(R.string.progress_log_weight)) }
    }
    TrendChart(
        primary = points.map { ChartPoint(it.date, unit.fromKg(it.weightKg)) },
        secondary = points.map { ChartPoint(it.date, unit.fromKg(it.sevenDayAverageKg)) },
        formatValue = { "%.0f".format(it) },
        emptyText = stringResource(R.string.progress_weight_empty),
    )
}

@Composable
private fun LogWeightDialog(unit: WeightUnit, initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.progress_log_weight)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { v -> text = v.filter { it.isDigit() || it == '.' || it == ',' }.take(6) },
                label = { Text(stringResource(R.string.progress_weight_label, unit.label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
