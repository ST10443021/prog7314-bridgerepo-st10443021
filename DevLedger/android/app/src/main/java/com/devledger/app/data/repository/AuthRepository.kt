package com.devledger.app.data.repository

import com.devledger.app.data.local.PinStorage
import com.devledger.app.data.local.dao.UserDao
import com.devledger.app.data.local.entity.UserEntity
import com.devledger.app.data.remote.DevLedgerApiService
import com.devledger.app.data.remote.auth.TokenManager
import com.devledger.app.data.remote.dto.EmailLoginRequest
import com.devledger.app.data.remote.dto.RegisterRequest
import com.devledger.app.data.remote.dto.SsoLoginRequest
import com.devledger.app.notifications.DeviceRegistrar
import com.devledger.app.util.DevLedgerResult
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton
import com.devledger.app.data.remote.dto.UpdateUserProfileRequest
import com.devledger.app.data.remote.dto.UserProfileDto

/**
 * Handles SSO / email authentication against the DevLedger REST API and persists the
 * resulting session both as an encrypted JWT pair ([TokenManager]) and as a cached
 * [UserEntity] row so the UI has an authenticated profile to render immediately.
 */
@Singleton
class AuthRepository @Inject constructor(
    private val api: DevLedgerApiService,
    private val tokenManager: TokenManager,
    private val userDao: UserDao,
    private val deviceRegistrar: DeviceRegistrar,
    private val pinStorage: PinStorage,
) {
    val currentUser: Flow<UserEntity?> = userDao.observeCurrentUser()

    fun isLoggedIn(): Boolean = tokenManager.isLoggedIn()

    suspend fun signInWithSso(provider: String, idToken: String? = null, code: String? = null): DevLedgerResult<UserEntity> =
        DevLedgerResult.safeCall {
            val response = api.ssoLogin(SsoLoginRequest(provider, idToken, code))
            if (!response.isSuccessful) {
                val errorMsg = response.errorBody()?.string() ?: "Unknown error"
                error("SSO sign-in failed: HTTP ${response.code()} - $errorMsg")
            }
            val body = response.body() ?: error("Empty auth response")
            tokenManager.saveTokens(body.accessToken, body.refreshToken)
            val user = UserEntity(
                userId = body.userId,
                email = body.email.lowercase().trim(),
                fullName = body.fullName,
                ssoProvider = provider,
                pinHash = body.pinHash,
                pinSalt = body.pinSalt,
                biometricEnabled = body.biometricEnabled ?: true
            )
            // Sync PIN to local storage if present in response and missing locally
            if (body.pinHash != null && body.pinSalt != null) {
                pinStorage.syncFromBackend(body.email, body.pinHash, body.pinSalt, body.biometricEnabled ?: true)
            }
            userDao.replaceCurrentUser(user)
            deviceRegistrar.registerCurrentToken()
            user
        }

    suspend fun signInWithEmail(email: String, password: String): DevLedgerResult<UserEntity> =
        DevLedgerResult.safeCall {
            val response = api.emailLogin(EmailLoginRequest(email, password))
            if (!response.isSuccessful) {
                when (response.code()) {
                    401 -> error("Wrong Password")
                    404 -> error("Account not found")
                    else -> error("Sign-in failed: HTTP ${response.code()}")
                }
            }
            val body = response.body() ?: error("Empty auth response")
            tokenManager.saveTokens(body.accessToken, body.refreshToken)
            val user = UserEntity(
                userId = body.userId, 
                email = body.email.lowercase().trim(), 
                fullName = body.fullName, 
                ssoProvider = null,
                pinHash = body.pinHash,
                pinSalt = body.pinSalt,
                biometricEnabled = body.biometricEnabled ?: true
            )
            if (body.pinHash != null && body.pinSalt != null) {
                pinStorage.syncFromBackend(body.email, body.pinHash, body.pinSalt, body.biometricEnabled ?: true)
            }
            userDao.replaceCurrentUser(user)
            deviceRegistrar.registerCurrentToken()
            user
        }

    suspend fun register(email: String, password: String, fullName: String): DevLedgerResult<UserEntity> =
        DevLedgerResult.safeCall {
            val response = api.register(RegisterRequest(email, password, fullName))
            if (!response.isSuccessful) {
                if (response.code() == 409) error("An account with this email already exists.")
                else error("Registration failed: HTTP ${response.code()}")
            }
            val body = response.body() ?: error("Empty auth response")
            tokenManager.saveTokens(body.accessToken, body.refreshToken)
            val user = UserEntity(
                userId = body.userId, 
                email = body.email.lowercase().trim(), 
                fullName = body.fullName, 
                ssoProvider = null,
                pinHash = body.pinHash,
                pinSalt = body.pinSalt,
                biometricEnabled = body.biometricEnabled ?: true
            )
            if (body.pinHash != null && body.pinSalt != null) {
                pinStorage.syncFromBackend(body.email, body.pinHash, body.pinSalt, body.biometricEnabled ?: true)
            }
            userDao.replaceCurrentUser(user)
            deviceRegistrar.registerCurrentToken()
            user
        }

    suspend fun refreshProfile(): DevLedgerResult<UserEntity> =
        DevLedgerResult.safeCall {
            val response = api.getProfile()

            if (!response.isSuccessful) {
                when (response.code()) {
                    401 -> error(
                        "Your session has expired. Please sign in again."
                    )

                    404 -> error("Your profile could not be found.")

                    else -> error(
                        "Profile request failed: HTTP ${response.code()}"
                    )
                }
            }

            val profile = response.body()
                ?: error("The profile response was empty.")

            val user = profile.toUserEntity()
            if (profile.pinHash != null && profile.pinSalt != null) {
                pinStorage.syncFromBackend(profile.email, profile.pinHash, profile.pinSalt, profile.biometricEnabled)
            }
            userDao.replaceCurrentUser(user)

            user
        }

    suspend fun updateProfile(
        fullName: String,
    ): DevLedgerResult<UserEntity> =
        DevLedgerResult.safeCall {
            val cleanedName = fullName.trim()

            require(cleanedName.isNotBlank()) {
                "Full name is required."
            }

            require(cleanedName.length <= 100) {
                "Full name cannot exceed 100 characters."
            }

            val response = api.updateProfile(
                UpdateUserProfileRequest(
                    fullName = cleanedName,
                )
            )

            if (!response.isSuccessful) {
                when (response.code()) {
                    400 -> error(
                        "Please enter a valid full name."
                    )

                    401 -> error(
                        "Your session has expired. Please sign in again."
                    )

                    404 -> error("Your profile could not be found.")

                    else -> error(
                        "Profile update failed: HTTP ${response.code()}"
                    )
                }
            }

            val profile = response.body()
                ?: error("The profile response was empty.")

            val user = profile.toUserEntity()
            if (profile.pinHash != null && profile.pinSalt != null) {
                pinStorage.syncFromBackend(profile.email, profile.pinHash, profile.pinSalt, profile.biometricEnabled)
            }
            userDao.replaceCurrentUser(user)

            user
        }

    suspend fun saveSecurityProfile(
        pinHash: String,
        pinSalt: String,
        biometricEnabled: Boolean
    ): DevLedgerResult<UserEntity> =
        DevLedgerResult.safeCall {
            val response = api.updateProfile(
                UpdateUserProfileRequest(
                    pinHash = pinHash,
                    pinSalt = pinSalt,
                    biometricEnabled = biometricEnabled
                )
            )
            if (!response.isSuccessful) error("Failed to sync security profile to backend: HTTP ${response.code()}")
            val profile = response.body() ?: error("Empty profile response")
            val user = profile.toUserEntity()
            userDao.replaceCurrentUser(user)
            user
        }

    private fun UserProfileDto.toUserEntity(): UserEntity {
        return UserEntity(
            userId = userId,
            email = email.lowercase().trim(),
            fullName = fullName,
            ssoProvider = ssoProvider,
            preferredLanguage = preferredLanguage,
            biometricEnabled = biometricEnabled,
            pinHash = pinHash,
            pinSalt = pinSalt,
            baseCurrency = baseCurrency,
        )
    }

    suspend fun signOut() {
        tokenManager.clear()
        userDao.clear()
    }
}