package com.viktorolsson.spotter.feature.session

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.viktorolsson.spotter.core.data.di.ApplicationScope
import com.viktorolsson.spotter.core.data.repository.ExerciseRepository
import com.viktorolsson.spotter.core.data.repository.RecommendationRepository
import com.viktorolsson.spotter.core.data.repository.RestTimerRepository
import com.viktorolsson.spotter.core.data.repository.UserPreferencesRepository
import com.viktorolsson.spotter.core.data.repository.UserProfileRepository
import com.viktorolsson.spotter.core.data.repository.WorkoutRepository
import com.viktorolsson.spotter.core.engine.ExerciseSimilarity
import com.viktorolsson.spotter.core.model.Equipment
import com.viktorolsson.spotter.core.model.Exercise
import com.viktorolsson.spotter.core.model.PreviousSet
import com.viktorolsson.spotter.core.model.RestTimer
import com.viktorolsson.spotter.core.model.SessionExercise
import com.viktorolsson.spotter.core.model.SetType
import com.viktorolsson.spotter.core.model.UserPreferences
import com.viktorolsson.spotter.core.model.WorkoutSession
import com.viktorolsson.spotter.core.model.parseToKg
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SwapFilter { ALL, SAME_EQUIPMENT, DUMBBELL, MACHINE, BODYWEIGHT }

data class SwapUiState(
    val sessionExercise: SessionExercise,
    val candidates: List<Exercise> = emptyList(),
    val filter: SwapFilter = SwapFilter.ALL,
) {
    val canReplaceInPlan: Boolean get() = sessionExercise.target != null

    val visible: List<Exercise>
        get() = candidates.filter { candidate ->
            val eq = candidate.equipment.toSet()
            when (filter) {
                SwapFilter.ALL -> true
                SwapFilter.SAME_EQUIPMENT -> eq == sessionExercise.exercise.equipment.toSet()
                SwapFilter.DUMBBELL -> Equipment.DUMBBELL in eq
                SwapFilter.MACHINE -> eq.any { it in setOf(Equipment.MACHINE, Equipment.CABLE, Equipment.SMITH_MACHINE) }
                SwapFilter.BODYWEIGHT -> eq.all { it in setOf(Equipment.PULL_UP_BAR, Equipment.DIP_STATION, Equipment.BENCH, Equipment.RESISTANCE_BAND) }
            }
        }
}

data class SessionUiState(
    val loading: Boolean = true,
    /** Null once loaded means the session no longer exists (discarded). */
    val session: WorkoutSession? = null,
    /** Last time's completed sets, by exercise id. */
    val previous: Map<String, List<PreviousSet>> = emptyMap(),
    val preferences: UserPreferences = UserPreferences(),
    val restTimer: RestTimer? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SessionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val workoutRepository: WorkoutRepository,
    private val restTimerRepository: RestTimerRepository,
    private val exerciseRepository: ExerciseRepository,
    private val profileRepository: UserProfileRepository,
    private val recommendationRepository: RecommendationRepository,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
    preferencesRepository: UserPreferencesRepository,
) : ViewModel() {
    private val _swap = MutableStateFlow<SwapUiState?>(null)
    /** The open swap sheet, if any. */
    val swap: StateFlow<SwapUiState?> = _swap.asStateFlow()

    /** Ranks alternatives using the user's equipment and limitations (everything, if no profile yet). */
    fun openSwap(exercise: SessionExercise) = launch {
        _swap.value = SwapUiState(exercise)
        val profile = profileRepository.get()
        val inSession = uiState.value.session?.exercises.orEmpty().map { it.exercise.id }.toSet()
        val ranked = ExerciseSimilarity.rank(
            original = exercise.exercise,
            library = exerciseRepository.observeAll().first(),
            availableEquipment = profile?.equipment ?: Equipment.entries.toSet(),
            limitations = profile?.limitations.orEmpty(),
            exclude = inSession,
        )
        _swap.value = _swap.value?.copy(candidates = ranked.map { it.exercise })
    }

    fun setSwapFilter(filter: SwapFilter) = _swap.update { it?.copy(filter = filter) }

    fun closeSwap() {
        _swap.value = null
    }

    fun swapTo(exerciseId: String, replaceInPlan: Boolean) = launch {
        val current = _swap.value?.sessionExercise ?: return@launch
        _swap.value = null
        workoutRepository.swapExercise(current.id, exerciseId, replaceInPlan)
    }

    val sessionId = savedStateHandle.toRoute<SessionRoute>().sessionId

    private val session = workoutRepository.observeSession(sessionId)

    private val previous = session.filterNotNull()
        .map { s -> s.exercises.map { it.exercise.id }.toSet() }
        .distinctUntilChanged()
        .mapLatest { ids -> ids.associateWith { workoutRepository.getPreviousSets(it) } }
        .onStart { emit(emptyMap()) }

    val uiState: StateFlow<SessionUiState> = combine(
        session,
        previous,
        preferencesRepository.preferences,
        restTimerRepository.restTimer,
    ) { session, previous, prefs, rest ->
        SessionUiState(loading = false, session = session, previous = previous, preferences = prefs, restTimer = rest)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionUiState())

    private val prefs get() = uiState.value.preferences

    fun updateSetValues(setId: Long, weightText: String, repsText: String, rirText: String) = launch {
        workoutRepository.updateSetValues(
            setId = setId,
            weightKg = prefs.weightUnit.parseToKg(weightText),
            reps = repsText.trim().toIntOrNull()?.takeIf { it in 0..999 },
            rir = rirText.trim().toIntOrNull()?.takeIf { it in 0..10 },
        )
    }

    /** Ticks a set; on completion starts the rest timer unless mid-superset. */
    fun toggleSet(exercise: SessionExercise, setId: Long) = launch {
        val completed = workoutRepository.toggleSetCompleted(setId)
        if (!completed) return@launch
        val restSeconds = exercise.restSeconds ?: prefs.defaultRestSeconds
        if (!exercise.isMidSuperset()) restTimerRepository.start(restSeconds)
    }

    private fun SessionExercise.isMidSuperset(): Boolean {
        val group = supersetGroup ?: return false
        val members = uiState.value.session?.exercises.orEmpty().filter { it.supersetGroup == group }
        return members.maxByOrNull { it.position }?.id != id
    }

    fun copyPrevious(setId: Long, previous: PreviousSet) = launch {
        workoutRepository.updateSetValues(setId, previous.weightKg, previous.reps, rir = null)
    }

    fun addSet(sessionExerciseId: Long) = launch { workoutRepository.addSet(sessionExerciseId) }
    fun deleteSet(setId: Long) = launch { workoutRepository.deleteSet(setId) }
    fun setSetType(setId: Long, type: SetType) = launch { workoutRepository.setSetType(setId, type) }
    fun setSetNote(setId: Long, note: String) = launch { workoutRepository.setSetNotes(setId, note) }

    fun setExerciseNote(id: Long, note: String) = launch { workoutRepository.setExerciseNotes(id, note) }
    fun setExerciseRest(id: Long, seconds: Int) = launch { workoutRepository.setExerciseRest(id, seconds) }
    fun supersetWithNext(id: Long) = launch { workoutRepository.supersetWithNext(id) }
    fun removeFromSuperset(id: Long) = launch { workoutRepository.removeFromSuperset(id) }
    fun moveExercise(id: Long, offset: Int) = launch { workoutRepository.moveExercise(id, offset) }
    fun removeExercise(id: Long) = launch { workoutRepository.removeExercise(id) }

    fun setSessionNote(note: String) = launch { workoutRepository.setSessionNotes(sessionId, note) }

    fun adjustRest(seconds: Int) = launch { restTimerRepository.adjust(seconds) }
    fun skipRest() = launch { restTimerRepository.stop() }
    fun startCountdown(seconds: Int) = launch { restTimerRepository.start(seconds) }

    fun finish(onFinished: (Long) -> Unit) = launch {
        restTimerRepository.stop()
        workoutRepository.finishWorkout(sessionId)
        // Outlives this screen: re-evaluate plateaus and suggestions with the new session.
        applicationScope.launch { recommendationRepository.refresh() }
        onFinished(sessionId)
    }

    fun discard(onDiscarded: () -> Unit) = launch {
        restTimerRepository.stop()
        workoutRepository.discardWorkout(sessionId)
        onDiscarded()
    }

    private fun launch(block: suspend () -> Unit) = viewModelScope.launch { block() }
}
