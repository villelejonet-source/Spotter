package com.viktorolsson.spotter.feature.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.viktorolsson.spotter.core.data.AppErrors
import com.viktorolsson.spotter.core.data.Failure
import com.viktorolsson.spotter.core.data.launchReporting
import com.viktorolsson.spotter.core.data.repository.ExerciseRepository
import com.viktorolsson.spotter.core.data.repository.PlanRepository
import com.viktorolsson.spotter.core.data.repository.UserPreferencesRepository
import com.viktorolsson.spotter.core.data.repository.UserProfileRepository
import com.viktorolsson.spotter.core.data.sync.SyncRepository
import com.viktorolsson.spotter.core.engine.GeneratedPlan
import com.viktorolsson.spotter.core.engine.KnownLifts
import com.viktorolsson.spotter.core.engine.LiftResult
import com.viktorolsson.spotter.core.engine.PlanGenerator
import com.viktorolsson.spotter.core.engine.PlanInput
import com.viktorolsson.spotter.core.model.BodyArea
import com.viktorolsson.spotter.core.model.Equipment
import com.viktorolsson.spotter.core.model.ExperienceLevel
import com.viktorolsson.spotter.core.model.Goal
import com.viktorolsson.spotter.core.model.Limitation
import com.viktorolsson.spotter.core.model.NumberInput
import com.viktorolsson.spotter.core.model.Sex
import com.viktorolsson.spotter.core.model.SplitType
import com.viktorolsson.spotter.core.model.UserProfile
import com.viktorolsson.spotter.core.model.WeightUnit
import com.viktorolsson.spotter.core.model.format
import com.viktorolsson.spotter.core.model.parseToKg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
import kotlinx.datetime.yearsUntil
import kotlin.math.roundToInt
import kotlin.time.Clock

enum class Step { BODY, GOAL, EXPERIENCE, DAYS, LENGTH, EQUIPMENT, FOCUS, LIMITATIONS, LIFTS, SUMMARY }

enum class Lift { BENCH, SQUAT, DEADLIFT }

enum class EquipmentPreset(val equipment: Set<Equipment>) {
    FULL_GYM(Equipment.entries.toSet()),
    HOME_GYM(setOf(Equipment.BARBELL, Equipment.SQUAT_RACK, Equipment.BENCH, Equipment.DUMBBELL, Equipment.PULL_UP_BAR)),
    DUMBBELLS(setOf(Equipment.DUMBBELL, Equipment.BENCH)),
    BODYWEIGHT(emptySet()),
}

data class LiftInput(val weight: String = "", val reps: String = "")

/** Raw answers, kept as text where the user types, so half-typed values survive. */
data class Answers(
    val sex: Sex? = null,
    val imperial: Boolean = false,
    val age: String = "",
    val heightCm: String = "",
    val heightFt: String = "",
    val heightIn: String = "",
    val weight: String = "",
    val goal: Goal? = null,
    val experience: ExperienceLevel? = null,
    val days: Int? = null,
    val minutes: Int? = null,
    val equipment: Set<Equipment> = EquipmentPreset.FULL_GYM.equipment,
    val focus: Set<BodyArea> = emptySet(),
    val focusTouched: Boolean = false,
    val limitations: Set<Limitation> = emptySet(),
    val lifts: Map<Lift, LiftInput> = emptyMap(),
) {
    val unit: WeightUnit get() = if (imperial) WeightUnit.LB else WeightUnit.KG

    val ageYears: Int? get() = NumberInput.wholeOrNull(age)?.takeIf { it in 13..100 }

    val heightCmValue: Double?
        get() = if (imperial) {
            val ft = NumberInput.wholeOrNull(heightFt) ?: return null
            val inches = if (heightIn.isBlank()) 0 else NumberInput.wholeOrNull(heightIn) ?: return null
            ((ft * 12 + inches) * 2.54).takeIf { it in 120.0..230.0 }
        } else {
            NumberInput.decimalOrNull(heightCm)?.takeIf { it in 120.0..230.0 }
        }

    val weightKg: Double? get() = unit.parseToKg(weight)?.takeIf { it in 30.0..300.0 }

    fun knownLifts(): KnownLifts {
        fun result(lift: Lift): LiftResult? {
            val input = lifts[lift] ?: return null
            val kg = unit.parseToKg(input.weight)?.takeIf { it > 0 } ?: return null
            val reps = NumberInput.wholeOrNull(input.reps)?.takeIf { it in 1..20 } ?: return null
            return LiftResult(kg, reps)
        }
        return KnownLifts(bench = result(Lift.BENCH), squat = result(Lift.SQUAT), deadlift = result(Lift.DEADLIFT))
    }

    fun isValid(step: Step): Boolean = when (step) {
        Step.BODY -> sex != null && ageYears != null && heightCmValue != null && weightKg != null
        Step.GOAL -> goal != null
        Step.EXPERIENCE -> experience != null
        Step.DAYS -> days != null
        Step.LENGTH -> minutes != null
        Step.EQUIPMENT, Step.FOCUS, Step.LIMITATIONS, Step.LIFTS, Step.SUMMARY -> true
    }
}

data class OnboardingUiState(
    val step: Step = Step.BODY,
    val answers: Answers = Answers(),
    val rebuild: Boolean = false,
    val splitOverride: SplitType? = null,
    val generated: GeneratedPlan? = null,
    val saving: Boolean = false,
    /** First launch: choose between an account and staying on this phone only. */
    val welcome: Boolean = false,
    /** Just signed in: waiting for the first sync to see if there's a backup to restore. */
    val checkingBackup: Boolean = false,
    val signedIn: Boolean = false,
    /** This build has cloud backup; without it there are no accounts, so no welcome screen. */
    val cloudAvailable: Boolean = false,
)

class OnboardingViewModel(
    savedStateHandle: SavedStateHandle,
    private val exerciseRepository: ExerciseRepository,
    private val planRepository: PlanRepository,
    private val profileRepository: UserProfileRepository,
    private val preferencesRepository: UserPreferencesRepository,
    syncRepository: SyncRepository,
    private val clock: Clock,
    private val errors: AppErrors,
) : ViewModel() {
    private val rebuild = savedStateHandle.toRoute<OnboardingRoute>().rebuild
    private val _uiState = MutableStateFlow(OnboardingUiState(rebuild = rebuild, welcome = !rebuild))
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        if (!rebuild) viewModelScope.launchReporting(errors, Failure.ACTION) {
            syncRepository.status.collect { sync ->
                val signedIn = sync.account != null
                _uiState.update {
                    it.copy(
                        // No welcome when this build has no cloud backup, or once signed in.
                        welcome = it.welcome && sync.available && !signedIn,
                        signedIn = signedIn,
                        cloudAvailable = sync.available,
                        checkingBackup = signedIn && sync.lastSyncedAt == null && sync.error == null,
                    )
                }
            }
        }
        viewModelScope.launchReporting(errors, Failure.ACTION) {
            val profile = profileRepository.get() ?: return@launchReporting
            _uiState.update { it.copy(answers = profile.toAnswers()) }
        }
    }

    /** Fires when setup gets completed some other way: restoring a backup from the cloud. */
    val restoredFromBackup: Flow<Unit> = preferencesRepository.preferences
        .map { it.onboardingCompleted }
        .distinctUntilChanged()
        .drop(1)
        .filter { it && !_uiState.value.saving }
        .map { }

    fun update(change: (Answers) -> Answers) = _uiState.update { it.copy(answers = change(it.answers)) }

    /** Switching units converts what's already typed instead of reinterpreting it. */
    fun setImperial(imperial: Boolean) = update { a ->
        if (a.imperial == imperial) return@update a
        val target = if (imperial) WeightUnit.LB else WeightUnit.KG
        val heightCm = a.heightCmValue
        val totalInches = heightCm?.let { (it / 2.54).roundToInt() }
        a.copy(
            imperial = imperial,
            weight = a.weightKg?.let { target.format(it).substringBefore('.') } ?: a.weight,
            heightCm = heightCm?.roundToInt()?.toString() ?: a.heightCm,
            heightFt = totalInches?.let { (it / 12).toString() } ?: a.heightFt,
            heightIn = totalInches?.let { (it % 12).toString() } ?: a.heightIn,
            lifts = emptyMap(),
        )
    }

    /** Single-choice questions move on as soon as something is picked. */
    fun choose(change: (Answers) -> Answers) {
        update(change)
        next()
    }

    fun next() {
        val state = _uiState.value
        if (!state.answers.isValid(state.step)) return
        val nextStep = Step.entries.getOrNull(state.step.ordinal + 1) ?: return
        _uiState.update { it.copy(step = nextStep, answers = it.answers.withDefaultFocus(nextStep)) }
        if (nextStep == Step.SUMMARY) generate()
    }

    fun continueWithoutAccount() = _uiState.update { it.copy(welcome = false) }

    /** Returns false when already on the first step (the caller then leaves). */
    fun back(): Boolean {
        val state = _uiState.value
        // Signed out on the first question: back returns to the welcome screen.
        if (state.step == Step.BODY && !state.rebuild && !state.signedIn && state.cloudAvailable) {
            _uiState.update { it.copy(welcome = true) }
            return true
        }
        val previous = Step.entries.getOrNull(state.step.ordinal - 1) ?: return false
        _uiState.update { it.copy(step = previous) }
        return true
    }

    fun setSplit(split: SplitType?) {
        _uiState.update { it.copy(splitOverride = split) }
        generate()
    }

    fun skip(onDone: () -> Unit) = viewModelScope.launchReporting(errors) {
        preferencesRepository.setOnboardingCompleted(true)
        onDone()
    }

    fun finish(onDone: () -> Unit) = viewModelScope.launchReporting(errors) {
        val state = _uiState.value
        val plan = state.generated?.plan ?: return@launchReporting
        val profile = state.answers.toProfile() ?: return@launchReporting
        _uiState.update { it.copy(saving = true) }
        profileRepository.save(profile)
        planRepository.saveAsActive(plan)
        preferencesRepository.setWeightUnit(profile.units)
        preferencesRepository.setOnboardingCompleted(true)
        onDone()
    }

    private fun generate() = viewModelScope.launchReporting(errors, Failure.ACTION) {
        val state = _uiState.value
        val profile = state.answers.toProfile() ?: return@launchReporting
        val library = exerciseRepository.observeAll().first()
        val generated = withContext(Dispatchers.Default) {
            PlanGenerator().generate(
                PlanInput(
                    profile = profile,
                    library = library,
                    today = clock.todayIn(TimeZone.currentSystemDefault()),
                    now = clock.now(),
                    knownLifts = state.answers.knownLifts(),
                    splitOverride = state.splitOverride,
                ),
            )
        }
        _uiState.update { it.copy(generated = generated) }
    }

    private fun Answers.toProfile(): UserProfile? = UserProfile(
        sex = sex ?: return null,
        heightCm = heightCmValue ?: return null,
        birthDate = clock.todayIn(TimeZone.currentSystemDefault()).minus((ageYears ?: return null).toLong(), DateTimeUnit.YEAR),
        bodyWeightKg = weightKg ?: return null,
        units = unit,
        experience = experience ?: return null,
        goal = goal ?: return null,
        daysPerWeek = days ?: return null,
        sessionLengthMinutes = minutes ?: return null,
        equipment = equipment,
        focusAreas = focus,
        limitations = limitations,
    )

    private fun UserProfile.toAnswers(): Answers {
        val imperial = units == WeightUnit.LB
        val totalInches = (heightCm / 2.54).roundToInt()
        return Answers(
            sex = sex,
            imperial = imperial,
            age = birthDate.yearsUntil(clock.todayIn(TimeZone.currentSystemDefault())).toString(),
            heightCm = heightCm.roundToInt().toString(),
            heightFt = (totalInches / 12).toString(),
            heightIn = (totalInches % 12).toString(),
            weight = units.format(bodyWeightKg),
            goal = goal,
            experience = experience,
            days = daysPerWeek,
            minutes = sessionLengthMinutes,
            equipment = equipment,
            focus = focusAreas,
            focusTouched = true,
            limitations = limitations,
        )
    }

    /**
     * The focus question opens with a default emphasis (lower body for women, upper
     * body for men), which is only a starting selection the user is free to change.
     */
    private fun Answers.withDefaultFocus(step: Step): Answers {
        if (step != Step.FOCUS || focusTouched) return this
        val defaults = when (sex) {
            Sex.FEMALE -> setOf(BodyArea.GLUTES, BodyArea.LEGS)
            Sex.MALE -> setOf(BodyArea.CHEST, BodyArea.ARMS)
            null -> emptySet()
        }
        return copy(focus = defaults, focusTouched = true)
    }
}
