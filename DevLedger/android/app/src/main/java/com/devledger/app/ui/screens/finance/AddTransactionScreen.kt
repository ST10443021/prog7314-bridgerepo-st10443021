package com.devledger.app.ui.screens.finance

import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.devledger.app.R
import com.devledger.app.ui.components.DevLedgerTextField
import com.devledger.app.ui.components.DevLedgerTopBar
import com.devledger.app.ui.theme.DevLedgerColors
import java.text.SimpleDateFormat
import java.util.*

/** "Add Transaction" — matches every field group in the Part 1 mockup: type toggle,
 *  amount, category/date, description, client/related project, payment/reference,
 *  recurring setup, and an optional attachment (Google Developers, n.d.d for the
 *  Storage Access Framework picker used for the file itself). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(
    onSaved: () -> Unit,
    onBack: () -> Unit,
    viewModel: AddTransactionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(state.saved) { if (state.saved) onSaved() }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val resolver = context.contentResolver
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return@rememberLauncherForActivityResult
        val name = resolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        } ?: uri.lastPathSegment ?: "attachment"
        viewModel.onAttachmentSelected(name, Base64.encodeToString(bytes, Base64.NO_WRAP), bytes.size.toLong())
    }

    Column(modifier = Modifier.fillMaxSize().background(DevLedgerColors.BgDeep)) {
        DevLedgerTopBar(title = stringResource(R.string.add_tx_title), onBack = onBack)

        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            state.errorMessage?.let { Text(it, color = DevLedgerColors.ErrorRed, fontSize = 12.sp, modifier = Modifier.padding(bottom = 12.dp)) }

            // Income / Expense toggle
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TypeToggleCard(
                    label = "Income", sublabel = "Money you receive", icon = Icons.Filled.ArrowUpward,
                    color = DevLedgerColors.SuccessGreen, selected = state.type == "INCOME",
                    modifier = Modifier.weight(1f),
                ) { viewModel.onTypeChange("INCOME") }
                TypeToggleCard(
                    label = "Expense", sublabel = "Money you spend", icon = Icons.Filled.ArrowDownward,
                    color = DevLedgerColors.ErrorRed, selected = state.type == "EXPENSE",
                    modifier = Modifier.weight(1f),
                ) { viewModel.onTypeChange("EXPENSE") }
            }

            FieldLabel("Amount *")
            DevLedgerTextField(
                value = state.amount, onValueChange = viewModel::onAmountChange,
                label = "", placeholder = "0.00", modifier = Modifier.padding(bottom = 16.dp),
            )

            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    FieldLabel("Category *")
                    CategoryDropdown(state.categories, state.category, viewModel::onCategoryChange)
                }
                Column(Modifier.weight(1f)) {
                    FieldLabel("Date *")
                    DateField(state.transactionDateMillis) { showDatePicker = true }
                }
            }

            FieldLabel("Description *")
            Box {
                DevLedgerTextField(
                    value = state.description, onValueChange = viewModel::onDescriptionChange,
                    label = "", placeholder = "Enter a short description...", singleLine = false, minLines = 2,
                )
            }
            Text("${state.description.length}/200", color = DevLedgerColors.TextMuted, fontSize = 10.sp,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)

            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    FieldLabel("Client / From *")
                    DevLedgerTextField(value = state.clientOrFrom, onValueChange = viewModel::onClientOrFromChange, label = "", placeholder = "e.g. Acme Corp")
                }
                Column(Modifier.weight(1f)) {
                    FieldLabel("Related Project")
                    ProjectAutocomplete(state.relatedProject, state.projectSuggestions, viewModel::onRelatedProjectChange)
                }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    FieldLabel("Payment Method")
                    CategoryDropdown(PaymentMethods, state.paymentMethod, viewModel::onPaymentMethodChange)
                }
                Column(Modifier.weight(1f)) {
                    FieldLabel("Reference / Invoice #")
                    DevLedgerTextField(value = state.referenceNumber, onValueChange = viewModel::onReferenceNumberChange, label = "", placeholder = "e.g. INV-2025-045")
                }
            }

            RecurringSection(state, viewModel::onRecurringToggle, viewModel::onRecurringFrequencyChange, viewModel::onDateChange)

            Spacer(Modifier.height(16.dp))
            Text("Attachment (Optional)", color = DevLedgerColors.TextMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
            AttachmentRow(state.attachmentFileName, onUpload = { filePicker.launch("*/*") }, onRemove = viewModel::onAttachmentRemoved)
            if (state.attachmentTooLarge) {
                Text("File is larger than 2MB — pick a smaller file.", color = DevLedgerColors.ErrorRed, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            }

            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp)); Text("Cancel")
                }
                Button(
                    onClick = viewModel::save, enabled = !state.isSaving,
                    colors = ButtonDefaults.buttonColors(containerColor = DevLedgerColors.AccentBlue),
                    modifier = Modifier.weight(1f),
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp)); Text("Save Transaction")
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = state.transactionDateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let(viewModel::onDateChange)
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) { DatePicker(state = datePickerState) }
    }
}

@Composable private fun FieldLabel(text: String) {
    Text(text, color = DevLedgerColors.TextMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
private fun TypeToggleCard(label: String, sublabel: String, icon: ImageVector, color: Color, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(12.dp))
            .background(if (selected) color.copy(alpha = 0.15f) else DevLedgerColors.SurfaceCard)
            .border(1.dp, if (selected) color else DevLedgerColors.Border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, color = color, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(sublabel, color = DevLedgerColors.TextMuted, fontSize = 11.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDropdown(options: List<String>, selected: String, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected, onValueChange = {}, readOnly = true, placeholder = { Text("Select") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { onSelected(option); expanded = false }) }
        }
    }
}

@Composable
private fun DateField(millis: Long, onClick: () -> Unit) {
    val formatted = remember(millis) { SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(millis)) }
    OutlinedTextField(
        value = formatted, onValueChange = {}, readOnly = true,
        trailingIcon = { Icon(Icons.Filled.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp)) },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        enabled = false,
        colors = OutlinedTextFieldDefaults.colors(disabledTextColor = DevLedgerColors.TextPrimary, disabledBorderColor = DevLedgerColors.Border, disabledTrailingIconColor = DevLedgerColors.TextMuted),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectAutocomplete(value: String, suggestions: List<String>, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val filtered = remember(value, suggestions) { suggestions.filter { it.contains(value, ignoreCase = true) && it != value } }
    ExposedDropdownMenuBox(expanded = expanded && filtered.isNotEmpty(), onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value, onValueChange = { onChange(it); expanded = true }, placeholder = { Text("Select project") },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded && filtered.isNotEmpty(), onDismissRequest = { expanded = false }) {
            filtered.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { onChange(option); expanded = false }) }
        }
    }
}

@Composable
private fun RecurringSection(state: AddTransactionUiState, onToggle: (Boolean) -> Unit, onFrequencyChange: (String) -> Unit, onDateChange: (Long) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(DevLedgerColors.SurfaceCard).padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Autorenew, contentDescription = null, tint = DevLedgerColors.AccentBlue, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("Recurring Transaction", color = DevLedgerColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text("Set this transaction to repeat automatically", color = DevLedgerColors.TextMuted, fontSize = 11.sp)
            }
            Switch(checked = state.isRecurring, onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(checkedThumbColor = DevLedgerColors.AccentBlue, checkedTrackColor = DevLedgerColors.AccentBlueDark))
        }
        if (state.isRecurring) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    FieldLabel("Frequency")
                    CategoryDropdown(RecurringFrequencies.map { it.second }, RecurringFrequencies.first { it.first == state.recurringFrequency }.second) { label ->
                        onFrequencyChange(RecurringFrequencies.first { it.second == label }.first)
                    }
                }
                Column(Modifier.weight(1f)) {
                    FieldLabel("Next Occurrence")
                    val next = remember(state.transactionDateMillis, state.recurringFrequency) {
                        val cal = Calendar.getInstance().apply { timeInMillis = state.transactionDateMillis }
                        when (state.recurringFrequency) {
                            "WEEKLY" -> cal.add(Calendar.WEEK_OF_YEAR, 1)
                            "YEARLY" -> cal.add(Calendar.YEAR, 1)
                            else -> cal.add(Calendar.MONTH, 1)
                        }
                        SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(cal.time)
                    }
                    Text(next, color = DevLedgerColors.TextMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 14.dp))
                }
            }
        }
    }
}

@Composable
private fun AttachmentRow(fileName: String?, onUpload: () -> Unit, onRemove: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        if (fileName != null) {
            Icon(Icons.Filled.AttachFile, contentDescription = null, tint = DevLedgerColors.AccentBlue, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(fileName, color = DevLedgerColors.TextPrimary, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1)
            TextButton(onClick = onRemove) { Text("Remove", color = DevLedgerColors.ErrorRed, fontSize = 12.sp) }
        } else {
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = onUpload) {
                Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp)); Text("Upload File")
            }
        }
    }
}