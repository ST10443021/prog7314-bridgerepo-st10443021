package com.devledger.app.ui.screens.finance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devledger.app.data.local.entity.TransactionEntity
import com.devledger.app.data.repository.AuthRepository
import com.devledger.app.data.repository.SettingsRepository
import com.devledger.app.data.repository.TransactionRepository
import com.devledger.app.util.CurrencyConverter
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Calendar
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.ExperimentalCoroutinesApi

data class FinanceUiState(
    val transactions: List<TransactionEntity> = emptyList(),
    val totalIncome: Double = 0.0,
    val totalExpenses: Double = 0.0,
    val monthlyExpenses: Double = 0.0,
    val baseCurrency: String = "ZAR",
    val monthlyBudget: Double = 5000.0,
) {
    val netBalance: Double
        get() = totalIncome - totalExpenses

    val budgetUsedFraction: Double
        get() = if (monthlyBudget <= 0.0) {
            0.0
        } else {
            (monthlyExpenses / monthlyBudget).coerceIn(0.0, 1.0)
        }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FinanceViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val settingsRepository: SettingsRepository,
    authRepository: AuthRepository,
) : ViewModel() {

    private val transactions =
        authRepository.currentUser.flatMapLatest { user ->
            if (user == null) {
                flowOf(emptyList())
            } else {
                transactionRepository.observeAllForUser(user.userId)
            }
        }

    val uiState: StateFlow<FinanceUiState> = combine(
        transactions,
        settingsRepository.baseCurrency,
    ) { transactions, currency ->

        val totalIncome = transactions
            .filter { it.type == "INCOME" }
            .sumOf {
                CurrencyConverter.convert(
                    amount = it.amount,
                    from = it.currency,
                    to = currency,
                )
            }

        val totalExpenses = transactions
            .filter { it.type == "EXPENSE" }
            .sumOf {
                CurrencyConverter.convert(
                    amount = it.amount,
                    from = it.currency,
                    to = currency,
                )
            }

        val (monthStart, nextMonthStart) = currentMonthBounds()

        val monthlyExpenses = transactions
            .filter {
                it.type == "EXPENSE" &&
                        it.transactionDate >= monthStart &&
                        it.transactionDate < nextMonthStart
            }
            .sumOf {
                CurrencyConverter.convert(
                    amount = it.amount,
                    from = it.currency,
                    to = currency,
                )
            }

        FinanceUiState(
            transactions = transactions,
            totalIncome = totalIncome,
            totalExpenses = totalExpenses,
            monthlyExpenses = monthlyExpenses,
            baseCurrency = currency,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FinanceUiState(),
    )
}

private fun currentMonthBounds(): Pair<Long, Long> {
    val start = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    val end = (start.clone() as Calendar).apply {
        add(Calendar.MONTH, 1)
    }

    return start.timeInMillis to end.timeInMillis
}