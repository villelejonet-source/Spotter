package com.viktorolsson.spotter.core.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.viktorolsson.spotter.core.model.Goal
import com.viktorolsson.spotter.core.model.ProgressionRule
import com.viktorolsson.spotter.core.model.SplitType
import java.time.Instant

@Entity(tableName = "plan")
data class PlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val splitType: SplitType,
    val goal: Goal,
    val createdAt: Instant,
    val isActive: Boolean,
)

@Entity(
    tableName = "plan_day",
    foreignKeys = [ForeignKey(PlanEntity::class, ["id"], ["planId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("planId")],
)
data class PlanDayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val position: Int,
    val name: String,
)

@Entity(
    tableName = "plan_exercise",
    foreignKeys = [
        ForeignKey(PlanDayEntity::class, ["id"], ["planDayId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(ExerciseEntity::class, ["id"], ["exerciseId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("planDayId"), Index("exerciseId")],
)
data class PlanExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planDayId: Long,
    val exerciseId: String,
    val position: Int,
    val sets: Int,
    val repMin: Int,
    val repMax: Int,
    val targetRir: Int?,
    val restSeconds: Int,
    val progressionRule: ProgressionRule,
    /** Exercises sharing a group number in the same day form a superset. */
    val supersetGroup: Int?,
)
