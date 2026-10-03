package com.viktorolsson.spotter.core.engine

import com.viktorolsson.spotter.core.model.BodyWeightEntry
import com.viktorolsson.spotter.core.model.BodyWeightPoint
import com.viktorolsson.spotter.core.model.Exercise
import com.viktorolsson.spotter.core.model.Goal
import com.viktorolsson.spotter.core.model.MuscleGroup
import com.viktorolsson.spotter.core.model.MuscleVolume

object MuscleBalance {
    /** Weekly working sets per muscle group, from the plan's goal table. */
    fun target(goal: Goal?): IntRange = when (goal) {
        Goal.HYPERTROPHY -> 10..20
        Goal.STRENGTH, Goal.GENERAL_FITNESS, Goal.FAT_LOSS, null -> 8..12
    }

    /**
     * [sets]: each completed working set's exercise. A set counts once for each group
     * its primary muscles belong to and half for groups only hit by secondary muscles.
     */
    fun weekly(sets: List<Exercise>, goal: Goal?): List<MuscleVolume> {
        val totals = MuscleGroup.entries.associateWith { 0.0 }.toMutableMap()
        sets.forEach { exercise ->
            val primary = exercise.primaryMuscles.map(MuscleGroup::of).toSet()
            val secondary = exercise.secondaryMuscles.map(MuscleGroup::of).toSet() - primary
            primary.forEach { totals[it] = totals.getValue(it) + 1.0 }
            secondary.forEach { totals[it] = totals.getValue(it) + 0.5 }
        }
        val target = target(goal)
        return totals.map { (group, count) -> MuscleVolume(group, count, target.first, target.last) }
    }
}

object BodyWeightTrend {
    /** Each entry with the mean of all entries in the 7 days ending on its date. */
    fun points(entries: List<BodyWeightEntry>): List<BodyWeightPoint> {
        val sorted = entries.sortedBy { it.date }
        return sorted.map { entry ->
            val window = sorted.filter { !it.date.isAfter(entry.date) && it.date.isAfter(entry.date.minusDays(7)) }
            BodyWeightPoint(entry.date, entry.weightKg, window.map { it.weightKg }.average())
        }
    }
}
