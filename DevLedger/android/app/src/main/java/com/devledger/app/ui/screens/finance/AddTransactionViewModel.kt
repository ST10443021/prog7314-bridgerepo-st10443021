package com.devledger.app.ui.screens.finance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devledger.app.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddTransactionUiState(
    val type: String = "EXPENSE",
    val amount: String = "",
    val currency: String = "ZAR",
    val category: String = "",
    val description: String = "",
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val saved: Boolean = false,
)

/** Backs the "Add Income/Expense" screen. Validates that type, amount, category and
 *  date are present, per the field requirements in the Part 1 design doc. */
@HiltViewModel
class AddTransactionViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddTransactionUiState())
    val uiState: StateFlow<AddTransactionUiState> = _uiState.asStateFlow()

    fun onTypeChange(type: String) { _uiState.value = _uiState.value.copy(type = type) }
    fun onAmountChange(v: String) { _uiState.value = _uiState.value.copy(amount = v, errorMessage = null) }
    fun onCurrencyChange(v: String) { _uiState.value = _uiState.value.copy(currency = v) }
    fun onCategoryChange(v: String) { _uiState.value = _uiState.value.copy(category = v, errorMessage = null) }
    fun onDescriptionChange(v: String) { _uiState.value = _uiState.value.copy(description = v) }

    fun save() {
        val state = _uiState.value
        val amountValue = state.amount.toDoubleOrNull()
        if (amountValue == null || amountValue <= 0.0 || state.category.isBlank()) {
            _uiState.value = state.copy(errorMessage = "A valid amount and category are required.")
            return
        }
        _uiState.value = state.copy(isSaving = true)
        viewModelScope.launch {
            transactionRepository.addTransaction(
                userId = "local-user",
                type = state.type,
                amount = amountValue,
                currency = state.currency,
                category = state.category,
                description = state.description.ifBlank { null },
                transactionDate = System.currentTimeMillis(),
            )
            _uiState.value = _uiState.value.copy(isSaving = false, saved = true)
        }
    }
}
