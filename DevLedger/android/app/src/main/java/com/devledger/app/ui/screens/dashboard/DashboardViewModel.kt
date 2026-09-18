package com.devledger.app.ui.screens.dashboard

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devledger.app.data.local.entity.SnippetEntity
import com.devledger.app.data.local.entity.TransactionEntity
import com.devledger.app.data.local.entity.UserEntity
import com.devledger.app.data.repository.AuthRepository
import com.devledger.app.data.repository.SettingsRepository
import com.devledger.app.data.repository.SnippetRepository
import com.devledger.app.data.repository.TransactionRepository
import com.devledger.app.util.CurrencyConverter
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Calendar
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

data class DashboardUiState(
    val user: UserEntity? = null,
    val baseCurrency: String = "ZAR",
    val totalSnippets: Int = 0,
    val monthlyIncome: Double = 0.0,
    val monthlyExpenses: Double = 0.0,
    val monthlyTransactions: List<TransactionEntity> = emptyList(),
    val recentTransactions: List<TransactionEntity> = emptyList(),
    val recentSnippets: List<SnippetEntity> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
/**
 * Reference scope: UI and state-holder separation.
 * (Google Developers, n.d.a; Google Developers, n.d.f). See REFERENCES-CODE.md for full entries.
 */
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
        try {
            val (monthStart, nextMonthStart) = currentMonthBounds()

            val monthlyTransactions = transactions
                .filter {
                    it.transactionDate >= monthStart &&
                            it.transactionDate < nextMonthStart
                }
                .sortedBy { it.transactionDate }

            val monthlyIncome = monthlyTransactions
                .filter { it.type.equals("INCOME", ignoreCase = true) }
                .sumOf {
                    CurrencyConverter.convert(
                        amount = it.amount,
                        from = it.currency,
                        to = currency,
                    )
                }

            val monthlyExpenses = monthlyTransactions
                .filter { it.type.equals("EXPENSE", ignoreCase = true) }
                .sumOf {
                    CurrencyConverter.convert(
                        amount = it.amount,
                        from = it.currency,
                        to = currency,
                    )
                }

            DashboardUiState(
                user = user,
                baseCurrency = currency,
                totalSnippets = snippets.size,
                monthlyIncome = monthlyIncome,
                monthlyExpenses = monthlyExpenses,
                monthlyTransactions = monthlyTransactions,
                recentTransactions = transactions
                    .sortedByDescending { it.transactionDate }
                    .take(3),
                recentSnippets = snippets
                    .sortedByDescending { it.updatedAt }
                    .take(3),
            )
        } catch (e: Exception) {
            // Log and return fallback state to prevent crash
            Log.e("DashboardViewModel", "Error calculating dashboard state", e)
            DashboardUiState(user = user, baseCurrency = currency)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
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