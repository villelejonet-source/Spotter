package com.viktorolsson.spotter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viktorolsson.spotter.core.data.repository.ActiveSession
import com.viktorolsson.spotter.core.data.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** The in-progress workout, if any, for the mini-bar shown on every tab. */
@HiltViewModel
class ActiveWorkoutViewModel @Inject constructor(
    workoutRepository: WorkoutRepository,
) : ViewModel() {
    val activeSession: StateFlow<ActiveSession?> = workoutRepository.observeActiveSession()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
