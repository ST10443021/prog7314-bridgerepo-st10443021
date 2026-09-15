package com.devledger.app.data.remote

import com.devledger.app.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.*

/**
 * Retrofit contract for the DevLedger REST API. Endpoint shapes match the
 * "5.2 Endpoint Specification" table of the Part 1 Planning and Design document
 * exactly, so the mobile client and the ASP.NET Core backend in /api stay in sync.
 */
interface DevLedgerApiService {

    @POST("api/auth/sso")
    suspend fun ssoLogin(@Body request: SsoLoginRequest): Response<AuthResponse>

    @POST("api/auth/login")
    suspend fun emailLogin(@Body request: EmailLoginRequest): Response<AuthResponse>

    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>

    @POST("api/auth/refresh")
    suspend fun refresh(@Body request: RefreshRequest): Response<AuthResponse>

    @GET("api/settings")
    suspend fun getSettings(): Response<UserSettingsDto>

    @PUT("api/settings")
    suspend fun updateSettings(
        @Body settings: UserSettingsDto
    ): Response<UserSettingsDto>

    @GET("api/users/me")
    suspend fun getProfile(): Response<UserProfileDto>

    @PUT("api/users/me")
    suspend fun updateProfile(
        @Body request: UpdateUserProfileRequest,
    ): Response<UserProfileDto>

    @GET("api/snippets")
    suspend fun getSnippets(): Response<List<SnippetDto>>

    @POST("api/snippets")
    suspend fun createSnippet(@Body request: CreateSnippetRequest): Response<SnippetDto>

    @PUT("api/snippets/{id}")
    suspend fun updateSnippet(@Path("id") id: String, @Body request: CreateSnippetRequest): Response<SnippetDto>

    @DELETE("api/snippets/{id}")
    suspend fun deleteSnippet(@Path("id") id: String): Response<Unit>

    @GET("api/folders")
    suspend fun getFolders(): Response<List<FolderDto>>

    @POST("api/folders")
    suspend fun createFolder(@Body request: CreateFolderRequest): Response<FolderDto>

    @PUT("api/folders/{id}")
    suspend fun renameFolder(@Path("id") id: String, @Body request: CreateFolderRequest): Response<FolderDto>

    @HTTP(method = "DELETE", path = "api/folders/{id}", hasBody = true)
    suspend fun deleteFolder(@Path("id") id: String, @Body request: DeleteFolderRequest): Response<Unit>
    @GET("api/transactions")
    suspend fun getTransactions(): Response<List<TransactionDto>>
    @POST("api/transactions")
    suspend fun createTransaction(@Body request: CreateTransactionRequest): Response<TransactionDto>

    @PUT("api/transactions/{id}")
    suspend fun updateTransaction(
        @Path("id") id: String,
        @Body request: CreateTransactionRequest,
    ): Response<TransactionDto>

    @DELETE("api/transactions/{id}")
    suspend fun deleteTransaction(
        @Path("id") id: String,
    ): Response<Unit>

    @GET("api/transactions/summary")
    suspend fun getTransactionSummary(): Response<TransactionSummaryDto>

    @POST("api/sync/batch")
    suspend fun syncBatch(@Body request: SyncBatchRequest): Response<SyncBatchResponse>

    @POST("api/notifications/register")
    suspend fun registerDevice(@Body request: RegisterDeviceRequest): Response<Unit>
}
