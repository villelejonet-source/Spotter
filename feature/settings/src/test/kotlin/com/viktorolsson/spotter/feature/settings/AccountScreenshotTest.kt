package com.viktorolsson.spotter.feature.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.viktorolsson.spotter.core.data.sync.CodePurpose
import com.viktorolsson.spotter.core.ui.theme.SpotterTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class AccountScreenshotTest {
    private fun capture(name: String, state: AccountUiState, dark: Boolean = false) = captureRoboImage("src/test/screenshots/$name.png") {
        SpotterTheme(darkTheme = dark, dynamicColor = false) {
            AccountScreen(state = state, onBack = {}, actions = AccountActions())
        }
    }

    @Test
    fun account_sign_in() = capture("account_sign_in", AccountUiState(email = "you@example.com", password = "secret-pass"))

    @Test
    fun account_sign_in_dark() = capture("account_sign_in_dark", AccountUiState(email = "you@example.com", password = "secret-pass"), dark = true)

    @Test
    fun account_sign_in_error() = capture(
        "account_sign_in_error",
        AccountUiState(email = "you@example.com", password = "secret-pass", error = R.string.account_error_wrong_credentials),
    )

    @Test
    fun account_sign_in_busy() = capture("account_sign_in_busy", AccountUiState(email = "you@example.com", password = "secret-pass", busy = true))

    @Test
    fun account_sign_up() = capture(
        "account_sign_up",
        AccountUiState(step = AccountStep.SIGN_UP, email = "you@example.com", password = "short", repeat = ""),
    )

    @Test
    fun account_forgot() = capture("account_forgot", AccountUiState(step = AccountStep.FORGOT, email = "you@example.com"))

    @Test
    fun account_confirm_code() = capture(
        "account_confirm_code",
        AccountUiState(step = AccountStep.CODE, codePurpose = CodePurpose.SIGN_UP, email = "you@example.com", code = "123456"),
    )

    @Test
    fun account_confirm_code_dark() = capture(
        "account_confirm_code_dark",
        AccountUiState(step = AccountStep.CODE, codePurpose = CodePurpose.RECOVERY, email = "you@example.com", notice = R.string.account_resent),
        dark = true,
    )

    @Test
    fun account_new_password() = capture(
        "account_new_password",
        AccountUiState(step = AccountStep.NEW_PASSWORD, needsNonce = true),
    )

    @Test
    fun account_password_saved() = capture("account_password_saved", AccountUiState(step = AccountStep.PASSWORD_SAVED))
}
