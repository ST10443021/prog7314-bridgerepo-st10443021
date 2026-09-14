package com.devledger.app.ui.screens.biometric

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

data class PinVerificationUiState(
    val pin: String = "",
    val errorMessage: String? = null,
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

    fun verifyPin() {
        val state = _uiState.value

        if (state.pin.length != 4) {
            _uiState.value = state.copy(
                errorMessage = "Enter your 4-digit PIN."
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
                    errorMessage = "No PIN has been configured for this account."
                )
                return@launch
            }

            val matches = withContext(Dispatchers.Default) {
                pinStorage.verifyPin(state.pin, email)
            }

            if (matches) {
                _uiState.value = _uiState.value.copy(
                    isVerifying = false,
                    isVerified = true,
                    errorMessage = null
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    pin = "",
                    isVerifying = false,
                    errorMessage = "Incorrect PIN. Please try again."
                )
            }
        }
    }
}