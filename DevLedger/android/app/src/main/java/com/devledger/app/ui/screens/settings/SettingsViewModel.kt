package com.devledger.app.ui.screens.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devledger.app.data.remote.dto.UserSettingsDto
import com.devledger.app.data.repository.AuthRepository
import com.devledger.app.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
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
            it.copy(
                isLoading = true,
                error = null,
                message = null,
            )
        }

        viewModelScope.launch {
            try {
                Log.d("SettingsVM", "Loading settings from API...")
                val settings = settingsRepository.loadFromApi()
                Log.d("SettingsVM", "Settings loaded successfully")

                if (_uiState.value.hasChanges) {
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
            } catch (error: Exception) {
                Log.e("SettingsVM", "Error loading settings", error)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = errorMessage(error),
                        isLoaded = false,
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

    private fun edit(
        change: (SettingsUiState) -> SettingsUiState,
    ) {
        val current = _uiState.value

        if (current.isSaving) return

        _uiState.value = change(current).copy(
            hasChanges = true,
            error = null,
            message = null,
        )
    }

    fun saveSettings() {
        val current = _uiState.value

        if (!current.isLoaded || current.isLoading || current.isSaving || !current.hasChanges) return

        _uiState.update {
            it.copy(
                isSaving = true,
                error = null,
                message = null,
            )
        }

        viewModelScope.launch {
            try {
                Log.d("SettingsVM", "Saving settings to API...")
                val saved = settingsRepository.saveToApi(
                    language = current.language,
                    biometricEnabled = current.biometricEnabled,
                    wifiOnlySync = current.wifiOnlySync,
                    baseCurrency = current.baseCurrency,
                )
                Log.d("SettingsVM", "Settings saved successfully")

                acceptServerSettings(
                    settings = saved,
                    message = "Settings saved.",
                )
            } catch (error: Exception) {
                Log.e("SettingsVM", "Error saving settings", error)
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
            401 -> "Your session has expired. Please Sign Out and Sign In again to continue."
            403 -> "You do not have permission to access these settings."
            404 -> "Settings were not found on the server."
            else -> "The API request failed (${error.code()})."
        }
        is IOException -> "Could not reach the API. Please check your internet connection."
        else -> error.message ?: "An unexpected error occurred. Please try again."
    }

    fun signOut() = viewModelScope.launch {
        authRepository.signOut()
    }
}
