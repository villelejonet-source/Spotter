package com.viktorolsson.spotter.feature.session

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoborazziComposeOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.fontScale
import com.viktorolsson.spotter.core.model.PersonalRecord
import com.viktorolsson.spotter.core.model.PersonalRecordType
import com.viktorolsson.spotter.core.model.PlanTarget
import com.viktorolsson.spotter.core.model.PreviousSet
import com.viktorolsson.spotter.core.model.ProgressionReason
import com.viktorolsson.spotter.core.model.SessionExercise
import com.viktorolsson.spotter.core.model.SetType
import com.viktorolsson.spotter.core.model.UserPreferences
import com.viktorolsson.spotter.core.model.WeightUnit
import com.viktorolsson.spotter.core.model.WorkoutSet
import com.viktorolsson.spotter.core.model.WorkoutSummary
import com.viktorolsson.spotter.core.ui.PreviewData
import com.viktorolsson.spotter.core.ui.theme.SpotterTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration
import java.time.Instant

@OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class SessionScreenshotTest {
    private val done = Instant.EPOCH
    private fun set(id: Long, pos: Int, kg: Double?, reps: Int?, type: SetType = SetType.WORKING, completed: Boolean = false) =
        WorkoutSet(id, pos, kg, reps, null, type, if (completed) done else null, null)

    private val bench = SessionExercise(
        id = 1,
        exercise = PreviewData.exercise(),
        position = 0,
        supersetGroup = null,
        restSeconds = 135,
        notes = null,
        sets = listOf(
            set(1, 0, 20.0, 10, SetType.WARMUP, completed = true),
            set(2, 1, 55.0, 3, SetType.WARMUP, completed = true),
            set(3, 2, 82.5, 10, completed = true),
            set(4, 3, 80.0, 10),
            set(5, 4, 80.0, 10),
        ),
        target = PlanTarget(3, 6, 12, 1),
        progressionReason = ProgressionReason.ADD_REPS,
    )
    private val previous = listOf(PreviousSet(80.0, 9), PreviousSet(80.0, 9), PreviousSet(80.0, 9))
    private val actions = ExerciseCardActions({ _, _ -> }, {}, {}, { _, _ -> }, {}, {}, { _, _ -> }, { _, _ -> }, {}, {}, {}, {}, {}, {}, {})

    private fun capture(name: String, dark: Boolean, fontScale: Float = 1f, content: @Composable () -> Unit) =
        captureRoboImage(
            "src/test/screenshots/$name.png",
            roborazziComposeOptions = RoborazziComposeOptions { fontScale(fontScale) },
        ) {
            SpotterTheme(darkTheme = dark, dynamicColor = false) { Surface { content() } }
        }

    @Composable
    private fun card(logRir: Boolean = false) = Column(Modifier.padding(12.dp)) {
        ExerciseCard(
            exercise = bench,
            isFirst = true,
            isLast = false,
            previous = previous,
            preferences = UserPreferences(logRir = logRir),
            focus = KeypadTarget(4, SetField.REPS),
            buffer = "1",
            actions = actions,
        )
    }

    @Test
    fun exerciseCard_light() = capture("exercise_card_light", dark = false) { card() }

    @Test
    fun exerciseCard_dark() = capture("exercise_card_dark", dark = true) { card(logRir = true) }

    /** Large text: the set row must stay usable (no clipped numbers or overlapping columns). */
    @Test
    fun exerciseCard_largeFont() = capture("exercise_card_font_1_6", dark = false, fontScale = 1.6f) { card() }

    @Test
    fun keypad_light() = capture("keypad_light", dark = false) {
        Keypad("Weight · set 1", SetField.WEIGHT, "2.5", showPlates = true, {}, {}, {}, {}, {}, {})
    }

    @Test
    fun summaryWithPrs_dark() = capture("summary_prs_dark", dark = true) {
        val summary = WorkoutSummary(
            duration = Duration.ofMinutes(54),
            completedSets = 18,
            volumeKg = 6240.0,
            exercises = WorkoutSummary.of(
                com.viktorolsson.spotter.core.model.WorkoutSession(1, done, done.plusSeconds(3240), null, listOf(bench)),
            ).exercises,
        )
        val records = listOf(
            PersonalRecord(1, "barbell-bench-press", "Barbell Bench Press", PersonalRecordType.ESTIMATED_1RM, 110.0, 82.5, 10, done, 1),
            PersonalRecord(2, "barbell-bench-press", "Barbell Bench Press", PersonalRecordType.REPS_AT_WEIGHT, 10.0, 80.0, 10, done, 1),
        )
        SummaryScreen(summary, WeightUnit.KG, records, onDone = {})
    }
}
