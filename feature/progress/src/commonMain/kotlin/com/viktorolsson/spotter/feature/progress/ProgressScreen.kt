package com.viktorolsson.spotter.feature.progress

import com.viktorolsson.spotter.core.ui.theme.SpotterTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.viktorolsson.spotter.core.model.BodyWeightPoint
import com.viktorolsson.spotter.core.model.MuscleVolume
import com.viktorolsson.spotter.core.model.VolumeStatus
import com.viktorolsson.spotter.core.model.WeightUnit
import com.viktorolsson.spotter.core.model.format
import com.viktorolsson.spotter.core.model.fromKg
import com.viktorolsson.spotter.core.model.label
import com.viktorolsson.spotter.core.ui.localFormat
import com.viktorolsson.spotter.core.ui.component.CardTitle
import com.viktorolsson.spotter.core.ui.component.ChartPoint
import com.viktorolsson.spotter.core.ui.component.ContentCard
import com.viktorolsson.spotter.core.ui.component.EmptyState
import com.viktorolsson.spotter.core.ui.component.LoadingState
import com.viktorolsson.spotter.core.ui.component.RecommendationCard
import com.viktorolsson.spotter.core.ui.component.RecordsCard
import com.viktorolsson.spotter.core.ui.component.SectionHeader
import com.viktorolsson.spotter.core.ui.component.SpotterTopBar
import com.viktorolsson.spotter.core.ui.component.TrendChart
import com.viktorolsson.spotter.core.ui.formatSessionCount
import com.viktorolsson.spotter.core.ui.formatShortDate
import com.viktorolsson.spotter.core.ui.formatWeight
import com.viktorolsson.spotter.core.ui.formatWeightOneDecimal
import com.viktorolsson.spotter.core.ui.labelRes
import com.viktorolsson.spotter.core.ui.localDate
import com.viktorolsson.spotter.core.ui.theme.Dimens
import com.viktorolsson.spotter.core.ui.theme.Spacing
import com.viktorolsson.spotter.core.ui.resources.Res as CoreRes
import com.viktorolsson.spotter.core.ui.resources.action_cancel
import com.viktorolsson.spotter.core.ui.resources.action_save
import org.jetbrains.compose.resources.stringResource
import com.viktorolsson.spotter.feature.progress.resources.Res
import com.viktorolsson.spotter.feature.progress.resources.progress_balance
import com.viktorolsson.spotter.feature.progress.resources.progress_balance_hint
import com.viktorolsson.spotter.feature.progress.resources.progress_balance_row
import com.viktorolsson.spotter.feature.progress.resources.progress_body_weight
import com.viktorolsson.spotter.feature.progress.resources.progress_empty_body
import com.viktorolsson.spotter.feature.progress.resources.progress_empty_title
import com.viktorolsson.spotter.feature.progress.resources.progress_lift_meta
import com.viktorolsson.spotter.feature.progress.resources.progress_lifts
import com.viktorolsson.spotter.feature.progress.resources.progress_log_weight
import com.viktorolsson.spotter.feature.progress.resources.progress_open_exercise
import com.viktorolsson.spotter.feature.progress.resources.progress_recommendations
import com.viktorolsson.spotter.feature.progress.resources.progress_records
import com.viktorolsson.spotter.feature.progress.resources.progress_records_empty
import com.viktorolsson.spotter.feature.progress.resources.progress_title
import com.viktorolsson.spotter.feature.progress.resources.progress_weight_average
import com.viktorolsson.spotter.feature.progress.resources.progress_weight_empty
import com.viktorolsson.spotter.feature.progress.resources.progress_weight_invalid
import com.viktorolsson.spotter.feature.progress.resources.progress_weight_label

@Composable
internal fun ProgressRoute(onOpenExercise: (String) -> Unit, viewModel: ProgressViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ProgressScreen(
        state = state,
        onOpenExercise = onOpenExercise,
        onApply = viewModel::apply,
        onDismiss = viewModel::dismiss,
        onLogWeight = viewModel::logWeight,
    )
}

@Composable
internal fun ProgressScreen(
    state: ProgressUiState,
    onOpenExercise: (String) -> Unit,
    onApply: (Long) -> Unit,
    onDismiss: (Long) -> Unit,
    /** Saves the typed weight; false when it isn't a weight in the accepted range. */
    onLogWeight: (String) -> Boolean,
) {
    var loggingWeight by rememberSaveable { mutableStateOf(false) }
    Scaffold(topBar = { SpotterTopBar(title = stringResource(Res.string.progress_title)) }) { padding ->
        if (state.loading) {
            LoadingState(Modifier.padding(padding).fillMaxSize())
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(Dimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.SectionGap),
        ) {
            if (state.recommendations.isNotEmpty()) {
                item { SectionHeader(stringResource(Res.string.progress_recommendations)) }
                items(state.recommendations, key = { "rec-${it.id}" }) { rec ->
                    RecommendationCard(
                        recommendation = rec,
                        unit = state.unit,
                        onApply = { onApply(rec.id) },
                        onDismiss = { onDismiss(rec.id) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
            if (state.isEmpty) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.Insights,
                        title = stringResource(Res.string.progress_empty_title),
                        body = stringResource(Res.string.progress_empty_body),
                    )
                }
            } else {
                item {
                    RecordsCard(
                        title = stringResource(Res.string.progress_records),
                        records = state.records,
                        unit = state.unit,
                        celebrate = false,
                        emptyText = stringResource(Res.string.progress_records_empty),
                    )
                }
                item { BalanceCard(state.volume) }
            }
            item { BodyWeightCard(state.bodyWeight, state.unit) { loggingWeight = true } }
            if (state.lifts.isNotEmpty()) {
                item { SectionHeader(stringResource(Res.string.progress_lifts)) }
                items(state.lifts, key = { it.exercise.id }) { lift ->
                    val openLabel = stringResource(Res.string.progress_open_exercise)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = Dimens.TouchTarget)
                            .clickable(onClickLabel = openLabel, role = Role.Button) { onOpenExercise(lift.exercise.id) }
                            .padding(vertical = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(lift.exercise.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(Res.string.progress_lift_meta, formatSessionCount(lift.sessionCount), formatShortDate(lift.lastDone.localDate())),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = SpotterTheme.colors.faint)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
    if (loggingWeight) {
        LogWeightDialog(
            unit = state.unit,
            initial = state.bodyWeight.lastOrNull()?.weightKg?.let(state.unit::format).orEmpty(),
            onSave = { text -> onLogWeight(text).also { saved -> if (saved) loggingWeight = false } },
            onDismiss = { loggingWeight = false },
        )
    }
}

/** Sets per muscle group against the goal's weekly range; under-trained groups stand out. */
@Composable
private fun BalanceCard(volume: List<MuscleVolume>) = ContentCard {
    CardTitle(stringResource(Res.string.progress_balance))
    volume.firstOrNull()?.let {
        Text(
            stringResource(Res.string.progress_balance_hint, it.targetLow, it.targetHigh),
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
        // Two lines, name and count over a full-width bar, so long names and large text fit.
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(stringResource(group.group.labelRes), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(
                    stringResource(Res.string.progress_balance_row, formatSets(group.sets), group.targetLow, group.targetHigh),
                    style = MaterialTheme.typography.labelMedium,
                    color = color,
                )
            }
            BalanceBar(group.sets, group.targetLow, group.targetHigh, color, Modifier.fillMaxWidth())
        }
    }
}

/**
 * Sets against the weekly range, as on the website: the target range is a tinted band on the
 * track, and the bar runs past it when a group is over.
 */
@Composable
private fun BalanceBar(sets: Double, low: Int, high: Int, color: Color, modifier: Modifier) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val zone = MaterialTheme.colorScheme.primary.copy(alpha = ZONE_ALPHA)
    Canvas(modifier.height(BAR_HEIGHT)) {
        val max = high * BAR_HEADROOM
        val radius = CornerRadius(size.height / 2)
        drawRoundRect(track, cornerRadius = radius)
        val start = size.width * (low / max).toFloat()
        drawRect(zone, topLeft = Offset(start, 0f), size = Size(size.width * ((high - low) / max).toFloat(), size.height))
        val end = size.width * (sets / max).toFloat().coerceIn(0f, 1f)
        if (end > 0f) drawRoundRect(color, size = Size(end.coerceAtLeast(size.height), size.height), cornerRadius = radius)
    }
}

private fun formatSets(sets: Double) = if (sets % 1.0 == 0.0) sets.toInt().toString() else "%.1f".localFormat(sets)

@Composable
private fun BodyWeightCard(points: List<BodyWeightPoint>, unit: WeightUnit, onLog: () -> Unit) = ContentCard {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            CardTitle(stringResource(Res.string.progress_body_weight))
            points.lastOrNull()?.let { latest ->
                Text(formatWeight(latest.weightKg, unit), style = SpotterTheme.type.numberLarge)
                Text(
                    stringResource(Res.string.progress_weight_average, formatWeightOneDecimal(latest.sevenDayAverageKg, unit)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
        FilledTonalButton(onClick = onLog) { Text(stringResource(Res.string.progress_log_weight)) }
    }
    val primary = remember(points, unit) { points.map { ChartPoint(it.date, unit.fromKg(it.weightKg)) } }
    val average = remember(points, unit) { points.map { ChartPoint(it.date, unit.fromKg(it.sevenDayAverageKg)) } }
    TrendChart(
        primary = primary,
        secondary = average,
        formatValue = { "%.0f".localFormat(it) },
        emptyText = stringResource(Res.string.progress_weight_empty),
    )
}

@Composable
internal fun LogWeightDialog(unit: WeightUnit, initial: String, onSave: (String) -> Boolean, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    var invalid by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.progress_log_weight)) },
        text = {
            OutlinedTextField(
                shape = MaterialTheme.shapes.small,
                value = text,
                onValueChange = { v ->
                    text = v.filter { it.isDigit() || it == '.' || it == ',' }.take(6)
                    invalid = false
                },
                label = { Text(stringResource(Res.string.progress_weight_label, unit.label)) },
                isError = invalid,
                supportingText = if (invalid) {
                    {
                        Text(
                            stringResource(
                                Res.string.progress_weight_invalid,
                                formatWeight(ProgressViewModel.MIN_WEIGHT_KG, unit),
                                formatWeight(ProgressViewModel.MAX_WEIGHT_KG, unit),
                            ),
                        )
                    }
                } else {
                    null
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        },
        confirmButton = {
            TextButton(onClick = { invalid = !onSave(text) }) { Text(stringResource(CoreRes.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreRes.string.action_cancel)) } },
    )
}


private const val ZONE_ALPHA = 0.16f
private const val BAR_HEADROOM = 1.25
private val BAR_HEIGHT = 8.dp
