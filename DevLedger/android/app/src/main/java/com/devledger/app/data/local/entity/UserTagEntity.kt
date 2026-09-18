package com.devledger.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** User-defined snippet tag with metadata (e.g. custom color), matching the
 *  "18 Tags Synced" requirement in the Sync Status mockup. */
@Serializable
@Entity(tableName = "user_tags")
/**
 * Reference scope: Room entity mapping.
 * (Google Developers, n.d.b). See REFERENCES-CODE.md for full entries.
 */
data class UserTagEntity(
    @PrimaryKey val tagId: String,
    val userId: String,
    val name: String,
    val colorHex: String? = null,
    val createdAt: Long,
    val syncStatus: String = SyncStatus.PENDING,
)
