package com.devledger.app.data.repository

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.devledger.app.MainActivity
import com.devledger.app.R
import com.devledger.app.data.local.dao.NotificationDao
import com.devledger.app.data.local.entity.NotificationEntity
import com.devledger.app.util.IdGenerator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Mirrors Firebase Cloud Messaging pushes locally so the Notifications screen works
 *  offline and stays consistent with the system tray (Firebase, n.d.). */
@Singleton
/**
 * Reference scope: repository separation and Room persistence.
 * (Google Developers, n.d.a; Google Developers, n.d.b). See REFERENCES-CODE.md for full entries.
 */
class NotificationRepository @Inject constructor(
    private val dao: NotificationDao,
    @ApplicationContext private val context: Context,
) {
    fun observeAll(): Flow<List<NotificationEntity>> = dao.observeAll()
    fun observeUnreadCount(): Flow<Int> = dao.observeUnreadCount()

    suspend fun record(notification: NotificationEntity) = dao.upsert(notification)

    /** Creates a notification, stores it in Room, and triggers a system tray alert. */
    suspend fun notify(title: String, message: String, type: String) {
        val id = IdGenerator.newId()
        val entity = NotificationEntity(
            notificationId = id,
            title = title,
            message = message,
            type = type,
            createdAt = System.currentTimeMillis()
        )
        record(entity)
        showSystemNotification(title, message)
    }

    private fun showSystemNotification(title: String, body: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "DevLedger Alerts", NotificationManager.IMPORTANCE_HIGH),
            )
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    suspend fun markRead(id: String) = dao.markRead(id)
    suspend fun markAllRead() = dao.markAllRead()

    private companion object {
        const val CHANNEL_ID = "devledger_alerts"
    }
}
