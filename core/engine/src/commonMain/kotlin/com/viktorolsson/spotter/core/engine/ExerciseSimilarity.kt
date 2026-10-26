package com.viktorolsson.spotter.core.engine

import com.viktorolsson.spotter.core.model.Equipment
import com.viktorolsson.spotter.core.model.Exercise
import com.viktorolsson.spotter.core.model.Limitation

data class SwapCandidate(val exercise: Exercise, val score: Double)

/**
 * Ranks alternatives for an exercise whose equipment is busy or missing. Same movement
 * pattern weighs most, then overlap in primary (and a little secondary) muscles, then
 * matching compound/isolation. Smaller nudges: keep a loaded lift loaded, prefer other
 * equipment (the original's is probably the busy one) and nothing harder than the
 * original. Exercises the user lacks equipment for, or that their limitations exclude,
 * are left out; milder limitation conflicts rank lower.
 */
object ExerciseSimilarity {
    private const val SAME_PATTERN = 10.0
    private const val PRIMARY_OVERLAP = 5.0
    private const val SECONDARY_OVERLAP = 1.0
    private const val SAME_MECHANICS = 2.0
    private const val STAPLE = 1.0
    private const val MIN_SCORE = 4.0

    fun rank(
        original: Exercise,
        library: List<Exercise>,
        availableEquipment: Set<Equipment>,
        limitations: Set<Limitation> = emptySet(),
        exclude: Set<String> = emptySet(),
    ): List<SwapCandidate> {
        val banned = limitations.flatMap { Rules.excludedFor[it].orEmpty() }.toSet()
        return library.asSequence()
            .filter { it.id != original.id && it.id !in exclude && it.id !in banned }
            .filter { availableEquipment.containsAll(it.equipment) }
            .map { SwapCandidate(it, score(original, it, limitations)) }
            .filter { it.score >= MIN_SCORE }
            .sortedWith(compareByDescending<SwapCandidate> { it.score }.thenBy { it.exercise.id })
            .toList()
    }

    fun score(original: Exercise, candidate: Exercise, limitations: Set<Limitation> = emptySet()): Double {
        var s = 0.0
        if (candidate.movementPattern == original.movementPattern) s += SAME_PATTERN
        s += PRIMARY_OVERLAP * jaccard(original.primaryMuscles.toSet(), candidate.primaryMuscles.toSet())
        s += SECONDARY_OVERLAP * jaccard(
            (original.primaryMuscles + original.secondaryMuscles).toSet(),
            (candidate.primaryMuscles + candidate.secondaryMuscles).toSet(),
        )
        if (candidate.mechanics == original.mechanics) s += SAME_MECHANICS
        if (candidate.id in Rules.staples) s += STAPLE
        if (candidate.unilateral != original.unilateral) s -= 0.5
        if (LoadFactors.equipment(original) != null && LoadFactors.equipment(candidate) == null) s -= 1.5
        if (candidate.equipment.toSet() == original.equipment.toSet()) s -= 1.0
        s -= (candidate.difficulty.ordinal - original.difficulty.ordinal).coerceAtLeast(0)
        s -= limitations.sumOf { Rules.limitationPenalty(it, candidate) }
        return s
    }

    private fun <T> jaccard(a: Set<T>, b: Set<T>): Double =
        if (a.isEmpty() && b.isEmpty()) 0.0 else a.intersect(b).size.toDouble() / a.union(b).size
}
