package com.viktorolsson.spotter.feature.session

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import platform.Foundation.NSProcessInfo
import platform.UIKit.UIApplication

@Composable
internal actual fun KeepScreenOn() {
    DisposableEffect(Unit) {
        UIApplication.sharedApplication.idleTimerDisabled = true
        onDispose { UIApplication.sharedApplication.idleTimerDisabled = false }
    }
}

// Rest notifications on iOS come in phase 5; until then there's nothing to ask for.
@Composable
internal actual fun RequestNotificationPermission() {}

internal actual fun elapsedRealtimeMs(): Long = (NSProcessInfo.processInfo.systemUptime * 1000).toLong()
