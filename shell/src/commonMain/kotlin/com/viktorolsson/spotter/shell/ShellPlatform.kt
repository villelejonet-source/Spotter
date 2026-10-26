package com.viktorolsson.spotter.shell

import androidx.compose.runtime.Composable

/**
 * Keeps whatever the platform runs during a workout going while one is [active]: on Android the
 * rest-timer foreground service (which also restores the timer after the process was killed);
 * nothing yet on iOS, whose rest alerts come in phase 5.
 */
@Composable
internal expect fun WhileWorkoutActive(active: Boolean)
