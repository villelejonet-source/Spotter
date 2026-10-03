package com.viktorolsson.spotter.core.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.viktorolsson.spotter.core.data.db.SpotterDatabase
import com.viktorolsson.spotter.core.data.db.toModel
import com.viktorolsson.spotter.core.data.repository.BodyWeightRepository
import com.viktorolsson.spotter.core.data.repository.ExerciseRepository
import com.viktorolsson.spotter.core.data.repository.MutableClock
import com.viktorolsson.spotter.core.data.repository.PlanRepository
import com.viktorolsson.spotter.core.data.repository.UserProfileRepository
import com.viktorolsson.spotter.core.data.repository.WorkoutRepository
import com.viktorolsson.spotter.core.data.repository.testPreferences
import com.viktorolsson.spotter.core.data.seed.SeedExercise
import com.viktorolsson.spotter.core.data.seed.parseExerciseSeed
import com.viktorolsson.spotter.core.engine.PlanGenerator
import com.viktorolsson.spotter.core.engine.PlanInput
import com.viktorolsson.spotter.core.model.Equipment
import com.viktorolsson.spotter.core.model.ExperienceLevel
import com.viktorolsson.spotter.core.model.Goal
import com.viktorolsson.spotter.core.model.MovementPattern
import com.viktorolsson.spotter.core.model.Muscle
import com.viktorolsson.spotter.core.model.Sex
import com.viktorolsson.spotter.core.model.UserProfile
import com.viktorolsson.spotter.core.model.WeightUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.time.LocalDate

/** An in-memory Supabase: last-write-wins on strictly newer rows, increasing server time. */
class FakeRemote : SyncRemote {
    private val rows = linkedMapOf<Pair<String, String>, RemoteRow>()
    private var serverTime = Instant.parse("2026-10-03T10:00:00Z")
    var pushes = 0

    override suspend fun hasRows() = rows.isNotEmpty()

    override suspend fun pull(since: String?, offset: Int, limit: Int): List<RemoteRow> =
        rows.values
            .filter { since == null || Instant.parse(it.serverUpdatedAt) > Instant.parse(since) }
            .sortedBy { Instant.parse(it.serverUpdatedAt) }
            .drop(offset).take(limit)

    override suspend fun push(rows: List<SyncRow>) {
        pushes++
        rows.forEach { row ->
            val key = row.tableName to row.syncId
            val existing = this.rows[key]
            if (existing == null || row.updatedAt > existing.updatedAt) {
                serverTime = serverTime.plusMillis(1)
                this.rows[key] = RemoteRow(row.tableName, row.syncId, row.payload, row.updatedAt, row.deletedAt, serverTime.toString())
            }
        }
    }
}

@RunWith(AndroidJUnit4::class)
class SyncEngineTest {
    private val remote = FakeRemote()
    private val devices = mutableListOf<Device>()

    /** One phone: its own database, repositories and sync engine. */
    inner class Device {
        val db: SpotterDatabase = SpotterDatabase.configure(
            Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), SpotterDatabase::class.java),
        ).allowMainThreadQueries().build()
        val clock = MutableClock(Instant.parse("2026-10-03T10:00:00Z"))
        val prefs = testPreferences()
        val workouts = WorkoutRepository(db, db.workoutDao(), db.planDao(), db.exerciseDao(), prefs, clock)
        val plans = PlanRepository(db, db.planDao())
        val profiles = UserProfileRepository(db.userProfileDao())
        val exercises = ExerciseRepository(db.exerciseDao())
        val bodyWeight = BodyWeightRepository(db, db.userProfileDao())
        val engine = SyncEngine(LocalSyncStore(db), remote, SyncCursorStore(prefsStore()), java.time.Clock.systemUTC())

        suspend fun seed() = db.exerciseDao().upsertAll(
            parseExerciseSeed(File("src/main/assets/exercises.json").readText()).exercises.map(SeedExercise::toEntity),
        )

        suspend fun sync(choice: FirstSyncChoice? = null) = engine.sync(USER, choice)

        suspend fun sessions() = db.workoutDao().observeFinishedSessions().first().map { it.toModel() }
    }

    private fun prefsStore() = androidx.datastore.preferences.core.PreferenceDataStoreFactory.create {
        File.createTempFile("sync", ".preferences_pb").apply { delete(); deleteOnExit() }
    }

    private suspend fun device() = Device().also { it.seed(); devices += it }

    @After
    fun tearDown() = devices.forEach { it.db.close() }

    private val profile = UserProfile(
        Sex.MALE, 180.0, LocalDate.of(1990, 1, 1), 82.0, WeightUnit.KG, ExperienceLevel.INTERMEDIATE, Goal.HYPERTROPHY,
        4, 60, Equipment.entries.toSet(), emptySet(), emptySet(),
    )

    /** A finished workout: bench 80 × 8, 82.5 × 6 and a custom exercise. */
    private suspend fun Device.logWorkout(): Long {
        val customId = exercises.createCustom("Landmine Row Variant", Muscle.UPPER_BACK, MovementPattern.HORIZONTAL_PULL, listOf(Equipment.LANDMINE))
        val id = workouts.startEmptyWorkout()
        workouts.addExercises(id, listOf("barbell-bench-press", customId), defaultSetCount = 2)
        val sets = workouts.getSession(id)!!.exercises.flatMap { it.sets }
        listOf(80.0 to 8, 82.5 to 6, 40.0 to 10, 40.0 to 10).zip(sets).forEach { (v, set) ->
            workouts.updateSetValues(set.id, v.first, v.second, null)
            workouts.toggleSetCompleted(set.id)
        }
        workouts.setSessionNotes(id, "Felt strong")
        clock.advanceSeconds(3600)
        workouts.finishWorkout(id)
        return id
    }

    private suspend fun Device.planFor(p: UserProfile) = plans.saveAsActive(
        PlanGenerator().generate(PlanInput(p, exercises.observeAll().first(), LocalDate.of(2026, 10, 3), Instant.now())).plan,
    )

    @Test
    fun backupThenRestoreOnAFreshPhone() = runTest {
        val a = device()
        a.profiles.save(profile)
        a.planFor(profile)
        a.logWorkout()
        a.bodyWeight.log(LocalDate.of(2026, 10, 1), 82.4)
        assertEquals(SyncOutcome.Done::class, a.sync()::class)

        val b = device()
        val outcome = b.sync() as SyncOutcome.Done
        assertTrue(outcome.pulled > 0)

        val session = b.sessions().single()
        assertEquals("Felt strong", session.notes)
        assertEquals(listOf("barbell-bench-press", a.sessions().single().exercises[1].exercise.id), session.exercises.map { it.exercise.id })
        assertEquals(listOf(80.0 to 8, 82.5 to 6), session.exercises[0].sets.map { it.weightKg to it.reps })
        assertTrue(session.exercises[1].exercise.isCustom)
        // Logging body weight also updated the profile's weight on A; B gets that version.
        assertEquals(profile.copy(bodyWeightKg = 82.4), b.profiles.get())
        assertEquals(82.4, b.bodyWeight.observeTrend().first().single().weightKg, 0.0)
        val planA = a.plans.observeActivePlan().first()!!
        val planB = b.plans.observeActivePlan().first()!!
        assertEquals(planA.days.map { d -> d.name to d.exercises.map { it.exercise.id } }, planB.days.map { d -> d.name to d.exercises.map { it.exercise.id } })
        // History works on the restored data (PR bests, previous sets).
        assertEquals(listOf(80.0, 82.5), b.workouts.getPreviousSets("barbell-bench-press").map { it.weightKg })
    }

    private suspend fun Device.firstSet() = sessions().single().exercises[0].sets[0]

    @Test
    fun editsAndDeletionsTravelBothWays() = runTest {
        val a = device()
        a.logWorkout()
        a.sync()
        val b = device()
        b.sync()

        b.workouts.updateSetValues(b.firstSet().id, 85.0, 5, 1)
        b.workouts.deleteSet(b.sessions().single().exercises[0].sets[1].id)
        b.sync()
        a.sync()
        val aSets = a.sessions().single().exercises[0].sets
        assertEquals(listOf(85.0 to 5), aSets.map { it.weightKg to it.reps })
        assertEquals(1, aSets.single().rir)

        // Deleting the whole workout on A removes it (and its sets) on B.
        a.workouts.discardWorkout(a.sessions().single().id)
        a.sync()
        b.sync()
        assertTrue(b.sessions().isEmpty())
        assertEquals(0, b.db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM set_entry").use { it.moveToFirst(); it.getInt(0) })
    }

    @Test
    fun theNewestEditWins() = runTest {
        val a = device()
        a.logWorkout()
        a.sync()
        val b = device()
        b.sync()

        b.workouts.updateSetValues(b.firstSet().id, 90.0, 3, null) // older edit
        Thread.sleep(20)
        a.workouts.updateSetValues(a.firstSet().id, 100.0, 1, null) // newer edit
        b.sync() // B's older edit reaches the server first
        a.sync() // A's newer edit replaces it
        b.sync()
        assertEquals(100.0 to 1, a.firstSet().let { it.weightKg to it.reps })
        assertEquals(100.0 to 1, b.firstSet().let { it.weightKg to it.reps })
    }

    @Test
    fun aPhoneWithItsOwnDataAsksBeforeRestoring() = runTest {
        val a = device()
        a.profiles.save(profile)
        a.planFor(profile)
        a.logWorkout()
        a.sync()

        val b = device()
        b.planFor(profile.copy(daysPerWeek = 3)) // b went through onboarding first
        assertEquals(SyncOutcome.NeedsChoice, b.sync())

        b.sync(FirstSyncChoice.USE_BACKUP)
        assertEquals(4, b.plans.observeActivePlan().first()!!.days.size) // A's plan, B's own is gone
        assertEquals(1, b.db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM plan").use { it.moveToFirst(); it.getInt(0) })
        assertEquals(1, b.sessions().size)
    }

    @Test
    fun mergingKeepsBothPhonesDataWithOneActivePlan() = runTest {
        val a = device()
        a.planFor(profile)
        a.logWorkout()
        a.sync()

        val b = device()
        Thread.sleep(20)
        b.planFor(profile.copy(daysPerWeek = 3))
        b.logWorkout()
        b.sync(FirstSyncChoice.MERGE)
        a.sync()

        listOf(a, b).forEach { d ->
            assertEquals(2, d.sessions().size)
            val active = d.db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM plan WHERE isActive = 1").use { it.moveToFirst(); it.getInt(0) }
            assertEquals(1, active)
            assertEquals(3, d.plans.observeActivePlan().first()!!.days.size) // the newer plan (B's) stays active
        }
    }

    @Test
    fun syncingAgainChangesNothing() = runTest {
        val a = device()
        a.logWorkout()
        a.sync()
        val b = device()
        b.sync()
        val before = b.sessions()
        val again = b.sync() as SyncOutcome.Done
        assertEquals(0, again.pushed)
        assertEquals(before, b.sessions())
    }

    private companion object {
        const val USER = "user-1"
    }
}
