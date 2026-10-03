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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.viktorolsson.spotter.core.engine.Plates
import com.viktorolsson.spotter.core.model.Exercise
import com.viktorolsson.spotter.core.model.WeightUnit
import com.viktorolsson.spotter.core.model.format
import com.viktorolsson.spotter.core.model.label
import java.math.BigDecimal

/** Plates per side for the weight in the focused field. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun PlateSheet(exercise: Exercise, weightKg: Double, unit: WeightUnit, onDismiss: () -> Unit) {
    val loading = Plates.load(weightKg, exercise, unit)
    fun fmt(value: Double) = "${BigDecimal(value).stripTrailingZeros().toPlainString()} ${unit.label}"
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.plates_title), style = MaterialTheme.typography.titleLarge)
            if (loading == null) {
                Text(stringResource(R.string.plates_not_bar), style = MaterialTheme.typography.bodyMedium)
                return@Column
            }
            Text("${unit.format(weightKg)} ${unit.label} · ${stringResource(R.string.plates_bar, fmt(loading.bar))}")
            if (loading.perSide.isEmpty()) {
                Text(stringResource(R.string.plates_none), style = MaterialTheme.typography.bodyMedium)
            } else {
                val heaviest = loading.perSide.max()
                val description = loading.perSide.joinToString(", ") { fmt(it) }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clearAndSetSemantics { contentDescription = description },
                ) {
                    loading.perSide.forEach { plate ->
                        // Plate height scales with weight, like on the bar.
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.width(44.dp).height((48 + 72 * plate / heaviest).dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    BigDecimal(plate).stripTrailingZeros().toPlainString(),
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
                    stringResource(R.string.plates_not_exact, "${unit.format(weightKg)} ${unit.label}", fmt(loading.total)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
