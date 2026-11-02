package com.viktorolsson.spotter.feature.session

import com.viktorolsson.spotter.core.engine.Gamification
import com.viktorolsson.spotter.core.model.Badge
import com.viktorolsson.spotter.core.model.PersonalRecordType
import com.viktorolsson.spotter.core.model.PlayedSession
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SummaryXpTest {
    private fun session(id: Long, day: Int, records: List<PersonalRecordType> = emptyList()) = PlayedSession(
        id, LocalDateTime(2026, 10, day, 18, 0), 45, false, null, null, 10, 0.0, emptySet(), records,
    )

    @Test
    fun `the first workout fills the bar from empty and unlocks its badge`() {
        val xp = SummaryXp.of(Gamification.progress(listOf(session(1, 5)), daysPerWeek = null), 1)!!
        assertEquals(150, xp.earned.total)
        assertEquals(0f, xp.from)
        assertFalse(xp.levelUp)
        assertEquals(listOf(Badge.FIRST_REP), xp.badges)
    }

    @Test
    fun `the bar starts where the workout before left it`() {
        val xp = SummaryXp.of(Gamification.progress(listOf(session(1, 5), session(2, 7)), daysPerWeek = null), 2)!!
        // 300 XP: level 2 exactly, reached by this workout.
        assertTrue(xp.levelUp)
        assertEquals(0f, xp.from)
        val third = SummaryXp.of(Gamification.progress(listOf(session(1, 5), session(2, 7), session(3, 9)), daysPerWeek = null), 3)!!
        assertFalse(third.levelUp)
        assertEquals(0f, third.from)
        assertEquals(150, third.level.xpIntoLevel)
    }

    @Test
    fun `a workout the history doesn't have has no XP`() {
        assertNull(SummaryXp.of(Gamification.progress(emptyList(), daysPerWeek = null), 1))
    }
}
