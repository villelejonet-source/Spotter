package com.viktorolsson.spotter.feature.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.viktorolsson.spotter.core.data.repository.ExerciseRepository
import com.viktorolsson.spotter.core.data.repository.PlanRepository
import com.viktorolsson.spotter.core.data.repository.UserPreferencesRepository
import com.viktorolsson.spotter.core.data.repository.UserProfileRepository
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
import com.viktorolsson.spotter.core.model.Sex
import com.viktorolsson.spotter.core.model.SplitType
import com.viktorolsson.spotter.core.model.UserProfile
import com.viktorolsson.spotter.core.model.WeightUnit
import com.viktorolsson.spotter.core.model.format
import com.viktorolsson.spotter.core.model.parseToKg
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.LocalDate
import java.time.Period
import javax.inject.Inject
import kotlin.math.roundToInt

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

    val ageYears: Int? get() = age.toIntOrNull()?.takeIf { it in 13..100 }

    val heightCmValue: Double?
        get() = if (imperial) {
            val ft = heightFt.toIntOrNull() ?: return null
            val inches = heightIn.toIntOrNull() ?: 0
            ((ft * 12 + inches) * 2.54).takeIf { it in 120.0..230.0 }
        } else {
            heightCm.replace(',', '.').toDoubleOrNull()?.takeIf { it in 120.0..230.0 }
        }

    val weightKg: Double? get() = unit.parseToKg(weight)?.takeIf { it in 30.0..300.0 }

    fun knownLifts(): KnownLifts {
        fun result(lift: Lift): LiftResult? {
            val input = lifts[lift] ?: return null
            val kg = unit.parseToKg(input.weight)?.takeIf { it > 0 } ?: return null
            val reps = input.reps.toIntOrNull()?.takeIf { it in 1..20 } ?: return null
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
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val exerciseRepository: ExerciseRepository,
    private val planRepository: PlanRepository,
    private val profileRepository: UserProfileRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val clock: Clock,
) : ViewModel() {
    private val rebuild = savedStateHandle.toRoute<OnboardingRoute>().rebuild
    private val _uiState = MutableStateFlow(OnboardingUiState(rebuild = rebuild))
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val profile = profileRepository.get() ?: return@launch
            _uiState.update { it.copy(answers = profile.toAnswers()) }
        }
    }

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

    /** Returns false when already on the first step (the caller then leaves). */
    fun back(): Boolean {
        val previous = Step.entries.getOrNull(_uiState.value.step.ordinal - 1) ?: return false
        _uiState.update { it.copy(step = previous) }
        return true
    }

    fun setSplit(split: SplitType?) {
        _uiState.update { it.copy(splitOverride = split) }
        generate()
    }

    fun skip(onDone: () -> Unit) = viewModelScope.launch {
        preferencesRepository.setOnboardingCompleted(true)
        onDone()
    }

    fun finish(onDone: () -> Unit) = viewModelScope.launch {
        val state = _uiState.value
        val plan = state.generated?.plan ?: return@launch
        val profile = state.answers.toProfile() ?: return@launch
        _uiState.update { it.copy(saving = true) }
        profileRepository.save(profile)
        planRepository.saveAsActive(plan)
        preferencesRepository.setWeightUnit(profile.units)
        preferencesRepository.setOnboardingCompleted(true)
        onDone()
    }

    private fun generate() = viewModelScope.launch {
        val state = _uiState.value
        val profile = state.answers.toProfile() ?: return@launch
        val library = exerciseRepository.observeAll().first()
        val generated = withContext(Dispatchers.Default) {
            PlanGenerator().generate(
                PlanInput(
                    profile = profile,
                    library = library,
                    today = LocalDate.now(clock),
                    now = clock.instant(),
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
        birthDate = LocalDate.now(clock).minusYears((ageYears ?: return null).toLong()),
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
            age = Period.between(birthDate, LocalDate.now(clock)).years.toString(),
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
