package com.viktorolsson.spotter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viktorolsson.spotter.core.data.repository.ActiveSession
import com.viktorolsson.spotter.core.data.repository.WorkoutRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** The in-progress workout, if any, for the mini-bar shown on every tab. */
class ActiveWorkoutViewModel(
    workoutRepository: WorkoutRepository,
) : ViewModel() {
    val activeSession: StateFlow<ActiveSession?> = workoutRepository.observeActiveSession()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
