package com.devledger.app.ui.screens.auth

import android.util.Patterns
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

data class RegisterUiState(
    val fullName: String = "",
    val email: String = "",
    val password: String = "",

    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isRegistered: Boolean = false,

    // Validation
    val isFullNameValid: Boolean = false,
    val isEmailValid: Boolean = false,
    val isPasswordValid: Boolean = false,

    val passwordStrength: Int = 0,

    val fullNameError: String? = null,
    val emailError: String? = null,
    val passwordError: String? = null,
)

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    fun onFullNameChange(value: String) {
        val isValid = value.trim().length >= 2

        _uiState.value = _uiState.value.copy(
            fullName = value,
            isFullNameValid = isValid,
            fullNameError = when {
                value.isBlank() -> null
                !isValid -> "Full name must contain at least 2 characters."
                else -> null
            },
            errorMessage = null
        )
    }

    fun onEmailChange(value: String) {
        val isValid = Patterns.EMAIL_ADDRESS.matcher(value.trim()).matches()

        _uiState.value = _uiState.value.copy(
            email = value,
            isEmailValid = isValid,
            emailError = when {
                value.isBlank() -> null
                !isValid -> "Enter a valid email address."
                else -> null
            },
            errorMessage = null
        )
    }

    fun onPasswordChange(value: String) {
        val strength = calculatePasswordStrength(value)
        val isValid = value.length >= 8

        _uiState.value = _uiState.value.copy(
            password = value,
            passwordStrength = strength,
            isPasswordValid = isValid,
            passwordError = when {
                value.isEmpty() -> null
                !isValid -> "Password must contain at least 8 characters."
                else -> null
            },
            errorMessage = null
        )
    }

    private fun calculatePasswordStrength(password: String): Int {
        if (password.isEmpty()) return 0

        var score = 0

        if (password.length >= 8) score++
        if (password.any { it.isDigit() }) score++
        if (password.any { it.isUpperCase() }) score++
        if (
            password.any { it.isLowerCase() } &&
            password.any { !it.isLetterOrDigit() }
        ) {
            score++
        }

        return score.coerceIn(1, 4)
    }

    fun register() {
        val state = _uiState.value

        val fullNameError =
            if (state.fullName.isBlank()) "Full name is required."
            else if (!state.isFullNameValid) "Full name must contain at least 2 characters."
            else null

        val emailError =
            if (state.email.isBlank()) "Email address is required."
            else if (!state.isEmailValid) "Enter a valid email address."
            else null

        val passwordError =
            if (state.password.isBlank()) "Password is required."
            else if (!state.isPasswordValid) "Password must contain at least 8 characters."
            else null

        val hasErrors =
            fullNameError != null ||
                    emailError != null ||
                    passwordError != null

        if (hasErrors) {
            _uiState.value = state.copy(
                fullNameError = fullNameError,
                emailError = emailError,
                passwordError = passwordError,
                errorMessage = "Please correct the highlighted fields."
            )
            return
        }

        _uiState.value = state.copy(
            isLoading = true,
            errorMessage = null
        )

        viewModelScope.launch {
            val result = authRepository.register(
                state.email.trim(),
                state.password,
                state.fullName.trim()
            )

            when (result) {
                is DevLedgerResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isRegistered = true
                    )
                }

                is DevLedgerResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.message
                    )
                }
            }
        }
    }
}