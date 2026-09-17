package com.devledger.app.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class SyncOperation(
    val entity: String,
    val action: String,
    val payload: String,
    val clientTimestamp: Long,
)

@Serializable
data class SnippetSyncPayload(
    val snippetId: String,
    val userId: String,
    val folderId: String? = null,
    val title: String,
    val content: String,
    val language: String,
    val description: String? = null,
    val tagsCsv: String = "",
    val isFavourite: Boolean = false,
    val isArchived: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class TransactionSyncPayload(
    val transactionId: String,
    val userId: String,
    val type: String,
    val amount: Double,
    val currency: String,
    val category: String,
    val description: String? = null,
    val clientOrFrom: String,
    val relatedProject: String? = null,
    val paymentMethod: String? = null,
    val referenceNumber: String? = null,
    val isRecurring: Boolean = false,
    val recurringFrequency: String? = null,
    val nextOccurrenceDate: Long? = null,
    val attachmentFileName: String? = null,
    val attachmentBase64: String? = null,
    val transactionDate: Long,
    val createdAt: Long,
)

@Serializable
data class SyncBatchRequest(
    val operations: List<SyncOperation>,
)

@Serializable
data class SyncBatchResponse(
    val applied: List<String>,
    val conflicts: List<String>,
)

@Serializable
data class RegisterDeviceRequest(
    val fcmToken: String,
    val deviceId: String,
)

@Serializable
data class UserSettingsDto(
    val language: String,
    val baseCurrency: String,
    val biometricEnabled: Boolean,
    val syncOnWifiOnly: Boolean,
    val monthlyBudget: Double? = null,
)

@Serializable
data class SnippetDeletePayload(val snippetId: String, val userId: String)

@Serializable
data class SnippetSnapshotDto(
    val snippets: List<SnippetDto>,
    val deletedIds: List<String>,
)
