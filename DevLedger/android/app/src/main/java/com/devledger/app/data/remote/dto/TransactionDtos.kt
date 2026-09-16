package com.devledger.app.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class TransactionDto(
    val transactionId: String,
    val type: String,
    val amount: Double,
    val currency: String,
    val category: String,
    val description: String?,
    val clientOrFrom: String,
    val relatedProject: String?,
    val paymentMethod: String?,
    val referenceNumber: String?,
    val isRecurring: Boolean,
    val recurringFrequency: String?,
    val nextOccurrenceDate: Long?,
    val attachmentFileName: String?,
    val hasAttachment: Boolean,
    val transactionDate: Long,
)

@Serializable
data class CreateTransactionRequest(
    val type: String,
    val amount: Double,
    val currency: String,
    val category: String,
    val description: String?,
    val clientOrFrom: String,
    val relatedProject: String?,
    val paymentMethod: String?,
    val referenceNumber: String?,
    val isRecurring: Boolean,
    val recurringFrequency: String?,
    val nextOccurrenceDate: Long?,
    val attachmentFileName: String?,
    val attachmentBase64: String?,
    val transactionDate: Long,
)

@Serializable
data class TransactionSummaryDto(val totalIncome: Double, val totalExpenses: Double, val netBalance: Double, val currency: String)

@Serializable
data class DailySeriesPointDto(val date: Long, val income: Double, val expenses: Double)

@Serializable
data class CategoryBreakdownDto(val category: String, val total: Double)