package com.devledger.app.di

import com.devledger.app.BuildConfig
import com.devledger.app.data.remote.DevLedgerApiService
import com.devledger.app.data.remote.auth.AuthInterceptor
import com.devledger.app.data.remote.auth.TokenAuthenticator
import com.devledger.app.data.remote.auth.TokenManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        tokenManager: TokenManager,
        authenticator: TokenAuthenticator
    ): OkHttpClient {
        // Log only the request line and status (BASIC). Bodies are never logged because login and
        // registration bodies carry passwords and the responses carry JWTs, and any app on a
        // debuggable device could read them from logcat (OWASP Foundation, n.d.b). Sensitive
        // headers are redacted as a second line of defence. See REFERENCES-CODE.md.
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
            redactHeader("Authorization")
            redactHeader("Cookie")
            redactHeader("Set-Cookie")
        }
        return OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenManager))
            .authenticator(authenticator)
            .addInterceptor(logging)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideApiService(retrofit: Retrofit): DevLedgerApiService =
        retrofit.create(DevLedgerApiService::class.java)
}
