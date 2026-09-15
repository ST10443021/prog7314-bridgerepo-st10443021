package com.devledger.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.devledger.app.data.local.entity.FolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders WHERE userId = :userId ORDER BY name ASC")
    fun observeForUser(userId: String): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE folderId = :id LIMIT 1")
    suspend fun getById(id: String): FolderEntity?

    @Upsert
    suspend fun upsert(folder: FolderEntity)

    @Query("DELETE FROM folders WHERE folderId = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM folders WHERE userId = :userId")
    suspend fun clearForUser(userId: String)
}