package com.devledger.app.di

import android.content.Context
import androidx.room.Room
import com.devledger.app.data.local.DevLedgerDatabase
import com.devledger.app.data.local.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
/**
 * Reference scope: Room persistence.
 * (Google Developers, n.d.b). See REFERENCES-CODE.md for full entries.
 */
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): DevLedgerDatabase =
        Room.databaseBuilder(context, DevLedgerDatabase::class.java, DevLedgerDatabase.DATABASE_NAME)
            .addMigrations(com.devledger.app.data.local.SyncMigrations.MIGRATION_5_6)
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideSnippetDao(db: DevLedgerDatabase): SnippetDao = db.snippetDao()
    @Provides fun provideTransactionDao(db: DevLedgerDatabase): TransactionDao = db.transactionDao()
    @Provides fun provideNotificationDao(db: DevLedgerDatabase): NotificationDao = db.notificationDao()
    @Provides fun provideFolderDao(db: DevLedgerDatabase): FolderDao = db.folderDao()
    @Provides fun provideUserDao(db: DevLedgerDatabase): UserDao = db.userDao()
    @Provides fun provideSyncLogDao(db: DevLedgerDatabase): SyncLogDao = db.syncLogDao()
    @Provides fun provideUserTagDao(db: DevLedgerDatabase): UserTagDao = db.userTagDao()
    @Provides fun provideDeletedDao(db: DevLedgerDatabase): DeletedDao = db.deletedDao()
}
