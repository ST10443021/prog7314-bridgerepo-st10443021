package com.devledger.app.ui.screens.sync

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.devledger.app.data.local.entity.SyncLogEntity
import com.devledger.app.ui.components.DevLedgerCard
import com.devledger.app.ui.components.DevLedgerTopBar
import com.devledger.app.ui.theme.DevLedgerColors
import java.text.SimpleDateFormat
import java.util.*

private enum class QueueTab(val label: String) { ALL("All"), UPLOADING("Uploading"), COMPLETED("Completed"), ERRORS("Errors") }
private val FrequencyOptions = listOf(15 to "15 minutes", 30 to "30 minutes", 60 to "1 hour")

/** Sync Status screen — Screen behaviour from the Part 1 design doc: manual sync,
 *  retry failed items, and preferences that drive [com.devledger.app.sync.SyncScheduler].
 *  Pending changes retry automatically when connectivity returns via WorkManager's
 *  built-in backoff (Google Developers, n.d.c). */
@Composable
fun SyncScreen(onBack: () -> Unit, viewModel: SyncViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableStateOf(QueueTab.ALL) }

    Column(modifier = Modifier.fillMaxSize().background(DevLedgerColors.BgDeep)) {
        DevLedgerTopBar(title = "Sync Status", onBack = onBack)

        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { Text("Monitor and manage your data synchronisation.", color = DevLedgerColors.TextMuted, fontSize = 12.sp) }

            item { HeroStatusCard(state, onSyncNow = viewModel::syncNow) }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatChip(Icons.Filled.Description, "${state.totalSnippets}", "Snippets", DevLedgerColors.AccentBlue, Modifier.weight(1f))
                    StatChip(Icons.Filled.Folder, "${state.totalFolders}", "Folders", DevLedgerColors.AccentPurple, Modifier.weight(1f))
                    StatChip(Icons.Filled.Label, "${state.totalTags}", "Tags", DevLedgerColors.SuccessGreen, Modifier.weight(1f))
                    StatChip(Icons.Filled.AttachMoney, "${state.totalTransactions}", "Transactions", DevLedgerColors.WarningAmber, Modifier.weight(1f))
                }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Sync Queue", color = DevLedgerColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Auto Sync: ${if (state.autoSyncEnabled) "ON" else "OFF"}", color = DevLedgerColors.TextMuted, fontSize = 11.sp)
                        Switch(checked = state.autoSyncEnabled, onCheckedChange = viewModel::onAutoSyncToggle,
                            colors = SwitchDefaults.colors(checkedThumbColor = DevLedgerColors.SuccessGreen))
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QueueTab.entries.forEach { tab ->
                        val count = when (tab) {
                            QueueTab.ALL -> state.uploadQueue.size
                            QueueTab.UPLOADING -> state.uploadQueue.size
                            QueueTab.COMPLETED -> state.completedCount
                            QueueTab.ERRORS -> state.errorCount
                        }
                        FilterChip(
                            selected = selectedTab == tab, onClick = { selectedTab = tab },
                            label = { Text("${tab.label} $count") },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = DevLedgerColors.AccentBlue),
                        )
                    }
                }
            }

            when (selectedTab) {
                QueueTab.ALL, QueueTab.UPLOADING -> {
                    if (state.uploadQueue.isEmpty()) {
                        item { EmptyQueueMessage("Nothing pending — everything is synced.") }
                    } else {
                        items(state.uploadQueue, key = { it.id }) { QueueRow(it, state.isSyncing) }
                    }
                }
                QueueTab.COMPLETED -> {
                    val completed = state.recentLog.filter { it.status == "COMPLETED" }
                    if (completed.isEmpty()) item { EmptyQueueMessage("No completed syncs yet.") }
                    else items(completed, key = { it.logId }) { LogRow(it) }
                }
                QueueTab.ERRORS -> {
                    val errors = state.recentLog.filter { it.status == "FAILED" }
                    if (errors.isEmpty()) item { EmptyQueueMessage("No sync errors — nice.") }
                    else items(errors, key = { it.logId }) { LogRow(it, onRetry = viewModel::retryFailed) }
                }
            }

            item {
                DevLedgerCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Shield, contentDescription = null, tint = DevLedgerColors.SuccessGreen, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Your data is safe", color = DevLedgerColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text("All data is encrypted and synchronised securely between your device and the cloud.", color = DevLedgerColors.TextMuted, fontSize = 11.sp)
                        }
                    }
                }
            }

            item { Text("Sync Settings", color = DevLedgerColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
            item {
                DevLedgerCard {
                    SyncSettingRow(Icons.Filled.Sync, "Sync Frequency", "Automatically sync every") {
                        FrequencyDropdown(state.syncFrequencyMinutes, viewModel::onFrequencyChange)
                    }
                    HorizontalDivider(color = DevLedgerColors.Border, modifier = Modifier.padding(vertical = 10.dp))
                    SyncSettingRow(Icons.Filled.Wifi, "Sync On Wi-Fi Only", "Use Wi-Fi to save mobile data") {
                        Switch(checked = state.wifiOnlySync, onCheckedChange = viewModel::onWifiOnlyToggle,
                            colors = SwitchDefaults.colors(checkedThumbColor = DevLedgerColors.AccentBlue))
                    }
                    HorizontalDivider(color = DevLedgerColors.Border, modifier = Modifier.padding(vertical = 10.dp))
                    SyncSettingRow(Icons.Filled.BatteryChargingFull, "Sync Only When Charging", "Sync only when device is charging") {
                        Switch(checked = state.chargingOnlySync, onCheckedChange = viewModel::onChargingOnlyToggle,
                            colors = SwitchDefaults.colors(checkedThumbColor = DevLedgerColors.AccentBlue))
                    }
                }
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }
}

@Composable
private fun HeroStatusCard(state: SyncUiState, onSyncNow: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault()) }
    DevLedgerCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(DevLedgerColors.AccentBlue.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                if (state.isSyncing) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = DevLedgerColors.AccentBlue, strokeWidth = 2.dp)
                else Icon(if (state.isUpToDate) Icons.Filled.CheckCircle else Icons.Filled.Warning, contentDescription = null,
                    tint = if (state.isUpToDate) DevLedgerColors.SuccessGreen else DevLedgerColors.WarningAmber, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when { state.isSyncing -> "Syncing..."; state.isUpToDate -> "All Up To Date!"; else -> "${state.uploadQueue.size} items pending" },
                    color = DevLedgerColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (state.isSyncing) "${state.processedItems}/${state.totalItems} • ${state.currentEntity ?: ""}"
                    else "Your data is synchronised and secure.",
                    color = DevLedgerColors.TextMuted, fontSize = 11.sp,
                )
            }
        }
        if (state.isSyncing) {
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth(), color = DevLedgerColors.AccentBlue)
        }
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            state.lastCompletedAt?.let {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clip(RoundedCornerShape(50)).background(DevLedgerColors.SuccessGreen.copy(alpha = 0.15f)).padding(horizontal = 10.dp, vertical = 5.dp)) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = DevLedgerColors.SuccessGreen, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Last synced ${dateFormat.format(Date(it))}", color = DevLedgerColors.SuccessGreen, fontSize = 10.sp)
                }
            } ?: Spacer(Modifier.width(1.dp))
            Button(onClick = onSyncNow, enabled = !state.isSyncing, colors = ButtonDefaults.buttonColors(containerColor = DevLedgerColors.AccentBlue)) {
                Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp)); Text("Sync Now")
            }
        }
        state.error?.let { Text(it, color = DevLedgerColors.ErrorRed, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp)) }
    }
}

@Composable
private fun StatChip(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(12.dp)).background(DevLedgerColors.SurfaceCard).border(1.dp, DevLedgerColors.Border, RoundedCornerShape(12.dp)).padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Text(value, color = DevLedgerColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(label, color = DevLedgerColors.TextMuted, fontSize = 10.sp)
        Text("✓ Synced", color = DevLedgerColors.SuccessGreen, fontSize = 9.sp)
    }
}

@Composable
private fun QueueRow(item: QueueItem, isSyncing: Boolean) {
    DevLedgerCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (item.direction == "UPLOAD") Icons.Filled.UploadFile else Icons.Filled.Download, contentDescription = null,
                tint = DevLedgerColors.AccentBlue, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(item.label, color = DevLedgerColors.TextPrimary, fontSize = 13.sp)
                Text(item.entityType, color = DevLedgerColors.TextMuted, fontSize = 11.sp)
            }
            Text(if (isSyncing) "Uploading" else "Pending", color = DevLedgerColors.AccentBlue, fontSize = 11.sp)
        }
    }
}

@Composable
private fun LogRow(entry: SyncLogEntity, onRetry: (() -> Unit)? = null) {
    val dateFormat = remember { SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault()) }
    val isFailed = entry.status == "FAILED"
    DevLedgerCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (isFailed) Icons.Filled.Error else Icons.Filled.CheckCircle, contentDescription = null,
                tint = if (isFailed) DevLedgerColors.ErrorRed else DevLedgerColors.SuccessGreen, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(if (isFailed) entry.errorMessage ?: "Sync failed" else "${entry.itemsSynced} items synced", color = DevLedgerColors.TextPrimary, fontSize = 13.sp)
                Text(dateFormat.format(Date(entry.timestamp)), color = DevLedgerColors.TextMuted, fontSize = 10.sp)
            }
            if (isFailed && onRetry != null) TextButton(onClick = onRetry) { Text("Retry", color = DevLedgerColors.AccentBlue, fontSize = 12.sp) }
        }
    }
}

@Composable
private fun EmptyQueueMessage(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
        Text(text, color = DevLedgerColors.TextMuted, fontSize = 12.sp)
    }
}

@Composable
private fun SyncSettingRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, trailing: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(icon, contentDescription = null, tint = DevLedgerColors.AccentBlue, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = DevLedgerColors.TextPrimary, fontSize = 13.sp)
            Text(subtitle, color = DevLedgerColors.TextMuted, fontSize = 11.sp)
        }
        trailing()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FrequencyDropdown(minutes: Int, onSelected: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(FrequencyOptions.first { it.first == minutes }.second, color = DevLedgerColors.AccentBlue, fontSize = 12.sp)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            FrequencyOptions.forEach { (value, label) -> DropdownMenuItem(text = { Text(label) }, onClick = { onSelected(value); expanded = false }) }
        }
    }
}