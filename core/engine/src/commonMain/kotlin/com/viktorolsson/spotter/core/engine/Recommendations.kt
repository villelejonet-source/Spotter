package com.viktorolsson.spotter.core.engine

import com.viktorolsson.spotter.core.model.BodyArea
import com.viktorolsson.spotter.core.model.DeloadReason
import com.viktorolsson.spotter.core.model.Equipment
import com.viktorolsson.spotter.core.model.Exercise
import com.viktorolsson.spotter.core.model.ExperienceLevel
import com.viktorolsson.spotter.core.model.Goal
import com.viktorolsson.spotter.core.model.Limitation
import com.viktorolsson.spotter.core.model.Mechanics
import com.viktorolsson.spotter.core.model.MuscleGroup
import com.viktorolsson.spotter.core.model.MuscleVolume
import com.viktorolsson.spotter.core.model.ProgressionRule
import com.viktorolsson.spotter.core.model.RecommendationAction
import com.viktorolsson.spotter.core.model.RecommendationEvidence
import com.viktorolsson.spotter.core.model.RecommendationPayload
import com.viktorolsson.spotter.core.model.RecommendationType
import com.viktorolsson.spotter.core.model.VolumeStatus
import com.viktorolsson.spotter.core.model.bestSetOrder
import com.viktorolsson.spotter.core.model.epley
import com.viktorolsson.spotter.core.model.isLift
import com.viktorolsson.spotter.core.model.isTimed
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.until

/** One session's working sets of an exercise (deload sessions excluded). */
data class ExerciseExposure(val date: LocalDate, val sets: List<LoggedSet>) {
    private val lifts get() = sets.filter { isLift(it.reps) }

    /** Any set with weight: then [score] is in kg, otherwise in reps. */
    val weighted: Boolean get() = lifts.any { it.weightKg != null }

    /** Best estimated 1RM, or most reps for bodyweight work. Compare only like with like ([comparable]). */
    val score: Double
        get() = lifts.maxOfOrNull { set -> set.weightKg?.let { epley(it, set.reps) } ?: set.reps.toDouble() } ?: 0.0
    val bestSet: LoggedSet? get() = lifts.maxWithOrNull(bestSetOrder({ it.weightKg }, { it.reps }))
}

/**
 * The latest sessions whose scores can be compared: those since the lift last moved between
 * bodyweight and added weight. Reps and a 1RM in kg don't compare, so going from 12
 * bodyweight pull-ups to +5 kg × 8 is a new start, not a drop.
 */
internal fun List<ExerciseExposure>.comparable(): List<ExerciseExposure> {
    val weighted = lastOrNull()?.weighted ?: return this
    return takeLastWhile { it.weighted == weighted }
}

/** An exercise in the active plan, with where it lives so actions can change it. */
data class PlanSlot(
    val planExerciseId: Long,
    val planDayId: Long,
    val dayName: String,
    val exercise: Exercise,
    val sets: Int,
    val repMin: Int,
    val repMax: Int,
    val targetRir: Int?,
    val rule: ProgressionRule,
    /**
     * Runs 5/3/1: its loads swing by design (week 4 is 40–60 %) and it moves its own training
     * max, so it's left out of plateau and falling-performance checks.
     */
    val fiveThreeOne: Boolean = false,
)

/** A previously applied variation swap that may be due to switch back. */
data class AppliedVariation(val action: RecommendationAction.SwapVariation, val appliedOn: LocalDate)

data class RecommendationContext(
    val today: LocalDate,
    val plan: List<PlanSlot>,
    /** Per exercise id, oldest first. */
    val history: Map<String, List<ExerciseExposure>>,
    /** Dates of finished, non-deload sessions. */
    val trainingDates: List<LocalDate>,
    val lastDeloadEnd: LocalDate?,
    val weeklyVolume: List<MuscleVolume>,
    /** Logged RIR values from the last two weeks. */
    val recentRir: List<Int>,
    val library: List<Exercise>,
    val equipment: Set<Equipment>,
    val limitations: Set<Limitation>,
    val appliedVariations: List<AppliedVariation> = emptyList(),
    /** A deload week is running now: hold per-lift changes until it's over. */
    val deloadActive: Boolean = false,
    /** A deload was applied or dismissed recently: don't suggest (or wait for) another. */
    val deloadRecentlyHandled: Boolean = false,
    /** Finished workouts in the last [RecommendationEngine.LOW_DAYS_WINDOW] days with a low check-in or rating (1–2). */
    val recentLowDays: Int = 0,
    val goal: Goal? = null,
    val experience: ExperienceLevel? = null,
    val focusAreas: Set<BodyArea> = emptySet(),
    /** When the active plan started; null counts from the first workout. */
    val planStarted: LocalDate? = null,
    /** Per stalled lift's exercise id, the changes applied to help it before. */
    val tried: Map<String, List<TriedFix>> = emptyMap(),
    /** Lifts changed or dismissed recently: given time before they're judged again. */
    val cooling: Set<String> = emptySet(),
)

data class RecommendationDraft(
    val type: RecommendationType,
    val exerciseId: String?,
    val payload: RecommendationPayload,
)

data class Plateau(
    val slot: PlanSlot,
    val latest: LoggedSet?,
    val sessions: Int,
    val weeks: Int,
    val missedTwice: Boolean,
    /** Days covered by this exercise's history in the plan. */
    val historyDays: Long,
)

/**
 * Turns training history into concrete plan changes. Each recommendation is built
 * from data tables and thresholds, not per-lift code, and carries the evidence the
 * card shows ("Bench 80 kg × 5 for 4 sessions").
 */
object RecommendationEngine {
    private const val MIN_STALLED_SESSIONS = 3
    private const val MIN_STALLED_DAYS = 21L
    private const val LONG_IN_RANGE_DAYS = 42L
    private const val IMPROVEMENT = 1.005
    private const val DELOAD_EVERY_WEEKS = 6

    /** Days looked back over for low check-ins, and how many low workouts in them suggest a deload. */
    const val LOW_DAYS_WINDOW = 10
    private const val LOW_DAYS_FOR_DELOAD = 3
    private const val VARIATION_WEEKS = 5

    /** A Monday; whole weeks since it number the calendar weeks. */
    private val WEEK_ORIGIN = LocalDate(2000, 1, 3)

    /**
     * The cards stored and synced: a lighter week and switching back after a variation.
     * They come early, since they're about recovery, not the plan. Changes for stalled lifts
     * come from [PlanReviewEngine], once the plan has had time to work.
     */
    fun evaluate(ctx: RecommendationContext): List<RecommendationDraft> {
        if (ctx.deloadActive) return returnFromVariations(ctx)
        // Several stalled lifts are a plan signal, so they wait for the plan to be old enough too.
        val plateaus = if (!PlanReviewEngine.planAge(ctx).ready) emptyList() else ctx.plan
            .filter { !it.fiveThreeOne }
            .distinctBy { it.exercise.id }
            .mapNotNull { slot -> detectPlateau(slot, ctx.history[slot.exercise.id].orEmpty()) }
        val deload = if (ctx.deloadRecentlyHandled) null else deload(ctx, plateaus)
        return listOfNotNull(deload) + returnFromVariations(ctx)
    }

    /**
     * Stalled when the best estimated 1RM (or reps, for bodyweight) hasn't improved for
     * 3+ sessions over 3+ weeks, or the progression target was missed twice in a row.
     * A lift set to no progression is held on purpose, so it never stalls.
     */
    fun detectPlateau(slot: PlanSlot, history: List<ExerciseExposure>): Plateau? {
        val exposures = history.comparable()
        if (exposures.size < 2 || slot.rule == ProgressionRule.NONE) return null
        var bestSoFar = exposures.first().score
        var lastImprovement = 0
        exposures.forEachIndexed { i, exposure ->
            if (i > 0 && exposure.score > bestSoFar * IMPROVEMENT) lastImprovement = i
            bestSoFar = maxOf(bestSoFar, exposure.score)
        }
        val stalled = exposures.size - 1 - lastImprovement
        val stalledDays = exposures[lastImprovement].date.daysUntil(exposures.last().date).toLong()
        val missedTwice = exposures.takeLast(2).let { last -> last.size == 2 && last.all { missed(slot, it.sets) } }
        if (!missedTwice && (stalled < MIN_STALLED_SESSIONS || stalledDays < MIN_STALLED_DAYS)) return null
        return Plateau(
            slot = slot,
            latest = exposures.last().bestSet,
            sessions = maxOf(stalled, if (missedTwice) 2 else 0),
            weeks = (stalledDays / 7).toInt(),
            missedTwice = missedTwice,
            historyDays = exposures.first().date.daysUntil(exposures.last().date).toLong(),
        )
    }

    private fun missed(slot: PlanSlot, sets: List<LoggedSet>): Boolean {
        if (sets.isEmpty()) return false
        val floor = if (slot.rule == ProgressionRule.LINEAR) slot.repMin + (slot.repMax - slot.repMin) / 2 else slot.repMin
        return sets.count { it.reps < floor } * 2 > sets.size
    }

    /** One more set for the stalled lift's muscle when it gets less than its weekly target. */
    fun moreSets(slot: PlanSlot, ctx: RecommendationContext): RecommendationAction.AddSets? {
        val groups = slot.exercise.primaryMuscles.map(MuscleGroup::of).toSet()
        val under = ctx.weeklyVolume.firstOrNull { it.group in groups && it.status == VolumeStatus.UNDER } ?: return null
        val targets = listOf(slot) + ctx.plan.filter { s ->
            s.planExerciseId != slot.planExerciseId && s.exercise.primaryMuscles.any { MuscleGroup.of(it) == under.group }
        }.take(1)
        return RecommendationAction.AddSets(targets.map { it.planExerciseId }, targets.map { it.exercise.name })
    }

    /**
     * A block in another rep range for a lift that has sat in one for [LONG_IN_RANGE_DAYS]:
     * heavy work moves to 8–10, light work to 6–10, the middle to 4–6 with a set more.
     */
    fun repRangeChange(p: Plateau): RecommendationAction.ChangeRepRange? {
        val slot = p.slot
        if (p.historyDays < LONG_IN_RANGE_DAYS || slot.exercise.isTimed) return null
        val (sets, min, max) = when {
            slot.repMax <= 6 -> Triple((slot.sets - 1).coerceAtLeast(3), 8, 10)
            slot.repMin >= 10 -> Triple(slot.sets, 6, 10)
            else -> Triple(slot.sets + 1, 4, 6)
        }
        return RecommendationAction.ChangeRepRange(slot.planExerciseId, slot.sets, slot.repMin, slot.repMax, sets, min, max)
    }

    /** Adds [accessory] to [slot]'s day: 3 sets, moderate reps, a couple of reps in reserve. */
    fun addAccessory(slot: PlanSlot, accessory: Exercise): RecommendationAction.AddExercise {
        val compound = accessory.mechanics == Mechanics.COMPOUND
        val holdSeconds = timedHoldSeconds(ExperienceLevel.INTERMEDIATE).takeIf { accessory.isTimed }
        return RecommendationAction.AddExercise(
            planDayId = slot.planDayId,
            dayName = slot.dayName,
            exerciseId = accessory.id,
            exerciseName = accessory.name,
            sets = 3,
            repMin = holdSeconds?.first ?: if (compound) 6 else 10,
            repMax = holdSeconds?.last ?: if (compound) 10 else 15,
            targetRir = 2,
            restSeconds = if (compound) 120 else 90,
        )
    }

    /** A same-pattern variation of [slot]'s lift for a few weeks, or null if none fits. */
    fun variationFor(slot: PlanSlot, ctx: RecommendationContext, exclude: Set<String> = emptySet()): RecommendationAction.SwapVariation? {
        val variant = ExerciseSimilarity.rank(
            original = slot.exercise,
            library = ctx.library,
            availableEquipment = ctx.equipment,
            limitations = ctx.limitations,
            exclude = ctx.plan.map { it.exercise.id }.toSet() + exclude,
        ).firstOrNull {
            it.exercise.movementPattern == slot.exercise.movementPattern && it.exercise.mechanics == slot.exercise.mechanics
        }?.exercise ?: return null
        return RecommendationAction.SwapVariation(
            slot.planExerciseId, slot.exercise.id, slot.exercise.name, variant.id, variant.name, VARIATION_WEEKS,
        )
    }

    /**
     * A lighter week when several lifts stall together, when performance falls while
     * sets are close to failure, or after ~6 weeks of steady training.
     */
    private fun deload(ctx: RecommendationContext, plateaus: List<Plateau>): RecommendationDraft? {
        val falling = ctx.plan.filter { !it.fiveThreeOne }.distinctBy { it.exercise.id }.filter { slot ->
            val exposures = ctx.history[slot.exercise.id].orEmpty().comparable()
            exposures.size >= 3 && exposures.last().score < exposures[exposures.size - 2].score * 0.975
        }
        val nearFailure = ctx.recentRir.size >= 4 && ctx.recentRir.average() <= 0.5
        val (reason, lifts) = when {
            plateaus.size >= 3 -> DeloadReason.SEVERAL_PLATEAUS to plateaus.map { it.slot.exercise.name }
            falling.size >= 3 || (falling.size >= 2 && nearFailure) -> DeloadReason.RECOVERY to falling.map { it.exercise.name }
            ctx.recentLowDays >= LOW_DAYS_FOR_DELOAD -> DeloadReason.RECOVERY to emptyList()
            weeksSinceDeload(ctx) >= DELOAD_EVERY_WEEKS -> DeloadReason.SCHEDULED to emptyList()
            else -> return null
        }
        // Feeling low is the reason only when no lift is named.
        val lowDays = ctx.recentLowDays.takeIf { reason == DeloadReason.RECOVERY && lifts.isEmpty() }
        return RecommendationDraft(
            RecommendationType.DELOAD,
            null,
            RecommendationPayload(
                RecommendationAction.Deload(),
                RecommendationEvidence(deloadReason = reason, lifts = lifts, weeks = weeksSinceDeload(ctx), lowDays = lowDays),
            ),
        )
    }

    /** Weeks containing at least one session since the last deload (or ever). */
    private fun weeksSinceDeload(ctx: RecommendationContext): Int =
        ctx.trainingDates
            .filter { date -> ctx.lastDeloadEnd == null || date > ctx.lastDeloadEnd }
            .map { WEEK_ORIGIN.until(it, DateTimeUnit.WEEK) }
            .distinct().size

    /** Variation blocks end after their planned weeks: suggest switching back. */
    private fun returnFromVariations(ctx: RecommendationContext): List<RecommendationDraft> =
        ctx.appliedVariations.filter { !it.action.returning }.mapNotNull { applied ->
            val action = applied.action
            if (applied.appliedOn.until(ctx.today, DateTimeUnit.WEEK) < action.weeks) return@mapNotNull null
            val slot = ctx.plan.firstOrNull { it.planExerciseId == action.planExerciseId && it.exercise.id == action.toExerciseId }
                ?: return@mapNotNull null
            RecommendationDraft(
                RecommendationType.VARIATION,
                slot.exercise.id,
                RecommendationPayload(
                    action.copy(
                        fromExerciseId = action.toExerciseId,
                        fromName = action.toName,
                        toExerciseId = action.fromExerciseId,
                        toName = action.fromName,
                        returning = true,
                    ),
                    RecommendationEvidence(exerciseName = action.toName, weeks = action.weeks),
                ),
            )
        }
}
