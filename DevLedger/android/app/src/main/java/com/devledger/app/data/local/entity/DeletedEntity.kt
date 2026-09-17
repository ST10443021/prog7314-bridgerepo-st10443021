package com.devledger.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Tracks entities deleted while offline so they can be removed from the server
 *  during the next synchronisation cycle. */
@Entity(tableName = "deleted_entities")
data class DeletedEntity(
    @PrimaryKey val entityId: String,
    val userId: String,
    val entityType: String, // "snippet" | "transaction" | "folder" | "tag"
    val deletedAt: Long = System.currentTimeMillis(),
)
