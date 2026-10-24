package com.viktorolsson.spotter.feature.history

import androidx.compose.runtime.Composable
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoborazziComposeOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.fontScale
import com.viktorolsson.spotter.core.model.EffortScale
import com.viktorolsson.spotter.core.model.ExerciseSessionLog
import com.viktorolsson.spotter.core.model.SetType
import com.viktorolsson.spotter.core.model.WorkoutSession
import com.viktorolsson.spotter.core.model.WorkoutSummary
import com.viktorolsson.spotter.core.ui.PreviewData
import com.viktorolsson.spotter.core.ui.localDate
import com.viktorolsson.spotter.core.ui.theme.SpotterTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalRoborazziApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class HistoryScreenshotTest {
    private val today = LocalDate.of(2026, 10, 24)
    private val month = YearMonth.of(2026, 10)

    /** Five sessions across October 2026 with different volumes, for the heat-map. */
    private val sessions: List<WorkoutSession> = listOf(
        PreviewData.session(1, dayName = "Upper A"),
        PreviewData.session(2, dayName = "Lower A", exercises = listOf(PreviewData.rows(id = 20), PreviewData.plank(id = 30))),
        PreviewData.session(3, dayName = null, exercises = listOf(PreviewData.bench(id = 40, completedWorking = 2))),
        PreviewData.session(4, dayName = "Upper B"),
        PreviewData.session(5, dayName = "Lower B", exercises = listOf(PreviewData.rows(id = 50))),
    ).mapIndexed { i, s ->
        val start = PreviewData.startedAt.plus(Duration.ofDays(listOf(0L, 2L, 5L, 9L, 14L)[i]))
        s.copy(startedAt = start, endedAt = start.plusSeconds(3000L + i * 400))
    }.sortedByDescending { it.startedAt }

    private fun historyState(sessions: List<WorkoutSession>) = HistoryUiState(
        loading = false,
        hasAnySession = true,
        month = month,
        monthSessions = sessions,
        volumeByDay = sessions.groupBy { it.startedAt.localDate() }
            .mapValues { (_, list) -> list.sumOf { WorkoutSummary.of(it).volumeKg }.coerceAtLeast(1.0) },
    )

    private fun capture(name: String, dark: Boolean, fontScale: Float = 1f, dynamic: Boolean = false, content: @Composable () -> Unit) =
        captureRoboImage(
            "src/test/screenshots/$name.png",
            roborazziComposeOptions = RoborazziComposeOptions { fontScale(fontScale) },
        ) {
            SpotterTheme(darkTheme = dark, dynamicColor = dynamic) { content() }
        }

    @Composable
    private fun history(state: HistoryUiState) = HistoryScreen(state, onOpenWorkout = {}, onPreviousMonth = {}, onNextMonth = {}, today = today)

    @Test
    fun history_light() = capture("history_light", dark = false) { history(historyState(sessions)) }

    @Test
    fun history_dark() = capture("history_dark", dark = true) { history(historyState(sessions)) }

    @Test
    fun history_dynamic() = capture("history_dynamic", dark = false, dynamic = true) { history(historyState(sessions)) }

    @Test
    fun history_largeFont() = capture("history_font_1_6", dark = false, fontScale = 1.6f) { history(historyState(sessions)) }

    @Test
    fun history_empty() = capture("history_empty_light", dark = false) { history(HistoryUiState(loading = false)) }

    @Test
    fun history_loading() = capture("history_loading_light", dark = false) { history(HistoryUiState(loading = true)) }

    @Test
    fun history_monthWithoutWorkouts() = capture("history_month_empty_dark", dark = true) {
        history(historyState(sessions).copy(monthSessions = emptyList(), volumeByDay = emptyMap(), month = YearMonth.of(2026, 9)))
    }

    private val detail = WorkoutDetailUiState(
        loading = false,
        session = PreviewData.session(notes = "Felt strong today."),
        records = PreviewData.records,
        effortScale = EffortScale.RIR,
    )

    @Composable
    private fun detail(state: WorkoutDetailUiState) = WorkoutDetailScreen(state, onBack = {}, onOpenExercise = {}, onRepeat = {})

    @Test
    fun workoutDetail_light() = capture("workout_detail_light", dark = false) { detail(detail) }

    @Test
    fun workoutDetail_dark() = capture("workout_detail_dark", dark = true) { detail(detail.copy(effortScale = EffortScale.RPE)) }

    @Test
    fun workoutDetail_notFound() = capture("workout_detail_not_found_light", dark = false) { detail(WorkoutDetailUiState(loading = false)) }

    private val logs: List<ExerciseSessionLog> = (0 until 6).map { i ->
        val kg = 70.0 + i * 2.5
        ExerciseSessionLog(
            sessionId = i.toLong() + 1,
            date = PreviewData.startedAt.plus(Duration.ofDays(i * 4L)),
            sets = listOf(
                PreviewData.set(i * 10L + 1, 0, 40.0, 8, SetType.WARMUP, completed = true),
                PreviewData.set(i * 10L + 2, 1, kg, 10, completed = true),
                PreviewData.set(i * 10L + 3, 2, kg, 9, completed = true),
                PreviewData.set(i * 10L + 4, 3, kg, 8, completed = true),
            ),
        )
    }

    private val exerciseHistory = ExerciseHistoryUiState(
        loading = false,
        exercise = PreviewData.exercise(),
        logs = logs,
        records = PreviewData.records,
        repRecords = logs.flatMap { it.sets }.filter { it.setType != SetType.WARMUP }
            .groupBy { it.weightKg ?: 0.0 }.map { (w, sets) -> w to sets.maxOf { it.reps ?: 0 } }.sortedByDescending { it.first },
    )

    @Composable
    private fun exerciseHistory(state: ExerciseHistoryUiState) =
        ExerciseHistoryScreen(state, onBack = {}, onMetric = {}, onApply = {}, onDismiss = {})

    @Test
    fun exerciseHistory_light() = capture("exercise_history_light", dark = false) { exerciseHistory(exerciseHistory) }

    @Test
    fun exerciseHistory_dark() = capture("exercise_history_dark", dark = true) {
        exerciseHistory(exerciseHistory.copy(metric = ExerciseMetric.VOLUME))
    }

    @Test
    fun exerciseHistory_empty() = capture("exercise_history_empty_light", dark = false) {
        exerciseHistory(ExerciseHistoryUiState(loading = false, exercise = PreviewData.exercise()))
    }
}
