package com.devledger.app.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.devledger.app.data.local.dao.SyncLogDao
import com.devledger.app.data.local.entity.SyncLogEntity
import com.devledger.app.util.IdGenerator
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** WorkManager adapter; the engine owns upload/download and conflict decisions. */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val engine: SyncEngine,
    private val syncLogDao: SyncLogDao,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        var account: String? = null
        try {
            val outcome = engine.run { progress ->
                account = progress.userId
                setProgress(workDataOf(KEY_USER_ID to progress.userId,
                    KEY_PROCESSED_ITEMS to progress.processed, KEY_TOTAL_ITEMS to progress.total,
                    KEY_CURRENT_ENTITY to progress.phase, KEY_UPDATED_AT to System.currentTimeMillis()))
            }
            val now = outcome.finishedAt
            outcome.userId?.let { userId -> syncLogDao.insert(SyncLogEntity(
                IdGenerator.newId(), userId, outcome.status, outcome.uploaded, outcome.message, now)) }
            val output = workDataOf(KEY_USER_ID to outcome.userId, KEY_SYNC_ERROR to outcome.message,
                KEY_COMPLETED_AT to if (outcome.status == "COMPLETED") now else 0L)
            return when (outcome.status) {
                "COMPLETED" -> Result.success(output)
                "RETRY", "PARTIAL" -> Result.retry()
                else -> Result.failure(output)
            }
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                account?.let { syncLogDao.insert(SyncLogEntity(IdGenerator.newId(), it, "CANCELLED",
                    0, "Sync stopped before verification completed.", System.currentTimeMillis())) }
            }
            throw cancelled
        }
    }
    companion object {
        const val WORK_NAME = "devledger_sync_work"
        const val KEY_USER_ID = "sync_user_id"
        const val KEY_UPDATED_AT = "sync_updated_at"
        const val KEY_PROCESSED_ITEMS = "processed_items"
        const val KEY_TOTAL_ITEMS = "total_items"
        const val KEY_CURRENT_ENTITY = "current_entity"
        const val KEY_COMPLETED_AT = "completed_at"
        const val KEY_SYNC_ERROR = "sync_error"
    }
}
