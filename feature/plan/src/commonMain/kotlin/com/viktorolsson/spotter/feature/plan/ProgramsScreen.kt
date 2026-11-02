package com.viktorolsson.spotter.feature.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.viktorolsson.spotter.core.model.Program
import com.viktorolsson.spotter.core.ui.component.ConfirmDialog
import com.viktorolsson.spotter.core.ui.component.LoadingState
import com.viktorolsson.spotter.core.ui.component.PrimaryButton
import com.viktorolsson.spotter.core.ui.component.ProgramCard
import com.viktorolsson.spotter.core.ui.component.SpotterTopBar
import com.viktorolsson.spotter.core.ui.labelRes
import com.viktorolsson.spotter.core.ui.theme.Dimens
import com.viktorolsson.spotter.feature.plan.resources.Res
import com.viktorolsson.spotter.feature.plan.resources.preview_confirm
import com.viktorolsson.spotter.feature.plan.resources.preview_confirm_body
import com.viktorolsson.spotter.feature.plan.resources.preview_confirm_title
import com.viktorolsson.spotter.feature.plan.resources.preview_estimated
import com.viktorolsson.spotter.feature.plan.resources.preview_intro
import com.viktorolsson.spotter.feature.plan.resources.preview_switch
import com.viktorolsson.spotter.feature.plan.resources.programs_intro
import com.viktorolsson.spotter.feature.plan.resources.programs_no_profile
import com.viktorolsson.spotter.feature.plan.resources.programs_questionnaire
import com.viktorolsson.spotter.feature.plan.resources.programs_title
import com.viktorolsson.spotter.feature.plan.resources.programs_use
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun ProgramsRoute(
    onBack: () -> Unit,
    onPreview: (Program) -> Unit,
    onQuestionnaire: () -> Unit,
    viewModel: ProgramsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ProgramsScreen(state, onBack = onBack, onPreview = onPreview, onQuestionnaire = onQuestionnaire)
}

@Composable
internal fun ProgramsScreen(
    state: ProgramsUiState,
    onBack: () -> Unit,
    onPreview: (Program) -> Unit,
    onQuestionnaire: () -> Unit,
    initiallyExpanded: Program? = null,
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    Scaffold(topBar = { SpotterTopBar(title = stringResource(Res.string.programs_title), onBack = onBack) }) { padding ->
        if (state.loading) {
            LoadingState(Modifier.padding(padding).fillMaxSize())
            return@Scaffold
        }
        val profile = state.profile
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(Dimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.ListGap),
        ) {
            item {
                Text(
                    stringResource(if (profile == null) Res.string.programs_no_profile else Res.string.programs_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (profile == null) {
                item { PrimaryButton(stringResource(Res.string.programs_questionnaire), onClick = onQuestionnaire, modifier = Modifier.fillMaxWidth()) }
            }
            items(Program.entries, key = { it.name }) { program ->
                val blocker = profile?.let { program.blocker(it.equipment, it.daysPerWeek) }
                val isOpen = expanded == program
                ProgramCard(
                    program = program,
                    daysPerWeek = profile?.let(program::daysPerWeek) ?: Program.FIVE_THREE_ONE_DAYS.takeIf { program == Program.FIVE_THREE_ONE },
                    onClick = { expanded = if (isOpen) null else program },
                    suits = program in state.suited,
                    current = program == state.current,
                    blocker = blocker,
                    expanded = isOpen,
                    expandable = true,
                    actions = if (profile != null && blocker == null && program != state.current) {
                        { PrimaryButton(stringResource(Res.string.programs_use), onClick = { onPreview(program) }, modifier = Modifier.fillMaxWidth()) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@Composable
internal fun ProgramPreviewRoute(
    onBack: () -> Unit,
    onSwitched: () -> Unit,
    viewModel: ProgramPreviewViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ProgramPreviewScreen(state, onBack = onBack, onSwitch = { viewModel.switch(onSwitched) })
}

@Composable
internal fun ProgramPreviewScreen(
    state: ProgramPreviewUiState,
    onBack: () -> Unit,
    onSwitch: () -> Unit,
) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    Scaffold(topBar = { SpotterTopBar(title = stringResource(state.program.labelRes), onBack = onBack) }) { padding ->
        val generated = state.generated
        if (generated == null) {
            LoadingState(Modifier.padding(padding).fillMaxSize())
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(Dimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.ListGap),
        ) {
            item {
                Text(stringResource(Res.string.preview_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.estimated) {
                item {
                    // A heads-up, not a failure: tertiary, as in onboarding's summary.
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                        Text(
                            stringResource(Res.string.preview_estimated),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(Dimens.CardPadding),
                        )
                    }
                }
            }
            items(generated.plan.days, key = { it.position }) { day ->
                DayCard(day, isNext = false, effortScale = state.effortScale, onStart = null)
            }
            item {
                PrimaryButton(
                    stringResource(Res.string.preview_switch),
                    onClick = { confirm = true },
                    busy = state.saving,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
    if (confirm) {
        ConfirmDialog(
            title = stringResource(Res.string.preview_confirm_title),
            text = stringResource(Res.string.preview_confirm_body),
            confirmText = stringResource(Res.string.preview_confirm),
            onConfirm = { confirm = false; onSwitch() },
            onDismiss = { confirm = false },
        )
    }
}
