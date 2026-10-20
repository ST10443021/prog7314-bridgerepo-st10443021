package com.devledger.app.ui.screens.settings

import com.devledger.app.data.local.PinStorage
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devledger.app.data.remote.dto.UserSettingsDto
import com.devledger.app.data.repository.AuthRepository
import com.devledger.app.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout


data class SettingsUiState(
    val language: String = "en",
    val biometricEnabled: Boolean = false,
    val isVerifyingBiometrics: Boolean = false,
    val wifiOnlySync: Boolean = false,
    val baseCurrency: String = "ZAR",
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isLoaded: Boolean = false,
    val hasChanges: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val appliedLanguage: String? = null,
    val appliedWifiOnlySync: Boolean? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val authRepository: AuthRepository,
    private val pinStorage: PinStorage,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState = _uiState.asStateFlow()

    private var biometricVerified = false
    private var loadedAccountId: String? = null

    init {
        loadSettings()
    }

    fun loadSettings() {
        if (_uiState.value.isSaving || _uiState.value.isLoading && _uiState.value.isLoaded ||
            _uiState.value.isVerifyingBiometrics) return

        _uiState.update {
            it.copy(
                isLoading = true,
                error = null,
                message = null,
                isLoaded = true,
            )
        }

        viewModelScope.launch {
            try {
                loadedAccountId = authRepository.currentUser.first()?.userId
                    ?: error("Sign in before loading settings.")
                val settings = withTimeout(10_000L) {
                    settingsRepository.loadFromApi()
                }

                if (_uiState.value.hasChanges) {
                    // The user changed something while the API request was running.
                    // Preserve those edits instead of replacing them with the older server state.
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isLoaded = true,
                            appliedLanguage = settings.language,
                            appliedWifiOnlySync = settings.syncOnWifiOnly,
                        )
                    }
                } else {
                    acceptServerSettings(settings)
                }
            } catch (timeout: TimeoutCancellationException) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Settings took too long to load. Check your connection and retry.",
                        isLoaded = true,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = errorMessage(error),
                        isLoaded = true,
                    )
                }
            }
        }
    }

    fun setLanguage(tag: String) = edit { it.copy(language = tag) }

    /** Enabling can only enter through the successful native prompt callback. */
    fun setBiometricEnabled(enabled: Boolean) {
        if (enabled) return
        if (_uiState.value.isVerifyingBiometrics) return
        biometricVerified = false
        edit { it.copy(biometricEnabled = false) }
    }

    fun beginBiometricVerification(): Boolean {
        val state = _uiState.value
        if (!state.isLoaded || state.isLoading || state.isSaving ||
            state.isVerifyingBiometrics) return false
        _uiState.update { it.copy(isVerifyingBiometrics = true, error = null, message = null) }
        return true
    }

    fun biometricVerificationSucceeded() {
        if (!_uiState.value.isVerifyingBiometrics) return
        biometricVerified = true
        _uiState.update { it.copy(isVerifyingBiometrics = false) }
        edit { it.copy(biometricEnabled = true) }
    }

    fun biometricVerificationFailed(message: String) {
        if (!_uiState.value.isVerifyingBiometrics) return
        _uiState.update { it.copy(isVerifyingBiometrics = false, error = message) }
    }

    fun biometricScanNotRecognised(message: String) {
        if (_uiState.value.isVerifyingBiometrics) {
            _uiState.update { it.copy(error = message) }
        }
    }

    fun cancelBiometricVerification() {
        _uiState.update { it.copy(isVerifyingBiometrics = false) }
    }

    fun setWifiOnlySync(enabled: Boolean) =
        edit { it.copy(wifiOnlySync = enabled) }

    fun setBaseCurrency(currency: String) =
        edit { it.copy(baseCurrency = currency) }

    private fun edit(
        change: (SettingsUiState) -> SettingsUiState,
    ) {
        val current = _uiState.value

        // Freeze edits while loading, saving, or waiting for native verification.
        if (current.isSaving || current.isLoading || current.isVerifyingBiometrics) return

        _uiState.value = change(current).copy(
            hasChanges = true,
            error = null,
            message = null,
        )
    }

    fun saveSettings() {
        val current = _uiState.value

        if (!current.isLoaded ||
            current.isLoading ||
            current.isSaving ||
            current.isVerifyingBiometrics ||
            !current.hasChanges
        ) return

        _uiState.update {
            it.copy(
                isSaving = true,
                error = null,
                message = null,
                isLoaded = true,
            )
        }

        viewModelScope.launch {
            try {
                val user = authRepository.currentUser.first()
                    ?: error("Sign in before saving settings.")
                check(user.userId == loadedAccountId) { "The account changed. Reopen Settings." }
                check(pinStorage.hasPin(user.email)) { "Configure a fallback PIN first." }
                val saved = withTimeout(10_000L) {
                    settingsRepository.saveToApi(
                        language = current.language,
                        biometricEnabled = current.biometricEnabled,
                        wifiOnlySync = current.wifiOnlySync,
                        baseCurrency = current.baseCurrency,
                        biometricVerified = biometricVerified,
                    )
                }

                acceptServerSettings(
                    settings = saved,
                    message = "Settings saved.",
                )
            } catch (timeout: TimeoutCancellationException) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        error = "Saving took too long. Check your connection and retry.",
                        isLoaded = true,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        error = errorMessage(error),
                        isLoaded = true,
                    )
                }
            }
        }
    }

    private fun acceptServerSettings(
        settings: UserSettingsDto,
        message: String? = null,
    ) {
        biometricVerified = false
        _uiState.value = SettingsUiState(
            language = settings.language,
            biometricEnabled = settings.biometricEnabled,
            wifiOnlySync = settings.syncOnWifiOnly,
            baseCurrency = settings.baseCurrency,
            isLoading = false,
            isLoaded = true,
            message = message,
            appliedLanguage = settings.language,
            appliedWifiOnlySync = settings.syncOnWifiOnly,
        )
    }

    private fun errorMessage(error: Exception): String = when (error) {
        is HttpException -> when (error.code()) {
            401 -> "Your session has expired. Sign out and sign in again."
            403 -> "You do not have permission to access these settings."
            404 -> "Settings were not found. Check the API deployment and account."
            else -> "The API request failed (${error.code()}). Please retry."
        }
        is IOException -> "Could not reach the API. Check your connection and retry."
        else -> "Could not complete the settings request. Please retry."
    }

    fun signOut(onComplete: () -> Unit = {}) = viewModelScope.launch {
        authRepository.signOut()
        onComplete()
    }
}
