package com.viktorolsson.spotter.core.data.repository

import androidx.room.withTransaction
import com.viktorolsson.spotter.core.data.db.SpotterDatabase
import com.viktorolsson.spotter.core.data.db.dao.PlanDao
import com.viktorolsson.spotter.core.data.db.entity.PlanDayEntity
import com.viktorolsson.spotter.core.data.db.entity.PlanEntity
import com.viktorolsson.spotter.core.data.db.entity.PlanExerciseEntity
import com.viktorolsson.spotter.core.data.db.toModel
import com.viktorolsson.spotter.core.model.Plan
import com.viktorolsson.spotter.core.model.PlanDay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class PlanRepository @Inject constructor(
    private val db: SpotterDatabase,
    private val dao: PlanDao,
) {
    fun observeActivePlan(): Flow<Plan?> = dao.observeActivePlan().map { it?.toModel() }

    /** The day after the last one completed, wrapping around; the first day for a fresh plan. */
    fun observeNextDay(): Flow<PlanDay?> = observeActivePlan().flatMapLatest { plan ->
        if (plan == null || plan.days.isEmpty()) return@flatMapLatest flowOf(null)
        dao.observeLastCompletedDayId(plan.id).map { lastDayId ->
            val lastIndex = plan.days.indexOfFirst { it.id == lastDayId }
            plan.days[(lastIndex + 1) % plan.days.size]
        }
    }

    /** Saves a generated plan and makes it the only active one. Returns the new plan id. */
    suspend fun saveAsActive(plan: Plan): Long = db.withTransaction {
        dao.deactivateAll()
        val planId = dao.insertPlan(
            PlanEntity(name = plan.name, splitType = plan.splitType, goal = plan.goal, createdAt = plan.createdAt, isActive = true),
        )
        plan.days.forEach { day ->
            val dayId = dao.insertDay(PlanDayEntity(planId = planId, position = day.position, name = day.name))
            dao.insertExercises(
                day.exercises.map {
                    PlanExerciseEntity(
                        planDayId = dayId,
                        exerciseId = it.exercise.id,
                        position = it.position,
                        sets = it.sets,
                        repMin = it.repMin,
                        repMax = it.repMax,
                        targetRir = it.targetRir,
                        restSeconds = it.restSeconds,
                        progressionRule = it.progressionRule,
                        supersetGroup = it.supersetGroup,
                        startingWeightKg = it.startingWeightKg,
                    )
                },
            )
        }
        planId
    }
}
