package com.devledger.app.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class SyncOperation(
    val entity: String,        // "snippet" | "transaction"
    val action: String,        // "create" | "update" | "delete"
    val payload: String,       // JSON-encoded entity payload
    val clientTimestamp: Long,
)

@Serializable
data class SyncBatchRequest(val operations: List<SyncOperation>)

@Serializable
data class SyncBatchResponse(val applied: List<String>, val conflicts: List<String>)

@Serializable
data class RegisterDeviceRequest(val fcmToken: String, val deviceId: String)

@Serializable
data class UserSettingsDto(
    val language: String,
    val baseCurrency: String,
    val biometricEnabled: Boolean,
    val syncOnWifiOnly: Boolean,
    val monthlyBudget: Double? = null,
)
