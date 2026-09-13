package com.devledger.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.devledger.app.MainActivity
import com.devledger.app.R
import com.devledger.app.data.local.entity.NotificationEntity
import com.devledger.app.data.repository.NotificationRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Real-time notification handler (Part 1 core requirement: "Implementation of
 * Real-time Notification"). Receives Firebase Cloud Messaging pushes for hosting-budget
 * threshold alerts, snippet-backup confirmations and new-device sign-ins, shows a system
 * notification, and mirrors the event into Room so it also appears on the in-app
 * Notifications screen when the device is offline (Firebase, n.d.).
 */
@AndroidEntryPoint
class DevLedgerMessagingService : FirebaseMessagingService() {

    @Inject lateinit var notificationRepository: NotificationRepository

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.notification?.title ?: message.data["title"] ?: "DevLedger"
        val body = message.notification?.body ?: message.data["body"] ?: ""
        val type = message.data["type"] ?: "SYSTEM"

        showSystemNotification(title, body)

        CoroutineScope(Dispatchers.IO).launch {
            notificationRepository.record(
                NotificationEntity(
                    notificationId = message.messageId ?: System.currentTimeMillis().toString(),
                    title = title,
                    message = body,
                    type = type,
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Enqueued via WorkManager (Google, n.d.c) rather than called directly: this
        // callback runs on a system-managed path outside normal Activity/ViewModel
        // scope, so a plain network call here would be unsafe if the process is about
        // to be torn down. RegisterDeviceWorker re-reads the token itself, so the
        // parameter isn't threaded through — see DeviceRegistrar.
        val request = OneTimeWorkRequestBuilder<RegisterDeviceWorker>().build()
        WorkManager.getInstance(applicationContext).enqueue(request)
    }

    private fun showSystemNotification(title: String, body: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "DevLedger Alerts", NotificationManager.IMPORTANCE_HIGH),
            )
        }
        val intent = android.content.Intent(this, MainActivity::class.java)
        val pendingIntent = android.app.PendingIntent.getActivity(
            this, 0, intent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private companion object {
        const val CHANNEL_ID = "devledger_alerts"
    }
}