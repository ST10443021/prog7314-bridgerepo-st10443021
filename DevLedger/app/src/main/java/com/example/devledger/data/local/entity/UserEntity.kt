package com.devledger.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Cached authenticated-user profile, used so the Dashboard and Settings screens can
 *  render immediately on cold start before the REST API profile call resolves. */
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val userId: String,
    val email: String,
    val fullName: String,
    val ssoProvider: String?,     // "GOOGLE" | "GITHUB" | null (email/password)
    val preferredLanguage: String = "en",
    val biometricEnabled: Boolean = true,
    val baseCurrency: String = "ZAR",
)
