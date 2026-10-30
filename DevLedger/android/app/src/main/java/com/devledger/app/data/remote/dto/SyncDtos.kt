package com.devledger.app.data.remote.dto

import kotlinx.serialization.Serializable

// Reference scope: kotlinx.serialization DTOs. (Kotlin, n.d.). See REFERENCES-CODE.md for full entries.

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
    val tags: List<String> = emptyList(), // Member's fix: use List<String> instead of CSV for API
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
data class FolderSyncPayload(
    val folderId: String,
    val userId: String,
    val name: String,
    val parentFolderId: String? = null,
    val createdAt: Long,
)

@Serializable
data class UserTagSyncPayload(
    val tagId: String,
    val userId: String,
    val name: String,
    val colorHex: String? = null,
    val createdAt: Long,
)

@Serializable
data class DeleteSyncPayload(
    val entityId: String,
    val userId: String? = null,
    val reassignToFolderId: String? = null,
)

@Serializable
data class SyncDeletionDto(val entityType: String, val entityId: String)

@Serializable
data class SnippetDeletePayload(val snippetId: String, val userId: String)

@Serializable
data class SnippetSnapshotDto(
    val snippets: List<SnippetDto>,
    val deletedIds: List<String>,
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
