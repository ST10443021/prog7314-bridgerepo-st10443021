package com.devledger.app.ui.screens.finance

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devledger.app.data.repository.AuthRepository
import com.devledger.app.data.repository.FolderRepository
import com.devledger.app.data.repository.TransactionRepository
import com.devledger.app.util.CurrencyConverter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

val IncomeCategories = listOf("Project Payment", "Freelance Work", "Consulting", "Salary", "Other Income")
val ExpenseCategories = listOf("Tools & Subscriptions", "Hosting & Domains", "Marketing", "Equipment", "Other Expenses")
val PaymentMethods = listOf("Bank Transfer", "Cash", "Credit Card", "PayPal", "Other")
val RecurringFrequencies = listOf("WEEKLY" to "Weekly", "MONTHLY" to "Monthly", "YEARLY" to "Yearly")
private const val MaxAttachmentBytes = 2 * 1024 * 1024

data class AddTransactionUiState(
    val type: String = "EXPENSE",
    val amount: String = "",
    val currency: String = "ZAR",
    val category: String = "",
    val description: String = "",
    val clientOrFrom: String = "",
    val relatedProject: String = "",
    val paymentMethod: String = "Bank Transfer",
    val referenceNumber: String = "",
    val transactionDateMillis: Long = System.currentTimeMillis(),
    val isRecurring: Boolean = false,
    val recurringFrequency: String = "MONTHLY",
    val attachmentFileName: String? = null,
    val attachmentBase64: String? = null,
    val attachmentTooLarge: Boolean = false,
    val projectSuggestions: List<String> = emptyList(),
    val availableCurrencies: List<String> = CurrencyConverter.supportedCurrencies(),
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val saved: Boolean = false,
) {
    val categories: List<String> get() = if (type == "INCOME") IncomeCategories else ExpenseCategories
}

/** Backs "Add Transaction" — matches every field in the Part 1 mockup: type toggle,
 *  amount, category/date, description, client/from, related project, payment method,
 *  reference number, recurring setup, and an optional attachment. */
@HiltViewModel
/**
 * Reference scope: UI and state-holder separation.
 * (Google Developers, n.d.a; Google Developers, n.d.f). See REFERENCES-CODE.md for full entries.
 */
class AddTransactionViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val transactionRepository: TransactionRepository,
    private val folderRepository: FolderRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddTransactionUiState())
    val uiState: StateFlow<AddTransactionUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val user = authRepository.currentUser.first { it != null } ?: return@launch
            
            // Combine project suggestions from existing transactions AND folders
            folderRepository.observeFolders().collectLatest { folders ->
                val folderNames = folders.map { it.name }
                val transactionProjects = transactionRepository.getDistinctProjects(user.userId)
                
                _uiState.value = _uiState.value.copy(
                    projectSuggestions = (folderNames + transactionProjects).distinct().sorted()
                )
            }
        }
    }

    private fun edit(transform: (AddTransactionUiState) -> AddTransactionUiState) {
        val state = _uiState.value
        if (state.isSaving || state.saved) return
        _uiState.value = transform(state).copy(errorMessage = null)
    }

    fun onTypeChange(type: String) = edit { it.copy(type = type, category = "") }
    fun onAmountChange(v: String) = edit { it.copy(amount = v) }
    fun onCurrencyChange(v: String) = edit { it.copy(currency = v) }
    fun onCategoryChange(v: String) = edit { it.copy(category = v) }
    fun onDescriptionChange(v: String) = edit { if (v.length <= 200) it.copy(description = v) else it }
    fun onClientOrFromChange(v: String) = edit { it.copy(clientOrFrom = v) }
    fun onRelatedProjectChange(v: String) = edit { it.copy(relatedProject = v) }
    fun onPaymentMethodChange(v: String) = edit { it.copy(paymentMethod = v) }
    fun onReferenceNumberChange(v: String) = edit { it.copy(referenceNumber = v) }
    fun onDateChange(millis: Long) = edit { it.copy(transactionDateMillis = millis) }
    fun onRecurringToggle(enabled: Boolean) = edit { it.copy(isRecurring = enabled) }
    fun onRecurringFrequencyChange(freq: String) = edit { it.copy(recurringFrequency = freq) }

    fun createFolderAndSelect(name: String) {
        viewModelScope.launch {
            try {
                folderRepository.create(name)
                // The observeFolders collector will update suggestions.
                // We select it immediately in the UI state.
                edit { it.copy(relatedProject = name) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create folder", e)
            }
        }
    }

    /** [fileSizeBytes] is checked here so the UI can show a rejection immediately,
     *  before spending memory holding an oversized base64 string in state. */
    fun onAttachmentSelected(fileName: String, base64: String, fileSizeBytes: Long) = edit {
        if (fileSizeBytes > MaxAttachmentBytes) {
            it.copy(attachmentTooLarge = true, attachmentFileName = null, attachmentBase64 = null)
        } else {
            it.copy(attachmentTooLarge = false, attachmentFileName = fileName, attachmentBase64 = base64)
        }
    }
    fun onAttachmentRemoved() = edit { it.copy(attachmentFileName = null, attachmentBase64 = null, attachmentTooLarge = false) }

    fun save() {
        val state = _uiState.value
        if (state.isSaving || state.saved) return

        val amount = state.amount.trim().toDoubleOrNull()
        val currency = state.currency.trim().uppercase(Locale.ROOT)

        val error = when {
            amount == null || !amount.isFinite() || amount <= 0.0 -> "Enter an amount greater than zero."
            state.category.isBlank() -> "Choose a category."
            state.description.isBlank() -> "Enter a description."
            state.clientOrFrom.isBlank() -> "Enter a client or source name."
            state.isRecurring && state.recurringFrequency.isBlank() -> "Choose a recurring frequency."
            state.attachmentTooLarge -> "The attachment is larger than 2MB — choose a smaller file."
            else -> null
        }
        if (error != null) { _uiState.value = state.copy(errorMessage = error); return }

        val validAmount = amount ?: return
        _uiState.value = state.copy(isSaving = true, errorMessage = null)

        viewModelScope.launch {
            try {
                val user = authRepository.currentUser.first()
                if (user == null) {
                    Log.w(TAG, "save() called with no signed-in user cached")
                    _uiState.value = _uiState.value.copy(errorMessage = "Please sign in before saving a transaction.")
                    return@launch
                }

                Log.i(TAG, "Saving $validAmount $currency ${state.type} transaction (${state.category}) for user ${user.userId}")

                val nextOccurrence = if (state.isRecurring) advanceOnce(state.transactionDateMillis, state.recurringFrequency) else null

                transactionRepository.addTransaction(
                    userId = user.userId, type = state.type, amount = validAmount, currency = currency,
                    category = state.category, description = state.description.trim(),
                    clientOrFrom = state.clientOrFrom.trim(), relatedProject = state.relatedProject.trim().ifBlank { null },
                    paymentMethod = state.paymentMethod, referenceNumber = state.referenceNumber.trim().ifBlank { null },
                    isRecurring = state.isRecurring, recurringFrequency = if (state.isRecurring) state.recurringFrequency else null,
                    nextOccurrenceDate = nextOccurrence, attachmentFileName = state.attachmentFileName,
                    attachmentBase64 = state.attachmentBase64, transactionDate = state.transactionDateMillis,
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

    private fun advanceOnce(fromMillis: Long, frequency: String): Long {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = fromMillis }
        when (frequency) {
            "WEEKLY" -> cal.add(java.util.Calendar.WEEK_OF_YEAR, 1)
            "YEARLY" -> cal.add(java.util.Calendar.YEAR, 1)
            else -> cal.add(java.util.Calendar.MONTH, 1)
        }
        return cal.timeInMillis
    }

    private companion object { const val TAG = "AddTransactionViewModel" }
}