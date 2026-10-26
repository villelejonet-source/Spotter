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
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.viktorolsson.spotter.core.ui.theme.Dimens
import com.viktorolsson.spotter.core.ui.theme.Spacing
import com.viktorolsson.spotter.core.model.Exercise
import com.viktorolsson.spotter.core.ui.labelRes
import org.jetbrains.compose.resources.stringResource
import com.viktorolsson.spotter.feature.session.resources.Res
import com.viktorolsson.spotter.feature.session.resources.info_bodyweight
import com.viktorolsson.spotter.feature.session.resources.info_equipment
import com.viktorolsson.spotter.feature.session.resources.info_how_to
import com.viktorolsson.spotter.feature.session.resources.info_primary
import com.viktorolsson.spotter.feature.session.resources.info_secondary
import com.viktorolsson.spotter.feature.session.resources.info_step_number

/** What an exercise works and how to do it: muscles, equipment and numbered coaching cues. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExerciseInfoSheet(exercise: Exercise, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) { ExerciseInfoSheetContent(exercise) }
}

/** The sheet's body, usable on its own (screenshot tests). */
@Composable
internal fun ExerciseInfoSheetContent(exercise: Exercise) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Dimens.SheetPadding).padding(bottom = Dimens.SheetPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Text(exercise.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })

            InfoLine(Res.string.info_primary, exercise.primaryMuscles.map { stringResource(it.labelRes) })
            InfoLine(Res.string.info_secondary, exercise.secondaryMuscles.map { stringResource(it.labelRes) })
            InfoLine(
                Res.string.info_equipment,
                exercise.equipment.map { stringResource(it.labelRes) }.ifEmpty { listOf(stringResource(Res.string.info_bodyweight)) },
            )

            val cues = exercise.instructions?.lines()?.map(String::trim)?.filter(String::isNotEmpty).orEmpty()
            if (cues.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(
                        stringResource(Res.string.info_how_to),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.semantics { heading() },
                    )
                    cues.forEachIndexed { i, cue ->
                        Row {
                            Text(
                                stringResource(Res.string.info_step_number, i + 1),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(STEP_NUMBER_WIDTH),
                            )
                            Text(cue, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
}

@Composable
private fun InfoLine(label: StringResource, values: List<String>) {
    if (values.isEmpty()) return
    Column {
        Text(stringResource(label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(values.joinToString(", "), style = MaterialTheme.typography.bodyLarge)
    }
}

private val STEP_NUMBER_WIDTH = 28.dp
