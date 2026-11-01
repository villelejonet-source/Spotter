package com.viktorolsson.spotter.feature.session

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import com.viktorolsson.spotter.core.model.SetType
import com.viktorolsson.spotter.core.model.WorkoutSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DebouncedEditTest {
    private val written = mutableListOf<String>()
    private val failures = mutableListOf<Throwable>()

    /** Suspends like a Room write does, so a cancelled caller loses the write. */
    private fun TestScope.debounced() = DebouncedEdit<String>(this, 300, onFailure = { failures += it }) {
        yield()
        written += it
    }

    @Test
    fun theEditIsWrittenAfterThePause() = runTest {
        val edit = debounced()
        edit.submit("42.5")
        advanceTimeBy(299)
        runCurrent()
        assertEquals(emptyList<String>(), written)
        advanceUntilIdle()
        assertEquals(listOf("42.5"), written)
        assertNull(edit.pending)
    }

    /** Keypad Done after the pause (the `+2.5` then Done bug): the value stays written, once. */
    @Test
    fun commitAfterThePauseKeepsTheWrite() = runTest {
        val edit = debounced()
        edit.submit("42.5")
        advanceUntilIdle()
        edit.commit()
        assertEquals(listOf("42.5"), written)
    }

    @Test
    fun onlyTheLatestEditIsWritten() = runTest {
        val edit = debounced()
        edit.submit("4")
        advanceTimeBy(100)
        edit.submit("42")
        advanceUntilIdle()
        assertEquals(listOf("42"), written)
    }

    @Test
    fun commitBeforeThePauseWritesAtOnce() = runTest {
        val edit = debounced()
        edit.submit("42")
        edit.commit()
        advanceUntilIdle()
        assertEquals(listOf("42"), written)
    }

    /**
     * Ticking a set just after the pause (the lost-tick bug): commit() must wait for the write
     * the pause already started, or the tick runs alongside it and one overwrites the other.
     */
    @Test
    fun commitWaitsForAWriteInFlight() = runTest {
        val gate = CompletableDeferred<Unit>()
        val edit = DebouncedEdit<String>(this, 300, onFailure = { failures += it }) {
            gate.await()
            written += it
        }
        edit.submit("85")
        advanceTimeBy(301)
        runCurrent() // the pause is over and the write is waiting on the gate
        val committed = launch { edit.commit() }
        runCurrent()
        assertFalse(committed.isCompleted, "commit() returned while the write was still running")
        gate.complete(Unit)
        advanceUntilIdle()
        assertTrue(committed.isCompleted)
        assertEquals(listOf("85"), written)
    }

    /** A write that has started finishes even if its scope goes (the screen closing mid-write). */
    @Test
    fun aStartedWriteSurvivesCancellation() = runTest {
        val gate = CompletableDeferred<Unit>()
        val scope = CoroutineScope(coroutineContext + Job())
        val edit = DebouncedEdit<String>(scope, 300, onFailure = { failures += it }) {
            gate.await()
            written += it
        }
        edit.submit("85")
        advanceTimeBy(301)
        runCurrent()
        scope.cancel()
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf("85"), written)
    }

    @Test
    fun takeDropsTheEdit() = runTest {
        val edit = debounced()
        edit.submit("42")
        assertEquals("42", edit.take())
        advanceUntilIdle()
        assertEquals(emptyList<String>(), written)
    }

    /** A write that fails after the pause used to escape into the screen's scope and crash the app. */
    @Test
    fun aWriteThatFailsAfterThePauseDoesNotEscape() = runTest {
        val edit = DebouncedEdit<String>(this, 300, onFailure = { failures += it }) { error("disk full") }
        edit.submit("42.5")
        advanceUntilIdle()
        assertNull(edit.pending)
        assertEquals(listOf("disk full"), failures.map { it.message })
    }

    @Test
    fun aSetCanBeTickedWithRepsJustTyped() {
        val empty = WorkoutSet(id = 1, position = 0, weightKg = 60.0, reps = null, rir = null, setType = SetType.WORKING, completedAt = null, notes = null)
        assertFalse(canTick(empty, focusedField = null, buffer = ""))
        assertTrue(canTick(empty, focusedField = SetField.REPS, buffer = "8"))
        assertFalse(canTick(empty, focusedField = SetField.WEIGHT, buffer = "62.5"))
        assertTrue(canTick(empty.copy(reps = 8), focusedField = null, buffer = ""))
    }
}
