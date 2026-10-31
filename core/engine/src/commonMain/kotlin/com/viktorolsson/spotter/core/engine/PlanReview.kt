package com.viktorolsson.spotter.core.engine

import com.viktorolsson.spotter.core.model.Exercise
import com.viktorolsson.spotter.core.model.Mechanics
import com.viktorolsson.spotter.core.model.RecommendationAction
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

/** How a main lift moved over the review period: its best estimated 1RM first and last. */
data class LiftTrend(val exercise: Exercise, val fromScore: Double, val toScore: Double) {
    /** Change as a fraction: 0.05 is 5 % stronger. */
    val change: Double get() = if (fromScore > 0) toScore / fromScore - 1 else 0.0
}

/** A stalled main lift with what could help it. */
data class StalledLift(
    val plateau: Plateau,
    /** Exercises for the muscles around the lift, each added to the lift's day. */
    val supports: List<Pair<SupportingExercise, RecommendationAction.AddExercise>>,
    /** Or swap the lift itself for a variation for a few weeks. */
    val variation: RecommendationAction.SwapVariation?,
)

data class PlanReview(
    /** The first day the review covers: the plan's start or the last review. */
    val since: LocalDate,
    val sessions: Int,
    val trends: List<LiftTrend>,
    val stalled: List<StalledLift>,
)

/**
 * Every few weeks, looks back at the plan: which main lifts moved, which stalled, and
 * what would help the stalled ones along. Due after [REVIEW_DAYS] and [MIN_SESSIONS] when
 * something has stalled, or sooner when several main lifts stall at once.
 */
object PlanReviewEngine {
    const val REVIEW_DAYS = 28
    const val MIN_SESSIONS = 8
    const val SNOOZE_DAYS = 7
    private const val EARLY_DAYS = 14
    private const val EARLY_STALLS = 2

    /**
     * The review, if one is due. [since] is when the plan started or was last reviewed;
     * [snoozedUntil] holds it back until that day has passed.
     */
    fun review(ctx: RecommendationContext, since: LocalDate, snoozedUntil: LocalDate? = null): PlanReview? {
        if (ctx.deloadActive || (snoozedUntil != null && ctx.today <= snoozedUntil)) return null
        val days = since.daysUntil(ctx.today)
        val sessions = ctx.trainingDates.count { it >= since }
        val mainLifts = ctx.plan.filter { it.exercise.mechanics == Mechanics.COMPOUND }.distinctBy { it.exercise.id }
        val plateaus = mainLifts.mapNotNull { slot -> RecommendationEngine.detectPlateau(slot, ctx.history[slot.exercise.id].orEmpty()) }
        val due = plateaus.isNotEmpty() && (
            (days >= REVIEW_DAYS && sessions >= MIN_SESSIONS) || (days >= EARLY_DAYS && plateaus.size >= EARLY_STALLS)
        )
        if (!due) return null

        val taken = mutableSetOf<String>()
        val stalled = plateaus.map { plateau ->
            val supports = SupportingLifts.suggest(plateau.slot, ctx, taken)
            taken += supports.map { it.exercise.id }
            val variation = RecommendationEngine.variationFor(plateau.slot, ctx, exclude = taken)
            variation?.let { taken += it.toExerciseId }
            StalledLift(plateau, supports.map { it to RecommendationEngine.addAccessory(plateau.slot, it.exercise) }, variation)
        }
        val trends = mainLifts.mapNotNull { slot ->
            val period = ctx.history[slot.exercise.id].orEmpty().filter { it.date >= since }
            if (period.size < 2) null else LiftTrend(slot.exercise, period.first().score, period.last().score)
        }
        return PlanReview(since, sessions, trends, stalled)
    }
}
