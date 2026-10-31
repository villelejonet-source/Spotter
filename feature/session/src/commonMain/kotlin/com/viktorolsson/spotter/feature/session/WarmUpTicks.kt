package com.viktorolsson.spotter.feature.session

import com.viktorolsson.spotter.core.engine.MobilityDrill
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Warm-up drills ticked off in the active workout. Lives as long as the app (a Koin single),
 * so minimizing the workout and coming back keeps them; never saved, and only one workout's
 * ticks are kept, since only one workout runs at a time.
 */
class WarmUpTicks {
    private val ticks = MutableStateFlow<Pair<Long, Set<MobilityDrill>>?>(null)

    fun observe(sessionId: Long): Flow<Set<MobilityDrill>> =
        ticks.map { it?.takeIf { (id, _) -> id == sessionId }?.second.orEmpty() }.distinctUntilChanged()

    fun get(sessionId: Long): Set<MobilityDrill> = ticks.value?.takeIf { it.first == sessionId }?.second.orEmpty()

    fun toggle(sessionId: Long, drill: MobilityDrill) = ticks.update { current ->
        val done = current?.takeIf { it.first == sessionId }?.second.orEmpty()
        sessionId to if (drill in done) done - drill else done + drill
    }
}
