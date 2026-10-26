package com.viktorolsson.spotter.feature.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.viktorolsson.spotter.core.ui.formatWeight
import com.viktorolsson.spotter.core.ui.theme.Dimens
import com.viktorolsson.spotter.core.ui.theme.Spacing
import com.viktorolsson.spotter.core.engine.Plates
import com.viktorolsson.spotter.core.model.Exercise
import com.viktorolsson.spotter.core.model.WeightUnit
import com.viktorolsson.spotter.core.model.label
import com.viktorolsson.spotter.core.ui.resources.Res as CoreRes
import com.viktorolsson.spotter.core.ui.resources.weight_with_unit
import org.jetbrains.compose.resources.stringResource
import com.viktorolsson.spotter.feature.session.resources.Res
import com.viktorolsson.spotter.feature.session.resources.plates_none
import com.viktorolsson.spotter.feature.session.resources.plates_not_bar
import com.viktorolsson.spotter.feature.session.resources.plates_not_exact
import com.viktorolsson.spotter.feature.session.resources.plates_summary
import com.viktorolsson.spotter.feature.session.resources.plates_title
import com.viktorolsson.spotter.core.ui.plainNumber

/** Plates per side for the weight in the focused field. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun PlateSheet(exercise: Exercise, weightKg: Double, unit: WeightUnit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) { PlateSheetContent(exercise, weightKg, unit) }
}

/** The sheet's body, usable on its own (screenshot tests). Plate values are in [unit] already. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlateSheetContent(exercise: Exercise, weightKg: Double, unit: WeightUnit) {
    val loading = Plates.load(weightKg, exercise, unit)
    @Composable
    fun fmt(value: Double) = stringResource(CoreRes.string.weight_with_unit, plainNumber(value), unit.label)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Dimens.SheetPadding).padding(bottom = Dimens.SheetPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
            Text(stringResource(Res.string.plates_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            if (loading == null) {
                Text(stringResource(Res.string.plates_not_bar), style = MaterialTheme.typography.bodyMedium)
                return@Column
            }
            Text(stringResource(Res.string.plates_summary, formatWeight(weightKg, unit), fmt(loading.bar)))
            if (loading.perSide.isEmpty()) {
                Text(stringResource(Res.string.plates_none), style = MaterialTheme.typography.bodyMedium)
            } else {
                val heaviest = loading.perSide.max()
                val description = loading.perSide.map { fmt(it) }.joinToString(", ")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    itemVerticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clearAndSetSemantics { contentDescription = description },
                ) {
                    loading.perSide.forEach { plate ->
                        // Plate height scales with weight, like on the bar.
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.width(PLATE_WIDTH).height(PLATE_MIN_HEIGHT + PLATE_EXTRA_HEIGHT * (plate / heaviest).toFloat()),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    plainNumber(plate),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            }
                        }
                    }
                }
            }
            if (!loading.exact) {
                Text(
                    stringResource(Res.string.plates_not_exact, formatWeight(weightKg, unit), fmt(loading.total)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
}

private val PLATE_WIDTH = 44.dp
private val PLATE_MIN_HEIGHT = 48.dp
private val PLATE_EXTRA_HEIGHT = 72.dp
