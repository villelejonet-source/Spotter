package com.viktorolsson.spotter.core.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

// Levels and badges: XP and badges are worked out from the workout history every time
// (core/engine Gamification.kt), never stored, so every phone and the website agree. The
// website repeats the rules (web/src/lib/gamification.ts); core/model/fixtures/training-math.json
// ("gamification") pins them.

/** A finished workout, reduced to what levels and badges count. */
data class PlayedSession(
    val id: Long,
    /** When it started, in the phone's (or the website's display) time zone. */
    val start: LocalDateTime,
    val minutes: Int,
    val lighter: Boolean,
    val readiness: Int?,
    val feel: Int?,
    /** Completed sets that aren't warm-ups. */
    val workingSets: Int,
    /** As [WorkoutSummary.volumeKg]. */
    val volumeKg: Double,
    /** Exercises with at least one completed working set. */
    val exerciseIds: Set<String>,
    /** The personal records set in this session, one entry per record. */
    val records: List<PersonalRecordType>,
)

/** The XP one workout earned, by where it came from. */
data class SessionXp(
    val sessionId: Long,
    val session: Int,
    val sets: Int,
    val records: Int,
    val checkIn: Int,
    val weeklyGoal: Int,
    /** Weekly quests this workout finished. */
    val quests: Int = 0,
) {
    val total: Int get() = session + sets + records + checkIn + weeklyGoal + quests
}

/** Titles, each held from [fromLevel] until the next one. */
enum class LevelTitle(val fromLevel: Int) {
    FRESH_CHALK(1),
    BENCH_WARMER(3),
    GYM_REGULAR(5),
    PLATE_COLLECTOR(8),
    IRON_ADDICT(12),
    BARBELL_BARON(16),
    SQUAT_ROYALTY(20),
    IRON_LEGEND(25),
    MYTHICAL_BEAST(30),
}

data class Level(
    val number: Int,
    val title: LevelTitle,
    /** XP earned since reaching this level. */
    val xpIntoLevel: Int,
    /** XP between this level and the next. */
    val xpForNext: Int,
)

/** What a badge counts, over every workout that counts (see `Gamification`). */
enum class BadgeMetric {
    WORKOUTS,
    RECORDS,
    /** Workouts with all three kinds of record. */
    TRIPLE_THREATS,
    /** Longest run of weeks in a row with a workout. */
    WEEK_STREAK,
    VOLUME_KG,
    /** Lighter sessions taken on a low day. */
    LISTENED,
    /** Workouts after three weeks or more away. */
    COMEBACKS,
    EARLY_STARTS,
    LATE_STARTS,
    EXERCISES,
}

/** Unlocked when [metric] reaches [target]. Ids are the enum names, as on the website. */
enum class Badge(val metric: BadgeMetric, val target: Double) {
    FIRST_REP(BadgeMetric.WORKOUTS, 1.0),
    TEN_DOWN(BadgeMetric.WORKOUTS, 10.0),
    HALF_CENTURY(BadgeMetric.WORKOUTS, 50.0),
    CENTURION(BadgeMetric.WORKOUTS, 100.0),
    CLUB_250(BadgeMetric.WORKOUTS, 250.0),
    FIRST_PR(BadgeMetric.RECORDS, 1.0),
    PR_HUNTER(BadgeMetric.RECORDS, 10.0),
    RECORD_MACHINE(BadgeMetric.RECORDS, 50.0),
    TRIPLE_THREAT(BadgeMetric.TRIPLE_THREATS, 1.0),
    STREAK_4(BadgeMetric.WEEK_STREAK, 4.0),
    STREAK_12(BadgeMetric.WEEK_STREAK, 12.0),
    STREAK_26(BadgeMetric.WEEK_STREAK, 26.0),
    STREAK_52(BadgeMetric.WEEK_STREAK, 52.0),
    GRAND_PIANO(BadgeMetric.VOLUME_KG, 500.0),
    CAR(BadgeMetric.VOLUME_KG, 1_500.0),
    ELEPHANT(BadgeMetric.VOLUME_KG, 6_000.0),
    BLUE_WHALE(BadgeMetric.VOLUME_KG, 150_000.0),
    EIFFEL_TOWER(BadgeMetric.VOLUME_KG, 7_300_000.0),
    LISTENED_TO_YOUR_BODY(BadgeMetric.LISTENED, 1.0),
    COMEBACK_KID(BadgeMetric.COMEBACKS, 1.0),
    EARLY_BIRD(BadgeMetric.EARLY_STARTS, 5.0),
    NIGHT_OWL(BadgeMetric.LATE_STARTS, 5.0),
    EXPLORER(BadgeMetric.EXERCISES, 20.0),
}

data class BadgeProgress(
    val badge: Badge,
    /** How far the metric has got, never past the badge's target. */
    val current: Double,
    /** The workout that unlocked it; null while it's locked. */
    val unlockedBy: Long?,
    val unlockedOn: LocalDate?,
) {
    val unlocked: Boolean get() = unlockedBy != null
}

data class PlayerProgress(
    val totalXp: Int,
    val level: Level,
    /** Every workout's XP, oldest first; workouts that don't count earn nothing. */
    val sessions: List<SessionXp>,
    /** Every badge, in [Badge] order. */
    val badges: List<BadgeProgress>,
    /** The quests of the week containing the day asked about; null before quests began. */
    val thisWeek: WeekQuests? = null,
) {
    fun xpFor(sessionId: Long): SessionXp? = sessions.firstOrNull { it.sessionId == sessionId }

    fun unlockedBy(sessionId: Long): List<Badge> = badges.filter { it.unlockedBy == sessionId }.map { it.badge }
}

/** What a weekly quest counts, over the week's workouts that count. */
enum class QuestKind {
    WORKOUTS,
    /** Workouts with an exercise never done before. */
    NEW_EXERCISE,
    /** Workouts with a check-in, before or after. */
    CHECK_INS,
    RECORDS,
    /** Working sets. */
    SETS,
}

data class Quest(val kind: QuestKind, val target: Int)

data class QuestProgress(
    val quest: Quest,
    /** Never past the target. */
    val current: Int,
    /** The workout that finished it; null while it's open. */
    val doneBy: Long?,
) {
    val done: Boolean get() = doneBy != null
}

/** Three quests a week, the same on every phone and the website (see `Gamification.questsFor`). */
data class WeekQuests(val weekStart: LocalDate, val quests: List<QuestProgress>)

/** The lifts mastery rates, each from the best estimated 1RM of any of its exercises. */
enum class MasteryLift(val exerciseIds: List<String>) {
    SQUAT(listOf("back-squat", "paused-squat")),
    BENCH_PRESS(listOf("barbell-bench-press", "paused-bench-press")),
    DEADLIFT(listOf("deadlift", "sumo-deadlift", "paused-deadlift")),
    OVERHEAD_PRESS(listOf("overhead-press")),
}

enum class MasteryTier { BRONZE, SILVER, GOLD }

data class LiftMastery(
    val lift: MasteryLift,
    /** Best estimated 1RM; null when the lift was never done. */
    val bestKg: Double?,
    /** Best 1RM ÷ body weight. */
    val ratio: Double?,
    val tier: MasteryTier?,
    /** The next tier and the 1RM it needs; null at gold. */
    val nextTier: MasteryTier?,
    val nextKg: Double?,
)

/** Null [lifts] when there's no body weight to rate them against. */
data class Mastery(val lifts: List<LiftMastery>?)

/** A year of training, shown from December 1 to January 15 (see `Gamification.reviewYear`). */
data class YearInReview(
    val year: Int,
    val workouts: Int,
    val minutes: Int,
    val volumeKg: Double,
    val records: Int,
    val xp: Int,
    val levelStart: Int,
    val levelEnd: Int,
    /** Badges unlocked during the year, in [Badge] order. */
    val badges: List<Badge>,
    /** 1–12, the month with the most volume (the earliest on a tie); null without volume. */
    val strongestMonth: Int?,
    /** The exercise in the most workouts (the first id on a tie). */
    val favouriteExercise: String?,
)
