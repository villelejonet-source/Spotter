package com.viktorolsson.spotter.feature.settings

import androidx.compose.runtime.Composable

/** Opens the phone's email app on its inbox; does nothing if there isn't one. */
@Composable
internal expect fun rememberOpenEmailApp(): () -> Unit
