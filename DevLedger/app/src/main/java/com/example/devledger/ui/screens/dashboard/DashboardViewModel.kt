package com.devledger.app.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devledger.app.data.local.entity.SnippetEntity
import com.devledger.app.data.local.entity.UserEntity
import com.devledger.app.data.repository.AuthRepository
import com.devledger.app.data.repository.SnippetRepository
import com.devledger.app.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class DashboardUiState(
    val user: UserEntity? = null,
    val totalSnippets: Int = 0,
    val monthlyIncome: Double = 0.0,
    val monthlyExpenses: Double = 0.0,
    val recentSnippets: List<SnippetEntity> = emptyList(),
)

/** Aggregates snippet counts and financial totals for the Dashboard's summary cards —
 *  Figure "Dashboard Screen" in the Part 1 design doc. */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    authRepository: AuthRepository,
    snippetRepository: SnippetRepository,
    transactionRepository: TransactionRepository,
) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> = combine(
        authRepository.currentUser,
        snippetRepository.observeSnippets(""),
        transactionRepository.observeTotalIncome(),
        transactionRepository.observeTotalExpenses(),
    ) { user, snippets, income, expenses ->
        DashboardUiState(
            user = user,
            totalSnippets = snippets.size,
            monthlyIncome = income,
            monthlyExpenses = expenses,
            recentSnippets = snippets.take(3),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())
}
