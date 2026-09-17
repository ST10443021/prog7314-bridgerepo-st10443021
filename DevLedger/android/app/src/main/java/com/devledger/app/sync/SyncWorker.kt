package com.devledger.app.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.devledger.app.data.local.dao.DeletedDao
import com.devledger.app.data.local.dao.SyncLogDao
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
 * Orchestrates bidirectional synchronization between local RoomDB and the DevLedger API.
 * Handles upserts and deletions for Snippets, Transactions, Folders, and Tags.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val api: DevLedgerApiService,
    private val snippetRepository: SnippetRepository,
    private val transactionRepository: TransactionRepository,
    private val folderRepository: FolderRepository,
    private val userTagRepository: UserTagRepository,
    private val authRepository: AuthRepository,
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
            val pendingSnippets = snippetRepository.getPendingSyncForUser(user.userId)
            val pendingTransactions = transactionRepository.getPendingSyncForUser(user.userId)
            val pendingFolders = folderRepository.getPendingSyncForUser(user.userId)
            val pendingTags = userTagRepository.getPendingSyncForUser(user.userId)
            val pendingDeletions = deletedDao.getForUser(user.userId)

            if (pendingSnippets.isEmpty() && pendingTransactions.isEmpty() && 
                pendingFolders.isEmpty() && pendingTags.isEmpty() && pendingDeletions.isEmpty()) {
                // If nothing to upload, still perform bidirectional sync (Download latest state)
                ensureSameSession(startingToken, currentUserId, processedItems)?.let { return it }
                snippetRepository.refreshFromApi()
                transactionRepository.refreshFromApi()
                folderRepository.refreshFromApi()
                userTagRepository.refreshFromApi()
                return success(itemsSynced = 0, userId = user.userId)
            }

            val totalItems = pendingSnippets.size + pendingTransactions.size + 
                             pendingFolders.size + pendingTags.size + pendingDeletions.size

            publishProgress(processedItems, totalItems, "Preparing")

            // 2. Perform Batch Upload
            val operations = mutableListOf<SyncOperation>()

            // Deletions first. The API remembers deleted IDs, so older uploads cannot revive them.
            pendingDeletions.forEach { del ->
                val payload = if (del.entityType == "snippet") {
                    json.encodeToString(SnippetDeletePayload(del.entityId, user.userId))
                } else {
                    json.encodeToString(DeleteSyncPayload(del.entityId))
                }
                
                operations.add(SyncOperation(
                    entity = del.entityType,
                    action = "delete",
                    payload = payload,
                    clientTimestamp = del.deletedAt
                ))
            }

            // Upserts
            pendingSnippets.forEach { s ->
                operations.add(SyncOperation("snippet", "upsert", json.encodeToString(
                    SnippetSyncPayload(
                        snippetId = s.snippetId, userId = s.userId, folderId = s.folderId, title = s.title, 
                        content = s.content, language = s.language, description = s.description, 
                        tags = s.tagsCsv.split(",").filter { it.isNotBlank() }, // teammate's List<String> fix
                        isFavourite = s.isFavourite, isArchived = s.isArchived, createdAt = s.createdAt, updatedAt = s.updatedAt
                    )
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

            // Chunk upload if too many operations
            for (batchOps in operations.chunked(100)) {
                ensureSameSession(startingToken, currentUserId, processedItems)?.let { return it }

                val response = api.syncBatch(SyncBatchRequest(batchOps))
                if (!response.isSuccessful) return responseFailure(response.code(), "batch", currentUserId, processedItems)

                val acknowledgement = response.body() ?: return failure("No acknowledgement received.", currentUserId, processedItems)
                
                // Mark items as synced locally
                acknowledgement.applied.forEach { id ->
                    snippetRepository.markSynced(id)
                    transactionRepository.markSynced(id)
                    folderRepository.markSynced(id)
                    userTagRepository.markSynced(id)
                    deletedDao.delete(id)
                }
                
                processedItems += batchOps.size
                publishProgress(processedItems, totalItems, "Uploading")
            }

            // 3. Bidirectional Sync: Download latest remote state
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
            // Log but don't fail the whole sync if download fails
        }
    }

    private suspend fun ensureSameSession(startingToken: String, userId: String?, itemsSynced: Int): Result? {
        return if (tokenManager.getAccessToken() != startingToken) failure("The account changed during synchronisation.", userId, itemsSynced) else null
    }

    private suspend fun responseFailure(statusCode: Int, entityName: String, userId: String?, itemsSynced: Int): Result {
        return when (statusCode) {
            408, 429 -> Result.retry()
            in 500..599 -> Result.retry()
            401, 403 -> failure("The API rejected the session or $entityName ownership.", userId, itemsSynced)
            409 -> failure("A $entityName conflicts with its server version.", userId, itemsSynced)
            else -> failure("The API rejected the $entityName sync request ($statusCode).", userId, itemsSynced)
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
