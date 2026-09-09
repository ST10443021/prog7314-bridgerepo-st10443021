package com.devledger.app.sync

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

/** Schedules [SyncWorker] to run periodically and be retried with exponential backoff,
 *  constrained to a network connection (optionally unmetered/Wi-Fi only per user setting). */
object SyncScheduler {

    fun schedulePeriodicSync(context: Context, wifiOnly: Boolean) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            SyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /** Triggers an immediate one-off sync, e.g. from the "Sync Now" button in Settings. */
    fun syncNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "${SyncWorker.WORK_NAME}_immediate",
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}
