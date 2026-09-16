package com.devledger.app.ui.screens.finance

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devledger.app.data.local.entity.TransactionEntity
import com.devledger.app.data.repository.AuthRepository
import com.devledger.app.data.repository.TransactionRepository
import com.devledger.app.ui.components.DevLedgerCard
import com.devledger.app.ui.components.DevLedgerTextField
import com.devledger.app.ui.components.DevLedgerTopBar
import com.devledger.app.ui.theme.DevLedgerColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

data class TransactionListUiState(val query: String = "", val transactions: List<TransactionEntity> = emptyList())

/** Past Income / Past Expenses list — one screen reused for both via [type], with
 *  search-by-name filtering applied over the RoomDB-backed list (Google Developers,
 *  n.d.b). */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TransactionListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    authRepository: AuthRepository,
    private val transactionRepository: TransactionRepository,
) : ViewModel() {

    val type: String = savedStateHandle.get<String>("type") ?: "EXPENSE"
    private val query = MutableStateFlow("")

    val uiState: StateFlow<TransactionListUiState> = combine(
        query.flatMapLatest { q ->
            authRepository.currentUser.flatMapLatest { user ->
                if (user == null) flowOf(emptyList()) else transactionRepository.observeByType(user.userId, type, q)
            }
        },
        query,
    ) { transactions, q -> TransactionListUiState(q, transactions) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TransactionListUiState())

    fun onQueryChange(value: String) { query.value = value }
}

@Composable
fun TransactionListScreen(onBack: () -> Unit, onOpenTransaction: (String) -> Unit, viewModel: TransactionListViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-ZA")) }
    val dateFormat = remember { SimpleDateFormat("d MMM yyyy", Locale.getDefault()) }
    val title = if (viewModel.type == "INCOME") "Past Income" else "Past Expenses"
    val accent = if (viewModel.type == "INCOME") DevLedgerColors.SuccessGreen else DevLedgerColors.ErrorRed

    Column(modifier = Modifier.fillMaxSize().background(DevLedgerColors.BgDeep)) {
        DevLedgerTopBar(title = title, onBack = onBack)
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            DevLedgerTextField(
                value = state.query, onValueChange = viewModel::onQueryChange,
                label = "Search by name...", modifier = Modifier.padding(bottom = 16.dp),
            )
            if (state.transactions.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                    Text("No matching transactions.", color = DevLedgerColors.TextMuted, fontSize = 13.sp)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(state.transactions, key = { it.transactionId }) { tx ->
                        DevLedgerCard(modifier = Modifier.clickable { onOpenTransaction(tx.transactionId) }) {
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Text(tx.category, color = DevLedgerColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    Text(tx.clientOrFrom, color = DevLedgerColors.TextMuted, fontSize = 11.sp)
                                    Text(dateFormat.format(Date(tx.transactionDate)), color = DevLedgerColors.TextMuted, fontSize = 10.sp)
                                }
                                Text(currencyFormat.format(tx.amount), color = accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}