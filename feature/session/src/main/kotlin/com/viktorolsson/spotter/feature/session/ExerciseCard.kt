package com.viktorolsson.spotter.feature.session

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingFlat
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.viktorolsson.spotter.core.engine.WarmUps
import com.viktorolsson.spotter.core.model.EffortScale
import com.viktorolsson.spotter.core.model.Mechanics
import com.viktorolsson.spotter.core.model.PreviousSet
import com.viktorolsson.spotter.core.model.ProgressionReason
import com.viktorolsson.spotter.core.model.SessionExercise
import com.viktorolsson.spotter.core.model.SetType
import com.viktorolsson.spotter.core.model.UserPreferences
import com.viktorolsson.spotter.core.model.isTimed
import com.viktorolsson.spotter.core.model.label
import com.viktorolsson.spotter.core.ui.component.ConfirmDialog
import com.viktorolsson.spotter.core.ui.component.RestTimePickerDialog
import com.viktorolsson.spotter.core.ui.component.SupersetBadge
import com.viktorolsson.spotter.core.ui.formatClock
import com.viktorolsson.spotter.core.ui.formatEffort
import com.viktorolsson.spotter.core.ui.formatTarget
import com.viktorolsson.spotter.core.ui.label
import com.viktorolsson.spotter.core.ui.theme.Dimens
import com.viktorolsson.spotter.core.ui.theme.Spacing
import java.util.Locale

/**
 * Everything a card can ask the screen to do. One instance serves every card (each
 * callback names the exercise or set it's about), so cards don't recompose when a
 * sibling changes.
 */
@Immutable
internal class ExerciseCardActions(
    val onFieldClick: (setId: Long, SetField) -> Unit,
    val onWarmUps: (exerciseId: Long) -> Unit,
    val onToggleSet: (SessionExercise, setId: Long) -> Unit,
    val onCopyPrevious: (setId: Long, PreviousSet) -> Unit,
    val onAddSet: (exerciseId: Long) -> Unit,
    val onDeleteSet: (setId: Long) -> Unit,
    val onSetType: (setId: Long, SetType) -> Unit,
    val onSetNote: (setId: Long, String) -> Unit,
    val onSwap: (SessionExercise) -> Unit,
    val onExerciseNote: (exerciseId: Long, String) -> Unit,
    val onRest: (exerciseId: Long, seconds: Int) -> Unit,
    val onSupersetNext: (exerciseId: Long) -> Unit,
    val onRemoveSuperset: (exerciseId: Long) -> Unit,
    val onMove: (exerciseId: Long, offset: Int) -> Unit,
    val onRemove: (exerciseId: Long) -> Unit,
)

// Fixed column widths so the header lines up with every row.
internal val SetColumnWidth = 40.dp
internal val WeightColumnWidth = 72.dp
internal val RepsColumnWidth = 56.dp
internal val EffortColumnWidth = 48.dp
internal val CheckColumnWidth = 48.dp

/**
 * One exercise in the workout. [focus] is the keypad target only when it belongs to
 * this card (null otherwise), and [buffer] what's being typed into it, so a keystroke
 * recomposes just the card being edited.
 */
@Composable
internal fun ExerciseCard(
    exercise: SessionExercise,
    isFirst: Boolean,
    isLast: Boolean,
    previous: List<PreviousSet>,
    preferences: UserPreferences,
    focus: KeypadTarget?,
    buffer: String,
    actions: ExerciseCardActions,
    modifier: Modifier = Modifier,
) {
    val timed = exercise.exercise.isTimed
    // Only offered when a ramp would actually add sets (an empty-bar working weight has none).
    val canWarmUp = remember(exercise, preferences.weightUnit) {
        exercise.exercise.mechanics == Mechanics.COMPOUND &&
            exercise.sets.none { it.setType == SetType.WARMUP || it.isCompleted } &&
            exercise.sets.firstOrNull { it.weightKg != null }?.weightKg
                ?.let { WarmUps.generate(it, exercise.exercise, preferences.weightUnit).isNotEmpty() } == true
    }
    var menuOpen by remember { mutableStateOf(false) }
    var editingNote by rememberSaveable { mutableStateOf(false) }
    var pickingRest by rememberSaveable { mutableStateOf(false) }
    var showingInfo by rememberSaveable { mutableStateOf(false) }
    var confirmRemove by rememberSaveable { mutableStateOf(false) }
    val restSeconds = exercise.restSeconds ?: preferences.defaultRestSeconds

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(vertical = Spacing.sm)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = Dimens.CardPadding, end = Spacing.xs),
            ) {
                Column(Modifier.weight(1f)) {
                    if (exercise.supersetGroup != null) SupersetBadge()
                    Text(
                        exercise.exercise.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clickable(onClickLabel = stringResource(R.string.a11y_exercise_info), role = Role.Button) { showingInfo = true }
                            .semantics { heading() },
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable(onClickLabel = stringResource(R.string.a11y_change_rest), role = Role.Button) { pickingRest = true }
                        .padding(horizontal = Spacing.xs, vertical = Spacing.sm),
                ) {
                    Icon(
                        Icons.Rounded.Timer,
                        contentDescription = stringResource(R.string.exercise_rest),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = Spacing.xs),
                    )
                    Text(
                        formatClock(restSeconds.toLong()),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { actions.onSwap(exercise) }) {
                    Icon(Icons.Rounded.SwapHoriz, stringResource(R.string.exercise_swap))
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Rounded.MoreVert, stringResource(R.string.session_more))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        MenuItem(R.string.exercise_swap) { menuOpen = false; actions.onSwap(exercise) }
                        if (canWarmUp) MenuItem(R.string.warmup_add) { menuOpen = false; actions.onWarmUps(exercise.id) }
                        MenuItem(R.string.exercise_note) { menuOpen = false; editingNote = true }
                        MenuItem(R.string.exercise_rest) { menuOpen = false; pickingRest = true }
                        if (!isLast) MenuItem(R.string.exercise_superset_next) { menuOpen = false; actions.onSupersetNext(exercise.id) }
                        if (exercise.supersetGroup != null) {
                            MenuItem(R.string.exercise_superset_remove) { menuOpen = false; actions.onRemoveSuperset(exercise.id) }
                        }
                        if (!isFirst) MenuItem(R.string.exercise_move_up) { menuOpen = false; actions.onMove(exercise.id, -1) }
                        if (!isLast) MenuItem(R.string.exercise_move_down) { menuOpen = false; actions.onMove(exercise.id, 1) }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.exercise_remove), color = MaterialTheme.colorScheme.error) },
                            onClick = { menuOpen = false; confirmRemove = true },
                        )
                    }
                }
            }
            exercise.target?.let { target ->
                val range = formatTarget(target.sets, target.repMin, target.repMax, timed)
                // Reps in reserve don't apply to a hold.
                val rir = target.targetRir?.takeUnless { timed }
                Text(
                    if (rir != null) stringResource(R.string.exercise_target_rir, range, formatEffort(rir, preferences.effortScale))
                    else stringResource(R.string.exercise_target, range),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Dimens.CardPadding),
                )
            }
            exercise.substitutedFromName?.let { from ->
                Text(
                    stringResource(R.string.exercise_swapped_from, from),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(horizontal = Dimens.CardPadding),
                )
            }
            exercise.progressionReason?.let { ProgressionNote(it, timed) }
            if (canWarmUp) {
                AssistChip(
                    onClick = { actions.onWarmUps(exercise.id) },
                    label = { Text(stringResource(R.string.warmup_add)) },
                    leadingIcon = { Icon(Icons.Rounded.Whatshot, contentDescription = null, modifier = Modifier.size(CHIP_ICON_SIZE)) },
                    modifier = Modifier.padding(horizontal = Dimens.CardPadding),
                )
            }
            exercise.notes?.let { note ->
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Dimens.CardPadding, vertical = Spacing.xxs),
                )
            }

            SetHeader(preferences, timed)

            var workingNumber = 0
            exercise.sets.forEach { set ->
                if (set.setType != SetType.WARMUP) workingNumber++
                // Keyed, so a row's swipe state leaves with its set instead of landing on the next one.
                key(set.id) {
                    SetRow(
                        set = set,
                        workingNumber = if (set.setType == SetType.WARMUP) 0 else workingNumber,
                        // Working sets line up with last time's working sets; warm-ups have no "previous".
                        previous = if (set.setType == SetType.WARMUP) null else previous.getOrNull(workingNumber - 1),
                        unit = preferences.weightUnit,
                        timed = timed,
                        effortScale = preferences.effortScale,
                        focusedField = focus?.takeIf { it.setId == set.id }?.field,
                        buffer = buffer,
                        onFieldClick = { actions.onFieldClick(set.id, it) },
                        onToggle = { actions.onToggleSet(exercise, set.id) },
                        onCopyPrevious = { actions.onCopyPrevious(set.id, it) },
                        onDelete = { actions.onDeleteSet(set.id) },
                        onTypeChange = { actions.onSetType(set.id, it) },
                        onNote = { actions.onSetNote(set.id, it) },
                    )
                }
            }

            TextButton(
                onClick = { actions.onAddSet(exercise.id) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Text(stringResource(R.string.add_set), modifier = Modifier.padding(start = Spacing.xs))
            }
        }
    }

    if (showingInfo) ExerciseInfoSheet(exercise.exercise, onDismiss = { showingInfo = false })
    if (editingNote) {
        TextInputDialog(
            title = stringResource(R.string.exercise_note),
            initial = exercise.notes.orEmpty(),
            onSave = { actions.onExerciseNote(exercise.id, it); editingNote = false },
            onDismiss = { editingNote = false },
        )
    }
    if (pickingRest) {
        RestTimePickerDialog(
            title = stringResource(R.string.exercise_rest),
            selectedSeconds = restSeconds,
            onSelect = { actions.onRest(exercise.id, it); pickingRest = false },
            onDismiss = { pickingRest = false },
        )
    }
    if (confirmRemove) {
        ConfirmDialog(
            title = stringResource(R.string.exercise_remove_title, exercise.exercise.name),
            text = stringResource(R.string.exercise_remove_body),
            confirmText = stringResource(R.string.exercise_remove_confirm),
            onConfirm = { confirmRemove = false; actions.onRemove(exercise.id) },
            onDismiss = { confirmRemove = false },
            destructive = true,
        )
    }
}

/** Explains the pre-filled target, so progression never feels like a black box. */
@Composable
private fun ProgressionNote(reason: ProgressionReason, timed: Boolean) {
    val (text, icon, emphasised) = when (reason) {
        ProgressionReason.CALIBRATION -> Triple(R.string.progress_calibration, Icons.Rounded.Tune, false)
        ProgressionReason.FIRST_TIME ->
            Triple(if (timed) R.string.progress_first_time_timed else R.string.progress_first_time, Icons.Rounded.Tune, false)
        ProgressionReason.INCREASE_WEIGHT -> Triple(R.string.progress_increase, Icons.AutoMirrored.Rounded.TrendingUp, true)
        ProgressionReason.INCREASE_TOO_EASY -> Triple(R.string.progress_increase_easy, Icons.AutoMirrored.Rounded.TrendingUp, true)
        ProgressionReason.ADD_REPS ->
            Triple(if (timed) R.string.progress_add_time else R.string.progress_add_reps, Icons.AutoMirrored.Rounded.TrendingUp, false)
        ProgressionReason.HOLD_TOO_HARD -> Triple(R.string.progress_hold_hard, Icons.AutoMirrored.Rounded.TrendingFlat, false)
        ProgressionReason.REPEAT -> Triple(R.string.progress_repeat, Icons.AutoMirrored.Rounded.TrendingFlat, false)
        ProgressionReason.RESET_AFTER_MISSES -> Triple(R.string.progress_reset, Icons.AutoMirrored.Rounded.TrendingDown, false)
        ProgressionReason.BODYWEIGHT_TOP_OF_RANGE -> Triple(R.string.progress_bodyweight_top, Icons.AutoMirrored.Rounded.TrendingUp, true)
        ProgressionReason.DELOAD -> Triple(R.string.progress_deload, Icons.AutoMirrored.Rounded.TrendingDown, false)
    }
    val color = if (emphasised) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        modifier = Modifier.padding(horizontal = Dimens.CardPadding, vertical = Spacing.xxs),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(Dimens.IconSmall))
        Text(stringResource(text), style = MaterialTheme.typography.bodySmall, color = color)
    }
}

@Composable
private fun MenuItem(label: Int, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = onClick)
}

@Composable
private fun SetHeader(preferences: UserPreferences, timed: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.sm)
            .padding(top = Spacing.sm, bottom = Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        HeaderCell(stringResource(R.string.col_set), Modifier.width(SetColumnWidth))
        HeaderCell(stringResource(R.string.col_previous), Modifier.weight(1f))
        HeaderCell(preferences.weightUnit.label, Modifier.width(WeightColumnWidth))
        HeaderCell(stringResource(if (timed) R.string.col_seconds else R.string.col_reps), Modifier.width(RepsColumnWidth))
        if (preferences.effortScale != EffortScale.OFF) HeaderCell(preferences.effortScale.label(), Modifier.width(EffortColumnWidth))
        Box(Modifier.width(CheckColumnWidth))
    }
}

@Composable
private fun RowScope.HeaderCell(text: String, modifier: Modifier) {
    Text(
        text.uppercase(Locale.getDefault()),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = modifier,
    )
}

private val CHIP_ICON_SIZE = 18.dp
