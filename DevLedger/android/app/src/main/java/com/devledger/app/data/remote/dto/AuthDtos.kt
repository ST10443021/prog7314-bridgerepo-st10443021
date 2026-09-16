package com.devledger.app.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class SsoLoginRequest(val provider: String, val idToken: String? = null, val code: String? = null)

@Serializable
data class EmailLoginRequest(val email: String, val password: String)

@Serializable
data class RegisterRequest(val email: String, val password: String, val fullName: String)

@Serializable
data class AuthResponse(
    val accessToken: String, 
    val refreshToken: String, 
    val userId: String, 
    val fullName: String, 
    val email: String,
    val pinHash: String? = null,
    val pinSalt: String? = null,
    val biometricEnabled: Boolean? = null
)

@Serializable
data class RefreshRequest(val refreshToken: String)
