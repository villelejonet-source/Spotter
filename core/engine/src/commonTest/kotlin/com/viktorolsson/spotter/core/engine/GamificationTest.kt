package com.viktorolsson.spotter.core.engine

import com.viktorolsson.spotter.core.model.Badge
import com.viktorolsson.spotter.core.model.LevelTitle
import com.viktorolsson.spotter.core.model.PersonalRecordType
import com.viktorolsson.spotter.core.model.PlayedSession
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GamificationTest {
    private fun session(
        id: Long,
        start: String,
        lighter: Boolean = false,
        sets: Int = 10,
        volumeKg: Double = 0.0,
        records: List<PersonalRecordType> = emptyList(),
    ) = PlayedSession(id, LocalDateTime.parse(start), 45, lighter, null, null, sets, volumeKg, emptySet(), records)

    @Test
    fun `a lighter session earns as much as any other`() {
        val progress = Gamification.progress(
            listOf(session(1, "2026-10-26T18:00"), session(2, "2026-10-28T18:00", lighter = true)),
            daysPerWeek = null,
        )
        assertEquals(progress.sessions[0].total, progress.sessions[1].total)
    }

    @Test
    fun `a workout that doesn't count doesn't count towards the week goal`() {
        val short = session(1, "2026-10-26T18:00").copy(minutes = 5)
        val progress = Gamification.progress(listOf(short, session(2, "2026-10-28T18:00")), daysPerWeek = 2)
        assertEquals(0, progress.sessions.sumOf { it.weeklyGoal })
    }

    @Test
    fun `a badge stays with the workout that unlocked it`() {
        val progress = Gamification.progress(
            listOf(
                session(1, "2026-10-26T18:00", records = listOf(PersonalRecordType.VOLUME)),
                session(2, "2026-10-28T18:00", records = listOf(PersonalRecordType.VOLUME)),
            ),
            daysPerWeek = null,
        )
        assertEquals(listOf(Badge.FIRST_REP, Badge.FIRST_PR), progress.unlockedBy(1))
        assertEquals(emptyList(), progress.unlockedBy(2))
    }

    @Test
    fun `progress stops at the target and a locked badge has no date`() {
        val progress = Gamification.progress(listOf(session(1, "2026-10-26T18:00", volumeKg = 900.0)), daysPerWeek = null)
        val piano = progress.badges.single { it.badge == Badge.GRAND_PIANO }
        val car = progress.badges.single { it.badge == Badge.CAR }
        assertEquals(500.0, piano.current)
        assertTrue(piano.unlocked)
        assertEquals(900.0, car.current)
        assertNull(car.unlockedOn)
    }

    @Test
    fun `sessions are counted in the order they started`() {
        val progress = Gamification.progress(
            listOf(session(2, "2026-10-28T18:00"), session(1, "2026-10-26T18:00")),
            daysPerWeek = null,
        )
        assertEquals(listOf(1L, 2L), progress.sessions.map { it.sessionId })
        assertEquals(listOf(Badge.FIRST_REP), progress.unlockedBy(1))
    }

    @Test
    fun `every title is reachable in order`() {
        LevelTitle.entries.forEach { title ->
            assertEquals(title, Gamification.level(GamificationRules.xpToReach(title.fromLevel)).title)
        }
    }

    @Test
    fun `no history is level 1 with every badge locked`() {
        val progress = Gamification.progress(emptyList(), daysPerWeek = 3)
        assertEquals(1, progress.level.number)
        assertTrue(progress.badges.none { it.unlocked })
    }
}
