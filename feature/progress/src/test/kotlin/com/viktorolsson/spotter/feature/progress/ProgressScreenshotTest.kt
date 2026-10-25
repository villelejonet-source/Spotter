package com.viktorolsson.spotter.feature.progress

import androidx.compose.runtime.Composable
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoborazziComposeOptions
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.ThresholdValidator
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.fontScale
import com.viktorolsson.spotter.core.data.repository.LoggedExerciseSummary
import com.viktorolsson.spotter.core.model.BodyWeightPoint
import com.viktorolsson.spotter.core.model.MovementPattern
import com.viktorolsson.spotter.core.model.MuscleGroup
import com.viktorolsson.spotter.core.model.MuscleVolume
import com.viktorolsson.spotter.core.ui.PreviewData
import com.viktorolsson.spotter.core.ui.theme.SpotterTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

@OptIn(ExperimentalRoborazziApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class ProgressScreenshotTest {
    private val volume = listOf(
        MuscleVolume(MuscleGroup.CHEST, 12.0, 10, 20),
        MuscleVolume(MuscleGroup.BACK, 14.5, 10, 20),
        MuscleVolume(MuscleGroup.SHOULDERS, 6.0, 10, 20),
        MuscleVolume(MuscleGroup.QUADS, 9.0, 10, 20),
        MuscleVolume(MuscleGroup.HAMSTRINGS, 4.0, 10, 20),
        MuscleVolume(MuscleGroup.GLUTES, 22.0, 10, 20),
    )
    private val weight = (0 until 10).map { i ->
        BodyWeightPoint(LocalDate.of(2026, 10, 1).plusDays(i * 2L), 82.0 - i * 0.2 + (i % 3) * 0.3, 82.0 - i * 0.18)
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
    private fun weightDialog() = LogWeightDialog(unit = com.viktorolsson.spotter.core.model.WeightUnit.KG, initial = "82.5", onSave = { false }, onDismiss = {})

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
            SpotterTheme(darkTheme = false, dynamicColor = false) { weightDialog() }
        }
    }
}
