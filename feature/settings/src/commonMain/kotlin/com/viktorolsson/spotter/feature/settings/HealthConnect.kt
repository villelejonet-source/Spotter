package com.viktorolsson.spotter.feature.settings

import androidx.compose.runtime.Composable

/** The two things the person can let Spotter do with the phone's health store, each asked for on its own. */
internal enum class HealthAccess { SAVE_WORKOUTS, READ_RECOVERY }

/** The two things only the platform can do for the health-store switches. */
internal class HealthStoreActions(
    /** Shows the health store's own permission screen for one kind of access; the result goes to `onAccess`. */
    val requestAccess: (HealthAccess) -> Unit,
    /** Opens the health store: where access is managed, or where it's installed from. */
    val open: () -> Unit,
)

/** [onAccess] gets what was asked for and whether the person allowed it. */
@Composable
internal expect fun rememberHealthStoreActions(onAccess: (HealthAccess, Boolean) -> Unit): HealthStoreActions
