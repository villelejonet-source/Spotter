package com.viktorolsson.spotter.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.viktorolsson.spotter.core.model.BodyArea
import com.viktorolsson.spotter.core.model.Equipment
import com.viktorolsson.spotter.core.model.Exercise
import com.viktorolsson.spotter.core.ui.labelRes

@Composable
internal fun ExercisePickerRoute(
    onDone: () -> Unit,
    viewModel: ExercisePickerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var creating by rememberSaveable { mutableStateOf(false) }

    ExercisePickerScreen(
        uiState = uiState,
        onBack = onDone,
        onQueryChange = viewModel::setQuery,
        onBodyAreaChange = viewModel::setBodyArea,
        onEquipmentChange = viewModel::setEquipment,
        onToggle = viewModel::toggle,
        onAdd = { viewModel.addSelected(onDone) },
        onCreate = { creating = true },
    )
    if (creating) {
        CreateExerciseDialog(
            initialName = uiState.filters.query,
            onCreate = { name, muscle, pattern, equipment ->
                viewModel.createCustom(name, muscle, pattern, equipment)
                creating = false
            },
            onDismiss = { creating = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExercisePickerScreen(
    uiState: ExercisePickerUiState,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onBodyAreaChange: (BodyArea?) -> Unit,
    onEquipmentChange: (EquipmentFilter?) -> Unit,
    onToggle: (String) -> Unit,
    onAdd: () -> Unit,
    onCreate: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.picker_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.picker_back))
                    }
                },
                actions = {
                    IconButton(onClick = onCreate) {
                        Icon(Icons.Rounded.Add, stringResource(R.string.picker_create))
                    }
                },
            )
        },
        floatingActionButton = {
            if (uiState.selectedIds.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onAdd,
                    icon = { Icon(Icons.Rounded.Check, contentDescription = null) },
                    text = { Text(stringResource(R.string.picker_add, uiState.selectedIds.size)) },
                    modifier = Modifier.imePadding(),
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = uiState.filters.query,
                onValueChange = onQueryChange,
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                placeholder = { Text(stringResource(R.string.picker_search)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { EquipmentFilterChip(uiState.filters.equipment, onEquipmentChange) }
                items(BodyArea.entries) { area ->
                    FilterChip(
                        selected = uiState.filters.bodyArea == area,
                        onClick = { onBodyAreaChange(if (uiState.filters.bodyArea == area) null else area) },
                        label = { Text(stringResource(area.labelRes)) },
                    )
                }
            }
            HorizontalDivider()
            if (uiState.exercises.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.TopCenter) {
                    Text(
                        stringResource(R.string.picker_no_results),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 96.dp),
                    modifier = Modifier.imePadding(),
                ) {
                    items(uiState.exercises, key = { it.id }) { exercise ->
                        val order = uiState.selectedIds.indexOf(exercise.id)
                        ExerciseRow(exercise, selectedOrder = order.takeIf { it >= 0 }?.plus(1)) {
                            onToggle(exercise.id)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EquipmentFilterChip(selected: EquipmentFilter?, onChange: (EquipmentFilter?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = selected != null,
            onClick = { open = true },
            label = {
                Text(
                    when (selected) {
                        null -> stringResource(R.string.picker_all_equipment)
                        EquipmentFilter.Bodyweight -> stringResource(R.string.picker_bodyweight)
                        is EquipmentFilter.Item -> stringResource(selected.equipment.labelRes)
                    },
                )
            },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.picker_all_equipment)) },
                onClick = { onChange(null); open = false },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.picker_bodyweight)) },
                onClick = { onChange(EquipmentFilter.Bodyweight); open = false },
            )
            Equipment.entries.forEach { equipment ->
                DropdownMenuItem(
                    text = { Text(stringResource(equipment.labelRes)) },
                    onClick = { onChange(EquipmentFilter.Item(equipment)); open = false },
                )
            }
        }
    }
}

@Composable
private fun ExerciseRow(exercise: Exercise, selectedOrder: Int?, onClick: () -> Unit) {
    val muscles = exercise.primaryMuscles.map { stringResource(it.labelRes) }
    val equipment = exercise.equipment.map { stringResource(it.labelRes) }
        .ifEmpty { listOf(stringResource(R.string.picker_bodyweight)) }
    val custom = if (exercise.isCustom) listOf(stringResource(R.string.picker_custom)) else emptyList()
    ListItem(
        headlineContent = { Text(exercise.name) },
        supportingContent = {
            Text((muscles.joinToString(", ") + " · " + equipment.joinToString(", ")).let { line ->
                (listOf(line) + custom).joinToString(" · ")
            })
        },
        trailingContent = {
            if (selectedOrder != null) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "$selectedOrder",
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        },
        colors = if (selectedOrder != null) {
            ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            ListItemDefaults.colors()
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
