package com.viktorolsson.spotter.core.model

/**
 * A way to train the user can pick: one of the generated splits, or 5/3/1. Never stored: a
 * saved plan keeps its [SplitType] (5/3/1 is CUSTOM with training maxes), so older app versions
 * read every row. [Plan.program] works it out from a saved plan.
 */
enum class Program {
    FULL_BODY,
    UPPER_LOWER,
    PUSH_PULL_LEGS,
    FIVE_THREE_ONE,
    ;

    /** The split the generator builds; null for 5/3/1, which has its own days. */
    val split: SplitType?
        get() = when (this) {
            FULL_BODY -> SplitType.FULL_BODY
            UPPER_LOWER -> SplitType.UPPER_LOWER
            PUSH_PULL_LEGS -> SplitType.PUSH_PULL_LEGS
            FIVE_THREE_ONE -> null
        }

    /** Training days a week it gives a profile: 5/3/1 is always four. */
    fun daysPerWeek(profile: UserProfile): Int = if (this == FIVE_THREE_ONE) FIVE_THREE_ONE_DAYS else profile.daysPerWeek

    /** Why the profile can't follow it (no barbell for 5/3/1, too few days for PPL); null when it can. */
    fun blocker(equipment: Set<Equipment>, daysPerWeek: Int): ProgramBlocker? = when {
        this == FIVE_THREE_ONE && Equipment.BARBELL !in equipment -> ProgramBlocker.NEEDS_BARBELL
        this == PUSH_PULL_LEGS && daysPerWeek < 3 -> ProgramBlocker.NEEDS_THREE_DAYS
        else -> null
    }

    fun availableFor(profile: UserProfile): Boolean = blocker(profile.equipment, profile.daysPerWeek) == null

    companion object {
        const val FIVE_THREE_ONE_DAYS = 4
    }
}

enum class ProgramBlocker { NEEDS_BARBELL, NEEDS_THREE_DAYS }

/**
 * The [Program] a saved plan follows: 5/3/1 when a lift has a training max, else its split.
 * Null for the automatic 5-day mixes and custom plans, which no single program describes.
 */
fun Plan.program(): Program? = when {
    days.any { day -> day.exercises.any { it.trainingMaxKg != null } } -> Program.FIVE_THREE_ONE
    splitType == SplitType.FULL_BODY -> Program.FULL_BODY
    splitType == SplitType.UPPER_LOWER -> Program.UPPER_LOWER
    splitType == SplitType.PUSH_PULL_LEGS -> Program.PUSH_PULL_LEGS
    else -> null
}
