package com.viktorolsson.spotter.feature.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viktorolsson.spotter.core.data.AppErrors
import com.viktorolsson.spotter.core.data.launchReporting
import com.viktorolsson.spotter.core.data.repository.BodyWeightRepository
import com.viktorolsson.spotter.core.data.repository.GamificationRepository
import com.viktorolsson.spotter.core.data.repository.HistoryRepository
import com.viktorolsson.spotter.core.data.repository.LoggedExerciseSummary
import com.viktorolsson.spotter.core.data.repository.RecommendationRepository
import com.viktorolsson.spotter.core.data.repository.UserPreferencesRepository
import com.viktorolsson.spotter.core.data.repository.UserProfileRepository
import com.viktorolsson.spotter.core.model.FeelSummary
import com.viktorolsson.spotter.core.model.Level
import com.viktorolsson.spotter.core.model.BodyWeightPoint
import com.viktorolsson.spotter.core.model.MuscleVolume
import com.viktorolsson.spotter.core.model.PersonalRecord
import com.viktorolsson.spotter.core.model.Recommendation
import com.viktorolsson.spotter.core.model.WeightUnit
import com.viktorolsson.spotter.core.model.parseToKg
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

data class ProgressUiState(
    val loading: Boolean = true,
    val records: List<PersonalRecord> = emptyList(),
    val volume: List<MuscleVolume> = emptyList(),
    val bodyWeight: List<BodyWeightPoint> = emptyList(),
    val lifts: List<LoggedExerciseSummary> = emptyList(),
    val unit: WeightUnit = WeightUnit.KG,
    val recommendations: List<Recommendation> = emptyList(),
    /** How recent workouts felt; null until one is rated. */
    val feel: FeelSummary? = null,
    /** Null when levels are switched off. */
    val levels: LevelSummary? = null,
) {
    val isEmpty: Boolean get() = lifts.isEmpty() && bodyWeight.isEmpty()
}

data class LevelSummary(val level: Level, val badgesUnlocked: Int, val badgesTotal: Int)

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressViewModel(
    historyRepository: HistoryRepository,
    private val bodyWeightRepository: BodyWeightRepository,
    private val recommendationRepository: RecommendationRepository,
    profileRepository: UserProfileRepository,
    preferencesRepository: UserPreferencesRepository,
    gamificationRepository: GamificationRepository,
    private val errors: AppErrors,
) : ViewModel() {
    private val levels = combine(gamificationRepository.observeProgress(), preferencesRepository.preferences) { progress, prefs ->
        if (prefs.showLevels) LevelSummary(progress.level, progress.badges.count { it.unlocked }, progress.badges.size) else null
    }

    private val base = combine(
        historyRepository.observeRecentRecords(limit = 5),
        profileRepository.observe().flatMapLatest { historyRepository.observeWeeklyVolume(it?.goal) },
        bodyWeightRepository.observeTrend(),
        historyRepository.observeLoggedExercises(),
        preferencesRepository.preferences,
    ) { records, volume, weight, lifts, prefs ->
        ProgressUiState(false, records, volume, weight, lifts, prefs.weightUnit)
    }

    val uiState: StateFlow<ProgressUiState> = combine(
        base, recommendationRepository.observeActive(), historyRepository.observeFeel(), levels,
    ) { state, recs, feel, levels ->
        state.copy(recommendations = recs, feel = feel, levels = levels)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressUiState())

    fun apply(id: Long) = viewModelScope.launchReporting(errors) { recommendationRepository.apply(id) }

    fun dismiss(id: Long) = viewModelScope.launchReporting(errors) { recommendationRepository.dismiss(id) }

    /** Saves today's body weight; returns false (and saves nothing) for text outside the accepted range. */
    fun logWeight(text: String): Boolean {
        val kg = uiState.value.unit.parseToKg(text)?.takeIf { it in MIN_WEIGHT_KG..MAX_WEIGHT_KG } ?: return false
        viewModelScope.launchReporting(errors) { bodyWeightRepository.log(Clock.System.todayIn(TimeZone.currentSystemDefault()), kg) }
        return true
    }

    companion object {
        const val MIN_WEIGHT_KG = 30.0
        const val MAX_WEIGHT_KG = 300.0
    }
}
