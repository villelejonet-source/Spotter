package com.viktorolsson.spotter.core.model

import java.time.LocalDate

data class UserProfile(
    val sex: Sex,
    val heightCm: Double,
    val birthDate: LocalDate,
    val bodyWeightKg: Double,
    val units: WeightUnit,
    val experience: ExperienceLevel,
    val goal: Goal,
    val daysPerWeek: Int,
    val sessionLengthMinutes: Int,
    val equipment: Set<Equipment>,
    val focusAreas: Set<Muscle>,
    val limitations: Set<Limitation>,
)

data class BodyWeightEntry(
    val id: Long,
    val date: LocalDate,
    val weightKg: Double,
)

enum class Sex { FEMALE, MALE }

enum class WeightUnit { KG, LB }

enum class ExperienceLevel { NEW, INTERMEDIATE, ADVANCED }

enum class Goal { STRENGTH, HYPERTROPHY, GENERAL_FITNESS, FAT_LOSS }

enum class Limitation { SHOULDER, KNEE, LOWER_BACK, WRIST }
