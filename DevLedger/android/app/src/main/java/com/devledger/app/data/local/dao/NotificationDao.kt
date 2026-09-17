package com.devledger.app.data.local.dao

import androidx.room.*
import com.devledger.app.data.local.entity.NotificationEntity
import kotlinx.coroutines.flow.Flow

@Dao
/**
 * Reference scope: Room persistence.
 * (Google Developers, n.d.b). See REFERENCES-CODE.md for full entries.
 */
interface NotificationDao {
    @Query("SELECT * FROM notifications ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<NotificationEntity>>

    @Query("SELECT COUNT(*) FROM notifications WHERE isRead = 0")
    fun observeUnreadCount(): Flow<Int>

    @Upsert
    suspend fun upsert(notification: NotificationEntity)

    @Query("UPDATE notifications SET isRead = 1 WHERE notificationId = :id")
    suspend fun markRead(id: String)

    @Query("UPDATE notifications SET isRead = 1")
    suspend fun markAllRead()
}
