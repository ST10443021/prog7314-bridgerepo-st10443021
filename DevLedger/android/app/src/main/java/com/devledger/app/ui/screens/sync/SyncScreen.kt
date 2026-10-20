package com.devledger.app.ui.screens.sync

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.devledger.app.R
import com.devledger.app.ui.components.DevLedgerCard
import com.devledger.app.ui.theme.DevLedgerColors
import java.text.DateFormat
import java.util.Date

@Composable
fun SyncScreen(onBack: () -> Unit, viewModel: SyncViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    var confirmId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.accountId) { confirmId = null }
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    val title = when {
        state.isUpToDate -> R.string.sync_state_verified
        state.accountId == null -> R.string.sync_state_signed_out
        state.conflicts.isNotEmpty() && !state.isSyncing -> R.string.sync_state_conflict
        state.runState == SyncRunState.RUNNING -> R.string.sync_state_running
        state.runState == SyncRunState.QUEUED -> R.string.sync_state_queued
        state.runState == SyncRunState.WAITING -> R.string.sync_state_waiting
        state.runState == SyncRunState.RETRYING -> R.string.sync_state_retrying
        state.runState == SyncRunState.PARTIAL -> R.string.sync_state_partial
        state.runState == SyncRunState.FAILED -> R.string.sync_state_failed
        state.runState == SyncRunState.CANCELLED -> R.string.sync_state_cancelled
        state.uploadQueue.isNotEmpty() -> R.string.sync_state_pending
        else -> R.string.sync_state_unverified
    }
    Column(Modifier.fillMaxSize().background(DevLedgerColors.BgDeep)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.sync_back), tint = Color.White)
            }
            Text(stringResource(R.string.sync_screen_title), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                DevLedgerCard {
                    Text(stringResource(title), color = if (state.isUpToDate) DevLedgerColors.SuccessGreen else Color.White,
                        fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.sync_verification_scope), color = DevLedgerColors.TextMuted)
                    Spacer(Modifier.height(8.dp))
                    Text(state.lastCompletedAt?.let { stringResource(R.string.sync_last_verified, dateFormat.format(Date(it))) }
                        ?: stringResource(R.string.sync_never_verified), color = DevLedgerColors.TextMuted)
                    if (state.runState == SyncRunState.RUNNING) {
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(if (state.downloadInProgress) R.string.sync_downloading else R.string.sync_uploading), color = Color.White)
                        if (state.totalItems > 0) {
                            Text(stringResource(R.string.sync_steps, state.processedItems, state.totalItems), color = DevLedgerColors.TextMuted)
                            LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                        } else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = viewModel::syncNow, enabled = state.accountId != null && !state.isSyncing && state.resolvingId == null) {
                        Text(stringResource(R.string.sync_now_action))
                    }
                    state.error?.let { Text(it, color = DevLedgerColors.WarningAmber) }
                    state.actionError?.let { Text(it, color = DevLedgerColors.ErrorRed) }
                }
            }
            item {
                Text(stringResource(R.string.sync_synced_totals, state.totalSnippets, state.totalFolders,
                    state.totalTags, state.totalTransactions), color = DevLedgerColors.TextMuted)
            }
            if (state.conflicts.isNotEmpty()) {
                item { Text(stringResource(R.string.sync_conflicts_title, state.conflicts.size), color = DevLedgerColors.WarningAmber, fontSize = 18.sp) }
                items(state.conflicts, key = { "conflict:${it.snippetId}" }) { draft ->
                    DevLedgerCard {
                        Text(draft.title, color = Color.White, fontWeight = FontWeight.Bold)
                        Text(draft.content, color = DevLedgerColors.TextMuted, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Text(stringResource(R.string.sync_conflict_help), color = DevLedgerColors.TextMuted)
                        Button(onClick = { confirmId = draft.snippetId },
                            enabled = state.resolvingId == null && state.runState != SyncRunState.RUNNING) {
                            Text(stringResource(if (state.resolvingId == draft.snippetId) R.string.sync_recovering else R.string.sync_keep_both))
                        }
                    }
                }
            }
            item { Text(stringResource(R.string.sync_queue_title, state.uploadQueue.size), color = Color.White, fontSize = 18.sp) }
            if (state.uploadQueue.isEmpty()) {
                item { Text(stringResource(R.string.sync_empty_queue), color = DevLedgerColors.TextMuted) }
            } else items(state.uploadQueue, key = { it.id }) { queued ->
                DevLedgerCard {
                    val entity = stringResource(when (queued.entityType) {
                        "snippet" -> R.string.sync_entity_snippet
                        "folder" -> R.string.sync_entity_folder
                        "tag" -> R.string.sync_entity_tag
                        else -> R.string.sync_entity_transaction
                    })
                    Text("$entity: ${queued.label}", color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(stringResource(if (queued.direction == "DELETE") R.string.sync_delete_pending else R.string.sync_upload_pending),
                        color = DevLedgerColors.TextMuted)
                }
            }
            item { Text(stringResource(R.string.sync_settings_title), color = Color.White, fontSize = 18.sp) }
            item {
                DevLedgerCard {
                    SyncToggle(stringResource(R.string.sync_auto), state.autoSyncEnabled, viewModel::onAutoSyncToggle)
                    SyncToggle(stringResource(R.string.sync_wifi), state.wifiOnlySync, viewModel::onWifiOnlyToggle)
                    SyncToggle(stringResource(R.string.sync_charging), state.chargingOnlySync, viewModel::onChargingOnlyToggle)
                    Text(stringResource(R.string.sync_frequency, state.syncFrequencyMinutes), color = DevLedgerColors.TextMuted)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(15, 30, 60).forEach { minutes ->
                            TextButton(onClick = { viewModel.onFrequencyChange(minutes) }) { Text(stringResource(R.string.sync_minutes, minutes)) }
                        }
                    }
                }
            }
            item { Text(stringResource(R.string.sync_history_title), color = Color.White, fontSize = 18.sp) }
            if (state.recentLog.isEmpty()) item { Text(stringResource(R.string.sync_no_history), color = DevLedgerColors.TextMuted) }
            items(state.recentLog, key = { it.logId }) { entry ->
                DevLedgerCard {
                    Text(stringResource(when (entry.status) {
                        "COMPLETED" -> R.string.sync_log_completed
                        "RETRY" -> R.string.sync_state_retrying
                        "PARTIAL" -> R.string.sync_state_partial
                        "CONFLICT" -> R.string.sync_state_conflict
                        "CANCELLED" -> R.string.sync_state_cancelled
                        else -> R.string.sync_state_failed
                    }), color = if (entry.status == "COMPLETED") DevLedgerColors.SuccessGreen else DevLedgerColors.WarningAmber)
                    Text(dateFormat.format(Date(entry.timestamp)), color = DevLedgerColors.TextMuted)
                    Text(stringResource(R.string.sync_uploaded_count, entry.itemsSynced), color = DevLedgerColors.TextMuted)
                    entry.errorMessage?.let { Text(it, color = DevLedgerColors.TextMuted) }
                }
            }
            item { Spacer(Modifier.height(48.dp)) }
        }
    }
    if (confirmId != null) AlertDialog(
        onDismissRequest = { confirmId = null },
        title = { Text(stringResource(R.string.sync_keep_both)) },
        text = { Text(stringResource(R.string.sync_recovery_confirmation)) },
        confirmButton = { TextButton(onClick = {
            confirmId?.let(viewModel::keepBothVersions); confirmId = null
        }) { Text(stringResource(R.string.sync_keep_both)) } },
        dismissButton = { TextButton(onClick = { confirmId = null }) { Text(stringResource(R.string.sync_cancel)) } },
    )
}

@Composable
private fun SyncToggle(label: String, checked: Boolean, onChanged: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChanged)
    }
}
