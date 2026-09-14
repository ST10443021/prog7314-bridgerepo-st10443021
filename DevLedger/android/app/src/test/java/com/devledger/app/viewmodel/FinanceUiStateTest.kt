package com.devledger.app.viewmodel

import com.devledger.app.ui.screens.finance.FinanceUiState
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for the Financial Tracker's derived balance and
 * current-month budget threshold calculations.
 */
class FinanceUiStateTest {

    @Test
    fun `netBalance is income minus expenses`() {
        val state = FinanceUiState(
            totalIncome = 5000.0,
            totalExpenses = 1200.0,
        )

        assertThat(state.netBalance).isEqualTo(3800.0)
    }

    @Test
    fun `budgetUsedFraction is zero when there is no budget set`() {
        val state = FinanceUiState(
            monthlyExpenses = 500.0,
            monthlyBudget = 0.0,
        )

        assertThat(state.budgetUsedFraction).isEqualTo(0.0)
    }

    @Test
    fun `budgetUsedFraction reflects monthly expenses as proportion of budget`() {
        val state = FinanceUiState(
            monthlyExpenses = 2500.0,
            monthlyBudget = 5000.0,
        )

        assertThat(state.budgetUsedFraction).isEqualTo(0.5)
    }

    @Test
    fun `budgetUsedFraction is capped at 1_0 when monthly expenses exceed budget`() {
        val state = FinanceUiState(
            monthlyExpenses = 9000.0,
            monthlyBudget = 5000.0,
        )

        assertThat(state.budgetUsedFraction).isEqualTo(1.0)
    }
}