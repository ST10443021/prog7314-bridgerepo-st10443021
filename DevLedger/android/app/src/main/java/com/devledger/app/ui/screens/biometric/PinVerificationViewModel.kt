package com.devledger.app.ui.screens.biometric

import com.devledger.app.util.localization.UiText

import com.devledger.app.R

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devledger.app.data.local.PinLockoutPolicy
import com.devledger.app.data.local.PinStorage
import com.devledger.app.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

// Reference scope: UI and state-holder separation. (Google Developers, n.d.a; Google Developers, n.d.f). See REFERENCES-CODE.md for full entries.

data class PinVerificationUiState(
    val pin: String = "",
    val errorMessage: UiText? = null,
    val isVerifying: Boolean = false,
    val isVerified: Boolean = false,
)

@HiltViewModel
class PinVerificationViewModel @Inject constructor(
    private val pinStorage: PinStorage,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PinVerificationUiState())
    val uiState: StateFlow<PinVerificationUiState> = _uiState.asStateFlow()

    fun onPinChange(value: String) {
        val filteredPin = value
            .filter { it.isDigit() }
            .take(4)

        _uiState.value = _uiState.value.copy(
            pin = filteredPin,
            errorMessage = null
        )
    }

    private fun lockedMessage(millis: Long): UiText =
        UiText.Resource(R.string.pin_locked_out, listOf(((millis + 999) / 1000).toInt()))

    fun verifyPin() {
        val state = _uiState.value

        if (state.pin.length != 4) {
            _uiState.value = state.copy(
                errorMessage = UiText.Resource(R.string.lang_enter_your_4_digit_pin)
            )
            return
        }

        _uiState.value = state.copy(
            isVerifying = true,
            errorMessage = null
        )

        viewModelScope.launch {
            val email = authRepository.currentUser.first()?.email

            if (!pinStorage.hasPin(email)) {
                _uiState.value = _uiState.value.copy(
                    isVerifying = false,
                    errorMessage = UiText.Resource(R.string.lang_no_pin_has_been_configured_for_this_account)
                )
                return@launch
            }

            // Brute-force protection: refuse to even check while a temporary lock is active.
            val lockedFor = pinStorage.lockoutRemainingMillis(email)
            if (lockedFor > 0) {
                _uiState.value = _uiState.value.copy(
                    pin = "",
                    isVerifying = false,
                    errorMessage = lockedMessage(lockedFor)
                )
                return@launch
            }

            val matches = withContext(Dispatchers.Default) {
                pinStorage.verifyPin(state.pin, email)
            }

            if (matches) {
                pinStorage.resetPinAttempts(email)
                _uiState.value = _uiState.value.copy(
                    isVerifying = false,
                    isVerified = true,
                    errorMessage = null
                )
            } else {
                val newLock = pinStorage.recordFailedPin(email)
                val message = if (newLock > 0) {
                    lockedMessage(newLock)
                } else {
                    val left = PinLockoutPolicy.attemptsLeft(pinStorage.failedAttempts(email))
                    UiText.Resource(R.string.pin_attempts_left, listOf(left))
                }
                _uiState.value = _uiState.value.copy(
                    pin = "",
                    isVerifying = false,
                    errorMessage = message
                )
            }
        }
    }
}
