package com.viktorolsson.spotter.feature.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.viktorolsson.spotter.core.data.repository.ExerciseRepository
import com.viktorolsson.spotter.core.data.repository.HistoryRepository
import com.viktorolsson.spotter.core.data.repository.RecommendationRepository
import com.viktorolsson.spotter.core.data.repository.UserPreferencesRepository
import com.viktorolsson.spotter.core.data.repository.WorkoutRepository
import com.viktorolsson.spotter.core.model.Exercise
import com.viktorolsson.spotter.core.model.ExerciseSessionLog
import com.viktorolsson.spotter.core.model.PersonalRecord
import com.viktorolsson.spotter.core.model.Recommendation
import com.viktorolsson.spotter.core.model.WeightUnit
import com.viktorolsson.spotter.core.model.WorkoutSession
import com.viktorolsson.spotter.core.ui.localDate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

data class HistoryUiState(
    val loading: Boolean = true,
    val sessions: List<WorkoutSession> = emptyList(),
    val month: YearMonth = YearMonth.now(),
    val unit: WeightUnit = WeightUnit.KG,
) {
    val monthSessions: List<WorkoutSession>
        get() = sessions.filter { YearMonth.from(it.startedAt.localDate()) == month }
}

@HiltViewModel
class HistoryViewModel @Inject constructor(
    historyRepository: HistoryRepository,
    preferencesRepository: UserPreferencesRepository,
) : ViewModel() {
    private val month = MutableStateFlow(YearMonth.now())

    val uiState: StateFlow<HistoryUiState> = combine(
        historyRepository.observeFinishedSessions(),
        month,
        preferencesRepository.preferences,
    ) { sessions, month, prefs -> HistoryUiState(false, sessions, month, prefs.weightUnit) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun shiftMonth(by: Long) = month.update { it.plusMonths(by) }
}

data class WorkoutDetailUiState(
    val session: WorkoutSession? = null,
    val records: List<PersonalRecord> = emptyList(),
    val unit: WeightUnit = WeightUnit.KG,
)

@HiltViewModel
class WorkoutDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val workoutRepository: WorkoutRepository,
    historyRepository: HistoryRepository,
    preferencesRepository: UserPreferencesRepository,
) : ViewModel() {
    private val sessionId = savedStateHandle.toRoute<WorkoutDetailRoute>().sessionId

    val uiState: StateFlow<WorkoutDetailUiState> = combine(
        workoutRepository.observeSession(sessionId),
        historyRepository.observeSessionRecords(sessionId),
        preferencesRepository.preferences,
    ) { session, records, prefs -> WorkoutDetailUiState(session, records, prefs.weightUnit) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutDetailUiState())

    fun repeat(onStarted: (Long) -> Unit) = viewModelScope.launch {
        onStarted(workoutRepository.repeatWorkout(sessionId))
    }
}

enum class ExerciseMetric { ONE_REP_MAX, BEST_SET, VOLUME }

data class ExerciseHistoryUiState(
    val exercise: Exercise? = null,
    val logs: List<ExerciseSessionLog> = emptyList(),
    val records: List<PersonalRecord> = emptyList(),
    val unit: WeightUnit = WeightUnit.KG,
    val metric: ExerciseMetric = ExerciseMetric.ONE_REP_MAX,
    val recommendations: List<Recommendation> = emptyList(),
) {
    /** Most reps achieved at each weight, heaviest first. */
    val repRecords: List<Pair<Double, Int>>
        get() = logs.flatMap { it.sets }
            .filter { it.weightKg != null && it.reps != null && it.setType != com.viktorolsson.spotter.core.model.SetType.WARMUP }
            .groupBy { it.weightKg!! }
            .map { (weight, sets) -> weight to sets.maxOf { it.reps!! } }
            .sortedByDescending { it.first }
}

@HiltViewModel
class ExerciseHistoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    exerciseRepository: ExerciseRepository,
    historyRepository: HistoryRepository,
    preferencesRepository: UserPreferencesRepository,
    private val recommendationRepository: RecommendationRepository,
) : ViewModel() {
    private val exerciseId = savedStateHandle.toRoute<ExerciseHistoryRoute>().exerciseId
    private val metric = MutableStateFlow(ExerciseMetric.ONE_REP_MAX)

    private val base = combine(
        flow { emit(exerciseRepository.getById(exerciseId)) },
        historyRepository.observeExerciseLogs(exerciseId),
        historyRepository.observeExerciseRecords(exerciseId),
        preferencesRepository.preferences,
        metric,
    ) { exercise, logs, records, prefs, metric ->
        ExerciseHistoryUiState(exercise, logs, records, prefs.weightUnit, metric)
    }

    val uiState: StateFlow<ExerciseHistoryUiState> = combine(base, recommendationRepository.observeActive()) { state, recs ->
        state.copy(recommendations = recs.filter { it.exerciseId == exerciseId })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExerciseHistoryUiState())

    fun apply(id: Long) = viewModelScope.launch { recommendationRepository.apply(id) }

    fun dismiss(id: Long) = viewModelScope.launch { recommendationRepository.dismiss(id) }

    fun setMetric(value: ExerciseMetric) {
        metric.value = value
    }
}
