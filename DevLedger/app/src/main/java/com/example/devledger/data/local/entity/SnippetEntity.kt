package com.devledger.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local, offline-first representation of a code snippet — mirrors the `Snippet` schema
 * defined in the Part 1 Planning and Design document's Data Models section.
 * [syncStatus] drives the offline-sync queue: PENDING rows are pushed to the REST API
 * by [com.devledger.app.sync.SyncWorker] once connectivity returns.
 */
@Entity(tableName = "snippets")
data class SnippetEntity(
    @PrimaryKey val snippetId: String,
    val userId: String,
    val folderId: String?,
    val title: String,
    val content: String,
    val language: String,
    val description: String?,
    val tagsCsv: String,          // comma-separated tag names (kept simple for the prototype)
    val isFavourite: Boolean = false,
    val isArchived: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val syncStatus: String = SyncStatus.PENDING,
)

object SyncStatus {
    const val PENDING = "PENDING"
    const val SYNCED = "SYNCED"
    const val FAILED = "FAILED"
}
