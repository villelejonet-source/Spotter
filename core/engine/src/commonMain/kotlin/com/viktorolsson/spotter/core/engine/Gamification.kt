package com.viktorolsson.spotter.core.engine

import com.viktorolsson.spotter.core.model.Badge
import com.viktorolsson.spotter.core.model.BadgeMetric
import com.viktorolsson.spotter.core.model.BadgeProgress
import com.viktorolsson.spotter.core.model.LOW_CHECK_IN
import com.viktorolsson.spotter.core.model.Level
import com.viktorolsson.spotter.core.model.LevelTitle
import com.viktorolsson.spotter.core.model.PersonalRecordType
import com.viktorolsson.spotter.core.model.PlayedSession
import com.viktorolsson.spotter.core.model.PlayerProgress
import com.viktorolsson.spotter.core.model.SessionXp
import com.viktorolsson.spotter.core.model.weekStart
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus

/** Every number levels and badges use, in one place to tune. The website copies them. */
object GamificationRules {
    /** Shorter workouts, or ones without a completed working set, don't count at all. */
    const val MIN_MINUTES = 10
    const val SESSION_XP = 100
    const val SET_XP = 5
    /** Sets past this earn nothing: no reward for junk volume. */
    const val MAX_XP_SETS = 25
    const val RECORD_XP = 50
    const val CHECK_IN_XP = 25
    const val WEEKLY_GOAL_XP = 150
    /** Only this many workouts a day earn session, set and check-in XP. */
    const val XP_SESSIONS_PER_DAY = 2
    const val COMEBACK_DAYS = 21
    /** Early bird: started before this hour. */
    const val EARLY_BEFORE_HOUR = 7
    /** Night owl: started at this hour or later. */
    const val LATE_FROM_HOUR = 21

    /** Total XP needed to reach [level]: 50 · n · (n + 1), level 1 from nothing. */
    fun xpToReach(level: Int): Int = if (level <= 1) 0 else 50 * level * (level + 1)
}

/**
 * Levels and badges from the workout history. A lighter session earns as much as any other:
 * resting is part of training. Missing a day never costs anything; the week goal is the
 * profile's days a week, counted in days trained.
 */
object Gamification {
    fun level(totalXp: Int): Level {
        var n = 1
        while (GamificationRules.xpToReach(n + 1) <= totalXp) n++
        val from = GamificationRules.xpToReach(n)
        return Level(
            number = n,
            title = LevelTitle.entries.last { it.fromLevel <= n },
            xpIntoLevel = totalXp - from,
            xpForNext = GamificationRules.xpToReach(n + 1) - from,
        )
    }

    fun counts(session: PlayedSession): Boolean =
        session.minutes >= GamificationRules.MIN_MINUTES && session.workingSets > 0

    /** [daysPerWeek]: the profile's weekly goal; null or 0 means no goal bonus. */
    fun progress(sessions: List<PlayedSession>, daysPerWeek: Int?): PlayerProgress {
        val tally = BadgeTally()
        val xp = mutableListOf<SessionXp>()
        val xpSessionsOnDay = mutableMapOf<LocalDate, Int>()
        val daysInWeek = mutableMapOf<LocalDate, MutableSet<LocalDate>>()

        for (session in sessions.sortedWith(compareBy({ it.start }, { it.id }))) {
            if (!counts(session)) {
                xp += SessionXp(session.id, 0, 0, 0, 0, 0)
                continue
            }
            val date = session.start.date
            val earnsBase = (xpSessionsOnDay[date] ?: 0) < GamificationRules.XP_SESSIONS_PER_DAY
            if (earnsBase) xpSessionsOnDay[date] = (xpSessionsOnDay[date] ?: 0) + 1

            val days = daysInWeek.getOrPut(weekStart(date)) { mutableSetOf() }
            val goalHit = daysPerWeek != null && daysPerWeek > 0 &&
                date !in days && days.size + 1 == daysPerWeek
            days += date

            xp += SessionXp(
                sessionId = session.id,
                session = if (earnsBase) GamificationRules.SESSION_XP else 0,
                sets = if (earnsBase) GamificationRules.SET_XP * minOf(session.workingSets, GamificationRules.MAX_XP_SETS) else 0,
                records = GamificationRules.RECORD_XP * session.records.size,
                checkIn = if (earnsBase && (session.readiness != null || session.feel != null)) GamificationRules.CHECK_IN_XP else 0,
                weeklyGoal = if (goalHit) GamificationRules.WEEKLY_GOAL_XP else 0,
            )
            tally.add(session)
        }

        val total = xp.sumOf { it.total }
        return PlayerProgress(total, level(total), xp, tally.badges())
    }

    /** Running totals of every [BadgeMetric], noting the workout each badge unlocked on. */
    private class BadgeTally {
        private val values = BadgeMetric.entries.associateWith { 0.0 }.toMutableMap()
        private val unlocked = mutableMapOf<Badge, Pair<Long, LocalDate>>()
        private val exercises = mutableSetOf<String>()
        private var lastDate: LocalDate? = null
        private var lastWeek: LocalDate? = null
        private var run = 0

        fun add(session: PlayedSession) {
            val date = session.start.date
            val week = weekStart(date)
            run = when (lastWeek) {
                week -> run
                week.plus(-1, DateTimeUnit.WEEK) -> run + 1
                else -> 1
            }
            val comeback = lastDate?.let { it.daysUntil(date) >= GamificationRules.COMEBACK_DAYS } == true
            lastWeek = week
            lastDate = date
            exercises += session.exerciseIds

            bump(BadgeMetric.WORKOUTS, 1.0)
            bump(BadgeMetric.RECORDS, session.records.size.toDouble())
            if (session.records.toSet() == PersonalRecordType.entries.toSet()) bump(BadgeMetric.TRIPLE_THREATS, 1.0)
            values[BadgeMetric.WEEK_STREAK] = maxOf(values.getValue(BadgeMetric.WEEK_STREAK), run.toDouble())
            bump(BadgeMetric.VOLUME_KG, session.volumeKg)
            val lowDay = (session.readiness ?: 5) <= LOW_CHECK_IN || (session.feel ?: 5) <= LOW_CHECK_IN
            if (session.lighter && lowDay) bump(BadgeMetric.LISTENED, 1.0)
            if (comeback) bump(BadgeMetric.COMEBACKS, 1.0)
            if (session.start.hour < GamificationRules.EARLY_BEFORE_HOUR) bump(BadgeMetric.EARLY_STARTS, 1.0)
            if (session.start.hour >= GamificationRules.LATE_FROM_HOUR) bump(BadgeMetric.LATE_STARTS, 1.0)
            values[BadgeMetric.EXERCISES] = exercises.size.toDouble()

            for (badge in Badge.entries) {
                if (badge !in unlocked && values.getValue(badge.metric) >= badge.target) unlocked[badge] = session.id to date
            }
        }

        private fun bump(metric: BadgeMetric, by: Double) {
            values[metric] = values.getValue(metric) + by
        }

        fun badges(): List<BadgeProgress> = Badge.entries.map { badge ->
            val by = unlocked[badge]
            BadgeProgress(badge, minOf(values.getValue(badge.metric), badge.target), by?.first, by?.second)
        }
    }
}
