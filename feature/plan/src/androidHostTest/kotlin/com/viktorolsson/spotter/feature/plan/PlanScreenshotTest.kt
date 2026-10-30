package com.viktorolsson.spotter.feature.plan

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoborazziComposeOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.fontScale
import com.viktorolsson.spotter.core.model.EffortScale
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
class PlanScreenshotTest {
    // Unused directly, but with it captureRoboImage doesn't wait for infinite animations
    // (loading spinners) to settle, which otherwise takes minutes per test.
    @get:Rule
    val compose = createComposeRule()

    private val withPlan = PlanUiState(loading = false, plan = PreviewData.plan, nextDayId = PreviewData.lowerA.id, effortScale = EffortScale.RIR)

    private fun capture(name: String, dark: Boolean, state: PlanUiState, fontScale: Float = 1f) =
        captureRoboImage(
            "src/test/screenshots/$name.png",
            roborazziComposeOptions = RoborazziComposeOptions { fontScale(fontScale) },
        ) {
            SpotterTheme(darkTheme = dark, dynamicColor = false) {
                PlanScreen(state, onBack = {}, onRebuild = {}, onStartDay = {})
            }
        }

    @Test
    fun plan_light() = capture("plan_light", dark = false, state = withPlan)

    @Test
    fun plan_dark() = capture("plan_dark", dark = true, state = withPlan.copy(effortScale = EffortScale.RPE))

    @Test
    fun plan_largeFont() = capture("plan_font_1_6", dark = false, state = withPlan, fontScale = 1.6f)

    @Test
    fun plan_empty() = capture("plan_empty_light", dark = false, state = PlanUiState(loading = false))

    @Test
    fun plan_loading() = capture("plan_loading_light", dark = false, state = PlanUiState(loading = true))
}
