package com.devledger.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One row per completed or failed sync run — backs the Sync screen's
 *  Completed/Errors tabs with real history (Google Developers, n.d.a) rather than
 *  simulated entries. */
@Entity(tableName = "sync_log")
data class SyncLogEntity(
    @PrimaryKey val logId: String,
    val userId: String,
    val status: String, // "COMPLETED" | "FAILED"
    val itemsSynced: Int,
    val errorMessage: String?,
    val timestamp: Long,
)