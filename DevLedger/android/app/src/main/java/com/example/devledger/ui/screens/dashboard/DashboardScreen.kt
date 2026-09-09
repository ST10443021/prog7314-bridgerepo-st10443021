package com.devledger.app.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.devledger.app.R
import com.devledger.app.ui.components.DevLedgerCard
import com.devledger.app.ui.theme.DevLedgerColors
import java.text.NumberFormat
import java.util.Locale

/** Home dashboard: snippet/income/expense summary cards, quick actions and recent
 *  snippets — matches Figure "Dashboard Screen" from the Part 1 design document. */
@Composable
fun DashboardScreen(
    onOpenSnippets: () -> Unit,
    onOpenFinance: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-ZA")) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DevLedgerColors.BgDeep),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column {
                Text(stringResource(R.string.dashboard_welcome_back), color = DevLedgerColors.TextMuted, fontSize = 13.sp)
                Text(
                    text = state.user?.fullName ?: "Developer",
                    color = DevLedgerColors.TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(stringResource(R.string.dashboard_overview), color = DevLedgerColors.TextMuted, fontSize = 12.sp)
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryStat(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.dashboard_total_snippets),
                    value = state.totalSnippets.toString(),
                    color = DevLedgerColors.AccentBlue,
                )
                SummaryStat(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.dashboard_monthly_income),
                    value = currencyFormat.format(state.monthlyIncome),
                    color = DevLedgerColors.SuccessGreen,
                )
            }
        }
        item {
            SummaryStat(
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.dashboard_monthly_expenses),
                value = currencyFormat.format(state.monthlyExpenses),
                color = DevLedgerColors.ErrorRed,
            )
        }

        item {
            Text(stringResource(R.string.dashboard_quick_actions), color = DevLedgerColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickActionTile(Modifier.weight(1f), stringResource(R.string.nav_snippets), onOpenSnippets)
                QuickActionTile(Modifier.weight(1f), stringResource(R.string.nav_finance), onOpenFinance)
                QuickActionTile(Modifier.weight(1f), stringResource(R.string.settings_title), onOpenSettings)
            }
        }

        item {
            Text(stringResource(R.string.dashboard_recent_activity), color = DevLedgerColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        items(state.recentSnippets, key = { it.snippetId }) { snippet ->
            DevLedgerCard {
                Text(snippet.title, color = DevLedgerColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(snippet.language, color = DevLedgerColors.TextMuted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun SummaryStat(modifier: Modifier = Modifier, label: String, value: String, color: Color) {
    DevLedgerCard(modifier = modifier) {
        Text(label, color = DevLedgerColors.TextMuted, fontSize = 11.sp)
        Text(value, color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun QuickActionTile(modifier: Modifier = Modifier, label: String, onClick: () -> Unit) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = DevLedgerColors.SurfaceCard),
    ) {
        Box(modifier = Modifier.padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
            Text(label, color = DevLedgerColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}
