package com.viktorolsson.spotter.feature.onboarding

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.viktorolsson.spotter.core.data.DataGraph
import com.viktorolsson.spotter.core.data.db.SpotterDatabase
import com.viktorolsson.spotter.core.data.sync.AppBuild
import com.viktorolsson.spotter.core.data.sync.SupabaseConfig
import com.viktorolsson.spotter.core.data.sync.SyncScheduler
import com.viktorolsson.spotter.core.model.Equipment
import com.viktorolsson.spotter.core.model.ExperienceLevel
import com.viktorolsson.spotter.core.model.Goal
import com.viktorolsson.spotter.core.model.Program
import com.viktorolsson.spotter.core.model.Sex
import com.viktorolsson.spotter.core.model.UserProfile
import com.viktorolsson.spotter.core.model.WeightUnit
import com.viktorolsson.spotter.core.model.program
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertTrue

/** The onboarding view model against a real (in-memory) database and preferences. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class OnboardingViewModelTest {
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

    private fun viewModel() = OnboardingViewModel(
        savedStateHandle = SavedStateHandle(mapOf("rebuild" to false)),
        exerciseRepository = graph.exercises,
        planRepository = graph.plans,
        profileRepository = graph.userProfiles,
        preferencesRepository = graph.preferences,
        syncRepository = graph.sync,
        clock = Clock.System,
        errors = graph.errors,
    )

    /** Counts how often the screen is told to close: by [OnboardingViewModel.restoredFromBackup] and by [onDone]. */
    private fun TestScope.closes(vm: OnboardingViewModel): IntArray {
        val count = intArrayOf(0)
        backgroundScope.launch { vm.restoredFromBackup.collect { count[0]++ } }
        runCurrent()
        return count
    }

    /**
     * Lets the preferences write reach the view model's flows. DataStore writes and emits on
     * its own thread, so this test's main dispatcher is pumped in real time until the write
     * is in, then a little longer for the flows to see it. (Waiting on the flow from the test
     * dispatcher itself could hang under a busy build.)
     */
    private suspend fun TestScope.settle() {
        withContext(Dispatchers.Default) {
            withTimeout(10.seconds) {
                while (!graph.preferences.preferences.first().onboardingCompleted) delay(10)
            }
        }
        repeat(30) {
            runCurrent()
            withContext(Dispatchers.Default) { delay(10) }
        }
        runCurrent()
    }

    /**
     * Skip closed the screen, and the completion it wrote looked like a restored backup, so
     * the screen was closed a second time: that also closed Today, leaving a blank screen.
     */
    @Test
    fun skippingClosesTheScreenOnce() = runTest(main) {
        val vm = viewModel()
        val closes = closes(vm)
        vm.skip { closes[0]++ }
        runCurrent()
        settle()
        assertEquals(1, closes[0])
    }

    /** Setup finished some other way (a backup restored while it's open) still closes it. */
    @Test
    fun aRestoredBackupClosesTheScreen() = runTest(main) {
        val vm = viewModel()
        val closes = closes(vm)
        graph.preferences.setOnboardingCompleted(true)
        settle()
        assertEquals(1, closes[0])
    }

    /** Pumps this test's main dispatcher in real time until [done]: the plan is generated off it. */
    private suspend fun TestScope.until(done: () -> Boolean) {
        repeat(500) {
            runCurrent()
            if (done()) return
            withContext(Dispatchers.Default) { delay(10) }
        }
    }

    @Test
    fun aProgramCardBuildsThatProgram() = runTest(main) {
        graph.seeder.seedIfNeeded()
        graph.userProfiles.save(
            UserProfile(
                sex = Sex.FEMALE, heightCm = 168.0, birthDate = LocalDate(1995, 5, 5), bodyWeightKg = 62.0, units = WeightUnit.KG,
                experience = ExperienceLevel.INTERMEDIATE, goal = Goal.STRENGTH, daysPerWeek = 4, sessionLengthMinutes = 60,
                equipment = Equipment.entries.toSet(), focusAreas = emptySet(), limitations = emptySet(),
            ),
        )
        val vm = viewModel()
        until { vm.uiState.value.answers.days != null }
        fun built() = vm.uiState.value.generated?.plan?.program()

        vm.chooseProgram(Program.FIVE_THREE_ONE)
        until { built() == Program.FIVE_THREE_ONE }
        assertEquals(Program.FIVE_THREE_ONE, built())
        assertTrue(Program.FIVE_THREE_ONE in vm.uiState.value.suited)

        vm.chooseProgram(Program.FULL_BODY)
        until { built() == Program.FULL_BODY }
        assertEquals(Program.FULL_BODY, built())

        // 5/3/1 picked, then the barbell unticked: the automatic plan instead.
        vm.chooseProgram(Program.FIVE_THREE_ONE)
        until { built() == Program.FIVE_THREE_ONE }
        vm.update { it.copy(equipment = it.equipment - Equipment.BARBELL) }
        vm.chooseProgram(Program.FIVE_THREE_ONE)
        until { built() == Program.UPPER_LOWER }
        assertEquals(Program.UPPER_LOWER, built())
        assertTrue(Program.FIVE_THREE_ONE !in vm.uiState.value.suited)
    }
}
