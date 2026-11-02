package com.viktorolsson.spotter.feature.session

import androidx.lifecycle.SavedStateHandle
import androidx.room.useWriterConnection
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.viktorolsson.spotter.core.data.DataGraph
import com.viktorolsson.spotter.core.data.Failure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The rating on the summary after a workout, against a real (in-memory) database. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class SummaryViewModelTest {
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

    private fun viewModel(sessionId: Long) = SummaryViewModel(
        savedStateHandle = SavedStateHandle(mapOf("sessionId" to sessionId)),
        workoutRepository = graph.workouts,
        historyRepository = graph.history,
        preferencesRepository = graph.preferences,
        gamificationRepository = graph.gamification,
        errors = graph.errors,
    )

    @Test
    fun ratingAWorkoutSavesFeelAndEffort() = runTest(main) {
        val id = graph.workouts.startEmptyWorkout(readiness = 2)
        graph.workouts.finishWorkout(id)
        val vm = viewModel(id)

        vm.rateFeel(4).join()
        vm.rateEffort(7).join()

        // Read back from the database, not by collecting uiState: a collector left running
        // re-queries after each write and could still be mid-query when tearDown closes the
        // database, failing whichever test ran next ("connection is closed").
        val rated = graph.workouts.getSession(id)!!
        assertEquals(4, rated.feel)
        assertEquals(7, rated.effort)
        assertEquals(2, rated.readiness)
    }

    @Test
    fun aRatingThatFailsToSaveIsShown() = runTest(main) {
        val id = graph.workouts.startEmptyWorkout()
        graph.workouts.finishWorkout(id)
        val vm = viewModel(id)
        val shown = mutableListOf<Failure>()
        backgroundScope.launch { graph.errors.messages.collect { shown += it } }
        runCurrent()

        graph.database.useWriterConnection { connection ->
            connection.usePrepared("CREATE TRIGGER reject_session_updates BEFORE UPDATE ON workout_session BEGIN SELECT RAISE(ABORT, 'disk full'); END") { it.step() }
        }
        vm.rateFeel(3).join()
        runCurrent()

        assertEquals(listOf(Failure.SAVE), shown)
    }
}
