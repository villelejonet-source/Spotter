package com.viktorolsson.spotter.feature.settings

import androidx.compose.runtime.Composable

// No health store on iOS yet (HealthKit is a later step), so the section is hidden and these are never called.
@Composable
internal actual fun rememberHealthStoreActions(onAccess: (Boolean) -> Unit): HealthStoreActions = HealthStoreActions({}, {})
