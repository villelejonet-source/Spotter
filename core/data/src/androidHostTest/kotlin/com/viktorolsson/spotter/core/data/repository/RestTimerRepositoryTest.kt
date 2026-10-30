package com.viktorolsson.spotter.core.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** RestTimerRepository.runOut: the rest timing both platforms share. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class RestTimerRepositoryTest {
    private val clock = MutableClock(Instant.parse("2026-10-30T10:00:00Z"))
    private val repo = RestTimerRepository(
        PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + SupervisorJob())) {
            File.createTempFile("rest", ".preferences_pb").apply { delete(); deleteOnExit() }
        },
        clock,
    )

    @Test
    fun aRestAlertsWhenItRunsOutThenEnds() = runTest {
        repo.start(90)
        val rest = repo.restTimer.first()!!
        val events = mutableListOf<String>()
        var waited: Duration? = null
        val job = launch {
            repo.runOut(rest, onResting = { waited = it; events += "resting" }, onEnded = { events += "ended" })
        }
        runCurrent()
        assertEquals(listOf("resting"), events)
        assertEquals(90.seconds, waited)

        advanceTimeBy(89_000)
        runCurrent()
        assertEquals(listOf("resting"), events)

        advanceTimeBy(2_000)
        job.join()
        assertEquals(listOf("resting", "ended"), events)
        assertNull(repo.restTimer.first())
    }

    /** The app wasn't running when the rest ran out: it ends without an alert. */
    @Test
    fun aRestThatAlreadyRanOutEndsQuietly() = runTest {
        repo.start(60)
        val rest = repo.restTimer.first()!!
        clock.advanceSeconds(65)
        val events = mutableListOf<String>()
        repo.runOut(rest, onResting = { events += "resting" }, onEnded = { events += "ended" })
        assertEquals(emptyList<String>(), events)
        assertNull(repo.restTimer.first())
    }
}
