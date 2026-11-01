package com.viktorolsson.spotter.feature.plan

import android.content.Context
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

/** The plan screen's view model against a real (in-memory) database. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class PlanViewModelTest {
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

    private suspend fun twoDayPlan(): Plan {
        val bench = graph.exercises.observeAll().first().first { it.id == "barbell-bench-press" }
        val ex = PlanExercise(0, bench, 0, 3, 8, 12, 2, 120, ProgressionRule.DOUBLE_PROGRESSION, null, 60.0)
        return Plan(0, "Test", SplitType.FULL_BODY, Goal.HYPERTROPHY, Clock.System.now(), listOf(PlanDay(0, 0, "Push", listOf(ex)), PlanDay(0, 1, "Pull", listOf(ex))))
    }

    /** Start on another day while a workout runs used to open the running one without a word. */
    @Test
    fun startingADayWhileAWorkoutRunsAsksFirst() = runTest(main) {
        graph.seeder.seedIfNeeded()
        graph.plans.saveAsActive(twoDayPlan())
        val pull = graph.plans.observeActivePlan().first()!!.days.single { it.name == "Pull" }
        val running = graph.workouts.startEmptyWorkout()

        val vm = PlanViewModel(graph.plans, graph.workouts, graph.preferences, graph.errors)
        var opened: Long? = null
        vm.startDay(pull.id) { opened = it }
        until { opened != null || vm.inProgress.value != null }
        assertNull("opened workout $opened without asking", opened)
        assertEquals(running, vm.inProgress.value)

        // "Go to workout" opens the running one; with none running, Start starts the day.
        vm.dismissInProgress()
        graph.workouts.discardWorkout(running)
        vm.startDay(pull.id) { opened = it }
        until { opened != null }
        assertEquals("Pull", graph.workouts.getSession(opened!!)!!.planDayName)
    }
}
