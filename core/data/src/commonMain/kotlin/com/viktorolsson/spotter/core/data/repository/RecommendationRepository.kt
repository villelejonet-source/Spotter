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
import com.viktorolsson.spotter.core.engine.PlanReview
import com.viktorolsson.spotter.core.engine.PlanReviewEngine
import com.viktorolsson.spotter.core.engine.PlanSlot
import com.viktorolsson.spotter.core.engine.RecommendationContext
import com.viktorolsson.spotter.core.engine.RecommendationDraft
import com.viktorolsson.spotter.core.engine.RecommendationEngine
import com.viktorolsson.spotter.core.engine.WeightConversion
import com.viktorolsson.spotter.core.model.Equipment
import com.viktorolsson.spotter.core.model.ProgressionRule
import com.viktorolsson.spotter.core.model.Recommendation
import com.viktorolsson.spotter.core.model.RecommendationAction
import com.viktorolsson.spotter.core.model.RecommendationEvidence
import com.viktorolsson.spotter.core.model.RecommendationPayload
import com.viktorolsson.spotter.core.model.RecommendationStatus
import com.viktorolsson.spotter.core.model.RecommendationType
import com.viktorolsson.spotter.core.model.WeightUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/** A change picked in the plan review, for the stalled lift [exerciseId]. */
data class PlanReviewChoice(val exerciseId: String, val exerciseName: String, val action: RecommendationAction)

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
            val drafts = RecommendationEngine.evaluate(context).map { it.copy(payload = it.payload.copy(action = withSyncIds(it.payload.action))) }
            val now = clock.now()
            // Two phones can each add the same card before they sync: every phone keeps the
            // oldest, so they agree on which one stays.
            val groups = existing.filter { it.status == RecommendationStatus.ACTIVE }.groupBy { it.dedupKey }.values
                .map { it.sortedWith(compareBy({ rec -> rec.createdAt }, { rec -> rec.syncId })) }
            val active = groups.associate { it.first().dedupKey to it.first() }
            val duplicates = groups.flatMap { it.drop(1) }
            val byKey = drafts.associateBy(::keyOf)
            // Cards whose problem went away, or whose best fix changed, are replaced. Row ids
            // differ per phone, so a card synced from another phone is compared without them.
            val outdated = active.values.filter { rec ->
                byKey[rec.dedupKey]?.payload?.withoutRowIds() != RecommendationPayload.decode(rec.payload).withoutRowIds()
            }
            if (outdated.isNotEmpty() || duplicates.isNotEmpty()) dao.delete((outdated + duplicates).map { it.id })
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
    suspend fun apply(id: Long) = apply(id, remake = true)

    private suspend fun apply(id: Long, remake: Boolean) {
        val entity = dao.get(id) ?: return
        val rec = entity.toModel()
        val action = onThisPhone(rec.payload.action)
        if (action == null) {
            // The card's plan rows aren't here (deleted, not synced yet, or a card from before
            // cards named rows by sync id). Make it again from this phone's plan, and apply the
            // new card only if it's the same change the lifter saw.
            dao.delete(listOf(id))
            refresh()
            val again = dao.getAll().firstOrNull { it.status == RecommendationStatus.ACTIVE && it.dedupKey == entity.dedupKey }
            if (remake && again != null && RecommendationPayload.decode(again.payload).withoutIds() == rec.payload.withoutIds()) apply(again.id, remake = false)
            return
        }
        val unit = preferences.preferences.first().weightUnit
        db.withTransaction {
            applyAction(action, unit)
            dao.resolve(id, RecommendationStatus.APPLIED, clock.now())
        }
        refresh()
    }

    /**
     * The plan review, when one is due: every few weeks from the plan's start or the last
     * review, sooner when several main lifts stall. Re-checked as workouts finish, the plan
     * changes or the review is answered.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observePlanReview(): Flow<PlanReview?> =
        combine(preferences.preferences, workoutDao.observeFinishedCount(), planDao.observeActivePlan()) { prefs, _, plan ->
            Triple(prefs.planReviewedOn, prefs.planReviewSnoozedUntil, plan)
        }.mapLatest { (reviewedOn, snoozedUntil, plan) ->
            plan ?: return@mapLatest null
            val context = buildContext(dao.getAll()) ?: return@mapLatest null
            val started = plan.plan.createdAt.toLocalDateTime(TimeZone.currentSystemDefault()).date
            PlanReviewEngine.review(context, since = maxOf(started, reviewedOn ?: started), snoozedUntil = snoozedUntil)
        }

    /**
     * Applies what the lifter picked in the review, in one go. Each change is stored as an
     * applied recommendation for its stalled lift, so the single cards don't suggest the
     * same lift again during the cooldown, and a variation is offered back when it ends.
     */
    suspend fun applyPlanReview(choices: List<PlanReviewChoice>) {
        val unit = preferences.preferences.first().weightUnit
        val now = clock.now()
        db.withTransaction {
            choices.forEach { applyAction(it.action, unit) }
            val keys = choices.map { plateauKey(it.exerciseId) }.toSet()
            dao.delete(dao.getAll().filter { it.status == RecommendationStatus.ACTIVE && it.dedupKey in keys }.map { it.id })
            dao.insert(
                choices.map { choice ->
                    RecommendationEntity(
                        type = if (choice.action is RecommendationAction.SwapVariation) RecommendationType.VARIATION else RecommendationType.ADD_ACCESSORY,
                        exerciseId = choice.exerciseId,
                        payload = RecommendationPayload(withSyncIds(choice.action), RecommendationEvidence(exerciseName = choice.exerciseName)).encode(),
                        createdAt = now,
                        status = RecommendationStatus.APPLIED,
                        resolvedAt = now,
                        dedupKey = plateauKey(choice.exerciseId),
                    )
                },
            )
        }
        preferences.setPlanReviewed(clock.todayIn(TimeZone.currentSystemDefault()))
        refresh()
    }

    /** "Keep my plan": nothing changes, and the next review is a few weeks away. */
    suspend fun keepPlan() = preferences.setPlanReviewed(clock.todayIn(TimeZone.currentSystemDefault()))

    /** "Later": the review comes back in a week. */
    suspend fun snoozePlanReview() = preferences.snoozePlanReview(
        clock.todayIn(TimeZone.currentSystemDefault()).plus(PlanReviewEngine.SNOOZE_DAYS - 1L, DateTimeUnit.DAY),
    )

    private suspend fun applyAction(action: RecommendationAction, unit: WeightUnit) {
        when (action) {
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
                val swap = RecommendationPayload.decode(rec.payload).action as? RecommendationAction.SwapVariation
                // A swap from before cards named rows by sync id keeps its row id (the engine
                // also checks the slot's exercise).
                val action = if (swap?.planExerciseSyncId == null) swap else onThisPhone(swap) as? RecommendationAction.SwapVariation
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
        is RecommendationAction.SwapVariation if action.returning -> "return|${action.planExerciseSyncId ?: action.planExerciseId}"
        else -> when (draft.type) {
            RecommendationType.INCREASE_FREQUENCY -> "frequency|${draft.payload.evidence.group}"
            else -> plateauKey(draft.exerciseId)
        }
    }

    private fun plateauKey(exerciseId: String?) = "plateau|$exerciseId"

    /** [action] with the sync ids of the plan rows it names, so it means the same rows on every phone. */
    private suspend fun withSyncIds(action: RecommendationAction): RecommendationAction = when (action) {
        is RecommendationAction.AddExercise ->
            action.copy(planDaySyncId = planDao.daySyncIds(listOf(action.planDayId)).firstOrNull()?.syncId)
        is RecommendationAction.AddSets -> {
            val syncIds = planDao.exerciseSyncIds(action.planExerciseIds).associate { it.id to it.syncId }
            action.copy(planExerciseSyncIds = action.planExerciseIds.map { syncIds[it] ?: return action })
        }
        is RecommendationAction.ChangeRepRange ->
            action.copy(planExerciseSyncId = planDao.exerciseSyncIds(listOf(action.planExerciseId)).firstOrNull()?.syncId)
        is RecommendationAction.SwapVariation ->
            action.copy(planExerciseSyncId = planDao.exerciseSyncIds(listOf(action.planExerciseId)).firstOrNull()?.syncId)
        is RecommendationAction.Deload -> action
    }

    /** [action] with this phone's row ids, found by sync id; null when a row isn't here or the card has no sync ids. */
    private suspend fun onThisPhone(action: RecommendationAction): RecommendationAction? = when (action) {
        is RecommendationAction.AddExercise ->
            action.planDaySyncId?.let { planDao.dayIds(listOf(it)).firstOrNull() }?.let { action.copy(planDayId = it.id) }
        is RecommendationAction.AddSets -> {
            val ids = planDao.exerciseIds(action.planExerciseSyncIds).associate { it.syncId to it.id }
            val local = action.planExerciseSyncIds.map { ids[it] ?: return null }
            if (local.isEmpty() || local.size != action.planExerciseIds.size) null else action.copy(planExerciseIds = local)
        }
        is RecommendationAction.ChangeRepRange ->
            action.planExerciseSyncId?.let { planDao.exerciseIds(listOf(it)).firstOrNull() }?.let { action.copy(planExerciseId = it.id) }
        is RecommendationAction.SwapVariation ->
            action.planExerciseSyncId?.let { planDao.exerciseIds(listOf(it)).firstOrNull() }?.let { action.copy(planExerciseId = it.id) }
        is RecommendationAction.Deload -> action
    }

    /** The card as every phone sees it: without this phone's row ids. */
    private fun RecommendationPayload.withoutRowIds() = copy(
        action = when (val a = action) {
            is RecommendationAction.AddExercise -> a.copy(planDayId = 0)
            is RecommendationAction.AddSets -> a.copy(planExerciseIds = emptyList())
            is RecommendationAction.ChangeRepRange -> a.copy(planExerciseId = 0)
            is RecommendationAction.SwapVariation -> a.copy(planExerciseId = 0)
            is RecommendationAction.Deload -> a
        },
    )

    /** The change the card shows, whichever rows it names. */
    private fun RecommendationPayload.withoutIds() = withoutRowIds().let {
        it.copy(
            action = when (val a = it.action) {
                is RecommendationAction.AddExercise -> a.copy(planDaySyncId = null)
                is RecommendationAction.AddSets -> a.copy(planExerciseSyncIds = emptyList())
                is RecommendationAction.ChangeRepRange -> a.copy(planExerciseSyncId = null)
                is RecommendationAction.SwapVariation -> a.copy(planExerciseSyncId = null)
                is RecommendationAction.Deload -> a
            },
        )
    }

    private companion object {
        val COOLDOWN: Duration = 28.days
    }
}
