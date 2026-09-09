package com.devledger.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Local mirror of push notifications received via Firebase Cloud Messaging, so the
 *  in-app Notifications screen stays consistent with the system tray even offline. */
@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey val notificationId: String,
    val title: String,
    val message: String,
    val type: String,             // "SYNC" | "BUDGET" | "SECURITY" | "SYSTEM"
    val isRead: Boolean = false,
    val createdAt: Long,
)
