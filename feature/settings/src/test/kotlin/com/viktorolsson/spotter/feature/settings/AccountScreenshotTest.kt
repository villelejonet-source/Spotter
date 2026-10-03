package com.viktorolsson.spotter.feature.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.viktorolsson.spotter.core.ui.theme.SpotterTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class AccountScreenshotTest {
    private val actions = AccountActions({}, {}, {}, {}, {}, {})

    private fun capture(name: String, state: AccountUiState) = captureRoboImage("src/test/screenshots/$name.png") {
        SpotterTheme(darkTheme = false, dynamicColor = false) {
            AccountScreen(state = state, newAccount = true, onBack = {}, actions = actions)
        }
    }

    @Test
    fun account_check_email() = capture("account_check_email", AccountUiState(email = "you@example.com", codeSent = true))

    @Test
    fun account_enter_code() =
        capture("account_enter_code", AccountUiState(email = "you@example.com", codeSent = true, showCode = true))
}
