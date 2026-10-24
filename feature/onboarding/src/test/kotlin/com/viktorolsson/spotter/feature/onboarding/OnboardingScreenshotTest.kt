package com.viktorolsson.spotter.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoborazziComposeOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.fontScale
import com.viktorolsson.spotter.core.engine.GeneratedPlan
import com.viktorolsson.spotter.core.model.Equipment
import com.viktorolsson.spotter.core.model.ExperienceLevel
import com.viktorolsson.spotter.core.model.Goal
import com.viktorolsson.spotter.core.model.Limitation
import com.viktorolsson.spotter.core.model.MovementPattern
import com.viktorolsson.spotter.core.model.Sex
import com.viktorolsson.spotter.core.ui.PreviewData
import com.viktorolsson.spotter.core.ui.theme.SpotterTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalRoborazziApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class OnboardingScreenshotTest {
    private val answers = Answers(
        sex = Sex.FEMALE, age = "34", heightCm = "168", weight = "64", goal = Goal.HYPERTROPHY,
        experience = ExperienceLevel.INTERMEDIATE, days = 4, minutes = 60,
        equipment = EquipmentPreset.HOME_GYM.equipment, focus = setOf(com.viktorolsson.spotter.core.model.BodyArea.GLUTES),
        limitations = setOf(Limitation.KNEE), lifts = mapOf(Lift.BENCH to LiftInput("45", "8")),
    )

    private fun capture(name: String, dark: Boolean, fontScale: Float = 1f, content: @Composable () -> Unit) =
        captureRoboImage(
            "src/test/screenshots/$name.png",
            roborazziComposeOptions = RoborazziComposeOptions { fontScale(fontScale) },
        ) {
            SpotterTheme(darkTheme = dark, dynamicColor = false) { content() }
        }

    @Composable
    private fun step(step: Step, dark: Boolean = false, state: OnboardingUiState = OnboardingUiState(step = step, answers = answers)) =
        OnboardingScreen(
            state = state, onBack = {}, onSkip = {}, onNext = {}, onUpdate = {}, onChoose = {},
            onImperial = {}, onSplit = {}, onFinish = {},
        )

    @Test
    fun welcome_light() = capture("welcome_light", dark = false) { WelcomeScreen({}, {}, {}) }

    @Test
    fun welcome_dark() = capture("welcome_dark", dark = true) { WelcomeScreen({}, {}, {}) }

    @Test
    fun checkingBackup_light() = capture("welcome_checking_backup_light", dark = false) { CheckingBackup() }

    @Test
    fun step_body() = capture("onboarding_body_light", dark = false) { step(Step.BODY) }

    @Test
    fun step_body_dark_largeFont() = capture("onboarding_body_dark_font_1_6", dark = true, fontScale = 1.6f) { step(Step.BODY) }

    @Test
    fun step_goal() = capture("onboarding_goal_light", dark = false) { step(Step.GOAL) }

    @Test
    fun step_experience_dark() = capture("onboarding_experience_dark", dark = true) { step(Step.EXPERIENCE) }

    @Test
    fun step_days() = capture("onboarding_days_light", dark = false) { step(Step.DAYS) }

    @Test
    fun step_length() = capture("onboarding_length_light", dark = false) { step(Step.LENGTH) }

    @Test
    fun step_equipment() = capture("onboarding_equipment_light", dark = false) { step(Step.EQUIPMENT) }

    @Test
    fun step_focus_dark() = capture("onboarding_focus_dark", dark = true) { step(Step.FOCUS) }

    @Test
    fun step_limitations() = capture("onboarding_limitations_light", dark = false) { step(Step.LIMITATIONS) }

    @Test
    fun step_lifts() = capture("onboarding_lifts_light", dark = false) { step(Step.LIFTS) }

    @Test
    fun step_summary_generating() = capture("onboarding_summary_loading_light", dark = false) { step(Step.SUMMARY) }

    @Test
    fun step_summary() = capture("onboarding_summary_light", dark = false) {
        step(
            Step.SUMMARY,
            state = OnboardingUiState(
                step = Step.SUMMARY,
                answers = answers,
                generated = GeneratedPlan(PreviewData.plan, listOf(52, 58), setOf(MovementPattern.VERTICAL_PULL)),
            ),
        )
    }

    @Test
    fun step_summary_dark() = capture("onboarding_summary_dark", dark = true) {
        step(
            Step.SUMMARY,
            state = OnboardingUiState(step = Step.SUMMARY, answers = answers, generated = GeneratedPlan(PreviewData.plan, listOf(52, 58), emptySet())),
        )
    }

    @Suppress("unused")
    private val keepImports = Equipment.BARBELL
}
