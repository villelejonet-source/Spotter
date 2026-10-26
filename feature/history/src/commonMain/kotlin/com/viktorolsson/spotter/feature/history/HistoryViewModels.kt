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
import com.viktorolsson.spotter.core.model.EffortScale
import com.viktorolsson.spotter.core.model.Exercise
import com.viktorolsson.spotter.core.model.ExerciseSessionLog
import com.viktorolsson.spotter.core.model.PersonalRecord
import com.viktorolsson.spotter.core.model.Recommendation
import com.viktorolsson.spotter.core.model.SetType
import com.viktorolsson.spotter.core.model.WeightUnit
import com.viktorolsson.spotter.core.model.WorkoutSession
import com.viktorolsson.spotter.core.model.WorkoutSummary
import com.viktorolsson.spotter.core.ui.localDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.plusMonth
import kotlinx.datetime.todayIn
import kotlinx.datetime.yearMonth
import kotlin.time.Clock

/** The current month in the phone's time zone. */
private fun thisMonth(): YearMonth = Clock.System.todayIn(TimeZone.currentSystemDefault()).yearMonth

data class HistoryUiState(
    val loading: Boolean = true,
    /** False until the first workout is finished; the screen then shows its empty state. */
    val hasAnySession: Boolean = false,
    val month: YearMonth = thisMonth(),
    val unit: WeightUnit = WeightUnit.KG,
    /** The sessions in [month], newest first. */
    val monthSessions: List<WorkoutSession> = emptyList(),
    /** Training days in [month] with the day's volume in kg, for the heat-map. */
    val volumeByDay: Map<LocalDate, Double> = emptyMap(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    historyRepository: HistoryRepository,
    preferencesRepository: UserPreferencesRepository,
) : ViewModel() {
    private val month = MutableStateFlow(thisMonth())

    // Only the month on screen is read; one extra row tells us whether anything exists at all.
    private val monthSessions = month.flatMapLatest { m ->
        val zone = TimeZone.currentSystemDefault()
        historyRepository.observeFinishedSessionsBetween(
            start = m.firstDay.atStartOfDayIn(zone),
            end = m.plusMonth().firstDay.atStartOfDayIn(zone),
        )
    }

    val uiState: StateFlow<HistoryUiState> = combine(
        historyRepository.observeRecentFinished(limit = 1),
        monthSessions,
        month,
        preferencesRepository.preferences,
    ) { latest, inMonth, month, prefs ->
        HistoryUiState(
            loading = false,
            hasAnySession = latest.isNotEmpty(),
            month = month,
            unit = prefs.weightUnit,
            monthSessions = inMonth,
            volumeByDay = inMonth.groupBy { it.startedAt.localDate() }
                .mapValues { (_, list) -> list.sumOf { WorkoutSummary.of(it).volumeKg }.coerceAtLeast(1.0) },
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun shiftMonth(by: Int) = month.update { it.plus(by, DateTimeUnit.MONTH) }
}

data class WorkoutDetailUiState(
    val loading: Boolean = true,
    /** Null once loaded means the workout no longer exists. */
    val session: WorkoutSession? = null,
    val records: List<PersonalRecord> = emptyList(),
    val unit: WeightUnit = WeightUnit.KG,
    val effortScale: EffortScale = EffortScale.OFF,
)

class WorkoutDetailViewModel(
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
    ) { session, records, prefs -> WorkoutDetailUiState(false, session, records, prefs.weightUnit, prefs.effortScale) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutDetailUiState())

    fun repeat(onStarted: (Long) -> Unit) = viewModelScope.launch {
        onStarted(workoutRepository.repeatWorkout(sessionId))
    }
}

enum class ExerciseMetric { ONE_REP_MAX, BEST_SET, VOLUME }

data class ExerciseHistoryUiState(
    val loading: Boolean = true,
    val exercise: Exercise? = null,
    val logs: List<ExerciseSessionLog> = emptyList(),
    val records: List<PersonalRecord> = emptyList(),
    val unit: WeightUnit = WeightUnit.KG,
    val metric: ExerciseMetric = ExerciseMetric.ONE_REP_MAX,
    val recommendations: List<Recommendation> = emptyList(),
    /** Most reps achieved at each weight, heaviest first. */
    val repRecords: List<Pair<Double, Int>> = emptyList(),
) {
    val bestOneRepMaxKg: Double? get() = logs.mapNotNull { it.estimatedOneRepMax }.maxOrNull()
    val heaviestKg: Double? get() = logs.mapNotNull { it.bestSet?.weightKg }.maxOrNull()
    val bestVolumeKg: Double get() = logs.maxOfOrNull { it.volumeKg } ?: 0.0
}

class ExerciseHistoryViewModel(
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
        ExerciseHistoryUiState(
            loading = false,
            exercise = exercise,
            logs = logs,
            records = records,
            unit = prefs.weightUnit,
            metric = metric,
            repRecords = logs.flatMap { it.sets }
                .filter { it.weightKg != null && it.reps != null && it.setType != SetType.WARMUP }
                .groupBy { it.weightKg ?: 0.0 }
                .map { (weight, sets) -> weight to sets.maxOf { it.reps ?: 0 } }
                .sortedByDescending { it.first },
        )
    }

    val uiState: StateFlow<ExerciseHistoryUiState> = combine(base, recommendationRepository.observeActive()) { state, recs ->
        state.copy(recommendations = recs.filter { it.exerciseId == exerciseId })
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExerciseHistoryUiState())

    fun apply(id: Long) = viewModelScope.launch { recommendationRepository.apply(id) }

    fun dismiss(id: Long) = viewModelScope.launch { recommendationRepository.dismiss(id) }

    fun setMetric(value: ExerciseMetric) {
        metric.value = value
    }
}
