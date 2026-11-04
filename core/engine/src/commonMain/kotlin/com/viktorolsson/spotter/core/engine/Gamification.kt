package com.viktorolsson.spotter.core.engine

import com.viktorolsson.spotter.core.model.Badge
import com.viktorolsson.spotter.core.model.BadgeMetric
import com.viktorolsson.spotter.core.model.BadgeProgress
import com.viktorolsson.spotter.core.model.LOW_CHECK_IN
import com.viktorolsson.spotter.core.model.Level
import com.viktorolsson.spotter.core.model.LevelTitle
import com.viktorolsson.spotter.core.model.LiftMastery
import com.viktorolsson.spotter.core.model.Mastery
import com.viktorolsson.spotter.core.model.MasteryLift
import com.viktorolsson.spotter.core.model.MasteryTier
import com.viktorolsson.spotter.core.model.PersonalRecordType
import com.viktorolsson.spotter.core.model.PlayedSession
import com.viktorolsson.spotter.core.model.PlayerProgress
import com.viktorolsson.spotter.core.model.Quest
import com.viktorolsson.spotter.core.model.QuestKind
import com.viktorolsson.spotter.core.model.QuestProgress
import com.viktorolsson.spotter.core.model.SessionXp
import com.viktorolsson.spotter.core.model.Sex
import com.viktorolsson.spotter.core.model.WeekQuests
import com.viktorolsson.spotter.core.model.YearInReview
import com.viktorolsson.spotter.core.model.weekStart
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.daysUntil
import kotlinx.datetime.number
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

    /**
     * The first week with quests: the week they shipped, so the update itself doesn't hand
     * out XP for weeks already trained. A Monday.
     */
    val QUESTS_FROM = LocalDate(2026, 11, 9)
    const val QUEST_XP = 50
    /** The workouts quest without a weekly goal in the profile. */
    const val QUEST_WORKOUTS = 3
    const val QUEST_CHECK_INS = 3
    const val QUEST_SETS = 40

    /** Bronze, silver and gold, as best estimated 1RM ÷ body weight. */
    val MASTERY: Map<Sex, Map<MasteryLift, List<Double>>> = mapOf(
        Sex.MALE to mapOf(
            MasteryLift.SQUAT to listOf(1.0, 1.5, 2.0),
            MasteryLift.BENCH_PRESS to listOf(0.75, 1.0, 1.5),
            MasteryLift.DEADLIFT to listOf(1.25, 1.75, 2.25),
            MasteryLift.OVERHEAD_PRESS to listOf(0.5, 0.75, 1.0),
        ),
        Sex.FEMALE to mapOf(
            MasteryLift.SQUAT to listOf(0.75, 1.0, 1.5),
            MasteryLift.BENCH_PRESS to listOf(0.5, 0.75, 1.0),
            MasteryLift.DEADLIFT to listOf(1.0, 1.25, 1.75),
            MasteryLift.OVERHEAD_PRESS to listOf(0.35, 0.5, 0.65),
        ),
    )

    /** The year in review shows through this day of January. */
    const val REVIEW_UNTIL_JANUARY = 15
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

    /**
     * The week's quests: always the workouts one, then two of the others in turn, moving on
     * one each week (so the website picks the same without sharing any state). Null before
     * [GamificationRules.QUESTS_FROM].
     */
    fun questsFor(weekStart: LocalDate, daysPerWeek: Int?): List<Quest>? {
        if (weekStart < GamificationRules.QUESTS_FROM) return null
        val turn = (weekStart.toEpochDays() / 7).toInt()
        val others = ROTATING_QUESTS
        return listOf(
            Quest(QuestKind.WORKOUTS, daysPerWeek?.takeIf { it in 1..7 } ?: GamificationRules.QUEST_WORKOUTS),
            others[turn % others.size].let { Quest(it, questTarget(it)) },
            others[(turn + 1) % others.size].let { Quest(it, questTarget(it)) },
        )
    }

    private val ROTATING_QUESTS = listOf(QuestKind.NEW_EXERCISE, QuestKind.CHECK_INS, QuestKind.RECORDS, QuestKind.SETS)

    private fun questTarget(kind: QuestKind): Int = when (kind) {
        QuestKind.WORKOUTS -> GamificationRules.QUEST_WORKOUTS
        QuestKind.NEW_EXERCISE, QuestKind.RECORDS -> 1
        QuestKind.CHECK_INS -> GamificationRules.QUEST_CHECK_INS
        QuestKind.SETS -> GamificationRules.QUEST_SETS
    }

    /**
     * [daysPerWeek]: the profile's weekly goal; null or 0 means no goal bonus. [today]: the
     * day whose week [PlayerProgress.thisWeek] shows; null leaves it out.
     */
    fun progress(sessions: List<PlayedSession>, daysPerWeek: Int?, today: LocalDate? = null): PlayerProgress {
        val tally = BadgeTally()
        val xp = mutableListOf<SessionXp>()
        val xpSessionsOnDay = mutableMapOf<LocalDate, Int>()
        val daysInWeek = mutableMapOf<LocalDate, MutableSet<LocalDate>>()
        val weeks = mutableMapOf<LocalDate, MutableList<QuestProgress>?>()
        val seenExercises = mutableSetOf<String>()

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

            val newExercise = !seenExercises.containsAll(session.exerciseIds)
            seenExercises += session.exerciseIds
            val quests = weeks.getOrPut(weekStart(date)) { openQuests(weekStart(date), daysPerWeek) }
            var questXp = 0
            if (quests != null) for (i in quests.indices) {
                val progress = quests[i]
                if (progress.done) continue
                val add = when (progress.quest.kind) {
                    QuestKind.WORKOUTS -> 1
                    QuestKind.NEW_EXERCISE -> if (newExercise) 1 else 0
                    QuestKind.CHECK_INS -> if (session.readiness != null || session.feel != null) 1 else 0
                    QuestKind.RECORDS -> session.records.size
                    QuestKind.SETS -> session.workingSets
                }
                val current = minOf(progress.current + add, progress.quest.target)
                val finished = current >= progress.quest.target
                if (finished) questXp += GamificationRules.QUEST_XP
                quests[i] = progress.copy(current = current, doneBy = if (finished) session.id else null)
            }

            xp += SessionXp(
                sessionId = session.id,
                session = if (earnsBase) GamificationRules.SESSION_XP else 0,
                sets = if (earnsBase) GamificationRules.SET_XP * minOf(session.workingSets, GamificationRules.MAX_XP_SETS) else 0,
                records = GamificationRules.RECORD_XP * session.records.size,
                checkIn = if (earnsBase && (session.readiness != null || session.feel != null)) GamificationRules.CHECK_IN_XP else 0,
                weeklyGoal = if (goalHit) GamificationRules.WEEKLY_GOAL_XP else 0,
                quests = questXp,
            )
            tally.add(session)
        }

        val total = xp.sumOf { it.total }
        val thisWeek = today?.let { weekStart(it) }?.let { week ->
            (weeks[week] ?: openQuests(week, daysPerWeek))?.let { WeekQuests(week, it.toList()) }
        }
        return PlayerProgress(total, level(total), xp, tally.badges(), thisWeek)
    }

    private fun openQuests(weekStart: LocalDate, daysPerWeek: Int?): MutableList<QuestProgress>? =
        questsFor(weekStart, daysPerWeek)?.map { QuestProgress(it, 0, null) }?.toMutableList()

    /**
     * Each lift's tier from its best estimated 1RM ([bestKg], by lift) against body weight.
     * Without a body weight there's nothing to rate against, and [Mastery.lifts] is null.
     */
    fun mastery(bestKg: Map<MasteryLift, Double>, sex: Sex?, bodyWeightKg: Double?): Mastery {
        if (sex == null || bodyWeightKg == null || bodyWeightKg <= 0) return Mastery(null)
        val standards = GamificationRules.MASTERY.getValue(sex)
        return Mastery(
            MasteryLift.entries.map { lift ->
                val best = bestKg[lift]?.takeIf { it > 0 }
                val ratio = best?.let { it / bodyWeightKg }
                val thresholds = standards.getValue(lift)
                val reached = ratio?.let { r -> thresholds.indexOfLast { r >= it } } ?: -1
                val next = MasteryTier.entries.getOrNull(reached + 1)
                LiftMastery(
                    lift = lift,
                    bestKg = best,
                    ratio = ratio,
                    tier = MasteryTier.entries.getOrNull(reached),
                    nextTier = next,
                    nextKg = next?.let { thresholds[it.ordinal] * bodyWeightKg },
                )
            },
        )
    }

    /** The year to look back on: the current one in December, last year until January 15. */
    fun reviewYear(today: LocalDate): Int? = when {
        today.month == Month.DECEMBER -> today.year
        today.month == Month.JANUARY && today.day <= GamificationRules.REVIEW_UNTIL_JANUARY -> today.year - 1
        else -> null
    }

    /** [progress] is [progress] over the same [sessions]. Null when no workout that year counted. */
    fun yearInReview(year: Int, sessions: List<PlayedSession>, progress: PlayerProgress): YearInReview? {
        val counted = sessions.filter { counts(it) }
        val inYear = counted.filter { it.start.year == year }
        if (inYear.isEmpty()) return null
        val xpBefore = counted.filter { it.start.year < year }.sumOf { progress.xpFor(it.id)?.total ?: 0 }
        val xpInYear = inYear.sumOf { progress.xpFor(it.id)?.total ?: 0 }
        val volumeByMonth = inYear.groupBy { it.start.month.number }.mapValues { (_, s) -> s.sumOf { it.volumeKg } }
        val usage = inYear.flatMap { it.exerciseIds }.groupingBy { it }.eachCount()
        return YearInReview(
            year = year,
            workouts = inYear.size,
            minutes = inYear.sumOf { it.minutes },
            volumeKg = inYear.sumOf { it.volumeKg },
            records = inYear.sumOf { it.records.size },
            xp = xpInYear,
            levelStart = level(xpBefore).number,
            levelEnd = level(xpBefore + xpInYear).number,
            badges = progress.badges.filter { it.unlockedOn?.year == year }.map { it.badge },
            strongestMonth = volumeByMonth.filterValues { it > 0 }.entries
                .sortedWith(compareByDescending<Map.Entry<Int, Double>> { it.value }.thenBy { it.key })
                .firstOrNull()?.key,
            favouriteExercise = usage.entries
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                .firstOrNull()?.key,
        )
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
