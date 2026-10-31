package com.viktorolsson.spotter.core.engine

import com.viktorolsson.spotter.core.model.PersonalRecordType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecordsTest {
    @Test
    fun `a set with no reps sets no baseline`() {
        val bests = PersonalRecords.bests(listOf(listOf(LoggedSet(140.0, 0), LoggedSet(80.0, 8))))
        assertEquals(epley(80.0, 8), bests.estimatedOneRepMax!!, 1e-9)
    }

    @Test
    fun `a failed heavy set is no record`() {
        val previous = PersonalRecords.bests(listOf(listOf(LoggedSet(80.0, 8))))
        val records = PersonalRecords.detect(listOf(LoggedSet(200.0, 0), LoggedSet(80.0, 8)), previous)
        assertTrue(records.none { it.type == PersonalRecordType.ESTIMATED_1RM }, "$records")
    }

    private fun epley(kg: Double, reps: Int) = com.viktorolsson.spotter.core.model.epley(kg, reps)
}
