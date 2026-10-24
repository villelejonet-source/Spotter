package com.viktorolsson.spotter.feature.today

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoborazziComposeOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.fontScale
import com.viktorolsson.spotter.core.model.ScheduledWorkout
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
class TodayScreenshotTest {
    private val lowerA = PreviewData.lowerA

    private fun capture(name: String, dark: Boolean, state: TodayUiState, dynamic: Boolean = false, fontScale: Float = 1f) =
        captureRoboImage(
            "src/test/screenshots/$name.png",
            roborazziComposeOptions = RoborazziComposeOptions { fontScale(fontScale) },
        ) {
            SpotterTheme(darkTheme = dark, dynamicColor = dynamic) {
                TodayScreen(state, onResume = {}, onStartNext = {}, onEmptyWorkout = {}, onOpenPlan = {}, onBuildPlan = {})
            }
        }

    @Test
    fun nextWorkout_light() = capture("today_next_light", false, TodayUiState(loading = false, hasPlan = true, nextDay = lowerA))

    @Test
    fun nextWorkout_dark() = capture("today_next_dark", true, TodayUiState(loading = false, hasPlan = true, nextDay = lowerA))

    @Test
    fun nextWorkout_largeFont() =
        capture("today_next_font_1_6", false, TodayUiState(loading = false, hasPlan = true, nextDay = lowerA), fontScale = 1.6f)

    /** Dynamic colour on: Robolectric's framework resources supply a fixed system palette. */
    @Test
    fun nextWorkout_dynamic() = capture("today_next_dynamic", false, TodayUiState(loading = false, hasPlan = true, nextDay = lowerA), dynamic = true)

    @Test
    fun deloadWeek_dark() = capture(
        "today_deload_dark", true,
        TodayUiState(loading = false, hasPlan = true, nextDay = lowerA, deloadUntil = LocalDate.of(2026, 10, 9)),
    )

    @Test
    fun noPlan_light() = capture("today_no_plan_light", false, TodayUiState(loading = false, hasPlan = false))

    @Test
    fun loading_light() = capture("today_loading_light", false, TodayUiState(loading = true))

    @Test
    fun activeWorkout_light() =
        capture("today_active_light", false, TodayUiState(loading = false, hasPlan = true, nextDay = lowerA, activeSessionId = 7))

    private fun scheduled(id: Long, date: LocalDate, day: String?, notes: String? = null) =
        ScheduledWorkout(id = id, date = date, planDayId = if (day != null) id else null, planDayName = day, notes = notes, sessionId = null, done = false)

    @Test
    fun scheduled_light() = capture(
        "today_scheduled_light", false,
        TodayUiState(
            loading = false, hasPlan = true, nextDay = lowerA,
            scheduledToday = scheduled(1, LocalDate.of(2026, 10, 24), "Lower A", notes = "Go heavy on squats"),
            scheduledDay = lowerA,
            upcoming = listOf(
                scheduled(2, LocalDate.of(2026, 10, 26), "Upper A"),
                scheduled(3, LocalDate.of(2026, 10, 28), null),
                scheduled(4, LocalDate.of(2026, 10, 30), "Lower B"),
            ),
        ),
    )

    @Test
    fun scheduledFree_dark() = capture(
        "today_scheduled_free_dark", true,
        TodayUiState(loading = false, hasPlan = true, nextDay = lowerA, scheduledToday = scheduled(1, LocalDate.of(2026, 10, 24), null)),
    )
}
