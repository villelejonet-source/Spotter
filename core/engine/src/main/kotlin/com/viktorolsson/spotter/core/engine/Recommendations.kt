package com.viktorolsson.spotter.core.engine

import com.viktorolsson.spotter.core.model.DeloadReason
import com.viktorolsson.spotter.core.model.Equipment
import com.viktorolsson.spotter.core.model.Exercise
import com.viktorolsson.spotter.core.model.Limitation
import com.viktorolsson.spotter.core.model.Mechanics
import com.viktorolsson.spotter.core.model.MovementPattern
import com.viktorolsson.spotter.core.model.MuscleGroup
import com.viktorolsson.spotter.core.model.MuscleVolume
import com.viktorolsson.spotter.core.model.ProgressionRule
import com.viktorolsson.spotter.core.model.RecommendationAction
import com.viktorolsson.spotter.core.model.RecommendationEvidence
import com.viktorolsson.spotter.core.model.RecommendationPayload
import com.viktorolsson.spotter.core.model.RecommendationType
import com.viktorolsson.spotter.core.model.VolumeStatus
import com.viktorolsson.spotter.core.model.epley
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** One session's working sets of an exercise (deload sessions excluded). */
data class ExerciseExposure(val date: LocalDate, val sets: List<LoggedSet>) {
    /** Best estimated 1RM, or most reps for bodyweight work. */
    val score: Double
        get() = sets.maxOfOrNull { set -> set.weightKg?.let { epley(it, set.reps) } ?: set.reps.toDouble() } ?: 0.0
    val bestSet: LoggedSet? get() = sets.maxWithOrNull(compareBy({ it.weightKg ?: 0.0 }, { it.reps }))
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
)

/** A previously applied variation swap that may be due to switch back. */
data class AppliedVariation(val action: RecommendationAction.SwapVariation, val appliedOn: LocalDate)

data class RecommendationContext(
    val today: LocalDate,
    val plan: List<PlanSlot>,
    /** Plan days in order: id to name. */
    val planDays: List<Pair<Long, String>>,
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
    private const val MIN_TRAINING_WEEKS_FOR_PLAN_CHECKS = 2
    private const val VARIATION_WEEKS = 5

    /**
     * Weak-point accessories by movement pattern, in order of preference. Applies to
     * every exercise of the pattern (barbell, dumbbell or machine).
     */
    val weakPointAccessories: Map<MovementPattern, List<String>> = mapOf(
        MovementPattern.HORIZONTAL_PUSH to listOf(
            "close-grip-bench-press", "paused-bench-press", "dumbbell-bench-press", "skull-crusher",
            "triceps-dip", "dumbbell-front-raise", "barbell-row",
        ),
        MovementPattern.VERTICAL_PUSH to listOf(
            "push-press", "dumbbell-lateral-raise", "cable-lateral-raise", "triceps-pushdown", "face-pull", "barbell-row",
        ),
        MovementPattern.SQUAT to listOf("paused-squat", "front-squat", "leg-press", "bulgarian-split-squat", "plank"),
        MovementPattern.HINGE to listOf("romanian-deadlift", "deficit-deadlift", "rack-pull", "back-extension", "barbell-row"),
        MovementPattern.VERTICAL_PULL to listOf(
            "negative-pull-up", "lat-pulldown", "seated-cable-row", "one-arm-dumbbell-row", "ez-bar-curl", "dumbbell-curl",
        ),
        MovementPattern.HORIZONTAL_PULL to listOf("chest-supported-dumbbell-row", "face-pull", "lat-pulldown", "dumbbell-curl"),
        MovementPattern.LUNGE to listOf("leg-press", "step-up", "barbell-hip-thrust", "goblet-squat"),
        MovementPattern.HIP_THRUST to listOf("romanian-deadlift", "hip-abduction-machine", "bulgarian-split-squat", "cable-glute-kickback"),
    )

    /** Groups worth hitting at least twice a week. */
    private val majorGroups = setOf(
        MuscleGroup.CHEST, MuscleGroup.BACK, MuscleGroup.SHOULDERS, MuscleGroup.QUADS, MuscleGroup.HAMSTRINGS, MuscleGroup.GLUTES,
    )

    fun evaluate(ctx: RecommendationContext): List<RecommendationDraft> {
        if (ctx.deloadActive) return returnFromVariations(ctx)
        val drafts = mutableListOf<RecommendationDraft>()
        val plateaus = ctx.plan
            .distinctBy { it.exercise.id }
            .mapNotNull { slot -> detectPlateau(slot, ctx.history[slot.exercise.id].orEmpty()) }

        if (!ctx.deloadRecentlyHandled) deload(ctx, plateaus)?.let { drafts += it }
        // During a suggested deload, per-lift changes wait: rest first, then reassess.
        if (drafts.isEmpty()) {
            plateaus.forEach { plateau -> diagnose(plateau, ctx)?.let { drafts += it } }
            drafts += frequency(ctx)
        }
        drafts += returnFromVariations(ctx)
        return drafts
    }

    /**
     * Stalled when the best estimated 1RM (or reps, for bodyweight) hasn't improved for
     * 3+ sessions over 3+ weeks, or the progression target was missed twice in a row.
     */
    fun detectPlateau(slot: PlanSlot, exposures: List<ExerciseExposure>): Plateau? {
        if (exposures.size < 2) return null
        var bestSoFar = exposures.first().score
        var lastImprovement = 0
        exposures.forEachIndexed { i, exposure ->
            if (i > 0 && exposure.score > bestSoFar * IMPROVEMENT) lastImprovement = i
            bestSoFar = maxOf(bestSoFar, exposure.score)
        }
        val stalled = exposures.size - 1 - lastImprovement
        val stalledDays = ChronoUnit.DAYS.between(exposures[lastImprovement].date, exposures.last().date)
        val missedTwice = exposures.takeLast(2).let { last -> last.size == 2 && last.all { missed(slot, it.sets) } }
        if (!missedTwice && (stalled < MIN_STALLED_SESSIONS || stalledDays < MIN_STALLED_DAYS)) return null
        return Plateau(
            slot = slot,
            latest = exposures.last().bestSet,
            sessions = maxOf(stalled, if (missedTwice) 2 else 0),
            weeks = (stalledDays / 7).toInt(),
            missedTwice = missedTwice,
            historyDays = ChronoUnit.DAYS.between(exposures.first().date, exposures.last().date),
        )
    }

    private fun missed(slot: PlanSlot, sets: List<LoggedSet>): Boolean {
        if (sets.isEmpty()) return false
        val floor = if (slot.rule == ProgressionRule.LINEAR) slot.repMin + (slot.repMax - slot.repMin) / 2 else slot.repMin
        return sets.count { it.reps < floor } * 2 > sets.size
    }

    private fun evidence(p: Plateau) = RecommendationEvidence(
        exerciseName = p.slot.exercise.name,
        weightKg = p.latest?.weightKg,
        reps = p.latest?.reps,
        sessions = p.sessions,
        weeks = p.weeks,
        missedTwice = p.missedTwice,
    )

    /** Picks one fix per stalled lift: volume, then rep range, then accessory, then variation. */
    private fun diagnose(p: Plateau, ctx: RecommendationContext): RecommendationDraft? {
        val slot = p.slot
        val groups = slot.exercise.primaryMuscles.map(MuscleGroup::of).toSet()
        val under = ctx.weeklyVolume.firstOrNull { it.group in groups && it.status == VolumeStatus.UNDER }
        if (under != null) {
            val targets = ctx.plan.filter { s -> s.exercise.primaryMuscles.any { MuscleGroup.of(it) == under.group } }.take(2)
            return RecommendationDraft(
                RecommendationType.ADD_VOLUME,
                slot.exercise.id,
                RecommendationPayload(
                    RecommendationAction.AddSets(targets.map { it.planExerciseId }, targets.map { it.exercise.name }),
                    evidence(p).copy(group = under.group, groupSets = under.sets, targetLow = under.targetLow, targetHigh = under.targetHigh),
                ),
            )
        }
        if (p.historyDays >= LONG_IN_RANGE_DAYS) {
            val (sets, min, max) = when {
                slot.repMax <= 6 -> Triple((slot.sets - 1).coerceAtLeast(3), 8, 10)
                slot.repMin >= 10 -> Triple(slot.sets, 6, 10)
                else -> Triple(slot.sets + 1, 4, 6)
            }
            return RecommendationDraft(
                RecommendationType.CHANGE_REP_RANGE,
                slot.exercise.id,
                RecommendationPayload(
                    RecommendationAction.ChangeRepRange(slot.planExerciseId, slot.sets, slot.repMin, slot.repMax, sets, min, max),
                    evidence(p).copy(weeks = (p.historyDays / 7).toInt()),
                ),
            )
        }
        accessoryFor(slot, ctx)?.let { accessory ->
            val compound = accessory.mechanics == Mechanics.COMPOUND
            return RecommendationDraft(
                RecommendationType.ADD_ACCESSORY,
                slot.exercise.id,
                RecommendationPayload(
                    RecommendationAction.AddExercise(
                        planDayId = slot.planDayId,
                        dayName = slot.dayName,
                        exerciseId = accessory.id,
                        exerciseName = accessory.name,
                        sets = 3,
                        repMin = if (compound) 6 else 10,
                        repMax = if (compound) 10 else 15,
                        targetRir = 2,
                        restSeconds = if (compound) 120 else 90,
                    ),
                    evidence(p),
                ),
            )
        }
        val variant = ExerciseSimilarity.rank(
            original = slot.exercise,
            library = ctx.library,
            availableEquipment = ctx.equipment,
            limitations = ctx.limitations,
            exclude = ctx.plan.map { it.exercise.id }.toSet(),
        ).firstOrNull {
            it.exercise.movementPattern == slot.exercise.movementPattern && it.exercise.mechanics == slot.exercise.mechanics
        }?.exercise ?: return null
        return RecommendationDraft(
            RecommendationType.VARIATION,
            slot.exercise.id,
            RecommendationPayload(
                RecommendationAction.SwapVariation(
                    slot.planExerciseId, slot.exercise.id, slot.exercise.name, variant.id, variant.name, VARIATION_WEEKS,
                ),
                evidence(p),
            ),
        )
    }

    private fun usable(ex: Exercise, ctx: RecommendationContext): Boolean {
        val banned = ctx.limitations.flatMap { Rules.excludedFor[it].orEmpty() }
        return ex.id !in banned && ctx.equipment.containsAll(ex.equipment)
    }

    /** First weak-point accessory the user can do that isn't already in the plan. */
    fun accessoryFor(slot: PlanSlot, ctx: RecommendationContext): Exercise? {
        if (slot.exercise.mechanics != Mechanics.COMPOUND) return null
        val inPlan = ctx.plan.map { it.exercise.id }.toSet()
        val byId = ctx.library.associateBy { it.id }
        return weakPointAccessories[slot.exercise.movementPattern].orEmpty()
            .mapNotNull { byId[it] }
            .firstOrNull { it.id != slot.exercise.id && it.id !in inPlan && usable(it, ctx) }
    }

    /** Major groups trained on only one plan day get a second exercise on another day. */
    private fun frequency(ctx: RecommendationContext): List<RecommendationDraft> {
        if (ctx.planDays.size < 2 || trainingWeeks(ctx) < MIN_TRAINING_WEEKS_FOR_PLAN_CHECKS) return emptyList()
        val daysByGroup = majorGroups.associateWith { group ->
            ctx.plan.filter { slot -> slot.exercise.primaryMuscles.any { MuscleGroup.of(it) == group } }.map { it.planDayId }.toSet()
        }
        val inPlan = ctx.plan.map { it.exercise.id }.toSet()
        return daysByGroup.filter { (_, days) -> days.size == 1 }.mapNotNull { (group, days) ->
            val day = ctx.planDays
                .filter { it.first !in days }
                .minByOrNull { (id, _) -> ctx.plan.count { it.planDayId == id } } ?: return@mapNotNull null
            val exercise = ctx.library
                .filter { ex -> ex.primaryMuscles.any { MuscleGroup.of(it) == group } && ex.id !in inPlan && usable(ex, ctx) && !ex.isCustom }
                .maxWithOrNull(compareBy<Exercise>({ it.id in Rules.staples }, { -it.difficulty.ordinal }).thenByDescending { it.id })
                ?: return@mapNotNull null
            val compound = exercise.mechanics == Mechanics.COMPOUND
            RecommendationDraft(
                RecommendationType.INCREASE_FREQUENCY,
                null,
                RecommendationPayload(
                    RecommendationAction.AddExercise(
                        planDayId = day.first,
                        dayName = day.second,
                        exerciseId = exercise.id,
                        exerciseName = exercise.name,
                        sets = 3,
                        repMin = if (compound) 8 else 10,
                        repMax = if (compound) 12 else 15,
                        targetRir = 2,
                        restSeconds = if (compound) 120 else 90,
                    ),
                    RecommendationEvidence(group = group),
                ),
            )
        }
    }

    /**
     * A lighter week when several lifts stall together, when performance falls while
     * sets are close to failure, or after ~6 weeks of steady training.
     */
    private fun deload(ctx: RecommendationContext, plateaus: List<Plateau>): RecommendationDraft? {
        val falling = ctx.plan.distinctBy { it.exercise.id }.filter { slot ->
            val exposures = ctx.history[slot.exercise.id].orEmpty()
            exposures.size >= 3 && exposures.last().score < exposures[exposures.size - 2].score * 0.975
        }
        val nearFailure = ctx.recentRir.size >= 4 && ctx.recentRir.average() <= 0.5
        val (reason, lifts) = when {
            plateaus.size >= 3 -> DeloadReason.SEVERAL_PLATEAUS to plateaus.map { it.slot.exercise.name }
            falling.size >= 3 || (falling.size >= 2 && nearFailure) -> DeloadReason.RECOVERY to falling.map { it.exercise.name }
            weeksSinceDeload(ctx) >= DELOAD_EVERY_WEEKS -> DeloadReason.SCHEDULED to emptyList()
            else -> return null
        }
        return RecommendationDraft(
            RecommendationType.DELOAD,
            null,
            RecommendationPayload(
                RecommendationAction.Deload(),
                RecommendationEvidence(deloadReason = reason, lifts = lifts, weeks = weeksSinceDeload(ctx)),
            ),
        )
    }

    /** Weeks containing at least one session since the last deload (or ever). */
    private fun weeksSinceDeload(ctx: RecommendationContext): Int =
        ctx.trainingDates
            .filter { date -> ctx.lastDeloadEnd == null || date.isAfter(ctx.lastDeloadEnd) }
            .map { ChronoUnit.WEEKS.between(LocalDate.of(2000, 1, 3), it) }
            .distinct().size

    private fun trainingWeeks(ctx: RecommendationContext): Int =
        ctx.trainingDates.map { ChronoUnit.WEEKS.between(LocalDate.of(2000, 1, 3), it) }.distinct().size

    /** Variation blocks end after their planned weeks: suggest switching back. */
    private fun returnFromVariations(ctx: RecommendationContext): List<RecommendationDraft> =
        ctx.appliedVariations.filter { !it.action.returning }.mapNotNull { applied ->
            val action = applied.action
            if (ChronoUnit.WEEKS.between(applied.appliedOn, ctx.today) < action.weeks) return@mapNotNull null
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
