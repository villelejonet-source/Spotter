package com.viktorolsson.spotter.feature.settings

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.viktorolsson.spotter.core.data.health.HealthConnectStore

// Health Connect's permission screen and settings are Android activities.
@Composable
internal actual fun rememberHealthStoreActions(onAccess: (Boolean) -> Unit): HealthStoreActions {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(HealthConnectStore.permissionRequest()) { granted ->
        onAccess(granted.containsAll(HealthConnectStore.WRITE_PERMISSIONS))
    }
    return remember(launcher, context) {
        HealthStoreActions(
            requestAccess = { launcher.launch(HealthConnectStore.WRITE_PERMISSIONS) },
            open = {
                try {
                    context.startActivity(HealthConnectStore.openIntent(context))
                } catch (_: ActivityNotFoundException) {
                    // No Play Store to install it from: nothing to open.
                }
            },
        )
    }
}
