package com.devledger.app.security

import com.devledger.app.data.local.PinStorage
import com.devledger.app.data.local.entity.UserEntity
import com.devledger.app.data.remote.dto.UserSettingsDto
import com.devledger.app.data.repository.AuthRepository
import com.devledger.app.data.repository.SettingsRepository
import com.devledger.app.ui.screens.settings.SettingsViewModel
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsBiometricViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<SettingsRepository>()
    private val auth = mockk<AuthRepository>()
    private val pins = mockk<PinStorage>()
    private val user = UserEntity("a", "a@example.test", "A", null)
    private val accounts = MutableStateFlow<UserEntity?>(user)
    private val initial = UserSettingsDto("en", "ZAR", false, false, 2500.0)

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { auth.currentUser } returns accounts
        every { pins.hasPin(user.email) } returns true
        coEvery { repository.loadFromApi() } returns initial
        coEvery { repository.saveToApi(any(), any(), any(), any(), any()) } answers {
            initial.copy(biometricEnabled = secondArg())
        }
    }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun loaded(): SettingsViewModel {
        val vm = SettingsViewModel(repository, auth, pins)
        dispatcher.scheduler.advanceUntilIdle()
        return vm
    }

    @Test fun `direct enable request cannot bypass the native callback`() = runTest(dispatcher) {
        val vm = loaded()
        vm.setBiometricEnabled(true)
        assertFalse(vm.uiState.value.biometricEnabled)
        assertFalse(vm.uiState.value.hasChanges)
    }

    @Test fun `scan success stages change and save sends verified preference`() = runTest(dispatcher) {
        val vm = loaded()
        assertTrue(vm.beginBiometricVerification())
        vm.biometricVerificationSucceeded()
        assertTrue(vm.uiState.value.hasChanges)
        vm.saveSettings()
        advanceUntilIdle()
        coVerify(exactly = 1) { repository.saveToApi("en", true, false, "ZAR", true) }
        assertEquals("Settings saved.", vm.uiState.value.message)
    }

    @Test fun `failed scan permits retry without enabling or saving`() = runTest(dispatcher) {
        val vm = loaded()
        vm.beginBiometricVerification()
        vm.biometricScanNotRecognised("Try again")
        assertTrue(vm.uiState.value.isVerifyingBiometrics)
        assertFalse(vm.uiState.value.biometricEnabled)
        vm.saveSettings()
        advanceUntilIdle()
        coVerify(exactly = 0) { repository.saveToApi(any(), any(), any(), any(), any()) }
    }

    @Test fun `cancelled scan and late success leave preference disabled`() = runTest(dispatcher) {
        val vm = loaded()
        vm.beginBiometricVerification()
        vm.biometricVerificationFailed("Cancelled")
        vm.biometricVerificationSucceeded()
        assertFalse(vm.uiState.value.biometricEnabled)
        assertFalse(vm.uiState.value.hasChanges)
    }

    @Test fun `API failure preserves draft and does not announce success`() = runTest(dispatcher) {
        coEvery { repository.saveToApi(any(), any(), any(), any(), any()) } throws IOException()
        val vm = loaded()
        vm.beginBiometricVerification()
        vm.biometricVerificationSucceeded()
        vm.saveSettings()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.hasChanges)
        assertNotNull(vm.uiState.value.error)
        assertNull(vm.uiState.value.message)
    }

    @Test fun `account switch after scan prevents saving under another account`() = runTest(dispatcher) {
        val vm = loaded()
        vm.beginBiometricVerification()
        vm.biometricVerificationSucceeded()
        accounts.value = user.copy(userId = "b", email = "b@example.test")
        vm.saveSettings()
        advanceUntilIdle()
        coVerify(exactly = 0) { repository.saveToApi(any(), any(), any(), any(), any()) }
        assertNotNull(vm.uiState.value.error)
    }

    @Test fun `disable saves false without discarding fallback PIN`() = runTest(dispatcher) {
        coEvery { repository.loadFromApi() } returns initial.copy(biometricEnabled = true)
        val vm = loaded()
        vm.setBiometricEnabled(false)
        vm.saveSettings()
        advanceUntilIdle()
        coVerify { repository.saveToApi("en", false, false, "ZAR", false) }
        verify(exactly = 0) { pins.clearPin(any()) }
    }
}
