package com.viktorolsson.spotter.feature.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoborazziComposeOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.fontScale
import com.viktorolsson.spotter.core.data.sync.Account
import com.viktorolsson.spotter.core.data.sync.SyncError
import com.viktorolsson.spotter.core.data.sync.SyncStatus
import com.viktorolsson.spotter.core.model.EffortScale
import com.viktorolsson.spotter.core.model.ThemeMode
import com.viktorolsson.spotter.core.model.UserPreferences
import com.viktorolsson.spotter.core.ui.PreviewData
import com.viktorolsson.spotter.core.ui.theme.SpotterTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalRoborazziApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class SettingsScreenshotTest {
    // Unused directly, but with it captureRoboImage doesn't wait for infinite animations
    // (loading spinners) to settle, which otherwise takes minutes per test.
    @get:Rule
    val compose = createComposeRule()

    private fun capture(name: String, dark: Boolean, syncStatus: SyncStatus = SyncStatus(), fontScale: Float = 1f, dynamic: Boolean = false) =
        captureRoboImage(
            "src/test/screenshots/$name.png",
            roborazziComposeOptions = RoborazziComposeOptions { fontScale(fontScale) },
        ) {
            SpotterTheme(darkTheme = dark, dynamicColor = dynamic) {
                SettingsScreen(
                    preferences = UserPreferences(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT, dynamicColor = dynamic, effortScale = EffortScale.RPE),
                    planName = "Upper / Lower · 4 days",
                    syncStatus = syncStatus,
                    sync = SyncActions({}, {}, {}, {}, {}),
                    onOpenPlan = {}, onRebuildPlan = {}, onThemeModeChange = {}, onDynamicColorChange = {},
                    onWeightUnitChange = {}, onDefaultRestChange = {}, onEffortScaleChange = {},
                )
            }
        }

    @Test
    fun settings_light() = capture("settings_light", dark = false)

    @Test
    fun settings_dark() = capture("settings_dark", dark = true)

    @Test
    fun settings_dynamic() = capture("settings_dynamic", dark = false, dynamic = true)

    @Test
    fun settings_largeFont() = capture("settings_font_1_6", dark = false, fontScale = 1.6f)

    @Test
    fun settings_backupNotSignedIn() = capture("settings_backup_signed_out_light", dark = false, syncStatus = SyncStatus(available = true))

    @Test
    fun settings_backupSignedIn_dark() = capture(
        "settings_backup_signed_in_dark", dark = true,
        syncStatus = SyncStatus(available = true, account = Account("user-1", "you@example.com"), lastSyncedAt = PreviewData.startedAt),
    )

    @Test
    fun settings_backupError() = capture(
        "settings_backup_error_light", dark = false,
        syncStatus = SyncStatus(available = true, account = Account("user-1", "you@example.com"), error = SyncError.OFFLINE),
    )
}
