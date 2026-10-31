package com.viktorolsson.spotter.core.engine

import com.viktorolsson.spotter.core.engine.Fixtures.byId
import com.viktorolsson.spotter.core.engine.MobilityDrill.ANKLE_ROCK
import com.viktorolsson.spotter.core.engine.MobilityDrill.ARM_CIRCLES
import com.viktorolsson.spotter.core.engine.MobilityDrill.CAT_COW
import com.viktorolsson.spotter.core.engine.MobilityDrill.JUMPING_JACKS
import com.viktorolsson.spotter.core.engine.MobilityDrill.LEG_SWING
import com.viktorolsson.spotter.core.engine.MobilityDrill.SCAPULAR_PUSH_UP
import com.viktorolsson.spotter.core.engine.MobilityDrill.WALL_SLIDE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MobilityWarmUpTest {
    private fun session(vararg ids: String) = ids.map { byId.getValue(it) to 3 }

    private val upperDrills = setOf(
        ARM_CIRCLES, SCAPULAR_PUSH_UP, WALL_SLIDE, MobilityDrill.THORACIC_ROTATION,
        MobilityDrill.PRONE_Y_RAISE, MobilityDrill.CROSS_BODY_ARM_SWING,
    )

    @Test
    fun `an upper-body day gets shoulder drills only`() {
        val drills = MobilityWarmUp.forSession(session("barbell-bench-press", "overhead-press", "barbell-row", "lat-pulldown"))
        assertEquals(JUMPING_JACKS, drills.first())
        assertEquals(MobilityWarmUp.MAX_DRILLS, drills.size)
        assertTrue(drills.drop(1).all { it in upperDrills }, "$drills")
        // Overhead pressing puts wall slides first, ahead of the default order.
        assertEquals(WALL_SLIDE, MobilityWarmUp.forSession(session("overhead-press", "dumbbell-lateral-raise"))[1])
    }

    @Test
    fun `a leg day gets hip and ankle drills`() {
        val drills = MobilityWarmUp.forSession(session("back-squat", "romanian-deadlift", "bulgarian-split-squat"))
        assertTrue(drills.none { it in upperDrills }, "$drills")
        assertTrue(ANKLE_ROCK in drills && CAT_COW in drills, "$drills")
    }

    @Test
    fun `a full-body day mixes both and stays capped`() {
        val drills = MobilityWarmUp.forSession(session("back-squat", "barbell-bench-press", "barbell-row", "romanian-deadlift"))
        assertEquals(MobilityWarmUp.MAX_DRILLS, drills.size)
        assertTrue(drills.any { it in upperDrills } && drills.any { it !in upperDrills && it != JUMPING_JACKS }, "$drills")
        assertEquals(drills.distinct(), drills)
    }

    @Test
    fun `no exercises gets a general warm-up - same input same drills`() {
        assertEquals(listOf(JUMPING_JACKS, MobilityDrill.INCHWORM, ARM_CIRCLES, MobilityDrill.WORLDS_GREATEST_STRETCH, MobilityDrill.BODYWEIGHT_SQUAT), MobilityWarmUp.forSession(emptyList()))
        val day = session("deadlift", "pull-up")
        assertEquals(MobilityWarmUp.forSession(day), MobilityWarmUp.forSession(day))
        assertTrue(LEG_SWING in MobilityWarmUp.forSession(session("deadlift")))
    }
}
