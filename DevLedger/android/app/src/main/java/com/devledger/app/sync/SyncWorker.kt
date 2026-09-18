package com.devledger.app.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.devledger.app.data.local.dao.DeletedDao
import com.devledger.app.data.local.dao.SyncLogDao
import com.devledger.app.data.local.entity.SnippetEntity
import com.devledger.app.data.local.entity.SyncLogEntity
import com.devledger.app.data.remote.DevLedgerApiService
import com.devledger.app.data.remote.auth.TokenManager
import com.devledger.app.data.remote.dto.*
import com.devledger.app.data.repository.*
import com.devledger.app.util.IdGenerator
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Orchestrates bidirectional synchronization.
 * Handles Snippets (marker-based delete), and Transactions/Folders/Tags (table-based delete).
 */
@HiltWorker
/**
 * Reference scope: WorkManager background execution.
 * (Google Developers, n.d.c). See REFERENCES-CODE.md for full entries.
 */
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val api: DevLedgerApiService,
    private val snippetRepository: SnippetRepository,
    private val transactionRepository: TransactionRepository,
    private val folderRepository: FolderRepository,
    private val userTagRepository: UserTagRepository,
    private val authRepository: AuthRepository,
    private val notificationRepository: NotificationRepository,
    private val tokenManager: TokenManager,
    private val syncLogDao: SyncLogDao,
    private val deletedDao: DeletedDao,
) : CoroutineWorker(context, params) {

    private val json = Json { encodeDefaults = true }

    override suspend fun doWork(): Result {
        var currentUserId: String? = null
        var processedItems = 0

        return try {
            val startingToken = tokenManager.getAccessToken()
                ?.takeIf { it.isNotBlank() }
                ?: return failure("Sign in before synchronising.", userId = null)

            val user = authRepository.currentUser.first()
                ?: return failure("The current account could not be loaded.", userId = null)

            currentUserId = user.userId

            // 1. Gather all pending changes
            val pendingDeletesMarkers = snippetRepository.getPendingDeletesForUser(user.userId)
            val pendingSnippets = snippetRepository.getPendingSyncForUser(user.userId)
            val pendingTransactions = transactionRepository.getPendingSyncForUser(user.userId)
            val pendingFolders = folderRepository.getPendingSyncForUser(user.userId)
            val pendingTags = userTagRepository.getPendingSyncForUser(user.userId)
            val pendingGenericDeletions = deletedDao.getForUser(user.userId)

            if (pendingDeletesMarkers.isEmpty() && pendingSnippets.isEmpty() && 
                pendingTransactions.isEmpty() && pendingFolders.isEmpty() && 
                pendingTags.isEmpty() && pendingGenericDeletions.isEmpty()) {
                
                ensureSameSession(startingToken, currentUserId, processedItems)?.let { return it }
                downloadLatest()
                return success(itemsSynced = 0, userId = user.userId)
            }

            val totalItems = pendingDeletesMarkers.size + pendingSnippets.size + 
                             pendingTransactions.size + pendingFolders.size + 
                             pendingTags.size + pendingGenericDeletions.size

            publishProgress(processedItems, totalItems, "Preparing")

            // 2. Perform Batch Upload
            val operations = mutableListOf<SyncOperation>()

            // Snippet marker-based deletions first
            pendingDeletesMarkers.forEach { s ->
                operations.add(SyncOperation("snippet", "delete", json.encodeToString(
                    SnippetDeletePayload(s.snippetId, s.userId)
                ), s.updatedAt))
            }

            // Generic deletions (Folders, Tags, Transactions)
            pendingGenericDeletions.forEach { del ->
                operations.add(SyncOperation(del.entityType, "delete", json.encodeToString(
                    DeleteSyncPayload(del.entityId)
                ), del.deletedAt))
            }

            // Upserts
            pendingSnippets.forEach { s ->
                operations.add(SyncOperation("snippet", "upsert", json.encodeToString(
                    SnippetSyncPayload(s.snippetId, s.userId, s.folderId, s.title, s.content, s.language, s.description, 
                                       s.tagsCsv.split(",").filter { it.isNotBlank() }, s.isFavourite, s.isArchived, s.createdAt, s.updatedAt)
                ), s.updatedAt))
            }

            pendingTransactions.forEach { t ->
                operations.add(SyncOperation("transaction", "upsert", json.encodeToString(
                    TransactionSyncPayload(t.transactionId, t.userId, t.type, t.amount, t.currency, t.category, t.description, 
                                           t.clientOrFrom, t.relatedProject, t.paymentMethod, t.referenceNumber, t.isRecurring, 
                                           t.recurringFrequency, t.nextOccurrenceDate, t.attachmentFileName, t.attachmentBase64, 
                                           t.transactionDate, t.createdAt)
                ), t.createdAt))
            }

            pendingFolders.forEach { f ->
                operations.add(SyncOperation("folder", "upsert", json.encodeToString(
                    FolderSyncPayload(f.folderId, f.userId, f.name, f.parentFolderId, f.createdAt)
                ), f.createdAt))
            }

            pendingTags.forEach { tg ->
                operations.add(SyncOperation("tag", "upsert", json.encodeToString(
                    UserTagSyncPayload(tg.tagId, tg.userId, tg.name, tg.colorHex, tg.createdAt)
                ), tg.createdAt))
            }

            // Chunk upload
            for (batchOps in operations.chunked(100)) {
                ensureSameSession(startingToken, currentUserId, processedItems)?.let { return it }

                val response = api.syncBatch(SyncBatchRequest(batchOps))
                if (!response.isSuccessful) return responseFailure(response.code(), "batch", currentUserId, processedItems)

                val acknowledgement = response.body() ?: return failure("No acknowledgement received.", currentUserId, processedItems)
                
                acknowledgement.applied.forEach { id ->
                    // Safely acknowledge deletions (only works if status is DELETE_PENDING)
                    snippetRepository.acknowledgeDelete(user.userId, id)
                    
                    // Safely mark upserts as synced (only works if status is PENDING)
                    snippetRepository.markSynced(id)
                    transactionRepository.markSynced(id)
                    folderRepository.markSynced(id)
                    userTagRepository.markSynced(id)
                    
                    // Cleanup generic deletion tracking
                    deletedDao.delete(id)
                }
                
                processedItems += batchOps.size
                publishProgress(processedItems, totalItems, "Uploading")
            }

            // 3. Bidirectional Sync
            downloadLatest()

            success(itemsSynced = processedItems, userId = user.userId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: IOException) {
            Result.retry()
        } catch (error: Exception) {
            failure("Synchronisation failed: ${error.message}", currentUserId, processedItems)
        }
    }

    private suspend fun downloadLatest() {
        publishProgress(0, 1, "Downloading")
        try {
            snippetRepository.refreshFromApi()
            transactionRepository.refreshFromApi()
            folderRepository.refreshFromApi()
            userTagRepository.refreshFromApi()
        } catch (e: Exception) {
            // Ignore download errors to preserve upload success
        }
    }

    private suspend fun ensureSameSession(startingToken: String, userId: String?, itemsSynced: Int): Result? {
        return if (tokenManager.getAccessToken() != startingToken) failure("Account session changed.", userId, itemsSynced) else null
    }

    private suspend fun responseFailure(statusCode: Int, entityName: String, userId: String?, itemsSynced: Int): Result {
        return when (statusCode) {
            408, 429 -> Result.retry()
            in 500..599 -> Result.retry()
            else -> failure("The API rejected the $entityName request ($statusCode).", userId, itemsSynced)
        }
    }

    private suspend fun publishProgress(processed: Int, total: Int, currentEntity: String) {
        setProgress(workDataOf(KEY_PROCESSED_ITEMS to processed, KEY_TOTAL_ITEMS to total, KEY_CURRENT_ENTITY to currentEntity))
    }

    private suspend fun success(itemsSynced: Int, userId: String): Result {
        syncLogDao.insert(SyncLogEntity(IdGenerator.newId(), userId, "COMPLETED", itemsSynced, null, System.currentTimeMillis()))
        
        return Result.success(workDataOf(KEY_COMPLETED_AT to System.currentTimeMillis()))
    }

    private suspend fun failure(message: String, userId: String?, itemsSynced: Int = 0): Result {
        if (userId != null) {
            syncLogDao.insert(SyncLogEntity(IdGenerator.newId(), userId, "FAILED", itemsSynced, message, System.currentTimeMillis()))
        }
        return Result.failure(workDataOf(KEY_SYNC_ERROR to message))
    }

    companion object {
        const val WORK_NAME = "devledger_sync_work"
        const val KEY_PROCESSED_ITEMS = "processed_items"
        const val KEY_TOTAL_ITEMS = "total_items"
        const val KEY_CURRENT_ENTITY = "current_entity"
        const val KEY_COMPLETED_AT = "completed_at"
        const val KEY_SYNC_ERROR = "sync_error"
    }
}
