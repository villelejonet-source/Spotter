package com.viktorolsson.spotter.core.engine

import com.viktorolsson.spotter.core.model.Difficulty
import com.viktorolsson.spotter.core.model.Equipment
import com.viktorolsson.spotter.core.model.Exercise
import com.viktorolsson.spotter.core.model.ExperienceLevel
import com.viktorolsson.spotter.core.model.Goal
import com.viktorolsson.spotter.core.model.Mechanics
import com.viktorolsson.spotter.core.model.MovementPattern
import com.viktorolsson.spotter.core.model.UserProfile

/**
 * Picks the best-fitting exercise for a slot. Hard filters: equipment the user has,
 * limitation exclusions, not already in the day. Everything else is a score; ties
 * break on exercise id, so the result never depends on how the library was loaded.
 */
internal class ExerciseSelector(
    private val profile: UserProfile,
    library: List<Exercise>,
    private val ageYears: Int,
) {
    private val excluded = profile.limitations.flatMap { Rules.excludedFor[it].orEmpty() }.toSet()
    private val bmi = profile.bodyWeightKg / ((profile.heightCm / 100) * (profile.heightCm / 100))

    private val pool: List<IndexedValue<Exercise>> = library.sortedBy { it.id }.withIndex().filter { (_, ex) ->
        !ex.isCustom && ex.id !in excluded && profile.equipment.containsAll(ex.equipment)
    }

    fun hasCandidates(pattern: MovementPattern) = pool.any { it.value.movementPattern == pattern }

    /**
     * Returns the pick, or null when no pattern in the slot can be filled with the
     * user's equipment. Patterns are tried in order, but a pattern whose best option
     * strains a limited area is skipped in favour of a later, comfortable fallback.
     */
    fun pick(slot: Slot, usedInDay: Set<String>, usedInPlan: Set<String>): Exercise? {
        val bestPerPattern = slot.patterns.mapNotNull { pattern ->
            pool.filter { it.value.movementPattern == pattern && it.value.id !in usedInDay }
                .maxWithOrNull(
                    compareBy<IndexedValue<Exercise>> { score(it.value, slot.role, usedInPlan) }
                        .thenByDescending { it.index },
                )
                ?.value
        }
        return bestPerPattern.firstOrNull { limitationPenalty(it) < STRAINS_LIMITATION } ?: bestPerPattern.firstOrNull()
    }

    private fun limitationPenalty(ex: Exercise) = profile.limitations.sumOf { Rules.limitationPenalty(it, ex) }

    fun score(ex: Exercise, role: SlotRole, usedInPlan: Set<String>): Double {
        var s = 0.0
        s += when (role) {
            SlotRole.MAIN -> if (ex.mechanics == Mechanics.COMPOUND) 3.0 else -3.0
            SlotRole.SECONDARY -> if (ex.mechanics == Mechanics.COMPOUND) 1.5 else 0.0
            SlotRole.ACCESSORY -> 0.0
        }
        if (role == SlotRole.MAIN && ex.unilateral) s -= 1.5
        if (role != SlotRole.MAIN && ex.id in Rules.mainLiftOnly) s -= 4.0
        s += equipmentScore(ex, role)
        s += when (profile.experience) {
            ExperienceLevel.NEW -> when (ex.difficulty) {
                Difficulty.BEGINNER -> 2.0; Difficulty.INTERMEDIATE -> 0.0; Difficulty.ADVANCED -> -6.0
            }
            ExperienceLevel.INTERMEDIATE -> when (ex.difficulty) {
                Difficulty.BEGINNER -> 1.0; Difficulty.INTERMEDIATE -> 1.0; Difficulty.ADVANCED -> -2.0
            }
            ExperienceLevel.ADVANCED -> when (ex.difficulty) {
                Difficulty.BEGINNER -> 0.0; Difficulty.INTERMEDIATE -> 1.0; Difficulty.ADVANCED -> 0.5
            }
        }
        if (ageYears >= 50) {
            if (ex.equipment.any { it in setOf(Equipment.MACHINE, Equipment.DUMBBELL, Equipment.CABLE) }) s += 1.5
            if (Equipment.BARBELL in ex.equipment) s -= 1.0
            if (ex.difficulty == Difficulty.ADVANCED) s -= 2.0
        }
        if (bmi >= HIGH_BMI && Rules.isBodyweightDependent(ex)) s -= 6.0
        s -= limitationPenalty(ex)
        if (ex.id in Rules.staples) s += 2.0
        if (ex.id in usedInPlan) s -= varietyPenalty(role)
        return s
    }

    /**
     * Different days get different variations of a movement, except main lifts for
     * beginners and strength goals, which progress best by repeating the same lift.
     * Accessories repeat freely.
     */
    private fun varietyPenalty(role: SlotRole): Double = when (role) {
        SlotRole.MAIN -> if (profile.experience == ExperienceLevel.NEW || profile.goal == Goal.STRENGTH) 0.0 else 4.0
        SlotRole.SECONDARY -> 4.0
        SlotRole.ACCESSORY -> 1.0
    }

    private fun equipmentScore(ex: Exercise, role: SlotRole): Double {
        val eq = ex.equipment
        if (ex.id in Rules.unloaded) return 0.5
        return when {
            Equipment.BARBELL in eq || Equipment.TRAP_BAR in eq -> when (role) {
                SlotRole.MAIN -> if (profile.goal == Goal.STRENGTH) 3.0 else 2.0
                SlotRole.SECONDARY -> 1.5
                SlotRole.ACCESSORY -> 0.5
            }
            Equipment.DUMBBELL in eq -> if (role == SlotRole.MAIN) 1.0 else 1.5
            Equipment.MACHINE in eq || Equipment.CABLE in eq -> if (role == SlotRole.MAIN) 1.0 else 1.5
            Equipment.EZ_BAR in eq -> 1.0
            Equipment.SMITH_MACHINE in eq || Equipment.KETTLEBELL in eq -> 0.5
            Equipment.RESISTANCE_BAND in eq -> -1.0
            else -> 0.5
        }
    }

    companion object {
        const val HIGH_BMI = 35.0
        private const val STRAINS_LIMITATION = 3.0
    }
}
