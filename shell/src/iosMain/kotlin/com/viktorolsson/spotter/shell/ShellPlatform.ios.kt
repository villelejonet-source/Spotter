package com.viktorolsson.spotter.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.viktorolsson.spotter.core.data.repository.RestTimerRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import org.koin.compose.koinInject
import kotlin.time.Clock

/**
 * As Android's workout service does while the app is open: a rest that runs out is cleared, so
 * the rest bar goes away instead of sitting at 0:00. Alerting when it ends (a local notification,
 * a Live Activity) comes in phase 5.
 */
@Composable
internal actual fun WhileWorkoutActive(active: Boolean) {
    val restTimer = koinInject<RestTimerRepository>()
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        restTimer.restTimer.collectLatest { rest ->
            rest ?: return@collectLatest
            val remaining = rest.endsAt - Clock.System.now()
            if (remaining.isPositive()) delay(remaining)
            restTimer.stop()
        }
    }
}
