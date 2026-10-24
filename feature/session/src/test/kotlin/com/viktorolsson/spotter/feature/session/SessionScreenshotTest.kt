package com.viktorolsson.spotter.feature.session

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoborazziComposeOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.github.takahirom.roborazzi.fontScale
import com.viktorolsson.spotter.core.engine.DetectedRecord
import com.viktorolsson.spotter.core.model.EffortScale
import com.viktorolsson.spotter.core.model.PersonalRecordType
import com.viktorolsson.spotter.core.model.PreviousSet
import com.viktorolsson.spotter.core.model.RestTimer
import com.viktorolsson.spotter.core.model.UserPreferences
import com.viktorolsson.spotter.core.model.WeightUnit
import com.viktorolsson.spotter.core.model.WorkoutSummary
import com.viktorolsson.spotter.core.ui.PreviewData
import com.viktorolsson.spotter.core.ui.theme.Spacing
import com.viktorolsson.spotter.core.ui.theme.SpotterTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalRoborazziApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class SessionScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val bench = PreviewData.bench()
    private val previous = listOf(PreviousSet(80.0, 9), PreviousSet(80.0, 9), PreviousSet(80.0, 9))
    private val cardActions = ExerciseCardActions(
        { _, _ -> }, {}, { _, _ -> }, { _, _ -> }, {}, {}, { _, _ -> }, { _, _ -> }, {}, { _, _ -> }, { _, _ -> }, {}, {}, { _, _ -> }, {},
    )
    private val actions = SessionActions({}, {}, {}, {}, {}, {}, {}, {}, { _, _, _ -> }, {}, {}, { _, _ -> }, {})
    private val active = PreviewData.session(finished = false, exercises = listOf(bench, PreviewData.rows(supersetGroup = 1), PreviewData.plank()))
    private val now = PreviewData.startedAt.plusSeconds(32 * 60 + 10)

    private fun capture(name: String, dark: Boolean, fontScale: Float = 1f, dynamic: Boolean = false, content: @Composable () -> Unit) =
        captureRoboImage(
            "src/test/screenshots/$name.png",
            roborazziComposeOptions = RoborazziComposeOptions { fontScale(fontScale) },
        ) {
            SpotterTheme(darkTheme = dark, dynamicColor = dynamic) { Surface { content() } }
        }

    @Composable
    private fun card(effortScale: EffortScale = EffortScale.OFF) = Column(Modifier.padding(Spacing.md)) {
        ExerciseCard(
            exercise = bench,
            isFirst = true,
            isLast = false,
            previous = previous,
            preferences = UserPreferences(effortScale = effortScale),
            focus = KeypadTarget(14, SetField.REPS),
            buffer = "1",
            actions = cardActions,
        )
    }

    @Test
    fun exerciseCard_light() = capture("exercise_card_light", dark = false) { card() }

    @Test
    fun exerciseCard_dark() = capture("exercise_card_dark", dark = true) { card(effortScale = EffortScale.RPE) }

    /** Large text: the set row must stay usable (no clipped numbers or overlapping columns). */
    @Test
    fun exerciseCard_largeFont() = capture("exercise_card_font_1_6", dark = false, fontScale = 1.6f) { card() }

    @Test
    fun keypad_light() = capture("keypad_light", dark = false) {
        Keypad("Weight · set 1", SetField.WEIGHT, "2.5", showPlates = true, {}, {}, {}, {}, {}, {})
    }

    @Composable
    private fun session(restTimer: RestTimer? = null, keypadTarget: KeypadTarget? = null, effortScale: EffortScale = EffortScale.RIR, celebration: PrEvent? = null) {
        val keypad = rememberKeypadState()
        if (keypadTarget != null && keypad.target == null) keypad.focus(keypadTarget, "10")
        SessionScreen(
            state = SessionUiState(
                loading = false, session = active,
                previous = mapOf("barbell-bench-press" to previous, "barbell-row" to listOf(PreviousSet(57.5, 10))),
                preferences = UserPreferences(effortScale = effortScale),
                restTimer = restTimer,
            ),
            session = active,
            swap = null,
            now = now,
            keypad = keypad,
            cardActions = cardActions,
            actions = actions,
            celebration = celebration,
        )
    }

    @Test
    fun session_light() = capture("session_light", dark = false) { session() }

    @Test
    fun session_dark_resting() = capture("session_dark_resting", dark = true) {
        session(restTimer = RestTimer(endsAt = now.plusSeconds(75), totalSeconds = 135), effortScale = EffortScale.RPE)
    }

    @Test
    fun session_resting_keypad() = capture("session_resting_keypad_light", dark = false) {
        session(restTimer = RestTimer(endsAt = now.plusSeconds(75), totalSeconds = 135), keypadTarget = KeypadTarget(14, SetField.WEIGHT))
    }

    @Test
    fun session_dynamic() = capture("session_dynamic", dark = false, dynamic = true) { session() }

    @Test
    fun session_largeFont() = capture("session_font_1_6", dark = false, fontScale = 1.6f) { session() }

    @Test
    fun session_prBanner() = capture("session_pr_banner_light", dark = false) {
        session(
            celebration = PrEvent(
                "Barbell Bench Press", 82.5, 10,
                listOf(DetectedRecord(PersonalRecordType.ESTIMATED_1RM, 110.0, 82.5, 10), DetectedRecord(PersonalRecordType.REPS_AT_WEIGHT, 10.0, 82.5, 10)),
                id = 1,
            ),
        )
    }

    @Test
    fun session_empty() = capture("session_empty_light", dark = false) {
        val empty = PreviewData.session(finished = false, exercises = emptyList(), dayName = null)
        SessionScreen(
            state = SessionUiState(loading = false, session = empty), session = empty, swap = null, now = now,
            keypad = rememberKeypadState(), cardActions = cardActions, actions = actions,
        )
    }

    @Test
    fun finishDialog_light() {
        compose.setContent {
            SpotterTheme(darkTheme = false, dynamicColor = false) {
                Surface(Modifier.fillMaxSize()) {}
                FinishWorkoutDialog(completed = 6, incomplete = 3, onFinish = {}, onDiscard = {}, onDismiss = {})
            }
        }
        captureScreenRoboImage("src/test/screenshots/session_finish_dialog_light.png")
    }

    @Test
    fun finishDialog_nothingDone_dark() {
        compose.setContent {
            SpotterTheme(darkTheme = true, dynamicColor = false) {
                Surface(Modifier.fillMaxSize()) {}
                FinishWorkoutDialog(completed = 0, incomplete = 9, onFinish = {}, onDiscard = {}, onDismiss = {})
            }
        }
        captureScreenRoboImage("src/test/screenshots/session_finish_dialog_empty_dark.png")
    }

    @Test
    fun discardDialog_light() {
        compose.setContent {
            SpotterTheme(darkTheme = false, dynamicColor = false) {
                Surface(Modifier.fillMaxSize()) {}
                DiscardWorkoutDialog(onConfirm = {}, onDismiss = {})
            }
        }
        captureScreenRoboImage("src/test/screenshots/session_discard_dialog_light.png")
    }

    @Test
    fun plateSheet_light() = capture("plate_sheet_light", dark = false) {
        PlateSheetContent(PreviewData.exercise(), 102.5, WeightUnit.KG)
    }

    @Test
    fun plateSheet_notExact_dark() = capture("plate_sheet_dark", dark = true) {
        PlateSheetContent(PreviewData.exercise(), 61.0, WeightUnit.KG)
    }

    @Test
    fun stopwatchSheet_light() = capture("stopwatch_sheet_light", dark = false) {
        StopwatchSheetContent(displayMs = 83_400, running = true, onCountdown = {}, onReset = {}, onStartPause = {})
    }

    @Test
    fun swapSheet_light() = capture("swap_sheet_light", dark = false) {
        SwapSheetContent(
            state = SwapUiState(
                sessionExercise = bench,
                candidates = listOf(
                    PreviewData.exercise("incline-dumbbell-press", "Incline Dumbbell Press", equipment = listOf(com.viktorolsson.spotter.core.model.Equipment.DUMBBELL)),
                    PreviewData.exercise("machine-chest-press", "Machine Chest Press", equipment = listOf(com.viktorolsson.spotter.core.model.Equipment.MACHINE)),
                    PreviewData.exercise("push-up", "Push-Up", equipment = emptyList()),
                ),
            ),
            replaceInPlan = false, onReplaceInPlan = {}, onFilter = {}, onPick = { _, _ -> },
        )
    }

    @Test
    fun swapSheet_loading_dark() = capture("swap_sheet_loading_dark", dark = true) {
        SwapSheetContent(state = SwapUiState(sessionExercise = bench), replaceInPlan = true, onReplaceInPlan = {}, onFilter = {}, onPick = { _, _ -> })
    }

    @Test
    fun exerciseInfoSheet_light() = capture("exercise_info_sheet_light", dark = false) {
        ExerciseInfoSheetContent(
            PreviewData.exercise(instructions = "Set your feet flat and arch slightly.\nLower the bar to mid-chest with elbows at about 45°.\nPress back up without bouncing."),
        )
    }

    @Test
    fun summaryWithPrs_dark() = capture("summary_prs_dark", dark = true) {
        SummaryScreen(WorkoutSummary.of(PreviewData.session()), WeightUnit.KG, PreviewData.records, onDone = {})
    }

    @Test
    fun summary_light() = capture("summary_light", dark = false) {
        SummaryScreen(WorkoutSummary.of(PreviewData.session()), WeightUnit.KG, emptyList(), onDone = {})
    }
}
