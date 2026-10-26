package com.viktorolsson.spotter.feature.session

import androidx.compose.runtime.Composable

// What the workout screen needs from the platform. The rest-timer alerts themselves are
// Android's foreground service for now; iOS gets local notifications in phase 5.

/** Keeps the screen on while the workout screen is showing. */
@Composable
internal expect fun KeepScreenOn()

/** Asks once, when a workout opens, for permission to show the rest notification where one is needed. */
@Composable
internal expect fun RequestNotificationPermission()

/** Milliseconds since boot, unaffected by clock changes, for the stopwatch. */
internal expect fun elapsedRealtimeMs(): Long
