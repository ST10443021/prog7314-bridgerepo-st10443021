package com.devledger.app.data.remote.auth

import okhttp3.Interceptor
import okhttp3.Response

/** Attaches the stored JWT bearer token, if any, to every outgoing API request. */
class AuthInterceptor(private val tokenManager: TokenManager) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val token = tokenManager.getAccessToken()
        val request = if (token != null) {
            original.newBuilder().header("Authorization", "Bearer $token").build()
        } else {
            original
        }
        return chain.proceed(request)
    }
}
