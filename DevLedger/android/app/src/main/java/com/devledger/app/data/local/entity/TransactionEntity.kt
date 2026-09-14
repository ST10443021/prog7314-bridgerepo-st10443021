package com.devledger.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** Income/expense record — mirrors the `Transaction` schema from Part 1's data model.
 *  @Serializable is required so SyncWorker can encode this directly for the sync batch
 *  payload (JetBrains, n.d.). */
@Serializable
@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey val transactionId: String,
    val userId: String,
    val type: String,
    val amount: Double,
    val currency: String,
    val category: String,
    val description: String?,
    val transactionDate: Long,
    val createdAt: Long,
    val syncStatus: String = SyncStatus.PENDING,
)