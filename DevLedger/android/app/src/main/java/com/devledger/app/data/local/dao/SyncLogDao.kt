package com.devledger.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.devledger.app.data.local.entity.SyncLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
/**
 * Reference scope: Room persistence.
 * (Google Developers, n.d.b). See REFERENCES-CODE.md for full entries.
 */
interface SyncLogDao {
    @Query("SELECT * FROM sync_log WHERE userId = :userId ORDER BY timestamp DESC LIMIT 50")
    fun observeForUser(userId: String): Flow<List<SyncLogEntity>>

    @Insert
    suspend fun insert(entry: SyncLogEntity)
}