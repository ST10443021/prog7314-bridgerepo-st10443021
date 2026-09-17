package com.devledger.app.sync

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

object SyncScheduler {

    private fun constraints(wifiOnly: Boolean, chargingOnly: Boolean): Constraints =
        Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresCharging(chargingOnly)
            .build()

    fun schedulePeriodicSync(context: Context, wifiOnly: Boolean, frequencyMinutes: Int = 15, chargingOnly: Boolean = false) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(frequencyMinutes.toLong().coerceAtLeast(15), TimeUnit.MINUTES)
            .setConstraints(constraints(wifiOnly, chargingOnly))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(SyncWorker.WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun cancelPeriodicSync(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(SyncWorker.WORK_NAME)
    }

    fun syncNow(context: Context, wifiOnly: Boolean = false, chargingOnly: Boolean = false) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints(wifiOnly, chargingOnly))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("${SyncWorker.WORK_NAME}_immediate", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }
}