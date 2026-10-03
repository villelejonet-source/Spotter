package com.viktorolsson.spotter.feature.session

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.viktorolsson.spotter.core.model.PreviousSet
import com.viktorolsson.spotter.core.model.SetType
import com.viktorolsson.spotter.core.model.WeightUnit
import com.viktorolsson.spotter.core.model.WorkoutSet
import com.viktorolsson.spotter.core.model.format
import com.viktorolsson.spotter.core.model.parseToKg
import kotlin.math.abs

@Composable
internal fun SetRow(
    set: WorkoutSet,
    workingNumber: Int,
    previous: PreviousSet?,
    unit: WeightUnit,
    logRir: Boolean,
    onValuesChange: (weight: String, reps: String, rir: String) -> Unit,
    onToggle: () -> Unit,
    onCopyPrevious: (PreviousSet) -> Unit,
    onDelete: () -> Unit,
    onTypeChange: (SetType) -> Unit,
    onNote: (String) -> Unit,
) {
    // Local text is the source of truth while typing; it only resyncs when the
    // stored value changes underneath it (copy-previous, carry-forward, unit switch).
    var weight by rememberSaveable(set.id, unit) { mutableStateOf(set.weightKg?.let(unit::format).orEmpty()) }
    var reps by rememberSaveable(set.id) { mutableStateOf(set.reps?.toString().orEmpty()) }
    var rir by rememberSaveable(set.id) { mutableStateOf(set.rir?.toString().orEmpty()) }
    LaunchedEffect(set.weightKg, unit) {
        val local = unit.parseToKg(weight)
        if (!sameWeight(local, set.weightKg)) weight = set.weightKg?.let(unit::format).orEmpty()
    }
    LaunchedEffect(set.reps) { if (reps.toIntOrNull() != set.reps) reps = set.reps?.toString().orEmpty() }
    LaunchedEffect(set.rir) { if (rir.toIntOrNull() != set.rir) rir = set.rir?.toString().orEmpty() }

    var editingNote by rememberSaveable { mutableStateOf(false) }

    val dismissState = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        onDismiss = { if (it == SwipeToDismissBoxValue.EndToStart) onDelete() },
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(end = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Rounded.Delete, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
    ) {
        val rowColor by animateColorAsState(
            if (set.isCompleted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            else MaterialTheme.colorScheme.surfaceContainerLow,
            label = "setRow",
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(rowColor)
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .height(44.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SetLabel(set, workingNumber, onTypeChange, onNote = { editingNote = true }, onDelete = onDelete)

            Text(
                previous?.let { formatPrevious(it, unit) } ?: "—",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = previous != null) { previous?.let(onCopyPrevious) }
                    .padding(vertical = 10.dp),
            )
            NumberField(
                value = weight,
                placeholder = previous?.weightKg?.let(unit::format).orEmpty(),
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.width(WeightColumnWidth),
            ) {
                weight = it
                onValuesChange(it, reps, rir)
            }
            NumberField(
                value = reps,
                placeholder = previous?.reps?.toString().orEmpty(),
                keyboardType = KeyboardType.Number,
                imeAction = if (logRir) ImeAction.Next else ImeAction.Done,
                modifier = Modifier.width(RepsColumnWidth),
            ) {
                reps = it.filter(Char::isDigit).take(3)
                onValuesChange(weight, reps, rir)
            }
            if (logRir) {
                NumberField(
                    value = rir,
                    placeholder = "",
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                    modifier = Modifier.width(RirColumnWidth),
                ) {
                    rir = it.filter(Char::isDigit).take(2)
                    onValuesChange(weight, reps, rir)
                }
            }
            CheckButton(
                completed = set.isCompleted,
                enabled = set.isCompleted || reps.toIntOrNull() != null,
                onClick = onToggle,
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
private fun SetLabel(
    set: WorkoutSet,
    workingNumber: Int,
    onTypeChange: (SetType) -> Unit,
    onNote: () -> Unit,
    onDelete: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val (text, color) = when (set.setType) {
        SetType.WARMUP -> stringResource(R.string.set_warmup_short) to MaterialTheme.colorScheme.tertiary
        SetType.DROP -> stringResource(R.string.set_drop_short) to MaterialTheme.colorScheme.secondary
        SetType.FAILURE -> stringResource(R.string.set_failure_short) to MaterialTheme.colorScheme.error
        SetType.WORKING -> "$workingNumber" to MaterialTheme.colorScheme.onSurface
    }
    Box(Modifier.width(SetColumnWidth)) {
        Text(
            text + if (set.notes != null) "•" else "",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = color,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { open = true }
                .padding(vertical = 10.dp),
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
                text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                onClick = { open = false; onDelete() },
            )
        }
    }
}

@Composable
private fun NumberField(
    value: String,
    placeholder: String,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Next,
    onValueChange: (String) -> Unit,
) {
    val textStyle = MaterialTheme.typography.titleMedium.copy(
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurface,
    )
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = textStyle,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        modifier = modifier.height(40.dp),
        decorationBox = { inner ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Box(Modifier.fillMaxSize().padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
                    if (value.isEmpty()) {
                        Text(placeholder, style = textStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)))
                    }
                    inner()
                }
            }
        },
    )
}

@Composable
private fun CheckButton(completed: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val description = stringResource(if (completed) R.string.set_completed else R.string.set_complete)
    Box(
        Modifier
            .width(CheckColumnWidth)
            .height(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = when {
                completed -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.surfaceContainerHighest
            },
            modifier = Modifier.size(36.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = when {
                        completed -> MaterialTheme.colorScheme.onPrimary
                        enabled -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    },
                )
            }
        }
    }
}

private fun formatPrevious(previous: PreviousSet, unit: WeightUnit): String {
    val reps = previous.reps?.toString() ?: "–"
    return previous.weightKg?.let { "${unit.format(it)} × $reps" } ?: "$reps reps"
}

private fun sameWeight(a: Double?, b: Double?) = when {
    a == null || b == null -> a == b
    else -> abs(a - b) < 0.0005
}
