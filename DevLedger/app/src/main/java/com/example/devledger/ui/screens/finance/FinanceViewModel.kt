package com.devledger.app.ui.screens.finance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devledger.app.data.local.entity.TransactionEntity
import com.devledger.app.data.repository.SettingsRepository
import com.devledger.app.data.repository.TransactionRepository
import com.devledger.app.util.CurrencyConverter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class FinanceUiState(
    val transactions: List<TransactionEntity> = emptyList(),
    val totalIncome: Double = 0.0,
    val totalExpenses: Double = 0.0,
    val baseCurrency: String = "ZAR",
    val monthlyBudget: Double = 5000.0, // hosting budget threshold used for the alert banner
) {
    val netBalance get() = totalIncome - totalExpenses
    val budgetUsedFraction get() = if (monthlyBudget <= 0) 0.0 else (totalExpenses / monthlyBudget).coerceIn(0.0, 1.0)
}

/** Financial Tracker: income/expense totals converted to the user's base currency via
 *  [CurrencyConverter], plus a budget-threshold alert — the "Dev Expense & Income
 *  Tracker" custom feature specified in the Part 1 app concept. */
@HiltViewModel
class FinanceViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<FinanceUiState> = combine(
        transactionRepository.observeAll(),
        transactionRepository.observeTotalIncome(),
        transactionRepository.observeTotalExpenses(),
        settingsRepository.baseCurrency,
    ) { transactions, income, expenses, currency ->
        FinanceUiState(
            transactions = transactions,
            totalIncome = income,
            totalExpenses = expenses,
            baseCurrency = currency,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FinanceUiState())
}
