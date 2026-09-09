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

private val Context.dataStore by preferencesDataStore(name = "devledger_settings")

/**
 * Local app preferences (language, biometric toggle, sync policy, base currency) via
 * Jetpack DataStore. Language changes are also pushed to the REST API's
 * `/api/users/me/settings` endpoint so a second device inherits the same preference,
 * per Section 3.3 of the Part 1 Planning and Design document.
 */
@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext context: Context) {

    private val store = context.dataStore

    private object Keys {
        val LANGUAGE = stringPreferencesKey("language")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val WIFI_ONLY_SYNC = booleanPreferencesKey("wifi_only_sync")
        val BASE_CURRENCY = stringPreferencesKey("base_currency")
    }

    val language: Flow<String> = store.data.map { it[Keys.LANGUAGE] ?: "en" }
    val biometricEnabled: Flow<Boolean> = store.data.map { it[Keys.BIOMETRIC_ENABLED] ?: true }
    val wifiOnlySync: Flow<Boolean> = store.data.map { it[Keys.WIFI_ONLY_SYNC] ?: false }
    val baseCurrency: Flow<String> = store.data.map { it[Keys.BASE_CURRENCY] ?: "ZAR" }

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
}
