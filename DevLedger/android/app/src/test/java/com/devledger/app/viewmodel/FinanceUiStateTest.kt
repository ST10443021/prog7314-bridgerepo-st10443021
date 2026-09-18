package com.devledger.app.viewmodel

import com.devledger.app.ui.screens.finance.FinanceUiState
import com.devledger.app.ui.screens.finance.MonthlyStat
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
            incomeStat = MonthlyStat(5000.0, null, emptyList<Pair<String, Double>>()),
            expenseStat = MonthlyStat(1200.0, null, emptyList<Pair<String, Double>>()),
        )

        assertThat(state.netBalance).isEqualTo(3800.0)
    }

    @Test
    fun `budgetUsedFraction is zero when there is no budget set`() {
        val state = FinanceUiState(
            expenseStat = MonthlyStat(500.0, null, emptyList<Pair<String, Double>>()),
            monthlyBudget = 0.0,
        )

        assertThat(state.budgetUsedFraction).isEqualTo(0.0)
    }

    @Test
    fun `budgetUsedFraction reflects monthly expenses as proportion of budget`() {
        val state = FinanceUiState(
            expenseStat = MonthlyStat(2500.0, null, emptyList<Pair<String, Double>>()),
            monthlyBudget = 5000.0,
        )

        assertThat(state.budgetUsedFraction).isEqualTo(0.5)
    }

    @Test
    fun `budgetUsedFraction is capped at 1_0 when monthly expenses exceed budget`() {
        val state = FinanceUiState(
            expenseStat = MonthlyStat(9000.0, null, emptyList<Pair<String, Double>>()),
            monthlyBudget = 5000.0,
        )

        assertThat(state.budgetUsedFraction).isEqualTo(1.0)
    }
}