package com.viktorolsson.spotter.feature.settings

import androidx.compose.runtime.Composable
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

// Mail's URL scheme opens the inbox; with Mail removed, nothing happens.
@Composable
internal actual fun rememberOpenEmailApp(): () -> Unit = {
    NSURL.URLWithString("message://")?.let { UIApplication.sharedApplication.openURL(it, emptyMap<Any?, Any>(), null) }
}
