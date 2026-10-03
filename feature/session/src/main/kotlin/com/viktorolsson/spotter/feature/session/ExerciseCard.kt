package com.viktorolsson.spotter.feature.session

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingFlat
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.viktorolsson.spotter.core.model.PreviousSet
import com.viktorolsson.spotter.core.model.ProgressionReason
import com.viktorolsson.spotter.core.model.SessionExercise
import com.viktorolsson.spotter.core.model.SetType
import com.viktorolsson.spotter.core.model.UserPreferences
import com.viktorolsson.spotter.core.model.label
import com.viktorolsson.spotter.core.ui.component.RestTimePickerDialog
import com.viktorolsson.spotter.core.ui.formatClock

internal class ExerciseCardActions(
    val onValuesChange: (setId: Long, weight: String, reps: String, rir: String) -> Unit,
    val onToggleSet: (setId: Long) -> Unit,
    val onCopyPrevious: (setId: Long, PreviousSet) -> Unit,
    val onAddSet: () -> Unit,
    val onDeleteSet: (setId: Long) -> Unit,
    val onSetType: (setId: Long, SetType) -> Unit,
    val onSetNote: (setId: Long, String) -> Unit,
    val onSwap: () -> Unit,
    val onExerciseNote: (String) -> Unit,
    val onRest: (Int) -> Unit,
    val onSupersetNext: () -> Unit,
    val onRemoveSuperset: () -> Unit,
    val onMove: (offset: Int) -> Unit,
    val onRemove: () -> Unit,
)

// Fixed column widths so the header lines up with every row.
internal val SetColumnWidth = 40.dp
internal val WeightColumnWidth = 72.dp
internal val RepsColumnWidth = 56.dp
internal val RirColumnWidth = 48.dp
internal val CheckColumnWidth = 48.dp

@Composable
internal fun ExerciseCard(
    exercise: SessionExercise,
    isFirst: Boolean,
    isLast: Boolean,
    previous: List<PreviousSet>,
    preferences: UserPreferences,
    actions: ExerciseCardActions,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var editingNote by rememberSaveable { mutableStateOf(false) }
    var pickingRest by rememberSaveable { mutableStateOf(false) }
    val restSeconds = exercise.restSeconds ?: preferences.defaultRestSeconds

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 16.dp, end = 4.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    if (exercise.supersetGroup != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Text(
                                stringResource(R.string.exercise_superset_badge),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                    Text(
                        exercise.exercise.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { pickingRest = true }.padding(horizontal = 4.dp, vertical = 8.dp),
                ) {
                    Icon(
                        Icons.Rounded.Timer,
                        contentDescription = stringResource(R.string.exercise_rest),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                    Text(
                        formatClock(restSeconds.toLong()),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = actions.onSwap) {
                    Icon(Icons.Rounded.SwapHoriz, stringResource(R.string.exercise_swap))
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Rounded.MoreVert, stringResource(R.string.session_more))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        MenuItem(R.string.exercise_swap) { menuOpen = false; actions.onSwap() }
                        MenuItem(R.string.exercise_note) { menuOpen = false; editingNote = true }
                        MenuItem(R.string.exercise_rest) { menuOpen = false; pickingRest = true }
                        if (!isLast) MenuItem(R.string.exercise_superset_next) { menuOpen = false; actions.onSupersetNext() }
                        if (exercise.supersetGroup != null) {
                            MenuItem(R.string.exercise_superset_remove) { menuOpen = false; actions.onRemoveSuperset() }
                        }
                        if (!isFirst) MenuItem(R.string.exercise_move_up) { menuOpen = false; actions.onMove(-1) }
                        if (!isLast) MenuItem(R.string.exercise_move_down) { menuOpen = false; actions.onMove(1) }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.exercise_remove), color = MaterialTheme.colorScheme.error) },
                            onClick = { menuOpen = false; actions.onRemove() },
                        )
                    }
                }
            }
            exercise.target?.let { target ->
                Text(
                    target.targetRir?.let { stringResource(R.string.exercise_target_rir, target.sets, target.repMin, target.repMax, it) }
                        ?: stringResource(R.string.exercise_target, target.sets, target.repMin, target.repMax),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            exercise.substitutedFromName?.let { from ->
                Text(
                    stringResource(R.string.exercise_swapped_from, from),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            exercise.progressionReason?.let { ProgressionNote(it) }
            exercise.notes?.let { note ->
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                )
            }

            SetHeader(preferences)

            var workingNumber = 0
            exercise.sets.forEach { set ->
                if (set.setType != SetType.WARMUP) workingNumber++
                SetRow(
                    set = set,
                    workingNumber = workingNumber,
                    previous = previous.getOrNull(set.position),
                    unit = preferences.weightUnit,
                    logRir = preferences.logRir,
                    onValuesChange = { w, r, rir -> actions.onValuesChange(set.id, w, r, rir) },
                    onToggle = { actions.onToggleSet(set.id) },
                    onCopyPrevious = { actions.onCopyPrevious(set.id, it) },
                    onDelete = { actions.onDeleteSet(set.id) },
                    onTypeChange = { actions.onSetType(set.id, it) },
                    onNote = { actions.onSetNote(set.id, it) },
                )
            }

            TextButton(
                onClick = actions.onAddSet,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Text(stringResource(R.string.add_set), modifier = Modifier.padding(start = 4.dp))
            }
        }
    }

    if (editingNote) {
        TextInputDialog(
            title = stringResource(R.string.exercise_note),
            initial = exercise.notes.orEmpty(),
            onSave = { actions.onExerciseNote(it); editingNote = false },
            onDismiss = { editingNote = false },
        )
    }
    if (pickingRest) {
        RestTimePickerDialog(
            title = stringResource(R.string.exercise_rest),
            selectedSeconds = restSeconds,
            onSelect = { actions.onRest(it); pickingRest = false },
            onDismiss = { pickingRest = false },
        )
    }
}

/** Explains the pre-filled target, so progression never feels like a black box. */
@Composable
private fun ProgressionNote(reason: ProgressionReason) {
    val (text, icon, emphasised) = when (reason) {
        ProgressionReason.CALIBRATION -> Triple(R.string.progress_calibration, Icons.Rounded.Tune, false)
        ProgressionReason.FIRST_TIME -> Triple(R.string.progress_first_time, Icons.Rounded.Tune, false)
        ProgressionReason.INCREASE_WEIGHT -> Triple(R.string.progress_increase, Icons.AutoMirrored.Rounded.TrendingUp, true)
        ProgressionReason.INCREASE_TOO_EASY -> Triple(R.string.progress_increase_easy, Icons.AutoMirrored.Rounded.TrendingUp, true)
        ProgressionReason.ADD_REPS -> Triple(R.string.progress_add_reps, Icons.AutoMirrored.Rounded.TrendingUp, false)
        ProgressionReason.HOLD_TOO_HARD -> Triple(R.string.progress_hold_hard, Icons.AutoMirrored.Rounded.TrendingFlat, false)
        ProgressionReason.REPEAT -> Triple(R.string.progress_repeat, Icons.AutoMirrored.Rounded.TrendingFlat, false)
        ProgressionReason.RESET_AFTER_MISSES -> Triple(R.string.progress_reset, Icons.AutoMirrored.Rounded.TrendingDown, false)
        ProgressionReason.BODYWEIGHT_TOP_OF_RANGE -> Triple(R.string.progress_bodyweight_top, Icons.AutoMirrored.Rounded.TrendingUp, true)
        ProgressionReason.DELOAD -> Triple(R.string.progress_deload, Icons.AutoMirrored.Rounded.TrendingDown, false)
    }
    val color = if (emphasised) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(stringResource(text), style = MaterialTheme.typography.bodySmall, color = color)
    }
}

@Composable
private fun MenuItem(label: Int, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = onClick)
}

@Composable
private fun SetHeader(preferences: UserPreferences) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .padding(top = 8.dp, bottom = 2.dp)
            .height(24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        HeaderCell(stringResource(R.string.col_set), Modifier.width(SetColumnWidth))
        HeaderCell(stringResource(R.string.col_previous), Modifier.weight(1f))
        HeaderCell(preferences.weightUnit.label, Modifier.width(WeightColumnWidth))
        HeaderCell(stringResource(R.string.col_reps), Modifier.width(RepsColumnWidth))
        if (preferences.logRir) HeaderCell(stringResource(R.string.col_rir), Modifier.width(RirColumnWidth))
        Box(Modifier.width(CheckColumnWidth))
    }
}

@Composable
private fun RowScope.HeaderCell(text: String, modifier: Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}
