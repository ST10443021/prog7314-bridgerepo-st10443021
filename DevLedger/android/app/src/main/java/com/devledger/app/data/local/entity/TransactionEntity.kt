package com.devledger.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Income/expense record — mirrors the Transaction schema from Part 1's data model. */
@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey val transactionId: String,
    val userId: String,
    val type: String,             // "INCOME" | "EXPENSE"
    val amount: Double,
    val currency: String,         // ISO 4217 code, e.g. "ZAR", "USD"
    val category: String,
    val description: String?,
    val transactionDate: Long,
    val createdAt: Long,
    val syncStatus: String = SyncStatus.PENDING,
)