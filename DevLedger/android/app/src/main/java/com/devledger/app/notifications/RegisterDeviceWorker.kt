package com.devledger.app.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * One-shot WorkManager job (Google Developers, n.d.c) that registers the device's current FCM
 * token with the API. Run from [DevLedgerMessagingService.onNewToken], which fires on
 * a system-managed, non-cancellable path where a direct network call would be unsafe —
 * WorkManager gives it retry-with-backoff and defers it if there's no connectivity yet,
 * matching the pattern already used by [com.devledger.app.sync.SyncWorker].
 */
@HiltWorker
/**
 * Reference scope: WorkManager background execution.
 * (Google Developers, n.d.c). See REFERENCES-CODE.md for full entries.
 */
class RegisterDeviceWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val deviceRegistrar: DeviceRegistrar,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        deviceRegistrar.registerCurrentToken()
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "devledger_register_device_work"
    }
}