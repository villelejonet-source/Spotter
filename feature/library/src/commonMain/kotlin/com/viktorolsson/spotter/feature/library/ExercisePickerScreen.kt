package com.viktorolsson.spotter.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.viktorolsson.spotter.core.model.BodyArea
import com.viktorolsson.spotter.core.model.Equipment
import com.viktorolsson.spotter.core.model.Exercise
import com.viktorolsson.spotter.core.ui.component.EmptyState
import com.viktorolsson.spotter.core.ui.component.LoadingState
import com.viktorolsson.spotter.core.ui.component.SpotterTopBar
import com.viktorolsson.spotter.core.ui.labelRes
import com.viktorolsson.spotter.core.ui.theme.Dimens
import com.viktorolsson.spotter.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import com.viktorolsson.spotter.feature.library.resources.Res
import com.viktorolsson.spotter.feature.library.resources.picker_add
import com.viktorolsson.spotter.feature.library.resources.picker_all_equipment
import com.viktorolsson.spotter.feature.library.resources.picker_bodyweight
import com.viktorolsson.spotter.feature.library.resources.picker_create
import com.viktorolsson.spotter.feature.library.resources.picker_custom
import com.viktorolsson.spotter.feature.library.resources.picker_no_results
import com.viktorolsson.spotter.feature.library.resources.picker_no_results_title
import com.viktorolsson.spotter.feature.library.resources.picker_search
import com.viktorolsson.spotter.feature.library.resources.picker_selected_order
import com.viktorolsson.spotter.feature.library.resources.picker_title

@Composable
internal fun ExercisePickerRoute(
    onDone: () -> Unit,
    viewModel: ExercisePickerViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var creating by rememberSaveable { mutableStateOf(false) }

    ExercisePickerScreen(
        uiState = uiState,
        query = viewModel.searchQuery,
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
            initialName = viewModel.searchQuery,
            onCreate = { name, muscle, pattern, equipment ->
                viewModel.createCustom(name, muscle, pattern, equipment)
                creating = false
            },
            onDismiss = { creating = false },
        )
    }
}

@Composable
internal fun ExercisePickerScreen(
    uiState: ExercisePickerUiState,
    query: String,
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
            SpotterTopBar(
                title = stringResource(Res.string.picker_title),
                onBack = onBack,
                actions = {
                    IconButton(onClick = onCreate) {
                        Icon(Icons.Rounded.Add, stringResource(Res.string.picker_create))
                    }
                },
            )
        },
        floatingActionButton = {
            if (uiState.selectedIds.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onAdd,
                    icon = { Icon(Icons.Rounded.Check, contentDescription = null) },
                    text = { Text(stringResource(Res.string.picker_add, uiState.selectedIds.size)) },
                    modifier = Modifier.imePadding(),
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                placeholder = { Text(stringResource(Res.string.picker_search)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.ScreenPadding),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = Dimens.ScreenPadding, vertical = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                item(key = "equipment") { EquipmentFilterChip(uiState.filters.equipment, onEquipmentChange) }
                items(BodyArea.entries, key = { it.name }) { area ->
                    FilterChip(
                        selected = uiState.filters.bodyArea == area,
                        onClick = { onBodyAreaChange(if (uiState.filters.bodyArea == area) null else area) },
                        label = { Text(stringResource(area.labelRes)) },
                    )
                }
            }
            HorizontalDivider()
            when {
                uiState.loading -> LoadingState(Modifier.fillMaxSize())
                uiState.exercises.isEmpty() -> EmptyState(
                    icon = Icons.Rounded.Search,
                    title = stringResource(Res.string.picker_no_results_title),
                    body = stringResource(Res.string.picker_no_results),
                    modifier = Modifier.fillMaxWidth(),
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(bottom = Dimens.FabClearance),
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
                        null -> stringResource(Res.string.picker_all_equipment)
                        EquipmentFilter.Bodyweight -> stringResource(Res.string.picker_bodyweight)
                        is EquipmentFilter.Item -> stringResource(selected.equipment.labelRes)
                    },
                )
            },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.picker_all_equipment)) },
                onClick = { onChange(null); open = false },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.picker_bodyweight)) },
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
        .ifEmpty { listOf(stringResource(Res.string.picker_bodyweight)) }
    val custom = if (exercise.isCustom) listOf(stringResource(Res.string.picker_custom)) else emptyList()
    val orderDescription = selectedOrder?.let { stringResource(Res.string.picker_selected_order, it) }
    ListItem(
        headlineContent = { Text(exercise.name) },
        supportingContent = {
            Text((muscles.joinToString(", ") + " · " + equipment.joinToString(", ")).let { line ->
                (listOf(line) + custom).joinToString(" · ")
            })
        },
        trailingContent = {
            if (selectedOrder != null && orderDescription != null) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(ORDER_BADGE_SIZE).semantics { contentDescription = orderDescription },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            selectedOrder.toString(),
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
        modifier = Modifier.selectable(selected = selectedOrder != null, role = Role.Checkbox, onClick = onClick),
    )
}

private val ORDER_BADGE_SIZE = 28.dp
