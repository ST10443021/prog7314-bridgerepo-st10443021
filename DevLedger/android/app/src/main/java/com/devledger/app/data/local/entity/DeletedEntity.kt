package com.devledger.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Retains pending and acknowledged deletion markers so stale reads cannot restore rows.
 * Tracks entities deleted while offline so they can be removed from the server
 *  during the next synchronisation cycle. */
@Entity(tableName = "deleted_entities")
/**
 * Reference scope: Room entity mapping.
 * (Google Developers, n.d.b). See REFERENCES-CODE.md for full entries.
 */
data class DeletedEntity(
    @PrimaryKey val entityId: String,
    val userId: String,
    val entityType: String, // "snippet" | "transaction" | "folder" | "tag"
    val deletedAt: Long = System.currentTimeMillis(),
    val reassignToFolderId: String? = null,
    @ColumnInfo(defaultValue = "'PENDING'") val syncStatus: String = SyncStatus.PENDING,
)
