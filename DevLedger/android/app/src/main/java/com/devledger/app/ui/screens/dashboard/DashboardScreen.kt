package com.devledger.app.ui.screens.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.devledger.app.R
import com.devledger.app.ui.components.DevLedgerCard
import com.devledger.app.ui.theme.DevLedgerColors
import java.text.NumberFormat
import java.util.Locale

/**
 * Home dashboard aligned with the Dashboard Screen from the Part 1 design document.
 */
@Composable
fun DashboardScreen(
    onOpenSnippets: () -> Unit,
    onOpenFinance: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenNotifications: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    val currencyFormat = remember {
        NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-ZA"))
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
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            DashboardHeader(onOpenNotifications = onOpenNotifications)
        }

        item {
            Column {
                Text(
                    text = stringResource(R.string.dashboard_welcome_back),
                    color = DevLedgerColors.TextMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )

                Text(
                    text = state.user?.fullName ?: "Developer",
                    color = DevLedgerColors.TextPrimary,
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Text(
                    text = stringResource(R.string.dashboard_overview),
                    color = DevLedgerColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SummaryStat(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Code,
                    label = stringResource(R.string.dashboard_total_snippets),
                    value = state.totalSnippets.toString(),
                    color = DevLedgerColors.AccentBlue,
                )

                SummaryStat(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.AttachMoney,
                    label = stringResource(R.string.dashboard_monthly_income),
                    value = currencyFormat.format(state.monthlyIncome),
                    color = DevLedgerColors.SuccessGreen,
                )

                SummaryStat(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.AttachMoney,
                    label = stringResource(R.string.dashboard_monthly_expenses),
                    value = currencyFormat.format(state.monthlyExpenses),
                    color = DevLedgerColors.ErrorRed,
                )
            }
        }

        item {
            FinancialOverviewCard(
                income = state.monthlyIncome,
                expenses = state.monthlyExpenses,
                netIncome = netIncome,
                currencyFormat = currencyFormat,
            )
        }

        item {
            SectionHeading(
                title = stringResource(R.string.dashboard_quick_actions),
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
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
                    icon = Icons.Filled.AttachMoney,
                    label = stringResource(R.string.nav_finance),
                    iconColor = DevLedgerColors.SuccessGreen,
                    onClick = onOpenFinance,
                )

                QuickActionTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Settings,
                    label = stringResource(R.string.settings_title),
                    iconColor = DevLedgerColors.AccentPurple,
                    onClick = onOpenSettings,
                )
            }
        }

        item {
            SectionHeading(
                title = stringResource(R.string.dashboard_recent_activity),
                actionText = stringResource(R.string.dashboard_view_all),
                onActionClick = onOpenSnippets,
            )
        }

        if (state.recentSnippets.isEmpty()) {
            item {
                DevLedgerCard {
                    Text(
                        text = stringResource(
                            R.string.dashboard_no_recent_activity
                        ),
                        color = DevLedgerColors.TextMuted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        } else {
            items(
                items = state.recentSnippets,
                key = { it.snippetId },
            ) { snippet ->
                DevLedgerCard(
                    modifier = Modifier.clickable(onClick = onOpenSnippets),
                ) {
                    Row(
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
                                modifier = Modifier.size(22.dp),
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = snippet.title,
                                color = DevLedgerColors.TextPrimary,
                                style = MaterialTheme.typography.bodyLarge,
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
            }
        }
    }
}

@Composable
private fun DashboardHeader(
    onOpenNotifications: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(DevLedgerColors.AccentBlueDark),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Code,
                contentDescription = null,
                tint = DevLedgerColors.AccentBlue,
                modifier = Modifier.size(22.dp),
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text = stringResource(R.string.app_name),
            color = DevLedgerColors.TextPrimary,
            style = MaterialTheme.typography.titleLarge,
        )

        Spacer(modifier = Modifier.weight(1f))

        IconButton(
            onClick = onOpenNotifications,
            modifier = Modifier
                .size(40.dp)
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

        Icon(
            imageVector = Icons.Filled.AccountCircle,
            contentDescription = null,
            tint = DevLedgerColors.AccentBlue,
            modifier = Modifier.size(40.dp),
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
        modifier = modifier.heightIn(min = 108.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, DevLedgerColors.Border),
        colors = CardDefaults.cardColors(
            containerColor = DevLedgerColors.SurfaceCard,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(16.dp),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = label,
                color = DevLedgerColors.TextMuted,
                fontSize = 10.sp,
                lineHeight = 12.sp,
                maxLines = 2,
            )

            Text(
                text = value,
                color = color,
                fontSize = 15.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FinancialOverviewCard(
    income: Double,
    expenses: Double,
    netIncome: Double,
    currencyFormat: NumberFormat,
) {
    val total = income + expenses
    val incomeShare = if (total > 0) {
        (income / total).toFloat().coerceIn(0f, 1f)
    } else {
        0f
    }

    val expenseShare = if (total > 0) {
        (expenses / total).toFloat().coerceIn(0f, 1f)
    } else {
        0f
    }

    DevLedgerCard {
        Text(
            text = stringResource(R.string.dashboard_financial_overview),
            color = DevLedgerColors.TextPrimary,
            style = MaterialTheme.typography.titleMedium,
        )

        Spacer(modifier = Modifier.height(14.dp))

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
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(18.dp))

        FinancialBar(
            label = stringResource(R.string.finance_total_income),
            value = currencyFormat.format(income),
            progress = incomeShare,
            color = DevLedgerColors.SuccessGreen,
        )

        Spacer(modifier = Modifier.height(12.dp))

        FinancialBar(
            label = stringResource(R.string.finance_total_expenses),
            value = currencyFormat.format(expenses),
            progress = expenseShare,
            color = DevLedgerColors.ErrorRed,
        )
    }
}

@Composable
private fun FinancialBar(
    label: String,
    value: String,
    progress: Float,
    color: Color,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = DevLedgerColors.TextSecondary,
            style = MaterialTheme.typography.bodySmall,
        )

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = value,
            color = color,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
        )
    }

    Spacer(modifier = Modifier.height(6.dp))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(DevLedgerColors.SurfaceCardAlt),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color),
        )
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
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, DevLedgerColors.Border),
        colors = CardDefaults.cardColors(
            containerColor = DevLedgerColors.SurfaceCard,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 88.dp)
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(19.dp),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = label,
                color = DevLedgerColors.TextPrimary,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}