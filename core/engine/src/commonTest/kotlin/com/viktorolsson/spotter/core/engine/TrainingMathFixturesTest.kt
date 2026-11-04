package com.viktorolsson.spotter.core.engine

import com.viktorolsson.spotter.core.model.Badge
import com.viktorolsson.spotter.core.model.ExerciseSessionLog
import com.viktorolsson.spotter.core.model.FeelSummary
import com.viktorolsson.spotter.core.model.MasteryLift
import com.viktorolsson.spotter.core.model.MasteryTier
import com.viktorolsson.spotter.core.model.NumberInput
import com.viktorolsson.spotter.core.model.PersonalRecordType
import com.viktorolsson.spotter.core.model.PlayedSession
import com.viktorolsson.spotter.core.model.SetType
import com.viktorolsson.spotter.core.model.Sex
import com.viktorolsson.spotter.core.model.TIMED_EXERCISE_IDS
import com.viktorolsson.spotter.core.model.WorkoutSet
import com.viktorolsson.spotter.core.model.countsTowardsChange
import com.viktorolsson.spotter.core.model.epley
import com.viktorolsson.spotter.core.model.formatChange
import com.viktorolsson.spotter.core.model.isRoughDay
import com.viktorolsson.spotter.core.model.percentChange
import com.viktorolsson.spotter.core.model.weekStreak
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Instant

/**
 * core/model/fixtures/training-math.json: the cases the website's tests check too
 * (web/src/lib/training-math.test.ts), so the two can't drift apart.
 */
class TrainingMathFixturesTest {
    @Serializable
    private data class Fixtures(
        val timedExerciseIds: Set<String>,
        val decimalInput: List<DecimalCase>,
        val epley: List<EpleyCase>,
        val exerciseSessions: List<SessionCase>,
        val percentChange: List<ChangeCase>,
        val weekStreak: List<StreakCase>,
        val checkIns: List<CheckInCase>,
        val feelSummary: List<FeelCase>,
        val gamification: GamificationCases,
    )

    @Serializable
    private data class GamificationCases(
        val levels: List<LevelCase>,
        val histories: List<HistoryCase>,
        val badges: List<BadgeCase>,
        val questWeeks: List<QuestWeekCase>,
        val mastery: List<MasteryCase>,
        val reviewYear: List<ReviewYearCase>,
        val yearInReview: List<YearInReviewCase>,
    )

    @Serializable
    private data class BadgeCase(val id: String, val metric: String, val target: Double)

    @Serializable
    private data class QuestWeekCase(val weekStart: LocalDate, val daysPerWeek: Int?, val quests: List<QuestTarget>?)

    @Serializable
    private data class QuestTarget(val kind: String, val target: Int)

    @Serializable
    private data class QuestCase(val kind: String, val target: Int, val current: Int, val doneBy: Long?)

    @Serializable
    private data class WeekCase(val weekStart: LocalDate, val quests: List<QuestCase>)

    @Serializable
    private data class MasteryCase(
        val name: String,
        val sex: Sex,
        val bodyWeightKg: Double?,
        val bestKg: Map<MasteryLift, Double>,
        val lifts: List<LiftCase>?,
    )

    @Serializable
    private data class LiftCase(val lift: MasteryLift, val tier: MasteryTier?, val nextTier: MasteryTier?, val nextKg: Double?)

    @Serializable
    private data class ReviewYearCase(val today: LocalDate, val year: Int?)

    @Serializable
    private data class YearInReviewCase(
        val name: String,
        val daysPerWeek: Int?,
        val sessions: List<HistorySession>,
        val years: Map<String, ReviewCase?>,
    )

    @Serializable
    private data class ReviewCase(
        val workouts: Int,
        val minutes: Int,
        val volumeKg: Double,
        val records: Int,
        val xp: Int,
        val levelStart: Int,
        val levelEnd: Int,
        val badges: List<String>,
        val strongestMonth: Int?,
        val favouriteExercise: String?,
    )

    @Serializable
    private data class LevelCase(val xp: Int, val level: Int, val title: String, val xpIntoLevel: Int, val xpForNext: Int)

    @Serializable
    private data class HistoryCase(
        val name: String,
        val daysPerWeek: Int?,
        val sessions: List<HistorySession>,
        val xp: List<XpCase>,
        val totalXp: Int,
        val level: Int,
        val unlocked: Map<String, Long>,
        val progress: Map<String, Double>,
        /** The week [Gamification.progress] reports; checked only when there's a day to ask about. */
        val today: LocalDate? = null,
        val thisWeek: WeekCase? = null,
    )

    @Serializable
    private data class HistorySession(
        val id: Long,
        val start: LocalDateTime,
        val minutes: Int,
        val lighter: Boolean = false,
        val readiness: Int? = null,
        val feel: Int? = null,
        val workingSets: Int,
        val volumeKg: Double = 0.0,
        val exerciseIds: Set<String> = emptySet(),
        val records: List<PersonalRecordType> = emptyList(),
    )

    @Serializable
    private data class XpCase(
        val id: Long,
        val session: Int,
        val sets: Int,
        val records: Int,
        val checkIn: Int,
        val weeklyGoal: Int,
        val quests: Int = 0,
    )

    private fun HistorySession.played() =
        PlayedSession(id, start, minutes, lighter, readiness, feel, workingSets, volumeKg, exerciseIds, records)

    @Serializable
    private data class CheckInCase(
        val name: String,
        val lighter: Boolean,
        val readiness: Int?,
        val feel: Int?,
        val roughDay: Boolean,
        val countsTowardsChange: Boolean,
    )

    @Serializable
    private data class FeelCase(val name: String, val checkIns: List<List<Int?>>, val summary: FeelSummaryCase?)

    @Serializable
    private data class FeelSummaryCase(val rated: Int, val averageReadiness: Double?, val averageFeel: Double?, val lowDays: Int)

    @Serializable
    private data class ChangeCase(val current: Double, val previous: Double, val percent: Int?, val text: String? = null)

    @Serializable
    private data class StreakCase(val name: String, val today: LocalDate, val dates: List<LocalDate>, val streak: Int)

    @Serializable
    private data class DecimalCase(val text: String, val value: Double? = null, val result: String? = null)

    @Serializable
    private data class EpleyCase(val weightKg: Double, val reps: Int, val oneRepMaxKg: Double)

    @Serializable
    private data class FixtureSet(val weightKg: Double?, val reps: Int?, val warmup: Boolean = false)

    @Serializable
    private data class SessionCase(
        val name: String,
        val exerciseId: String,
        val sets: List<FixtureSet>,
        val volumeKg: Double,
        val oneRepMaxKg: Double?,
        val bestSet: FixtureSet?,
    )

    private val fixtures = json.decodeFromString<Fixtures>(readTextFile(TRAINING_MATH_PATH))

    private fun assertClose(expected: Double?, actual: Double?, what: String) {
        if (expected == null || actual == null) return assertEquals(expected, actual, what)
        assertTrue(abs(expected - actual) < 1e-4, "$what: expected $expected, got $actual")
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }

    @Test
    fun timedExercisesMatch() = assertEquals(fixtures.timedExerciseIds, TIMED_EXERCISE_IDS)

    @Test
    fun decimalInput() = fixtures.decimalInput.forEach { case ->
        val expected = when (case.result) {
            "blank" -> NumberInput.Blank
            "invalid" -> NumberInput.Invalid
            null -> NumberInput.Value(case.value ?: fail("\"${case.text}\" has no value"))
            else -> fail("Unknown result ${case.result}")
        }
        assertEquals(expected, NumberInput.decimal(case.text), "decimal(\"${case.text}\")")
    }

    @Test
    fun epleyCases() = fixtures.epley.forEach { case ->
        assertClose(case.oneRepMaxKg, epley(case.weightKg, case.reps), "epley(${case.weightKg}, ${case.reps})")
        assertClose(case.oneRepMaxKg, LiftResult(case.weightKg, case.reps).estimatedOneRepMax, "LiftResult(${case.weightKg}, ${case.reps})")
    }

    @Test
    fun exerciseSessions() = fixtures.exerciseSessions.forEach { case ->
        val log = ExerciseSessionLog(
            sessionId = 1,
            date = Instant.fromEpochMilliseconds(0),
            sets = case.sets.mapIndexed { i, s ->
                WorkoutSet(
                    id = i.toLong(), position = i, weightKg = s.weightKg, reps = s.reps, rir = null,
                    setType = if (s.warmup) SetType.WARMUP else SetType.WORKING,
                    completedAt = Instant.fromEpochMilliseconds(0), notes = null,
                )
            },
            timed = case.exerciseId in TIMED_EXERCISE_IDS,
        )
        assertClose(case.volumeKg, log.volumeKg, "${case.name}: volume")
        assertClose(case.oneRepMaxKg, log.estimatedOneRepMax, "${case.name}: 1RM")
        assertEquals(case.bestSet, log.bestSet?.let { FixtureSet(it.weightKg, it.reps) }, "${case.name}: best set")
    }

    @Test
    fun percentChanges() = fixtures.percentChange.forEach { case ->
        val percent = percentChange(case.current, case.previous)
        assertEquals(case.percent, percent, "percentChange(${case.current}, ${case.previous})")
        if (percent != null) assertEquals(case.text, formatChange(percent), "formatChange($percent)")
    }

    @Test
    fun weekStreaks() = fixtures.weekStreak.forEach { case ->
        assertEquals(case.streak, weekStreak(case.dates, case.today), case.name)
    }

    @Test
    fun checkIns() = fixtures.checkIns.forEach { case ->
        assertEquals(case.roughDay, isRoughDay(case.lighter, case.readiness, case.feel), "${case.name}: rough day")
        assertEquals(case.countsTowardsChange, countsTowardsChange(case.lighter, case.readiness, case.feel), "${case.name}: counts")
    }

    @Test
    fun feelSummaries() = fixtures.feelSummary.forEach { case ->
        val summary = FeelSummary.of(case.checkIns.map { it[0] to it[1] })
        val expected = case.summary ?: return@forEach assertEquals(null, summary, case.name)
        if (summary == null) fail("${case.name}: expected a summary")
        assertEquals(expected.rated, summary.rated, "${case.name}: rated")
        assertClose(expected.averageReadiness, summary.averageReadiness, "${case.name}: before")
        assertClose(expected.averageFeel, summary.averageFeel, "${case.name}: after")
        assertEquals(expected.lowDays, summary.lowDays, "${case.name}: low days")
    }

    @Test
    fun levels() = fixtures.gamification.levels.forEach { case ->
        val level = Gamification.level(case.xp)
        assertEquals(case.level, level.number, "level(${case.xp})")
        assertEquals(case.title, level.title.name, "title(${case.xp})")
        assertEquals(case.xpIntoLevel, level.xpIntoLevel, "into level(${case.xp})")
        assertEquals(case.xpForNext, level.xpForNext, "for next(${case.xp})")
    }

    @Test
    fun gamificationHistories() = fixtures.gamification.histories.forEach { case ->
        val progress = Gamification.progress(case.sessions.map { it.played() }, case.daysPerWeek, case.today)
        assertEquals(
            case.xp,
            progress.sessions.map { XpCase(it.sessionId, it.session, it.sets, it.records, it.checkIn, it.weeklyGoal, it.quests) },
            "${case.name}: XP",
        )
        assertEquals(case.totalXp, progress.totalXp, "${case.name}: total")
        assertEquals(case.level, progress.level.number, "${case.name}: level")
        assertEquals(
            case.unlocked,
            progress.badges.filter { it.unlocked }.associate { it.badge.name to it.unlockedBy!! },
            "${case.name}: unlocked",
        )
        case.progress.forEach { (badge, current) ->
            assertClose(current, progress.badges.single { it.badge == Badge.valueOf(badge) }.current, "${case.name}: $badge")
        }
        if (case.today != null) {
            assertEquals(
                case.thisWeek,
                progress.thisWeek?.let { week ->
                    WeekCase(week.weekStart, week.quests.map { QuestCase(it.quest.kind.name, it.quest.target, it.current, it.doneBy) })
                },
                "${case.name}: this week",
            )
        }
    }

    @Test
    fun everyBadge() = assertEquals(
        fixtures.gamification.badges,
        Badge.entries.map { BadgeCase(it.name, it.metric.name, it.target) },
    )

    @Test
    fun questWeeks() = fixtures.gamification.questWeeks.forEach { case ->
        assertEquals(
            case.quests,
            Gamification.questsFor(case.weekStart, case.daysPerWeek)?.map { QuestTarget(it.kind.name, it.target) },
            "quests for ${case.weekStart}",
        )
    }

    @Test
    fun mastery() = fixtures.gamification.mastery.forEach { case ->
        val lifts = Gamification.mastery(case.bestKg, case.sex, case.bodyWeightKg).lifts
        val expected = case.lifts ?: return@forEach assertEquals(null, lifts, case.name)
        if (lifts == null) fail("${case.name}: expected lifts")
        assertEquals(expected.map { it.lift }, lifts.map { it.lift }, case.name)
        expected.zip(lifts).forEach { (want, got) ->
            assertEquals(want.tier, got.tier, "${case.name}: ${want.lift} tier")
            assertEquals(want.nextTier, got.nextTier, "${case.name}: ${want.lift} next")
            assertClose(want.nextKg, got.nextKg, "${case.name}: ${want.lift} next kg")
        }
    }

    @Test
    fun reviewYears() = fixtures.gamification.reviewYear.forEach { case ->
        assertEquals(case.year, Gamification.reviewYear(case.today), "review year on ${case.today}")
    }

    @Test
    fun yearsInReview() = fixtures.gamification.yearInReview.forEach { case ->
        val sessions = case.sessions.map { it.played() }
        val progress = Gamification.progress(sessions, case.daysPerWeek)
        case.years.forEach { (year, expected) ->
            val review = Gamification.yearInReview(year.toInt(), sessions, progress)
            assertEquals(
                expected,
                review?.let {
                    ReviewCase(
                        it.workouts, it.minutes, it.volumeKg, it.records, it.xp, it.levelStart, it.levelEnd,
                        it.badges.map { badge -> badge.name }, it.strongestMonth, it.favouriteExercise,
                    )
                },
                "${case.name}: $year",
            )
        }
    }
}
