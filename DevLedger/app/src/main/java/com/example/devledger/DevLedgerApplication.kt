package com.devledger.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point. Enables Hilt dependency injection app-wide and wires
 * WorkManager to Hilt so [com.devledger.app.sync.SyncWorker] can receive injected
 * dependencies (Room DAOs, Retrofit API service) — see Google Developers (n.d.c) for
 * the recommended HiltWorkerFactory pattern.
 */
@HiltAndroidApp
class DevLedgerApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
