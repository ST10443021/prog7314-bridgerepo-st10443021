package com.devledger.app.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class UserProfileDto(
    val userId: String,
    val email: String,
    val fullName: String,
    val ssoProvider: String? = null,
    val preferredLanguage: String,
    val baseCurrency: String,
    val biometricEnabled: Boolean,
    val pinHash: String? = null,
    val pinSalt: String? = null,
    val createdAt: String,
)

@Serializable
data class UpdateUserProfileRequest(
    val fullName: String? = null,
    val pinHash: String? = null,
    val pinSalt: String? = null,
    val biometricEnabled: Boolean? = null,
)
