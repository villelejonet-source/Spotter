package com.viktorolsson.spotter.feature.session

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
import com.viktorolsson.spotter.core.model.PreviousSet
import com.viktorolsson.spotter.core.model.WorkoutSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.time.Clock

/** The session screen's view model against a real (in-memory) database. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class SessionViewModelTest {
    private val main = StandardTestDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var graph: DataGraph

    @Before
    fun setUp() {
        Dispatchers.setMain(main)
        graph = testDataGraph(scope)
    }

    @After
    fun tearDown() {
        graph.database.close()
        scope.cancel()
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(sessionId: Long) = SessionViewModel(
        savedStateHandle = SavedStateHandle(mapOf("sessionId" to sessionId)),
        workoutRepository = graph.workouts,
        restTimerRepository = graph.restTimer,
        exerciseRepository = graph.exercises,
        profileRepository = graph.userProfiles,
        recommendationRepository = graph.recommendations,
        syncRepository = graph.sync,
        applicationScope = backgroundScope,
        preferencesRepository = graph.preferences,
    )

    private suspend fun sets(sessionId: Long): List<WorkoutSet> =
        graph.workouts.getSession(sessionId)!!.exercises.single().sets

    /**
     * A typed weight used to be written together with the reps and effort the screen showed,
     * not the ones in the database, so it undid a value the screen hadn't caught up with yet
     * (the next set filled in by a tick, a copied set, a sync).
     */
    @Test
    fun aTypedWeightKeepsRepsTheScreenHasNotShownYet() = runTest(main) {
        graph.seeder.seedIfNeeded()
        val id = graph.workouts.startEmptyWorkout()
        graph.workouts.addExercises(id, listOf("barbell-bench-press"))
        val (first, second) = sets(id)
        val vm = viewModel(id)
        vm.uiState.first { it.session != null }
        // Nobody watches the screen for a while, so its copy of the session goes stale.
        advanceTimeBy(6_000)

        graph.workouts.updateSetValues(first.id, 80.0, 8, null)
        graph.workouts.toggleSetCompleted(first.id) // fills the next set with 80 kg × 8
        vm.updateSetField(second.id, SetField.WEIGHT, "85")
        vm.flushEdit().join()

        val after = sets(id)[1]
        assertEquals(85.0, after.weightKg!!, 0.0)
        assertEquals(8, after.reps)
    }

    /** Copying last time's set onto a set with a typed value waiting: the copy is the later action and wins. */
    @Test
    fun copyPreviousWinsOverAPendingEdit() = runTest(main) {
        graph.seeder.seedIfNeeded()
        val id = graph.workouts.startEmptyWorkout()
        graph.workouts.addExercises(id, listOf("barbell-bench-press"))
        val set = sets(id).first()
        val vm = viewModel(id)
        vm.uiState.first { it.session != null }

        vm.updateSetField(set.id, SetField.WEIGHT, "60")
        vm.copyPrevious(set.id, PreviousSet(weightKg = 70.0, reps = 6)).join()
        vm.flushEdit().join() // anything still queued would land now

        val after = sets(id).first()
        assertEquals(70.0, after.weightKg!!, 0.0)
        assertEquals(6, after.reps)
        assertNull(after.rir)
    }
}

/** A [DataGraph] on an in-memory database and a temporary DataStore, with cloud sync off. */
internal fun testDataGraph(scope: CoroutineScope): DataGraph {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefsFile = File.createTempFile("prefs", ".preferences_pb").apply { delete(); deleteOnExit() }
    return DataGraph(
        database = SpotterDatabase.configure(
            Room.inMemoryDatabaseBuilder<SpotterDatabase>(context),
            AndroidSQLiteDriver(),
        ).build(),
        dataStore = PreferenceDataStoreFactory.create(scope = scope) { prefsFile },
        clock = Clock.System,
        scope = scope,
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
