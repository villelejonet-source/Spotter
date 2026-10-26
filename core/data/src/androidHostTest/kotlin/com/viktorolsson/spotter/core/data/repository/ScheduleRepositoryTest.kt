package com.viktorolsson.spotter.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.viktorolsson.spotter.core.data.db.SpotterDatabase
import com.viktorolsson.spotter.core.data.db.entity.PlanDayEntity
import com.viktorolsson.spotter.core.data.db.entity.PlanEntity
import com.viktorolsson.spotter.core.data.sql
import com.viktorolsson.spotter.core.data.testDatabase
import com.viktorolsson.spotter.core.model.Goal
import com.viktorolsson.spotter.core.model.SplitType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Instant

@RunWith(AndroidJUnit4::class)
class ScheduleRepositoryTest {
    private lateinit var db: SpotterDatabase
    private lateinit var workouts: WorkoutRepository
    private lateinit var repo: ScheduleRepository
    private val clock = MutableClock(Instant.parse("2026-10-24T08:00:00Z")) // epoch day 20750

    @Before
    fun setUp() = runTest {
        db = testDatabase()
        workouts = WorkoutRepository(db, db.workoutDao(), db.planDao(), db.exerciseDao(), testPreferences(db), clock)
        repo = ScheduleRepository(db.scheduleDao(), workouts, clock)
    }

    @After
    fun tearDown() = db.close()

    private fun exec(sql: String) = db.sql.execSQL(sql)

    @Test
    fun upcomingCoversTheNextWeekWithDayNames() = runTest {
        val plan = db.planDao().insertPlan(PlanEntity(name = "P", splitType = SplitType.CUSTOM, goal = Goal.STRENGTH, createdAt = Instant.fromEpochMilliseconds(0), isActive = true))
        val day = db.planDao().insertDay(PlanDayEntity(planId = plan, position = 0, name = "Upper"))
        exec("INSERT INTO scheduled_session (date, planDayId, notes, sessionId) VALUES (20749, $day, NULL, NULL)") // yesterday
        exec("INSERT INTO scheduled_session (date, planDayId, notes, sessionId) VALUES (20750, $day, 'Heavy', NULL)") // today
        exec("INSERT INTO scheduled_session (date, planDayId, notes, sessionId) VALUES (20756, NULL, NULL, NULL)") // in 6 days
        exec("INSERT INTO scheduled_session (date, planDayId, notes, sessionId) VALUES (20757, NULL, NULL, NULL)") // in 7 days

        val upcoming = repo.observeUpcoming().first()
        assertEquals(listOf(20750L, 20756L), upcoming.map { it.date.toEpochDays() })
        assertEquals("Upper", upcoming[0].planDayName)
        assertEquals("Heavy", upcoming[0].notes)
        assertEquals(null, upcoming[1].planDayName) // free workout
    }

    @Test
    fun startingLinksTheWorkoutAndFinishingMarksItDone() = runTest {
        exec("INSERT INTO scheduled_session (date, planDayId, notes, sessionId) VALUES (20750, NULL, NULL, NULL)")
        val scheduled = repo.observeUpcoming().first().single()
        val sessionId = repo.start(scheduled)
        assertEquals(sessionId, repo.observeUpcoming().first().single().sessionId)
        assertEquals(false, repo.observeUpcoming().first().single().done)
        exec("UPDATE workout_session SET endedAt = 1 WHERE id = $sessionId")
        assertTrue(repo.observeUpcoming().first().single().done)
    }

    @Test
    fun aWorkoutAlreadyRunningIsReturnedAndNotLinked() = runTest {
        val running = workouts.startEmptyWorkout()
        exec("INSERT INTO scheduled_session (date, planDayId, notes, sessionId) VALUES (20750, NULL, NULL, NULL)")
        assertEquals(running, repo.start(repo.observeUpcoming().first().single()))
        assertEquals(null, repo.observeUpcoming().first().single().sessionId)
    }
}
