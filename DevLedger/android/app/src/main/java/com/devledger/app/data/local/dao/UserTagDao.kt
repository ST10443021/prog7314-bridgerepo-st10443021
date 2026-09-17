package com.devledger.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.devledger.app.data.local.entity.SyncStatus
import com.devledger.app.data.local.entity.UserTagEntity
import kotlinx.coroutines.flow.Flow

@Dao
/**
 * Reference scope: Room persistence.
 * (Google Developers, n.d.b). See REFERENCES-CODE.md for full entries.
 */
interface UserTagDao {
    @Query("SELECT * FROM user_tags WHERE userId = :userId ORDER BY name ASC")
    fun observeForUser(userId: String): Flow<List<UserTagEntity>>

    @Query("SELECT * FROM user_tags WHERE tagId = :id LIMIT 1")
    suspend fun getById(id: String): UserTagEntity?

    @Query("SELECT * FROM user_tags WHERE userId = :userId AND syncStatus = 'PENDING' ORDER BY createdAt ASC")
    suspend fun getPendingSyncForUser(userId: String): List<UserTagEntity>

    @Upsert
    suspend fun upsert(tag: UserTagEntity)

    @Query("UPDATE user_tags SET syncStatus = :status WHERE tagId = :id")
    suspend fun updateSyncStatus(id: String, status: String)

    @Query("DELETE FROM user_tags WHERE tagId = :id")
    suspend fun delete(id: String)

    @Transaction
    suspend fun mergeRemoteTags(userId: String, remoteTags: List<UserTagEntity>) {
        for (remote in remoteTags) {
            require(remote.userId == userId) { "The downloaded tag belongs to a different account." }
            val local = getById(remote.tagId)
            require(local == null || local.userId == userId) { "A tag ID conflicts with another cached account." }
            
            if (local == null) {
                upsert(remote)
            } else if (local.syncStatus == SyncStatus.SYNCED) {
                upsert(remote)
            }
        }
    }
}
