package com.devledger.app.viewmodel

import com.devledger.app.util.localization.UiText

import com.devledger.app.R

import com.devledger.app.data.local.entity.UserEntity
import com.devledger.app.data.repository.AuthRepository
import com.devledger.app.ui.screens.auth.AuthViewModel
import com.devledger.app.util.DevLedgerResult
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Exercises the current provider-specific SSO loading flow and email validation.
 * A deferred repository response keeps loading observable until the test explicitly
 * supplies the result; assertions do not depend on transient StateFlow emissions.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var authRepository: AuthRepository
    private lateinit var viewModel: AuthViewModel

    private val fakeUser = UserEntity(
        userId = "u1",
        email = "dev@devledger.app",
        fullName = "Test Dev",
        ssoProvider = "GOOGLE",
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        authRepository = mockk()
        viewModel = AuthViewModel(authRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `signInWithGoogle sets isAuthenticated true on success`() = runTest(dispatcher) {
        val response = CompletableDeferred<DevLedgerResult<UserEntity>>()
        coEvery {
            authRepository.signInWithSso("GOOGLE", idToken = "token-123", code = null)
        } coAnswers { response.await() }

        assertThat(viewModel.uiState.value.isAuthenticated).isFalse()
        // AuthScreen starts provider loading before it launches the Google UI.
        viewModel.onGoogleSignInClicked()
        viewModel.signInWithGoogle("token-123")
        runCurrent()

        assertThat(viewModel.uiState.value.isGoogleLoading).isTrue()
        assertThat(viewModel.uiState.value.isAuthenticated).isFalse()
        coVerify(exactly = 1) {
            authRepository.signInWithSso("GOOGLE", idToken = "token-123", code = null)
        }

        response.complete(DevLedgerResult.Success(fakeUser))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isAuthenticated).isTrue()
        assertThat(state.isGoogleLoading).isFalse()
        assertThat(state.isGitHubLoading).isFalse()
        assertThat(state.isLoading).isFalse()
        assertThat(state.errorMessage).isNull()
    }

    @Test
    fun `signInWithGoogle surfaces the repository error message on failure`() = runTest(dispatcher) {
        val response = CompletableDeferred<DevLedgerResult<UserEntity>>()
        coEvery {
            authRepository.signInWithSso("GOOGLE", idToken = "bad-token", code = null)
        } coAnswers { response.await() }

        viewModel.onGoogleSignInClicked()
        viewModel.signInWithGoogle("bad-token")
        runCurrent()
        assertThat(viewModel.uiState.value.isGoogleLoading).isTrue()
        assertThat(viewModel.uiState.value.isAuthenticated).isFalse()

        response.complete(DevLedgerResult.Error("SSO sign-in failed: HTTP 401"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.errorMessage).isEqualTo(UiText.Resource(R.string.auth_error_generic))
        assertThat(state.isAuthenticated).isFalse()
        assertThat(state.isGoogleLoading).isFalse()
        assertThat(state.isGitHubLoading).isFalse()
        assertThat(state.isLoading).isFalse()
        coVerify(exactly = 1) {
            authRepository.signInWithSso("GOOGLE", idToken = "bad-token", code = null)
        }
    }

    @Test
    fun `signInWithEmail rejects blank credentials without calling the repository`() = runTest(dispatcher) {
        viewModel.onEmailChange("")
        viewModel.onPasswordChange("")
        viewModel.signInWithEmail()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.emailError).isEqualTo(UiText.Resource(R.string.lang_enter_your_email_address))
        assertThat(state.passwordError).isEqualTo(UiText.Resource(R.string.lang_enter_your_password))
        assertThat(state.errorMessage).isNull()
        assertThat(state.isLoading).isFalse()
        assertThat(state.isAuthenticated).isFalse()
        coVerify(exactly = 0) { authRepository.signInWithEmail(any(), any()) }
    }
}
