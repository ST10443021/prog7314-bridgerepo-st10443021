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

    @Query("UPDATE snippets SET isArchived = :archived, syncStatus = 'PENDING' WHERE snippetId = :id")
    suspend fun setArchived(id: String, archived: Boolean)

    @Query("UPDATE snippets SET isFavourite = :favourite, syncStatus = 'PENDING' WHERE snippetId = :id")
    suspend fun setFavourite(id: String, favourite: Boolean)

    @Delete
    suspend fun delete(snippet: SnippetEntity)

    @Query("SELECT COUNT(*) FROM snippets WHERE isArchived = 0")
    fun observeActiveCount(): Flow<Int>
}
