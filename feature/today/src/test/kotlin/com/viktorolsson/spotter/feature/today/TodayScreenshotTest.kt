package com.viktorolsson.spotter.feature.today

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.viktorolsson.spotter.core.model.MovementPattern
import com.viktorolsson.spotter.core.model.PlanDay
import com.viktorolsson.spotter.core.model.PlanExercise
import com.viktorolsson.spotter.core.model.ProgressionRule
import com.viktorolsson.spotter.core.ui.PreviewData
import com.viktorolsson.spotter.core.ui.theme.SpotterTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class TodayScreenshotTest {
    private fun ex(name: String, position: Int, sets: Int, min: Int, max: Int) = PlanExercise(
        id = position.toLong(), exercise = PreviewData.exercise(id = name, name = name, pattern = MovementPattern.SQUAT),
        position = position, sets = sets, repMin = min, repMax = max, targetRir = 1, restSeconds = 120,
        progressionRule = ProgressionRule.DOUBLE_PROGRESSION, supersetGroup = null, startingWeightKg = null,
    )

    private val lowerA = PlanDay(
        2, 1, "Lower A",
        listOf(
            ex("Barbell Back Squat", 0, 3, 6, 12), ex("Romanian Deadlift", 1, 3, 8, 14), ex("Bulgarian Split Squat", 2, 3, 8, 14),
            ex("Leg Extension", 3, 3, 10, 15), ex("Barbell Hip Thrust", 4, 3, 10, 15), ex("Lying Leg Curl", 5, 3, 10, 15),
        ),
    )

    private fun capture(name: String, dark: Boolean, state: TodayUiState) = captureRoboImage("src/test/screenshots/$name.png") {
        SpotterTheme(darkTheme = dark, dynamicColor = false) {
            TodayScreen(state, onResume = {}, onStartNext = {}, onEmptyWorkout = {}, onOpenPlan = {}, onBuildPlan = {})
        }
    }

    @Test
    fun nextWorkout_light() = capture("today_next_light", false, TodayUiState(loading = false, hasPlan = true, nextDay = lowerA))

    @Test
    fun deloadWeek_dark() = capture(
        "today_deload_dark", true,
        TodayUiState(loading = false, hasPlan = true, nextDay = lowerA, deloadUntil = LocalDate.of(2026, 10, 9)),
    )

    @Test
    fun noPlan_light() = capture("today_no_plan_light", false, TodayUiState(loading = false, hasPlan = false))
}
