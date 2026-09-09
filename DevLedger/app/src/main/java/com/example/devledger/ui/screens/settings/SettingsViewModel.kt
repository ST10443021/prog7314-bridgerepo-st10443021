package com.devledger.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devledger.app.data.repository.AuthRepository
import com.devledger.app.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val language: String = "en",
    val biometricEnabled: Boolean = true,
    val wifiOnlySync: Boolean = false,
    val baseCurrency: String = "ZAR",
)

/**
 * Backs the Settings screen. Satisfies the Part 1 core requirement "The user must be
 * able to change their settings in the app" — language (multi-language support),
 * biometric lock, sync policy (offline mode) and base currency are all changeable
 * here and persisted via [SettingsRepository] / DataStore.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.language,
        settingsRepository.biometricEnabled,
        settingsRepository.wifiOnlySync,
        settingsRepository.baseCurrency,
    ) { language, biometric, wifiOnly, currency ->
        SettingsUiState(language, biometric, wifiOnly, currency)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    fun setLanguage(tag: String) = viewModelScope.launch { settingsRepository.setLanguage(tag) }
    fun setBiometricEnabled(enabled: Boolean) = viewModelScope.launch { settingsRepository.setBiometricEnabled(enabled) }
    fun setWifiOnlySync(enabled: Boolean) = viewModelScope.launch { settingsRepository.setWifiOnlySync(enabled) }
    fun setBaseCurrency(currency: String) = viewModelScope.launch { settingsRepository.setBaseCurrency(currency) }
    fun signOut() = viewModelScope.launch { authRepository.signOut() }
}
