package com.devledger.app.ui.screens.finance

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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

/** Financial Tracker dashboard — month selector, income/expense cards with % vs prior
 *  month and top-3 categories, net balance, a monthly income/expense chart drawn with
 *  Canvas (Google Developers, n.d.e), and recent activity. Matches the Part 1 mockup. */
@Composable
fun FinancialTrackerScreen(
    onAddTransaction: () -> Unit,
    onViewIncome: () -> Unit,
    onViewExpenses: () -> Unit,
    onOpenTransaction: (String) -> Unit,
    viewModel: FinanceViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val currencyFormat = remember(state.baseCurrency) {
        NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-ZA")).apply { currency = java.util.Currency.getInstance(state.baseCurrency) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(DevLedgerColors.BgDeep),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(stringResource(R.string.finance_title), color = DevLedgerColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.finance_subtitle), color = DevLedgerColors.TextMuted, fontSize = 12.sp)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.background(DevLedgerColors.SurfaceCard, androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).padding(horizontal = 6.dp),
                ) {
                    IconButton(onClick = viewModel::onPreviousMonth) { Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous month", tint = DevLedgerColors.TextMuted) }
                    Text(state.monthLabel, color = DevLedgerColors.TextPrimary, fontSize = 13.sp)
                    IconButton(onClick = viewModel::onNextMonth) { Icon(Icons.Filled.ChevronRight, contentDescription = "Next month", tint = DevLedgerColors.TextMuted) }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryCard(
                    title = "Total Income", amount = state.incomeStat.total, percentChange = state.incomeStat.percentChangeVsPrevMonth,
                    color = DevLedgerColors.SuccessGreen, topCategories = state.incomeStat.topCategories,
                    currencyFormat = currencyFormat, viewAllLabel = "View all income", onViewAll = onViewIncome,
                    modifier = Modifier.weight(1f),
                )
                SummaryCard(
                    title = "Total Expenses", amount = state.expenseStat.total, percentChange = state.expenseStat.percentChangeVsPrevMonth,
                    color = DevLedgerColors.ErrorRed, topCategories = state.expenseStat.topCategories,
                    currencyFormat = currencyFormat, viewAllLabel = "View all expenses", onViewAll = onViewExpenses,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            DevLedgerCard {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Net Balance", color = DevLedgerColors.TextMuted, fontSize = 12.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(currencyFormat.format(state.netBalance), color = DevLedgerColors.AccentBlue, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            state.netBalancePercentChange?.let {
                                Text("  ${if (it >= 0) "+" else ""}${"%.1f".format(it)}% vs last month", color = if (it >= 0) DevLedgerColors.SuccessGreen else DevLedgerColors.ErrorRed, fontSize = 11.sp)
                            }
                        }
                    }
                    Icon(Icons.Filled.ShowChart, contentDescription = null, tint = DevLedgerColors.AccentBlue,
                        modifier = Modifier.size(36.dp).background(DevLedgerColors.AccentBlue.copy(alpha = 0.15f), androidx.compose.foundation.shape.CircleShape).padding(8.dp))
                }
            }
        }

        item {
            DevLedgerCard {
                Text("Monthly Summary", color = DevLedgerColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 4.dp))
                Row(modifier = Modifier.padding(bottom = 12.dp)) {
                    LegendDot("Income", DevLedgerColors.SuccessGreen); Spacer(Modifier.width(12.dp))
                    LegendDot("Expenses", DevLedgerColors.ErrorRed); Spacer(Modifier.width(12.dp))
                    LegendDot("Net Balance", DevLedgerColors.AccentBlue)
                }
                MonthlySummaryChart(state.dailyPoints, modifier = Modifier.fillMaxWidth().height(180.dp))
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Recent Transactions", color = DevLedgerColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("View All", color = DevLedgerColors.AccentBlue, fontSize = 12.sp, modifier = Modifier.clickable(onClick = onViewIncome))
            }
        }
        items(state.recentTransactions, key = { it.transactionId }) { tx ->
            TransactionRow(tx, currencyFormat) { onOpenTransaction(tx.transactionId) }
        }
        item { Spacer(modifier = Modifier.height(72.dp)) }
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).background(color, androidx.compose.foundation.shape.CircleShape))
        Spacer(Modifier.width(4.dp))
        Text(label, color = DevLedgerColors.TextMuted, fontSize = 10.sp)
    }
}

@Composable
private fun SummaryCard(
    title: String, amount: Double, percentChange: Double?, color: Color, topCategories: List<Pair<String, Double>>,
    currencyFormat: NumberFormat, viewAllLabel: String, onViewAll: () -> Unit, modifier: Modifier = Modifier,
) {
    DevLedgerCard(modifier = modifier) {
        Text(title, color = DevLedgerColors.TextMuted, fontSize = 11.sp)
        Text(currencyFormat.format(amount), color = DevLedgerColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        percentChange?.let {
            Text("${if (it >= 0) "+" else ""}${"%.1f".format(it)}% vs last month", color = if (it >= 0) DevLedgerColors.SuccessGreen else DevLedgerColors.ErrorRed, fontSize = 10.sp)
        }
        Spacer(Modifier.height(8.dp))
        topCategories.forEach { (category, total) ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(category, color = DevLedgerColors.TextSecondary, fontSize = 11.sp, maxLines = 1)
                Text(currencyFormat.format(total), color = DevLedgerColors.TextSecondary, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(onClick = onViewAll)) {
            Text(viewAllLabel, color = color, fontSize = 11.sp)
            Icon(Icons.Filled.ArrowForward, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
        }
    }
}

@Composable
private fun MonthlySummaryChart(points: List<DailyPoint>, modifier: Modifier = Modifier) {
    if (points.isEmpty()) return
    val maxValue = points.maxOf { maxOf(it.income, it.expenses) }.coerceAtLeast(1.0)

    Canvas(modifier = modifier) {
        val barWidth = size.width / points.size
        points.forEachIndexed { index, point ->
            val net = point.income - point.expenses
            val barHeight = (kotlin.math.abs(net) / maxValue * size.height * 0.5).toFloat()
            val x = index * barWidth
            drawRect(
                color = DevLedgerColors.AccentBlue.copy(alpha = 0.6f),
                topLeft = Offset(x + barWidth * 0.25f, if (net >= 0) size.height / 2 - barHeight else size.height / 2),
                size = androidx.compose.ui.geometry.Size(barWidth * 0.5f, barHeight),
            )
        }
        fun linePath(selector: (DailyPoint) -> Double, color: Color) {
            val path = androidx.compose.ui.graphics.Path()
            points.forEachIndexed { index, point ->
                val x = index * barWidth + barWidth / 2
                val y = size.height - (selector(point) / maxValue * size.height).toFloat()
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color = color, style = Stroke(width = 3f))
        }
        linePath({ it.income }, DevLedgerColors.SuccessGreen)
        linePath({ it.expenses }, DevLedgerColors.ErrorRed)
    }
}

@Composable
private fun TransactionRow(tx: TransactionEntity, currencyFormat: NumberFormat, onClick: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("d MMM yyyy", Locale.getDefault()) }
    DevLedgerCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(tx.category, color = DevLedgerColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text(tx.clientOrFrom.ifBlank { dateFormat.format(Date(tx.transactionDate)) }, color = DevLedgerColors.TextMuted, fontSize = 11.sp)
            }
            Text(
                text = (if (tx.type == "INCOME") "+" else "-") + currencyFormat.format(tx.amount),
                color = if (tx.type == "INCOME") DevLedgerColors.SuccessGreen else DevLedgerColors.ErrorRed,
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            )
        }
    }
}