package com.devledger.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import android.util.Log
import com.devledger.app.data.local.dao.UserDao
import com.devledger.app.data.repository.SettingsRepository
import com.devledger.app.notifications.RegisterDeviceWorker
import com.devledger.app.sync.RecurringTransactionWorker
import com.devledger.app.sync.SyncPreferences
import com.devledger.app.sync.SyncScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Application entry point. Enables Hilt dependency injection app-wide and wires
 * WorkManager to Hilt so [com.devledger.app.sync.SyncWorker] can receive injected
 * dependencies (Room DAOs, Retrofit API service) — see Google Developers (n.d.c) for
 * the recommended HiltWorkerFactory pattern.
 */
@HiltAndroidApp
/**
 * Reference scope: WorkManager background execution.
 * (Google Developers, n.d.c). See REFERENCES-CODE.md for full entries.
 */
class DevLedgerApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    // Lazy so the Retrofit/OkHttp/Keystore-backed graph behind these is built off the main thread
    // (inside the coroutines below) instead of during Application.onCreate, keeping start-up fast.
    @Inject lateinit var lazySettingsRepository: dagger.Lazy<SettingsRepository>
    @Inject lateinit var lazyUserDao: dagger.Lazy<UserDao>

    /** Process-wide scope for the small observers below; they never touch the UI. */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        // Schedule the recurring transaction worker on application startup
        RecurringTransactionWorker.schedule(this)
        // Repair missed registration on upgrade/startup without resetting an active retry.
        RegisterDeviceWorker.enqueue(this, replaceExisting = false)
        observeSyncPreferences()
    }

    /**
     * Keeps WorkManager in step with the signed-in user and the saved sync preferences.
     * This runs at start-up, right after sign-in (the user row appears) and whenever the user
     * edits a sync preference in Settings or on the Sync screen, so a fresh install no longer
     * waits for a record to be saved before periodic sync exists, and turning automatic sync
     * off is respected (Google Developers, n.d.c; Google Developers, n.d.l).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeSyncPreferences() {
        applicationScope.launch {
            val settingsRepository = lazySettingsRepository.get()
            val userDao = lazyUserDao.get()
            val preferences = combine(
                settingsRepository.autoSyncEnabled,
                settingsRepository.wifiOnlySync,
                settingsRepository.syncFrequencyMinutes,
                settingsRepository.chargingOnlySync,
            ) { auto, wifi, minutes, charging -> SyncPreferences(auto, wifi, minutes, charging) }
                .distinctUntilChanged()

            userDao.observeCurrentUser()
                .map { it?.userId }
                .distinctUntilChanged()
                .flatMapLatest { userId -> if (userId == null) flowOf(null) else preferences }
                .collect { prefs ->
                    try {
                        if (prefs == null) {
                            SyncScheduler.cancelPeriodicSync(this@DevLedgerApplication)
                        } else {
                            SyncScheduler.apply(this@DevLedgerApplication, prefs)
                        }
                    } catch (error: Exception) {
                        Log.e("DevLedgerApp", "Could not apply sync preferences", error)
                    }
                }
        }
        // Upload anything queued while offline as soon as an account is available at start-up.
        applicationScope.launch {
            val settingsRepository = lazySettingsRepository.get()
            val userDao = lazyUserDao.get()
            userDao.observeCurrentUser().map { it?.userId }.distinctUntilChanged().collect { userId ->
                if (userId == null) return@collect
                try {
                    SyncScheduler.queueAfterLocalChange(this@DevLedgerApplication, settingsRepository)
                } catch (error: Exception) {
                    Log.e("DevLedgerApp", "Start-up sync could not be queued", error)
                }
            }
        }
    }
}