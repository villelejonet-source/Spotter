package com.viktorolsson.spotter.feature.plan

import com.viktorolsson.spotter.core.data.repository.UserPreferencesRepository
import com.viktorolsson.spotter.core.model.EffortScale
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viktorolsson.spotter.core.data.repository.PlanRepository
import com.viktorolsson.spotter.core.data.repository.WorkoutRepository
import com.viktorolsson.spotter.core.model.Plan
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlanUiState(
    val loading: Boolean = true,
    val plan: Plan? = null,
    val nextDayId: Long? = null,
    val effortScale: EffortScale = EffortScale.OFF,
)

@HiltViewModel
class PlanViewModel @Inject constructor(
    planRepository: PlanRepository,
    private val workoutRepository: WorkoutRepository,
    preferencesRepository: UserPreferencesRepository,
) : ViewModel() {
    // One plan subscription: the next day is derived inside the repository.
    val uiState: StateFlow<PlanUiState> = combine(
        planRepository.observePlanAndNextDay(),
        preferencesRepository.preferences,
    ) { (plan, next), prefs -> PlanUiState(loading = false, plan = plan, nextDayId = next?.id, effortScale = prefs.effortScale) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanUiState())

    fun startDay(planDayId: Long, onStarted: (Long) -> Unit) = viewModelScope.launch {
        onStarted(workoutRepository.startPlannedWorkout(planDayId))
    }
}
