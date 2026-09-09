package com.devledger.app.data.repository

import com.devledger.app.data.local.dao.NotificationDao
import com.devledger.app.data.local.entity.NotificationEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Mirrors Firebase Cloud Messaging pushes locally so the Notifications screen works
 *  offline and stays consistent with the system tray (Firebase, n.d.). */
@Singleton
class NotificationRepository @Inject constructor(
    private val dao: NotificationDao,
) {
    fun observeAll(): Flow<List<NotificationEntity>> = dao.observeAll()
    fun observeUnreadCount(): Flow<Int> = dao.observeUnreadCount()

    suspend fun record(notification: NotificationEntity) = dao.upsert(notification)
    suspend fun markRead(id: String) = dao.markRead(id)
    suspend fun markAllRead() = dao.markAllRead()
}
