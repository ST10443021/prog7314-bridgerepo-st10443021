package com.devledger.app.ui.screens.finance

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devledger.app.data.repository.AuthRepository
import com.devledger.app.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale
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

/** Backs the "Add Income/Expense" screen — validates type, amount, currency and
 *  category before saving, per the field requirements in the Part 1 design doc. */
@HiltViewModel
class AddTransactionViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val transactionRepository: TransactionRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddTransactionUiState())
    val uiState: StateFlow<AddTransactionUiState> = _uiState.asStateFlow()

private fun edit(
        transform: (AddTransactionUiState) -> AddTransactionUiState,
    ) {
        val state = _uiState.value
        if (state.isSaving || state.saved) return
        _uiState.value = transform(state).copy(errorMessage = null)
    }

    fun onTypeChange(type: String) = edit { it.copy(type = type) }
    fun onAmountChange(v: String) = edit { it.copy(amount = v) }
    fun onCurrencyChange(v: String) = edit { it.copy(currency = v) }
    fun onCategoryChange(v: String) = edit { it.copy(category = v) }
    fun onDescriptionChange(v: String) = edit { it.copy(description = v) }

    fun save() {
        val state = _uiState.value
        if (state.isSaving || state.saved) return

        val amount = state.amount.trim().toDoubleOrNull()
        val currency = state.currency.trim().uppercase(Locale.ROOT)

        val error = when {
            state.type !in setOf("INCOME", "EXPENSE") ->
                "Choose income or expense."
            amount == null || !amount.isFinite() || amount <= 0.0 ->
                "Enter an amount greater than zero."
            !currency.matches(Regex("[A-Z]{3}")) ->
                "Choose a valid currency."
            state.category.isBlank() ->
                "Enter a category."
            else -> null
        }

        if (error != null) {
            _uiState.value = state.copy(errorMessage = error)
            return
        }

        val validAmount = amount ?: return
        _uiState.value = state.copy(isSaving = true, errorMessage = null)

        viewModelScope.launch {
            try {
                val user = authRepository.currentUser.first()
                if (user == null) {
                    Log.w(TAG, "save() called with no signed-in user cached — this should not happen post-auth")
                    _uiState.value = _uiState.value.copy(errorMessage = "Please sign in before saving a transaction.")
                    return@launch
                }

                Log.i(TAG, "Saving $validAmount $currency ${state.type} transaction (${state.category}) for user ${user.userId}")

                transactionRepository.addTransaction(
                    userId = user.userId,
                    type = state.type,
                    amount = validAmount,
                    currency = currency,
                    category = state.category.trim(),
                    description = state.description.trim().ifBlank { null },
                    transactionDate = System.currentTimeMillis(),
                )

                _uiState.value = _uiState.value.copy(saved = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save transaction", e)
                _uiState.value = _uiState.value.copy(errorMessage = "Could not save the transaction. Please try again.")
            } finally {
                _uiState.value = _uiState.value.copy(isSaving = false)
            }
        }
    }

    private companion object {
        const val TAG = "AddTransactionViewModel"
    }
            try {
                val user = authRepository.currentUser.first()
                if (user == null) {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "Please sign in before saving a transaction.",
                    )
                    return@launch
                }

                transactionRepository.addTransaction(
                    userId = user.userId,
                    type = state.type,
                    amount = validAmount,
                    currency = currency,
                    category = state.category.trim(),
                    description = state.description.trim().ifBlank { null },
                    transactionDate = System.currentTimeMillis(),
                )

                _uiState.value = _uiState.value.copy(saved = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Could not save the transaction. Please try again.",
                )
            } finally {
                _uiState.value = _uiState.value.copy(isSaving = false)
            }
        }
    }
}