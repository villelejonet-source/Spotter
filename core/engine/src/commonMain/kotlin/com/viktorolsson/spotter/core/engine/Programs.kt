package com.viktorolsson.spotter.core.engine

import com.viktorolsson.spotter.core.model.ExperienceLevel
import com.viktorolsson.spotter.core.model.Goal
import com.viktorolsson.spotter.core.model.Program
import com.viktorolsson.spotter.core.model.SplitType
import com.viktorolsson.spotter.core.model.UserProfile

/**
 * The programs that suit a profile, for the "Suits you" mark on the program cards. The split
 * is the one the generator picks on its own ([PlanGenerator.chooseSplit]), so the mark and the
 * "Recommended" plan never disagree; the 5-day mixes have no card, so 5 days marks none of them.
 * 5/3/1 is never picked automatically and suits a lifter past the beginning who wants strength
 * on four days with a barbell.
 */
fun suitedPrograms(profile: UserProfile): Set<Program> = buildSet {
    when (PlanGenerator.chooseSplit(profile.daysPerWeek, profile.experience, override = null)) {
        SplitType.FULL_BODY -> add(Program.FULL_BODY)
        SplitType.UPPER_LOWER -> add(Program.UPPER_LOWER)
        SplitType.PUSH_PULL_LEGS -> add(Program.PUSH_PULL_LEGS)
        else -> Unit
    }
    val fiveThreeOne = profile.goal == Goal.STRENGTH &&
        profile.experience != ExperienceLevel.NEW &&
        profile.daysPerWeek == Program.FIVE_THREE_ONE_DAYS &&
        Program.FIVE_THREE_ONE.availableFor(profile)
    if (fiveThreeOne) add(Program.FIVE_THREE_ONE)
}

/** [input] set up to build [program] (null: the automatic choice). */
fun PlanInput.forProgram(program: Program?): PlanInput =
    copy(splitOverride = program?.split, fiveThreeOne = program == Program.FIVE_THREE_ONE)
