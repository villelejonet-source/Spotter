package com.viktorolsson.spotter.core.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.viktorolsson.spotter.core.model.Difficulty
import com.viktorolsson.spotter.core.model.Equipment
import com.viktorolsson.spotter.core.model.Mechanics
import com.viktorolsson.spotter.core.model.MovementPattern
import com.viktorolsson.spotter.core.model.Muscle

/** Seeded exercises use stable slug ids (e.g. `barbell-bench-press`); custom ones use `custom-<uuid>`. */
@Entity(
    tableName = "exercise",
    indices = [Index("movementPattern"), Index("name")],
)
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val primaryMuscles: List<Muscle>,
    val secondaryMuscles: List<Muscle>,
    val movementPattern: MovementPattern,
    val equipment: List<Equipment>,
    val mechanics: Mechanics,
    val difficulty: Difficulty,
    val unilateral: Boolean,
    val instructions: String?,
    val isCustom: Boolean,
)

@Entity(
    tableName = "exercise_similarity",
    primaryKeys = ["exerciseA", "exerciseB"],
    foreignKeys = [
        ForeignKey(ExerciseEntity::class, ["id"], ["exerciseA"], onDelete = ForeignKey.CASCADE),
        ForeignKey(ExerciseEntity::class, ["id"], ["exerciseB"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("exerciseB")],
)
data class ExerciseSimilarityEntity(
    val exerciseA: String,
    val exerciseB: String,
    val score: Double,
)
