package com.devledger.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Snippet organisation folder — supports the nested-folder pattern adopted from the
 *  SnippetsLab competitive analysis in the Part 1 Research Report. */
@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey val folderId: String,
    val userId: String,
    val name: String,
    val parentFolderId: String? = null,
    val createdAt: Long,
)
