package com.devledger.app.data.repository

import com.devledger.app.util.localization.UiText

import com.devledger.app.R

import com.devledger.app.data.local.dao.SnippetDao
import com.devledger.app.data.local.entity.SnippetEntity
import com.devledger.app.data.local.entity.SyncStatus
import com.devledger.app.util.IdGenerator
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton
import com.devledger.app.data.local.dao.UserDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import android.content.Context
import android.util.Log
import com.devledger.app.data.local.dao.DeletedDao
import com.devledger.app.data.local.entity.DeletedEntity
import com.devledger.app.sync.SyncScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import com.devledger.app.data.remote.DevLedgerApiService
import com.devledger.app.data.remote.auth.TokenManager
import retrofit2.HttpException

/**
 * Offline-first snippet vault repository. All writes land in Room immediately (so the UI
 * never blocks on network) and are flagged PENDING for [com.devledger.app.sync.SyncWorker]
 * to push to the REST API once connectivity returns.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
/**
 * Reference scope: repository separation and Room persistence.
 * (Google Developers, n.d.a; Google Developers, n.d.b). See REFERENCES-CODE.md for full entries.
 */
class SnippetRepository @Inject constructor(
    private val dao: SnippetDao,
    private val deletedDao: DeletedDao,
    private val userDao: UserDao,
    private val notificationRepository: NotificationRepository,
    private val settingsRepository: SettingsRepository,
    private val api: DevLedgerApiService,
    private val tokenManager: TokenManager,
    @ApplicationContext private val context: Context,
) {
    fun observeSnippets(query: String): Flow<List<SnippetEntity>> =
        userDao.observeCurrentUser().flatMapLatest { user ->
            if (user == null) {
                flowOf(emptyList<SnippetEntity>())
            } else {
                dao.observeSnippetsForUser(user.userId, query.trim())
            }
        }

    /**
     * Downloads a full snapshot of snippets (active + deleted IDs) for the current account.
     * This ensures local cleanup of snippets deleted on other devices.
     */
    suspend fun refreshFromApi() {
        val startingToken = tokenManager.getAccessToken()
            ?.takeIf { it.isNotBlank() }
            ?: return

        val user = userDao.getCurrentUser() ?: return

        check(tokenManager.getAccessToken() == startingToken) { "Account session changed." }

        val response = api.getSnippetSnapshot()

        if (!response.isSuccessful) {
            throw HttpException(response)
        }

        val snapshot = response.body() ?: error("The API returned no snippet list.")

        if (tokenManager.getAccessToken() != startingToken) return

        val entities = snapshot.snippets.map { remote ->
            SnippetEntity(
                snippetId = remote.snippetId,
                userId = user.userId,
                folderId = remote.folderId,
                title = remote.title,
                content = remote.content,
                language = remote.language,
                description = remote.description,
                tagsCsv = remote.tags.joinToString(","),
                isFavourite = remote.isFavourite,
                isArchived = remote.isArchived,
                createdAt = remote.createdAt,
                updatedAt = remote.updatedAt,
                syncStatus = SyncStatus.SYNCED,
            )
        }

        dao.mergeRemoteSnippets(user.userId, entities, snapshot.deletedIds)
    }

    fun observeArchived(): Flow<List<SnippetEntity>> =
        userDao.observeCurrentUser().flatMapLatest { user ->
            if (user == null) {
                flowOf(emptyList<SnippetEntity>())
            } else {
                dao.observeArchivedForUser(user.userId)
            }
        }

    fun observeActiveCount(): Flow<Int> =
        userDao.observeCurrentUser().flatMapLatest { user ->
            if (user == null) {
                flowOf(0)
            } else {
                dao.observeActiveCountForUser(user.userId)
            }
        }

    suspend fun getById(id: String): SnippetEntity? {
        val user = userDao.getCurrentUser() ?: return null
        return dao.getById(id)?.takeIf { it.userId == user.userId && it.syncStatus !in listOf(SyncStatus.DELETE_PENDING, SyncStatus.DELETED) }
    }

    suspend fun createOrUpdate(
        userId: String,
        id: String? = null,
        title: String,
        content: String,
        language: String,
        description: String?,
        tags: List<String>,
        folderId: String? = null,
    ): SnippetEntity {
        require(userId.isNotBlank()) { "Sign in before saving a snippet." }
        require(title.isNotBlank()) { "Enter a snippet title." }
        require(content.isNotBlank()) { "Enter some code or configuration content." }

        val existing = id?.let { dao.getById(it) }
        require(existing == null || existing.userId == userId) { "Ownership mismatch." }

        val now = System.currentTimeMillis()
        val updatedAt = if (existing != null) maxOf(now, existing.updatedAt + 1) else now
        val tagsCsv = tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(",")

        val snippet = if (existing != null) {
            existing.copy(
                folderId = folderId, title = title.trim(), content = content,
                language = language.trim(), description = description?.trim()?.takeIf { it.isNotEmpty() },
                tagsCsv = tagsCsv, updatedAt = updatedAt,
                syncStatus = if (existing.syncStatus == SyncStatus.CONFLICT) SyncStatus.CONFLICT else SyncStatus.PENDING,
            )
        } else {
            SnippetEntity(
                snippetId = id ?: IdGenerator.newId(), userId = userId, folderId = folderId,
                title = title.trim(), content = content, language = language.trim(),
                description = description?.trim()?.takeIf { it.isNotEmpty() },
                tagsCsv = tagsCsv, createdAt = now, updatedAt = updatedAt,
                syncStatus = SyncStatus.PENDING,
            )
        }

        dao.saveUnlessDeleted(snippet)

        if (existing == null) {
            notificationRepository.notify(
                title = UiText.Resource(R.string.lang_snippet_created),
                message = UiText.Resource(R.string.lang_snippet_created_body, listOf(snippet.title)),
                type = "SNIPPET"
            )
        }

        queueSync()
        return snippet
    }

    suspend fun toggleFavourite(id: String, favourite: Boolean) {
        val snippet = getById(id) ?: return
        dao.setFavourite(snippet.snippetId, favourite)
        queueSync()
    }

    suspend fun archive(id: String, archived: Boolean = true) {
        val snippet = getById(id) ?: return
        dao.setArchived(snippet.snippetId, archived)
        queueSync()
    }

    suspend fun delete(snippet: SnippetEntity) {
        deleteArchived(listOf(snippet.snippetId))
    }

    suspend fun deleteArchived(ids: List<String>): Int {
        val user = userDao.getCurrentUser() ?: error("Sign in before deleting snippets.")
        val count = dao.queueArchivedDeletes(user.userId, ids)
        if (count > 0) {
            notificationRepository.notify(
                title = UiText.Resource(R.string.lang_snippet_deleted),
                message = UiText.Quantity(R.plurals.lang_snippets_queued_delete, count),
                type = "SNIPPET"
            )
            queueSync()
        }
        return count
    }

    suspend fun getPendingDeletesForUser(userId: String): List<SnippetEntity> =
        dao.getPendingDeletesForUser(userId)

    suspend fun acknowledgeDelete(userId: String, id: String) =
        dao.acknowledgeDeletion(userId, id)

    suspend fun getPendingSync() = dao.getPendingSync()
    suspend fun markSynced(id: String) = dao.updateSyncStatus(id, SyncStatus.SYNCED)

    suspend fun getPendingSyncForUser(userId: String): List<SnippetEntity> =
        dao.getPendingSyncForUser(userId)

    suspend fun markSyncedIfUnchanged(snippet: SnippetEntity): Boolean =
        dao.markSyncedIfUnchanged(
            id = snippet.snippetId,
            userId = snippet.userId,
            uploadedTimestamp = snippet.updatedAt,
        ) == 1

    private suspend fun queueSync() {
        try {
            val wifiOnly = settingsRepository.wifiOnlySync.first()
            SyncScheduler.schedulePeriodicSync(context, wifiOnly)
            SyncScheduler.syncNow(context, wifiOnly)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e("SnippetRepository", "Could not schedule snippet sync.", error)
        }
    }
}
