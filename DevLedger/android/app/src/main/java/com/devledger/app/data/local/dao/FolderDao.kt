package com.devledger.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.devledger.app.data.local.entity.FolderEntity
import com.devledger.app.data.local.entity.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders WHERE userId = :userId ORDER BY name ASC")
    fun observeForUser(userId: String): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE folderId = :id LIMIT 1")
    suspend fun getById(id: String): FolderEntity?

    @Query("SELECT * FROM folders WHERE userId = :userId AND syncStatus = 'PENDING' ORDER BY createdAt ASC")
    suspend fun getPendingSyncForUser(userId: String): List<FolderEntity>

    @Upsert
    suspend fun upsert(folder: FolderEntity)

    @Query("UPDATE folders SET syncStatus = :status WHERE folderId = :id")
    suspend fun updateSyncStatus(id: String, status: String)

    @Query("DELETE FROM folders WHERE folderId = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM folders WHERE userId = :userId")
    suspend fun clearForUser(userId: String)

    @Transaction
    suspend fun mergeRemoteFolders(userId: String, remoteFolders: List<FolderEntity>) {
        for (remote in remoteFolders) {
            require(remote.userId == userId) { "The downloaded folder belongs to a different account." }
            val local = getById(remote.folderId)
            require(local == null || local.userId == userId) { "A folder ID conflicts with another cached account." }
            
            if (local == null) {
                upsert(remote)
            } else if (local.syncStatus == SyncStatus.SYNCED) {
                upsert(remote)
            }
        }
    }
}
