package com.viktorolsson.spotter.feature.settings

import androidx.compose.runtime.Composable

/** The two things only the platform can do for the health-store switch. */
internal class HealthStoreActions(
    /** Shows the health store's own permission screen; the result goes to `onAccess`. */
    val requestAccess: () -> Unit,
    /** Opens the health store: where access is managed, or where it's installed from. */
    val open: () -> Unit,
)

/** [onAccess] gets true when the person has allowed Spotter to save workouts. */
@Composable
internal expect fun rememberHealthStoreActions(onAccess: (Boolean) -> Unit): HealthStoreActions
