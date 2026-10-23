package com.viktorolsson.spotter.core.engine

import com.viktorolsson.spotter.core.model.Exercise
import com.viktorolsson.spotter.core.model.ExperienceLevel
import com.viktorolsson.spotter.core.model.Goal
import com.viktorolsson.spotter.core.model.MovementPattern
import com.viktorolsson.spotter.core.model.Plan
import com.viktorolsson.spotter.core.model.PlanDay
import com.viktorolsson.spotter.core.model.PlanExercise
import com.viktorolsson.spotter.core.model.ProgressionRule
import com.viktorolsson.spotter.core.model.Sex
import com.viktorolsson.spotter.core.model.SplitType
import com.viktorolsson.spotter.core.model.UserProfile
import com.viktorolsson.spotter.core.model.isTimed
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import kotlin.math.ceil

data class PlanInput(
    val profile: UserProfile,
    val library: List<Exercise>,
    val today: LocalDate,
    val now: Instant,
    val knownLifts: KnownLifts = KnownLifts(),
    /** FULL_BODY, UPPER_LOWER or PUSH_PULL_LEGS to override the automatic choice. */
    val splitOverride: SplitType? = null,
)

data class GeneratedPlan(
    val plan: Plan,
    /** Estimated minutes per day, same order as [Plan.days]. */
    val estimatedMinutes: List<Int>,
    /** Movement patterns that couldn't be programmed with the user's equipment. */
    val unfilledPatterns: Set<MovementPattern>,
)

/**
 * Builds a plan from questionnaire answers. Deterministic: the same input always
 * gives the same plan. Steps: choose the split, fill each day's movement-pattern
 * slots, add focus-area accessories, apply goal/experience/sex/age parameters,
 * then trim lowest-priority work until each day fits the session length.
 */
class PlanGenerator {

    fun generate(input: PlanInput): GeneratedPlan {
        val profile = input.profile
        val age = Period.between(profile.birthDate, input.today).years
        val selector = ExerciseSelector(profile, input.library, age)
        val weights = StartingWeights(profile, input.knownLifts, age)
        val split = chooseSplit(profile.daysPerWeek, profile.experience, input.splitOverride)
        val templates = dayTemplates(split, profile.daysPerWeek)

        val usedInPlan = mutableSetOf<String>()
        val unfilled = mutableSetOf<MovementPattern>()
        val builtByKey = mutableMapOf<String, List<Planned>>()

        val days = templates.mapIndexed { index, template ->
            val planned = builtByKey.getOrPut(template.key) {
                buildDay(template, profile, age, selector, weights, usedInPlan, unfilled)
                    .also { day -> usedInPlan += day.map { it.exercise.id } }
            }
            PlanDay(
                id = 0,
                position = index,
                name = template.name,
                exercises = planned.mapIndexed { i, p -> p.toPlanExercise(i) },
            )
        }
        val plan = Plan(
            id = 0,
            name = planName(split, profile.daysPerWeek),
            splitType = split,
            goal = profile.goal,
            createdAt = input.now,
            days = days,
        )
        return GeneratedPlan(plan, days.map { estimateMinutes(it.exercises) }, unfilled)
    }

    private class Planned(
        val exercise: Exercise,
        val role: SlotRole,
        val sets: Int,
        val repMin: Int,
        val repMax: Int,
        val rir: Int,
        val rest: Int,
        val progression: ProgressionRule,
        val startingWeightKg: Double?,
        var supersetGroup: Int? = null,
    ) {
        fun toPlanExercise(position: Int) = PlanExercise(
            id = 0,
            exercise = exercise,
            position = position,
            sets = sets,
            repMin = repMin,
            repMax = repMax,
            targetRir = rir,
            restSeconds = rest,
            progressionRule = progression,
            supersetGroup = supersetGroup,
            startingWeightKg = startingWeightKg,
        )
    }

    private fun buildDay(
        template: DayTemplate,
        profile: UserProfile,
        age: Int,
        selector: ExerciseSelector,
        weights: StartingWeights,
        usedInPlan: Set<String>,
        unfilled: MutableSet<MovementPattern>,
    ): List<Planned> {
        // Priority order: mains, secondaries, focus accessories, then regular accessories.
        val usedInDay = mutableSetOf<String>()
        fun fill(slots: List<Slot>): List<Planned> = slots.mapNotNull { slot ->
            val exercise = selector.pick(slot, usedInDay, usedInPlan)
            if (exercise == null) {
                // Missing accessories are fine; a missing main movement is worth telling the user about.
                if (slot.role != SlotRole.ACCESSORY) unfilled += slot.patterns.first()
                return@mapNotNull null
            }
            usedInDay += exercise.id
            prescribe(exercise, slot.role, profile, age, weights)
        }

        val base = fill(template.slots.filter { it.role != SlotRole.ACCESSORY })
        val accessorySlots = template.slots.filter { it.role == SlotRole.ACCESSORY }
        // Focus areas add work the day doesn't already have, judged by what was actually picked.
        val covered = (base.map { it.exercise.movementPattern } + accessorySlots.map { it.patterns.first() }).toSet()
        val focusSlots = profile.focusAreas.sorted()
            .filter { template.region in Rules.focusRegions.getValue(it) }
            .mapNotNull { area ->
                Rules.focusPatterns.getValue(area)
                    .filter { it !in covered && selector.hasCandidates(it) }
                    .takeIf { it.isNotEmpty() }
                    ?.let { Slot(SlotRole.ACCESSORY, it) }
            }
            .take(MAX_FOCUS_SLOTS_PER_DAY)
        val planned = (base + fill(focusSlots) + fill(accessorySlots)).toMutableList()

        if (profile.goal == Goal.FAT_LOSS) pairAccessoriesIntoSupersets(planned)

        // Trim from the lowest priority end until the day fits, keeping at least three exercises.
        val budget = if (profile.sessionLengthMinutes >= 75) 90 else profile.sessionLengthMinutes
        while (planned.size > MIN_EXERCISES && estimateMinutes(planned.map { it.toPlanExercise(0) }) > budget) {
            planned.removeAt(planned.lastIndex)
            if (profile.goal == Goal.FAT_LOSS) pairAccessoriesIntoSupersets(planned)
        }
        return planned
    }

    private fun prescribe(
        exercise: Exercise,
        role: SlotRole,
        profile: UserProfile,
        age: Int,
        weights: StartingWeights,
    ): Planned {
        val base = prescription(profile.goal, role)
        var rir = base.targetRir
        if (profile.experience == ExperienceLevel.NEW) rir += 1
        if (age >= 50) rir += 1
        rir = rir.coerceAtMost(3)

        var rest = base.restSeconds
        var repMax = base.repMax
        if (profile.sex == Sex.FEMALE) {
            // Modest evidence of faster between-set recovery and higher rep tolerance.
            rest = (rest - 15).coerceAtLeast(45)
            if (role != SlotRole.MAIN) repMax = (repMax + 2).coerceAtMost(15).coerceAtLeast(base.repMax)
        }

        if (exercise.isTimed) {
            val seconds = timedHoldSeconds(profile.experience)
            return Planned(
                exercise = exercise,
                role = role,
                sets = base.sets(profile.experience),
                repMin = seconds.first,
                repMax = seconds.last,
                rir = rir,
                rest = rest,
                progression = ProgressionRule.DOUBLE_PROGRESSION,
                startingWeightKg = null,
            )
        }

        val progression = when {
            role == SlotRole.MAIN && (profile.goal == Goal.STRENGTH || profile.experience == ExperienceLevel.NEW) ->
                ProgressionRule.LINEAR
            else -> ProgressionRule.DOUBLE_PROGRESSION
        }
        return Planned(
            exercise = exercise,
            role = role,
            sets = base.sets(profile.experience),
            repMin = base.repMin,
            repMax = repMax,
            rir = rir,
            rest = rest,
            progression = progression,
            startingWeightKg = weights.estimateKg(exercise, base.repMin, rir),
        )
    }

    /** Fat loss: accessories run as back-to-back pairs to keep the session dense. */
    private fun pairAccessoriesIntoSupersets(planned: List<Planned>) {
        planned.forEach { it.supersetGroup = null }
        planned.filter { it.role == SlotRole.ACCESSORY }.chunked(2)
            .filter { it.size == 2 }
            .forEachIndexed { i, pair -> pair.forEach { it.supersetGroup = i + 1 } }
    }

    companion object {
        private const val MIN_EXERCISES = 3
        private const val MAX_FOCUS_SLOTS_PER_DAY = 2
        private const val WORK_SECONDS_PER_SET = 40
        private const val WARM_UP_MINUTES = 5

        fun chooseSplit(days: Int, experience: ExperienceLevel, override: SplitType?): SplitType = when (override) {
            SplitType.FULL_BODY -> SplitType.FULL_BODY
            SplitType.UPPER_LOWER -> SplitType.UPPER_LOWER
            SplitType.PUSH_PULL_LEGS -> if (days >= 3) SplitType.PUSH_PULL_LEGS else SplitType.UPPER_LOWER
            else -> when {
                days <= 3 -> SplitType.FULL_BODY
                days == 4 -> SplitType.UPPER_LOWER
                days == 5 -> if (experience == ExperienceLevel.ADVANCED) SplitType.PPL_UPPER_LOWER else SplitType.UPPER_LOWER_FULL
                else -> SplitType.PUSH_PULL_LEGS
            }
        }

        internal fun dayTemplates(split: SplitType, days: Int): List<DayTemplate> {
            val cycle = when (split) {
                SplitType.FULL_BODY -> listOf(Templates.fullA, Templates.fullB, Templates.fullC)
                SplitType.UPPER_LOWER -> listOf(Templates.upperA, Templates.lowerA, Templates.upperB, Templates.lowerB)
                SplitType.PUSH_PULL_LEGS -> listOf(Templates.push, Templates.pull, Templates.legs)
                SplitType.UPPER_LOWER_FULL ->
                    listOf(Templates.upperA, Templates.lowerA, Templates.fullA, Templates.upperB, Templates.lowerB)
                SplitType.PPL_UPPER_LOWER ->
                    listOf(Templates.push, Templates.pull, Templates.legs, Templates.upperA, Templates.lowerB)
                SplitType.CUSTOM -> error("Custom plans aren't generated")
            }
            return List(days) { cycle[it % cycle.size] }
        }

        /** Work time per set plus rest; a superset round rests once after its last exercise. */
        fun estimateMinutes(exercises: List<PlanExercise>): Int {
            var seconds = WARM_UP_MINUTES * 60.0
            val (grouped, single) = exercises.partition { it.supersetGroup != null }
            single.forEach { seconds += it.sets * (WORK_SECONDS_PER_SET + it.restSeconds) }
            grouped.groupBy { it.supersetGroup }.values.forEach { group ->
                val rounds = group.maxOf { it.sets }
                seconds += rounds * (WORK_SECONDS_PER_SET * group.size + group.maxOf { it.restSeconds })
            }
            return ceil(seconds / 60).toInt()
        }

        private fun planName(split: SplitType, days: Int): String {
            val name = when (split) {
                SplitType.FULL_BODY -> "Full body"
                SplitType.UPPER_LOWER -> "Upper / Lower"
                SplitType.PUSH_PULL_LEGS -> "Push / Pull / Legs"
                SplitType.UPPER_LOWER_FULL -> "Upper / Lower + Full body"
                SplitType.PPL_UPPER_LOWER -> "Push / Pull / Legs + Upper / Lower"
                SplitType.CUSTOM -> "Custom"
            }
            return "$name · $days days"
        }
    }
}
