package com.devledger.app.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.devledger.app.data.local.PinStorage
import com.devledger.app.data.local.dao.UserDao
import com.devledger.app.data.local.entity.UserEntity
import com.devledger.app.data.remote.DevLedgerApiService
import com.devledger.app.data.remote.dto.UserSettingsDto
import com.devledger.app.data.repository.SettingsRepository
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BiometricSettingsRepositoryTest {
    private val api = mockk<DevLedgerApiService>()
    private val dao = mockk<UserDao>()
    private val pins = mockk<PinStorage>(relaxed = true)
    private var user = UserEntity("a", "a@example.test", "A", null, biometricEnabled = false)
    private val remote = UserSettingsDto("en", "ZAR", false, false, 2500.0)
    private lateinit var repository: SettingsRepository

    @Before fun setUp() {
        every { dao.observeCurrentUser() } returns flowOf(user)
        coEvery { dao.getCurrentUser() } answers { user }
        coEvery { dao.updateSettings(any(), any(), any(), any()) } just Runs
        every { pins.hasPin(user.email) } returns true
        coEvery { api.getSettings() } returns Response.success(remote)
        coEvery { api.updateSettings(any()) } answers { Response.success(firstArg<UserSettingsDto>()) }
        repository = SettingsRepository(ApplicationProvider.getApplicationContext<Context>(), api, dao, pins)
    }

    @Test fun `server true alone never enables local biometrics`() = runBlocking {
        coEvery { api.getSettings() } returns Response.success(remote.copy(biometricEnabled = true))
        val actual = repository.loadFromApi()
        assertFalse(actual.biometricEnabled)
        verify(exactly = 0) { pins.setFingerprintRegistered(any(), any()) }
    }

    @Test fun `enable without native verification is rejected before HTTP write`() = runBlocking {
        try {
            repository.saveToApi("en", true, false, "ZAR")
            fail("Expected verification requirement")
        } catch (_: IllegalArgumentException) { }
        coVerify(exactly = 0) { api.updateSettings(any()) }
    }

    @Test fun `acknowledged enable persists profile flag and preserves budget`() = runBlocking {
        val saved = repository.saveToApi("en", true, false, "ZAR", biometricVerified = true)
        assertEquals(remote.monthlyBudget, saved.monthlyBudget)
        verify { pins.setFingerprintRegistered(true, user.email) }
        coVerify { dao.updateSettings(user.userId, "en", true, "ZAR") }
    }

    @Test fun `failed PUT never commits local verification or account preference`() = runBlocking {
        coEvery { api.updateSettings(any()) } throws IOException("Disconnected")
        try {
            repository.saveToApi("en", true, false, "ZAR", biometricVerified = true)
            fail("Expected failure")
        } catch (_: IOException) { }
        verify(exactly = 0) { pins.setFingerprintRegistered(any(), any()) }
        coVerify(exactly = 0) { dao.updateSettings(any(), any(), any(), any()) }
    }

    @Test fun `account change during fetch prevents PUT and local mutation`() = runBlocking {
        coEvery { api.getSettings() } answers {
            user = user.copy(userId = "b", email = "b@example.test")
            Response.success(remote)
        }
        try {
            repository.saveToApi("en", false, false, "ZAR")
            fail("Expected account guard")
        } catch (_: IllegalStateException) { }
        coVerify(exactly = 0) { api.updateSettings(any()) }
        coVerify(exactly = 0) { dao.updateSettings(any(), any(), any(), any()) }
    }
}
