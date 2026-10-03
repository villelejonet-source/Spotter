package com.viktorolsson.spotter.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viktorolsson.spotter.core.data.repository.ExerciseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class TodayUiState(
    val exerciseCount: Int = 0,
)

@HiltViewModel
class TodayViewModel @Inject constructor(
    exerciseRepository: ExerciseRepository,
) : ViewModel() {
    val uiState: StateFlow<TodayUiState> = exerciseRepository.observeCount()
        .map { TodayUiState(exerciseCount = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())
}
