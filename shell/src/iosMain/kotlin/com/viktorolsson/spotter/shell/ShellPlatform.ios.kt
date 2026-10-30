package com.viktorolsson.spotter.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.viktorolsson.spotter.core.data.repository.RestTimerRepository
import kotlinx.coroutines.flow.collectLatest
import org.koin.compose.koinInject

/**
 * As Android's workout service does while the app is open: a rest that runs out is cleared
 * (RestTimerRepository.runOut), so the rest bar goes away instead of sitting at 0:00. Alerting
 * when it ends (a local notification, a Live Activity) comes in phase 5, as runOut's onEnded.
 */
@Composable
internal actual fun WhileWorkoutActive(active: Boolean) {
    val restTimer = koinInject<RestTimerRepository>()
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        restTimer.restTimer.collectLatest { rest -> rest?.let { restTimer.runOut(it) } }
    }
}
