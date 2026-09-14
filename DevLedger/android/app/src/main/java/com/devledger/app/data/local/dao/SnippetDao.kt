package com.devledger.app.data.local.dao

import androidx.room.*
import com.devledger.app.data.local.entity.SnippetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SnippetDao {
    @Query("SELECT * FROM snippets WHERE isArchived = 0 ORDER BY updatedAt DESC")
    fun observeActiveSnippets(): Flow<List<SnippetEntity>>

    @Query("SELECT * FROM snippets WHERE isArchived = 1 ORDER BY updatedAt DESC")
    fun observeArchivedSnippets(): Flow<List<SnippetEntity>>

    @Query("""
        SELECT * FROM snippets
        WHERE isArchived = 0
        AND (title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%' OR tagsCsv LIKE '%' || :query || '%')
        ORDER BY updatedAt DESC
    """)
    fun searchSnippets(query: String): Flow<List<SnippetEntity>>

    @Query("SELECT * FROM snippets WHERE snippetId = :id LIMIT 1")
    suspend fun getById(id: String): SnippetEntity?

    @Query("SELECT * FROM snippets WHERE syncStatus = 'PENDING'")
    suspend fun getPendingSync(): List<SnippetEntity>

    @Upsert
    suspend fun upsert(snippet: SnippetEntity)

    @Query("UPDATE snippets SET syncStatus = :status WHERE snippetId = :id")
    suspend fun updateSyncStatus(id: String, status: String)

    @Query("""
    UPDATE snippets
    SET isArchived = :archived,
        updatedAt = MAX(updatedAt + 1, :changedAt),
        syncStatus = 'PENDING'
    WHERE snippetId = :id
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
        syncStatus = 'PENDING'
    WHERE snippetId = :id
""")
    suspend fun setFavourite(
        id: String,
        favourite: Boolean,
        changedAt: Long = System.currentTimeMillis(),
    )

    @Delete
    suspend fun delete(snippet: SnippetEntity)

    @Query("SELECT COUNT(*) FROM snippets WHERE isArchived = 0")
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
    WHERE snippetId = :id
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
      AND isArchived = 0
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
    WHERE userId = :userId AND isArchived = 1
    ORDER BY updatedAt DESC
""")
    fun observeArchivedForUser(userId: String): Flow<List<SnippetEntity>>

    @Query("""
    SELECT COUNT(*) FROM snippets
    WHERE userId = :userId AND isArchived = 0
""")
    fun observeActiveCountForUser(userId: String): Flow<Int>

    @Transaction
    suspend fun mergeRemoteSnippets(
        userId: String,
        remoteSnippets: List<SnippetEntity>,
    ) {
        for (remote in remoteSnippets) {
            require(remote.userId == userId) {
                "The downloaded snippet belongs to a different account."
            }

            val local = getById(remote.snippetId)

            require(local == null || local.userId == userId) {
                "A snippet ID conflicts with another cached account."
            }

            if (local == null) {
                upsert(remote)
            } else if (
                local.syncStatus == "SYNCED" &&
                remote.updatedAt > local.updatedAt
            ) {
                upsert(remote)
            }
        }
    }
}
