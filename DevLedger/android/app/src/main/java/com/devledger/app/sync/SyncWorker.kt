package com.devledger.app.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.devledger.app.data.local.entity.SyncStatus
import com.devledger.app.data.remote.DevLedgerApiService
import com.devledger.app.data.remote.dto.SyncBatchRequest
import com.devledger.app.data.remote.dto.SyncOperation
import com.devledger.app.data.repository.SnippetRepository
import com.devledger.app.data.repository.TransactionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Background sync job — the WorkManager-driven queue described in the Part 1 Planning
 * and Design document (Section 5, "Offline Mode with Synchronisation"). Replays every
 * PENDING snippet and transaction to the REST API's `/api/sync/batch` endpoint in
 * creation order once connectivity is available, resolving conflicts server-side on a
 * last-write-wins basis keyed by `updatedAt`.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val api: DevLedgerApiService,
    private val snippetRepository: SnippetRepository,
    private val transactionRepository: TransactionRepository,
) : CoroutineWorker(context, params) {

    private val json = Json { encodeDefaults = true }

    override suspend fun doWork(): Result {
        return try {
            val pendingSnippets = snippetRepository.getPendingSync()
            val pendingTransactions = transactionRepository.getPendingSync()

            if (pendingSnippets.isEmpty() && pendingTransactions.isEmpty()) {
                return Result.success()
            }

            val operations = pendingSnippets.map {
                SyncOperation(entity = "snippet", action = "upsert", payload = json.encodeToString(it), clientTimestamp = it.updatedAt)
            } + pendingTransactions.map {
                SyncOperation(entity = "transaction", action = "upsert", payload = json.encodeToString(it), clientTimestamp = it.createdAt)
            }

            val response = api.syncBatch(SyncBatchRequest(operations))
            if (!response.isSuccessful) return Result.retry()

            pendingSnippets.forEach { snippetRepository.markSynced(it.snippetId) }
            pendingTransactions.forEach { transactionRepository.markSynced(it.transactionId) }

            Result.success()
        } catch (e: Exception) {
            // No connectivity or a transient server error — WorkManager will retry with backoff.
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "devledger_sync_work"
    }
}
