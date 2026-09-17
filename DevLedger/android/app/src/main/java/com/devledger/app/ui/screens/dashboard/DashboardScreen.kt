package com.devledger.app.ui.screens.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.devledger.app.R
import com.devledger.app.data.local.entity.SnippetEntity
import com.devledger.app.data.local.entity.TransactionEntity
import com.devledger.app.data.local.entity.UserEntity
import com.devledger.app.ui.theme.DevLedgerColors
import com.devledger.app.ui.theme.DevLedgerTheme
import com.devledger.app.util.CurrencyConverter
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Currency
import java.util.Locale

@Composable
/**
 * Reference scope: UI and state-holder separation.
 * (Google Developers, n.d.a; Google Developers, n.d.f). See REFERENCES-CODE.md for full entries.
 */
fun DashboardScreen(
    onOpenSnippets: () -> Unit,
    onOpenFinance: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSync: () -> Unit,
    onOpenRecycle: () -> Unit,
    onCreateSnippet: () -> Unit = onOpenSnippets,
    onAddTransaction: () -> Unit = onOpenFinance,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    DashboardScreenContent(
        state = state,
        onOpenSnippets = onOpenSnippets,
        onOpenFinance = onOpenFinance,
        onOpenSettings = onOpenSettings,
        onOpenNotifications = onOpenNotifications,
        onOpenProfile = onOpenProfile,
        onOpenSync = onOpenSync,
        onOpenRecycle = onOpenRecycle,
        onCreateSnippet = onCreateSnippet,
        onAddTransaction = onAddTransaction,
    )
}

@Composable
/**
 * Reference scope: UI and state-holder separation.
 * (Google Developers, n.d.a; Google Developers, n.d.f). See REFERENCES-CODE.md for full entries.
 */
fun DashboardScreenContent(
    state: DashboardUiState,
    onOpenSnippets: () -> Unit,
    onOpenFinance: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenSync: () -> Unit,
    onOpenRecycle: () -> Unit,
    onCreateSnippet: () -> Unit = onOpenSnippets,
    onAddTransaction: () -> Unit = onOpenFinance,
    onOpenProfile: () -> Unit,
) {
    val currencyFormat = remember(state.baseCurrency) {
        NumberFormat.getCurrencyInstance(
            Locale.forLanguageTag("en-ZA")
        ).apply {
            runCatching {
                currency = Currency.getInstance(state.baseCurrency)
            }
        }
    }

    val netIncome = state.monthlyIncome - state.monthlyExpenses

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DevLedgerColors.BgDeep)
            .statusBarsPadding(),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = 12.dp,
            end = 16.dp,
            bottom = 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            DashboardHeader(
                onOpenNotifications = onOpenNotifications,
                onOpenProfile = onOpenProfile,
            )
        }

        item {
            WelcomeSection(
                userName = state.user?.fullName ?: "Developer",
            )
        }

        item {
            SummaryCards(
                totalSnippets = state.totalSnippets,
                monthlyIncome = state.monthlyIncome,
                monthlyExpenses = state.monthlyExpenses,
                currencyFormat = currencyFormat,
            )
        }

        item {
            FinancialOverviewCard(
                transactions = state.monthlyTransactions,
                income = state.monthlyIncome,
                expenses = state.monthlyExpenses,
                netIncome = netIncome,
                baseCurrency = state.baseCurrency,
                currencyFormat = currencyFormat,
            )
        }

        item {
            QuickActionsSection(
                onAddTransaction = onAddTransaction,
                onOpenFinance = onOpenFinance,
                onCreateSnippet = onCreateSnippet,
                onOpenSnippets = onOpenSnippets,
                onOpenSettings = onOpenSettings,
                onOpenSync = onOpenSync,
                onOpenRecycle = onOpenRecycle,
            )
        }

        item {
            RecentActivitySection(
                state = state,
                currencyFormat = currencyFormat,
                onOpenFinance = onOpenFinance,
                onOpenSnippets = onOpenSnippets,
            )
        }
    }
}

@Composable
private fun DashboardHeader(
    onOpenNotifications: () -> Unit,
    onOpenProfile: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(DevLedgerColors.AccentBlueDark),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Code,
                contentDescription = null,
                tint = DevLedgerColors.AccentBlue,
                modifier = Modifier.size(23.dp),
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text = buildAnnotatedString {
                withStyle(
                    SpanStyle(
                        color = DevLedgerColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                ) {
                    append("Dev")
                }

                withStyle(
                    SpanStyle(
                        color = DevLedgerColors.AccentBlue,
                        fontWeight = FontWeight.Bold,
                    )
                ) {
                    append("Ledger")
                }
            },
            fontSize = 22.sp,
        )

        Spacer(modifier = Modifier.weight(1f))

        IconButton(
            onClick = onOpenNotifications,
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(DevLedgerColors.SurfaceCard)
                .border(
                    width = 1.dp,
                    color = DevLedgerColors.Border,
                    shape = CircleShape,
                ),
        ) {
            Icon(
                imageVector = Icons.Filled.Notifications,
                contentDescription = stringResource(
                    R.string.nav_notifications
                ),
                tint = DevLedgerColors.TextSecondary,
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        IconButton(
            onClick = onOpenProfile,
            modifier = Modifier.size(42.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.AccountCircle,
                contentDescription = "Open profile",
                tint = DevLedgerColors.AccentBlue,
                modifier = Modifier.size(42.dp),
            )
        }
    }
}

@Composable
private fun WelcomeSection(
    userName: String,
) {
    Column {
        Text(
            text = stringResource(R.string.dashboard_welcome_back),
            color = DevLedgerColors.TextMuted,
            style = MaterialTheme.typography.bodyMedium,
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = userName,
            color = DevLedgerColors.TextPrimary,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = stringResource(R.string.dashboard_overview),
            color = DevLedgerColors.TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun SummaryCards(
    totalSnippets: Int,
    monthlyIncome: Double,
    monthlyExpenses: Double,
    currencyFormat: NumberFormat,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SummaryStat(
            modifier = Modifier.weight(1f),
            icon = Icons.Outlined.Code,
            label = stringResource(R.string.dashboard_total_snippets),
            value = totalSnippets.toString(),
            color = DevLedgerColors.AccentBlue,
        )

        SummaryStat(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.AttachMoney,
            label = stringResource(R.string.dashboard_monthly_income),
            value = currencyFormat.format(monthlyIncome),
            color = DevLedgerColors.SuccessGreen,
        )

        SummaryStat(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.AttachMoney,
            label = stringResource(R.string.dashboard_monthly_expenses),
            value = currencyFormat.format(monthlyExpenses),
            color = DevLedgerColors.ErrorRed,
        )
    }
}

@Composable
private fun SummaryStat(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    color: Color,
) {
    Card(
        modifier = modifier.heightIn(min = 122.dp),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            DevLedgerColors.Border,
        ),
        colors = CardDefaults.cardColors(
            containerColor = DevLedgerColors.SurfaceCard,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.17f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(19.dp),
                )
            }

            Spacer(modifier = Modifier.height(9.dp))

            Text(
                text = label,
                color = DevLedgerColors.TextMuted,
                fontSize = 10.sp,
                lineHeight = 12.sp,
                minLines = 2,
                maxLines = 2,
            )

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = value,
                color = color,
                fontSize = 14.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private data class ChartPoint(
    val day: Int,
    val income: Double,
    val expenses: Double,
)

@Composable
private fun FinancialOverviewCard(
    transactions: List<TransactionEntity>,
    income: Double,
    expenses: Double,
    netIncome: Double,
    baseCurrency: String,
    currencyFormat: NumberFormat,
) {
    val chartPoints = remember(transactions, baseCurrency) {
        createChartPoints(
            transactions = transactions,
            baseCurrency = baseCurrency,
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            DevLedgerColors.Border,
        ),
        colors = CardDefaults.cardColors(
            containerColor = DevLedgerColors.SurfaceCard,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(
                        R.string.dashboard_financial_overview
                    ),
                    color = DevLedgerColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = "This Month",
                    color = DevLedgerColors.TextSecondary,
                    style = MaterialTheme.typography.labelMedium,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.dashboard_net_income),
                color = DevLedgerColors.TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )

            Text(
                text = currencyFormat.format(netIncome),
                color = if (netIncome >= 0) {
                    DevLedgerColors.AccentBlue
                } else {
                    DevLedgerColors.ErrorRed
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                ChartLegend(
                    label = stringResource(R.string.finance_total_income),
                    color = DevLedgerColors.AccentBlue,
                )

                Spacer(modifier = Modifier.width(16.dp))

                ChartLegend(
                    label = stringResource(R.string.finance_total_expenses),
                    color = DevLedgerColors.ErrorRed,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            FinancialChart(
                points = chartPoints,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = currencyFormat.format(income),
                    color = DevLedgerColors.SuccessGreen,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = currencyFormat.format(expenses),
                    color = DevLedgerColors.ErrorRed,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun ChartLegend(
    label: String,
    color: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color),
        )

        Spacer(modifier = Modifier.width(5.dp))

        Text(
            text = label,
            color = DevLedgerColors.TextMuted,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun FinancialChart(
    points: List<ChartPoint>,
) {
    val gridColor = DevLedgerColors.Border.copy(alpha = 0.55f)
    val incomeColor = DevLedgerColors.AccentBlue
    val expenseColor = DevLedgerColors.ErrorRed

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(145.dp),
    ) {
        val chartTop = 8.dp.toPx()
        val chartBottom = size.height - 12.dp.toPx()
        val chartHeight = chartBottom - chartTop

        repeat(4) { index ->
            val y = chartTop + chartHeight * index / 3f

            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
            )
        }

        if (points.isEmpty()) {
            return@Canvas
        }

        val maximum = points.maxOf {
            maxOf(it.income, it.expenses)
        }.coerceAtLeast(1.0)

        fun xPosition(index: Int): Float {
            return if (points.size <= 1) {
                0f
            } else {
                size.width * index / (points.size - 1f)
            }
        }

        fun yPosition(value: Double): Float {
            return chartBottom -
                    ((value / maximum).toFloat() * chartHeight)
        }

        val incomePath = Path()
        val expensePath = Path()

        points.forEachIndexed { index, point ->
            val x = xPosition(index)
            val incomeY = yPosition(point.income)
            val expenseY = yPosition(point.expenses)

            if (index == 0) {
                incomePath.moveTo(x, incomeY)
                expensePath.moveTo(x, expenseY)
            } else {
                incomePath.lineTo(x, incomeY)
                expensePath.lineTo(x, expenseY)
            }
        }

        drawPath(
            path = incomePath,
            color = incomeColor,
            style = Stroke(
                width = 3.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )

        drawPath(
            path = expensePath,
            color = expenseColor,
            style = Stroke(
                width = 3.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}

private fun createChartPoints(
    transactions: List<TransactionEntity>,
    baseCurrency: String,
): List<ChartPoint> {
    val calendar = Calendar.getInstance()
    val numberOfDays = calendar.getActualMaximum(
        Calendar.DAY_OF_MONTH
    )

    val transactionsByDay = transactions.groupBy { transaction ->
        Calendar.getInstance().apply {
            timeInMillis = transaction.transactionDate
        }.get(Calendar.DAY_OF_MONTH)
    }

    var cumulativeIncome = 0.0
    var cumulativeExpenses = 0.0

    return (1..numberOfDays).map { day ->
        transactionsByDay[day]
            .orEmpty()
            .forEach { transaction ->
                val convertedAmount = CurrencyConverter.convert(
                    amount = transaction.amount,
                    from = transaction.currency,
                    to = baseCurrency,
                )

                if (transaction.type.equals("INCOME", true)) {
                    cumulativeIncome += convertedAmount
                } else if (
                    transaction.type.equals("EXPENSE", true)
                ) {
                    cumulativeExpenses += convertedAmount
                }
            }

        ChartPoint(
            day = day,
            income = cumulativeIncome,
            expenses = cumulativeExpenses,
        )
    }
}

@Composable
private fun QuickActionsSection(
    onAddTransaction: () -> Unit,
    onOpenFinance: () -> Unit,
    onCreateSnippet: () -> Unit,
    onOpenSnippets: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSync: () -> Unit,
    onOpenRecycle: () -> Unit,
){
    Column {
        SectionHeading(
            title = stringResource(R.string.dashboard_quick_actions),
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            QuickActionTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Add,
                label = "Add Transaction",
                iconColor = DevLedgerColors.SuccessGreen,
                onClick = onAddTransaction,
            )

            QuickActionTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Code,
                label = "New Snippet",
                iconColor = DevLedgerColors.AccentBlue,
                onClick = onCreateSnippet,
            )

            QuickActionTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.AttachMoney,
                label = stringResource(R.string.nav_finance),
                iconColor = DevLedgerColors.ErrorRed,
                onClick = onOpenFinance,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            QuickActionTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Code,
                label = stringResource(R.string.nav_snippets),
                iconColor = DevLedgerColors.AccentBlue,
                onClick = onOpenSnippets,
            )

            QuickActionTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Settings,
                label = stringResource(R.string.settings_title),
                iconColor = DevLedgerColors.AccentPurple,
                onClick = onOpenSettings,
            )

            QuickActionTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Sync,
                label = "Sync",
                iconColor = DevLedgerColors.SuccessGreen,
                onClick = onOpenSync,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            QuickActionTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.RestoreFromTrash,
                label = "Recycle Bin",
                iconColor = DevLedgerColors.ErrorRed,
                onClick = onOpenRecycle,
            )

            QuickActionTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Archive,
                label = "Archive",
                iconColor = DevLedgerColors.AccentPurple,
                onClick = onOpenRecycle, // Both point to the same screen for now as RecycleScreen handles archived items
            )

            Spacer(modifier = Modifier.weight(1f)) 
        }
    }
}

@Composable
private fun QuickActionTile(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    iconColor: Color,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            DevLedgerColors.Border,
        ),
        colors = CardDefaults.cardColors(
            containerColor = DevLedgerColors.SurfaceCard,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 86.dp)
                .padding(9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.17f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(19.dp),
                )
            }

            Spacer(modifier = Modifier.height(7.dp))

            Text(
                text = label,
                color = DevLedgerColors.TextPrimary,
                fontSize = 11.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun RecentActivitySection(
    state: DashboardUiState,
    currencyFormat: NumberFormat,
    onOpenFinance: () -> Unit,
    onOpenSnippets: () -> Unit,
) {
    Column {
        SectionHeading(
            title = stringResource(R.string.dashboard_recent_activity),
            actionText = stringResource(R.string.dashboard_view_all),
            onActionClick = if (state.recentTransactions.isNotEmpty()) {
                onOpenFinance
            } else {
                onOpenSnippets
            },
        )

        Spacer(modifier = Modifier.height(10.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                DevLedgerColors.Border,
            ),
            colors = CardDefaults.cardColors(
                containerColor = DevLedgerColors.SurfaceCard,
            ),
        ) {
            Column {
                when {
                    state.recentTransactions.isNotEmpty() -> {
                        state.recentTransactions.forEachIndexed {
                                index,
                                transaction,
                            ->
                            TransactionActivityRow(
                                transaction = transaction,
                                convertedAmount = runCatching {
                                    CurrencyConverter.convert(
                                        amount = transaction.amount,
                                        from = transaction.currency,
                                        to = state.baseCurrency,
                                    )
                                }.getOrDefault(transaction.amount),
                                currencyFormat = currencyFormat,
                                onClick = onOpenFinance,
                            )

                            if (
                                index <
                                state.recentTransactions.lastIndex
                            ) {
                                HorizontalDivider(
                                    color = DevLedgerColors.Border,
                                )
                            }
                        }
                    }

                    state.recentSnippets.isNotEmpty() -> {
                        state.recentSnippets.forEachIndexed {
                                index,
                                snippet,
                            ->
                            SnippetActivityRow(
                                snippet = snippet,
                                onClick = onOpenSnippets,
                            )

                            if (index < state.recentSnippets.lastIndex) {
                                HorizontalDivider(
                                    color = DevLedgerColors.Border,
                                )
                            }
                        }
                    }

                    else -> {
                        Text(
                            text = stringResource(
                                R.string.dashboard_no_recent_activity
                            ),
                            color = DevLedgerColors.TextMuted,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionActivityRow(
    transaction: TransactionEntity,
    convertedAmount: Double,
    currencyFormat: NumberFormat,
    onClick: () -> Unit,
) {
    val isIncome = transaction.type.equals(
        "INCOME",
        ignoreCase = true,
    )

    val color = if (isIncome) {
        DevLedgerColors.SuccessGreen
    } else {
        DevLedgerColors.ErrorRed
    }

    val dateFormat = remember {
        SimpleDateFormat("d MMM yyyy", Locale.getDefault())
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.17f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.AttachMoney,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(22.dp),
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = transaction.description
                    ?.takeIf { it.isNotBlank() }
                    ?: transaction.category,
                color = DevLedgerColors.TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = transaction.category,
                color = DevLedgerColors.TextMuted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                text = buildString {
                    append(if (isIncome) "+ " else "- ")
                    append(currencyFormat.format(convertedAmount))
                },
                color = color,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )

            Text(
                text = dateFormat.format(transaction.transactionDate),
                color = DevLedgerColors.TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun SnippetActivityRow(
    snippet: SnippetEntity,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(
                    DevLedgerColors.AccentBlue.copy(alpha = 0.17f)
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Code,
                contentDescription = null,
                tint = DevLedgerColors.AccentBlue,
                modifier = Modifier.size(22.dp),
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = snippet.title,
                color = DevLedgerColors.TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = snippet.language,
                color = DevLedgerColors.TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun SectionHeading(
    title: String,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = DevLedgerColors.TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )

        Spacer(modifier = Modifier.weight(1f))

        if (actionText != null && onActionClick != null) {
            Text(
                text = actionText,
                color = DevLedgerColors.AccentBlue,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.clickable(onClick = onActionClick),
            )
        }
    }
}

@Preview(
    showBackground = true,
    widthDp = 390,
    heightDp = 844,
)
@Composable
private fun DashboardScreenPreview() {
    val now = System.currentTimeMillis()

    DevLedgerTheme {
        DashboardScreenContent(
            onOpenProfile = {},
            state = DashboardUiState(
                user = UserEntity(
                    userId = "user123",
                    email = "john@example.com",
                    fullName = "John Doe",
                    ssoProvider = null,
                ),
                baseCurrency = "ZAR",
                totalSnippets = 5,
                monthlyIncome = 45_000.0,
                monthlyExpenses = 12_000.0,
                monthlyTransactions = listOf(
                    TransactionEntity(
                        transactionId = "t1",
                        userId = "user123",
                        type = "INCOME",
                        amount = 45_000.0,
                        currency = "ZAR",
                        category = "Development",
                        description = "Payment received",
                        clientOrFrom = "Acme Corp",
                        relatedProject = "API Overhaul",
                        paymentMethod = "EFT",
                        referenceNumber = "REF-001",
                        transactionDate = now - 172_800_000L,
                        createdAt = now - 172_800_000L,
                    ),
                    TransactionEntity(
                        transactionId = "t2",
                        userId = "user123",
                        type = "EXPENSE",
                        amount = 12_000.0,
                        currency = "ZAR",
                        category = "Cloud Services",
                        description = "Hosting expense",
                        clientOrFrom = "AWS",
                        relatedProject = "Production",
                        paymentMethod = "Credit Card",
                        referenceNumber = "INV-99",
                        transactionDate = now - 86_400_000L,
                        createdAt = now - 86_400_000L,
                    ),
                ),
                recentTransactions = listOf(
                    TransactionEntity(
                        transactionId = "t1",
                        userId = "user123",
                        type = "INCOME",
                        amount = 45_000.0,
                        currency = "ZAR",
                        category = "Development",
                        description = "Payment received",
                        clientOrFrom = "Acme Corp",
                        relatedProject = "API Overhaul",
                        paymentMethod = "EFT",
                        referenceNumber = "REF-001",
                        transactionDate = now - 172_800_000L,
                        createdAt = now - 172_800_000L,
                    ),
                    TransactionEntity(
                        transactionId = "t2",
                        userId = "user123",
                        type = "EXPENSE",
                        amount = 12_000.0,
                        currency = "ZAR",
                        category = "Cloud Services",
                        description = "Hosting expense",
                        clientOrFrom = "AWS",
                        relatedProject = "Production",
                        paymentMethod = "Credit Card",
                        referenceNumber = "INV-99",
                        transactionDate = now - 86_400_000L,
                        createdAt = now - 86_400_000L,
                    ),
                ),
            ),
            onOpenSnippets = {},
            onOpenFinance = {},
            onOpenSettings = {},
            onOpenNotifications = {},
            onCreateSnippet = {},
            onAddTransaction = {},
            onOpenSync = {},
            onOpenRecycle = {},
        )
    }
}
