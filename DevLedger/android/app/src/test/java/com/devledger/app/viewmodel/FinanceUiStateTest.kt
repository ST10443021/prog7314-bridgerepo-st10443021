package com.devledger.app.viewmodel

import com.devledger.app.data.local.entity.TransactionEntity
import com.devledger.app.data.local.entity.UserEntity
import com.devledger.app.data.repository.AuthRepository
import com.devledger.app.data.repository.SettingsRepository
import com.devledger.app.data.repository.TransactionRepository
import com.devledger.app.ui.screens.finance.FinanceViewModel
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test

// Reference scope: JVM unit testing for Android with JUnit, MockK and Truth. (Google Developers, n.d.s). See REFERENCES-CODE.md for full entries.

/** Exercises the current view-model calculation rather than removed budget-state fields. */
@OptIn(ExperimentalCoroutinesApi::class)
class FinanceUiStateTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun transaction(type: String, amount: Double, month: Int): TransactionEntity =
        mockk<TransactionEntity>(relaxed = true).also { tx ->
            every { tx.type } returns type
            every { tx.amount } returns amount
            every { tx.currency } returns "ZAR"
            every { tx.category } returns "Test"
            every { tx.transactionDate } returns Calendar.getInstance().apply {
                clear(); set(2026, month, 15, 12, 0, 0)
            }.timeInMillis
        }

    private fun viewModel(transactions: List<TransactionEntity>, budget: Double = 0.0): FinanceViewModel {
        val repository = mockk<TransactionRepository>()
        val settings = mockk<SettingsRepository>()
        val auth = mockk<AuthRepository>()
        every { settings.baseCurrency } returns flowOf("ZAR")
        every { settings.monthlyBudget } returns flowOf(budget)
        every { auth.currentUser } returns flowOf(UserEntity("a", "a@example.test", "A", null))
        every { repository.observeAllForUser("a") } returns flowOf(transactions)
        return FinanceViewModel(repository, settings, auth)
    }

    @Test fun `selected month balance excludes other months`() = runTest(dispatcher) {
        val vm = viewModel(listOf(
            transaction("INCOME", 5000.0, Calendar.OCTOBER),
            transaction("EXPENSE", 1200.0, Calendar.OCTOBER),
            transaction("EXPENSE", 9999.0, Calendar.SEPTEMBER),
        ))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect() }
        vm.onMonthSelected(2026, Calendar.OCTOBER)
        advanceUntilIdle()
        assertThat(vm.uiState.value.incomeStat.total).isEqualTo(5000.0)
        assertThat(vm.uiState.value.expenseStat.total).isEqualTo(1200.0)
        assertThat(vm.uiState.value.netBalance).isEqualTo(3800.0)
    }

    @Test fun `month comparison uses previous month net balance`() = runTest(dispatcher) {
        val vm = viewModel(listOf(
            transaction("INCOME", 5000.0, Calendar.OCTOBER),
            transaction("EXPENSE", 1200.0, Calendar.OCTOBER),
            transaction("INCOME", 1000.0, Calendar.SEPTEMBER),
            transaction("EXPENSE", 200.0, Calendar.SEPTEMBER),
        ))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect() }
        vm.onMonthSelected(2026, Calendar.OCTOBER)
        advanceUntilIdle()
        assertThat(vm.uiState.value.netBalancePercentChange).isEqualTo(375.0)
    }

    @Test fun `empty month has zero balance and no fabricated percentage`() = runTest(dispatcher) {
        val vm = viewModel(emptyList())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect() }
        vm.onMonthSelected(2026, Calendar.OCTOBER)
        advanceUntilIdle()
        assertThat(vm.uiState.value.netBalance).isEqualTo(0.0)
        assertThat(vm.uiState.value.netBalancePercentChange).isNull()
    }
}
