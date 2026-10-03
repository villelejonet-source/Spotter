package com.viktorolsson.spotter.feature.session

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.viktorolsson.spotter.core.model.Exercise
import com.viktorolsson.spotter.core.ui.labelRes

/** Ranked alternatives for a busy or missing piece of equipment; "Just this session" by default. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SwapSheet(
    state: SwapUiState,
    onFilter: (SwapFilter) -> Unit,
    onPick: (exerciseId: String, replaceInPlan: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var replaceInPlan by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.swap_title, state.sessionExercise.exercise.name),
                style = MaterialTheme.typography.titleLarge,
            )
            if (state.canReplaceInPlan) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(false to R.string.swap_this_session, true to R.string.swap_in_plan).forEachIndexed { i, (value, label) ->
                        SegmentedButton(
                            selected = replaceInPlan == value,
                            onClick = { replaceInPlan = value },
                            shape = SegmentedButtonDefaults.itemShape(i, 2),
                        ) { Text(stringResource(label)) }
                    }
                }
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(
                listOf(
                    SwapFilter.ALL to R.string.swap_filter_all,
                    SwapFilter.SAME_EQUIPMENT to R.string.swap_filter_same,
                    SwapFilter.DUMBBELL to R.string.swap_filter_dumbbell,
                    SwapFilter.MACHINE to R.string.swap_filter_machine,
                    SwapFilter.BODYWEIGHT to R.string.swap_filter_bodyweight,
                ),
            ) { (filter, label) ->
                FilterChip(selected = state.filter == filter, onClick = { onFilter(filter) }, label = { Text(stringResource(label)) })
            }
        }
        when {
            state.candidates.isEmpty() -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.visible.isEmpty() -> Text(
                stringResource(R.string.swap_none),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(32.dp),
            )
            else -> LazyColumn(Modifier.heightIn(max = 520.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(state.visible, key = { it.id }) { exercise ->
                    CandidateRow(exercise) { onPick(exercise.id, replaceInPlan) }
                }
            }
        }
    }
}

@Composable
private fun CandidateRow(exercise: Exercise, onClick: () -> Unit) {
    val muscles = exercise.primaryMuscles.map { stringResource(it.labelRes) }.joinToString(", ")
    val equipment = exercise.equipment.map { stringResource(it.labelRes) }.joinToString(", ")
        .ifEmpty { stringResource(R.string.swap_bodyweight) }
    ListItem(
        headlineContent = { Text(exercise.name) },
        supportingContent = { Text("$muscles · $equipment") },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
