package com.viktorolsson.spotter.core.data.health

import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.metadata.Metadata
import com.viktorolsson.spotter.core.model.HealthWorkout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.time.toJavaInstant

/**
 * What a workout looks like as Health Connect records. The calls to Health Connect itself
 * aren't tested here: its test double only exists for a pre-release of the library.
 */
class HealthConnectStoreTest {
    private val start = Instant.parse("2026-11-13T17:00:00Z")
    private val workout = HealthWorkout(id = "abc123", version = 10, title = "Upper A", start = start, end = start + 60.minutes, activeKcal = 298)

    @Test
    fun aWorkoutBecomesAStrengthSessionAndItsCalories() {
        val records = workout.toRecords(ZoneId.of("Europe/Stockholm"))
        val session = records.filterIsInstance<ExerciseSessionRecord>().single()
        val energy = records.filterIsInstance<ActiveCaloriesBurnedRecord>().single()

        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING, session.exerciseType)
        assertEquals("Upper A", session.title)
        assertEquals(start.toJavaInstant(), session.startTime)
        assertEquals((start + 60.minutes).toJavaInstant(), session.endTime)
        assertEquals(ZoneOffset.ofHours(1), session.startZoneOffset)
        assertEquals(298.0, energy.energy.inKilocalories, 0.001)
        assertEquals(session.startTime, energy.startTime)
        assertEquals(session.endTime, energy.endTime)
        // Both carry the workout's id and version, which is what makes a second write replace the first.
        listOf(session.metadata, energy.metadata).forEach {
            assertEquals("abc123", it.clientRecordId)
            assertEquals(10L, it.clientRecordVersion)
            assertEquals(Metadata.RECORDING_METHOD_MANUAL_ENTRY, it.recordingMethod)
        }
    }

    @Test
    fun withoutCaloriesOnlyTheSessionIsWritten() {
        assertTrue(workout.copy(activeKcal = null).toRecords(ZoneOffset.UTC).single() is ExerciseSessionRecord)
    }
}
