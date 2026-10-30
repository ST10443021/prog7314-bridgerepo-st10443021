package com.devledger.app.data.remote.auth

import okhttp3.Interceptor
import okhttp3.Response

// Reference scope: Token storage and OkHttp authenticators. (Google Developers, n.d.e; Square, n.d.). See REFERENCES-CODE.md for full entries.

/** Attaches the stored JWT bearer token, if any, to every outgoing API request. */
class AuthInterceptor(private val tokenManager: TokenManager) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val path = original.url.encodedPath

        // Skip adding the Authorization header for auth endpoints
        if (path.contains("api/auth/login") ||
            path.contains("api/auth/register") ||
            path.contains("api/auth/refresh") ||
            path.contains("api/auth/sso")
        ) {
            return chain.proceed(original)
        }

        val token = tokenManager.getAccessToken()
        val request = if (token != null) {
            original.newBuilder().header("Authorization", "Bearer $token").build()
        } else {
            original
        }
        return chain.proceed(request)
    }
}
