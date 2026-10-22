package com.devledger.app.notifications

import android.app.Application
import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.devledger.app.data.local.dao.UserDao
import com.devledger.app.data.local.entity.UserEntity
import com.devledger.app.data.remote.DevLedgerApiService
import com.devledger.app.data.remote.auth.TokenManager
import com.devledger.app.data.remote.dto.RegisterDeviceRequest
import com.google.firebase.installations.FirebaseInstallationsException
import io.mockk.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class DeviceRegistrarTest {
    private val api = mockk<DevLedgerApiService>()
    private val tokens = mockk<TokenManager>()
    private val users = mockk<UserDao>()
    private val fcm = mockk<FcmTokenProvider>()
    private val user = UserEntity("account-a", "a@example.test", "A", null)
    private lateinit var context: Context
    private lateinit var registrar: DeviceRegistrar

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        Settings.Secure.putString(context.contentResolver, Settings.Secure.ANDROID_ID, "test-device")
        every { tokens.isLoggedIn() } returns true
        coEvery { users.getCurrentUser() } returns user
        coEvery { fcm.currentToken() } returns "current-fcm-token"
        coEvery { api.registerDevice(any()) } returns Response.success(Unit)
        registrar = DeviceRegistrar(context, api, tokens, users, fcm)
    }

    @Test fun `success requires an API acknowledgement with the current token and device`() = runTest {
        assertEquals(DeviceRegistrationOutcome.REGISTERED, registrar.registerCurrentToken())
        coVerify(exactly = 1) {
            api.registerDevice(RegisterDeviceRequest(fcmToken = "current-fcm-token", deviceId = "test-device"))
        }
    }

    @Test fun `temporary HTTP errors remain retryable`() = runTest {
        for (code in listOf(408, 429, 500, 502, 503, 504, 599)) {
            coEvery { api.registerDevice(any()) } returns Response.error(code, "temporary".toResponseBody())
            assertEquals("HTTP $code", DeviceRegistrationOutcome.RETRY, registrar.registerCurrentToken())
        }
    }

    @Test fun `permanent HTTP and rejected credentials do not retry forever`() = runTest {
        for (code in listOf(400, 401, 403, 404, 409, 422)) {
            coEvery { api.registerDevice(any()) } returns Response.error(code, "rejected".toResponseBody())
            assertEquals("HTTP $code", DeviceRegistrationOutcome.REJECTED, registrar.registerCurrentToken())
        }
    }

    @Test fun `API network exception requests retry`() = runTest {
        coEvery { api.registerDevice(any()) } throws IOException("offline")
        assertEquals(DeviceRegistrationOutcome.RETRY, registrar.registerCurrentToken())
    }

    @Test fun `Firebase network exception requests retry without an API call`() = runTest {
        coEvery { fcm.currentToken() } throws IOException("SERVICE_NOT_AVAILABLE")
        assertEquals(DeviceRegistrationOutcome.RETRY, registrar.registerCurrentToken())
        coVerify(exactly = 0) { api.registerDevice(any()) }
    }

    @Test fun `Firebase bad configuration stops automatic retries`() = runTest {
        coEvery { fcm.currentToken() } throws
            FirebaseInstallationsException(FirebaseInstallationsException.Status.BAD_CONFIG)
        assertEquals(DeviceRegistrationOutcome.REJECTED, registrar.registerCurrentToken())
    }

    @Test fun `Firebase service failure requests retry`() = runTest {
        coEvery { fcm.currentToken() } throws
            FirebaseInstallationsException(FirebaseInstallationsException.Status.UNAVAILABLE)
        assertEquals(DeviceRegistrationOutcome.RETRY, registrar.registerCurrentToken())
    }

    @Test fun `Firebase rate limit requests retry`() = runTest {
        coEvery { fcm.currentToken() } throws
            FirebaseInstallationsException(FirebaseInstallationsException.Status.TOO_MANY_REQUESTS)
        assertEquals(DeviceRegistrationOutcome.RETRY, registrar.registerCurrentToken())
    }

    @Test fun `token acquisition timeout requests retry`() = runTest {
        coEvery { fcm.currentToken() } coAnswers { awaitCancellation() }
        assertEquals(DeviceRegistrationOutcome.RETRY, registrar.registerCurrentToken())
        coVerify(exactly = 0) { api.registerDevice(any()) }
    }

    @Test fun `blank Firebase token is never uploaded`() = runTest {
        coEvery { fcm.currentToken() } returns " "
        assertEquals(DeviceRegistrationOutcome.RETRY, registrar.registerCurrentToken())
        coVerify(exactly = 0) { api.registerDevice(any()) }
    }

    @Test fun `signed out does not fetch or upload a token`() = runTest {
        every { tokens.isLoggedIn() } returns false
        assertEquals(DeviceRegistrationOutcome.SIGNED_OUT, registrar.registerCurrentToken())
        coVerify(exactly = 0) { fcm.currentToken() }
        coVerify(exactly = 0) { api.registerDevice(any()) }
    }

    @Test fun `missing local account does not register`() = runTest {
        coEvery { users.getCurrentUser() } returns null
        assertEquals(DeviceRegistrationOutcome.SIGNED_OUT, registrar.registerCurrentToken())
        coVerify(exactly = 0) { api.registerDevice(any()) }
    }

    @Test fun `sign out during Firebase acquisition prevents upload`() = runTest {
        coEvery { fcm.currentToken() } coAnswers {
            every { tokens.isLoggedIn() } returns false
            "current-fcm-token"
        }
        assertEquals(DeviceRegistrationOutcome.SIGNED_OUT, registrar.registerCurrentToken())
        coVerify(exactly = 0) { api.registerDevice(any()) }
    }

    @Test fun `account change during Firebase acquisition prevents obsolete upload`() = runTest {
        coEvery { fcm.currentToken() } coAnswers {
            coEvery { users.getCurrentUser() } returns user.copy(userId = "account-b")
            "current-fcm-token"
        }
        assertEquals(DeviceRegistrationOutcome.SESSION_CHANGED, registrar.registerCurrentToken())
        coVerify(exactly = 0) { api.registerDevice(any()) }
    }

    @Test fun `retry reads rotated token instead of reusing an old payload`() = runTest {
        coEvery { fcm.currentToken() } returnsMany listOf("old-token", "new-token")
        coEvery { api.registerDevice(any()) } returnsMany listOf(
            Response.error(503, "unavailable".toResponseBody()), Response.success(Unit),
        )
        assertEquals(DeviceRegistrationOutcome.RETRY, registrar.registerCurrentToken())
        assertEquals(DeviceRegistrationOutcome.REGISTERED, registrar.registerCurrentToken())
        coVerifyOrder {
            api.registerDevice(RegisterDeviceRequest(fcmToken = "old-token", deviceId = "test-device"))
            api.registerDevice(RegisterDeviceRequest(fcmToken = "new-token", deviceId = "test-device"))
        }
    }

    @Test fun `Firebase cancellation propagates instead of reporting success`() = runTest {
        coEvery { fcm.currentToken() } throws CancellationException("worker stopped")
        try {
            registrar.registerCurrentToken()
            fail("Cancellation was swallowed")
        } catch (_: CancellationException) { }
        coVerify(exactly = 0) { api.registerDevice(any()) }
    }

    @Test fun `API cancellation propagates instead of reporting success`() = runTest {
        coEvery { api.registerDevice(any()) } throws CancellationException("worker stopped")
        try {
            registrar.registerCurrentToken()
            fail("Cancellation was swallowed")
        } catch (_: CancellationException) { }
    }

    @Test fun `unexpected client configuration failure stops retries`() = runTest {
        coEvery { fcm.currentToken() } throws IllegalStateException("Firebase not initialized")
        assertEquals(DeviceRegistrationOutcome.REJECTED, registrar.registerCurrentToken())
    }

    @Test fun `missing device id never registers a shared unknown device`() = runTest {
        Settings.Secure.putString(context.contentResolver, Settings.Secure.ANDROID_ID, "")
        assertEquals(DeviceRegistrationOutcome.REJECTED, registrar.registerCurrentToken())
        coVerify(exactly = 0) { api.registerDevice(any()) }
    }
}
