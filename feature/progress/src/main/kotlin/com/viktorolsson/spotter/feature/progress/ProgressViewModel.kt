package com.viktorolsson.spotter.feature.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viktorolsson.spotter.core.data.repository.BodyWeightRepository
import com.viktorolsson.spotter.core.data.repository.HistoryRepository
import com.viktorolsson.spotter.core.data.repository.LoggedExerciseSummary
import com.viktorolsson.spotter.core.data.repository.UserPreferencesRepository
import com.viktorolsson.spotter.core.data.repository.UserProfileRepository
import com.viktorolsson.spotter.core.model.BodyWeightPoint
import com.viktorolsson.spotter.core.model.MuscleVolume
import com.viktorolsson.spotter.core.model.PersonalRecord
import com.viktorolsson.spotter.core.model.WeightUnit
import com.viktorolsson.spotter.core.model.parseToKg
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class ProgressUiState(
    val loading: Boolean = true,
    val records: List<PersonalRecord> = emptyList(),
    val volume: List<MuscleVolume> = emptyList(),
    val bodyWeight: List<BodyWeightPoint> = emptyList(),
    val lifts: List<LoggedExerciseSummary> = emptyList(),
    val unit: WeightUnit = WeightUnit.KG,
) {
    val isEmpty: Boolean get() = lifts.isEmpty() && bodyWeight.isEmpty()
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProgressViewModel @Inject constructor(
    historyRepository: HistoryRepository,
    private val bodyWeightRepository: BodyWeightRepository,
    profileRepository: UserProfileRepository,
    preferencesRepository: UserPreferencesRepository,
) : ViewModel() {
    val uiState: StateFlow<ProgressUiState> = combine(
        historyRepository.observeRecentRecords(limit = 5),
        profileRepository.observe().flatMapLatest { historyRepository.observeWeeklyVolume(it?.goal) },
        bodyWeightRepository.observeTrend(),
        historyRepository.observeLoggedExercises(),
        preferencesRepository.preferences,
    ) { records, volume, weight, lifts, prefs ->
        ProgressUiState(false, records, volume, weight, lifts, prefs.weightUnit)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressUiState())

    fun logWeight(text: String) = viewModelScope.launch {
        val kg = uiState.value.unit.parseToKg(text)?.takeIf { it in 30.0..300.0 } ?: return@launch
        bodyWeightRepository.log(LocalDate.now(), kg)
    }
}
