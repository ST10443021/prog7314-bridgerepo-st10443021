package com.devledger.app.notifications

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.devledger.app.data.remote.DevLedgerApiService
import com.devledger.app.data.remote.dto.RegisterDeviceRequest
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registers this device's current FCM token with the API (Firebase, n.d.) so the
 * backend can target push notifications at it. This is the missing link behind
 * "Implementation of Real-time Notification" — the messaging service and endpoint
 * already existed, but nothing was actually calling this before now. Called once
 * after every successful sign-in ([com.devledger.app.data.repository.AuthRepository])
 * and again whenever FCM issues a new token
 * ([DevLedgerMessagingService.onNewToken] via [RegisterDeviceWorker]).
 */
@Singleton
/**
 * Reference scope: FCM device tokens and message handling.
 * (Firebase, n.d.). See REFERENCES-CODE.md for full entries.
 */
class DeviceRegistrar @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: DevLedgerApiService,
) {
    private val deviceId: String
        get() = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown-device"

    suspend fun registerCurrentToken() {
        try {
            val token = FirebaseMessaging.getInstance().token.await()
            val response = api.registerDevice(RegisterDeviceRequest(fcmToken = token, deviceId = deviceId))
            if (!response.isSuccessful) {
                Log.w(TAG, "Device token registration rejected: HTTP ${response.code()}")
            } else {
                Log.i(TAG, "Device token registered for push notifications")
            }
        } catch (e: Exception) {
            // No connectivity, or called before the user is authenticated — safe to
            // skip; it's retried on the next sign-in or token refresh, not critical-path.
            Log.w(TAG, "Device token registration skipped: ${e.message}")
        }
    }

    private companion object {
        const val TAG = "DeviceRegistrar"
    }
}