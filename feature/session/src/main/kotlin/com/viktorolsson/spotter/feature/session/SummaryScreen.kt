package com.viktorolsson.spotter.feature.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.unit.dp
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
import com.viktorolsson.spotter.core.model.format
import com.viktorolsson.spotter.core.model.label
import com.viktorolsson.spotter.core.ui.formatClock
import com.viktorolsson.spotter.core.ui.formatRecordValue
import com.viktorolsson.spotter.core.ui.formatTotal
import com.viktorolsson.spotter.core.ui.labelRes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class SummaryUiState(
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
        SummaryUiState(session?.let { WorkoutSummary.of(it) }, prefs.weightUnit, records)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SummaryUiState())
}

@Composable
internal fun SummaryRoute(onDone: () -> Unit, viewModel: SummaryViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val summary = uiState.summary ?: return
    SummaryScreen(summary, uiState.unit, uiState.records, onDone)
}

@Composable
internal fun SummaryScreen(summary: WorkoutSummary, unit: WeightUnit, records: List<PersonalRecord>, onDone: () -> Unit) {
    Scaffold(
        bottomBar = {
            Button(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).height(56.dp),
            ) { Text(stringResource(R.string.summary_done)) }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        Icons.Rounded.EmojiEvents,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp),
                    )
                    Text(stringResource(R.string.summary_title), style = MaterialTheme.typography.headlineMedium)
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile(stringResource(R.string.summary_duration), formatClock(summary.duration), Modifier.weight(1f))
                    StatTile(
                        stringResource(R.string.summary_volume),
                        formatTotal(summary.volumeKg, unit),
                        Modifier.weight(1f),
                    )
                    StatTile(stringResource(R.string.summary_sets), "${summary.completedSets}", Modifier.weight(1f))
                }
            }
            if (records.isNotEmpty()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                pluralStringResource(R.plurals.summary_new_prs, records.size, records.size),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                            records.forEach { record ->
                                Row {
                                    Text(
                                        "${record.exerciseName} · ${stringResource(record.type.labelRes)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        formatRecordValue(record, unit),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            items(summary.exercises) { ex ->
                val best = ex.bestSet?.let { set ->
                    val reps = set.reps ?: 0
                    set.weightKg?.let { "${unit.format(it)} ${unit.label} × $reps" } ?: "$reps reps"
                }
                ListItem(
                    headlineContent = { Text(ex.exerciseName) },
                    supportingContent = {
                        Text(
                            if (best != null) stringResource(R.string.summary_exercise_line, ex.completedSets, best)
                            else stringResource(R.string.summary_exercise_sets, ex.completedSets),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
    }
}
