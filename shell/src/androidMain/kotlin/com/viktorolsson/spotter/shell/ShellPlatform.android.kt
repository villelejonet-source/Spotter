package com.viktorolsson.spotter.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.viktorolsson.spotter.feature.session.timer.WorkoutService

/**
 * Starts the workout service whenever the app is in the foreground with a workout
 * active, which also restores the rest timer after the process was killed.
 */
@Composable
internal actual fun WhileWorkoutActive(active: Boolean) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { WorkoutService.start(context) }
    }
}
