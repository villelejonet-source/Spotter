package com.viktorolsson.spotter.core.engine

import com.viktorolsson.spotter.core.engine.Fixtures.byId
import com.viktorolsson.spotter.core.model.BodyWeightEntry
import com.viktorolsson.spotter.core.model.Goal
import com.viktorolsson.spotter.core.model.MuscleGroup
import com.viktorolsson.spotter.core.model.PersonalRecordType
import com.viktorolsson.spotter.core.model.VolumeStatus
import com.viktorolsson.spotter.core.model.epley
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ProgressStatsTest {
    private fun sets(weight: Double?, vararg reps: Int) = reps.map { LoggedSet(weight, it) }

    @Test
    fun `first session sets a baseline without PRs`() {
        assertTrue(PersonalRecords.detect(sets(80.0, 8, 8, 8), ExerciseBests.NONE).isEmpty())
    }

    @Test
    fun `heavier or more reps gives 1RM, rep and volume PRs`() {
        // Previous best session volume: 75 × 27 = 2025 kg.
        val previous = PersonalRecords.bests(listOf(sets(80.0, 8, 8, 7), sets(75.0, 10, 9, 8)))
        val records = PersonalRecords.detect(sets(80.0, 9, 9, 8), previous).associateBy { it.type }

        assertEquals(epley(80.0, 9), records.getValue(PersonalRecordType.ESTIMATED_1RM).value, 0.001)
        val repPr = records.getValue(PersonalRecordType.REPS_AT_WEIGHT)
        assertEquals(80.0 to 9, repPr.weightKg to repPr.reps)
        assertEquals(80.0 * 26, records.getValue(PersonalRecordType.VOLUME).value, 0.001)

        // Volume below the best session isn't a volume PR, even with a heavier top set.
        val noVolume = PersonalRecords.detect(sets(82.5, 8, 8), previous).map { it.type }
        assertTrue(PersonalRecordType.VOLUME !in noVolume && PersonalRecordType.ESTIMATED_1RM in noVolume)
    }

    @Test
    fun `matching or lighter work is not a PR, and reps at a lighter weight must beat heavier bests too`() {
        val previous = PersonalRecords.bests(listOf(sets(80.0, 8, 8, 8)))
        assertTrue(PersonalRecords.detect(sets(80.0, 8, 8, 8), previous).isEmpty())
        // 8 reps at 70 kg is not a rep PR: 8 reps were already done at 80 kg.
        assertTrue(PersonalRecords.detect(sets(70.0, 8), previous).none { it.type == PersonalRecordType.REPS_AT_WEIGHT })
        // 9 at 70 kg is.
        assertTrue(PersonalRecords.detect(sets(70.0, 9), previous).any { it.type == PersonalRecordType.REPS_AT_WEIGHT })
    }

    @Test
    fun `bodyweight sets only produce rep PRs`() {
        val previous = PersonalRecords.bests(listOf(sets(null, 10, 10)))
        val records = PersonalRecords.detect(sets(null, 12, 10), previous)
        assertEquals(listOf(PersonalRecordType.REPS_AT_WEIGHT), records.map { it.type })
    }

    @Test
    fun `weekly sets count primary muscles fully and secondary muscles half`() {
        // Bench: chest primary; front delts and triceps secondary. Row: upper back + lats primary.
        val week = List(3) { byId.getValue("barbell-bench-press") } + List(4) { byId.getValue("barbell-row") }
        val volume = MuscleBalance.weekly(week, Goal.HYPERTROPHY).associateBy { it.group }
        assertEquals(3.0, volume.getValue(MuscleGroup.CHEST).sets, 0.0)
        assertEquals(1.5, volume.getValue(MuscleGroup.TRICEPS).sets, 0.0)
        assertEquals(4.0, volume.getValue(MuscleGroup.BACK).sets, 0.0) // lats + upper back are one group
        assertEquals(VolumeStatus.UNDER, volume.getValue(MuscleGroup.CHEST).status)
        assertEquals(10, volume.getValue(MuscleGroup.CHEST).targetLow)

        val lots = MuscleBalance.weekly(List(25) { byId.getValue("barbell-bench-press") }, Goal.STRENGTH)
        assertEquals(VolumeStatus.OVER, lots.first { it.group == MuscleGroup.CHEST }.status)
    }

    @Test
    fun `body weight trend is a trailing seven-day mean`() {
        val start = LocalDate.of(2026, 10, 1)
        val entries = listOf(80.0, 81.0, 79.0, 80.0).mapIndexed { i, kg -> BodyWeightEntry(i.toLong(), start.plusDays(i * 3L), kg) }
        val points = BodyWeightTrend.points(entries.reversed())
        assertEquals(entries.map { it.date }, points.map { it.date })
        assertEquals(80.0, points[0].sevenDayAverageKg, 0.001)
        assertEquals(80.5, points[1].sevenDayAverageKg, 0.001) // Oct 1 and 4
        assertEquals(80.0, points[2].sevenDayAverageKg, 0.001) // Oct 1, 4 and 7
        assertEquals(80.0, points[3].sevenDayAverageKg, 0.001) // Oct 4, 7 and 10 (Oct 1 is 9 days back)
    }
}
