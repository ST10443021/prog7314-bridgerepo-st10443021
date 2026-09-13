package com.devledger.app.ui.screens.settings

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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException

data class SettingsUiState(
    val language: String = "en",
    val biometricEnabled: Boolean = true,
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
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    fun loadSettings() {
        if (_uiState.value.isSaving) return

        _uiState.update {
            it.copy(isLoading = true, error = null, message = null)
        }

        viewModelScope.launch {
            try {
                acceptServerSettings(settingsRepository.loadFromApi())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = errorMessage(error),
                    )
                }
            }
        }
    }

    fun setLanguage(tag: String) = edit { it.copy(language = tag) }

    fun setBiometricEnabled(enabled: Boolean) =
        edit { it.copy(biometricEnabled = enabled) }

    fun setWifiOnlySync(enabled: Boolean) =
        edit { it.copy(wifiOnlySync = enabled) }

    fun setBaseCurrency(currency: String) =
        edit { it.copy(baseCurrency = currency) }

    private fun edit(change: (SettingsUiState) -> SettingsUiState) {
        val current = _uiState.value
        if (!current.isLoaded || current.isLoading || current.isSaving) return

        _uiState.value = change(current).copy(
            hasChanges = true,
            error = null,
            message = null,
        )
    }

    fun saveSettings() {
        val current = _uiState.value
        if (!current.isLoaded || current.isLoading ||
            current.isSaving || !current.hasChanges
        ) return

        _uiState.update {
            it.copy(isSaving = true, error = null, message = null)
        }

        viewModelScope.launch {
            try {
                val saved = settingsRepository.saveToApi(
                    language = current.language,
                    biometricEnabled = current.biometricEnabled,
                    wifiOnlySync = current.wifiOnlySync,
                    baseCurrency = current.baseCurrency,
                )
                acceptServerSettings(saved, "Settings saved.")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // Keep the user's edits so they can retry saving.
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        error = errorMessage(error),
                    )
                }
            }
        }
    }

    private fun acceptServerSettings(
        settings: UserSettingsDto,
        message: String? = null,
    ) {
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

    fun signOut() = viewModelScope.launch {
        authRepository.signOut()
    }
}