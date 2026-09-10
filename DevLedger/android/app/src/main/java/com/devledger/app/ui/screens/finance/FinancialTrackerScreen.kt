package com.devledger.app.ui.screens.finance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.devledger.app.R
import com.devledger.app.data.local.entity.TransactionEntity
import com.devledger.app.ui.components.DevLedgerCard
import com.devledger.app.ui.theme.DevLedgerColors
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Financial Tracker — the "Dev Expense & Income Tracker" custom feature: totals,
 *  budget-threshold alert banner, and a transaction list. Matches "Screen 5" in the
 *  Part 1 design doc. */
@Composable
fun FinancialTrackerScreen(
    onAddTransaction: () -> Unit,
    viewModel: FinanceViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val currencyFormat = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-ZA"))

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(DevLedgerColors.BgDeep),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(stringResource(R.string.finance_title), color = DevLedgerColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.finance_subtitle), color = DevLedgerColors.TextMuted, fontSize = 12.sp)
        }
        item {
            DevLedgerCard {
                Text(stringResource(R.string.finance_net_balance), color = DevLedgerColors.TextMuted, fontSize = 12.sp)
                Text(
                    text = currencyFormat.format(state.netBalance),
                    color = if (state.netBalance >= 0) DevLedgerColors.SuccessGreen else DevLedgerColors.ErrorRed,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DevLedgerCard(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.finance_total_income), color = DevLedgerColors.TextMuted, fontSize = 11.sp)
                    Text(currencyFormat.format(state.totalIncome), color = DevLedgerColors.SuccessGreen, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
                DevLedgerCard(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.finance_total_expenses), color = DevLedgerColors.TextMuted, fontSize = 11.sp)
                    Text(currencyFormat.format(state.totalExpenses), color = DevLedgerColors.ErrorRed, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (state.budgetUsedFraction >= 0.8) {
            item {
                DevLedgerCard {
                    Text(stringResource(R.string.finance_budget_alert), color = DevLedgerColors.WarningAmber, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    LinearProgressIndicator(
                        progress = { state.budgetUsedFraction.toFloat() },
                        color = DevLedgerColors.WarningAmber,
                        trackColor = DevLedgerColors.SurfaceCardAlt,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            }
        }
        item {
            Text(stringResource(R.string.finance_recent_transactions), color = DevLedgerColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        items(state.transactions, key = { it.transactionId }) { tx ->
            TransactionRow(tx, currencyFormat)
        }
        item { Spacer(modifier = Modifier.height(72.dp)) } // room for the FAB
    }
}

@Composable
private fun TransactionRow(tx: TransactionEntity, currencyFormat: NumberFormat) {
    val dateFormat = remember { SimpleDateFormat("d MMM yyyy", Locale.getDefault()) }
    DevLedgerCard {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column {
                Text(tx.category, color = DevLedgerColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text(dateFormat.format(Date(tx.transactionDate)), color = DevLedgerColors.TextMuted, fontSize = 11.sp)
            }
            Text(
                text = (if (tx.type == "INCOME") "+" else "-") + currencyFormat.format(tx.amount),
                color = if (tx.type == "INCOME") DevLedgerColors.SuccessGreen else DevLedgerColors.ErrorRed,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
