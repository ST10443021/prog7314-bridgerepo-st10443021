package com.devledger.app.data.local.dao

import androidx.room.*
import com.devledger.app.data.local.entity.SnippetEntity
import kotlinx.coroutines.flow.Flow

@Dao
/**
 * Reference scope: Room persistence.
 * (Google Developers, n.d.b). See REFERENCES-CODE.md for full entries.
 */
interface SnippetDao {
    @Query("SELECT * FROM snippets WHERE isArchived = 0 AND syncStatus NOT IN ('DELETE_PENDING', 'DELETED') ORDER BY updatedAt DESC")
    fun observeActiveSnippets(): Flow<List<SnippetEntity>>

    @Query("SELECT * FROM snippets WHERE isArchived = 1 AND syncStatus NOT IN ('DELETE_PENDING', 'DELETED') ORDER BY updatedAt DESC")
    fun observeArchivedSnippets(): Flow<List<SnippetEntity>>

    @Query("""
        SELECT * FROM snippets
        WHERE isArchived = 0 AND syncStatus NOT IN ('DELETE_PENDING', 'DELETED')
        AND (title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%' OR tagsCsv LIKE '%' || :query || '%')
        ORDER BY updatedAt DESC
    """)
    fun searchSnippets(query: String): Flow<List<SnippetEntity>>

    @Query("SELECT * FROM snippets WHERE userId = :userId AND syncStatus != 'DELETED' ORDER BY updatedAt DESC")
    fun observeForSync(userId: String): Flow<List<SnippetEntity>>

    @Query("SELECT COUNT(*) FROM snippets WHERE userId = :userId AND syncStatus = 'CONFLICT'")
    suspend fun conflictCount(userId: String): Int

    @Transaction
    suspend fun flagConflictIfUnchanged(uploaded: SnippetEntity): Boolean {
        if (uploaded.syncStatus != "PENDING" || getById(uploaded.snippetId) != uploaded) return false
        upsert(uploaded.copy(syncStatus = "CONFLICT"))
        return true
    }

    /** Atomic, explicit recovery: preserve the local content under a fresh ID and
     * install the downloaded server version (or its permanent-deletion marker). */
    @Transaction
    suspend fun keepBothConflictVersions(
        expected: SnippetEntity, remote: SnippetEntity?, serverDeleted: Boolean,
        copyId: String, now: Long,
    ): String {
        check(expected.syncStatus == "CONFLICT" && getById(expected.snippetId) == expected) {
            "The snippet changed during recovery. Review it and try again."
        }
        require(copyId != expected.snippetId && getById(copyId) == null)
        require((remote != null) != serverDeleted) { "A confirmed server version or deletion is required." }
        require(remote == null || (remote.snippetId == expected.snippetId && remote.userId == expected.userId))
        val copyFolder = expected.folderId?.takeUnless { hasDeletedFolder(expected.userId, it) }
        upsert(expected.copy(snippetId = copyId, folderId = copyFolder, createdAt = now,
            updatedAt = now, syncStatus = "PENDING"))
        if (serverDeleted) markDeleted(expected.userId, expected.snippetId)
        else {
            val server = requireNotNull(remote)
            val folder = server.folderId?.takeUnless { hasDeletedFolder(expected.userId, it) }
            upsert(server.copy(folderId = folder, syncStatus = "SYNCED"))
        }
        return copyId
    }

    @Query("SELECT * FROM snippets WHERE snippetId = :id LIMIT 1")
    suspend fun getById(id: String): SnippetEntity?

    @Query("SELECT * FROM snippets WHERE syncStatus = 'PENDING'")
    suspend fun getPendingSync(): List<SnippetEntity>

    @Upsert
    suspend fun upsert(snippet: SnippetEntity)

    @Query("UPDATE snippets SET syncStatus = :status WHERE snippetId = :id AND syncStatus = 'PENDING'")
    suspend fun updateSyncStatus(id: String, status: String)

    @Query("""
    UPDATE snippets
    SET isArchived = :archived,
        updatedAt = MAX(updatedAt + 1, :changedAt),
        syncStatus = CASE WHEN syncStatus = 'CONFLICT' THEN 'CONFLICT' ELSE 'PENDING' END
    WHERE snippetId = :id AND syncStatus NOT IN ('DELETE_PENDING', 'DELETED')
""")
    suspend fun setArchived(
        id: String,
        archived: Boolean,
        changedAt: Long = System.currentTimeMillis(),
    )

    @Query("""
    UPDATE snippets
    SET isFavourite = :favourite,
        updatedAt = MAX(updatedAt + 1, :changedAt),
        syncStatus = CASE WHEN syncStatus = 'CONFLICT' THEN 'CONFLICT' ELSE 'PENDING' END
    WHERE snippetId = :id AND syncStatus NOT IN ('DELETE_PENDING', 'DELETED')
""")
    suspend fun setFavourite(
        id: String,
        favourite: Boolean,
        changedAt: Long = System.currentTimeMillis(),
    )


    @Query("SELECT COUNT(*) FROM snippets WHERE isArchived = 0 AND syncStatus NOT IN ('DELETE_PENDING', 'DELETED')")
    fun observeActiveCount(): Flow<Int>

    @Query("""
    SELECT * FROM snippets
    WHERE userId = :userId AND syncStatus = 'PENDING'
    ORDER BY updatedAt ASC
""")
    suspend fun getPendingSyncForUser(userId: String): List<SnippetEntity>

    @Query("""
    UPDATE snippets
    SET syncStatus = 'SYNCED'
    WHERE snippetId = :id AND syncStatus NOT IN ('DELETE_PENDING', 'DELETED')
      AND userId = :userId
      AND updatedAt = :uploadedTimestamp
      AND syncStatus = 'PENDING'
""")
    suspend fun markSyncedIfUnchanged(
        id: String,
        userId: String,
        uploadedTimestamp: Long,
    ): Int

    @Query("""
    SELECT * FROM snippets
    WHERE userId = :userId
      AND isArchived = 0 AND syncStatus NOT IN ('DELETE_PENDING', 'DELETED')
      AND (
          :query = ''
          OR title LIKE '%' || :query || '%'
          OR content LIKE '%' || :query || '%'
          OR tagsCsv LIKE '%' || :query || '%'
      )
    ORDER BY updatedAt DESC
""")
    fun observeSnippetsForUser(
        userId: String,
        query: String,
    ): Flow<List<SnippetEntity>>

    @Query("""
    SELECT * FROM snippets
    WHERE userId = :userId AND isArchived = 1 AND syncStatus NOT IN ('DELETE_PENDING', 'DELETED')
    ORDER BY updatedAt DESC
""")
    fun observeArchivedForUser(userId: String): Flow<List<SnippetEntity>>

    @Query("""
    SELECT COUNT(*) FROM snippets
    WHERE userId = :userId AND isArchived = 0 AND syncStatus NOT IN ('DELETE_PENDING', 'DELETED')
""")
    fun observeActiveCountForUser(userId: String): Flow<Int>

    @Query("UPDATE snippets SET folderId = :newFolderId, syncStatus = CASE WHEN syncStatus = 'CONFLICT' THEN 'CONFLICT' ELSE 'PENDING' END, updatedAt = MAX(updatedAt + 1, :now) WHERE folderId = :oldFolderId")
    suspend fun reassignFolder(oldFolderId: String, newFolderId: String?, now: Long = System.currentTimeMillis())

    @Transaction
    suspend fun mergeRemoteSnippets(
        userId: String,
        remoteSnippets: List<SnippetEntity>,
        deletedIds: List<String> = emptyList(),
    ) {
        // Ghosting protection: Ensure items the server says are deleted remain marked as DELETED locally
        for (id in deletedIds) {
            // A conflict is an unresolved local draft; preserve it until the user
            // explicitly saves a copy, even if the server original was deleted.
            if (getById(id)?.syncStatus != "CONFLICT") markDeleted(userId, id)
        }

        for (source in remoteSnippets) {
            val remote = if (source.folderId != null && hasDeletedFolder(userId, source.folderId))
                source.copy(folderId = null) else source
            if (remote.snippetId in deletedIds) continue
            require(remote.userId == userId) { "The downloaded snippet belongs to a different account." }

            val local = getById(remote.snippetId)
            if (local == null) {
                upsert(remote)
            } else if (local.syncStatus == "SYNCED" && remote.updatedAt > local.updatedAt) {
                upsert(remote)
            }
        }
    }

    @Query("SELECT * FROM snippets WHERE userId = :userId AND syncStatus = 'DELETE_PENDING'")
    suspend fun getPendingDeletesForUser(userId: String): List<SnippetEntity>

    @Query("""
        UPDATE snippets SET title = '', content = '', language = '', description = NULL,
            tagsCsv = '', folderId = NULL, isFavourite = 0, isArchived = 1,
            syncStatus = 'DELETE_PENDING', updatedAt = MAX(updatedAt + 1, :changedAt)
        WHERE snippetId = :id AND userId = :userId AND isArchived = 1
            AND syncStatus NOT IN ('DELETE_PENDING', 'DELETED')
    """)
    suspend fun queueDelete(userId: String, id: String, changedAt: Long): Int

    @Transaction
    suspend fun queueArchivedDeletes(userId: String, ids: List<String>): Int {
        var count = 0
        for (id in ids.distinct()) count += queueDelete(userId, id, System.currentTimeMillis())
        return count
    }

    /** Keep only an erased marker so a stale download cannot recreate deleted content. */
    @Transaction
    suspend fun markDeleted(userId: String, id: String) {
        val local = getById(id)
        require(local == null || local.userId == userId) { "Snippet account mismatch." }
        upsert(SnippetEntity(
            snippetId = id, userId = userId, folderId = null, title = "", content = "",
            language = "", description = null, tagsCsv = "", isArchived = true,
            createdAt = local?.createdAt ?: 0L, updatedAt = local?.updatedAt ?: 0L,
            syncStatus = "DELETED",
        ))
    }

    /** Transition from DELETE_PENDING to DELETED after server acknowledgement. Safe for newly created items. */
    @Query("UPDATE snippets SET syncStatus = 'DELETED' WHERE snippetId = :id AND userId = :userId AND syncStatus = 'DELETE_PENDING'")
    suspend fun acknowledgeDeletion(userId: String, id: String)

    @Transaction
    suspend fun saveUnlessDeleted(snippet: SnippetEntity) {
        val current = getById(snippet.snippetId)
        check(current == null || current.syncStatus !in listOf("DELETE_PENDING", "DELETED")) {
            "This snippet was permanently deleted. Create a new snippet instead."
        }
        // Edits to a held draft remain held until explicit recovery.
        upsert(if (current?.syncStatus == "CONFLICT") snippet.copy(syncStatus = "CONFLICT") else snippet)
    }

    @Query("SELECT EXISTS(SELECT 1 FROM deleted_entities WHERE userId = :userId AND entityType = 'folder' AND entityId = :id)")
    suspend fun hasDeletedFolder(userId: String, id: String): Boolean

    /** Live (not deleted) snippets of an account that carry at least one tag; used by tag rename/delete. */
    @Query("SELECT * FROM snippets WHERE userId = :userId AND tagsCsv != '' AND syncStatus NOT IN ('DELETE_PENDING', 'DELETED')")
    suspend fun getTaggedForUser(userId: String): List<SnippetEntity>

    /** Writes several snippets atomically so a tag rewrite is never half-applied. */
    @Transaction
    suspend fun upsertAll(snippets: List<SnippetEntity>) {
        for (snippet in snippets) upsert(snippet)
    }
}
