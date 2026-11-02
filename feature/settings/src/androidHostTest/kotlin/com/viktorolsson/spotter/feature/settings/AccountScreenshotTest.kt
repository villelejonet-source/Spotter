package com.viktorolsson.spotter.feature.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.viktorolsson.spotter.core.data.sync.CodePurpose
import com.viktorolsson.spotter.core.ui.theme.SpotterTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import com.viktorolsson.spotter.feature.settings.resources.Res
import com.viktorolsson.spotter.feature.settings.resources.account_error_wrong_credentials
import com.viktorolsson.spotter.feature.settings.resources.account_resent

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class AccountScreenshotTest {
    // Unused directly, but with it captureRoboImage doesn't wait for infinite animations
    // (loading spinners) to settle, which otherwise takes minutes per test.
    @get:Rule
    val compose = createComposeRule()

    private fun capture(name: String, state: AccountUiState, dark: Boolean = false) = captureRoboImage("src/test/screenshots/$name.png") {
        SpotterTheme(darkTheme = dark, dynamicColor = false) {
            AccountScreen(state = state, onBack = {}, actions = AccountActions())
        }
    }

    @Test
    fun account_email() = capture("account_email", AccountUiState(email = "you@example.com"))

    @Test
    fun account_email_dark() = capture("account_email_dark", AccountUiState(email = "you@example.com"), dark = true)

    @Test
    fun account_email_busy() = capture("account_email_busy", AccountUiState(email = "you@example.com", busy = true))

    @Test
    fun account_password() = capture("account_password", AccountUiState(step = AccountStep.PASSWORD, email = "you@example.com", password = "secret-pass"))

    @Test
    fun account_password_error() = capture(
        "account_password_error",
        AccountUiState(step = AccountStep.PASSWORD, email = "you@example.com", password = "secret-pass", error = Res.string.account_error_wrong_credentials),
    )

    @Test
    fun account_forgot() = capture("account_forgot", AccountUiState(step = AccountStep.FORGOT, email = "you@example.com"))

    @Test
    fun account_confirm_code() = capture(
        "account_confirm_code",
        AccountUiState(step = AccountStep.CODE, codePurpose = CodePurpose.SIGN_IN, email = "you@example.com", code = "123456"),
    )

    @Test
    fun account_confirm_code_dark() = capture(
        "account_confirm_code_dark",
        AccountUiState(step = AccountStep.CODE, codePurpose = CodePurpose.RECOVERY, email = "you@example.com", notice = Res.string.account_resent),
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
