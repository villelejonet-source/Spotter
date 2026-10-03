package com.viktorolsson.spotter.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.viktorolsson.spotter.core.data.db.entity.PlanDayEntity
import com.viktorolsson.spotter.core.data.db.entity.PlanDayWithExercises
import com.viktorolsson.spotter.core.data.db.entity.PlanEntity
import com.viktorolsson.spotter.core.data.db.entity.PlanExerciseEntity
import com.viktorolsson.spotter.core.data.db.entity.PlanWithDays
import kotlinx.coroutines.flow.Flow

@Dao
interface PlanDao {
    @Insert
    suspend fun insertPlan(plan: PlanEntity): Long

    @Insert
    suspend fun insertDay(day: PlanDayEntity): Long

    @Insert
    suspend fun insertExercises(exercises: List<PlanExerciseEntity>)

    /** Old plans are kept (history links to their days) but only one is active. */
    @Query("UPDATE plan SET isActive = 0 WHERE isActive = 1")
    suspend fun deactivateAll()

    @Transaction
    @Query("SELECT * FROM plan WHERE isActive = 1 ORDER BY createdAt DESC LIMIT 1")
    fun observeActivePlan(): Flow<PlanWithDays?>

    @Transaction
    @Query("SELECT * FROM plan_day WHERE id = :planDayId")
    suspend fun getDay(planDayId: Long): PlanDayWithExercises?

    /** "Replace in plan" from a swap: future sessions use the new exercise. */
    @Query("UPDATE plan_exercise SET exerciseId = :exerciseId, startingWeightKg = :startingWeightKg WHERE id = :planExerciseId")
    suspend fun replaceExercise(planExerciseId: Long, exerciseId: String, startingWeightKg: Double?)

    @Transaction
    @Query("SELECT * FROM plan WHERE isActive = 1 ORDER BY createdAt DESC LIMIT 1")
    suspend fun getActivePlan(): PlanWithDays?

    @Query("SELECT COALESCE(MAX(position) + 1, 0) FROM plan_exercise WHERE planDayId = :planDayId")
    suspend fun nextExercisePosition(planDayId: Long): Int

    @Query("UPDATE plan_exercise SET sets = sets + 1 WHERE id IN (:planExerciseIds)")
    suspend fun addOneSet(planExerciseIds: List<Long>)

    @Query("UPDATE plan_exercise SET sets = :sets, repMin = :repMin, repMax = :repMax WHERE id = :planExerciseId")
    suspend fun setRepRange(planExerciseId: Long, sets: Int, repMin: Int, repMax: Int)

    /** The plan day of the most recently finished workout in [planId], if any. */
    @Query(
        """
        SELECT ws.planDayId FROM workout_session ws
        JOIN plan_day pd ON pd.id = ws.planDayId
        WHERE pd.planId = :planId AND ws.endedAt IS NOT NULL
        ORDER BY ws.startedAt DESC LIMIT 1
        """,
    )
    fun observeLastCompletedDayId(planId: Long): Flow<Long?>
}
