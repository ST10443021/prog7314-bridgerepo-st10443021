package com.devledger.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.devledger.app.notifications.RegisterDeviceWorker
import com.devledger.app.sync.RecurringTransactionWorker
import dagger.hilt.android.HiltAndroidApp
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
    }
}