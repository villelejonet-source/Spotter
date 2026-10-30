package com.viktorolsson.spotter.core.data.repository

import com.viktorolsson.spotter.core.data.db.SpotterDatabase
import com.viktorolsson.spotter.core.data.db.dao.ExerciseDao
import com.viktorolsson.spotter.core.data.db.dao.PlanDao
import com.viktorolsson.spotter.core.data.db.dao.RecommendationDao
import com.viktorolsson.spotter.core.data.db.dao.WorkoutDao
import com.viktorolsson.spotter.core.data.db.entity.PlanExerciseEntity
import com.viktorolsson.spotter.core.data.db.entity.RecommendationEntity
import com.viktorolsson.spotter.core.data.db.toModel
import com.viktorolsson.spotter.core.data.db.withTransaction
import com.viktorolsson.spotter.core.engine.AppliedVariation
import com.viktorolsson.spotter.core.engine.ExerciseExposure
import com.viktorolsson.spotter.core.engine.LoggedSet
import com.viktorolsson.spotter.core.engine.PlanSlot
import com.viktorolsson.spotter.core.engine.RecommendationContext
import com.viktorolsson.spotter.core.engine.RecommendationDraft
import com.viktorolsson.spotter.core.engine.RecommendationEngine
import com.viktorolsson.spotter.core.engine.WeightConversion
import com.viktorolsson.spotter.core.model.Equipment
import com.viktorolsson.spotter.core.model.ProgressionRule
import com.viktorolsson.spotter.core.model.Recommendation
import com.viktorolsson.spotter.core.model.RecommendationAction
import com.viktorolsson.spotter.core.model.RecommendationPayload
import com.viktorolsson.spotter.core.model.RecommendationStatus
import com.viktorolsson.spotter.core.model.RecommendationType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * Runs the recommendation engine over the active plan and history after each workout,
 * keeps one card per suggestion, and applies accepted ones to the plan.
 */
class RecommendationRepository(
    private val db: SpotterDatabase,
    private val dao: RecommendationDao,
    private val workoutDao: WorkoutDao,
    private val planDao: PlanDao,
    private val exerciseDao: ExerciseDao,
    private val profiles: UserProfileRepository,
    private val history: HistoryRepository,
    private val preferences: UserPreferencesRepository,
    private val clock: Clock,
) {
    fun observeActive(): Flow<List<Recommendation>> = dao.observeActive().map { rows -> rows.map { it.toModel() } }

    /**
     * Re-evaluates and syncs cards: new suggestions are added, active ones that no longer
     * apply are removed, and anything dismissed or applied recently isn't repeated.
     */
    suspend fun refresh() {
        db.withTransaction {
            // One read of the recommendation table serves both the engine's context and the diff below.
            val existing = dao.getAll()
            val context = buildContext(existing) ?: return@withTransaction
            val drafts = RecommendationEngine.evaluate(context)
            val now = clock.now()
            val active = existing.filter { it.status == RecommendationStatus.ACTIVE }.associateBy { it.dedupKey }
            val byKey = drafts.associateBy(::keyOf)
            // Cards whose problem went away, or whose best fix changed, are replaced.
            val outdated = active.values.filter { rec -> byKey[rec.dedupKey]?.payload?.encode() != rec.payload }
            if (outdated.isNotEmpty()) dao.delete(outdated.map { it.id })
            val coolingDown = existing
                .filter { it.resolvedAt != null && (now - it.resolvedAt) < COOLDOWN }
                .map { it.dedupKey }.toSet()
            val keep = active.keys - outdated.map { it.dedupKey }.toSet()
            val fresh = byKey.filterKeys { it !in coolingDown && it !in keep }
            dao.insert(
                fresh.map { (key, draft) ->
                    RecommendationEntity(
                        type = draft.type,
                        exerciseId = draft.exerciseId,
                        payload = draft.payload.encode(),
                        createdAt = now,
                        status = RecommendationStatus.ACTIVE,
                        dedupKey = key,
                    )
                },
            )
        }
    }

    /** Dismisses, then re-evaluates: e.g. dismissing a deload lets held-back per-lift fixes through. */
    suspend fun dismiss(id: Long) {
        dao.resolve(id, RecommendationStatus.DISMISSED, clock.now())
        refresh()
    }

    /** Applies the suggestion to the plan (or starts a deload week) and marks it applied. */
    suspend fun apply(id: Long) {
        val rec = dao.get(id)?.toModel() ?: return
        val unit = preferences.preferences.first().weightUnit
        db.withTransaction {
            when (val action = rec.payload.action) {
                is RecommendationAction.AddExercise -> planDao.insertExercises(
                    listOf(
                        PlanExerciseEntity(
                            planDayId = action.planDayId,
                            exerciseId = action.exerciseId,
                            position = planDao.nextExercisePosition(action.planDayId),
                            sets = action.sets,
                            repMin = action.repMin,
                            repMax = action.repMax,
                            targetRir = action.targetRir,
                            restSeconds = action.restSeconds,
                            progressionRule = ProgressionRule.DOUBLE_PROGRESSION,
                            supersetGroup = null,
                        ),
                    ),
                )
                is RecommendationAction.AddSets -> planDao.addOneSet(action.planExerciseIds)
                is RecommendationAction.ChangeRepRange ->
                    planDao.setRepRange(action.planExerciseId, action.sets, action.repMin, action.repMax)
                is RecommendationAction.SwapVariation -> {
                    val from = exerciseDao.getById(action.fromExerciseId)?.toModel()
                    val to = exerciseDao.getById(action.toExerciseId)?.toModel()
                    val lastKg = workoutDao.getRecentWorkingSets(action.fromExerciseId, sessions = 1).mapNotNull { it.set.weightKg }.maxOrNull()
                    val start = if (from != null && to != null && lastKg != null) WeightConversion.convert(from, lastKg, to, unit) else null
                    planDao.replaceExercise(action.planExerciseId, action.toExerciseId, start)
                }
                is RecommendationAction.Deload ->
                    preferences.setDeloadUntil(clock.todayIn(TimeZone.currentSystemDefault()).plus(action.days - 1L, DateTimeUnit.DAY))
            }
            dao.resolve(id, RecommendationStatus.APPLIED, clock.now())
        }
        refresh()
    }

    private suspend fun buildContext(all: List<RecommendationEntity>): RecommendationContext? {
        val plan = planDao.getActivePlan() ?: return null
        val zone = TimeZone.currentSystemDefault()
        val today = clock.todayIn(zone)
        val profile = profiles.get()
        val slots = plan.days.sortedBy { it.day.position }.flatMap { day ->
            day.exercises.sortedBy { it.planExercise.position }.map { (pe, exercise) ->
                PlanSlot(pe.id, day.day.id, day.day.name, exercise.toModel(), pe.sets, pe.repMin, pe.repMax, pe.targetRir, pe.progressionRule)
            }
        }
        val historyByExercise = slots.map { it.exercise.id }.distinct().associateWith { exerciseId ->
            workoutDao.getExposureSets(exerciseId).groupBy { it.sessionId }.values.map { sets ->
                ExerciseExposure(
                    sets.first().startedAt.toLocalDateTime(zone).date,
                    sets.map { LoggedSet(it.set.weightKg, it.set.reps ?: 0, it.set.rir) },
                )
            }
        }
        val prefs = preferences.preferences.first()
        val lastDeload = listOfNotNull(
            prefs.deloadUntil?.takeIf { it <= today },
            workoutDao.getLastDeloadSession()?.toLocalDateTime(zone)?.date,
        ).maxOrNull()
        val deloadRecentlyHandled = all.any {
            it.type == RecommendationType.DELOAD && it.resolvedAt != null && (clock.now() - it.resolvedAt) < COOLDOWN
        }
        val applied = all
            .filter { it.status == RecommendationStatus.APPLIED && it.type == RecommendationType.VARIATION }
            .mapNotNull { rec ->
                val action = RecommendationPayload.decode(rec.payload).action as? RecommendationAction.SwapVariation
                val on = rec.resolvedAt?.toLocalDateTime(zone)?.date
                if (action != null && on != null) AppliedVariation(action, on) else null
            }
        return RecommendationContext(
            today = today,
            plan = slots,
            planDays = plan.days.sortedBy { it.day.position }.map { it.day.id to it.day.name },
            history = historyByExercise,
            trainingDates = workoutDao.getTrainingStarts().map { it.toLocalDateTime(zone).date },
            lastDeloadEnd = lastDeload,
            weeklyVolume = history.observeWeeklyVolume(profile?.goal).first(),
            recentRir = workoutDao.getRirSince(clock.now().minus(14.days)),
            library = exerciseDao.observeAll().first().map { it.toModel() },
            equipment = profile?.equipment ?: Equipment.entries.toSet(),
            limitations = profile?.limitations.orEmpty(),
            appliedVariations = applied,
            deloadActive = prefs.isDeload(today),
            deloadRecentlyHandled = deloadRecentlyHandled,
        )
    }

    /**
     * What counts as "the same suggestion" for de-duplication and the cooldown: any
     * fix for a stalled lift (one per lift at a time), a frequency fix per muscle group,
     * a switch back per plan slot, and the deload.
     */
    private fun keyOf(draft: RecommendationDraft): String = when (val action = draft.payload.action) {
        is RecommendationAction.Deload -> "deload"
        is RecommendationAction.SwapVariation if action.returning -> "return|${action.planExerciseId}"
        else -> when (draft.type) {
            RecommendationType.INCREASE_FREQUENCY -> "frequency|${draft.payload.evidence.group}"
            else -> "plateau|${draft.exerciseId}"
        }
    }

    private companion object {
        val COOLDOWN: Duration = 28.days
    }
}
