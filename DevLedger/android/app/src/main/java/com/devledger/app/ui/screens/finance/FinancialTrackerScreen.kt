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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.devledger.app.data.local.entity.TransactionEntity
import com.devledger.app.ui.theme.DevLedgerColors
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Currency
import java.util.Date
import java.util.Locale

/**
 * Financial Tracker dashboard.
 *
 * The previous version displayed a separate FinancialHeader above this screen.
 * That header created unnecessary vertical space and duplicated the application's
 * main navigation/header area. The Financial Tracker now begins directly with
 * its title and month selector to provide a cleaner and more consistent layout.
 *
 * The dashboard displays:
 * - Monthly income
 * - Monthly expenses
 * - Net balance
 * - Monthly summary chart
 * - Recent transactions
 *
 * Canvas is used for the financial chart as supported by the Android Jetpack
 * Compose graphics APIs (Google Developers, n.d.).
 */
@Composable
fun FinancialTrackerScreen(
    onAddTransaction: () -> Unit,
    onViewIncome: () -> Unit,
    onViewExpenses: () -> Unit,
    onOpenTransaction: (String) -> Unit,
    viewModel: FinanceViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    val prevMonthLabel = remember(
        state.selectedYear,
        state.selectedMonth
    ) {
        val cal = Calendar.getInstance().apply {
            set(state.selectedYear, state.selectedMonth, 1)
            add(Calendar.MONTH, -1)
        }

        SimpleDateFormat(
            "MMM yyyy",
            Locale.getDefault()
        ).format(cal.time)
    }

    val currencyFormat = remember(state.baseCurrency) {
        NumberFormat.getCurrencyInstance(
            Locale.forLanguageTag("en-ZA")
        ).apply {
            currency = Currency.getInstance(state.baseCurrency)
            minimumFractionDigits = 2
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DevLedgerColors.BgDeep),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {


        item {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Financial Tracker",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(
                    modifier = Modifier.height(4.dp)
                )

                Text(
                    text = "Track your income, expenses and overall financial health.",
                    color = DevLedgerColors.TextMuted,
                    fontSize = 13.sp
                )

                Spacer(
                    modifier = Modifier.height(12.dp)
                )

                /*
                 * The month selector is placed underneath the title instead of
                 * beside it. This prevents the selector from being squeezed on
                 * smaller physical-device screens.
                 */
                Box(
                    modifier = Modifier
                        .align(Alignment.End)
                        .clip(RoundedCornerShape(8.dp))
                        .background(DevLedgerColors.SurfaceCard)
                        .border(
                            width = 1.dp,
                            color = DevLedgerColors.Border,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable {
                            // Month picker can be connected here.
                        }
                        .padding(
                            horizontal = 10.dp,
                            vertical = 6.dp
                        )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = "Select month",
                            tint = DevLedgerColors.TextMuted,
                            modifier = Modifier.size(16.dp)
                        )

                        Spacer(
                            modifier = Modifier.width(6.dp)
                        )

                        Text(
                            text = state.monthLabel,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(
                            modifier = Modifier.width(4.dp)
                        )

                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Open month selector",
                            tint = DevLedgerColors.TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
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
            MonthlySummarySection(
                points = state.dailyPoints,
                currentRange = state.summaryRange,
                onRangeChange = viewModel::onSummaryRangeChanged
            )
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = "Recent Transactions",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "View All",
                    color = DevLedgerColors.AccentBlue,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(
                        onClick = onViewIncome
                    )
                )
            }
        }

        items(
            state.recentTransactions,
            key = { it.transactionId }
        ) { tx ->
            TransactionListItem(
                tx = tx,
                currencyFormat = currencyFormat
            ) {
                onOpenTransaction(tx.transactionId)
            }
        }

        item {
            Spacer(
                modifier = Modifier.height(80.dp)
            )
        }
    }
}

@Composable
private fun SummaryCard(
    title: String,
    amount: Double,
    percentChange: Double?,
    prevMonthName: String,
    color: Color,
    icon: ImageVector,
    topCategories: List<Pair<String, Double>>,
    currencyFormat: NumberFormat,
    viewAllLabel: String,
    onViewAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(DevLedgerColors.SurfaceCard)
            .border(
                width = 1.dp,
                color = DevLedgerColors.Border,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = title,
                            color = DevLedgerColors.TextSecondary,
                            fontSize = 12.sp
                        )

                        Spacer(
                            modifier = Modifier.width(4.dp)
                        )

                        Icon(
                            imageVector = if (title.contains("Income")) {
                                Icons.Default.TrendingUp
                            } else {
                                Icons.Default.TrendingDown
                            },
                            contentDescription = null,
                            tint = color,
                            modifier = Modifier.size(12.dp)
                        )
                    }

                    Spacer(
                        modifier = Modifier.height(4.dp)
                    )

                    Text(
                        text = currencyFormat.format(amount),
                        color = color,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    percentChange?.let {
                        Text(
                            text = "${if (it >= 0) "+" else ""}${"%.1f".format(it)}% vs $prevMonthName",
                            color = if (
                                it >= 0 && title.contains("Income")
                            ) {
                                DevLedgerColors.SuccessGreen
                            } else {
                                DevLedgerColors.ErrorRed
                            },
                            fontSize = 11.sp
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            color.copy(alpha = 0.15f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                topCategories.forEach { (category, valueAmount) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = category,
                            color = DevLedgerColors.TextMuted,
                            fontSize = 12.sp,
                            maxLines = 1
                        )

                        Text(
                            text = currencyFormat.format(valueAmount),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        onClick = onViewAll
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = viewAllLabel,
                    color = color,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun NetBalanceCard(
    amount: Double,
    percentChange: Double?,
    prevMonthName: String,
    currencyFormat: NumberFormat
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DevLedgerColors.SurfaceCard)
            .border(
                width = 1.dp,
                color = DevLedgerColors.Border,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Net Balance",
                    color = DevLedgerColors.TextSecondary,
                    fontSize = 13.sp
                )

                Spacer(
                    modifier = Modifier.height(4.dp)
                )

                Text(
                    text = currencyFormat.format(amount),
                    color = DevLedgerColors.AccentBlue,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                percentChange?.let {
                    Text(
                        text = "${if (it >= 0) "+" else ""}${"%.1f".format(it)}% vs $prevMonthName",
                        color = if (
                            it >= 0
                        ) {
                            DevLedgerColors.SuccessGreen
                        } else {
                            DevLedgerColors.ErrorRed
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(
                    modifier = Modifier.width(12.dp)
                )

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            DevLedgerColors.AccentBlue.copy(
                                alpha = 0.15f
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoGraph,
                        contentDescription = null,
                        tint = DevLedgerColors.AccentBlue,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthlySummarySection(
    points: List<DailyPoint>,
    currentRange: SummaryRange,
    onRangeChange: (SummaryRange) -> Unit
) {
    var showRangeMenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DevLedgerColors.SurfaceCard)
            .border(
                width = 1.dp,
                color = DevLedgerColors.Border,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Financial Summary",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Box {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(DevLedgerColors.SurfaceCardAlt)
                            .clickable { showRangeMenu = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val rangeLabel = when(currentRange) {
                                SummaryRange.WEEK -> "This Week"
                                SummaryRange.MONTH -> "This Month"
                                SummaryRange.YEAR -> "This Year"
                            }
                            Text(
                                text = rangeLabel,
                                color = Color.White,
                                fontSize = 12.sp
                            )

                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = DevLedgerColors.TextMuted,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showRangeMenu,
                        onDismissRequest = { showRangeMenu = false },
                        modifier = Modifier.background(DevLedgerColors.SurfaceCard)
                    ) {
                        SummaryRange.values().forEach { range ->
                            val label = when(range) {
                                SummaryRange.WEEK -> "This Week"
                                SummaryRange.MONTH -> "This Month"
                                SummaryRange.YEAR -> "This Year"
                            }
                            DropdownMenuItem(
                                text = { Text(label, color = Color.White) },
                                onClick = {
                                    onRangeChange(range)
                                    showRangeMenu = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LegendItem(label = "Income", color = DevLedgerColors.SuccessGreen)
                LegendItem(label = "Expenses", color = DevLedgerColors.ErrorRed)
                LegendItem(label = "Net Balance", color = DevLedgerColors.AccentBlue)
            }

            Spacer(modifier = Modifier.height(24.dp))

            MonthlySummaryChart(
                points = points,
                modifier = Modifier.fillMaxWidth().height(160.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Show labels from the data points if available, otherwise fallback
                if (points.size >= 5) {
                    val indices = listOf(0, points.size / 4, points.size / 2, 3 * points.size / 4, points.size - 1)
                    indices.forEach { idx ->
                        Text(
                            text = points[idx].label,
                            color = DevLedgerColors.TextMuted,
                            fontSize = 10.sp
                        )
                    }
                } else {
                    points.forEach { point ->
                        Text(
                            text = point.label,
                            color = DevLedgerColors.TextMuted,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendItem(
    label: String,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )

        Spacer(
            modifier = Modifier.width(6.dp)
        )

        Text(
            text = label,
            color = DevLedgerColors.TextSecondary,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun MonthlySummaryChart(
    points: List<DailyPoint>,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) return

    val maxValue = (
            points.maxOfOrNull {
                maxOf(
                    it.income,
                    it.expenses
                )
            } ?: 100.0
            ).coerceAtLeast(1.0) * 1.2

    Canvas(
        modifier = modifier
    ) {
        val width = size.width
        val height = size.height

        val stepX = width /
                (points.size - 1).coerceAtLeast(1)

        // Draw horizontal grid lines.
        val gridLines = 4

        for (i in 0..gridLines) {
            val y = height -
                    (i * (height / gridLines))

            drawLine(
                color = DevLedgerColors.Border.copy(
                    alpha = 0.3f
                ),
                start = Offset(
                    0f,
                    y
                ),
                end = Offset(
                    width,
                    y
                ),
                strokeWidth = 1.dp.toPx()
            )
        }

        fun getSmoothPath(
            selector: (DailyPoint) -> Double
        ): Path {
            val path = Path()

            if (points.isEmpty()) {
                return path
            }

            val pts = points.mapIndexed { index, point ->
                Offset(
                    x = index * stepX,
                    y = height -
                            (
                                    selector(point).toFloat() /
                                            maxValue.toFloat() *
                                            height
                                    )
                )
            }

            path.moveTo(
                pts[0].x,
                pts[0].y
            )

            for (i in 0 until pts.size - 1) {
                val p0 = pts[i]
                val p1 = pts[i + 1]

                path.quadraticBezierTo(
                    p0.x,
                    p0.y,
                    (p0.x + p1.x) / 2,
                    (p0.y + p1.y) / 2
                )
            }

            path.lineTo(
                pts.last().x,
                pts.last().y
            )

            return path
        }

        // Draw area gradients behind each chart line.
        fun drawArea(
            selector: (DailyPoint) -> Double,
            color: Color
        ) {
            val areaPath = getSmoothPath(selector)

            areaPath.lineTo(
                width,
                height
            )

            areaPath.lineTo(
                0f,
                height
            )

            areaPath.close()

            drawPath(
                path = areaPath,
                brush = Brush.verticalGradient(
                    listOf(
                        color.copy(alpha = 0.15f),
                        Color.Transparent
                    )
                )
            )
        }

        drawArea(
            selector = { it.income },
            color = DevLedgerColors.SuccessGreen
        )

        drawArea(
            selector = { it.expenses },
            color = DevLedgerColors.ErrorRed
        )

        drawArea(
            selector = {
                it.income - it.expenses
            },
            color = DevLedgerColors.AccentBlue
        )

        // Draw income line.
        drawPath(
            path = getSmoothPath { it.income },
            color = DevLedgerColors.SuccessGreen,
            style = Stroke(
                width = 2.5.dp.toPx()
            )
        )

        // Draw expenses line.
        drawPath(
            path = getSmoothPath { it.expenses },
            color = DevLedgerColors.ErrorRed,
            style = Stroke(
                width = 2.5.dp.toPx()
            )
        )

        // Draw net balance line.
        drawPath(
            path = getSmoothPath {
                it.income - it.expenses
            },
            color = DevLedgerColors.AccentBlue,
            style = Stroke(
                width = 2.5.dp.toPx()
            )
        )
    }
}

@Composable
private fun TransactionListItem(
    tx: TransactionEntity,
    currencyFormat: NumberFormat,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClick = onClick
            )
            .padding(
                vertical = 4.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val isIncome = tx.type == "INCOME"

        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    if (isIncome) {
                        DevLedgerColors.SuccessGreen.copy(
                            alpha = 0.15f
                        )
                    } else {
                        DevLedgerColors.ErrorRed.copy(
                            alpha = 0.15f
                        )
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isIncome) {
                    Icons.Default.VerticalAlignBottom
                } else {
                    Icons.Default.VerticalAlignTop
                },
                contentDescription = null,
                tint = if (isIncome) {
                    DevLedgerColors.SuccessGreen
                } else {
                    DevLedgerColors.ErrorRed
                },
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(
            modifier = Modifier.width(14.dp)
        )

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = tx.category,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = tx.description
                    ?.ifBlank {
                        "No description"
                    }
                    ?: "No description",
                color = DevLedgerColors.TextMuted,
                fontSize = 13.sp,
                maxLines = 1
            )
        }

        val dateLabel = remember(
            tx.transactionDate
        ) {
            val now = System.currentTimeMillis()
            val diff = now - tx.transactionDate

            if (
                diff < 24 * 60 * 60 * 1000
            ) {
                "Today"
            } else {
                SimpleDateFormat(
                    "d MMM",
                    Locale.getDefault()
                ).format(
                    Date(tx.transactionDate)
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text =
                    (if (isIncome) "+" else "-") +
                            currencyFormat.format(
                                tx.amount
                            ),
                color = if (isIncome) {
                    DevLedgerColors.SuccessGreen
                } else {
                    DevLedgerColors.ErrorRed
                },
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = dateLabel,
                color = DevLedgerColors.TextMuted,
                fontSize = 12.sp
            )
        }
    }
}
