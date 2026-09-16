package com.devledger.app.ui.screens.finance

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.devledger.app.R
import com.devledger.app.data.local.entity.TransactionEntity
import com.devledger.app.ui.components.DevLedgerCard
import com.devledger.app.ui.theme.DevLedgerColors
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Currency
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
    val prevMonthLabel = remember(state.selectedYear, state.selectedMonth) {
        val cal = Calendar.getInstance().apply {
            set(state.selectedYear, state.selectedMonth, 1)
            add(Calendar.MONTH, -1)
        }
        SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(cal.time)
    }
    
    val currencyFormat = remember(state.baseCurrency) {
        NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-ZA")).apply {
            currency = Currency.getInstance(state.baseCurrency)
            minimumFractionDigits = 2
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(DevLedgerColors.BgDeep),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            FinancialHeader(userAvatarUrl = state.recentTransactions.firstOrNull()?.userId) // Placeholder
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Financial Tracker", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("Track your income, expenses and overall financial health.", color = DevLedgerColors.TextMuted, fontSize = 13.sp)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(DevLedgerColors.SurfaceCard)
                        .border(1.dp, DevLedgerColors.Border, RoundedCornerShape(8.dp))
                        .clickable { /* Month picker */ }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = DevLedgerColors.TextMuted, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(state.monthLabel, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = DevLedgerColors.TextMuted, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                SummaryCard(
                    title = "Total Income",
                    amount = state.incomeStat.total,
                    percentChange = state.incomeStat.percentChangeVsPrevMonth,
                    prevMonthName = prevMonthLabel,
                    color = DevLedgerColors.SuccessGreen,
                    icon = Icons.Default.AccountBalanceWallet,
                    topCategories = state.incomeStat.topCategories,
                    currencyFormat = currencyFormat,
                    viewAllLabel = "View all income",
                    onViewAll = onViewIncome,
                    modifier = Modifier.weight(1f),
                )
                SummaryCard(
                    title = "Total Expenses",
                    amount = state.expenseStat.total,
                    percentChange = state.expenseStat.percentChangeVsPrevMonth,
                    prevMonthName = prevMonthLabel,
                    color = DevLedgerColors.ErrorRed,
                    icon = Icons.Default.Wallet,
                    topCategories = state.expenseStat.topCategories,
                    currencyFormat = currencyFormat,
                    viewAllLabel = "View all expenses",
                    onViewAll = onViewExpenses,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            NetBalanceCard(
                amount = state.netBalance,
                percentChange = state.netBalancePercentChange,
                prevMonthName = prevMonthLabel,
                currencyFormat = currencyFormat
            )
        }

        item {
            MonthlySummarySection(state.dailyPoints)
        }

        item {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Text("Recent Transactions", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("View All", color = DevLedgerColors.AccentBlue, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(onClick = onViewIncome))
            }
        }

        items(state.recentTransactions, key = { it.transactionId }) { tx ->
            TransactionListItem(tx, currencyFormat) { onOpenTransaction(tx.transactionId) }
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
private fun FinancialHeader(userAvatarUrl: String?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color.White, modifier = Modifier.size(28.dp))
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = null,
                tint = DevLedgerColors.AccentBlue,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text("DevLedger", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            BadgedBox(
                badge = { Badge(containerColor = DevLedgerColors.AccentBlue) { Text("3", color = Color.White) } }
            ) {
                Icon(Icons.Default.NotificationsNone, contentDescription = "Notifications", tint = Color.White, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(16.dp))
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape).background(DevLedgerColors.SurfaceCardAlt)
            ) {
                AsyncImage(
                    model = "https://ui-avatars.com/api/?name=Dev+Ledger&background=1d2235&color=fff",
                    contentDescription = "Avatar",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(
    title: String, amount: Double, percentChange: Double?, prevMonthName: String,
    color: Color, icon: ImageVector,
    topCategories: List<Pair<String, Double>>, currencyFormat: NumberFormat,
    viewAllLabel: String, onViewAll: () -> Unit, modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(DevLedgerColors.SurfaceCard)
            .border(1.dp, DevLedgerColors.Border, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(title, color = DevLedgerColors.TextSecondary, fontSize = 12.sp)
                        Spacer(Modifier.width(4.dp))
                        Icon(if (title.contains("Income")) Icons.Default.TrendingUp else Icons.Default.TrendingDown, 
                             contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(currencyFormat.format(amount), color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    percentChange?.let {
                        Text(
                            text = "${if (it >= 0) "+" else ""}${"%.1f".format(it)}% vs $prevMonthName",
                            color = if (it >= 0 && title.contains("Income")) DevLedgerColors.SuccessGreen else DevLedgerColors.ErrorRed,
                            fontSize = 11.sp
                        )
                    }
                }
                Box(
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(Modifier.height(16.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                topCategories.forEach { (cat, valAmount) ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(cat, color = DevLedgerColors.TextMuted, fontSize = 12.sp, maxLines = 1)
                        Text(currencyFormat.format(valAmount), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onViewAll),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(viewAllLabel, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Icon(Icons.Default.ArrowForward, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun NetBalanceCard(amount: Double, percentChange: Double?, prevMonthName: String, currencyFormat: NumberFormat) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DevLedgerColors.SurfaceCard)
            .border(1.dp, DevLedgerColors.Border, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Net Balance", color = DevLedgerColors.TextSecondary, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                Text(currencyFormat.format(amount), color = DevLedgerColors.AccentBlue, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                percentChange?.let {
                    Text(
                        text = "${if (it >= 0) "+" else ""}${"%.1f".format(it)}% vs $prevMonthName",
                        color = if (it >= 0) DevLedgerColors.SuccessGreen else DevLedgerColors.ErrorRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(Modifier.width(12.dp))
                Box(
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(DevLedgerColors.AccentBlue.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.AutoGraph, contentDescription = null, tint = DevLedgerColors.AccentBlue, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

@Composable
private fun MonthlySummarySection(points: List<DailyPoint>) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DevLedgerColors.SurfaceCard)
            .border(1.dp, DevLedgerColors.Border, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Monthly Summary", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(DevLedgerColors.SurfaceCardAlt)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("This Month", color = Color.White, fontSize = 12.sp)
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = DevLedgerColors.TextMuted, modifier = Modifier.size(14.dp))
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LegendItem("Income", DevLedgerColors.SuccessGreen)
                LegendItem("Expenses", DevLedgerColors.ErrorRed)
                LegendItem("Net Balance", DevLedgerColors.AccentBlue)
            }

            Spacer(Modifier.height(24.dp))

            MonthlySummaryChart(points, modifier = Modifier.fillMaxWidth().height(160.dp))
            
            Spacer(Modifier.height(8.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("1 May", "8 May", "15 May", "22 May", "29 May").forEach { label ->
                    Text(label, color = DevLedgerColors.TextMuted, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, color = DevLedgerColors.TextSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun MonthlySummaryChart(points: List<DailyPoint>, modifier: Modifier = Modifier) {
    if (points.isEmpty()) return
    val maxValue = (points.maxOfOrNull { maxOf(it.income, it.expenses) } ?: 100.0).coerceAtLeast(1.0) * 1.2

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val stepX = width / (points.size - 1).coerceAtLeast(1)

        // Draw horizontal grid lines
        val gridLines = 4
        for (i in 0..gridLines) {
            val y = height - (i * (height / gridLines))
            drawLine(DevLedgerColors.Border.copy(alpha = 0.3f), Offset(0f, y), Offset(width, y), strokeWidth = 1.dp.toPx())
        }

        fun getSmoothPath(selector: (DailyPoint) -> Double): Path {
            val path = Path()
            if (points.isEmpty()) return path
            
            val pts = points.mapIndexed { index, point ->
                Offset(index * stepX, height - (selector(point).toFloat() / maxValue.toFloat() * height))
            }
            
            path.moveTo(pts[0].x, pts[0].y)
            for (i in 0 until pts.size - 1) {
                val p0 = pts[i]
                val p1 = pts[i + 1]
                path.quadraticBezierTo(p0.x, p0.y, (p0.x + p1.x) / 2, (p0.y + p1.y) / 2)
            }
            path.lineTo(pts.last().x, pts.last().y)
            return path
        }

        // Draw area gradients
        fun drawArea(selector: (DailyPoint) -> Double, color: Color) {
            val areaPath = getSmoothPath(selector)
            areaPath.lineTo(width, height)
            areaPath.lineTo(0f, height)
            areaPath.close()
            drawPath(areaPath, brush = Brush.verticalGradient(listOf(color.copy(alpha = 0.15f), Color.Transparent)))
        }

        drawArea({ it.income }, DevLedgerColors.SuccessGreen)
        drawArea({ it.expenses }, DevLedgerColors.ErrorRed)
        drawArea({ it.income - it.expenses }, DevLedgerColors.AccentBlue)

        // Draw lines
        drawPath(getSmoothPath { it.income }, color = DevLedgerColors.SuccessGreen, style = Stroke(2.5.dp.toPx()))
        drawPath(getSmoothPath { it.expenses }, color = DevLedgerColors.ErrorRed, style = Stroke(2.5.dp.toPx()))
        drawPath(getSmoothPath { it.income - it.expenses }, color = DevLedgerColors.AccentBlue, style = Stroke(2.5.dp.toPx()))
    }
}

@Composable
private fun TransactionListItem(tx: TransactionEntity, currencyFormat: NumberFormat, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val isIncome = tx.type == "INCOME"
        Box(
            modifier = Modifier.size(48.dp).clip(CircleShape).background(if (isIncome) DevLedgerColors.SuccessGreen.copy(alpha = 0.15f) else DevLedgerColors.ErrorRed.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isIncome) Icons.Default.VerticalAlignBottom else Icons.Default.VerticalAlignTop,
                contentDescription = null,
                tint = if (isIncome) DevLedgerColors.SuccessGreen else DevLedgerColors.ErrorRed,
                modifier = Modifier.size(24.dp)
            )
        }
        
        Spacer(Modifier.width(14.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(tx.category, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(tx.description?.ifBlank { "No description" } ?: "No description", color = DevLedgerColors.TextMuted, fontSize = 13.sp, maxLines = 1)
        }
        
        val dateLabel = remember(tx.transactionDate) {
            val now = System.currentTimeMillis()
            val diff = now - tx.transactionDate
            if (diff < 24 * 60 * 60 * 1000) "Today"
            else SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(tx.transactionDate))
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = (if (isIncome) "+" else "-") + currencyFormat.format(tx.amount),
                color = if (isIncome) DevLedgerColors.SuccessGreen else DevLedgerColors.ErrorRed,
                fontSize = 16.sp, fontWeight = FontWeight.Bold,
            )
            Text(dateLabel, color = DevLedgerColors.TextMuted, fontSize = 12.sp)
        }
    }
}
