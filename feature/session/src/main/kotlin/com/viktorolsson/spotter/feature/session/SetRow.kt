package com.viktorolsson.spotter.feature.session

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.viktorolsson.spotter.core.model.EffortScale
import com.viktorolsson.spotter.core.model.PreviousSet
import com.viktorolsson.spotter.core.model.SetType
import com.viktorolsson.spotter.core.model.WeightUnit
import com.viktorolsson.spotter.core.model.WorkoutSet
import com.viktorolsson.spotter.core.model.format
import com.viktorolsson.spotter.core.ui.formatSet
import com.viktorolsson.spotter.core.ui.theme.Dimens
import com.viktorolsson.spotter.core.ui.theme.Spacing
import com.viktorolsson.spotter.core.ui.resources.Res as CoreRes
import com.viktorolsson.spotter.core.ui.resources.action_delete
import org.jetbrains.compose.resources.stringResource

/**
 * One set: label (tap for type/note/delete), previous (tap to copy), weight, reps,
 * optional effort (RIR or RPE), and the tick. Values are edited with the in-app [Keypad]; the focused
 * field shows what's being typed.
 */
@Composable
internal fun SetRow(
    set: WorkoutSet,
    workingNumber: Int,
    previous: PreviousSet?,
    unit: WeightUnit,
    /** A hold: the reps field holds seconds. */
    timed: Boolean,
    effortScale: EffortScale,
    focusedField: SetField?,
    buffer: String,
    onFieldClick: (SetField) -> Unit,
    onToggle: () -> Unit,
    onCopyPrevious: (PreviousSet) -> Unit,
    onDelete: () -> Unit,
    onTypeChange: (SetType) -> Unit,
    onNote: (String) -> Unit,
) {
    var editingNote by rememberSaveable { mutableStateOf(false) }
    val setNumber = if (set.setType == SetType.WARMUP) stringResource(R.string.set_warmup_short) else workingNumber.toString()

    val dismissState = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        onDismiss = { if (it == SwipeToDismissBoxValue.EndToStart) onDelete() },
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(end = Spacing.xl),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Rounded.Delete, stringResource(CoreRes.string.action_delete), tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
    ) {
        // A done row is tinted towards the secondary container. Opaque on purpose: the row sits
        // on the swipe-to-delete background, whose error colour would bleed through any alpha
        // (which is what made done rows look red in dark).
        val scheme = MaterialTheme.colorScheme
        val rowColor by animateColorAsState(
            if (set.isCompleted) lerp(scheme.surfaceContainerLow, scheme.secondaryContainer, 0.6f) else scheme.surfaceContainerLow,
            label = "setRow",
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(rowColor)
                .padding(horizontal = Spacing.sm, vertical = Spacing.xxs)
                .height(Dimens.SetRowHeight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            SetLabel(set, setNumber, onTypeChange, onNote = { editingNote = true }, onDelete = onDelete)
            PreviousCell(previous, unit, timed, onCopyPrevious, Modifier.weight(1f))
            FieldBox(
                text = if (focusedField == SetField.WEIGHT) buffer else set.weightKg?.let(unit::format).orEmpty(),
                placeholder = previous?.weightKg?.let(unit::format).orEmpty(),
                focused = focusedField == SetField.WEIGHT,
                description = stringResource(R.string.a11y_weight_field, workingNumber),
                modifier = Modifier.width(WeightColumnWidth),
                onClick = { onFieldClick(SetField.WEIGHT) },
            )
            FieldBox(
                text = if (focusedField == SetField.REPS) buffer else set.reps?.toString().orEmpty(),
                placeholder = previous?.reps?.toString().orEmpty(),
                focused = focusedField == SetField.REPS,
                description = stringResource(if (timed) R.string.a11y_seconds_field else R.string.a11y_reps_field, workingNumber),
                modifier = Modifier.width(RepsColumnWidth),
                onClick = { onFieldClick(SetField.REPS) },
            )
            if (effortScale != EffortScale.OFF) {
                FieldBox(
                    text = if (focusedField == SetField.EFFORT) buffer else set.rir?.let(effortScale::display)?.toString().orEmpty(),
                    placeholder = "",
                    focused = focusedField == SetField.EFFORT,
                    description = stringResource(
                        if (effortScale == EffortScale.RPE) R.string.a11y_rpe_field else R.string.a11y_rir_field,
                        workingNumber,
                    ),
                    modifier = Modifier.width(EffortColumnWidth),
                    onClick = { onFieldClick(SetField.EFFORT) },
                )
            }
            CheckButton(
                completed = set.isCompleted,
                enabled = set.isCompleted || set.reps != null,
                setLabel = setNumber,
                onToggle = onToggle,
            )
        }
    }

    if (editingNote) {
        TextInputDialog(
            title = stringResource(R.string.set_note),
            initial = set.notes.orEmpty(),
            onSave = { onNote(it); editingNote = false },
            onDismiss = { editingNote = false },
        )
    }
}

@Composable
private fun PreviousCell(previous: PreviousSet?, unit: WeightUnit, timed: Boolean, onCopy: (PreviousSet) -> Unit, modifier: Modifier) {
    // The column header already names the unit, so the cell leaves it out.
    val text = previous?.let { formatSet(it.weightKg, it.reps, unit, timed, withUnit = false) } ?: stringResource(R.string.set_no_previous)
    val description = previous?.let { stringResource(R.string.a11y_previous, formatSet(it.weightKg, it.reps, unit, timed)) }
        ?: stringResource(R.string.a11y_no_previous)
    val copyLabel = stringResource(R.string.a11y_copy_previous)
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(enabled = previous != null, onClickLabel = copyLabel, role = Role.Button) { previous?.let(onCopy) }
            .semantics { contentDescription = description }
            .padding(vertical = Spacing.md),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FieldBox(
    text: String,
    placeholder: String,
    focused: Boolean,
    description: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(focused) { if (focused) requester.bringIntoView() }
    val empty = stringResource(R.string.a11y_empty)
    val editLabel = stringResource(R.string.a11y_edit)
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (focused) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        border = if (focused) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = modifier
            .height(Dimens.FieldHeight)
            .bringIntoViewRequester(requester)
            .clip(MaterialTheme.shapes.small)
            .clickable(onClickLabel = editLabel, role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = description
                stateDescription = text.ifEmpty { empty }
            },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text.ifEmpty { placeholder },
                style = MaterialTheme.typography.titleMedium,
                color = if (text.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SetLabel(
    set: WorkoutSet,
    number: String,
    onTypeChange: (SetType) -> Unit,
    onNote: () -> Unit,
    onDelete: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val (typeLabel, color) = when (set.setType) {
        SetType.WARMUP -> stringResource(R.string.set_type_warmup) to MaterialTheme.colorScheme.tertiary
        SetType.DROP -> stringResource(R.string.set_type_drop) to MaterialTheme.colorScheme.secondary
        SetType.FAILURE -> stringResource(R.string.set_type_failure) to MaterialTheme.colorScheme.error
        SetType.WORKING -> stringResource(R.string.set_type_working) to MaterialTheme.colorScheme.onSurface
    }
    val shortLabel = when (set.setType) {
        SetType.DROP -> stringResource(R.string.set_drop_short)
        SetType.FAILURE -> stringResource(R.string.set_failure_short)
        else -> number
    }
    val description = stringResource(R.string.a11y_set_label, number, typeLabel)
    val changeLabel = stringResource(R.string.a11y_change_type)
    Box(Modifier.width(SetColumnWidth)) {
        Text(
            if (set.notes != null) stringResource(R.string.set_label_with_note, shortLabel) else shortLabel,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = color,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .clickable(onClickLabel = changeLabel, role = Role.Button) { open = true }
                .semantics { contentDescription = description }
                .padding(vertical = Spacing.md),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf(
                SetType.WARMUP to R.string.set_type_warmup,
                SetType.WORKING to R.string.set_type_working,
                SetType.DROP to R.string.set_type_drop,
                SetType.FAILURE to R.string.set_type_failure,
            ).forEach { (type, label) ->
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    leadingIcon = { if (type == set.setType) Icon(Icons.Rounded.Check, contentDescription = null) },
                    onClick = { open = false; onTypeChange(type) },
                )
            }
            DropdownMenuItem(text = { Text(stringResource(R.string.set_note)) }, onClick = { open = false; onNote() })
            DropdownMenuItem(
                text = { Text(stringResource(CoreRes.string.action_delete), color = MaterialTheme.colorScheme.error) },
                onClick = { open = false; onDelete() },
            )
        }
    }
}

/** The tick; a checkbox for screen readers. */
@Composable
private fun CheckButton(completed: Boolean, enabled: Boolean, setLabel: String, onToggle: () -> Unit) {
    val description = stringResource(R.string.a11y_complete_set, setLabel)
    Box(
        Modifier
            .width(CheckColumnWidth)
            .height(Dimens.TouchTarget)
            .clip(MaterialTheme.shapes.medium)
            .toggleable(value = completed, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() })
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = if (completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.size(CHECK_SIZE),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = when {
                        completed -> MaterialTheme.colorScheme.onPrimary
                        enabled -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    },
                )
            }
        }
    }
}

private val CHECK_SIZE = 38.dp
