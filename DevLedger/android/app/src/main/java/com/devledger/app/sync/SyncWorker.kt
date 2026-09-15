package com.devledger.app.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.devledger.app.data.remote.DevLedgerApiService
import com.devledger.app.data.remote.auth.TokenManager
import com.devledger.app.data.remote.dto.SyncBatchRequest
import com.devledger.app.data.remote.dto.SyncOperation
import com.devledger.app.data.repository.AuthRepository
import com.devledger.app.data.repository.SnippetRepository
import com.devledger.app.data.repository.TransactionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.devledger.app.data.remote.dto.SnippetSyncPayload
import com.devledger.app.data.remote.dto.TransactionSyncPayload

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
) : CoroutineWorker(context, params) {

    private val json = Json {
        encodeDefaults = true
    }

    override suspend fun doWork(): Result {
        return try {
            val startingToken = tokenManager.getAccessToken()
                ?.takeIf { it.isNotBlank() }
                ?: return failure("Sign in before synchronising.")

            val user = authRepository.currentUser.first()
                ?: return failure("The current account could not be loaded.")

            val pendingSnippets =
                snippetRepository.getPendingSyncForUser(user.userId)

            val pendingTransactions =
                transactionRepository.getPendingSyncForUser(user.userId)

            if (pendingSnippets.isEmpty() && pendingTransactions.isEmpty()) {
                return Result.success()
            }

            var changedDuringUpload = false

            // ---- Snippets -------------------------------------------------
            for (batch in pendingSnippets.chunked(100)) {
                ensureSameSession(startingToken)?.let {
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
                                tags = snippet.tagsCsv.split(",").map(String::trim).filter(String::isNotEmpty),
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
                    )
                }

                val acknowledgement = response.body()
                    ?: return failure(
                        "The API returned no snippet sync acknowledgement."
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
                        "The API did not confirm every submitted snippet."
                    )
                }

                ensureSameSession(startingToken)?.let {
                    return it
                }

                for (snippet in batch) {
                    if (!snippetRepository.markSyncedIfUnchanged(snippet)) {
                        changedDuringUpload = true
                    }
                }
            }

            // ---- Financial transactions ----------------------------------
            for (batch in pendingTransactions.chunked(100)) {
                ensureSameSession(startingToken)?.let {
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
                    )
                }

                val acknowledgement = response.body()
                    ?: return failure(
                        "The API returned no transaction sync acknowledgement."
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
                        "The API did not confirm every submitted transaction."
                    )
                }

                ensureSameSession(startingToken)?.let {
                    return it
                }

                for (transaction in batch) {
                    transactionRepository.markSynced(
                        transaction.transactionId
                    )
                }
            }

            // A snippet modified while its previous version was uploading must
            // remain pending and be retried.
            if (changedDuringUpload) {
                Result.retry()
            } else {
                Result.success()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: IOException) {
            Result.retry()
        } catch (error: Exception) {
            failure(
                "Synchronisation failed. Local records remain available."
            )
        }
    }

    private fun ensureSameSession(
        startingToken: String,
    ): Result? {
        return if (tokenManager.getAccessToken() != startingToken) {
            failure(
                "The account changed during synchronisation."
            )
        } else {
            null
        }
    }

    private fun responseFailure(
        statusCode: Int,
        entityName: String,
    ): Result {
        return when (statusCode) {
            408, 429 -> Result.retry()

            in 500..599 -> Result.retry()

            401, 403 -> failure(
                "The API rejected the session or $entityName ownership."
            )

            409 -> failure(
                "A $entityName conflicts with its server version."
            )

            else -> failure(
                "The API rejected the $entityName sync request ($statusCode)."
            )
        }
    }

    private fun failure(message: String): Result =
        Result.failure(
            workDataOf("sync_error" to message)
        )

    companion object {
        const val WORK_NAME = "devledger_sync_work"
    }
}