package com.devledger.app.data.repository

import com.devledger.app.data.local.dao.SnippetDao
import com.devledger.app.data.local.entity.SnippetEntity
import com.devledger.app.data.local.entity.SyncStatus
import com.devledger.app.util.IdGenerator
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline-first snippet vault repository. All writes land in Room immediately (so the UI
 * never blocks on network) and are flagged PENDING for [com.devledger.app.sync.SyncWorker]
 * to push to the REST API once connectivity returns — the pattern described in Section 5
 * ("Offline Mode with Synchronisation") of the Part 1 Planning and Design document.
 */
@Singleton
class SnippetRepository @Inject constructor(
    private val dao: SnippetDao,
) {
    fun observeSnippets(query: String): Flow<List<SnippetEntity>> =
        if (query.isBlank()) dao.observeActiveSnippets() else dao.searchSnippets(query)

    fun observeArchived(): Flow<List<SnippetEntity>> = dao.observeArchivedSnippets()
    fun observeActiveCount(): Flow<Int> = dao.observeActiveCount()

    suspend fun getById(id: String): SnippetEntity? = dao.getById(id)

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
        val now = System.currentTimeMillis()
        val snippet = SnippetEntity(
            snippetId = id ?: IdGenerator.newId(),
            userId = userId,
            folderId = folderId,
            title = title,
            content = content,
            language = language,
            description = description,
            tagsCsv = tags.joinToString(","),
            createdAt = now,
            updatedAt = now,
            syncStatus = SyncStatus.PENDING,
        )
        dao.upsert(snippet)
        return snippet
    }

    suspend fun toggleFavourite(id: String, favourite: Boolean) = dao.setFavourite(id, favourite)
    suspend fun archive(id: String, archived: Boolean = true) = dao.setArchived(id, archived)
    suspend fun delete(snippet: SnippetEntity) = dao.delete(snippet)
    suspend fun getPendingSync() = dao.getPendingSync()
    suspend fun markSynced(id: String) = dao.updateSyncStatus(id, SyncStatus.SYNCED)
}
