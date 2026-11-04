package com.viktorolsson.spotter.core.data.repository

import com.viktorolsson.spotter.core.data.db.dao.WorkoutDao
import com.viktorolsson.spotter.core.engine.Gamification
import com.viktorolsson.spotter.core.model.Mastery
import com.viktorolsson.spotter.core.model.MasteryLift
import com.viktorolsson.spotter.core.model.PlayedSession
import com.viktorolsson.spotter.core.model.PlayerProgress
import com.viktorolsson.spotter.core.model.TIMED_EXERCISE_IDS
import com.viktorolsson.spotter.core.model.YearInReview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/**
 * Levels and badges, worked out from the whole history whenever it changes (nothing is
 * stored, so synced workouts from another phone count straight away). Reads one row per
 * workout rather than whole sessions, so it stays cheap on a long history.
 */
class GamificationRepository(
    private val dao: WorkoutDao,
    private val profiles: UserProfileRepository,
    private val clock: Clock = Clock.System,
) {
    /** Progress, with the quests of the current week. */
    fun observeProgress(zone: TimeZone = TimeZone.currentSystemDefault()): Flow<PlayerProgress> =
        observeLevels(zone = zone).map { it.progress }

    /**
     * Progress and a year in review, from one pass over the history: [year]'s, or without
     * one the year shown from December 1 to January 15 (null the rest of the year). Moves on
     * to the next week's quests at midnight even when nothing else changes.
     */
    fun observeLevels(year: Int? = null, zone: TimeZone = TimeZone.currentSystemDefault()): Flow<Levels> =
        combine(observePlayed(zone), observeToday(zone)) { played, today ->
            val progress = Gamification.progress(played.sessions, played.daysPerWeek, today)
            val shown = year ?: Gamification.reviewYear(today)
            Levels(progress, shown?.let { Gamification.yearInReview(it, played.sessions, progress) })
        }.flowOn(Dispatchers.Default)

    /** Each mastery lift's tier, from its best estimated 1RM and the profile's body weight. */
    fun observeMastery(): Flow<Mastery> =
        combine(dao.observeBestEstimatedOneRepMaxes(MasteryLift.entries.flatMap { it.exerciseIds }), profiles.observe()) { best, profile ->
            val byExercise = best.associate { it.exerciseId to it.value }
            Gamification.mastery(
                MasteryLift.entries.mapNotNull { lift -> lift.exerciseIds.mapNotNull(byExercise::get).maxOrNull()?.let { lift to it } }.toMap(),
                profile?.sex,
                profile?.bodyWeightKg,
            )
        }.flowOn(Dispatchers.Default)

    private class Played(val sessions: List<PlayedSession>, val daysPerWeek: Int?)

    /** Today's date, again whenever it changes. */
    private fun observeToday(zone: TimeZone): Flow<LocalDate> = flow {
        while (true) {
            emit(clock.now().toLocalDateTime(zone).date)
            delay(1.minutes)
        }
    }.distinctUntilChanged()

    private fun observePlayed(zone: TimeZone): Flow<Played> =
        combine(
            dao.observePlayedSessions(TIMED_EXERCISE_IDS.toList()),
            dao.observePlayedExercises(),
            dao.observeRecordTypes(),
            profiles.observe(),
        ) { sessions, exercises, records, profile ->
            val exerciseIds = exercises.groupBy({ it.sessionId }, { it.exerciseId })
            val recordTypes = records.groupBy({ it.sessionId }, { it.type })
            val played = sessions.map { row ->
                PlayedSession(
                    id = row.id,
                    start = row.startedAt.toLocalDateTime(zone),
                    minutes = (row.endedAt - row.startedAt).inWholeMinutes.toInt(),
                    lighter = row.isDeload,
                    readiness = row.readiness,
                    feel = row.feel,
                    workingSets = row.workingSets,
                    volumeKg = row.volumeKg,
                    exerciseIds = exerciseIds[row.id].orEmpty().toSet(),
                    records = recordTypes[row.id].orEmpty(),
                )
            }
            Played(played, profile?.daysPerWeek)
        }
}

/** [GamificationRepository.observeLevels]: progress and, when there is one, a year in review. */
data class Levels(val progress: PlayerProgress, val yearInReview: YearInReview?)
