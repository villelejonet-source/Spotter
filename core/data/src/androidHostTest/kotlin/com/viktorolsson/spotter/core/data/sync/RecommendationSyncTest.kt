package com.viktorolsson.spotter.core.data.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.viktorolsson.spotter.core.data.db.SpotterDatabase
import com.viktorolsson.spotter.core.data.db.toModel
import com.viktorolsson.spotter.core.data.repository.HistoryRepository
import com.viktorolsson.spotter.core.data.repository.MutableClock
import com.viktorolsson.spotter.core.data.repository.PlanRepository
import com.viktorolsson.spotter.core.data.repository.PlanReviewChoice
import com.viktorolsson.spotter.core.data.repository.RecommendationRepository
import com.viktorolsson.spotter.core.data.repository.UserProfileRepository
import com.viktorolsson.spotter.core.data.repository.WorkoutRepository
import com.viktorolsson.spotter.core.data.repository.testPreferences
import com.viktorolsson.spotter.core.data.seed.SeedExercise
import com.viktorolsson.spotter.core.data.seed.parseExerciseSeed
import com.viktorolsson.spotter.core.data.testDatabase
import com.viktorolsson.spotter.core.model.Goal
import com.viktorolsson.spotter.core.model.Plan
import com.viktorolsson.spotter.core.model.PlanDay
import com.viktorolsson.spotter.core.model.PlanExercise
import com.viktorolsson.spotter.core.model.ProgressionRule
import com.viktorolsson.spotter.core.model.RecommendationAction
import com.viktorolsson.spotter.core.model.RecommendationType
import com.viktorolsson.spotter.core.model.SplitType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Recommendation cards sync between phones, whose local row ids differ: a card made on one
 * phone must act on the same plan rows on the other, and the phones must agree on one card.
 */
@RunWith(AndroidJUnit4::class)
class RecommendationSyncTest {
    private val remote = FakeRemote()
    private val clock = MutableClock(Instant.parse("2026-08-01T10:00:00Z"))
    private val phones = mutableListOf<Phone>()

    inner class Phone {
        val db: SpotterDatabase = testDatabase()
        val prefs = testPreferences(db)
        val plans = PlanRepository(db, db.planDao())
        val workouts = WorkoutRepository(db, db.workoutDao(), db.planDao(), db.exerciseDao(), prefs, clock)
        val recommendations = RecommendationRepository(
            db, db.recommendationDao(), db.workoutDao(), db.planDao(), db.exerciseDao(), UserProfileRepository(db.userProfileDao()),
            HistoryRepository(db.workoutDao(), clock), prefs, clock,
        )
        private val engine = SyncEngine(LocalSyncStore(db), remote, SyncCursorStore(prefsStore()), Clock.System) {
            DeviceCheckIn("install", versionCode = 9, schemaVersion = 8)
        }

        suspend fun sync(choice: FirstSyncChoice? = null) = engine.sync(USER, choice, fromLink = false)

        suspend fun active() = recommendations.observeActive().first()

        suspend fun benchSlots() = plans.observeActivePlan().first()!!.days.map { day -> day.exercises.first { it.exercise.id == BENCH } }
    }

    private fun prefsStore() = androidx.datastore.preferences.core.PreferenceDataStoreFactory.create {
        File.createTempFile("sync", ".preferences_pb").apply { delete(); deleteOnExit() }
    }

    private suspend fun phone() = Phone().also { p ->
        p.db.exerciseDao().upsertAll(parseExerciseSeed(File("catalog/exercises.json").readText()).exercises.map(SeedExercise::toEntity))
        phones += p
    }

    /** A second phone whose own row ids are ahead of the first's: it had a plan before restoring. */
    private suspend fun restoredPhone() = phone().also { p ->
        p.plans.saveAsActive(plan(p.db))
        p.sync(FirstSyncChoice.USE_BACKUP)
    }

    @After
    fun tearDown() = phones.forEach { it.db.close() }

    private suspend fun plan(db: SpotterDatabase): Plan {
        suspend fun ex(id: String, position: Int) = PlanExercise(
            0, db.exerciseDao().getById(id)!!.toModel(), position, 3, 6, 12, 1, 120, ProgressionRule.DOUBLE_PROGRESSION, null, 60.0,
        )
        val day = listOf(ex(BENCH, 0), ex("barbell-row", 1), ex("back-squat", 2), ex("romanian-deadlift", 3), ex("overhead-press", 4), ex("barbell-hip-thrust", 5))
        return Plan(0, "Test", SplitType.FULL_BODY, Goal.HYPERTROPHY, clock.now(), listOf(PlanDay(0, 0, "Full A", day), PlanDay(0, 1, "Full B", day)))
    }

    /** Four weeks of bench stuck at 80 × 6 with little chest volume: a card adds a set to both bench slots. */
    private suspend fun Phone.stallBench() = repeat(4) {
        val sessionId = workouts.startPlannedWorkout(plans.observeNextDay().first()!!.id)
        workouts.getSession(sessionId)!!.exercises.filter { it.exercise.id == BENCH }.flatMap { it.sets }.forEach {
            workouts.updateSetValues(it.id, 80.0, 6, null)
            workouts.toggleSetCompleted(it.id)
        }
        clock.advanceSeconds(3600)
        workouts.finishWorkout(sessionId)
        clock.advanceSeconds(7 * 86_400L - 3600)
    }

    @Test
    fun aCardMadeOnOnePhoneChangesTheSameRowsOnTheOther() = runTest {
        val a = phone()
        a.plans.saveAsActive(plan(a.db))
        a.stallBench()
        a.recommendations.refresh()
        a.sync()

        val b = restoredPhone()
        val card = b.active().single()
        assertEquals(RecommendationType.ADD_VOLUME, card.type)
        b.recommendations.apply(card.id)

        assertEquals(listOf(4, 4), b.benchSlots().map { it.sets })
    }

    @Test
    fun twoPhonesKeepOneCardInsteadOfReplacingEachOthers() = runTest {
        val a = phone()
        a.plans.saveAsActive(plan(a.db))
        a.stallBench()
        a.sync()
        val b = restoredPhone()

        // Both phones make the same card before either has seen the other's.
        a.recommendations.refresh()
        b.recommendations.refresh()
        a.sync(); b.sync(); a.sync()
        a.recommendations.refresh()
        b.recommendations.refresh()
        a.sync(); b.sync(); a.sync()

        val onA = a.active().map { it.payload.action }
        assertEquals(1, onA.size)
        assertEquals(1, b.active().size)
        // Refreshing again on either phone leaves the card alone: nothing new to sync.
        val before = a.db.recommendationDao().getAll().map { it.syncId to it.updatedAt }
        b.recommendations.refresh()
        b.sync(); a.sync()
        assertEquals(before, a.db.recommendationDao().getAll().map { it.syncId to it.updatedAt })
    }

    @Test
    fun aVariationSwappedOnOnePhoneIsSwitchedBackOnTheRightRowOnTheOther() = runTest {
        val a = phone()
        a.plans.saveAsActive(plan(a.db))
        val slot = a.benchSlots().first()
        val swap = RecommendationAction.SwapVariation(slot.id, BENCH, "Barbell Bench Press", "dumbbell-bench-press", "Dumbbell Bench Press", weeks = 5)
        a.recommendations.applyPlanReview(listOf(PlanReviewChoice(BENCH, "Barbell Bench Press", swap)))
        a.sync()

        val b = restoredPhone()
        clock.advanceSeconds(5 * 7 * 86_400L)
        b.recommendations.refresh()
        val back = b.active().single { (it.payload.action as? RecommendationAction.SwapVariation)?.returning == true }
        b.recommendations.apply(back.id)

        assertEquals(listOf(BENCH, BENCH), b.plans.observeActivePlan().first()!!.days.map { it.exercises.first().exercise.id })
    }

    private companion object {
        const val USER = "user-1"
        const val BENCH = "barbell-bench-press"
    }
}
