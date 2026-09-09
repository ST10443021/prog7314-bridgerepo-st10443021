package com.devledger.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.devledger.app.data.local.dao.*
import com.devledger.app.data.local.entity.*

/**
 * Room database — the single source of truth for the DevLedger UI, per the
 * offline-first architecture specified in the Part 1 Planning and Design document
 * (Section 5: "RoomDB as the local store ... WorkManager-driven sync queue").
 */
@Database(
    entities = [
        SnippetEntity::class,
        TransactionEntity::class,
        NotificationEntity::class,
        FolderEntity::class,
        UserEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class DevLedgerDatabase : RoomDatabase() {
    abstract fun snippetDao(): SnippetDao
    abstract fun transactionDao(): TransactionDao
    abstract fun notificationDao(): NotificationDao
    abstract fun folderDao(): FolderDao
    abstract fun userDao(): UserDao

    companion object {
        const val DATABASE_NAME = "devledger.db"
    }
}
