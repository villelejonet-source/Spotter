package com.viktorolsson.spotter.feature.plan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.viktorolsson.spotter.core.data.AppErrors
import com.viktorolsson.spotter.core.data.Failure
import com.viktorolsson.spotter.core.data.launchReporting
import com.viktorolsson.spotter.core.data.repository.ExerciseRepository
import com.viktorolsson.spotter.core.data.repository.HistoryRepository
import com.viktorolsson.spotter.core.data.repository.PlanRepository
import com.viktorolsson.spotter.core.data.repository.UserPreferencesRepository
import com.viktorolsson.spotter.core.data.repository.UserProfileRepository
import com.viktorolsson.spotter.core.engine.GeneratedPlan
import com.viktorolsson.spotter.core.engine.KnownLifts
import com.viktorolsson.spotter.core.engine.LiftResult
import com.viktorolsson.spotter.core.engine.PlanGenerator
import com.viktorolsson.spotter.core.engine.PlanInput
import com.viktorolsson.spotter.core.engine.forProgram
import com.viktorolsson.spotter.core.engine.suitedPrograms
import com.viktorolsson.spotter.core.model.EffortScale
import com.viktorolsson.spotter.core.model.PersonalRecordType
import com.viktorolsson.spotter.core.model.Program
import com.viktorolsson.spotter.core.model.UserProfile
import com.viktorolsson.spotter.core.model.program
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

data class ProgramsUiState(
    val loading: Boolean = true,
    /** Null when onboarding was skipped: the cards are read-only until the questionnaire is done. */
    val profile: UserProfile? = null,
    /** The active plan's program; null without a plan or for a 5-day mix or custom plan. */
    val current: Program? = null,
    val suited: Set<Program> = emptySet(),
)

/** Every program as a card, marked against the saved profile and the active plan. */
class ProgramsViewModel(
    profileRepository: UserProfileRepository,
    planRepository: PlanRepository,
) : ViewModel() {
    val uiState: StateFlow<ProgramsUiState> = combine(profileRepository.observe(), planRepository.observeActivePlan()) { profile, plan ->
        ProgramsUiState(
            loading = false,
            profile = profile,
            current = plan?.program(),
            suited = profile?.let(::suitedPrograms).orEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgramsUiState())
}

data class ProgramPreviewUiState(
    val program: Program,
    val generated: GeneratedPlan? = null,
    /** No recent bench, squat or deadlift to start from: weights are estimated from the profile. */
    val estimated: Boolean = false,
    val effortScale: EffortScale = EffortScale.OFF,
    val saving: Boolean = false,
)

/**
 * The plan a program would give, built from the saved profile (no questionnaire), and the
 * switch to it. Starting weights come from the last [RECENT] of 1RM records on the three
 * anchor lifts, as the questionnaire's "recent set" answers would.
 */
class ProgramPreviewViewModel(
    savedStateHandle: SavedStateHandle,
    private val profileRepository: UserProfileRepository,
    private val planRepository: PlanRepository,
    private val exerciseRepository: ExerciseRepository,
    private val historyRepository: HistoryRepository,
    preferencesRepository: UserPreferencesRepository,
    private val clock: Clock,
    private val errors: AppErrors,
) : ViewModel() {
    private val program = Program.valueOf(savedStateHandle.toRoute<ProgramPreviewRoute>().program)
    private val _uiState = MutableStateFlow(ProgramPreviewUiState(program))
    val uiState: StateFlow<ProgramPreviewUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launchReporting(errors, Failure.ACTION) {
            preferencesRepository.preferences.collect { prefs -> _uiState.update { it.copy(effortScale = prefs.effortScale) } }
        }
        viewModelScope.launchReporting(errors, Failure.ACTION) {
            val profile = profileRepository.get() ?: return@launchReporting
            val library = exerciseRepository.observeAll().first()
            val known = knownLifts()
            val generated = withContext(Dispatchers.Default) {
                PlanGenerator().generate(
                    PlanInput(
                        profile = profile,
                        library = library,
                        today = clock.todayIn(TimeZone.currentSystemDefault()),
                        now = clock.now(),
                        knownLifts = known,
                    ).forProgram(program),
                )
            }
            _uiState.update { it.copy(generated = generated, estimated = known == KnownLifts()) }
        }
    }

    /** Makes the previewed plan the active one; history stays. */
    fun switch(onDone: () -> Unit) = viewModelScope.launchReporting(errors, Failure.SAVE) {
        val state = _uiState.value
        val plan = state.generated?.plan ?: return@launchReporting
        if (state.saving) return@launchReporting
        _uiState.update { it.copy(saving = true) }
        try {
            planRepository.saveAsActive(plan)
        } catch (e: Throwable) {
            // The snackbar says it failed; the button works again so it can be retried.
            _uiState.update { it.copy(saving = false) }
            throw e
        }
        onDone()
    }

    private suspend fun knownLifts(): KnownLifts {
        val since = clock.now() - RECENT
        suspend fun recent(exerciseId: String): LiftResult? = historyRepository.observeExerciseRecords(exerciseId).first()
            .filter { it.type == PersonalRecordType.ESTIMATED_1RM && it.achievedAt >= since }
            .maxByOrNull { it.value }
            ?.let { record -> LiftResult(record.weightKg ?: return@let null, record.reps ?: return@let null) }
        return KnownLifts(bench = recent(BENCH), squat = recent(SQUAT), deadlift = recent(DEADLIFT))
    }

    private companion object {
        val RECENT = 90.days
        const val BENCH = "barbell-bench-press"
        const val SQUAT = "back-squat"
        const val DEADLIFT = "deadlift"
    }
}
