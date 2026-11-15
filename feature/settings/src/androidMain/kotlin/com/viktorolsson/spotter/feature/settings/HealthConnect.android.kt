package com.viktorolsson.spotter.feature.settings

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.viktorolsson.spotter.core.data.health.HealthConnectStore

// Health Connect's permission screen and settings are Android activities.
@Composable
internal actual fun rememberHealthStoreActions(onAccess: (HealthAccess, Boolean) -> Unit): HealthStoreActions {
    val context = LocalContext.current
    val saving = rememberLauncherForActivityResult(HealthConnectStore.permissionRequest()) { granted ->
        onAccess(HealthAccess.SAVE_WORKOUTS, granted.containsAll(HealthConnectStore.WRITE_PERMISSIONS))
    }
    // Reading works with whichever of the three the person allows.
    val reading = rememberLauncherForActivityResult(HealthConnectStore.permissionRequest()) { granted ->
        onAccess(HealthAccess.READ_RECOVERY, granted.any { it in HealthConnectStore.READ_PERMISSIONS })
    }
    return remember(saving, reading, context) {
        HealthStoreActions(
            requestAccess = { access ->
                when (access) {
                    HealthAccess.SAVE_WORKOUTS -> saving.launch(HealthConnectStore.WRITE_PERMISSIONS)
                    HealthAccess.READ_RECOVERY -> reading.launch(HealthConnectStore.READ_PERMISSIONS)
                }
            },
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
