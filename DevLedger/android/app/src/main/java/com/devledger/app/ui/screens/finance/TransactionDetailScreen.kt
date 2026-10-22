package com.devledger.app.ui.screens.finance

import com.devledger.app.util.localization.rememberAppStrings

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import com.devledger.app.R
import kotlinx.coroutines.CancellationException
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devledger.app.data.local.entity.TransactionEntity
import com.devledger.app.data.repository.TransactionRepository
import com.devledger.app.ui.components.DevLedgerCard
import com.devledger.app.ui.components.DevLedgerTopBar
import com.devledger.app.ui.theme.DevLedgerColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
/**
 * Reference scope: UI and state-holder separation.
 * (Google Developers, n.d.a; Google Developers, n.d.f). See REFERENCES-CODE.md for full entries.
 */
class TransactionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val transactionRepository: TransactionRepository,
) : ViewModel() {
    private val transactionId: String = requireNotNull(savedStateHandle["transactionId"])
    private val _transaction = MutableStateFlow<TransactionEntity?>(null)
    val transaction: StateFlow<TransactionEntity?> = _transaction.asStateFlow()
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isDeleting = MutableStateFlow(false)
    val isDeleting: StateFlow<Boolean> = _isDeleting.asStateFlow()
    private val _isDeleted = MutableStateFlow(false)
    val isDeleted: StateFlow<Boolean> = _isDeleted.asStateFlow()
    private val _deleteFailed = MutableStateFlow(false)
    val deleteFailed: StateFlow<Boolean> = _deleteFailed.asStateFlow()

    fun deleteTransaction() {
        val row = _transaction.value ?: return
        if (_isDeleting.value || _isDeleted.value) return
        _isDeleting.value = true
        _deleteFailed.value = false
        viewModelScope.launch {
            try {
                transactionRepository.delete(row)
                _isDeleted.value = true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _deleteFailed.value = true
            } finally {
                _isDeleting.value = false
            }
        }
    }

    init { viewModelScope.launch { _transaction.value = transactionRepository.getById(transactionId); _isLoading.value = false } }
}

/** Full detail view for a single transaction — every field the mockup's "Add
 *  Transaction" form collects, shown back for review, matching the requirement that a
 *  user can view all details of a specific income/expense entry. */
@Composable
/**
 * Reference scope: UI and state-holder separation.
 * (Google Developers, n.d.a; Google Developers, n.d.f). See REFERENCES-CODE.md for full entries.
 */
fun TransactionDetailScreen(onBack: () -> Unit, viewModel: TransactionDetailViewModel = hiltViewModel()) {
    val strings = rememberAppStrings()
    val tx by viewModel.transaction.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isDeleting by viewModel.isDeleting.collectAsState()
    val isDeleted by viewModel.isDeleted.collectAsState()
    val deleteFailed by viewModel.deleteFailed.collectAsState()
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(isDeleted) { if (isDeleted) onBack() }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { if (!isDeleting) confirmDelete = false },
            title = { Text(stringResource(R.string.sync_delete_transaction)) },
            text = { Text(stringResource(R.string.sync_delete_transaction_explanation)) },
            confirmButton = {
                TextButton(enabled = !isDeleting, onClick = {
                    confirmDelete = false
                    viewModel.deleteTransaction()
                }) { Text(stringResource(R.string.sync_delete_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.sync_delete_cancel)) }
            },
        )
    }
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-ZA")) }
    val dateFormat = remember(strings.locale) { SimpleDateFormat("d MMMM yyyy", strings.locale) }

    Column(modifier = Modifier.fillMaxSize().background(DevLedgerColors.BgDeep)) {
        DevLedgerTopBar(title = strings.get(R.string.lang_transaction_details), onBack = onBack)
        if (isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

        val transaction = tx
        if (transaction == null) {
            if (!isLoading) Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text(strings.get(R.string.lang_this_transaction_could_not_be_found), color = DevLedgerColors.TextMuted)
            }
        } else {
            val accent = if (transaction.type == "INCOME") DevLedgerColors.SuccessGreen else DevLedgerColors.ErrorRed
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                DevLedgerCard {
                    Text(if (transaction.type == "INCOME") strings.get(R.string.lang_income) else strings.get(R.string.lang_expense), color = accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(currencyFormat.format(transaction.amount), color = DevLedgerColors.TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text(strings.label(transaction.category), color = DevLedgerColors.TextMuted, fontSize = 13.sp)
                }
                Spacer(Modifier.height(16.dp))
                DetailRow(strings.get(R.string.lang_description_label), transaction.description ?: "—")
                DetailRow(strings.get(R.string.lang_client_from_label), transaction.clientOrFrom)
                DetailRow(strings.get(R.string.lang_related_project), transaction.relatedProject ?: "—")
                DetailRow(strings.get(R.string.lang_payment_method), transaction.paymentMethod?.let(strings::label) ?: "—")
                DetailRow(strings.get(R.string.lang_reference_invoice), transaction.referenceNumber ?: "—")
                DetailRow(strings.get(R.string.lang_date_label), dateFormat.format(Date(transaction.transactionDate)))
                DetailRow(strings.get(R.string.lang_recurring), if (transaction.isRecurring) strings.get(R.string.lang_recurring_value, strings.label(transaction.recurringFrequency.orEmpty())) else strings.get(R.string.lang_no))

                if (transaction.attachmentFileName != null) {
                    AttachmentPreview(transaction.attachmentFileName, transaction.attachmentBase64)
                }
                if (deleteFailed) {
                    Text(stringResource(R.string.sync_delete_failed), color = DevLedgerColors.ErrorRed)
                }
                OutlinedButton(
                    enabled = !isDeleting,
                    onClick = { confirmDelete = true },
                    border = BorderStroke(1.dp, DevLedgerColors.ErrorRed),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                ) {
                    Text(stringResource(if (isDeleting) R.string.sync_deleting else R.string.sync_delete_transaction),
                        color = DevLedgerColors.ErrorRed)
                }
            }
        }
    }
}

@Composable
private fun AttachmentPreview(fileName: String, base64: String?) {
    val strings = rememberAppStrings()
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
        Text(strings.get(R.string.lang_attachment), color = DevLedgerColors.TextMuted, fontSize = 11.sp, modifier = Modifier.padding(bottom = 8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(DevLedgerColors.SurfaceCard)
                .border(1.dp, DevLedgerColors.Border, RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AttachFile, contentDescription = null, tint = DevLedgerColors.TextMuted, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(fileName, color = DevLedgerColors.TextPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                if (base64 != null) {
                    val bitmap = remember(base64) {
                        try {
                            val decodedString = Base64.decode(base64, Base64.DEFAULT)
                            BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)
                        } catch (e: Exception) {
                            null
                        }
                    }

                    if (bitmap != null) {
                        Spacer(Modifier.height(12.dp))
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = strings.get(R.string.lang_attachment_preview),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 400.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
        Text(label, color = DevLedgerColors.TextMuted, fontSize = 11.sp)
        Text(value, color = DevLedgerColors.TextPrimary, fontSize = 14.sp)
    }
}
