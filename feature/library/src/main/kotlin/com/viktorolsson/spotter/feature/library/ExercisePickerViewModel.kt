package com.viktorolsson.spotter.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.viktorolsson.spotter.core.data.repository.ExerciseRepository
import com.viktorolsson.spotter.core.data.repository.WorkoutRepository
import com.viktorolsson.spotter.core.model.BodyArea
import com.viktorolsson.spotter.core.model.Equipment
import com.viktorolsson.spotter.core.model.Exercise
import com.viktorolsson.spotter.core.model.MovementPattern
import com.viktorolsson.spotter.core.model.Muscle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Equipment filter: a specific item, or exercises needing no equipment at all. */
sealed interface EquipmentFilter {
    data object Bodyweight : EquipmentFilter
    data class Item(val equipment: Equipment) : EquipmentFilter
}

data class PickerFilters(
    val query: String = "",
    val bodyArea: BodyArea? = null,
    val equipment: EquipmentFilter? = null,
)

data class ExercisePickerUiState(
    val filters: PickerFilters = PickerFilters(),
    val exercises: List<Exercise> = emptyList(),
    /** In tap order, which becomes the order they're added to the workout. */
    val selectedIds: List<String> = emptyList(),
)

@HiltViewModel
class ExercisePickerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val exerciseRepository: ExerciseRepository,
    private val workoutRepository: WorkoutRepository,
) : ViewModel() {
    private val sessionId = savedStateHandle.toRoute<ExercisePickerRoute>().sessionId
    private val filters = MutableStateFlow(PickerFilters())
    private val selected = MutableStateFlow<List<String>>(emptyList())

    val uiState: StateFlow<ExercisePickerUiState> =
        combine(exerciseRepository.observeAll(), filters, selected) { all, f, sel ->
            ExercisePickerUiState(f, all.filter { it.matches(f) }, sel)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExercisePickerUiState())

    fun setQuery(query: String) = filters.update { it.copy(query = query) }

    fun setBodyArea(area: BodyArea?) = filters.update { it.copy(bodyArea = area) }

    fun setEquipment(filter: EquipmentFilter?) = filters.update { it.copy(equipment = filter) }

    fun toggle(exerciseId: String) = selected.update { if (exerciseId in it) it - exerciseId else it + exerciseId }

    fun addSelected(onDone: () -> Unit) = viewModelScope.launch {
        workoutRepository.addExercises(sessionId, selected.value)
        onDone()
    }

    fun createCustom(name: String, muscle: Muscle, pattern: MovementPattern, equipment: Equipment?) =
        viewModelScope.launch {
            val id = exerciseRepository.createCustom(name, muscle, pattern, listOfNotNull(equipment))
            selected.update { it + id }
            filters.update { it.copy(query = name.trim()) }
        }
}

private fun Exercise.matches(f: PickerFilters): Boolean {
    val words = f.query.trim().lowercase().split(Regex("\\s+")).filter(String::isNotEmpty)
    val text = name.lowercase()
    return words.all { it in text } &&
        (f.bodyArea == null || primaryMuscles.any { it in f.bodyArea.muscles }) &&
        when (val e = f.equipment) {
            null -> true
            EquipmentFilter.Bodyweight -> equipment.isEmpty()
            is EquipmentFilter.Item -> e.equipment in equipment
        }
}
