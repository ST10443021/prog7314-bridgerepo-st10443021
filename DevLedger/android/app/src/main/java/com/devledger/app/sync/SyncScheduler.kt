package com.devledger.app.sync

import android.content.Context
import android.util.Log
import androidx.work.*
import com.devledger.app.data.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * The user's saved background-sync choices. They live in DataStore ([SettingsRepository]) and are
 * the single source of truth for how WorkManager is configured.
 */
data class SyncPreferences(
    val autoSync: Boolean = true,
    val wifiOnly: Boolean = false,
    val frequencyMinutes: Int = 15,
    val chargingOnly: Boolean = false,
)

/** What the scheduler should do for a given [SyncPreferences]; pure so it can be unit tested. */
enum class SyncPlan { PERIODIC, CANCEL }

/**
 * Reference scope: WorkManager background execution.
 * (Google Developers, n.d.c). See REFERENCES-CODE.md for full entries.
 */
object SyncScheduler {
    private const val TAG = "SyncScheduler"

    /** WorkManager enforces a 15 minute minimum for periodic work (Google Developers, n.d.c). */
    const val MIN_PERIODIC_MINUTES = 15

    private fun constraints(wifiOnly: Boolean, chargingOnly: Boolean): Constraints =
        Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresCharging(chargingOnly)
            .build()

    /** Pure decision used by [apply]: automatic sync off means no periodic job at all. */
    fun planFor(preferences: SyncPreferences): SyncPlan =
        if (preferences.autoSync) SyncPlan.PERIODIC else SyncPlan.CANCEL

    fun schedulePeriodicSync(context: Context, wifiOnly: Boolean, frequencyMinutes: Int = 15, chargingOnly: Boolean = false) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(frequencyMinutes.toLong().coerceAtLeast(MIN_PERIODIC_MINUTES.toLong()), TimeUnit.MINUTES)
            .setConstraints(constraints(wifiOnly, chargingOnly))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(SyncWorker.WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun cancelPeriodicSync(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(SyncWorker.WORK_NAME)
    }

    /**
     * Applies the saved preferences: schedules the recurring job with the user's Wi-Fi, charging
     * and interval choices, or cancels it when automatic sync is off. Called at app start, after
     * sign-in and whenever a preference changes (see DevLedgerApplication).
     */
    fun apply(context: Context, preferences: SyncPreferences) {
        when (planFor(preferences)) {
            SyncPlan.PERIODIC -> {
                Log.i(TAG, "Scheduling periodic sync every ${preferences.frequencyMinutes} min " +
                    "(wifiOnly=${preferences.wifiOnly}, chargingOnly=${preferences.chargingOnly})")
                schedulePeriodicSync(context, preferences.wifiOnly, preferences.frequencyMinutes, preferences.chargingOnly)
            }
            SyncPlan.CANCEL -> {
                Log.i(TAG, "Automatic sync is off; periodic job cancelled")
                cancelPeriodicSync(context)
            }
        }
    }

    /** Reads the saved preferences once. */
    suspend fun currentPreferences(settings: SettingsRepository): SyncPreferences = SyncPreferences(
        autoSync = settings.autoSyncEnabled.first(),
        wifiOnly = settings.wifiOnlySync.first(),
        frequencyMinutes = settings.syncFrequencyMinutes.first(),
        chargingOnly = settings.chargingOnlySync.first(),
    )

    /**
     * Called by every repository after a local write. The change is already PENDING in Room, so
     * nothing is lost either way: with automatic sync on an immediate one-time job is queued
     * (it waits for connectivity); with it off the change simply stays queued for a manual sync.
     */
    suspend fun queueAfterLocalChange(context: Context, settings: SettingsRepository) {
        val preferences = currentPreferences(settings)
        if (!preferences.autoSync) {
            Log.i(TAG, "Automatic sync is off; change stays queued until Sync now")
            return
        }
        syncNow(context, preferences.wifiOnly, preferences.chargingOnly)
    }

    fun syncNow(context: Context, wifiOnly: Boolean = false, chargingOnly: Boolean = false) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints(wifiOnly, chargingOnly))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("${SyncWorker.WORK_NAME}_immediate", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }
}
