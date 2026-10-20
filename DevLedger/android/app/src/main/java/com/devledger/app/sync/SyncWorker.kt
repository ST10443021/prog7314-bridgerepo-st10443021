package com.devledger.app.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.devledger.app.data.local.dao.DeletedDao
import com.devledger.app.data.local.dao.SyncLogDao
import com.devledger.app.data.local.entity.DeletedEntity
import com.devledger.app.data.local.entity.SyncLogEntity
import com.devledger.app.data.local.entity.SyncStatus
import com.devledger.app.data.remote.DevLedgerApiService
import com.devledger.app.data.remote.auth.TokenManager
import com.devledger.app.data.remote.dto.SyncBatchRequest
import com.devledger.app.data.repository.*
import com.devledger.app.util.DevLedgerResult
import com.devledger.app.util.IdGenerator
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import retrofit2.HttpException

/**
 * Uploads dependency-ordered batches and acknowledges only the submitted versions.
 * Room remains authoritative for UI; deletion markers survive acknowledgement.
 * Reference: https://developer.android.com/topic/architecture/data-layer/offline-first
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
    private val notificationRepository: NotificationRepository,
    private val tokenManager: TokenManager,
    private val syncLogDao: SyncLogDao,
    private val deletedDao: DeletedDao,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        var userId: String? = null
        var processed = 0
        return try {
            val token = tokenManager.getAccessToken()?.takeIf { it.isNotBlank() }
                ?: return failure("Sign in before synchronising.", null)
            val user = authRepository.currentUser.first()
                ?: return failure("The current account could not be loaded.", null)
            userId = user.userId
            ensureSession(token, user.userId)
            val plan = SyncBatchPlanner.plan(
                user.userId,
                snippetRepository.getPendingSyncForUser(user.userId),
                transactionRepository.getPendingSyncForUser(user.userId),
                folderRepository.getPendingSyncForUser(user.userId),
                userTagRepository.getPendingSyncForUser(user.userId),
                snippetRepository.getPendingDeletesForUser(user.userId),
                deletedDao.getForUser(user.userId),
            )
            var changedDuringUpload = false
            for (batch in plan.chunked(100)) {
                ensureSession(token, user.userId)
                val response = api.syncBatch(SyncBatchRequest(batch.map { it.operation }))
                ensureSession(token, user.userId)
                if (!response.isSuccessful) throw HttpException(response)
                val acknowledgement = response.body() ?: error("No acknowledgement received.")
                SyncBatchPlanner.validateAcknowledgement(batch, acknowledgement)
                // Match each acknowledgement to its entity/action and original snapshot.
                for (upload in batch) {
                    ensureSession(token, user.userId)
                    when (val source = upload.source) {
                        is SyncUploadSource.Snippet -> if (!snippetRepository.markSyncedIfUnchanged(source.row)) changedDuringUpload = true
                        is SyncUploadSource.Transaction -> if (!transactionRepository.markSyncedIfUnchanged(source.row)) changedDuringUpload = true
                        is SyncUploadSource.Folder -> if (!folderRepository.markSyncedIfUnchanged(source.row)) changedDuringUpload = true
                        is SyncUploadSource.Tag -> if (!userTagRepository.markSyncedIfUnchanged(source.row)) changedDuringUpload = true
                        is SyncUploadSource.SnippetDelete -> snippetRepository.acknowledgeDelete(user.userId, upload.id)
                        is SyncUploadSource.Delete -> deletedDao.acknowledge(user.userId, source.row.entityType, upload.id, source.row.deletedAt)
                    }
                }
                processed += batch.size
                publishProgress(processed, plan.size, "Uploading")
            }
            downloadLatest(token, user.userId)
            if (changedDuringUpload) {
                Log.i("DevLedgerSync", "An uploaded record changed locally; retaining pending work for retry")
                Result.retry()
            } else success(processed, user.userId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (http: HttpException) {
            if (http.code() in setOf(408, 429) || http.code() in 500..599) Result.retry()
            else failure("The API rejected sync (${http.code()}). Pending work was retained.", userId, processed)
        } catch (_: IOException) {
            Log.i("DevLedgerSync", "Network interrupted; pending work retained for retry")
            Result.retry()
        } catch (error: Exception) {
            failure(error.message ?: "Synchronisation failed.", userId, processed)
        }
    }

    private suspend fun downloadLatest(token: String, userId: String) {
        publishProgress(0, 1, "Downloading")
        applyServerDeletions(token, userId)
        ensureSession(token, userId)
        folderRepository.refreshFromApi().requireDownloaded("folders")
        ensureSession(token, userId)
        userTagRepository.refreshFromApi().requireDownloaded("tags")
        ensureSession(token, userId)
        snippetRepository.refreshFromApi()
        ensureSession(token, userId)
        transactionRepository.refreshFromApi()
        // Catch a deletion committed while a list/snapshot request was in flight.
        applyServerDeletions(token, userId)
        ensureSession(token, userId)
    }

    private suspend fun applyServerDeletions(token: String, userId: String) {
        ensureSession(token, userId)
        val response = api.getSyncDeletions()
        ensureSession(token, userId)
        if (!response.isSuccessful) throw HttpException(response)
        for (marker in response.body() ?: error("The API returned no deletion list.")) {
            ensureSession(token, userId)
            deletedDao.applyRemoteDeletion(DeletedEntity(marker.entityId, userId, marker.entityType,
                syncStatus = SyncStatus.SYNCED))
        }
    }

    private fun DevLedgerResult<Unit>.requireDownloaded(entity: String) {
        if (this is DevLedgerResult.Error) {
            if (isOffline) throw IOException("Could not download $entity.")
            error("Could not download $entity: $message")
        }
    }

    private suspend fun ensureSession(token: String, userId: String) {
        check(tokenManager.getAccessToken() == token && authRepository.currentUser.first()?.userId == userId) {
            "Account session changed. Pending work was retained."
        }
    }

    private suspend fun publishProgress(processed: Int, total: Int, entity: String) {
        setProgress(workDataOf(KEY_PROCESSED_ITEMS to processed, KEY_TOTAL_ITEMS to total, KEY_CURRENT_ENTITY to entity))
    }

    private suspend fun success(items: Int, userId: String): Result {
        syncLogDao.insert(SyncLogEntity(IdGenerator.newId(), userId, "COMPLETED", items, null, System.currentTimeMillis()))
        return Result.success(workDataOf(KEY_COMPLETED_AT to System.currentTimeMillis()))
    }

    private suspend fun failure(message: String, userId: String?, items: Int = 0): Result {
        if (userId != null) syncLogDao.insert(SyncLogEntity(IdGenerator.newId(), userId, "FAILED", items, message, System.currentTimeMillis()))
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
