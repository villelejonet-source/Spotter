package com.viktorolsson.spotter.feature.settings

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.viktorolsson.spotter.core.data.AppErrors
import com.viktorolsson.spotter.core.data.sync.Account
import com.viktorolsson.spotter.core.data.sync.Accounts
import com.viktorolsson.spotter.core.data.sync.AuthException
import com.viktorolsson.spotter.core.data.sync.AuthProblem
import com.viktorolsson.spotter.core.data.sync.CodePurpose
import com.viktorolsson.spotter.core.data.sync.SyncStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import com.viktorolsson.spotter.feature.settings.resources.Res
import com.viktorolsson.spotter.feature.settings.resources.account_error_code

/** The account screen's steps, against a fake account service. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AccountViewModelTest {
    private val accounts = FakeAccounts()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(newPassword: Boolean = false) =
        AccountViewModel(accounts, SavedStateHandle(mapOf("newPassword" to newPassword)), AppErrors())

    @Test
    fun opensOnTheEmailStep() {
        assertEquals(AccountStep.EMAIL, viewModel().state.value.step)
        assertEquals(AccountStep.NEW_PASSWORD, viewModel(newPassword = true).state.value.step)
    }

    @Test
    fun anEmailAndItsCodeSignIn() {
        val vm = viewModel()
        vm.setEmail("  you@example.com ")
        vm.sendCode()
        assertEquals(listOf("you@example.com"), accounts.codesSent)
        assertEquals(AccountStep.CODE, vm.state.value.step)
        assertEquals(CodePurpose.SIGN_IN, vm.state.value.codePurpose)

        vm.setCode("12 34-56")
        vm.verify()
        assertEquals(Triple("you@example.com", "123456", CodePurpose.SIGN_IN), accounts.verified.single())
        assertTrue(vm.state.value.done)
    }

    @Test
    fun aWrongCodeSaysSoAndStays() {
        accounts.verifyFails = AuthProblem.CODE_INVALID
        val vm = viewModel()
        vm.setEmail("you@example.com")
        vm.sendCode()
        vm.setCode("000000")
        vm.verify()
        assertEquals(AccountStep.CODE, vm.state.value.step)
        assertEquals(Res.string.account_error_code, vm.state.value.error)
        assertFalse(vm.state.value.done)
    }

    @Test
    fun aPasswordStillSignsIn() {
        val vm = viewModel()
        vm.setEmail("you@example.com")
        vm.goTo(AccountStep.PASSWORD)
        assertEquals("you@example.com", vm.state.value.email)
        vm.setPassword("long-password")
        vm.signIn()
        assertEquals(listOf("you@example.com" to "long-password"), accounts.passwordSignIns)
        assertTrue(vm.state.value.done)
    }

    @Test
    fun anUnconfirmedAccountGetsTheSignInCode() {
        accounts.signInFails = AuthProblem.EMAIL_NOT_CONFIRMED
        val vm = viewModel()
        vm.setEmail("you@example.com")
        vm.goTo(AccountStep.PASSWORD)
        vm.setPassword("long-password")
        vm.signIn()
        assertEquals(listOf("you@example.com"), accounts.codesSent)
        assertEquals(AccountStep.CODE, vm.state.value.step)
        assertTrue(vm.state.value.unconfirmed)
        assertEquals(CodePurpose.SIGN_IN, vm.state.value.codePurpose)
    }

    @Test
    fun resendingSendsTheSameKindOfEmail() {
        val vm = viewModel()
        vm.setEmail("you@example.com")
        vm.sendCode()
        vm.resend()
        assertEquals(2, accounts.codesSent.size)

        vm.goTo(AccountStep.FORGOT)
        vm.sendReset()
        vm.resend()
        assertEquals(2, accounts.resetsSent.size)
    }

    @Test
    fun anotherEmailGoesBackToWhereItWasTyped() {
        val vm = viewModel()
        vm.setEmail("you@example.com")
        vm.sendCode()
        vm.goTo(AccountStep.EMAIL)
        assertEquals(AccountStep.EMAIL, vm.state.value.step)
        assertEquals("", vm.state.value.code)
    }
}

private class FakeAccounts : Accounts {
    private val account = MutableStateFlow<Account?>(null)
    override val status = account.map { SyncStatus(available = true, account = it) }

    val codesSent = mutableListOf<String>()
    val resetsSent = mutableListOf<String>()
    val verified = mutableListOf<Triple<String, String, CodePurpose>>()
    val passwordSignIns = mutableListOf<Pair<String, String>>()
    var verifyFails: AuthProblem? = null
    var signInFails: AuthProblem? = null

    override suspend fun signIn(email: String, password: String) {
        signInFails?.let { throw AuthException(it) }
        passwordSignIns += email to password
        account.value = Account("user", email)
    }

    override suspend fun sendCode(email: String) {
        codesSent += email
    }

    override suspend fun sendPasswordReset(email: String) {
        resetsSent += email
    }

    override suspend fun verifyCode(email: String, code: String, purpose: CodePurpose) {
        verifyFails?.let { throw AuthException(it) }
        verified += Triple(email, code, purpose)
        account.value = Account("user", email)
    }

    override suspend fun updatePassword(password: String, nonce: String?) = Unit

    override fun finishPasswordRecovery() = Unit
}
