package com.devledger.app.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.devledger.app.data.repository.TransactionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/** Daily check for due recurring transactions — the actual mechanism behind the
 *  "Recurring Transaction" toggle, not just stored metadata (Google Developers, n.d.c).
 *  Each due transaction is rolled forward exactly once per run to avoid double-firing
 *  if the device was offline across more than one due date. */
@HiltWorker
class RecurringTransactionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val transactionRepository: TransactionRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val due = transactionRepository.getDueRecurring()
            due.forEach { transactionRepository.rollForwardRecurring(it) }
            Result.success()
        } catch (e: Exception) {
            Log.e("RecurringWorker", "Failed to process recurring transactions", e)
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "devledger_recurring_transactions"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RecurringTransactionWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}