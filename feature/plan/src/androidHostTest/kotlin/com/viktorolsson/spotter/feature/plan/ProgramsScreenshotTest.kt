package com.viktorolsson.spotter.feature.plan

import androidx.compose.runtime.Composable
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoborazziComposeOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.fontScale
import com.viktorolsson.spotter.core.engine.GeneratedPlan
import com.viktorolsson.spotter.core.model.EffortScale
import com.viktorolsson.spotter.core.model.Equipment
import com.viktorolsson.spotter.core.model.ExperienceLevel
import com.viktorolsson.spotter.core.model.Goal
import com.viktorolsson.spotter.core.model.Program
import com.viktorolsson.spotter.core.model.Sex
import com.viktorolsson.spotter.core.model.UserProfile
import com.viktorolsson.spotter.core.model.WeightUnit
import com.viktorolsson.spotter.core.ui.PreviewData
import com.viktorolsson.spotter.core.ui.theme.SpotterTheme
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalRoborazziApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class ProgramsScreenshotTest {
    // Unused directly, but with it captureRoboImage doesn't wait for infinite animations
    // (loading spinners) to settle, which otherwise takes minutes per test.
    @get:Rule
    val compose = createComposeRule()

    // Dumbbells only: 5/3/1 shows why it can't be picked.
    private val profile = UserProfile(
        sex = Sex.FEMALE, heightCm = 168.0, birthDate = LocalDate(1995, 5, 5), bodyWeightKg = 62.0, units = WeightUnit.KG,
        experience = ExperienceLevel.INTERMEDIATE, goal = Goal.HYPERTROPHY, daysPerWeek = 4, sessionLengthMinutes = 60,
        equipment = setOf(Equipment.DUMBBELL, Equipment.BENCH), focusAreas = emptySet(), limitations = emptySet(),
    )
    private val list = ProgramsUiState(loading = false, profile = profile, current = Program.FULL_BODY, suited = setOf(Program.UPPER_LOWER))
    private val preview = ProgramPreviewUiState(
        Program.UPPER_LOWER,
        generated = GeneratedPlan(PreviewData.plan, listOf(52, 58), emptySet()),
        estimated = true,
        effortScale = EffortScale.RIR,
    )

    private fun capture(name: String, dark: Boolean, fontScale: Float = 1f, content: @Composable () -> Unit) =
        captureRoboImage(
            "src/test/screenshots/$name.png",
            roborazziComposeOptions = RoborazziComposeOptions { fontScale(fontScale) },
        ) {
            SpotterTheme(darkTheme = dark, dynamicColor = false) { content() }
        }

    @Composable
    private fun Programs(state: ProgramsUiState, expanded: Program? = Program.UPPER_LOWER) =
        ProgramsScreen(state, onBack = {}, onPreview = {}, onQuestionnaire = {}, initiallyExpanded = expanded)

    @Test
    fun programs_light() = capture("programs_light", dark = false) { Programs(list) }

    @Test
    fun programs_dark() = capture("programs_dark", dark = true) { Programs(list) }

    @Test
    fun programs_largeFont() = capture("programs_font_1_6", dark = false, fontScale = 1.6f) { Programs(list) }

    @Test
    fun programs_noProfile() = capture("programs_no_profile_light", dark = false) { Programs(ProgramsUiState(loading = false), expanded = null) }

    @Test
    fun preview_light() = capture("program_preview_light", dark = false) { ProgramPreviewScreen(preview, onBack = {}, onSwitch = {}) }

    @Test
    fun preview_dark() = capture("program_preview_dark", dark = true) { ProgramPreviewScreen(preview, onBack = {}, onSwitch = {}) }
}
