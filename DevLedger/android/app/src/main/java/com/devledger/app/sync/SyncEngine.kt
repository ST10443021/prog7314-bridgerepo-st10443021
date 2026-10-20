package com.devledger.app.sync

import com.devledger.app.data.local.dao.DeletedDao
import com.devledger.app.data.local.dao.SnippetDao
import com.devledger.app.data.local.entity.DeletedEntity
import com.devledger.app.data.local.entity.SyncStatus
import com.devledger.app.data.remote.DevLedgerApiService
import com.devledger.app.data.remote.auth.TokenManager
import com.devledger.app.data.remote.dto.SyncBatchRequest
import com.devledger.app.data.remote.dto.SyncBatchResponse
import com.devledger.app.data.repository.*
import com.devledger.app.util.DevLedgerResult
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import retrofit2.HttpException

data class SyncProgress(val userId: String, val processed: Int, val total: Int, val phase: String)
data class SyncOutcome(val status: String, val userId: String?, val uploaded: Int, val message: String? = null,
    val finishedAt: Long = System.currentTimeMillis())

/** One shared engine serialises immediate/background runs. Room writes remain
 * version checked, and success requires all downloads plus an empty outbox. */
@Singleton
class SyncEngine @Inject constructor(
    private val api: DevLedgerApiService,
    private val snippetRepository: SnippetRepository,
    private val transactionRepository: TransactionRepository,
    private val folderRepository: FolderRepository,
    private val userTagRepository: UserTagRepository,
    private val authRepository: AuthRepository,
    private val tokenManager: TokenManager,
    private val deletedDao: DeletedDao,
    private val snippetDao: SnippetDao,
) {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun run(progress: suspend (SyncProgress) -> Unit): SyncOutcome = mutex.withLock {
        var userId: String? = null
        var uploaded = 0
        try {
            val token = tokenManager.getAccessToken()?.takeIf { it.isNotBlank() }
                ?: return@withLock SyncOutcome("FAILED", null, 0, "Sign in before synchronising.")
            val account = authRepository.currentUser.first()?.userId
                ?: return@withLock SyncOutcome("FAILED", null, 0, "The current account could not be loaded.")
            userId = account
            ensureSession(token, account)
            val plan = pendingPlan(account)
            val total = plan.size + DOWNLOAD_STEPS
            progress(SyncProgress(account, 0, total, "Uploading"))
            for (batch in plan.chunked(100)) {
                ensureSession(token, account)
                val response = api.syncBatch(SyncBatchRequest(batch.map { it.operation }))
                ensureSession(token, account)
                if (response.code() == 409) {
                    val body = response.errorBody()?.string() ?: error("Missing conflict acknowledgement.")
                    val conflicts = SyncBatchPlanner.conflictedSnippets(batch, json.decodeFromString<SyncBatchResponse>(body))
                    var held = 0
                    for (row in conflicts) {
                        ensureSession(token, account)
                        if (snippetDao.flagConflictIfUnchanged(row)) held++
                    }
                    return@withLock if (held > 0) SyncOutcome("CONFLICT", account, uploaded,
                        "Local drafts were retained. Open Sync Status and choose Keep both versions.")
                    else SyncOutcome("RETRY", account, uploaded, "A local edit changed during upload; retrying its current version.")
                }
                if (!response.isSuccessful) throw HttpException(response)
                val acknowledgement = response.body() ?: error("No acknowledgement received.")
                SyncBatchPlanner.validateAcknowledgement(batch, acknowledgement)
                for (upload in batch) {
                    ensureSession(token, account)
                    when (val source = upload.source) {
                        is SyncUploadSource.Snippet -> snippetRepository.markSyncedIfUnchanged(source.row)
                        is SyncUploadSource.Transaction -> transactionRepository.markSyncedIfUnchanged(source.row)
                        is SyncUploadSource.Folder -> folderRepository.markSyncedIfUnchanged(source.row)
                        is SyncUploadSource.Tag -> userTagRepository.markSyncedIfUnchanged(source.row)
                        is SyncUploadSource.SnippetDelete -> snippetRepository.acknowledgeDelete(account, upload.id)
                        is SyncUploadSource.Delete -> deletedDao.acknowledge(account, source.row.entityType, upload.id, source.row.deletedAt)
                    }
                }
                uploaded += batch.size
                progress(SyncProgress(account, uploaded, total, "Uploading"))
            }
            var processed = uploaded
            suspend fun downloaded() {
                ensureSession(token, account)
                processed++
                progress(SyncProgress(account, processed, total, "Downloading"))
            }
            progress(SyncProgress(account, processed, total, "Downloading"))
            applyServerDeletions(token, account); downloaded()
            folderRepository.refreshFromApi().requireDownloaded("folders"); downloaded()
            userTagRepository.refreshFromApi().requireDownloaded("tags"); downloaded()
            snippetRepository.refreshFromApi(); downloaded()
            transactionRepository.refreshFromApi(); downloaded()
            applyServerDeletions(token, account); downloaded()
            val remaining = pendingPlan(account)
            ensureSession(token, account)
            when {
                snippetDao.conflictCount(account) > 0 -> SyncOutcome("CONFLICT", account, uploaded,
                    "Unresolved local drafts remain. Choose Keep both versions in Sync Status.")
                remaining.isNotEmpty() -> SyncOutcome("PARTIAL", account, uploaded,
                    "New local changes remain queued. Another sync attempt is required.")
                else -> SyncOutcome("COMPLETED", account, uploaded)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (http: HttpException) {
            val retry = http.code() in setOf(408, 429) || http.code() in 500..599
            SyncOutcome(if (retry) "RETRY" else "FAILED", userId, uploaded,
                "Sync request failed (${http.code()}). Unacknowledged work was retained.")
        } catch (_: IOException) {
            SyncOutcome("RETRY", userId, uploaded, "Network interrupted. Waiting to retry.")
        } catch (error: Exception) {
            SyncOutcome("FAILED", userId, uploaded, error.message ?: "Synchronisation failed.")
        }
    }

    private suspend fun pendingPlan(userId: String) = SyncBatchPlanner.plan(userId,
        snippetRepository.getPendingSyncForUser(userId), transactionRepository.getPendingSyncForUser(userId),
        folderRepository.getPendingSyncForUser(userId), userTagRepository.getPendingSyncForUser(userId),
        snippetRepository.getPendingDeletesForUser(userId), deletedDao.getForUser(userId))

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
            "Account session changed. Unacknowledged work was retained."
        }
    }
    private companion object { const val DOWNLOAD_STEPS = 6 }
}
