package com.viktorolsson.spotter.feature.library

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoborazziComposeOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.fontScale
import com.viktorolsson.spotter.core.model.BodyArea
import com.viktorolsson.spotter.core.model.Equipment
import com.viktorolsson.spotter.core.model.MovementPattern
import com.viktorolsson.spotter.core.model.Muscle
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
class ExercisePickerScreenshotTest {
    private val exercises = listOf(
        PreviewData.exercise(),
        PreviewData.exercise("incline-dumbbell-press", "Incline Dumbbell Press", equipment = listOf(Equipment.DUMBBELL, Equipment.BENCH)),
        PreviewData.exercise("push-up", "Push-Up", equipment = emptyList()),
        PreviewData.exercise("cable-fly", "Cable Fly", MovementPattern.CHEST_FLY, listOf(Equipment.CABLE)),
        PreviewData.exercise("dip", "Dip", MovementPattern.VERTICAL_PUSH, listOf(Equipment.DIP_STATION), primary = listOf(Muscle.CHEST, Muscle.TRICEPS)),
        PreviewData.exercise("machine-chest-press", "Machine Chest Press", equipment = listOf(Equipment.MACHINE)).copy(isCustom = true),
    )
    private val loaded = ExercisePickerUiState(
        loading = false,
        filters = PickerFilters(bodyArea = BodyArea.CHEST),
        exercises = exercises,
        selectedIds = listOf("push-up", "barbell-bench-press"),
    )

    private fun capture(name: String, dark: Boolean, state: ExercisePickerUiState, fontScale: Float = 1f) =
        captureRoboImage(
            "src/test/screenshots/$name.png",
            roborazziComposeOptions = RoborazziComposeOptions { fontScale(fontScale) },
        ) {
            SpotterTheme(darkTheme = dark, dynamicColor = false) {
                ExercisePickerScreen(
                    uiState = state, query = state.filters.query, onBack = {}, onQueryChange = {},
                    onBodyAreaChange = {}, onEquipmentChange = {}, onToggle = {}, onAdd = {}, onCreate = {},
                )
            }
        }

    @Test
    fun picker_light() = capture("picker_light", dark = false, state = loaded)

    @Test
    fun picker_dark() = capture("picker_dark", dark = true, state = loaded)

    @Test
    fun picker_largeFont() = capture("picker_font_1_6", dark = false, state = loaded, fontScale = 1.6f)

    @Test
    fun picker_noResults() = capture("picker_empty_light", dark = false, state = loaded.copy(filters = PickerFilters(query = "zzz"), exercises = emptyList(), selectedIds = emptyList()))

    @Test
    fun picker_loading() = capture("picker_loading_light", dark = false, state = ExercisePickerUiState(loading = true))
}
