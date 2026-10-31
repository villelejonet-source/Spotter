package com.viktorolsson.spotter.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import com.viktorolsson.spotter.core.ui.theme.Spacing
import com.viktorolsson.spotter.core.model.Equipment
import com.viktorolsson.spotter.core.model.MovementPattern
import com.viktorolsson.spotter.core.model.Muscle
import com.viktorolsson.spotter.core.ui.labelRes
import com.viktorolsson.spotter.core.ui.resources.Res as CoreRes
import com.viktorolsson.spotter.core.ui.resources.action_cancel
import org.jetbrains.compose.resources.stringResource
import com.viktorolsson.spotter.feature.library.resources.Res
import com.viktorolsson.spotter.feature.library.resources.create_equipment
import com.viktorolsson.spotter.feature.library.resources.create_muscle
import com.viktorolsson.spotter.feature.library.resources.create_name
import com.viktorolsson.spotter.feature.library.resources.create_pattern
import com.viktorolsson.spotter.feature.library.resources.create_save
import com.viktorolsson.spotter.feature.library.resources.create_title
import com.viktorolsson.spotter.feature.library.resources.picker_bodyweight

@Composable
internal fun CreateExerciseDialog(
    initialName: String,
    onCreate: (String, Muscle, MovementPattern, Equipment?) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName.trim()) }
    var muscle by rememberSaveable { mutableStateOf(Muscle.CHEST) }
    var pattern by rememberSaveable { mutableStateOf(MovementPattern.HORIZONTAL_PUSH) }
    var equipment by rememberSaveable { mutableStateOf<Equipment?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.create_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    shape = MaterialTheme.shapes.small,
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(Res.string.create_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                EnumDropdown(stringResource(Res.string.create_muscle), Muscle.entries, muscle, { stringResource(it.labelRes) }) {
                    muscle = it
                }
                EnumDropdown(stringResource(Res.string.create_pattern), MovementPattern.entries, pattern, { stringResource(it.labelRes) }) {
                    pattern = it
                }
                EnumDropdown(
                    label = stringResource(Res.string.create_equipment),
                    options = listOf(null) + Equipment.entries,
                    selected = equipment,
                    optionLabel = { it?.let { e -> stringResource(e.labelRes) } ?: stringResource(Res.string.picker_bodyweight) },
                ) { equipment = it }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name, muscle, pattern, equipment) },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(Res.string.create_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(CoreRes.string.action_cancel)) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> EnumDropdown(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            shape = MaterialTheme.shapes.small,
            value = optionLabel(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = { onSelect(option); expanded = false },
                )
            }
        }
    }
}
