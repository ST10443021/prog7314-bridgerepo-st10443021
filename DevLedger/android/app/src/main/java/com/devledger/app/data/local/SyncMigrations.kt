package com.devledger.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// Reference scope: On-device security and storage. (Google Developers, n.d.e). See REFERENCES-CODE.md for full entries.

/** Retain all Room v5 data and queued deletions during the offline-sync upgrade. */
object SyncMigrations {
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE deleted_entities ADD COLUMN reassignToFolderId TEXT")
            db.execSQL("ALTER TABLE deleted_entities ADD COLUMN syncStatus TEXT NOT NULL DEFAULT 'PENDING'")
        }
    }
}
