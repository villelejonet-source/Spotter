package com.viktorolsson.spotter.feature.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.viktorolsson.spotter.core.model.Exercise
import com.viktorolsson.spotter.core.ui.labelRes

/** What an exercise works and how to do it: muscles, equipment and numbered coaching cues. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExerciseInfoSheet(exercise: Exercise, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(exercise.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })

            InfoLine(R.string.info_primary, exercise.primaryMuscles.map { stringResource(it.labelRes) })
            InfoLine(R.string.info_secondary, exercise.secondaryMuscles.map { stringResource(it.labelRes) })
            InfoLine(
                R.string.info_equipment,
                exercise.equipment.map { stringResource(it.labelRes) }.ifEmpty { listOf(stringResource(R.string.info_bodyweight)) },
            )

            val cues = exercise.instructions?.lines()?.map(String::trim)?.filter(String::isNotEmpty).orEmpty()
            if (cues.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.info_how_to),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.semantics { heading() },
                    )
                    cues.forEachIndexed { i, cue ->
                        Row {
                            Text(
                                "${i + 1}.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(28.dp),
                            )
                            Text(cue, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoLine(label: Int, values: List<String>) {
    if (values.isEmpty()) return
    Column {
        Text(stringResource(label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(values.joinToString(", "), style = MaterialTheme.typography.bodyLarge)
    }
}
