package com.devledger.app.sync

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.devledger.app.data.local.SyncMigrations
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Reference scope: JVM unit testing for Android with JUnit, MockK and Truth. (Google Developers, n.d.s). See REFERENCES-CODE.md for full entries.

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SyncMigrationTest {
    @Test fun `v5 queued delete remains pending after additive migration`() {
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(
            ApplicationProvider.getApplicationContext<Context>()).callback(object : SupportSQLiteOpenHelper.Callback(1) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE deleted_entities(entityId TEXT NOT NULL PRIMARY KEY, userId TEXT NOT NULL, entityType TEXT NOT NULL, deletedAt INTEGER NOT NULL)")
                db.execSQL("INSERT INTO deleted_entities VALUES ('tx', 'a', 'transaction', 42)")
            }
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }).build())
        try {
            val database = helper.writableDatabase
            SyncMigrations.MIGRATION_5_6.migrate(database)
            database.query("SELECT entityId, deletedAt, syncStatus, reassignToFolderId FROM deleted_entities").use { rows ->
                assertTrue(rows.moveToFirst())
                assertEquals("tx", rows.getString(0))
                assertEquals(42L, rows.getLong(1))
                assertEquals("PENDING", rows.getString(2))
                assertTrue(rows.isNull(3))
            }
        } finally { helper.close() }
    }
}
