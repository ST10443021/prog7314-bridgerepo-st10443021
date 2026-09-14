package com.devledger.app.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devledger.app.data.local.entity.SnippetEntity
import com.devledger.app.data.local.entity.UserEntity
import com.devledger.app.data.repository.AuthRepository
import com.devledger.app.data.repository.SettingsRepository
import com.devledger.app.data.repository.SnippetRepository
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

data class DashboardUiState(
    val user: UserEntity? = null,
    val totalSnippets: Int = 0,
    val monthlyIncome: Double = 0.0,
    val monthlyExpenses: Double = 0.0,
    val recentSnippets: List<SnippetEntity> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    authRepository: AuthRepository,
    snippetRepository: SnippetRepository,
    transactionRepository: TransactionRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val transactions =
        authRepository.currentUser.flatMapLatest { user ->
            if (user == null) {
                flowOf(emptyList())
            } else {
                transactionRepository.observeAllForUser(user.userId)
            }
        }

    val uiState: StateFlow<DashboardUiState> = combine(
        authRepository.currentUser,
        snippetRepository.observeSnippets(""),
        transactions,
        settingsRepository.baseCurrency,
    ) { user, snippets, transactions, currency ->

        val (monthStart, nextMonthStart) = currentMonthBounds()

        val monthlyTransactions = transactions.filter {
            it.transactionDate >= monthStart &&
                    it.transactionDate < nextMonthStart
        }

        val monthlyIncome = monthlyTransactions
            .filter { it.type == "INCOME" }
            .sumOf {
                CurrencyConverter.convert(
                    amount = it.amount,
                    from = it.currency,
                    to = currency,
                )
            }

        val monthlyExpenses = monthlyTransactions
            .filter { it.type == "EXPENSE" }
            .sumOf {
                CurrencyConverter.convert(
                    amount = it.amount,
                    from = it.currency,
                    to = currency,
                )
            }

        DashboardUiState(
            user = user,
            totalSnippets = snippets.size,
            monthlyIncome = monthlyIncome,
            monthlyExpenses = monthlyExpenses,
            recentSnippets = snippets.take(3),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState(),
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