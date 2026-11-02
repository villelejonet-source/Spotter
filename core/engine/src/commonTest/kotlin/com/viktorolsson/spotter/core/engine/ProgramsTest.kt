package com.viktorolsson.spotter.core.engine

import com.viktorolsson.spotter.core.engine.Fixtures.generate
import com.viktorolsson.spotter.core.engine.Fixtures.profile
import com.viktorolsson.spotter.core.model.Equipment
import com.viktorolsson.spotter.core.model.ExperienceLevel
import com.viktorolsson.spotter.core.model.Goal
import com.viktorolsson.spotter.core.model.Program
import com.viktorolsson.spotter.core.model.ProgramBlocker
import com.viktorolsson.spotter.core.model.SplitType
import com.viktorolsson.spotter.core.model.program
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProgramsTest {

    private fun build(program: Program?, days: Int = 4) =
        PlanGenerator().generate(PlanInput(profile(days = days), Fixtures.library, Fixtures.today, Fixtures.now).forProgram(program)).plan

    @Test
    fun `a generated plan knows its program`() {
        Program.entries.forEach { assertEquals(it, build(it).program()) }
    }

    @Test
    fun `the 5-day mixes and custom plans have no program`() {
        assertEquals(SplitType.UPPER_LOWER_FULL, generate(profile(days = 5)).plan.splitType)
        assertNull(generate(profile(days = 5)).plan.program())
        assertNull(generate(profile(days = 5)).plan.copy(splitType = SplitType.CUSTOM).program())
    }

    @Test
    fun `the suited split is the one the generator picks`() {
        (2..6).forEach { days ->
            ExperienceLevel.entries.forEach { experience ->
                val p = profile(days = days, experience = experience)
                val split = generate(p).plan.splitType
                val suited = suitedPrograms(p)
                Program.entries.filter { it.split != null }.forEach {
                    assertEquals(it.split == split, it in suited, "$days days, $experience, $it")
                }
            }
        }
    }

    @Test
    fun `5-3-1 suits a lifter past the beginning who wants strength on four days with a barbell`() {
        assertTrue(Program.FIVE_THREE_ONE in suitedPrograms(profile(goal = Goal.STRENGTH)))
        assertFalse(Program.FIVE_THREE_ONE in suitedPrograms(profile(goal = Goal.HYPERTROPHY)))
        assertFalse(Program.FIVE_THREE_ONE in suitedPrograms(profile(goal = Goal.STRENGTH, experience = ExperienceLevel.NEW)))
        assertFalse(Program.FIVE_THREE_ONE in suitedPrograms(profile(goal = Goal.STRENGTH, days = 3)))
        assertFalse(Program.FIVE_THREE_ONE in suitedPrograms(profile(goal = Goal.STRENGTH, equipment = Fixtures.dumbbellsOnly)))
    }

    @Test
    fun `5-3-1 needs a barbell and PPL three days`() {
        assertEquals(ProgramBlocker.NEEDS_BARBELL, Program.FIVE_THREE_ONE.blocker(Fixtures.dumbbellsOnly, 4))
        assertNull(Program.FIVE_THREE_ONE.blocker(setOf(Equipment.BARBELL), 2))
        assertEquals(ProgramBlocker.NEEDS_THREE_DAYS, Program.PUSH_PULL_LEGS.blocker(Fixtures.fullGym, 2))
        assertNull(Program.PUSH_PULL_LEGS.blocker(Fixtures.fullGym, 3))
        assertNull(Program.FULL_BODY.blocker(emptySet(), 2))
    }
}
