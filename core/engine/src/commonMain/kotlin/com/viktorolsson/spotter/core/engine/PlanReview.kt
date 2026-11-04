package com.viktorolsson.spotter.core.engine

import com.viktorolsson.spotter.core.model.LOWER_BODY_MUSCLES
import com.viktorolsson.spotter.core.model.ExperienceLevel
import com.viktorolsson.spotter.core.model.Exercise
import com.viktorolsson.spotter.core.model.Goal
import com.viktorolsson.spotter.core.model.Mechanics
import com.viktorolsson.spotter.core.model.RecommendationAction
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus

/** How a main lift moved over the review period: its best estimated 1RM first and last. */
data class LiftTrend(val exercise: Exercise, val fromScore: Double, val toScore: Double) {
    /** Change as a fraction: 0.05 is 5 % stronger. */
    val change: Double get() = if (fromScore > 0) toScore / fromScore - 1 else 0.0
}

/** The kinds of change that can help a stalled lift. */
enum class FixKind { SUPPORT, VARIATION, REP_RANGE, MORE_SETS }

/** A change applied earlier to help a stalled lift, and when. */
data class TriedFix(val kind: FixKind, val on: LocalDate)

/** One change offered for a stalled lift; [reason] says what a [FixKind.SUPPORT] exercise helps with. */
data class LiftOption(val kind: FixKind, val action: RecommendationAction, val reason: SupportReason? = null) {
    /** The exercise this option brings into the plan, if any. */
    val addsExerciseId: String?
        get() = when (action) {
            is RecommendationAction.AddExercise -> action.exerciseId
            is RecommendationAction.SwapVariation -> action.toExerciseId
            else -> null
        }

    /** Names the option by what it does, so a ticked option stays the same when the list is rebuilt. */
    val key: String get() = "$kind|${addsExerciseId.orEmpty()}"
}

/** A stalled main lift with what could help it, best first. */
data class StalledLift(val plateau: Plateau, val options: List<LiftOption>)

/** How long the current plan has run, against what suggestions wait for. */
data class PlanAge(val sessions: Int, val weeks: Int) {
    val ready: Boolean get() = sessions >= PlanReviewEngine.MIN_PLAN_SESSIONS && weeks >= PlanReviewEngine.MIN_PLAN_WEEKS
}

data class PlanReview(
    /** The first day the review covers: the plan's start or the last review. */
    val since: LocalDate,
    val sessions: Int,
    val trends: List<LiftTrend>,
    val stalled: List<StalledLift>,
)

/**
 * Looks at the plan as a whole: which main lifts moved, which stalled, and what would help
 * the stalled ones along, ordered for the lifter's goal, experience and what was tried
 * before. Nothing is suggested until the plan has had [MIN_PLAN_WEEKS] and
 * [MIN_PLAN_SESSIONS] to work; after that Progress shows it all the time, and Today brings
 * it up as a review every [REVIEW_DAYS].
 */
object PlanReviewEngine {
    const val MIN_PLAN_WEEKS = 6
    const val MIN_PLAN_SESSIONS = 12
    const val REVIEW_DAYS = 28
    const val SNOOZE_DAYS = 7

    /** The trend on Progress covers this many days. */
    const val TREND_DAYS = 28L

    /** A rep range changed this recently isn't changed again: it would only flip back. */
    private const val REP_RANGE_AGAIN_DAYS = 56

    /** Sessions and whole weeks since the plan started (or, without a start, since the first workout). */
    fun planAge(ctx: RecommendationContext): PlanAge {
        val start = ctx.planStarted ?: ctx.trainingDates.minOrNull() ?: ctx.today
        return PlanAge(ctx.trainingDates.count { it >= start }, start.daysUntil(ctx.today) / 7)
    }

    /**
     * Every stalled main lift with its options, focus areas first. Lifts changed or
     * dismissed recently ([RecommendationContext.cooling]) get time before they're judged
     * again, and a lift with nothing to offer is left out.
     */
    fun stalledLifts(ctx: RecommendationContext): List<StalledLift> {
        if (ctx.deloadActive || !planAge(ctx).ready) return emptyList()
        val taken = mutableSetOf<String>()
        return mainLifts(ctx)
            .filter { it.exercise.id !in ctx.cooling }
            .mapNotNull { slot -> RecommendationEngine.detectPlateau(slot, ctx.history[slot.exercise.id].orEmpty()) }
            .sortedByDescending { p -> ctx.focusAreas.any { area -> p.slot.exercise.primaryMuscles.any { it in area.muscles } } }
            .mapNotNull { plateau ->
                val options = options(plateau, ctx, taken)
                taken += options.mapNotNull { it.addsExerciseId }
                StalledLift(plateau, options).takeIf { options.isNotEmpty() }
            }
    }

    /** Each main lift's change from its first to its latest session since [since]. */
    fun trends(ctx: RecommendationContext, since: LocalDate = ctx.today.minus(TREND_DAYS, DateTimeUnit.DAY)): List<LiftTrend> =
        mainLifts(ctx).mapNotNull { slot ->
            val period = ctx.history[slot.exercise.id].orEmpty().filter { it.date >= since }.comparable()
            if (period.size < 2) null else LiftTrend(slot.exercise, period.first().score, period.last().score)
        }

    /**
     * Today's review, if one is due: [REVIEW_DAYS] after [since] (the plan's start or the
     * last review) once the plan is old enough and something has stalled. [snoozedUntil]
     * holds it back until that day has passed.
     */
    fun review(ctx: RecommendationContext, since: LocalDate, snoozedUntil: LocalDate? = null): PlanReview? {
        if (snoozedUntil != null && ctx.today <= snoozedUntil) return null
        if (since.daysUntil(ctx.today) < REVIEW_DAYS) return null
        val stalled = stalledLifts(ctx).ifEmpty { return null }
        return PlanReview(since, ctx.trainingDates.count { it >= since }, trends(ctx, since), stalled)
    }

    private fun mainLifts(ctx: RecommendationContext) =
        ctx.plan.filter { it.exercise.mechanics == Mechanics.COMPOUND && !it.fiveThreeOne }.distinctBy { it.exercise.id }

    /**
     * What could help [plateau]'s lift: supporting exercises on its own day, a variation, a
     * new rep range, another set. Ordered for the goal, with kinds tried before last so a
     * lift that stalls again gets something new first.
     */
    private fun options(plateau: Plateau, ctx: RecommendationContext, taken: Set<String>): List<LiftOption> {
        val slot = plateau.slot
        val tried = ctx.tried[slot.exercise.id].orEmpty()
        val supports = SupportingLifts.suggest(slot, ctx, taken).map {
            LiftOption(FixKind.SUPPORT, RecommendationEngine.addAccessory(slot, it.exercise), it.reason)
        }
        // A new lifter keeps practising the lift rather than swapping it.
        val variation = RecommendationEngine.variationFor(slot, ctx, exclude = taken + supports.mapNotNull { it.addsExerciseId })
            ?.takeIf { ctx.experience != ExperienceLevel.NEW }
            ?.let { LiftOption(FixKind.VARIATION, it) }
        val repRange = RecommendationEngine.repRangeChange(plateau)
            ?.takeIf { tried.none { t -> t.kind == FixKind.REP_RANGE && t.on.daysUntil(ctx.today) < REP_RANGE_AGAIN_DAYS } }
            ?.let { LiftOption(FixKind.REP_RANGE, it) }
        val moreSets = RecommendationEngine.moreSets(slot, ctx)
            ?.takeIf { ctx.goal != Goal.GENERAL_FITNESS && ctx.goal != Goal.FAT_LOSS }
            ?.let { LiftOption(FixKind.MORE_SETS, it) }
        val byKind = (listOfNotNull(moreSets, repRange, variation) + supports).groupBy { it.kind }
        val triedKinds = tried.map { it.kind }.toSet()
        return order(ctx.goal).sortedBy { it in triedKinds }.flatMap { byKind[it].orEmpty() }
    }

    /** Which kind of change comes first: heavier work for strength, more work for size. */
    private fun order(goal: Goal?): List<FixKind> = when (goal) {
        Goal.STRENGTH -> listOf(FixKind.REP_RANGE, FixKind.VARIATION, FixKind.SUPPORT, FixKind.MORE_SETS)
        Goal.HYPERTROPHY -> listOf(FixKind.MORE_SETS, FixKind.SUPPORT, FixKind.VARIATION, FixKind.REP_RANGE)
        else -> listOf(FixKind.SUPPORT, FixKind.MORE_SETS, FixKind.VARIATION, FixKind.REP_RANGE)
    }

    /** The kind of change [action] makes, for remembering what was tried. */
    fun kindOf(action: RecommendationAction): FixKind? = when (action) {
        is RecommendationAction.AddExercise -> FixKind.SUPPORT
        is RecommendationAction.SwapVariation -> if (action.returning) null else FixKind.VARIATION
        is RecommendationAction.ChangeRepRange -> FixKind.REP_RANGE
        is RecommendationAction.AddSets -> FixKind.MORE_SETS
        is RecommendationAction.Deload -> null
    }
}

/** True for lifts driven by the legs, hips or lower back; the rest count as upper body. */
val Exercise.isLowerBody: Boolean
    get() = primaryMuscles.any { it in LOWER_BODY_MUSCLES }
