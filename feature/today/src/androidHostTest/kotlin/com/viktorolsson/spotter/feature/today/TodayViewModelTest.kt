package com.viktorolsson.spotter.feature.today

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.viktorolsson.spotter.core.data.DataGraph
import com.viktorolsson.spotter.core.data.db.SpotterDatabase
import com.russhwolf.settings.SharedPreferencesSettings
import com.viktorolsson.spotter.core.data.health.HealthStore
import com.viktorolsson.spotter.core.data.sync.AppBuild
import com.viktorolsson.spotter.core.model.HealthAvailability
import com.viktorolsson.spotter.core.model.HealthWorkout
import com.viktorolsson.spotter.core.model.ReadinessLevel
import com.viktorolsson.spotter.core.model.RecoveryNight
import com.viktorolsson.spotter.core.model.SessionAdvice
import com.viktorolsson.spotter.core.data.sync.SupabaseConfig
import com.viktorolsson.spotter.core.data.sync.SyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import com.viktorolsson.spotter.core.model.weekStart
import kotlinx.coroutines.withContext
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** The Today screen's view model against a real (in-memory) database, with a clock it controls. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class TodayViewModelTest {
    private val main = StandardTestDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val clock = object : Clock {
        var now = Instant.parse("2026-10-25T12:00:00Z") // a Sunday
        override fun now() = now
    }
    private lateinit var graph: DataGraph
    private val context: Context = ApplicationProvider.getApplicationContext()

    /** A health store that lets Spotter read [nights]. */
    private val health = object : HealthStore {
        var nights = emptyList<RecoveryNight>()
        override suspend fun availability() = HealthAvailability.AVAILABLE
        override suspend fun canWriteWorkouts() = false
        override suspend fun writeWorkout(workout: HealthWorkout) = Unit
        override suspend fun deleteWorkout(id: String) = Unit
        override suspend fun canReadRecovery() = true
        override suspend fun readRecovery(from: LocalDate, to: LocalDate) = nights
    }

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
            clock = clock,
            applicationScope = scope,
            supabaseConfig = SupabaseConfig("", ""),
            sessionStore = { null },
            deviceStore = { SharedPreferencesSettings(context.getSharedPreferences("device", Context.MODE_PRIVATE)) },
            healthStore = health,
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

    private fun viewModel() = TodayViewModel(
        graph.plans, graph.workouts, graph.schedule, graph.history, graph.userProfiles, graph.preferences,
        graph.recommendations, graph.gamification, graph.health, graph.errors, clock,
    )

    /** The database answers on its own thread: pump this test's main dispatcher in real time until [done]. */
    private suspend fun TestScope.awaitState(vm: TodayViewModel, done: (TodayUiState) -> Boolean): TodayUiState {
        repeat(500) {
            runCurrent()
            if (done(vm.uiState.value)) return vm.uiState.value
            withContext(Dispatchers.Default) { delay(10) }
        }
        error("State never arrived: ${vm.uiState.value}")
    }

    private suspend fun TestScope.await(what: String, done: () -> Boolean) {
        repeat(500) {
            runCurrent()
            if (done()) return
            withContext(Dispatchers.Default) { delay(10) }
        }
        error("Never happened: $what")
    }

    /**
     * Reading sleep and recovery is offered until it's turned on; from then on Today shows
     * what last night looked like, asked again each time the screen is shown.
     */
    @Test
    fun lastNightsRecoveryIsShownOnceReadingIsOn() = runTest(main) {
        health.nights = (0..10).map { RecoveryNight(today.minus(DatePeriod(days = it)), sleep = if (it == 0) 5.hours else 8.hours) }
        val vm = viewModel()
        backgroundScope.launch { vm.healthIntro.collect {} }
        vm.refreshHealth()
        await("the offer") { vm.healthIntro.value }
        assertNull(vm.readiness.value)

        graph.health.setReadingRecovery(true)
        vm.refreshHealth()
        await("readiness") { vm.readiness.value != null }
        assertEquals(ReadinessLevel.LOW, vm.readiness.value!!.level)
        assertEquals(SessionAdvice.LIGHTER, vm.readiness.value!!.advice)
        await("the offer going away") { !vm.healthIntro.value }

        // The watch hasn't synced last night yet: nothing to show, not yesterday's picture.
        health.nights = health.nights.drop(1)
        vm.refreshHealth()
        await("readiness going away") { vm.readiness.value == null }
    }

    @Test
    fun theOfferStaysAwayOnceDismissed() = runTest(main) {
        val vm = viewModel()
        backgroundScope.launch { vm.healthIntro.collect {} }
        vm.refreshHealth()
        await("the offer") { vm.healthIntro.value }

        vm.dismissHealthIntro()
        await("the offer going away") { !vm.healthIntro.value }
    }

    private val today: LocalDate get() = clock.todayIn(TimeZone.currentSystemDefault())

    /**
     * Today is the start screen, so its view model lives as long as the app does. It used to
     * keep the date it was made on: left open overnight into Monday, "this week" and the
     * streak stayed on last week.
     */
    @Test
    fun theDashboardMovesOnWhenTheDateChanges() = runTest(main) {
        graph.seeder.seedIfNeeded()
        val id = graph.workouts.startEmptyWorkout()
        graph.workouts.addExercises(id, listOf("barbell-bench-press"), defaultSetCount = 1)
        val set = graph.workouts.getSession(id)!!.exercises.single().sets.single()
        graph.workouts.updateSetValues(set.id, 80.0, 8, null)
        graph.workouts.toggleSetCompleted(set.id)
        clock.now += 1.hours
        graph.workouts.finishWorkout(id)

        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        val sunday = today
        assertEquals(sunday, awaitState(vm) { it.dashboard != null }.dashboard!!.today)

        clock.now += 24.hours // Monday, with the app still open
        advanceTimeBy(2.minutes)
        val monday = today
        val state = awaitState(vm) { it.dashboard?.today == monday }
        assertEquals(monday, state.today)
        assertEquals(weekStart(monday), state.dashboard!!.weeks.last().start)
    }
}
