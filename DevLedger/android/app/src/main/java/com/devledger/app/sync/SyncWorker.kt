package com.devledger.app.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.devledger.app.data.local.dao.SyncLogDao
import com.devledger.app.data.local.entity.SyncLogEntity
import com.devledger.app.data.remote.DevLedgerApiService
import com.devledger.app.data.remote.auth.TokenManager
import com.devledger.app.data.remote.dto.SnippetSyncPayload
import com.devledger.app.data.remote.dto.SyncBatchRequest
import com.devledger.app.data.remote.dto.SyncOperation
import com.devledger.app.data.remote.dto.TransactionSyncPayload
import com.devledger.app.data.repository.AuthRepository
import com.devledger.app.data.repository.SnippetRepository
import com.devledger.app.data.repository.TransactionRepository
import com.devledger.app.util.IdGenerator
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.devledger.app.data.remote.dto.SnippetDeletePayload

/**
 * Uploads pending offline changes belonging to the current account.
 *
 * Snippets and financial transactions are saved to Room first and then pushed
 * to the REST API once network connectivity is available.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val api: DevLedgerApiService,
    private val snippetRepository: SnippetRepository,
    private val transactionRepository: TransactionRepository,
    private val authRepository: AuthRepository,
    private val tokenManager: TokenManager,
    private val syncLogDao: SyncLogDao,
) : CoroutineWorker(context, params) {

    private val json = Json {
        encodeDefaults = true
    }

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

            val pendingDeletes = snippetRepository.getPendingDeletesForUser(user.userId)

            val pendingSnippets =
                snippetRepository.getPendingSyncForUser(user.userId)

            val pendingTransactions =
                transactionRepository.getPendingSyncForUser(user.userId)

            if (pendingDeletes.isEmpty() && pendingSnippets.isEmpty() && pendingTransactions.isEmpty()) {
                ensureSameSession(startingToken, currentUserId, processedItems)?.let { return it }
                snippetRepository.refreshFromApi()
                return success(itemsSynced = 0, userId = user.userId)
            }

            val totalItems =
                pendingDeletes.size + pendingSnippets.size + pendingTransactions.size

            publishProgress(
                processed = processedItems,
                total = totalItems,
                currentEntity = "Preparing",
            )

            var changedDuringUpload = false

            // Delete first. The API remembers deleted IDs, so older uploads cannot revive them.
            for (batch in pendingDeletes.chunked(100)) {
                ensureSameSession(startingToken, currentUserId, processedItems)?.let { return it }
                val operations = batch.map { snippet ->
                    SyncOperation(
                        entity = "snippet", action = "delete",
                        payload = json.encodeToString(SnippetDeletePayload(snippet.snippetId, snippet.userId)),
                        clientTimestamp = snippet.updatedAt,
                    )
                }
                val response = api.syncBatch(SyncBatchRequest(operations))
                if (!response.isSuccessful) return responseFailure(response.code(), "snippet deletion", currentUserId, processedItems)
                val ack = response.body() ?: return failure("Missing deletion acknowledgement.", currentUserId, processedItems)
                val expected = batch.map { it.snippetId }.toSet()
                if (ack.conflicts.isNotEmpty() || ack.applied.toSet() != expected || ack.applied.size != expected.size) {
                    return failure("The API did not confirm every deletion.", currentUserId, processedItems)
                }
                ensureSameSession(startingToken, currentUserId, processedItems)?.let { return it }
                for (snippet in batch) snippetRepository.acknowledgeDelete(snippet)
                processedItems += batch.size
                publishProgress(processedItems, totalItems, "Deleted snippets")
            }

            // ---- Snippets -------------------------------------------------
            for (batch in pendingSnippets.chunked(100)) {
                ensureSameSession(startingToken, currentUserId, processedItems)?.let {
                    return it
                }

                val operations = batch.map { snippet ->
                    SyncOperation(
                        entity = "snippet",
                        action = "upsert",
                        payload = json.encodeToString(
                            SnippetSyncPayload(
                                snippetId = snippet.snippetId,
                                userId = snippet.userId,
                                folderId = snippet.folderId,
                                title = snippet.title,
                                content = snippet.content,
                                language = snippet.language,
                                description = snippet.description,
                                tagsCsv = snippet.tagsCsv,
                                isFavourite = snippet.isFavourite,
                                isArchived = snippet.isArchived,
                                createdAt = snippet.createdAt,
                                updatedAt = snippet.updatedAt,
                            )
                        ),
                        clientTimestamp = snippet.updatedAt,
                    )
                }

                val response = api.syncBatch(
                    SyncBatchRequest(operations)
                )

                if (!response.isSuccessful) {
                    return responseFailure(
                        response.code(),
                        "snippet",
                        currentUserId,
                        processedItems,
                    )
                }

                val acknowledgement = response.body()
                    ?: return failure(
                        "The API returned no snippet sync acknowledgement.",
                        currentUserId,
                        processedItems,
                    )

                val expectedIds =
                    batch.map { it.snippetId }.toSet()

                val appliedIds =
                    acknowledgement.applied.toSet()

                if (
                    acknowledgement.conflicts.isNotEmpty() ||
                    appliedIds != expectedIds ||
                    acknowledgement.applied.size != expectedIds.size
                ) {
                    return failure(
                        "The API did not confirm every submitted snippet.",
                        currentUserId,
                        processedItems,
                    )
                }

                ensureSameSession(startingToken, currentUserId, processedItems)?.let {
                    return it
                }

                for (snippet in batch) {
                    if (!snippetRepository.markSyncedIfUnchanged(snippet)) {
                        changedDuringUpload = true
                    }
                }

                processedItems += batch.size

                publishProgress(
                    processed = processedItems,
                    total = totalItems,
                    currentEntity = "Snippets",
                )
            }

            ensureSameSession(startingToken, currentUserId, processedItems)?.let { return it }
            snippetRepository.refreshFromApi()

            // ---- Financial transactions ----------------------------------
            for (batch in pendingTransactions.chunked(100)) {
                ensureSameSession(startingToken, currentUserId, processedItems)?.let {
                    return it
                }

                val operations = batch.map { transaction ->
                    SyncOperation(
                        entity = "transaction",
                        action = "upsert",
                        payload = json.encodeToString(
                            TransactionSyncPayload(
                                transactionId = transaction.transactionId,
                                userId = transaction.userId,
                                type = transaction.type,
                                amount = transaction.amount,
                                currency = transaction.currency,
                                category = transaction.category,
                                description = transaction.description,
                                clientOrFrom = transaction.clientOrFrom,
                                relatedProject = transaction.relatedProject,
                                paymentMethod = transaction.paymentMethod,
                                referenceNumber = transaction.referenceNumber,
                                isRecurring = transaction.isRecurring,
                                recurringFrequency = transaction.recurringFrequency,
                                nextOccurrenceDate = transaction.nextOccurrenceDate,
                                attachmentFileName = transaction.attachmentFileName,
                                attachmentBase64 = transaction.attachmentBase64,
                                transactionDate = transaction.transactionDate,
                                createdAt = transaction.createdAt,
                            )
                        ),
                        clientTimestamp = transaction.createdAt,
                    )
                }

                val response = api.syncBatch(
                    SyncBatchRequest(operations)
                )

                if (!response.isSuccessful) {
                    return responseFailure(
                        response.code(),
                        "transaction",
                        currentUserId,
                        processedItems,
                    )
                }

                val acknowledgement = response.body()
                    ?: return failure(
                        "The API returned no transaction sync acknowledgement.",
                        currentUserId,
                        processedItems,
                    )

                val expectedIds =
                    batch.map { it.transactionId }.toSet()

                val appliedIds =
                    acknowledgement.applied.toSet()

                if (
                    acknowledgement.conflicts.isNotEmpty() ||
                    appliedIds != expectedIds ||
                    acknowledgement.applied.size != expectedIds.size
                ) {
                    return failure(
                        "The API did not confirm every submitted transaction.",
                        currentUserId,
                        processedItems,
                    )
                }

                ensureSameSession(startingToken, currentUserId, processedItems)?.let {
                    return it
                }

                for (transaction in batch) {
                    transactionRepository.markSynced(
                        transaction.transactionId
                    )
                }

                processedItems += batch.size

                publishProgress(
                    processed = processedItems,
                    total = totalItems,
                    currentEntity = "Transactions",
                )
            }

            // A snippet modified while its previous version was uploading must
            // remain pending and be retried.
            if (changedDuringUpload) {
                Result.retry()
            } else {
                success(itemsSynced = processedItems, userId = user.userId)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: IOException) {
            Result.retry()
        } catch (error: Exception) {
            failure(
                "Synchronisation failed. Local records remain available.",
                currentUserId,
                processedItems,
            )
        }
    }

    private suspend fun ensureSameSession(
        startingToken: String,
        userId: String?,
        itemsSynced: Int,
    ): Result? {
        return if (tokenManager.getAccessToken() != startingToken) {
            failure(
                "The account changed during synchronisation.",
                userId,
                itemsSynced,
            )
        } else {
            null
        }
    }

    private suspend fun responseFailure(
        statusCode: Int,
        entityName: String,
        userId: String?,
        itemsSynced: Int,
    ): Result {
        return when (statusCode) {
            408, 429 -> Result.retry()

            in 500..599 -> Result.retry()

            401, 403 -> failure(
                "The API rejected the session or $entityName ownership.",
                userId,
                itemsSynced,
            )

            409 -> failure(
                "A $entityName conflicts with its server version.",
                userId,
                itemsSynced,
            )

            else -> failure(
                "The API rejected the $entityName sync request ($statusCode).",
                userId,
                itemsSynced,
            )
        }
    }

    private suspend fun publishProgress(
        processed: Int,
        total: Int,
        currentEntity: String,
    ) {
        setProgress(
            workDataOf(
                KEY_PROCESSED_ITEMS to processed,
                KEY_TOTAL_ITEMS to total,
                KEY_CURRENT_ENTITY to currentEntity,
            )
        )
    }

    private suspend fun success(itemsSynced: Int, userId: String): Result {
        syncLogDao.insert(
            SyncLogEntity(
                logId = IdGenerator.newId(),
                userId = userId,
                status = "COMPLETED",
                itemsSynced = itemsSynced,
                errorMessage = null,
                timestamp = System.currentTimeMillis(),
            )
        )
        return Result.success(workDataOf(KEY_COMPLETED_AT to System.currentTimeMillis()))
    }

    private suspend fun failure(message: String, userId: String?, itemsSynced: Int = 0): Result {
        if (userId != null) {
            syncLogDao.insert(
                SyncLogEntity(
                    logId = IdGenerator.newId(),
                    userId = userId,
                    status = "FAILED",
                    itemsSynced = itemsSynced,
                    errorMessage = message,
                    timestamp = System.currentTimeMillis(),
                )
            )
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
