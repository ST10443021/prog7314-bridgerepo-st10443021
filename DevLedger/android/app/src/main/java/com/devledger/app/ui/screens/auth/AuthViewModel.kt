package com.devledger.app.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devledger.app.data.repository.AuthRepository
import com.devledger.app.util.DevLedgerResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.util.Patterns

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isAuthenticated: Boolean = false,
    val emailError: String? = null,
    val passwordError: String? = null,
)

/**
 * Drives the Authentication screen. Supports Google/GitHub SSO (Part 1 core
 * requirement: "register and log in ... using single sign-on") as well as an email
 * fallback, delegating all network/session handling to [AuthRepository].
 *
 * Google and GitHub use different token types under the hood — Google hands the app
 * an ID token directly; GitHub hands back only an authorization code, which the API
 * (holding the client secret) exchanges server-side — so [performSso] accepts either,
 * exactly one of which is non-null depending on [provider].
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) {
        val state = _uiState.value
        if (state.isLoading || state.isAuthenticated) return

        _uiState.value = state.copy(
            email = value,
            emailError = null,
            errorMessage = null,
        )
    }

    fun onPasswordChange(value: String) {
        val state = _uiState.value
        if (state.isLoading || state.isAuthenticated) return

        _uiState.value = state.copy(
            password = value,
            passwordError = null,
            errorMessage = null,
        )
    }
    fun signInWithGoogle(idToken: String) = performSso("GOOGLE", idToken = idToken)
    fun signInWithGitHub(code: String) = performSso("GITHUB", code = code)

    /** Called when the GitHub redirect itself failed (e.g. the user cancelled, or GitHub
     *  returned an `error` query param) — before we ever get as far as calling the API. */
    fun onGitHubOAuthFailure(reason: String) {
        _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "GitHub sign-in failed: $reason")
    }

    private fun performSso(provider: String, idToken: String? = null, code: String? = null) {
        if (_uiState.value.isLoading || _uiState.value.isAuthenticated) return
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            when (val result = authRepository.signInWithSso(provider, idToken = idToken, code = code)) {
                is DevLedgerResult.Success -> _uiState.value = _uiState.value.copy(isLoading = false, isAuthenticated = true)
                is DevLedgerResult.Error -> _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = result.message)
            }
        }
    }

    fun signInWithEmail() {
        val state = _uiState.value
        if (state.isLoading || state.isAuthenticated) return

        val email = state.email.trim()

        val emailError = when {
            email.isBlank() -> "Enter your email address."
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() ->
                "Enter a valid email address."
            else -> null
        }

        val passwordError = if (state.password.isEmpty()) {
            "Enter your password."
        } else {
            null
        }

        if (emailError != null || passwordError != null) {
            _uiState.value = state.copy(
                email = email,
                emailError = emailError,
                passwordError = passwordError,
                errorMessage = null,
            )
            return
        }

        _uiState.value = state.copy(
            email = email,
            isLoading = true,
            emailError = null,
            passwordError = null,
            errorMessage = null,
        )

        viewModelScope.launch {
            when (
                val result = authRepository.signInWithEmail(
                    email,
                    state.password,
                )
            ) {
                is DevLedgerResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isAuthenticated = true,
                        password = "",
                    )
                }

                is DevLedgerResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.message,
                    )
                }
            }
        }
    }
}