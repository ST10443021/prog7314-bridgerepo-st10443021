package com.devledger.app.viewmodel

import app.cash.turbine.test
import com.devledger.app.data.local.entity.UserEntity
import com.devledger.app.data.repository.AuthRepository
import com.devledger.app.ui.screens.auth.AuthViewModel
import com.devledger.app.util.DevLedgerResult
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Verifies [AuthViewModel]'s SSO and email sign-in flows, including the "Email and
 * password are required" client-side validation before any network call is made.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var authRepository: AuthRepository
    private lateinit var viewModel: AuthViewModel

    private val fakeUser = UserEntity(userId = "u1", email = "dev@devledger.app", fullName = "Test Dev", ssoProvider = "GOOGLE")

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
    fun `signInWithGoogle sets isAuthenticated true on success`() = runTest {
        coEvery { authRepository.signInWithSso("GOOGLE", any()) } returns DevLedgerResult.Success(fakeUser)

        viewModel.uiState.test {
            assertThat(awaitItem().isAuthenticated).isFalse()
            viewModel.signInWithGoogle("token-123")
            dispatcher.scheduler.advanceUntilIdle()
            // Loading, then authenticated
            val loading = awaitItem()
            assertThat(loading.isLoading).isTrue()
            val done = awaitItem()
            assertThat(done.isAuthenticated).isTrue()
            assertThat(done.isLoading).isFalse()
        }
    }

    @Test
    fun `signInWithGoogle surfaces the repository error message on failure`() = runTest {
        coEvery { authRepository.signInWithSso("GOOGLE", any()) } returns
            DevLedgerResult.Error("SSO sign-in failed: HTTP 401")

        viewModel.uiState.test {
            awaitItem() // initial
            viewModel.signInWithGoogle("bad-token")
            dispatcher.scheduler.advanceUntilIdle()
            awaitItem() // loading
            val errorState = awaitItem()
            assertThat(errorState.errorMessage).isEqualTo("SSO sign-in failed: HTTP 401")
            assertThat(errorState.isAuthenticated).isFalse()
        }
    }

    @Test
    fun `signInWithEmail rejects blank credentials without calling the repository`() = runTest {
        viewModel.onEmailChange("")
        viewModel.onPasswordChange("")

        viewModel.uiState.test {
            awaitItem()

            viewModel.signInWithEmail()

            val result = awaitItem()

            assertThat(result.emailError)
                .isEqualTo("Enter your email address.")

            assertThat(result.passwordError)
                .isEqualTo("Enter your password.")

            assertThat(result.errorMessage).isNull()
            assertThat(result.isLoading).isFalse()
            assertThat(result.isAuthenticated).isFalse()
        }
    }
}
