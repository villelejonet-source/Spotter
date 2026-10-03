package com.viktorolsson.spotter.core.engine

import com.viktorolsson.spotter.core.model.ExperienceLevel
import com.viktorolsson.spotter.core.model.Goal

/** Sets are a range; experience picks within it (new → low end, advanced → high end). */
data class Prescription(
    val setsLow: Int,
    val setsHigh: Int,
    val repMin: Int,
    val repMax: Int,
    val targetRir: Int,
    val restSeconds: Int,
) {
    fun sets(experience: ExperienceLevel): Int = when (experience) {
        ExperienceLevel.NEW -> setsLow
        ExperienceLevel.INTERMEDIATE -> (setsLow + setsHigh) / 2
        ExperienceLevel.ADVANCED -> setsHigh
    }
}

/**
 * Goal parameters from the plan. Secondary compounds use the main lift's rep range
 * with accessory-level sets and a little less rest.
 */
internal fun prescription(goal: Goal, role: SlotRole): Prescription = when (goal) {
    Goal.STRENGTH -> when (role) {
        SlotRole.MAIN -> Prescription(3, 5, 3, 6, targetRir = 2, restSeconds = 180)
        SlotRole.SECONDARY -> Prescription(3, 4, 5, 8, targetRir = 2, restSeconds = 150)
        SlotRole.ACCESSORY -> Prescription(3, 3, 6, 10, targetRir = 2, restSeconds = 120)
    }
    Goal.HYPERTROPHY -> when (role) {
        SlotRole.MAIN -> Prescription(3, 4, 6, 12, targetRir = 1, restSeconds = 150)
        SlotRole.SECONDARY -> Prescription(3, 4, 8, 12, targetRir = 1, restSeconds = 120)
        SlotRole.ACCESSORY -> Prescription(3, 3, 10, 15, targetRir = 1, restSeconds = 90)
    }
    Goal.GENERAL_FITNESS -> when (role) {
        SlotRole.MAIN -> Prescription(3, 3, 8, 12, targetRir = 2, restSeconds = 120)
        SlotRole.SECONDARY -> Prescription(3, 3, 8, 12, targetRir = 2, restSeconds = 90)
        SlotRole.ACCESSORY -> Prescription(2, 3, 10, 15, targetRir = 2, restSeconds = 75)
    }
    Goal.FAT_LOSS -> when (role) {
        SlotRole.MAIN -> Prescription(3, 3, 8, 12, targetRir = 2, restSeconds = 90)
        SlotRole.SECONDARY -> Prescription(3, 3, 10, 12, targetRir = 2, restSeconds = 75)
        SlotRole.ACCESSORY -> Prescription(2, 3, 12, 15, targetRir = 2, restSeconds = 60)
    }
}
