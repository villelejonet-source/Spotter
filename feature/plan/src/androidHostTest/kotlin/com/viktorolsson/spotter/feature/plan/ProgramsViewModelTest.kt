package com.viktorolsson.spotter.feature.plan

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.viktorolsson.spotter.core.data.db.entity.PersonalRecordEntity
import com.viktorolsson.spotter.core.model.Equipment
import com.viktorolsson.spotter.core.model.ExperienceLevel
import com.viktorolsson.spotter.core.model.PersonalRecordType
import com.viktorolsson.spotter.core.model.Program
import com.viktorolsson.spotter.core.model.Sex
import com.viktorolsson.spotter.core.model.UserProfile
import com.viktorolsson.spotter.core.model.WeightUnit
import com.viktorolsson.spotter.core.model.program
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import kotlin.time.Duration.Companion.days
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.viktorolsson.spotter.core.data.DataGraph
import com.viktorolsson.spotter.core.data.db.SpotterDatabase
import com.viktorolsson.spotter.core.data.sync.AppBuild
import com.viktorolsson.spotter.core.data.sync.SupabaseConfig
import com.viktorolsson.spotter.core.data.sync.SyncScheduler
import com.viktorolsson.spotter.core.model.Goal
import com.viktorolsson.spotter.core.model.Plan
import com.viktorolsson.spotter.core.model.PlanDay
import com.viktorolsson.spotter.core.model.PlanExercise
import com.viktorolsson.spotter.core.model.ProgressionRule
import com.viktorolsson.spotter.core.model.SplitType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.time.Clock

/** The program cards and the switch to a program, against a real (in-memory) database. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ProgramsViewModelTest {
    private val main = StandardTestDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var graph: DataGraph

    @Before
    fun setUp() {
        Dispatchers.setMain(main)
        val prefsFile = File.createTempFile("prefs", ".preferences_pb").apply { delete(); deleteOnExit() }
        graph = DataGraph(
            database = SpotterDatabase.configure(
                Room.inMemoryDatabaseBuilder<SpotterDatabase>(ApplicationProvider.getApplicationContext<Context>()),
                AndroidSQLiteDriver(),
            ).build(),
            dataStore = PreferenceDataStoreFactory.create(scope = scope) { prefsFile },
            clock = Clock.System,
            applicationScope = scope,
            supabaseConfig = SupabaseConfig("", ""),
            sessionStore = { null },
            deviceStore = { error("No device store in tests") },
            appBuild = AppBuild(versionCode = 1),
            catalog = { File("../../core/data/catalog/exercises.json").readText() },
            syncScheduler = object : SyncScheduler {
                override fun schedulePeriodic() = Unit
                override fun syncSoon() = Unit
            },
        )
    }

    @After
    fun tearDown() {
        graph.database.close()
        scope.cancel()
        Dispatchers.resetMain()
    }

    /** The view model's work goes through Room on its own thread: pump this test's main dispatcher until [done]. */
    private suspend fun TestScope.until(done: () -> Boolean) {
        repeat(500) {
            runCurrent()
            if (done()) return
            withContext(Dispatchers.Default) { delay(10) }
        }
    }

    private val profile = UserProfile(
        sex = Sex.MALE, heightCm = 180.0, birthDate = LocalDate(1990, 1, 1), bodyWeightKg = 80.0, units = WeightUnit.KG,
        experience = ExperienceLevel.INTERMEDIATE, goal = Goal.STRENGTH, daysPerWeek = 4, sessionLengthMinutes = 60,
        equipment = Equipment.entries.toSet(), focusAreas = emptySet(), limitations = emptySet(),
    )

    private suspend fun upperLowerPlan(): Plan {
        val bench = graph.exercises.observeAll().first().first { it.id == "barbell-bench-press" }
        val ex = PlanExercise(0, bench, 0, 3, 8, 12, 2, 120, ProgressionRule.DOUBLE_PROGRESSION, null, 60.0)
        return Plan(0, "Test", SplitType.UPPER_LOWER, Goal.STRENGTH, Clock.System.now(), listOf(PlanDay(0, 0, "Upper", listOf(ex))))
    }

    private fun preview(program: Program) = ProgramPreviewViewModel(
        SavedStateHandle(mapOf("program" to program.name)), graph.userProfiles, graph.plans, graph.exercises, graph.history,
        graph.preferences, Clock.System, graph.errors,
    )

    @Test
    fun theCardsMarkTheCurrentProgramAndTheOnesThatSuit() = runTest(main) {
        graph.seeder.seedIfNeeded()
        graph.userProfiles.save(profile)
        graph.plans.saveAsActive(upperLowerPlan())

        val vm = ProgramsViewModel(graph.userProfiles, graph.plans)
        backgroundScope.launch { vm.uiState.collect {} }
        until { vm.uiState.value.current != null }
        assertEquals(Program.UPPER_LOWER, vm.uiState.value.current)
        assertEquals(setOf(Program.UPPER_LOWER, Program.FIVE_THREE_ONE), vm.uiState.value.suited)
    }

    @Test
    fun switchingTo531StartsFromARecentBench() = runTest(main) {
        graph.seeder.seedIfNeeded()
        graph.userProfiles.save(profile)
        graph.plans.saveAsActive(upperLowerPlan())
        val recent = PersonalRecordEntity(
            exerciseId = "barbell-bench-press", type = PersonalRecordType.ESTIMATED_1RM, value = 116.7,
            weightKg = 100.0, reps = 5, achievedAt = Clock.System.now() - 10.days, sessionId = null,
        )
        // An old, heavier record is out of date: the recent one counts.
        val old = recent.copy(value = 150.0, weightKg = 150.0, reps = 1, achievedAt = Clock.System.now() - 200.days)
        withContext(Dispatchers.IO) { graph.database.workoutDao().insertRecords(listOf(recent, old)) }

        val vm = preview(Program.FIVE_THREE_ONE)
        until { vm.uiState.value.generated != null }
        val plan = vm.uiState.value.generated!!.plan
        assertFalse(vm.uiState.value.estimated)
        // Bench 100 × 5: an estimated 1RM of 116.7 kg, a training max of 105.
        assertEquals(105.0, plan.days.single { it.name == "Bench day" }.exercises.first().trainingMaxKg!!, 0.001)

        var done = false
        vm.switch { done = true }
        until { done }
        assertEquals(Program.FIVE_THREE_ONE, graph.plans.observeActivePlan().first()!!.program())
    }

    @Test
    fun withoutRecentLiftsTheWeightsAreEstimated() = runTest(main) {
        graph.seeder.seedIfNeeded()
        graph.userProfiles.save(profile)

        val vm = preview(Program.FULL_BODY)
        until { vm.uiState.value.generated != null }
        assertTrue(vm.uiState.value.estimated)
        assertEquals(Program.FULL_BODY, vm.uiState.value.generated!!.plan.program())
        assertEquals(4, vm.uiState.value.generated!!.plan.days.size)
    }
}
