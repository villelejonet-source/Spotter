package com.viktorolsson.spotter.feature.today

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoborazziComposeOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.fontScale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.viktorolsson.spotter.core.engine.LiftTrend
import com.viktorolsson.spotter.core.engine.LoggedSet
import com.viktorolsson.spotter.core.engine.PlanReview
import com.viktorolsson.spotter.core.engine.PlanSlot
import com.viktorolsson.spotter.core.engine.Plateau
import com.viktorolsson.spotter.core.engine.StalledLift
import com.viktorolsson.spotter.core.engine.SupportReason
import com.viktorolsson.spotter.core.engine.SupportingExercise
import com.viktorolsson.spotter.core.model.Mechanics
import com.viktorolsson.spotter.core.model.MovementPattern
import com.viktorolsson.spotter.core.model.Muscle
import com.viktorolsson.spotter.core.model.ProgressionRule
import com.viktorolsson.spotter.core.model.RecommendationAction
import com.viktorolsson.spotter.core.model.ScheduledWorkout
import com.viktorolsson.spotter.core.model.TrainedDay
import com.viktorolsson.spotter.core.model.WeightUnit
import com.viktorolsson.spotter.core.model.weekStreak
import com.viktorolsson.spotter.core.model.weeklyTotals
import com.viktorolsson.spotter.core.ui.PreviewData
import com.viktorolsson.spotter.core.ui.theme.SpotterTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus

@OptIn(ExperimentalRoborazziApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class TodayScreenshotTest {
    // Unused directly, but with it captureRoboImage doesn't wait for infinite animations
    // (loading spinners) to settle, which otherwise takes minutes per test.
    @get:Rule
    val compose = createComposeRule()

    private val lowerA = PreviewData.lowerA
    private val today = LocalDate(2026, 10, 28)

    /** Eighteen weeks of training, three or four days a week, two workouts so far this week. */
    private val dashboard = run {
        val days = (0 until Dashboard.HEATMAP_WEEKS * 7).mapNotNull { i ->
            val date = today.minus(i, DateTimeUnit.DAY)
            val weekday = date.dayOfWeek.isoDayNumber
            val trained = weekday == 1 || weekday == 3 || (weekday == 5 && i % 14 != 5) || (weekday == 6 && i % 21 == 3)
            if (trained) TrainedDay(date, 4200.0 + (i % 5) * 350.0 - i * 6.0) else null
        }
        Dashboard(
            today = today,
            weeks = weeklyTotals(days, Dashboard.HEATMAP_WEEKS, today),
            workoutsByDay = days.groupingBy { it.date }.eachCount() + (today to 2),
            weeklyGoal = 4,
            streak = weekStreak(days.map { it.date }, today),
            totalWorkouts = 1204,
            unit = WeightUnit.KG,
        )
    }

    private fun next(dashboard: Dashboard? = this.dashboard) =
        TodayUiState(loading = false, today = today, hasPlan = true, nextDay = lowerA, dashboard = dashboard)

    private fun capture(
        name: String,
        dark: Boolean,
        state: TodayUiState,
        dynamic: Boolean = false,
        fontScale: Float = 1f,
        review: PlanReview? = null,
    ) =
        captureRoboImage(
            "src/test/screenshots/$name.png",
            roborazziComposeOptions = RoborazziComposeOptions { fontScale(fontScale) },
        ) {
            SpotterTheme(darkTheme = dark, dynamicColor = dynamic) {
                TodayScreen(state, review = review, onResume = {}, onStartNext = {}, onEmptyWorkout = {}, onOpenPlan = {}, onBuildPlan = {})
            }
        }

    /** Bench stalled at 80 × 6 for four weeks, with three helpers and a variation to pick from. */
    private val review = run {
        val bench = PreviewData.exercise()
        val row = PreviewData.exercise("barbell-row", "Barbell Row", MovementPattern.HORIZONTAL_PULL, primary = listOf(Muscle.UPPER_BACK))
        val slot = PlanSlot(1, 1, "Upper A", bench, 3, 6, 10, 1, ProgressionRule.DOUBLE_PROGRESSION)
        fun add(id: String, name: String, reason: SupportReason, mechanics: Mechanics = Mechanics.COMPOUND) =
            SupportingExercise(PreviewData.exercise(id, name, mechanics = mechanics), reason) to
                RecommendationAction.AddExercise(1, "Upper A", id, name, 3, 6, 10, 2, 120)
        PlanReview(
            since = today.minus(28, DateTimeUnit.DAY),
            sessions = 11,
            trends = listOf(LiftTrend(bench, 98.0, 98.4), LiftTrend(row, 80.0, 86.0)),
            stalled = listOf(
                StalledLift(
                    Plateau(slot, LoggedSet(80.0, 6), sessions = 4, weeks = 4, missedTwice = false, historyDays = 28),
                    listOf(
                        add("incline-barbell-bench-press", "Incline Barbell Bench Press", SupportReason.UPPER_CHEST),
                        add("close-grip-bench-press", "Close-Grip Bench Press", SupportReason.TRICEPS),
                        add("seated-dumbbell-shoulder-press", "Seated Dumbbell Shoulder Press", SupportReason.SHOULDERS),
                    ),
                    RecommendationAction.SwapVariation(1, bench.id, bench.name, "paused-bench-press", "Paused Bench Press", 5),
                ),
            ),
        )
    }

    @Test
    fun planReviewCard_light() = capture("today_review_card_light", false, next(), review = review)

    private fun captureSheet(name: String, dark: Boolean, fontScale: Float = 1f) = captureRoboImage(
        "src/test/screenshots/$name.png",
        roborazziComposeOptions = RoborazziComposeOptions { fontScale(fontScale) },
    ) {
        SpotterTheme(darkTheme = dark) {
            Surface {
                PlanReviewContent(review, WeightUnit.KG, onApply = {}, onKeep = {}, initiallyPicked = setOf("barbell-bench-press|close-grip-bench-press"))
            }
        }
    }

    @Test
    fun planReviewSheet_light() = captureSheet("today_review_sheet_light", false)

    @Test
    fun planReviewSheet_dark() = captureSheet("today_review_sheet_dark", true)

    @Test
    fun planReviewSheet_largeFont() = captureSheet("today_review_sheet_font_1_6", false, fontScale = 1.6f)

    @Test
    fun nextWorkout_light() = capture("today_next_light", false, next())

    @Test
    fun nextWorkout_dark() = capture("today_next_dark", true, next())

    @Test
    fun nextWorkout_largeFont() =
        capture("today_next_font_1_6", false, next(), fontScale = 1.6f)

    /** Dynamic colour on: Robolectric's framework resources supply a fixed system palette. */
    @Test
    fun nextWorkout_dynamic() = capture("today_next_dynamic", false, next(), dynamic = true)

    @Test
    fun deloadWeek_dark() = capture(
        "today_deload_dark", true,
        TodayUiState(loading = false, hasPlan = true, nextDay = lowerA, deloadUntil = LocalDate(2026, 10, 9)),
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
            scheduledToday = scheduled(1, LocalDate(2026, 10, 24), "Lower A", notes = "Go heavy on squats"),
            scheduledDay = lowerA,
            upcoming = listOf(
                scheduled(2, LocalDate(2026, 10, 26), "Upper A"),
                scheduled(3, LocalDate(2026, 10, 28), null),
                scheduled(4, LocalDate(2026, 10, 30), "Lower B"),
            ),
        ),
    )

    @Test
    fun scheduledFree_dark() = capture(
        "today_scheduled_free_dark", true,
        TodayUiState(loading = false, hasPlan = true, nextDay = lowerA, scheduledToday = scheduled(1, LocalDate(2026, 10, 24), null)),
    )

    /** The stats and heatmap, which sit below the fold on a phone. */
    private fun captureDashboard(name: String, dark: Boolean, fontScale: Float = 1f) =
        captureRoboImage(
            "src/test/screenshots/$name.png",
            roborazziComposeOptions = RoborazziComposeOptions { fontScale(fontScale) },
        ) {
            SpotterTheme(darkTheme = dark) {
                Surface {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Stats(dashboard)
                        TrainingDays(dashboard)
                    }
                }
            }
        }

    @Test
    fun dashboard_dark() = captureDashboard("today_dashboard_dark", true)

    @Test
    fun dashboard_light() = captureDashboard("today_dashboard_light", false)

    @Test
    fun dashboard_largeFont() = captureDashboard("today_dashboard_font_1_6", false, fontScale = 1.6f)

    @Test
    fun noWorkoutsYet_light() = capture("today_first_week_light", false, next(dashboard = null))
}
