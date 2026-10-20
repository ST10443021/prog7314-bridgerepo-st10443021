package com.devledger.app.security

import com.devledger.app.data.local.PinCredentials
import com.devledger.app.data.local.PinStorage
import com.devledger.app.data.local.entity.UserEntity
import com.devledger.app.data.repository.AuthRepository
import com.devledger.app.ui.screens.biometric.SecuritySetupViewModel
import com.devledger.app.util.DevLedgerResult
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BiometricSecuritySetupTest {
    private val dispatcher = StandardTestDispatcher()
    private val pins = mockk<PinStorage>(relaxed = true)
    private val auth = mockk<AuthRepository>()
    private val user = UserEntity("a", "a@example.test", "A", null)
    private val credentials = PinCredentials("encoded-hash", "encoded-salt")

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { auth.currentUser } returns flowOf(user)
        every { pins.createPinCredentials("1234") } returns credentials
    }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun setup() = SecuritySetupViewModel(pins, auth).also {
        it.pin = "1234"
        it.confirmPin = "1234"
    }

    @Test fun `unverified enable cannot save security setup`() = runTest(dispatcher) {
        val vm = setup()
        vm.isFingerprintEnabled = true
        vm.saveSetup()?.join()
        advanceUntilIdle()
        coVerify(exactly = 0) { auth.saveSecurityProfile(any(), any(), any()) }
        assertFalse(vm.isSaved)
    }

    @Test fun `server failure leaves no new local PIN or biometric approval`() = runTest(dispatcher) {
        coEvery { auth.saveSecurityProfile(any(), any(), any()) } returns DevLedgerResult.Error("Offline")
        val vm = setup()
        vm.saveSetup()?.join()
        advanceUntilIdle()
        verify(exactly = 0) { pins.savePinCredentials(any(), any()) }
        verify(exactly = 0) { pins.setFingerprintRegistered(any(), any()) }
        assertFalse(vm.isSaved)
        assertNotNull(vm.errorText)
    }

    @Test fun `verified setup saves through existing API before local activation`() = runTest(dispatcher) {
        coEvery { auth.saveSecurityProfile(any(), any(), any()) } returns DevLedgerResult.Success(user)
        val vm = setup()
        vm.isFingerprintEnabled = true
        vm.isFingerprintVerified = true
        vm.saveSetup()?.join()
        advanceUntilIdle()
        coVerify { auth.saveSecurityProfile(credentials.hash, credentials.salt, true) }
        verify { pins.savePinCredentials(credentials, user.email) }
        verify { pins.setFingerprintRegistered(true, user.email) }
        assertTrue(vm.isSaved)
    }
}
