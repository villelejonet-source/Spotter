package com.viktorolsson.spotter.feature.session

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.math.roundToInt
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.viktorolsson.spotter.core.data.AppErrors
import com.viktorolsson.spotter.core.data.Failure
import com.viktorolsson.spotter.core.data.launchReporting
import com.viktorolsson.spotter.core.data.repository.HistoryRepository
import com.viktorolsson.spotter.core.data.repository.UserPreferencesRepository
import com.viktorolsson.spotter.core.data.repository.WorkoutRepository
import com.viktorolsson.spotter.core.model.PersonalRecord
import com.viktorolsson.spotter.core.model.WeightUnit
import com.viktorolsson.spotter.core.model.WorkoutSummary
import com.viktorolsson.spotter.core.ui.component.Confetti
import com.viktorolsson.spotter.core.ui.component.ContentCard
import com.viktorolsson.spotter.core.ui.component.ErrorState
import com.viktorolsson.spotter.core.ui.component.FeelPicker
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import com.viktorolsson.spotter.feature.session.resources.Res
import com.viktorolsson.spotter.feature.session.resources.summary_done
import com.viktorolsson.spotter.feature.session.resources.summary_duration
import com.viktorolsson.spotter.feature.session.resources.summary_exercise_line
import com.viktorolsson.spotter.feature.session.resources.summary_new_prs
import com.viktorolsson.spotter.feature.session.resources.summary_sets
import com.viktorolsson.spotter.feature.session.resources.summary_title
import com.viktorolsson.spotter.feature.session.resources.summary_volume
import com.viktorolsson.spotter.feature.session.resources.summary_effort
import com.viktorolsson.spotter.feature.session.resources.summary_effort_unset
import com.viktorolsson.spotter.feature.session.resources.summary_effort_value
import com.viktorolsson.spotter.feature.session.resources.summary_feel_title

data class SummaryUiState(
    val loading: Boolean = true,
    /** Null once loaded means the workout is gone. */
    val summary: WorkoutSummary? = null,
    val unit: WeightUnit = WeightUnit.KG,
    val records: List<PersonalRecord> = emptyList(),
    /** How the session went, 1–5, once rated. */
    val feel: Int? = null,
    /** Session effort, 1–10, once rated. */
    val effort: Int? = null,
)

class SummaryViewModel(
    savedStateHandle: SavedStateHandle,
    private val workoutRepository: WorkoutRepository,
    historyRepository: HistoryRepository,
    preferencesRepository: UserPreferencesRepository,
    private val errors: AppErrors,
) : ViewModel() {
    private val sessionId = savedStateHandle.toRoute<SummaryRoute>().sessionId

    val uiState: StateFlow<SummaryUiState> = combine(
        workoutRepository.observeSession(sessionId),
        historyRepository.observeSessionRecords(sessionId),
        preferencesRepository.preferences,
    ) { session, records, prefs ->
        SummaryUiState(false, session?.let { WorkoutSummary.of(it) }, prefs.weightUnit, records, session?.feel, session?.effort)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SummaryUiState())

    fun rateFeel(feel: Int) = viewModelScope.launchReporting(errors, Failure.SAVE) {
        workoutRepository.setSessionFeel(sessionId, feel)
    }

    fun rateEffort(effort: Int) = viewModelScope.launchReporting(errors, Failure.SAVE) {
        workoutRepository.setSessionEffort(sessionId, effort)
    }
}

@Composable
internal fun SummaryRoute(onDone: () -> Unit, viewModel: SummaryViewModel = koinViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val summary = uiState.summary
    when {
        uiState.loading -> Scaffold { padding -> LoadingState(Modifier.padding(padding).fillMaxSize()) }
        summary == null -> Scaffold(
            bottomBar = { PrimaryButton(stringResource(Res.string.summary_done), onDone, Modifier.navigationBarsPadding().padding(Dimens.ScreenPadding)) },
        ) { padding -> ErrorState(Modifier.padding(padding).fillMaxSize()) }
        else -> Box {
            SummaryScreen(
                summary, uiState.unit, uiState.records, onDone,
                feel = uiState.feel, effort = uiState.effort, onFeel = viewModel::rateFeel, onEffort = viewModel::rateEffort,
            )
            if (uiState.records.isNotEmpty()) Confetti(key = uiState.records.size)
        }
    }
}

@Composable
internal fun SummaryScreen(
    summary: WorkoutSummary,
    unit: WeightUnit,
    records: List<PersonalRecord>,
    onDone: () -> Unit,
    feel: Int? = null,
    effort: Int? = null,
    onFeel: (Int) -> Unit = {},
    onEffort: (Int) -> Unit = {},
) {
    Scaffold(
        bottomBar = {
            PrimaryButton(
                text = stringResource(Res.string.summary_done),
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
                        stringResource(Res.string.summary_title),
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.semantics { heading() },
                    )
                }
            }
            item(key = "stats") {
                Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    StatTile(stringResource(Res.string.summary_duration), formatClock(summary.duration), Modifier.weight(1f))
                    StatTile(stringResource(Res.string.summary_volume), formatTotal(summary.volumeKg, unit), Modifier.weight(1f))
                    StatTile(stringResource(Res.string.summary_sets), summary.completedSets.toString(), Modifier.weight(1f))
                }
            }
            item(key = "rating") { RatingCard(feel, effort, onFeel, onEffort) }
            if (records.isNotEmpty()) {
                item(key = "records") {
                    RecordsCard(pluralStringResource(Res.plurals.summary_new_prs, records.size, records.size), records, unit)
                }
            }
            itemsIndexed(summary.exercises, key = { i, ex -> "$i-${ex.exerciseName}" }) { _, ex ->
                val best = ex.bestSet?.let { set -> formatSet(set.weightKg, set.reps, unit, ex.timed) }
                ListItem(
                    headlineContent = { Text(ex.exerciseName) },
                    supportingContent = {
                        Text(
                            if (best != null) stringResource(Res.string.summary_exercise_line, formatSetCount(ex.completedSets), best)
                            else formatSetCount(ex.completedSets),
                        )
                    },
                )
            }
        }
    }
}

/** "How did it go?" (1–5) and an optional effort (1–10), both saved as soon as they change. */
@Composable
private fun RatingCard(feel: Int?, effort: Int?, onFeel: (Int) -> Unit, onEffort: (Int) -> Unit) {
    ContentCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(
                stringResource(Res.string.summary_feel_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            FeelPicker(selected = feel, onSelect = onFeel)
            // The slider moves locally and saves when let go, so dragging writes once.
            var dragging by remember(effort) { mutableStateOf(effort?.toFloat()) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.summary_effort), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(
                    dragging?.let { stringResource(Res.string.summary_effort_value, it.roundToInt()) } ?: stringResource(Res.string.summary_effort_unset),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Slider(
                value = dragging ?: 5f,
                onValueChange = { dragging = it },
                onValueChangeFinished = { dragging?.let { onEffort(it.roundToInt()) } },
                valueRange = 1f..10f,
                steps = 8,
            )
        }
    }
}
