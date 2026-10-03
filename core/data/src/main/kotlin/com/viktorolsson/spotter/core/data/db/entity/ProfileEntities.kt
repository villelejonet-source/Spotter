package com.viktorolsson.spotter.core.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.viktorolsson.spotter.core.model.Equipment
import com.viktorolsson.spotter.core.model.ExperienceLevel
import com.viktorolsson.spotter.core.model.Goal
import com.viktorolsson.spotter.core.model.Limitation
import com.viktorolsson.spotter.core.model.Muscle
import com.viktorolsson.spotter.core.model.Sex
import com.viktorolsson.spotter.core.model.WeightUnit
import java.time.LocalDate

/** Single-user app: the profile is always the row with [id] = [SINGLETON_ID]. */
@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
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
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}

@Entity(tableName = "body_weight_entry", indices = [Index("date", unique = true)])
data class BodyWeightEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val weightKg: Double,
)
