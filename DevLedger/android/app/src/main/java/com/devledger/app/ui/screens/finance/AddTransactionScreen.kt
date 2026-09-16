package com.devledger.app.ui.screens.finance

import android.provider.OpenableColumns
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.devledger.app.ui.components.DevLedgerTextField
import com.devledger.app.ui.theme.DevLedgerColors
import java.text.SimpleDateFormat
import java.util.*

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
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        } ?: uri.lastPathSegment ?: "attachment"
        viewModel.onAttachmentSelected(name, Base64.encodeToString(bytes, Base64.NO_WRAP), bytes.size.toLong())
    }

    Column(modifier = Modifier.fillMaxSize().background(DevLedgerColors.BgDeep)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text("Add Transaction", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = { /* History */ }) {
                Icon(Icons.Default.History, contentDescription = "History", tint = DevLedgerColors.AccentBlue)
            }
        }

        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            state.errorMessage?.let { Text(it, color = DevLedgerColors.ErrorRed, fontSize = 12.sp, modifier = Modifier.padding(bottom = 12.dp)) }

            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TypeToggleCard(
                    label = "Income", sublabel = "Money you receive", icon = Icons.Default.ArrowUpward,
                    color = DevLedgerColors.SuccessGreen, selected = state.type == "INCOME",
                    modifier = Modifier.weight(1f),
                ) { viewModel.onTypeChange("INCOME") }
                TypeToggleCard(
                    label = "Expense", sublabel = "Money you spend", icon = Icons.Default.ArrowDownward,
                    color = DevLedgerColors.ErrorRed, selected = state.type == "EXPENSE",
                    modifier = Modifier.weight(1f),
                ) { viewModel.onTypeChange("EXPENSE") }
            }

            FieldLabel("Amount *")
            DevLedgerTextField(
                value = state.amount, onValueChange = viewModel::onAmountChange,
                label = "", placeholder = "0.00",
                leadingIcon = Icons.Default.CurrencyExchange,
                modifier = Modifier.padding(bottom = 20.dp),
            )

            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f)) {
                    FieldLabel("Category *")
                    CategoryDropdown(
                        options = state.categories, 
                        selected = state.category, 
                        icon = Icons.Default.Work, 
                        onSelected = viewModel::onCategoryChange
                    )
                }
                Column(Modifier.weight(1f)) {
                    FieldLabel("Date *")
                    DateField(state.transactionDateMillis) { showDatePicker = true }
                }
            }

            FieldLabel("Description *")
            Box(modifier = Modifier.padding(bottom = 20.dp)) {
                Column {
                    DevLedgerTextField(
                        value = state.description, onValueChange = viewModel::onDescriptionChange,
                        label = "", placeholder = "Enter a short description...", singleLine = false, minLines = 3,
                    )
                    Text("${state.description.length}/200", color = DevLedgerColors.TextMuted, fontSize = 11.sp,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, end = 4.dp), textAlign = TextAlign.End)
                }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f)) {
                    FieldLabel("Client / From *")
                    DevLedgerTextField(
                        value = state.clientOrFrom, onValueChange = viewModel::onClientOrFromChange, 
                        label = "", placeholder = "e.g. Acme Corp", leadingIcon = Icons.Default.Person
                    )
                }
                Column(Modifier.weight(1f)) {
                    FieldLabel("Related Project")
                    ProjectAutocomplete(state.relatedProject, state.projectSuggestions, viewModel::onRelatedProjectChange)
                }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f)) {
                    FieldLabel("Payment Method")
                    CategoryDropdown(
                        options = PaymentMethods, 
                        selected = state.paymentMethod, 
                        icon = Icons.Default.CreditCard, 
                        onSelected = viewModel::onPaymentMethodChange
                    )
                }
                Column(Modifier.weight(1f)) {
                    FieldLabel("Reference / Invoice #")
                    DevLedgerTextField(
                        value = state.referenceNumber, onValueChange = viewModel::onReferenceNumberChange, 
                        label = "", placeholder = "e.g. INV-2025-045", leadingIcon = Icons.Default.Numbers
                    )
                }
            }

            RecurringSection(state, viewModel::onRecurringToggle, viewModel::onRecurringFrequencyChange, viewModel::onDateChange)

            Spacer(Modifier.height(20.dp))
            
            AttachmentSection(state.attachmentFileName, onUpload = { filePicker.launch("*/*") }, onRemove = viewModel::onAttachmentRemoved)
            if (state.attachmentTooLarge) {
                Text("File is larger than 2MB — pick a smaller file.", color = DevLedgerColors.ErrorRed, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            }

            Spacer(Modifier.height(32.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onBack, 
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, DevLedgerColors.Border)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Cancel", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = viewModel::save, enabled = !state.isSaving,
                    colors = ButtonDefaults.buttonColors(containerColor = DevLedgerColors.AccentBlue),
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Save Transaction", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.height(100.dp))
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
    Text(text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun TypeToggleCard(label: String, sublabel: String, icon: ImageVector, color: Color, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) color.copy(alpha = 0.1f) else DevLedgerColors.SurfaceCard)
            .border(if (selected) 2.dp else 1.dp, if (selected) color else DevLedgerColors.Border, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(if (selected) color.copy(alpha = 0.2f) else DevLedgerColors.SurfaceCardAlt),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = if (selected) color else DevLedgerColors.TextMuted, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(label, color = if (selected) color else Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(sublabel, color = DevLedgerColors.TextMuted, fontSize = 11.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDropdown(options: List<String>, selected: String, icon: ImageVector, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected, onValueChange = {}, readOnly = true, placeholder = { Text("Select", color = DevLedgerColors.TextMuted) },
            leadingIcon = { Icon(icon, contentDescription = null, tint = DevLedgerColors.TextMuted, modifier = Modifier.size(20.dp)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = DevLedgerColors.AccentBlue,
                unfocusedBorderColor = DevLedgerColors.Border,
                unfocusedTextColor = Color.White,
                focusedTextColor = Color.White,
                unfocusedContainerColor = DevLedgerColors.BgDeep,
                focusedContainerColor = DevLedgerColors.BgDeep
            )
        )
        ExposedDropdownMenu(
            expanded = expanded, 
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(DevLedgerColors.SurfaceCard)
        ) {
            options.forEach { option -> 
                DropdownMenuItem(
                    text = { Text(option, color = Color.White) }, 
                    onClick = { onSelected(option); expanded = false },
                    modifier = Modifier.background(DevLedgerColors.SurfaceCard)
                ) 
            }
        }
    }
}

@Composable
private fun DateField(millis: Long, onClick: () -> Unit) {
    val formatted = remember(millis) { SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(millis)) }
    OutlinedTextField(
        value = formatted, onValueChange = {}, readOnly = true,
        leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null, tint = DevLedgerColors.TextMuted, modifier = Modifier.size(20.dp)) },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        enabled = false,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            disabledTextColor = Color.White,
            disabledBorderColor = DevLedgerColors.Border,
            disabledContainerColor = DevLedgerColors.BgDeep,
            disabledLeadingIconColor = DevLedgerColors.TextMuted
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectAutocomplete(value: String, suggestions: List<String>, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val filtered = remember(value, suggestions) { suggestions.filter { it.contains(value, ignoreCase = true) && it != value } }
    ExposedDropdownMenuBox(expanded = expanded && filtered.isNotEmpty(), onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value, onValueChange = { onChange(it); expanded = true }, placeholder = { Text("Select project", color = DevLedgerColors.TextMuted) },
            leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null, tint = DevLedgerColors.TextMuted, modifier = Modifier.size(20.dp)) },
            trailingIcon = { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = DevLedgerColors.TextMuted) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = DevLedgerColors.AccentBlue,
                unfocusedBorderColor = DevLedgerColors.Border,
                unfocusedTextColor = Color.White,
                focusedTextColor = Color.White,
                unfocusedContainerColor = DevLedgerColors.BgDeep,
                focusedContainerColor = DevLedgerColors.BgDeep
            )
        )
        ExposedDropdownMenu(
            expanded = expanded && filtered.isNotEmpty(), 
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(DevLedgerColors.SurfaceCard)
        ) {
            filtered.forEach { option -> 
                DropdownMenuItem(
                    text = { Text(option, color = Color.White) }, 
                    onClick = { onChange(option); expanded = false },
                    modifier = Modifier.background(DevLedgerColors.SurfaceCard)
                ) 
            }
        }
    }
}

@Composable
private fun RecurringSection(state: AddTransactionUiState, onToggle: (Boolean) -> Unit, onFrequencyChange: (String) -> Unit, onDateChange: (Long) -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(DevLedgerColors.SurfaceCard).border(1.dp, DevLedgerColors.Border, RoundedCornerShape(12.dp)).padding(16.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Autorenew, contentDescription = null, tint = DevLedgerColors.AccentBlue, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Recurring Transaction", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("Set this transaction to repeat automatically", color = DevLedgerColors.TextMuted, fontSize = 12.sp)
                }
                Switch(checked = state.isRecurring, onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = DevLedgerColors.AccentBlue, uncheckedTrackColor = DevLedgerColors.SurfaceCardAlt))
            }
            if (state.isRecurring) {
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("Frequency", color = DevLedgerColors.TextMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                        CategoryDropdown(RecurringFrequencies.map { it.second }, RecurringFrequencies.first { it.first == state.recurringFrequency }.second, Icons.Default.Repeat) { label ->
                            onFrequencyChange(RecurringFrequencies.first { it.second == label }.first)
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Next Occurrence", color = DevLedgerColors.TextMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                        val next = remember(state.transactionDateMillis, state.recurringFrequency) {
                            val cal = Calendar.getInstance().apply { timeInMillis = state.transactionDateMillis }
                            when (state.recurringFrequency) {
                                "WEEKLY" -> cal.add(Calendar.WEEK_OF_YEAR, 1)
                                "YEARLY" -> cal.add(Calendar.YEAR, 1)
                                else -> cal.add(Calendar.MONTH, 1)
                            }
                            SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(cal.time)
                        }
                        Box(
                            modifier = Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(12.dp)).background(DevLedgerColors.BgDeep).border(1.dp, DevLedgerColors.Border, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CalendarToday, contentDescription = null, tint = DevLedgerColors.TextMuted, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(next, color = Color.White, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentSection(fileName: String?, onUpload: () -> Unit, onRemove: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(DevLedgerColors.SurfaceCard).border(1.dp, DevLedgerColors.Border, RoundedCornerShape(12.dp)).padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.AttachFile, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Attachment (Optional)", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(fileName ?: "Attach receipt or invoice", color = DevLedgerColors.TextMuted, fontSize = 12.sp, maxLines = 1)
            }
            if (fileName != null) {
                TextButton(onClick = onRemove) { Text("Remove", color = DevLedgerColors.ErrorRed, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
            } else {
                Button(
                    onClick = onUpload,
                    colors = ButtonDefaults.buttonColors(containerColor = DevLedgerColors.SurfaceCardAlt),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = null, tint = DevLedgerColors.AccentBlue, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Upload File", color = Color.White, fontSize = 13.sp)
                }
            }
        }
    }
}
