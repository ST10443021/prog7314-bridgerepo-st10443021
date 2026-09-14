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
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Uploads pending snippets belonging to the current account.
 * Marks only acknowledged, unchanged local records as synced.
 *
 * Transaction uploads remain pending until backend support is implemented.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val api: DevLedgerApiService,
    private val snippetRepository: SnippetRepository,
    private val authRepository: AuthRepository,
    private val tokenManager: TokenManager,
) : CoroutineWorker(context, params) {

    private val json = Json { encodeDefaults = true }

    override suspend fun doWork(): Result {
        return try {
            val startingToken = tokenManager.getAccessToken()
                ?.takeIf { it.isNotBlank() }
                ?: return failure("Sign in before synchronising.")

            val user = authRepository.currentUser.first()
                ?: return failure("The current account could not be loaded.")

            val pending = snippetRepository.getPendingSyncForUser(user.userId)

            if (pending.isEmpty()) {
                return Result.success()
            }

            var changedDuringUpload = false

            // Matches the API's maximum batch size.
            for (batch in pending.chunked(100)) {
                if (tokenManager.getAccessToken() != startingToken) {
                    return failure("The account changed. Retry synchronisation.")
                }

                val operations = batch.map { snippet ->
                    SyncOperation(
                        entity = "snippet",
                        action = "upsert",
                        payload = json.encodeToString(snippet),
                        clientTimestamp = snippet.updatedAt,
                    )
                }

                val response = api.syncBatch(SyncBatchRequest(operations))

                if (!response.isSuccessful) {
                    return when (response.code()) {
                        408, 429 -> Result.retry()
                        in 500..599 -> Result.retry()
                        401, 403 -> failure(
                            "The API rejected the session or snippet ownership."
                        )
                        409 -> failure(
                            "A snippet conflicts with its server version."
                        )
                        else -> failure(
                            "The API rejected the sync request (${response.code()})."
                        )
                    }
                }

                val acknowledgement = response.body()
                    ?: return failure("The API returned no sync acknowledgement.")

                val expectedIds = batch.map { it.snippetId }.toSet()
                val appliedIds = acknowledgement.applied.toSet()

                // An HTTP 200 alone does not prove that records were saved.
                if (acknowledgement.conflicts.isNotEmpty() ||
                    appliedIds != expectedIds ||
                    acknowledgement.applied.size != expectedIds.size
                ) {
                    return failure(
                        "The API did not confirm every submitted snippet."
                    )
                }

                if (tokenManager.getAccessToken() != startingToken) {
                    return failure("The account changed during synchronisation.")
                }

                for (snippet in batch) {
                    if (!snippetRepository.markSyncedIfUnchanged(snippet)) {
                        changedDuringUpload = true
                    }
                }
            }

            // A newer local edit must be uploaded in another attempt.
            if (changedDuringUpload) Result.retry() else Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: IOException) {
            Result.retry()
        } catch (error: Exception) {
            failure("Synchronisation failed. Local records remain available.")
        }
    }

    private fun failure(message: String): Result =
        Result.failure(workDataOf("sync_error" to message))

    companion object {
        const val WORK_NAME = "devledger_sync_work"
    }
}