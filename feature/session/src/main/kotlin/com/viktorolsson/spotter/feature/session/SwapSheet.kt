package com.viktorolsson.spotter.feature.session

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.viktorolsson.spotter.core.model.Exercise
import com.viktorolsson.spotter.core.ui.component.EmptyState
import com.viktorolsson.spotter.core.ui.component.LoadingState
import com.viktorolsson.spotter.core.ui.component.SegmentedChoice
import com.viktorolsson.spotter.core.ui.labelRes
import com.viktorolsson.spotter.core.ui.theme.Dimens
import com.viktorolsson.spotter.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource

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
        SwapSheetContent(state, replaceInPlan, onReplaceInPlan = { replaceInPlan = it }, onFilter = onFilter, onPick = onPick)
    }
}

/** The sheet's body, usable on its own (screenshot tests). */
@Composable
internal fun SwapSheetContent(
    state: SwapUiState,
    replaceInPlan: Boolean,
    onReplaceInPlan: (Boolean) -> Unit,
    onFilter: (SwapFilter) -> Unit,
    onPick: (exerciseId: String, replaceInPlan: Boolean) -> Unit,
) {
    Column(Modifier.padding(horizontal = Dimens.SheetPadding), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text(
            stringResource(R.string.swap_title, state.sessionExercise.exercise.name),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        if (state.canReplaceInPlan) {
            SegmentedChoice(
                options = listOf(false to stringResource(R.string.swap_this_session), true to stringResource(R.string.swap_in_plan)),
                selected = replaceInPlan,
                onSelect = onReplaceInPlan,
            )
        }
    }
    LazyRow(
        contentPadding = PaddingValues(horizontal = Dimens.SheetPadding, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
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
        state.loading -> LoadingState(Modifier.fillMaxWidth().padding(bottom = Dimens.SheetPadding))
        state.visible.isEmpty() -> EmptyState(
            icon = Icons.Rounded.SwapHoriz,
            title = stringResource(R.string.swap_none_title),
            body = stringResource(R.string.swap_none),
            modifier = Modifier.padding(bottom = Dimens.SheetPadding),
        )
        else -> LazyColumn(Modifier.heightIn(max = LIST_MAX_HEIGHT), contentPadding = PaddingValues(bottom = Dimens.SheetPadding)) {
            items(state.visible, key = { it.id }) { exercise ->
                CandidateRow(exercise) { onPick(exercise.id, replaceInPlan) }
            }
        }
    }
}

@Composable
private fun CandidateRow(exercise: Exercise, onClick: () -> Unit) {
    val muscles = exercise.primaryMuscles.map { stringResource(it.labelRes) }.joinToString(", ")
    val equipment = exercise.equipment.map { stringResource(it.labelRes) }.joinToString(", ")
        .ifEmpty { stringResource(R.string.swap_bodyweight) }
    val pickLabel = stringResource(R.string.swap_pick)
    ListItem(
        headlineContent = { Text(exercise.name) },
        supportingContent = { Text("$muscles · $equipment") },
        modifier = Modifier.clickable(onClickLabel = pickLabel, role = Role.Button, onClick = onClick),
    )
}

private val LIST_MAX_HEIGHT = 520.dp
