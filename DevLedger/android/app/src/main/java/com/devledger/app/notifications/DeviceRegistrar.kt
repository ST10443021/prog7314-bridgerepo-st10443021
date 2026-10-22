package com.devledger.app.notifications

import android.content.Context
import android.provider.Settings
import android.util.Log
import androidx.work.WorkManager
import com.devledger.app.data.local.dao.UserDao
import com.devledger.app.data.remote.DevLedgerApiService
import com.devledger.app.data.remote.auth.TokenManager
import com.devledger.app.data.remote.dto.RegisterDeviceRequest
import com.google.firebase.installations.FirebaseInstallationsException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Outcomes describe registration, not delivery of a push notification. */
enum class DeviceRegistrationOutcome { REGISTERED, RETRY, SIGNED_OUT, SESSION_CHANGED, REJECTED }

/** Upload the latest token on every attempt; never persist an FCM/JWT token in WorkData. */
@Singleton
class DeviceRegistrar @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: DevLedgerApiService,
    private val tokenManager: TokenManager,
    private val userDao: UserDao,
    private val tokenProvider: FcmTokenProvider,
) {
    fun scheduleRegistration() = RegisterDeviceWorker.enqueue(context, replaceExisting = true)

    fun cancelRegistration() = WorkManager.getInstance(context)
        .cancelUniqueWork(RegisterDeviceWorker.WORK_NAME)

    suspend fun registerCurrentToken(): DeviceRegistrationOutcome {
        try {
            if (!tokenManager.isLoggedIn()) return DeviceRegistrationOutcome.SIGNED_OUT
            val userId = userDao.getCurrentUser()?.userId
                ?: return DeviceRegistrationOutcome.SIGNED_OUT

            // A stalled Firebase Task must not occupy the worker indefinitely. Parent
            // cancellation still propagates, including WorkManager stop/sign-out.
            val token = withTimeoutOrNull(TOKEN_TIMEOUT_MS) { tokenProvider.currentToken() }
            if (token.isNullOrBlank()) return DeviceRegistrationOutcome.RETRY

            currentCoroutineContext().ensureActive()
            if (!tokenManager.isLoggedIn()) return DeviceRegistrationOutcome.SIGNED_OUT
            if (userDao.getCurrentUser()?.userId != userId) {
                return DeviceRegistrationOutcome.SESSION_CHANGED
            }
            val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            if (deviceId.isNullOrBlank()) {
                Log.w(TAG, "Device registration rejected: missing device identifier")
                return DeviceRegistrationOutcome.REJECTED
            }

            val response = api.registerDevice(RegisterDeviceRequest(fcmToken = token, deviceId = deviceId))
            response.errorBody()?.close()
            val result = when {
                response.isSuccessful -> DeviceRegistrationOutcome.REGISTERED
                response.code() == 408 || response.code() == 429 || response.code() in 500..599 ->
                    DeviceRegistrationOutcome.RETRY
                else -> DeviceRegistrationOutcome.REJECTED
            }
            if (result == DeviceRegistrationOutcome.REGISTERED) {
                Log.i(TAG, "Device token registration acknowledged")
            } else {
                // Neither token values nor error bodies/exceptions belong in logs.
                Log.w(TAG, "Device token registration: HTTP ${response.code()}, outcome=$result")
            }
            return result
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            currentCoroutineContext().ensureActive()
            Log.w(TAG, "Device token registration deferred: network unavailable")
            return DeviceRegistrationOutcome.RETRY
        } catch (e: FirebaseInstallationsException) {
            currentCoroutineContext().ensureActive()
            return if (e.status == FirebaseInstallationsException.Status.BAD_CONFIG) {
                Log.w(TAG, "Device token registration rejected: Firebase configuration")
                DeviceRegistrationOutcome.REJECTED
            } else {
                Log.w(TAG, "Device token registration deferred: Firebase unavailable")
                DeviceRegistrationOutcome.RETRY
            }
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            Log.w(TAG, "Device token registration rejected: unexpected client failure")
            return DeviceRegistrationOutcome.REJECTED
        }
    }

    private companion object {
        const val TAG = "DeviceRegistrar"
        const val TOKEN_TIMEOUT_MS = 30_000L
    }
}
