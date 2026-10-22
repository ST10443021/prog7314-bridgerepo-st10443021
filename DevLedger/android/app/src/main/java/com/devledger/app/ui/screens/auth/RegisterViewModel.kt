package com.devledger.app.ui.screens.auth

import com.devledger.app.util.localization.UiText

import com.devledger.app.R

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
    val errorMessage: UiText? = null,
    val isRegistered: Boolean = false,

    // Validation
    val isFullNameValid: Boolean = false,
    val isEmailValid: Boolean = false,
    val isPasswordValid: Boolean = false,

    val passwordStrength: Int = 0,

    val fullNameError: UiText? = null,
    val emailError: UiText? = null,
    val passwordError: UiText? = null,
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
                !isValid -> UiText.Resource(R.string.lang_full_name_must_contain_at_least_2_characters)
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
                !isValid -> UiText.Resource(R.string.lang_enter_a_valid_email_address)
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
                !isValid -> UiText.Resource(R.string.lang_password_must_contain_at_least_8_characters)
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
            if (state.fullName.isBlank()) UiText.Resource(R.string.lang_full_name_is_required)
            else if (!state.isFullNameValid) UiText.Resource(R.string.lang_full_name_must_contain_at_least_2_characters)
            else null

        val emailError =
            if (state.email.isBlank()) UiText.Resource(R.string.lang_email_address_is_required)
            else if (!state.isEmailValid) UiText.Resource(R.string.lang_enter_a_valid_email_address)
            else null

        val passwordError =
            if (state.password.isBlank()) UiText.Resource(R.string.lang_password_is_required)
            else if (!state.isPasswordValid) UiText.Resource(R.string.lang_password_must_contain_at_least_8_characters)
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
                errorMessage = UiText.Resource(R.string.lang_please_correct_the_highlighted_fields)
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
                        errorMessage = UiText.Resource(R.string.lang_registration_failed_check_your_details_and_try_again)
                    )
                }
            }
        }
    }
}
