package com.viktorolsson.spotter.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viktorolsson.spotter.core.data.repository.ExerciseRepository
import com.viktorolsson.spotter.core.data.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TodayUiState(
    val exerciseCount: Int = 0,
    val activeSessionId: Long? = null,
)

@HiltViewModel
class TodayViewModel @Inject constructor(
    exerciseRepository: ExerciseRepository,
    private val workoutRepository: WorkoutRepository,
) : ViewModel() {
    val uiState: StateFlow<TodayUiState> = combine(
        exerciseRepository.observeCount(),
        workoutRepository.observeActiveSession(),
    ) { count, active -> TodayUiState(exerciseCount = count, activeSessionId = active?.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    fun startEmptyWorkout(onStarted: (Long) -> Unit) = viewModelScope.launch {
        onStarted(workoutRepository.startEmptyWorkout())
    }
}
