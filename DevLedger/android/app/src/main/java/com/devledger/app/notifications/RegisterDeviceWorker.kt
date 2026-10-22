package com.devledger.app.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/** Durable registration with constraints/backoff (Google Developers, n.d.c; REFERENCES-CODE.md). */
@HiltWorker
class RegisterDeviceWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val deviceRegistrar: DeviceRegistrar,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = when (deviceRegistrar.registerCurrentToken()) {
        DeviceRegistrationOutcome.REGISTERED -> Result.success()
        DeviceRegistrationOutcome.RETRY -> Result.retry()
        // No account to register. The next successful sign-in schedules fresh work.
        DeviceRegistrationOutcome.SIGNED_OUT, DeviceRegistrationOutcome.SESSION_CHANGED -> Result.success()
        // Includes 400/401/403/404: fix configuration or sign in again, rather than
        // retrying a rejected request forever. A new sign-in/token event can recover.
        DeviceRegistrationOutcome.REJECTED -> Result.failure()
    }

    companion object {
        const val WORK_NAME = "devledger_register_device_work"

        internal fun request(): OneTimeWorkRequest = OneTimeWorkRequestBuilder<RegisterDeviceWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(WORK_NAME)
            .build()

        fun enqueue(context: Context, replaceExisting: Boolean = true) =
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                // Explicit sign-in/token changes supersede obsolete attempts. App
                // startup keeps pending work and its backoff, preventing retry storms.
                if (replaceExisting) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                request(),
            )
    }
}
