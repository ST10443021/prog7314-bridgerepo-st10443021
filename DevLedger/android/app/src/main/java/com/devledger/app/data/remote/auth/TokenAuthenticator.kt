package com.devledger.app.data.remote.auth

import com.devledger.app.BuildConfig
import com.devledger.app.data.remote.DevLedgerApiService
import com.devledger.app.data.remote.dto.RefreshRequest
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Provider

// Reference scope: Token storage and OkHttp authenticators. (Google Developers, n.d.e; Square, n.d.). See REFERENCES-CODE.md for full entries.

/**
 * Automatically attempts to exchange a refresh token for a new access token pair if a
 * request returns 401 Unauthorized. If the refresh token is also rejected or missing,
 * it returns null to let the 401 bubble up, which typically triggers a sign-out.
 */
class TokenAuthenticator @Inject constructor(
    private val tokenManager: TokenManager,
    private val apiProvider: Provider<DevLedgerApiService>
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // Avoid infinite loops if the refresh call itself returns 401
        if (response.request.url.encodedPath.contains("api/auth/refresh")) {
            return null
        }

        val refreshToken = tokenManager.getRefreshToken() ?: return null

        synchronized(this) {
            val currentToken = tokenManager.getAccessToken()
            val requestToken = response.request.header("Authorization")?.removePrefix("Bearer ")

            // If the token in the request is already different from what we have, 
            // another thread might have refreshed it. Try with the new one.
            if (currentToken != null && currentToken != requestToken) {
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $currentToken")
                    .build()
            }

            // Otherwise, perform the refresh call.
            return runBlocking {
                try {
                    val refreshResponse = apiProvider.get().refresh(RefreshRequest(refreshToken))
                    if (refreshResponse.isSuccessful) {
                        val body = refreshResponse.body()
                        if (body != null) {
                            tokenManager.saveTokens(body.accessToken, body.refreshToken)
                            return@runBlocking response.request.newBuilder()
                                .header("Authorization", "Bearer ${body.accessToken}")
                                .build()
                        }
                    }
                    null
                } catch (e: Exception) {
                    null
                }
            }
        }
    }
}
