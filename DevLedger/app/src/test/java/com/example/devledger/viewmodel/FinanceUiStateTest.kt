package com.devledger.app.viewmodel

import com.devledger.app.ui.screens.finance.FinanceUiState
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Pure unit tests for [FinanceUiState]'s derived properties — the budget-threshold
 *  alert logic behind the Financial Tracker's custom "budget alerts" feature. */
class FinanceUiStateTest {

    @Test
    fun `netBalance is income minus expenses`() {
        val state = FinanceUiState(totalIncome = 5000.0, totalExpenses = 1200.0)
        assertThat(state.netBalance).isEqualTo(3800.0)
    }

    @Test
    fun `budgetUsedFraction is zero when there is no budget set`() {
        val state = FinanceUiState(totalExpenses = 500.0, monthlyBudget = 0.0)
        assertThat(state.budgetUsedFraction).isEqualTo(0.0)
    }

    @Test
    fun `budgetUsedFraction reflects expenses as a proportion of the budget`() {
        val state = FinanceUiState(totalExpenses = 2500.0, monthlyBudget = 5000.0)
        assertThat(state.budgetUsedFraction).isEqualTo(0.5)
    }

    @Test
    fun `budgetUsedFraction is capped at 1_0 even when expenses exceed the budget`() {
        val state = FinanceUiState(totalExpenses = 9000.0, monthlyBudget = 5000.0)
        assertThat(state.budgetUsedFraction).isEqualTo(1.0)
    }
}
