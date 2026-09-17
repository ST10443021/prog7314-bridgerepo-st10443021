package com.devledger.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** Income/expense record — mirrors the extended Transaction schema. @Serializable is
 *  required so SyncWorker can encode this directly for the sync batch payload. */
@Serializable
@Entity(tableName = "transactions")
/**
 * Reference scope: Room entity mapping.
 * (Google Developers, n.d.b). See REFERENCES-CODE.md for full entries.
 */
data class TransactionEntity(
    @PrimaryKey val transactionId: String,
    val userId: String,
    val type: String,
    val amount: Double,
    val currency: String,
    val category: String,
    val description: String?,
    val clientOrFrom: String,
    val relatedProject: String?,
    val paymentMethod: String?,
    val referenceNumber: String?,
    val isRecurring: Boolean = false,
    val recurringFrequency: String? = null,
    val nextOccurrenceDate: Long? = null,
    val attachmentFileName: String? = null,
    val attachmentBase64: String? = null,
    val transactionDate: Long,
    val createdAt: Long,
    val syncStatus: String = SyncStatus.PENDING,
)