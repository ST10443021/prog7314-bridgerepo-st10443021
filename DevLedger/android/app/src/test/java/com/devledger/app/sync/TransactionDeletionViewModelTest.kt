package com.devledger.app.sync

import androidx.lifecycle.SavedStateHandle
import com.devledger.app.data.local.entity.TransactionEntity
import com.devledger.app.data.repository.TransactionRepository
import com.devledger.app.ui.screens.finance.TransactionDetailViewModel
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionDeletionViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<TransactionRepository>()
    private val row = mockk<TransactionEntity>()
    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { repository.getById("tx") } returns row
    }
    @After fun tearDown() { Dispatchers.resetMain() }
    private fun loaded(): TransactionDetailViewModel = TransactionDetailViewModel(
        SavedStateHandle(mapOf("transactionId" to "tx")), repository).also { dispatcher.scheduler.advanceUntilIdle() }

    @Test fun `successful local deletion signals navigation once`() = runTest(dispatcher) {
        coEvery { repository.delete(row) } just Runs
        val vm = loaded()
        vm.deleteTransaction()
        advanceUntilIdle()
        assertTrue(vm.isDeleted.value)
        assertFalse(vm.deleteFailed.value)
        vm.deleteTransaction()
        advanceUntilIdle()
        coVerify(exactly = 1) { repository.delete(row) }
    }

    @Test fun `failed deletion retains detail and offers retry`() = runTest(dispatcher) {
        coEvery { repository.delete(row) } throws IOException("Disk unavailable")
        val vm = loaded()
        vm.deleteTransaction()
        advanceUntilIdle()
        assertFalse(vm.isDeleted.value)
        assertTrue(vm.deleteFailed.value)
        assertFalse(vm.isDeleting.value)
        assertSame(row, vm.transaction.value)
    }

    @Test fun `missing transaction cannot be deleted`() = runTest(dispatcher) {
        coEvery { repository.getById("tx") } returns null
        val vm = loaded()
        vm.deleteTransaction()
        advanceUntilIdle()
        coVerify(exactly = 0) { repository.delete(any()) }
        assertFalse(vm.isDeleted.value)
    }
}
