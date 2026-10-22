package com.devledger.app.notifications

import com.devledger.app.data.local.PinStorage
import com.devledger.app.data.local.dao.UserDao
import com.devledger.app.data.remote.DevLedgerApiService
import com.devledger.app.data.remote.auth.TokenManager
import com.devledger.app.data.remote.dto.AuthResponse
import com.devledger.app.data.repository.AuthRepository
import com.devledger.app.util.DevLedgerResult
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class AuthRegistrationTest {
    private val api = mockk<DevLedgerApiService>()
    private val tokens = mockk<TokenManager>(relaxed = true)
    private val users = mockk<UserDao>(relaxed = true)
    private val registrar = mockk<DeviceRegistrar>(relaxed = true)
    private val pins = mockk<PinStorage>(relaxed = true)
    private lateinit var repository: AuthRepository
    private val response = AuthResponse("access", "refresh", "account-a", "A", "a@example.test",
        pinHash = "hash", pinSalt = "salt", biometricEnabled = false)

    @Before fun setUp() {
        every { users.observeCurrentUser() } returns flowOf(null)
        repository = AuthRepository(api, tokens, users, registrar, pins)
    }

    @Test fun `email login persists the account then schedules without running registration inline`() = runTest {
        coEvery { api.emailLogin(any()) } returns Response.success(response)
        assertTrue(repository.signInWithEmail("a@example.test", "password") is DevLedgerResult.Success)
        coVerifyOrder {
            tokens.saveTokens("access", "refresh")
            users.replaceCurrentUser(match { it.userId == "account-a" })
            registrar.scheduleRegistration()
        }
        coVerify(exactly = 0) { registrar.registerCurrentToken() }
    }

    @Test fun `SSO login schedules registration`() = runTest {
        coEvery { api.ssoLogin(any()) } returns Response.success(response)
        assertTrue(repository.signInWithSso("GOOGLE", idToken = "id") is DevLedgerResult.Success)
        verify(exactly = 1) { registrar.scheduleRegistration() }
        coVerify(exactly = 0) { registrar.registerCurrentToken() }
    }

    @Test fun `new account registration schedules registration`() = runTest {
        coEvery { api.register(any()) } returns Response.success(response)
        assertTrue(repository.register("a@example.test", "password", "A") is DevLedgerResult.Success)
        verify(exactly = 1) { registrar.scheduleRegistration() }
        coVerify(exactly = 0) { registrar.registerCurrentToken() }
    }

    @Test fun `failed login never schedules registration`() = runTest {
        coEvery { api.emailLogin(any()) } returns Response.error(401, "rejected".toResponseBody())
        assertTrue(repository.signInWithEmail("a@example.test", "bad") is DevLedgerResult.Error)
        verify(exactly = 0) { registrar.scheduleRegistration() }
    }

    @Test fun `sign out clears credentials and cancels queued registration`() = runTest {
        repository.signOut()
        coVerifyOrder {
            tokens.clear()
            registrar.cancelRegistration()
            users.clear()
        }
    }
}
