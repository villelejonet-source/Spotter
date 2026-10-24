package com.viktorolsson.spotter.feature.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.viktorolsson.spotter.core.data.repository.HistoryRepository
import com.viktorolsson.spotter.core.data.repository.UserPreferencesRepository
import com.viktorolsson.spotter.core.data.repository.WorkoutRepository
import com.viktorolsson.spotter.core.model.PersonalRecord
import com.viktorolsson.spotter.core.model.WeightUnit
import com.viktorolsson.spotter.core.model.WorkoutSummary
import com.viktorolsson.spotter.core.ui.component.Confetti
import com.viktorolsson.spotter.core.ui.component.ErrorState
import com.viktorolsson.spotter.core.ui.component.LoadingState
import com.viktorolsson.spotter.core.ui.component.PrimaryButton
import com.viktorolsson.spotter.core.ui.component.RecordsCard
import com.viktorolsson.spotter.core.ui.component.StatTile
import com.viktorolsson.spotter.core.ui.formatClock
import com.viktorolsson.spotter.core.ui.formatSet
import com.viktorolsson.spotter.core.ui.formatSetCount
import com.viktorolsson.spotter.core.ui.formatTotal
import com.viktorolsson.spotter.core.ui.theme.Dimens
import com.viktorolsson.spotter.core.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class SummaryUiState(
    val loading: Boolean = true,
    /** Null once loaded means the workout is gone. */
    val summary: WorkoutSummary? = null,
    val unit: WeightUnit = WeightUnit.KG,
    val records: List<PersonalRecord> = emptyList(),
)

@HiltViewModel
class SummaryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    workoutRepository: WorkoutRepository,
    historyRepository: HistoryRepository,
    preferencesRepository: UserPreferencesRepository,
) : ViewModel() {
    private val sessionId = savedStateHandle.toRoute<SummaryRoute>().sessionId

    val uiState: StateFlow<SummaryUiState> = combine(
        workoutRepository.observeSession(sessionId),
        historyRepository.observeSessionRecords(sessionId),
        preferencesRepository.preferences,
    ) { session, records, prefs ->
        SummaryUiState(false, session?.let { WorkoutSummary.of(it) }, prefs.weightUnit, records)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SummaryUiState())
}

@Composable
internal fun SummaryRoute(onDone: () -> Unit, viewModel: SummaryViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val summary = uiState.summary
    when {
        uiState.loading -> Scaffold { padding -> LoadingState(Modifier.padding(padding).fillMaxSize()) }
        summary == null -> Scaffold(
            bottomBar = { PrimaryButton(stringResource(R.string.summary_done), onDone, Modifier.navigationBarsPadding().padding(Dimens.ScreenPadding)) },
        ) { padding -> ErrorState(Modifier.padding(padding).fillMaxSize()) }
        else -> Box {
            SummaryScreen(summary, uiState.unit, uiState.records, onDone)
            if (uiState.records.isNotEmpty()) Confetti(key = uiState.records.size)
        }
    }
}

@Composable
internal fun SummaryScreen(summary: WorkoutSummary, unit: WeightUnit, records: List<PersonalRecord>, onDone: () -> Unit) {
    Scaffold(
        bottomBar = {
            PrimaryButton(
                text = stringResource(R.string.summary_done),
                onClick = onDone,
                modifier = Modifier.navigationBarsPadding().padding(Dimens.ScreenPadding),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(Dimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.ListGap),
        ) {
            item(key = "title") {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = Spacing.xl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        Icons.Rounded.EmojiEvents,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(Dimens.IconLarge),
                    )
                    Text(
                        stringResource(R.string.summary_title),
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.semantics { heading() },
                    )
                }
            }
            item(key = "stats") {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    StatTile(stringResource(R.string.summary_duration), formatClock(summary.duration), Modifier.weight(1f))
                    StatTile(stringResource(R.string.summary_volume), formatTotal(summary.volumeKg, unit), Modifier.weight(1f))
                    StatTile(stringResource(R.string.summary_sets), summary.completedSets.toString(), Modifier.weight(1f))
                }
            }
            if (records.isNotEmpty()) {
                item(key = "records") {
                    RecordsCard(pluralStringResource(R.plurals.summary_new_prs, records.size, records.size), records, unit)
                }
            }
            itemsIndexed(summary.exercises, key = { i, ex -> "$i-${ex.exerciseName}" }) { _, ex ->
                val best = ex.bestSet?.let { set -> formatSet(set.weightKg, set.reps, unit, ex.timed) }
                ListItem(
                    headlineContent = { Text(ex.exerciseName) },
                    supportingContent = {
                        Text(
                            if (best != null) stringResource(R.string.summary_exercise_line, formatSetCount(ex.completedSets), best)
                            else formatSetCount(ex.completedSets),
                        )
                    },
                )
            }
        }
    }
}
