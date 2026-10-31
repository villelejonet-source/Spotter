package com.viktorolsson.spotter.core.engine

import com.viktorolsson.spotter.core.model.Exercise
import com.viktorolsson.spotter.core.model.Mechanics
import com.viktorolsson.spotter.core.model.MovementPattern
import com.viktorolsson.spotter.core.model.Muscle

/** Why a supporting exercise helps a stalled lift; the review sheet words each one. */
enum class SupportReason {
    UPPER_CHEST, TRICEPS, SHOULDERS, SIDE_DELTS, UPPER_BACK, REAR_DELTS, LATS, BICEPS,
    QUADS, HAMSTRINGS, GLUTES, LOWER_BACK, CORE,
}

data class SupportingExercise(val exercise: Exercise, val reason: SupportReason)

/**
 * The muscles around a main lift that help it move: a stalled bench gets incline work for
 * the bottom, triceps for the lockout and overhead pressing for the shoulders. Each role
 * lists exercises in order of preference, then falls back to anything usable that trains
 * the role's muscles.
 */
object SupportingLifts {
    private data class Role(val reason: SupportReason, val muscles: Set<Muscle>, val preferred: List<String>)

    private val TRICEPS = Role(
        SupportReason.TRICEPS, setOf(Muscle.TRICEPS),
        listOf("close-grip-bench-press", "skull-crusher", "triceps-dip", "triceps-pushdown"),
    )
    private val BICEPS = Role(SupportReason.BICEPS, setOf(Muscle.BICEPS), listOf("ez-bar-curl", "dumbbell-curl", "hammer-curl"))
    private val UPPER_BACK = Role(
        SupportReason.UPPER_BACK, setOf(Muscle.UPPER_BACK),
        listOf("barbell-row", "chest-supported-dumbbell-row", "seated-cable-row", "one-arm-dumbbell-row"),
    )
    private val HAMSTRINGS = Role(
        SupportReason.HAMSTRINGS, setOf(Muscle.HAMSTRINGS),
        listOf("romanian-deadlift", "dumbbell-romanian-deadlift", "lying-leg-curl", "seated-leg-curl"),
    )
    private val QUADS = Role(
        SupportReason.QUADS, setOf(Muscle.QUADS),
        listOf("paused-squat", "front-squat", "leg-press", "bulgarian-split-squat", "goblet-squat"),
    )

    private val roles: Map<MovementPattern, List<Role>> = mapOf(
        MovementPattern.HORIZONTAL_PUSH to listOf(
            Role(
                SupportReason.UPPER_CHEST, setOf(Muscle.CHEST, Muscle.FRONT_DELTS),
                listOf("incline-barbell-bench-press", "incline-dumbbell-press", "incline-machine-press", "incline-push-up"),
            ),
            TRICEPS,
            Role(
                SupportReason.SHOULDERS, setOf(Muscle.FRONT_DELTS),
                listOf("overhead-press", "seated-dumbbell-shoulder-press", "machine-shoulder-press"),
            ),
        ),
        MovementPattern.VERTICAL_PUSH to listOf(
            TRICEPS.copy(preferred = listOf("close-grip-bench-press", "triceps-pushdown", "skull-crusher", "triceps-dip")),
            Role(SupportReason.SIDE_DELTS, setOf(Muscle.SIDE_DELTS), listOf("dumbbell-lateral-raise", "cable-lateral-raise")),
            Role(SupportReason.UPPER_BACK, setOf(Muscle.UPPER_BACK, Muscle.REAR_DELTS), listOf("face-pull", "barbell-row")),
        ),
        MovementPattern.SQUAT to listOf(
            QUADS,
            HAMSTRINGS,
            Role(SupportReason.CORE, setOf(Muscle.ABS), listOf("plank", "barbell-rollout", "dead-bug")),
        ),
        MovementPattern.HINGE to listOf(
            HAMSTRINGS.copy(preferred = listOf("romanian-deadlift", "deficit-deadlift", "good-morning", "lying-leg-curl")),
            Role(SupportReason.LOWER_BACK, setOf(Muscle.LOWER_BACK), listOf("back-extension", "reverse-hyperextension")),
            UPPER_BACK,
        ),
        MovementPattern.VERTICAL_PULL to listOf(
            Role(SupportReason.LATS, setOf(Muscle.LATS), listOf("negative-pull-up", "lat-pulldown", "single-arm-lat-pulldown")),
            BICEPS,
            UPPER_BACK.copy(preferred = listOf("seated-cable-row", "one-arm-dumbbell-row", "chest-supported-dumbbell-row")),
        ),
        MovementPattern.HORIZONTAL_PULL to listOf(
            UPPER_BACK.copy(preferred = listOf("chest-supported-dumbbell-row", "seated-cable-row", "one-arm-dumbbell-row")),
            Role(SupportReason.REAR_DELTS, setOf(Muscle.REAR_DELTS), listOf("face-pull", "bent-over-rear-delt-fly", "reverse-pec-deck")),
            BICEPS,
        ),
        MovementPattern.LUNGE to listOf(
            QUADS.copy(preferred = listOf("leg-press", "step-up", "goblet-squat")),
            Role(SupportReason.GLUTES, setOf(Muscle.GLUTES), listOf("barbell-hip-thrust", "glute-bridge")),
            HAMSTRINGS,
        ),
        MovementPattern.HIP_THRUST to listOf(
            HAMSTRINGS,
            Role(SupportReason.GLUTES, setOf(Muscle.GLUTES), listOf("hip-abduction-machine", "cable-glute-kickback")),
            QUADS.copy(preferred = listOf("bulgarian-split-squat", "leg-press")),
        ),
    )

    /** Every exercise id the rules name; a test checks they're all in the catalog. */
    val namedExerciseIds: Set<String> get() = roles.values.flatten().flatMap { it.preferred }.toSet()

    /**
     * Up to one exercise per role for a stalled compound [slot], skipping anything in the
     * plan, in [taken] or that the user can't do. Isolation lifts get none.
     */
    fun suggest(slot: PlanSlot, ctx: RecommendationContext, taken: Set<String> = emptySet()): List<SupportingExercise> {
        if (slot.exercise.mechanics != Mechanics.COMPOUND) return emptyList()
        val byId = ctx.library.associateBy { it.id }
        val banned = ctx.limitations.flatMap { Rules.excludedFor[it].orEmpty() }.toSet()
        val skip = (ctx.plan.map { it.exercise.id } + taken + slot.exercise.id).toMutableSet()
        fun usable(ex: Exercise) = ex.id !in skip && ex.id !in banned && !ex.isCustom && ctx.equipment.containsAll(ex.equipment)
        return roles[slot.exercise.movementPattern].orEmpty().mapNotNull { role ->
            val pick = role.preferred.mapNotNull { byId[it] }.firstOrNull(::usable)
                ?: ctx.library
                    .filter { ex -> usable(ex) && ex.primaryMuscles.any { it in role.muscles } }
                    .maxWithOrNull(compareBy<Exercise>({ it.id in Rules.staples }, { -it.difficulty.ordinal }).thenByDescending { it.id })
                ?: return@mapNotNull null
            skip += pick.id
            SupportingExercise(pick, role.reason)
        }
    }
}
