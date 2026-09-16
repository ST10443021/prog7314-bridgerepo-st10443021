package com.devledger.app.ui.screens.finance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devledger.app.data.local.entity.TransactionEntity
import com.devledger.app.data.repository.TransactionRepository
import com.devledger.app.ui.components.DevLedgerCard
import com.devledger.app.ui.components.DevLedgerTopBar
import com.devledger.app.ui.theme.DevLedgerColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class TransactionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val transactionRepository: TransactionRepository,
) : ViewModel() {
    private val transactionId: String = requireNotNull(savedStateHandle["transactionId"])
    private val _transaction = MutableStateFlow<TransactionEntity?>(null)
    val transaction: StateFlow<TransactionEntity?> = _transaction.asStateFlow()
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init { viewModelScope.launch { _transaction.value = transactionRepository.getById(transactionId); _isLoading.value = false } }
}

/** Full detail view for a single transaction — every field the mockup's "Add
 *  Transaction" form collects, shown back for review, matching the requirement that a
 *  user can view all details of a specific income/expense entry. */
@Composable
fun TransactionDetailScreen(onBack: () -> Unit, viewModel: TransactionDetailViewModel = hiltViewModel()) {
    val tx by viewModel.transaction.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-ZA")) }
    val dateFormat = remember { SimpleDateFormat("d MMMM yyyy", Locale.getDefault()) }

    Column(modifier = Modifier.fillMaxSize().background(DevLedgerColors.BgDeep)) {
        DevLedgerTopBar(title = "Transaction Details", onBack = onBack)
        if (isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

        val transaction = tx
        if (transaction == null) {
            if (!isLoading) Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("This transaction could not be found.", color = DevLedgerColors.TextMuted)
            }
        } else {
            val accent = if (transaction.type == "INCOME") DevLedgerColors.SuccessGreen else DevLedgerColors.ErrorRed
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                DevLedgerCard {
                    Text(if (transaction.type == "INCOME") "Income" else "Expense", color = accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(currencyFormat.format(transaction.amount), color = DevLedgerColors.TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text(transaction.category, color = DevLedgerColors.TextMuted, fontSize = 13.sp)
                }
                Spacer(Modifier.height(16.dp))
                DetailRow("Description", transaction.description ?: "—")
                DetailRow("Client / From", transaction.clientOrFrom)
                DetailRow("Related Project", transaction.relatedProject ?: "—")
                DetailRow("Payment Method", transaction.paymentMethod ?: "—")
                DetailRow("Reference / Invoice #", transaction.referenceNumber ?: "—")
                DetailRow("Date", dateFormat.format(Date(transaction.transactionDate)))
                DetailRow("Recurring", if (transaction.isRecurring) "Yes — ${transaction.recurringFrequency?.lowercase()?.replaceFirstChar { it.uppercase() }}" else "No")
                if (transaction.attachmentFileName != null) DetailRow("Attachment", transaction.attachmentFileName)
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
        Text(label, color = DevLedgerColors.TextMuted, fontSize = 11.sp)
        Text(value, color = DevLedgerColors.TextPrimary, fontSize = 14.sp)
    }
}