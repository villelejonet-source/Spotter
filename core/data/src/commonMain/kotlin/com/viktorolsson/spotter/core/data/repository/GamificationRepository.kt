package com.viktorolsson.spotter.core.data.repository

import com.viktorolsson.spotter.core.data.db.dao.WorkoutDao
import com.viktorolsson.spotter.core.engine.Gamification
import com.viktorolsson.spotter.core.model.PlayedSession
import com.viktorolsson.spotter.core.model.PlayerProgress
import com.viktorolsson.spotter.core.model.TIMED_EXERCISE_IDS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Levels and badges, worked out from the whole history whenever it changes (nothing is
 * stored, so synced workouts from another phone count straight away). Reads one row per
 * workout rather than whole sessions, so it stays cheap on a long history.
 */
class GamificationRepository(
    private val dao: WorkoutDao,
    private val profiles: UserProfileRepository,
) {
    fun observeProgress(zone: TimeZone = TimeZone.currentSystemDefault()): Flow<PlayerProgress> =
        combine(
            dao.observePlayedSessions(TIMED_EXERCISE_IDS.toList()),
            dao.observePlayedExercises(),
            dao.observeRecordTypes(),
            profiles.observe(),
        ) { sessions, exercises, records, profile ->
            val exerciseIds = exercises.groupBy({ it.sessionId }, { it.exerciseId })
            val recordTypes = records.groupBy({ it.sessionId }, { it.type })
            Gamification.progress(
                sessions.map { row ->
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
                },
                profile?.daysPerWeek,
            )
        }.flowOn(Dispatchers.Default)
}
