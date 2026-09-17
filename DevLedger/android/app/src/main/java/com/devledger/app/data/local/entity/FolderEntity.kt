package com.devledger.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** Snippet organisation folder — supports the nested-folder pattern adopted from the
 *  SnippetsLab competitive analysis in the Part 1 Research Report. */
@Serializable
@Entity(tableName = "folders")
/**
 * Reference scope: Room entity mapping.
 * (Google Developers, n.d.b). See REFERENCES-CODE.md for full entries.
 */
data class FolderEntity(
    @PrimaryKey val folderId: String,
    val userId: String,
    val name: String,
    val parentFolderId: String? = null,
    val createdAt: Long,
    val syncStatus: String = SyncStatus.PENDING,
)
