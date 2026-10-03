package com.viktorolsson.spotter.feature.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.viktorolsson.spotter.core.model.ThemeMode
import com.viktorolsson.spotter.core.model.UserPreferences
import com.viktorolsson.spotter.core.ui.theme.SpotterTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class SettingsScreenshotTest {
    private fun capture(name: String, dark: Boolean) = captureRoboImage("src/test/screenshots/$name.png") {
        SpotterTheme(darkTheme = dark, dynamicColor = false) {
            SettingsScreen(
                preferences = UserPreferences(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT, dynamicColor = false, logRir = true),
                planName = "Upper / Lower · 4 days",
                onOpenPlan = {}, onRebuildPlan = {}, onThemeModeChange = {}, onDynamicColorChange = {},
                onWeightUnitChange = {}, onDefaultRestChange = {}, onLogRirChange = {},
            )
        }
    }

    @Test
    fun settings_light() = capture("settings_light", dark = false)

    @Test
    fun settings_dark() = capture("settings_dark", dark = true)
}
