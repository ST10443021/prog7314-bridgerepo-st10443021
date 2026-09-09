package com.devledger.app.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class TransactionDto(
    val transactionId: String,
    val type: String,
    val amount: Double,
    val currency: String,
    val category: String,
    val description: String? = null,
    val transactionDate: Long,
)

@Serializable
data class CreateTransactionRequest(
    val type: String,
    val amount: Double,
    val currency: String,
    val category: String,
    val description: String? = null,
    val transactionDate: Long,
)

@Serializable
data class TransactionSummaryDto(
    val totalIncome: Double,
    val totalExpenses: Double,
    val netBalance: Double,
    val currency: String,
)
