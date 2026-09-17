package com.devledger.app.data.repository

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import com.devledger.app.data.remote.DevLedgerApiService
import com.devledger.app.data.remote.dto.UserSettingsDto
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException

private val Context.dataStore by preferencesDataStore(name = "devledger_settings")

/**
 * Stores local preferences in DataStore and provides explicit API load/save
 * operations through /api/settings.
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val api: DevLedgerApiService,
)
{

    private val store = context.dataStore

    private val remoteMutex = Mutex()

    private object Keys {
        val LANGUAGE = stringPreferencesKey("language")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val WIFI_ONLY_SYNC = booleanPreferencesKey("wifi_only_sync")
        val BASE_CURRENCY = stringPreferencesKey("base_currency")
        val AUTO_SYNC_ENABLED = booleanPreferencesKey("auto_sync_enabled")
        val SYNC_FREQUENCY_MINUTES = androidx.datastore.preferences.core.intPreferencesKey("sync_frequency_minutes")
        val CHARGING_ONLY_SYNC = booleanPreferencesKey("charging_only_sync")
    }

    val language: Flow<String> = store.data.map { it[Keys.LANGUAGE] ?: "en" }
    val biometricEnabled: Flow<Boolean> = store.data.map { it[Keys.BIOMETRIC_ENABLED] ?: true }
    val wifiOnlySync: Flow<Boolean> = store.data.map { it[Keys.WIFI_ONLY_SYNC] ?: false }
    val baseCurrency: Flow<String> = store.data.map { it[Keys.BASE_CURRENCY] ?: "ZAR" }
    val autoSyncEnabled: Flow<Boolean> = store.data.map { it[Keys.AUTO_SYNC_ENABLED] ?: true }
    val syncFrequencyMinutes: Flow<Int> = store.data.map { it[Keys.SYNC_FREQUENCY_MINUTES] ?: 15 }
    val chargingOnlySync: Flow<Boolean> = store.data.map { it[Keys.CHARGING_ONLY_SYNC] ?: false }

    suspend fun setLanguage(languageTag: String) {
        store.edit { it[Keys.LANGUAGE] = languageTag }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        store.edit { it[Keys.BIOMETRIC_ENABLED] = enabled }
    }

    suspend fun setWifiOnlySync(enabled: Boolean) {
        store.edit { it[Keys.WIFI_ONLY_SYNC] = enabled }
    }

    suspend fun setBaseCurrency(currency: String) {
        store.edit { it[Keys.BASE_CURRENCY] = currency }
    }

    suspend fun setAutoSyncEnabled(enabled: Boolean) {
        store.edit { it[Keys.AUTO_SYNC_ENABLED] = enabled }
    }

    suspend fun setSyncFrequencyMinutes(minutes: Int) {
        store.edit { it[Keys.SYNC_FREQUENCY_MINUTES] = minutes }
    }

    suspend fun setChargingOnlySync(enabled: Boolean) {
        store.edit { it[Keys.CHARGING_ONLY_SYNC] = enabled }
    }

    /**
     * Loads the authenticated user's settings from the API and updates
     * the local DataStore cache only after a successful response.
     */
    suspend fun loadFromApi(): UserSettingsDto = remoteMutex.withLock {
        val settings = fetchRemoteSettings()
        cacheSettings(settings)
        settings
    }

    /**
     * Updates the four preferences currently exposed by the Settings screen.
     * Fetches the latest server values first to preserve the monthly budget.
     */
    suspend fun saveToApi(
        language: String,
        biometricEnabled: Boolean,
        wifiOnlySync: Boolean,
        baseCurrency: String,
    ): UserSettingsDto = remoteMutex.withLock {
        val current = fetchRemoteSettings()

        val updated = current.copy(
            language = language,
            biometricEnabled = biometricEnabled,
            syncOnWifiOnly = wifiOnlySync,
            baseCurrency = baseCurrency,
        )

        val response = api.updateSettings(updated)
        if (!response.isSuccessful) {
            throw HttpException(response)
        }

        val saved = response.body()
            ?: throw IllegalStateException("The API returned no saved settings.")

        cacheSettings(saved)
        saved
    }

    private suspend fun fetchRemoteSettings(): UserSettingsDto {
        val response = api.getSettings()
        if (!response.isSuccessful) {
            throw HttpException(response)
        }

        return response.body()
            ?: throw IllegalStateException("The API returned no settings.")
    }

    private suspend fun cacheSettings(settings: UserSettingsDto) {
        store.edit { preferences ->
            preferences[Keys.LANGUAGE] = settings.language
            preferences[Keys.BIOMETRIC_ENABLED] = settings.biometricEnabled
            preferences[Keys.WIFI_ONLY_SYNC] = settings.syncOnWifiOnly
            preferences[Keys.BASE_CURRENCY] = settings.baseCurrency
        }
    }
}
