package com.devledger.app.ui.screens.dashboard

import com.devledger.app.util.localization.rememberAppStrings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
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
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

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
    val strings = rememberAppStrings()
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
                userName = state.user?.fullName ?: strings.get(R.string.lang_developer),
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
    val strings = rememberAppStrings()
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
                contentDescription = strings.get(R.string.lang_open_profile),
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
    val strings = rememberAppStrings()
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
                    text = strings.get(R.string.lang_this_month),
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
                    color = DevLedgerColors.SuccessGreen,
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
                locale = strings.locale,
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

/**
 * Cumulative income / expense chart for the current month.
 *
 * Drawn with the Compose Canvas API (Google Developers, n.d.v):
 * - smooth, non-overshooting curves (each segment is a cubic Bezier whose control points share the
 *   segment's mid x-value, so a cumulative series never dips below or above its real values);
 * - a soft gradient fill and glow under each line, green for income and red for expenses so the
 *   colours match the figures printed under the chart;
 * - a y-axis scaled to round values, a day axis along the bottom and an end-point marker on each line;
 * - a left-to-right reveal animation using [Animatable] (Google Developers, n.d.w).
 * With no transactions it shows a quiet dashed baseline instead of two overlapping flat lines.
 */
@Composable
private fun FinancialChart(
    points: List<ChartPoint>,
    locale: Locale,
) {
    val gridColor = DevLedgerColors.Border.copy(alpha = 0.7f)
    val incomeColor = DevLedgerColors.SuccessGreen
    val expenseColor = DevLedgerColors.ErrorRed
    val labelColor = DevLedgerColors.TextMuted
    val markerCoreColor = DevLedgerColors.SurfaceCard

    val reveal = remember { Animatable(0f) }
    LaunchedEffect(points) {
        reveal.snapTo(0f)
        reveal.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        )
    }

    val labelPaint = remember {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    }
    val axisNumberFormat = remember(locale) {
        NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = 0
            maximumFractionDigits = 1
            isGroupingUsed = false
        }
    }

    val hasData = points.any { it.income > 0.0 || it.expenses > 0.0 }

    // A single day still needs two points to form a line.
    val series = if (points.size == 1) listOf(points[0], points[0]) else points

    val maximum = points.maxOfOrNull { maxOf(it.income, it.expenses) } ?: 0.0
    val axisStep = if (hasData) niceAxisStep(maximum / 3.0) else 1.0
    val axisMaximum = axisStep * 3.0

    // Up to five evenly spaced day labels, always including the first and last day.
    val dayLabelIndexes = if (points.size <= 1) {
        listOf(0)
    } else {
        val labelCount = minOf(5, points.size)
        (0 until labelCount)
            .map { (it * (points.size - 1f) / (labelCount - 1)).roundToInt() }
            .distinct()
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(176.dp),
    ) {
        val plotLeft = if (hasData) 40.dp.toPx() else 12.dp.toPx()
        val plotRight = size.width - 10.dp.toPx()
        val plotTop = 10.dp.toPx()
        val plotBottom = size.height - 22.dp.toPx()
        val plotWidth = plotRight - plotLeft
        val plotHeight = plotBottom - plotTop

        fun xAt(index: Int): Float =
            if (series.size <= 1) plotLeft else plotLeft + plotWidth * index / (series.size - 1f)

        fun yAt(value: Double): Float =
            plotBottom - (value / axisMaximum).toFloat().coerceIn(0f, 1f) * plotHeight

        labelPaint.textSize = 10.sp.toPx()
        labelPaint.color = labelColor.toArgb()

        // Grid lines (solid baseline, dashed guides) and y-axis labels.
        val dashed = PathEffect.dashPathEffect(
            floatArrayOf(4.dp.toPx(), 5.dp.toPx()),
            0f,
        )
        repeat(4) { index ->
            val y = plotBottom - plotHeight * index / 3f

            drawLine(
                color = gridColor,
                start = Offset(plotLeft, y),
                end = Offset(plotRight, y),
                strokeWidth = 1.dp.toPx(),
                pathEffect = if (index == 0) null else dashed,
            )

            if (hasData) {
                labelPaint.textAlign = android.graphics.Paint.Align.RIGHT
                drawContext.canvas.nativeCanvas.drawText(
                    compactAmount(axisStep * index, axisNumberFormat),
                    plotLeft - 8.dp.toPx(),
                    y + 3.5.dp.toPx(),
                    labelPaint,
                )
            }
        }

        // Day labels along the bottom edge.
        labelPaint.textAlign = android.graphics.Paint.Align.CENTER
        dayLabelIndexes.forEach { index ->
            val fraction = if (points.size <= 1) 0.5f else index / (points.size - 1f)
            drawContext.canvas.nativeCanvas.drawText(
                points[index].day.toString(),
                plotLeft + plotWidth * fraction,
                size.height - 4.dp.toPx(),
                labelPaint,
            )
        }

        // Everything below is revealed from left to right.
        val revealRight = if (reveal.value >= 1f) {
            size.width
        } else {
            plotLeft + plotWidth * reveal.value
        }

        clipRect(left = 0f, top = 0f, right = revealRight, bottom = size.height) {
            if (!hasData) {
                drawLine(
                    color = labelColor.copy(alpha = 0.6f),
                    start = Offset(plotLeft, plotBottom),
                    end = Offset(plotRight, plotBottom),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = dashed,
                )
                return@clipRect
            }

            fun buildLine(selector: (ChartPoint) -> Double): Path {
                val path = Path()
                series.forEachIndexed { index, point ->
                    val x = xAt(index)
                    val y = yAt(selector(point))
                    if (index == 0) {
                        path.moveTo(x, y)
                    } else {
                        val previousX = xAt(index - 1)
                        val previousY = yAt(selector(series[index - 1]))
                        val midX = (previousX + x) / 2f
                        path.cubicTo(midX, previousY, midX, y, x, y)
                    }
                }
                return path
            }

            fun drawSeries(color: Color, selector: (ChartPoint) -> Double) {
                val line = buildLine(selector)

                val area = Path().apply {
                    addPath(line)
                    lineTo(xAt(series.lastIndex), plotBottom)
                    lineTo(xAt(0), plotBottom)
                    close()
                }

                drawPath(
                    path = area,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            color.copy(alpha = 0.28f),
                            color.copy(alpha = 0f),
                        ),
                        startY = plotTop,
                        endY = plotBottom,
                    ),
                )

                // Soft glow, then the crisp line on top.
                drawPath(
                    path = line,
                    color = color.copy(alpha = 0.18f),
                    style = Stroke(
                        width = 8.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
                drawPath(
                    path = line,
                    color = color,
                    style = Stroke(
                        width = 2.5.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
            }

            drawSeries(incomeColor) { it.income }
            drawSeries(expenseColor) { it.expenses }

            // End-point markers (ring + dot) on the latest value of each line.
            val last = series.last()
            val lastX = xAt(series.lastIndex)
            listOf(
                incomeColor to last.income,
                expenseColor to last.expenses,
            ).forEach { (color, value) ->
                val center = Offset(lastX, yAt(value))
                drawCircle(color = color.copy(alpha = 0.25f), radius = 8.dp.toPx(), center = center)
                drawCircle(color = color, radius = 4.5.dp.toPx(), center = center)
                drawCircle(color = markerCoreColor, radius = 2.dp.toPx(), center = center)
            }
        }
    }
}

/** Rounds [raw] up to a "nice" axis step (1, 2, 2.5, 5 or 10 times a power of ten). */
private fun niceAxisStep(raw: Double): Double {
    if (raw <= 0.0) return 1.0
    val base = 10.0.pow(floor(log10(raw)))
    val fraction = raw / base
    val nice = when {
        fraction <= 1.0 -> 1.0
        fraction <= 2.0 -> 2.0
        fraction <= 2.5 -> 2.5
        fraction <= 5.0 -> 5.0
        else -> 10.0
    }
    return nice * base
}

/** Short axis label, for example 850, 1.5k or 2M (no currency symbol, the card already shows it). */
private fun compactAmount(value: Double, format: NumberFormat): String =
    when {
        value >= 1_000_000.0 -> format.format(value / 1_000_000.0) + "M"
        value >= 1_000.0 -> format.format(value / 1_000.0) + "k"
        else -> format.format(value)
    }

private fun createChartPoints(
    transactions: List<TransactionEntity>,
    baseCurrency: String,
): List<ChartPoint> {
    val calendar = Calendar.getInstance()
    val numberOfDays = calendar.getActualMaximum(
        Calendar.DAY_OF_MONTH
    )
    val today = calendar.get(Calendar.DAY_OF_MONTH)

    val transactionsByDay = transactions.groupBy { transaction ->
        Calendar.getInstance().apply {
            timeInMillis = transaction.transactionDate
        }.get(Calendar.DAY_OF_MONTH)
    }

    // The cumulative lines stop at today (or at the latest future-dated transaction this month)
    // instead of running flat to the end of the month, which looked like real data for days that
    // have not happened yet.
    val lastDay = maxOf(today, transactionsByDay.keys.maxOrNull() ?: 0)
        .coerceIn(1, numberOfDays)

    var cumulativeIncome = 0.0
    var cumulativeExpenses = 0.0

    return (1..lastDay).map { day ->
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
    val strings = rememberAppStrings()
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
                label = strings.get(R.string.lang_add_transaction),
                iconColor = DevLedgerColors.SuccessGreen,
                onClick = onAddTransaction,
            )

            QuickActionTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Code,
                label = strings.get(R.string.lang_new_snippet),
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
                label = strings.get(R.string.lang_sync),
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
                label = strings.get(R.string.lang_recycle_bin),
                iconColor = DevLedgerColors.ErrorRed,
                onClick = onOpenRecycle,
            )

            QuickActionTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Archive,
                label = strings.get(R.string.lang_archive),
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
    val strings = rememberAppStrings()
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
        SimpleDateFormat("d MMM yyyy", strings.locale)
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
                text = strings.label(transaction.category),
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
    val strings = rememberAppStrings()
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
                        paymentMethod = strings.get(R.string.lang_credit_card),
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
                        paymentMethod = strings.get(R.string.lang_credit_card),
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
