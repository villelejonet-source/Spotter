package com.viktorolsson.spotter.feature.session

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class DebouncedEditTest {
    private val written = mutableListOf<String>()

    /** Suspends like a Room write does, so a cancelled caller loses the write. */
    private fun TestScope.debounced() = DebouncedEdit<String>(this, 300) {
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

    @Test
    fun takeDropsTheEdit() = runTest {
        val edit = debounced()
        edit.submit("42")
        assertEquals("42", edit.take())
        advanceUntilIdle()
        assertEquals(emptyList<String>(), written)
    }
}
