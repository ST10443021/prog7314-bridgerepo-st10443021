package com.devledger.app.data.repository

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
                email = body.email,
                fullName = body.fullName,
                ssoProvider = provider,
            )
            userDao.upsert(user)
            deviceRegistrar.registerCurrentToken()
            user
        }

    suspend fun signInWithEmail(email: String, password: String): DevLedgerResult<UserEntity> =
        DevLedgerResult.safeCall {
            val response = api.emailLogin(EmailLoginRequest(email, password))
            if (!response.isSuccessful) error("Sign-in failed: HTTP ${response.code()}")
            val body = response.body() ?: error("Empty auth response")
            tokenManager.saveTokens(body.accessToken, body.refreshToken)
            val user = UserEntity(userId = body.userId, email = body.email, fullName = body.fullName, ssoProvider = null)
            userDao.upsert(user)
            deviceRegistrar.registerCurrentToken()
            user
        }

    suspend fun register(email: String, password: String, fullName: String): DevLedgerResult<UserEntity> =
        DevLedgerResult.safeCall {
            val response = api.register(RegisterRequest(email, password, fullName))
            if (!response.isSuccessful) error("Registration failed: HTTP ${response.code()}")
            val body = response.body() ?: error("Empty auth response")
            tokenManager.saveTokens(body.accessToken, body.refreshToken)
            val user = UserEntity(userId = body.userId, email = body.email, fullName = body.fullName, ssoProvider = null)
            userDao.upsert(user)
            deviceRegistrar.registerCurrentToken()
            user
        }

    suspend fun signOut() {
        tokenManager.clear()
        userDao.clear()
    }
}