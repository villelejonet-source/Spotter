package com.viktorolsson.spotter.feature.progress

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoborazziComposeOptions
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.ThresholdValidator
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.fontScale
import com.viktorolsson.spotter.core.data.repository.LoggedExerciseSummary
import androidx.compose.material3.Surface
import com.viktorolsson.spotter.core.model.BodyWeightPoint
import com.viktorolsson.spotter.core.model.FeelSummary
import com.viktorolsson.spotter.core.model.MovementPattern
import com.viktorolsson.spotter.core.model.MuscleGroup
import com.viktorolsson.spotter.core.model.MuscleVolume
import com.viktorolsson.spotter.core.ui.PreviewData
import com.viktorolsson.spotter.core.ui.theme.SpotterTheme
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import com.viktorolsson.spotter.core.engine.Gamification
import com.viktorolsson.spotter.core.model.PersonalRecordType
import com.viktorolsson.spotter.core.model.PlayedSession
import kotlinx.datetime.atTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalRoborazziApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class ProgressScreenshotTest {
    // Unused directly, but with it captureRoboImage doesn't wait for infinite animations
    // (loading spinners) to settle, which otherwise takes minutes per test.
    @get:Rule
    val compose = createComposeRule()

    private val volume = listOf(
        MuscleVolume(MuscleGroup.CHEST, 12.0, 10, 20),
        MuscleVolume(MuscleGroup.BACK, 14.5, 10, 20),
        MuscleVolume(MuscleGroup.SHOULDERS, 6.0, 10, 20),
        MuscleVolume(MuscleGroup.QUADS, 9.0, 10, 20),
        MuscleVolume(MuscleGroup.HAMSTRINGS, 4.0, 10, 20),
        MuscleVolume(MuscleGroup.GLUTES, 22.0, 10, 20),
    )
    private val weight = (0 until 10).map { i ->
        BodyWeightPoint(LocalDate(2026, 10, 1).plus(i * 2L, DateTimeUnit.DAY), 82.0 - i * 0.2 + (i % 3) * 0.3, 82.0 - i * 0.18)
    }
    private val lifts = listOf(
        LoggedExerciseSummary(PreviewData.exercise(), PreviewData.startedAt, 12),
        LoggedExerciseSummary(PreviewData.exercise("barbell-back-squat", "Barbell Back Squat", MovementPattern.SQUAT), PreviewData.startedAt, 9),
        LoggedExerciseSummary(PreviewData.exercise("barbell-row", "Barbell Row", MovementPattern.HORIZONTAL_PULL), PreviewData.startedAt, 11),
    )
    private val full = ProgressUiState(
        loading = false,
        records = PreviewData.records,
        volume = volume,
        bodyWeight = weight,
        lifts = lifts,
    )

    private fun capture(name: String, dark: Boolean, state: ProgressUiState, fontScale: Float = 1f, dynamic: Boolean = false) =
        captureRoboImage(
            "src/test/screenshots/$name.png",
            roborazziComposeOptions = RoborazziComposeOptions { fontScale(fontScale) },
        ) {
            SpotterTheme(darkTheme = dark, dynamicColor = dynamic) {
                ProgressScreen(state, onOpenExercise = {}, onApply = {}, onDismiss = {}, onLogWeight = { true })
            }
        }

    private fun captureFeel(name: String, dark: Boolean, fontScale: Float = 1f) = captureRoboImage(
        "src/test/screenshots/$name.png",
        roborazziComposeOptions = RoborazziComposeOptions { fontScale(fontScale) },
    ) {
        SpotterTheme(darkTheme = dark, dynamicColor = false) {
            Surface { FeelCard(FeelSummary(rated = 9, averageReadiness = 3.4, averageFeel = 3.9, lowDays = 2)) }
        }
    }

    @Test
    fun feelCard_light() = captureFeel("progress_feel_light", dark = false)

    @Test
    fun feelCard_dark() = captureFeel("progress_feel_dark", dark = true)

    @Test
    fun feelCard_largeFont() = captureFeel("progress_feel_font_1_6", dark = false, fontScale = 1.6f)

    @Test
    fun progress_light() = capture("progress_light", dark = false, state = full)

    @Test
    fun progress_dark() = capture("progress_dark", dark = true, state = full)

    @Test
    fun progress_dynamic() = capture("progress_dynamic", dark = false, state = full, dynamic = true)

    @Test
    fun progress_largeFont() = capture("progress_font_1_6", dark = false, state = full, fontScale = 1.6f)

    @Test
    fun progress_empty() = capture("progress_empty_light", dark = false, state = ProgressUiState(loading = false))

    @Test
    fun progress_loading() = capture("progress_loading_light", dark = false, state = ProgressUiState(loading = true))

    @Composable
    private fun WeightDialog() = LogWeightDialog(unit = com.viktorolsson.spotter.core.model.WeightUnit.KG, initial = "82.5", onSave = { false }, onDismiss = {})

    @Test
    fun logWeightDialog_light() {
        // The dialog's rounded window corners anti-alias slightly differently on macOS and
        // Linux; allow a sliver of changed pixels (0.1 %, far less than any text change).
        captureRoboImage(
            "src/test/screenshots/progress_log_weight_light.png",
            roborazziOptions = RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(resultValidator = ThresholdValidator(0.001f)),
            ),
        ) {
            SpotterTheme(darkTheme = false, dynamicColor = false) { WeightDialog() }
        }
    }

    /** Six weeks of three workouts a week, a few PRs along the way. */
    private val progress = Gamification.progress(
        (0 until 18).map { i ->
            PlayedSession(
                i.toLong(), LocalDate(2026, 9, 1).plus(i * 2 + i / 3, DateTimeUnit.DAY).atTime(if (i < 5) 6 else 18, 0), 55, i == 9, if (i == 9) 2 else 4, null,
                16, 2_400.0, setOf("bench-$i"), if (i % 4 == 3) listOf(PersonalRecordType.ESTIMATED_1RM) else emptyList(),
            )
        },
        daysPerWeek = 3,
    )

    private fun captureAchievements(name: String, dark: Boolean, fontScale: Float = 1f) = captureRoboImage(
        "src/test/screenshots/$name.png",
        roborazziComposeOptions = RoborazziComposeOptions { fontScale(fontScale) },
    ) {
        SpotterTheme(darkTheme = dark, dynamicColor = false) {
            AchievementsScreen(AchievementsUiState(loading = false, progress = progress), onBack = {})
        }
    }

    @Test
    fun achievements_light() = captureAchievements("achievements_light", dark = false)

    @Test
    fun achievements_dark() = captureAchievements("achievements_dark", dark = true)

    @Test
    fun achievements_largeFont() = captureAchievements("achievements_font_1_6", dark = false, fontScale = 1.6f)

    @Test
    fun withLevels_light() = capture(
        "progress_levels_light", dark = false,
        full.copy(levels = LevelSummary(progress.level, progress.badges.count { it.unlocked }, progress.badges.size)),
    )
}
