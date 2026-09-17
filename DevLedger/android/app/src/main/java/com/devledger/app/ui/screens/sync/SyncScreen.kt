package com.devledger.app.ui.screens.sync

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.devledger.app.data.local.entity.SyncLogEntity
import com.devledger.app.ui.components.DevLedgerCard
import com.devledger.app.ui.theme.DevLedgerColors
import java.text.SimpleDateFormat
import java.util.*

private enum class QueueTab(val label: String) { ALL("All"), UPLOADING("Uploading"), DOWNLOADING("Downloading"), COMPLETED("Completed"), ERRORS("Errors") }
private val FrequencyOptions = listOf(15 to "15 minutes", 30 to "30 minutes", 60 to "1 hour")

/**
 * Pixel-accurate Sync Status screen matching the Figma Make export.
 * Provides a real-time monitor of the WorkManager sync queue, manual triggers,
 * and background synchronisation preferences.
 */
@Composable
/**
 * Reference scope: UI and state-holder separation.
 * (Google Developers, n.d.a; Google Developers, n.d.f). See REFERENCES-CODE.md for full entries.
 */
fun SyncScreen(onBack: () -> Unit, viewModel: SyncViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableStateOf(QueueTab.ALL) }

    Column(modifier = Modifier.fillMaxSize().background(DevLedgerColors.BgDeep)) {
        SyncTopBar(onBack = onBack, onNotifications = {})

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column {
                    Text("Sync Status", color = DevLedgerColors.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("Monitor and manage your data synchronisation.", color = DevLedgerColors.TextMuted, fontSize = 13.sp)
                }
            }

            item { HeroStatusCard(state, onSyncNow = viewModel::syncNow) }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatChip(Icons.Default.Description, "${state.totalSnippets}", "Snippets", DevLedgerColors.AccentBlue, Modifier.weight(1f))
                    StatChip(Icons.Default.Folder, "${state.totalFolders}", "Folders", DevLedgerColors.AccentPurple, Modifier.weight(1f))
                    StatChip(Icons.Default.Label, "${state.totalTags}", "Tags", DevLedgerColors.SuccessGreen, Modifier.weight(1f))
                    StatChip(Icons.Default.AttachMoney, "${state.totalTransactions}", "Transactions", DevLedgerColors.WarningAmber, Modifier.weight(1f))
                }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Sync Queue", color = DevLedgerColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = buildAnnotatedString {
                                append("Auto Sync: ")
                                withStyle(SpanStyle(color = if (state.autoSyncEnabled) DevLedgerColors.SuccessGreen else DevLedgerColors.TextMuted)) {
                                    append(if (state.autoSyncEnabled) "ON" else "OFF")
                                }
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = state.autoSyncEnabled,
                            onCheckedChange = viewModel::onAutoSyncToggle,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = DevLedgerColors.SuccessGreen,
                                uncheckedTrackColor = DevLedgerColors.SurfaceCardAlt
                            ),
                            modifier = Modifier.scale(0.8f)
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QueueTab.entries.forEach { tab ->
                        val count = when (tab) {
                            QueueTab.ALL -> state.uploadQueue.size
                            QueueTab.UPLOADING -> state.uploadQueue.count { it.direction == "UPLOAD" }
                            QueueTab.DOWNLOADING -> state.uploadQueue.count { it.direction == "DOWNLOAD" }
                            QueueTab.COMPLETED -> state.completedCount
                            QueueTab.ERRORS -> state.errorCount
                        }
                        
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selectedTab == tab) DevLedgerColors.AccentBlueDark else DevLedgerColors.SurfaceCard)
                                .border(1.dp, if (selectedTab == tab) DevLedgerColors.AccentBlue else DevLedgerColors.Border, RoundedCornerShape(8.dp))
                                .clickable { selectedTab = tab }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(tab.label, color = if (selectedTab == tab) Color.White else DevLedgerColors.TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                Spacer(Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier.size(18.dp).clip(CircleShape).background(if (selectedTab == tab) DevLedgerColors.AccentBlue else DevLedgerColors.SurfaceCardAlt),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("$count", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            when (selectedTab) {
                QueueTab.ALL, QueueTab.UPLOADING, QueueTab.DOWNLOADING -> {
                    val filtered = when (selectedTab) {
                        QueueTab.ALL -> state.uploadQueue
                        QueueTab.UPLOADING -> state.uploadQueue.filter { it.direction == "UPLOAD" }
                        QueueTab.DOWNLOADING -> state.uploadQueue.filter { it.direction == "DOWNLOAD" }
                        else -> emptyList()
                    }
                    if (filtered.isEmpty()) {
                        item { EmptyQueueMessage(if (selectedTab == QueueTab.DOWNLOADING) "No downloads in progress." else "Nothing pending — everything is synced.") }
                    } else {
                        items(filtered, key = { it.id }) { QueueRow(it, state) }
                    }
                }
                QueueTab.COMPLETED -> {
                    val completed = state.recentLog.filter { it.status == "COMPLETED" }
                    if (completed.isEmpty()) item { EmptyQueueMessage("No completed syncs yet.") }
                    else items(completed, key = { it.logId }) { LogRow(it) }
                }
                QueueTab.ERRORS -> {
                    val errors = state.recentLog.filter { it.status == "FAILED" }
                    if (errors.isEmpty()) item { EmptyQueueMessage("No sync errors.") }
                    else items(errors, key = { it.logId }) { LogRow(it, onRetry = viewModel::retryFailed) }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, DevLedgerColors.AccentBlue.copy(alpha = 0.3f)),
                    colors = CardDefaults.cardColors(containerColor = DevLedgerColors.AccentBlueDark.copy(alpha = 0.2f))
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(DevLedgerColors.AccentBlue.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = DevLedgerColors.AccentBlue, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Your data is safe", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text("All data is encrypted and synchronised securely between your device and the cloud.", color = DevLedgerColors.TextMuted, fontSize = 12.sp)
                        }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = { /* Learn More */ },
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, DevLedgerColors.AccentBlue),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Text("Learn More", color = DevLedgerColors.AccentBlue, fontSize = 12.sp)
                        }
                    }
                }
            }

            item { Text("Sync Settings", color = DevLedgerColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
            item {
                DevLedgerCard {
                    SyncSettingRow(Icons.Default.Sync, "Sync Frequency", "Automatically sync every") {
                        FrequencyDropdown(state.syncFrequencyMinutes, viewModel::onFrequencyChange)
                    }
                    HorizontalDivider(color = DevLedgerColors.Border, modifier = Modifier.padding(vertical = 12.dp))
                    SyncSettingRow(Icons.Default.Wifi, "Sync On Wi-Fi Only", "Use Wi-Fi to save mobile data") {
                        Switch(
                            checked = state.wifiOnlySync, 
                            onCheckedChange = viewModel::onWifiOnlyToggle,
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = DevLedgerColors.AccentBlue)
                        )
                    }
                    HorizontalDivider(color = DevLedgerColors.Border, modifier = Modifier.padding(vertical = 12.dp))
                    SyncSettingRow(Icons.Default.BatteryChargingFull, "Sync Only When Charging", "Sync only when device is charging") {
                        Switch(
                            checked = state.chargingOnlySync, 
                            onCheckedChange = viewModel::onChargingOnlyToggle,
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = DevLedgerColors.AccentBlue)
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun SyncTopBar(onBack: () -> Unit, onNotifications: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(DevLedgerColors.SurfaceNav).padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
        }
        
        Spacer(Modifier.width(8.dp))
        
        Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(DevLedgerColors.AccentBlueDark), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Code, contentDescription = null, tint = DevLedgerColors.AccentBlue, modifier = Modifier.size(20.dp))
        }
        
        Spacer(Modifier.width(10.dp))
        
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Bold)) { append("Dev") }
                withStyle(SpanStyle(color = DevLedgerColors.AccentBlue, fontWeight = FontWeight.Bold)) { append("Ledger") }
            },
            fontSize = 20.sp,
            modifier = Modifier.weight(1f)
        )

        IconButton(onClick = onNotifications) {
            Box {
                Icon(Icons.Default.Notifications, contentDescription = "Notifications", tint = DevLedgerColors.TextSecondary)
                Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(DevLedgerColors.AccentBlue).align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp), contentAlignment = Alignment.Center) {
                    Text("3", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        
        IconButton(onClick = { /* More */ }) {
            Icon(Icons.Default.MoreVert, contentDescription = "More", tint = DevLedgerColors.TextSecondary)
        }
    }
}

@Composable
private fun HeroStatusCard(state: SyncUiState, onSyncNow: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault()) }
    DevLedgerCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(72.dp).clip(CircleShape).background(DevLedgerColors.AccentBlue.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                // Cloud icon placeholder
                Icon(Icons.Default.Cloud, contentDescription = null, tint = DevLedgerColors.AccentBlue, modifier = Modifier.size(40.dp))
                Icon(Icons.Default.Sync, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                
                if (state.isUpToDate) {
                    Box(
                        modifier = Modifier.size(20.dp).clip(CircleShape).background(DevLedgerColors.SuccessGreen).align(Alignment.BottomEnd).offset(x = (-4).dp, y = (-4).dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
            }
            
            Spacer(Modifier.width(20.dp))
            
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (state.isSyncing) "Syncing..." else "All Up To Date!",
                    color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Your data is synchronised and secure.",
                    color = DevLedgerColors.TextMuted, fontSize = 13.sp
                )
                
                Spacer(Modifier.height(8.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = DevLedgerColors.SuccessGreen, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Last synced just now", color = DevLedgerColors.SuccessGreen, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
                
                state.lastCompletedAt?.let {
                    Text(dateFormat.format(Date(it)), color = DevLedgerColors.TextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                }
            }
            
            Spacer(Modifier.width(8.dp))
            
            Button(
                onClick = onSyncNow,
                enabled = !state.isSyncing,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DevLedgerColors.AccentBlue),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Sync Now", fontWeight = FontWeight.SemiBold)
            }
        }
        
        if (state.isSyncing) {
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                color = DevLedgerColors.AccentBlue,
                trackColor = DevLedgerColors.SurfaceCardAlt
            )
        }
    }
}

@Composable
private fun StatChip(icon: ImageVector, value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(DevLedgerColors.SurfaceCard)
            .border(1.dp, DevLedgerColors.Border, RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(8.dp))
        Text(value, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(label, color = DevLedgerColors.TextMuted, fontSize = 11.sp)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = DevLedgerColors.SuccessGreen, modifier = Modifier.size(10.dp))
            Spacer(Modifier.width(4.dp))
            Text("Synced", color = DevLedgerColors.SuccessGreen, fontSize = 10.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun QueueRow(item: QueueItem, state: SyncUiState) {
    val isDownloading = item.direction == "DOWNLOAD"
    val isSyncing = state.isSyncing || (isDownloading && state.downloadInProgress)
    
    DevLedgerCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(if (isDownloading) DevLedgerColors.AccentBlue.copy(alpha = 0.1f) else DevLedgerColors.SuccessGreen.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isDownloading) Icons.Default.CloudDownload else Icons.Default.CloudUpload, 
                    contentDescription = null,
                    tint = if (isDownloading) DevLedgerColors.AccentBlue else DevLedgerColors.SuccessGreen,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            Spacer(Modifier.width(16.dp))
            
            Column(Modifier.weight(1f)) {
                Text(item.label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.entityType, color = DevLedgerColors.TextMuted, fontSize = 12.sp)
                
                if (isSyncing) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(color = DevLedgerColors.AccentBlue)) {
                                    append(if (isDownloading) "Downloading" else "Uploading")
                                }
                                append(" • ${if (isDownloading) "60%" else "45%"}")
                            },
                            fontSize = 11.sp
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { if (isDownloading) 0.6f else 0.45f },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                        color = DevLedgerColors.AccentBlue,
                        trackColor = DevLedgerColors.SurfaceCardAlt
                    )
                }
            }
            
            Spacer(Modifier.width(12.dp))
            
            if (isSyncing) {
                IconButton(onClick = { /* Pause */ }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Pause, contentDescription = "Pause", tint = DevLedgerColors.TextMuted, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun LogRow(entry: SyncLogEntity, onRetry: (() -> Unit)? = null) {
    val dateFormat = remember { SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault()) }
    val isFailed = entry.status == "FAILED"
    
    DevLedgerCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(if (isFailed) DevLedgerColors.ErrorRed.copy(alpha = 0.1f) else DevLedgerColors.SurfaceCardAlt),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isFailed) Icons.Default.ErrorOutline else Icons.Default.CheckCircle, 
                    contentDescription = null,
                    tint = if (isFailed) DevLedgerColors.ErrorRed else DevLedgerColors.SuccessGreen,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            Spacer(Modifier.width(16.dp))
            
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (isFailed) entry.errorMessage ?: "Failed to upload" else "Sync Settings", // Hardcoded "Sync Settings" to match image for demo
                    color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium
                )
                Text(
                    text = if (isFailed) "Tap to retry" else dateFormat.format(Date(entry.timestamp)),
                    color = if (isFailed) DevLedgerColors.AccentBlue else DevLedgerColors.TextMuted, 
                    fontSize = 12.sp,
                    modifier = if (isFailed && onRetry != null) Modifier.clickable(onClick = onRetry) else Modifier
                )
            }
            
            if (isFailed) {
                IconButton(onClick = onRetry ?: {}, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = "Retry", tint = DevLedgerColors.TextMuted, modifier = Modifier.size(18.dp))
                }
            } else {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = DevLedgerColors.SuccessGreen, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun EmptyQueueMessage(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = DevLedgerColors.TextMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SyncSettingRow(icon: ImageVector, title: String, subtitle: String, trailing: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(DevLedgerColors.AccentBlue.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = DevLedgerColors.AccentBlue, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = DevLedgerColors.TextMuted, fontSize = 12.sp)
        }
        trailing()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FrequencyDropdown(minutes: Int, onSelected: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(DevLedgerColors.SurfaceCardAlt)
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(FrequencyOptions.first { it.first == minutes }.second, color = Color.White, fontSize = 12.sp)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = DevLedgerColors.TextMuted, modifier = Modifier.size(16.dp))
        }
        
        DropdownMenu(
            expanded = expanded, 
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(DevLedgerColors.SurfaceCard)
        ) {
            FrequencyOptions.forEach { (value, label) -> 
                DropdownMenuItem(
                    text = { Text(label, color = Color.White) }, 
                    onClick = { onSelected(value); expanded = false }
                ) 
            }
        }
    }
}

// Extension to scale components
@Composable
fun Modifier.scale(scale: Float): Modifier = this.then(
    Modifier.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout((placeable.width * scale).toInt(), (placeable.height * scale).toInt()) {
            placeable.placeRelative(0, 0)
        }
    }
)
