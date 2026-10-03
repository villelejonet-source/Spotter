package com.viktorolsson.spotter.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viktorolsson.spotter.core.data.repository.PlanRepository
import com.viktorolsson.spotter.core.data.repository.UserPreferencesRepository
import com.viktorolsson.spotter.core.data.repository.WorkoutRepository
import com.viktorolsson.spotter.core.model.PlanDay
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TodayUiState(
    val loading: Boolean = true,
    val hasPlan: Boolean = false,
    val nextDay: PlanDay? = null,
    val activeSessionId: Long? = null,
    /** Last day of a deload week in progress. */
    val deloadUntil: java.time.LocalDate? = null,
)

@HiltViewModel
class TodayViewModel @Inject constructor(
    planRepository: PlanRepository,
    private val workoutRepository: WorkoutRepository,
    preferencesRepository: UserPreferencesRepository,
) : ViewModel() {
    val uiState: StateFlow<TodayUiState> = combine(
        planRepository.observeActivePlan(),
        planRepository.observeNextDay(),
        workoutRepository.observeActiveSession(),
        preferencesRepository.preferences,
    ) { plan, next, active, prefs ->
        TodayUiState(
            loading = false,
            hasPlan = plan != null,
            nextDay = next,
            activeSessionId = active?.id,
            deloadUntil = prefs.deloadUntil?.takeIf { prefs.isDeload(java.time.LocalDate.now()) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    fun startNext(onStarted: (Long) -> Unit) = viewModelScope.launch {
        val day = uiState.value.nextDay ?: return@launch
        onStarted(workoutRepository.startPlannedWorkout(day.id))
    }

    fun startEmptyWorkout(onStarted: (Long) -> Unit) = viewModelScope.launch {
        onStarted(workoutRepository.startEmptyWorkout())
    }
}
