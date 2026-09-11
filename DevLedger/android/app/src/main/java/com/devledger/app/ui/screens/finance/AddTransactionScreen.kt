package com.devledger.app.ui.screens.finance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.devledger.app.R
import com.devledger.app.ui.components.DevLedgerPrimaryButton
import com.devledger.app.ui.components.DevLedgerTextField
import com.devledger.app.ui.components.DevLedgerTopBar
import com.devledger.app.ui.theme.DevLedgerColors
import com.devledger.app.util.CurrencyConverter

/** "Add Income/Expense" screen — records a transaction. Type, amount, category and
 *  date are required fields per the Part 1 design doc's field spec. */
@Composable
fun AddTransactionScreen(
    onSaved: () -> Unit,
    onBack: () -> Unit,
    viewModel: AddTransactionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.saved) {
        if (state.saved) onSaved()
    }

    Column(modifier = Modifier.fillMaxSize().background(DevLedgerColors.BgDeep)) {
        DevLedgerTopBar(title = stringResource(R.string.add_tx_title), onBack = onBack)
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 16.dp)) {
                FilterChip(
                    selected = state.type == "INCOME",
                    onClick = { viewModel.onTypeChange("INCOME") },
                    label = { androidx.compose.material3.Text(stringResource(R.string.add_tx_income)) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = DevLedgerColors.SuccessGreen),
                )
                FilterChip(
                    selected = state.type == "EXPENSE",
                    onClick = { viewModel.onTypeChange("EXPENSE") },
                    label = { androidx.compose.material3.Text(stringResource(R.string.add_tx_expense)) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = DevLedgerColors.ErrorRed),
                )
            }

            DevLedgerTextField(
                value = state.amount,
                onValueChange = viewModel::onAmountChange,
                label = stringResource(R.string.add_tx_amount) + " (${state.currency})",
                isError = state.errorMessage != null,
                supportingText = state.errorMessage,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                CurrencyConverter.supportedCurrencies().forEach { code ->
                    FilterChip(
                        selected = state.currency == code,
                        onClick = { viewModel.onCurrencyChange(code) },
                        label = { androidx.compose.material3.Text(code) },
                    )
                }
            }

            DevLedgerTextField(
                value = state.category,
                onValueChange = viewModel::onCategoryChange,
                label = stringResource(R.string.add_tx_category),
                modifier = Modifier.padding(bottom = 12.dp),
            )
            DevLedgerTextField(
                value = state.description,
                onValueChange = viewModel::onDescriptionChange,
                label = stringResource(R.string.add_tx_description),
                singleLine = false,
                minLines = 2,
                modifier = Modifier.padding(bottom = 20.dp),
            )

            DevLedgerPrimaryButton(
                text = stringResource(R.string.add_tx_save),
                onClick = viewModel::save,
                loading = state.isSaving,
            )
        }
    }
}
