package com.viktorolsson.spotter.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
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
import com.viktorolsson.spotter.core.ui.component.ConfirmDialog
import com.viktorolsson.spotter.core.ui.component.EmptyState
import com.viktorolsson.spotter.core.ui.component.ErrorState
import com.viktorolsson.spotter.core.ui.component.LoadingState
import com.viktorolsson.spotter.core.ui.component.WorkoutMiniBar
import com.viktorolsson.spotter.core.ui.theme.SpotterTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

/** The shared screen states and small components every feature reuses. */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class StatesScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun capture(name: String, dark: Boolean, fontScale: Float = 1f, content: @Composable () -> Unit) =
        captureRoboImage(
            "src/test/screenshots/$name.png",
            roborazziComposeOptions = RoborazziComposeOptions { fontScale(fontScale) },
        ) {
            SpotterTheme(darkTheme = dark, dynamicColor = false) { Surface(Modifier.fillMaxSize()) { content() } }
        }

    @Composable
    private fun empty() = EmptyState(
        icon = Icons.Rounded.CalendarMonth,
        title = "No workouts yet",
        body = "Finished workouts show up here, with every set, by date and by exercise.",
        modifier = Modifier.fillMaxSize(),
        actionLabel = "Build my plan",
        onAction = {},
    )

    @Test
    fun emptyState_light() = capture("empty_state_light", dark = false) { empty() }

    @Test
    fun emptyState_dark() = capture("empty_state_dark", dark = true) { empty() }

    @Test
    fun emptyState_largeFont() = capture("empty_state_font_1_6", dark = false, fontScale = 1.6f) { empty() }

    @Test
    fun loadingState_light() = capture("loading_state_light", dark = false) {
        Column(Modifier.fillMaxSize()) {
            LoadingState(Modifier.fillMaxSize())
        }
    }

    @Test
    fun loadingState_withText_dark() = capture("loading_state_text_dark", dark = true) {
        LoadingState(Modifier.fillMaxSize(), text = "Looking for your backup…")
    }

    @Test
    fun errorState_light() = capture("error_state_light", dark = false) { ErrorState(Modifier.fillMaxSize(), onRetry = {}) }

    @Test
    fun errorState_dark() = capture("error_state_dark", dark = true) {
        ErrorState(Modifier.fillMaxSize(), title = "Workout not found", body = "It may have been deleted on another phone.")
    }

    @Test
    fun miniBar_light() = capture("mini_bar_light", dark = false) {
        Column { WorkoutMiniBar(Duration.ofMinutes(32).plusSeconds(10), onClick = {}) }
    }

    @Test
    fun miniBar_dark() = capture("mini_bar_dark", dark = true) {
        Column { WorkoutMiniBar(Duration.ofMinutes(32).plusSeconds(10), onClick = {}) }
    }

    @Test
    fun confirmDialog_destructive_light() {
        compose.setContent {
            SpotterTheme(darkTheme = false, dynamicColor = false) {
                Surface(Modifier.fillMaxSize()) {}
                ConfirmDialog(
                    title = "Delete your account?",
                    text = "This deletes your account and everything backed up in the cloud. The data on this phone stays.",
                    confirmText = "Delete",
                    onConfirm = {},
                    onDismiss = {},
                    destructive = true,
                )
            }
        }
        captureScreenRoboImage("src/test/screenshots/confirm_dialog_destructive_light.png")
    }

    @Test
    fun confirmDialog_dark() {
        compose.setContent {
            SpotterTheme(darkTheme = true, dynamicColor = false) {
                Surface(Modifier.fillMaxSize()) {}
                ConfirmDialog(
                    title = "Rebuild your plan?",
                    text = "You'll retake the questionnaire and get a new plan in place of this one. Your workout history is kept.",
                    confirmText = "Rebuild plan",
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }
        captureScreenRoboImage("src/test/screenshots/confirm_dialog_dark.png")
    }
}
