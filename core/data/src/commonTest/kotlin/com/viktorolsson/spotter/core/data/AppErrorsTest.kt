package com.viktorolsson.spotter.core.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AppErrorsTest {
    private val errors = AppErrors()

    private fun kotlinx.coroutines.test.TestScope.shown(): MutableList<Failure> {
        val shown = mutableListOf<Failure>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { errors.messages.collect { shown += it } }
        return shown
    }

    @Test
    fun aFailedActionIsShownNotThrown() = runTest {
        val shown = shown()
        val job = launchReporting(errors, Failure.SAVE) { error("disk full") }
        job.join()
        assertEquals(listOf(Failure.SAVE), shown)
        assertTrue(job.isCompleted && !job.isCancelled)
    }

    @Test
    fun cancellationIsNotAFailure() = runTest {
        val shown = shown()
        val job = launchReporting(errors) { awaitCancellation() }
        job.cancel()
        job.join()
        assertEquals(emptyList(), shown)
    }

    @Test
    fun theHandlerCatchesWhatAScopeDidNot() = runTest {
        val shown = shown()
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler) + errors.handler)
        scope.launch { error("boom") }.join()
        assertEquals(listOf(Failure.ACTION), shown)
    }
}
