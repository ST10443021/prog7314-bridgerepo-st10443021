package com.devledger.app.ui.screens.recycle

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import com.devledger.app.data.local.entity.SnippetEntity
import com.devledger.app.ui.theme.DevLedgerColors
import com.devledger.app.ui.theme.DevLedgerTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RecycleScreen(onBack: () -> Unit, viewModel: RecycleViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    RecycleScreenContent(
        state = state,
        onBack = onBack,
        onQueryChange = viewModel::setQuery,
        onLanguageChange = viewModel::setLanguage,
        onFolderChange = viewModel::setFolder,
        onToggleSort = viewModel::toggleSort,
        onClearFilters = viewModel::clearFilters,
        onRestore = viewModel::restore,
        onDelete = viewModel::deleteArchived,
        onRefresh = viewModel::refresh,
    )
}

/** Real archived data with confirmed, offline-queued permanent deletion. */
@Composable
fun RecycleScreenContent(
    state: RecycleUiState,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onLanguageChange: (String?) -> Unit,
    onFolderChange: (String?) -> Unit,
    onToggleSort: () -> Unit,
    onClearFilters: () -> Unit,
    onRestore: (String) -> Unit,
    onDelete: (List<String>) -> Unit,
    onRefresh: () -> Unit,
) {
    var languageMenu by remember { mutableStateOf(false) }
    var folderMenu by remember { mutableStateOf(false) }
    var deleteIds by remember { mutableStateOf<List<String>?>(null) }
    var deleteTitle by remember { mutableStateOf("") }
    val busy = state.restoringId != null || state.deleting || state.refreshing || state.isLoading
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(DevLedgerColors.BgDeep)
            .statusBarsPadding().imePadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = DevLedgerColors.TextSecondary)
                }
                Icon(Icons.Default.DeleteOutline, null, tint = DevLedgerColors.ErrorRed, modifier = Modifier.size(30.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Archived Snippets", color = DevLedgerColors.TextPrimary,
                        fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    Text("Restore or permanently delete snippets.", color = DevLedgerColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            ArchivePanel {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Archived items are hidden from your active vault.", color = DevLedgerColors.TextPrimary)
                        Spacer(Modifier.height(6.dp))
                        Text("Restore them, or delete them permanently.", color = DevLedgerColors.TextSecondary,
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.totalCount.toString(), color = DevLedgerColors.ErrorRed, fontSize = 28.sp)
                        Text("Items", color = DevLedgerColors.TextSecondary)
                    }
                }
            }
        }
        item {
            OutlinedTextField(
                value = state.filters.query, onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text("Search archived snippets") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = DevLedgerColors.TextPrimary,
                    unfocusedTextColor = DevLedgerColors.TextPrimary,
                    focusedBorderColor = DevLedgerColors.AccentBlue,
                    unfocusedBorderColor = DevLedgerColors.Border,
                ),
            )
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    OutlinedButton(onClick = { languageMenu = true }) {
                        Text(state.filters.language ?: "All languages")
                    }
                    DropdownMenu(expanded = languageMenu, onDismissRequest = { languageMenu = false }) {
                        DropdownMenuItem(text = { Text("All languages") }, onClick = {
                            onLanguageChange(null); languageMenu = false
                        })
                        state.languages.forEach { language ->
                            DropdownMenuItem(text = { Text(language) }, onClick = {
                                onLanguageChange(language); languageMenu = false
                            })
                        }
                    }
                }
                Box {
                    OutlinedButton(onClick = { folderMenu = true }) {
                        Text(state.filters.folderId?.let { state.folders[it] ?: "Unknown folder" } ?: "All folders")
                    }
                    DropdownMenu(expanded = folderMenu, onDismissRequest = { folderMenu = false }) {
                        DropdownMenuItem(text = { Text("All folders") }, onClick = {
                            onFolderChange(null); folderMenu = false
                        })
                        state.folders.entries.sortedBy { it.value.lowercase() }.forEach { folder ->
                            DropdownMenuItem(text = { Text(folder.value) }, onClick = {
                                onFolderChange(folder.key); folderMenu = false
                            })
                        }
                    }
                }
                OutlinedButton(onClick = onToggleSort) {
                    Text(if (state.filters.newestFirst) "Updated: newest" else "Updated: oldest")
                }
                TextButton(onClick = onClearFilters) { Text("Reset") }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onRefresh, enabled = !busy) { Text("Refresh") }
                OutlinedButton(
                    enabled = !busy && state.archivedIds.isNotEmpty(),
                    onClick = { deleteIds = state.archivedIds.toList(); deleteTitle = "Empty recycle bin?" },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DevLedgerColors.ErrorRed),
                ) { Text("Empty Recycle Bin") }
            }
            Text("Empty Bin includes all saved archived items, even those hidden by filters. Refresh first to load cloud items.",
                color = DevLedgerColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
        state.error?.let { message -> item { Text(message, color = DevLedgerColors.ErrorRed) } }
        state.message?.let { message -> item { Text(message, color = DevLedgerColors.SuccessGreen) } }
        if (state.isLoading || state.refreshing || state.deleting) {
            item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
        } else if (state.snippets.isEmpty() && state.error == null) {
            item {
                ArchivePanel {
                    Text(
                        if (state.totalCount == 0) "Your archive is empty. Archived snippets will appear here."
                        else "No snippets match these filters.",
                        modifier = Modifier.padding(24.dp), color = DevLedgerColors.TextSecondary,
                    )
                }
            }
        }
        items(state.snippets, key = { it.snippetId }) { snippet ->
            ArchivedSnippetCard(
                snippet = snippet,
                folder = if (snippet.folderId == null) "No folder" else state.folders[snippet.folderId] ?: "Folder unavailable",
                isRestoring = state.restoringId == snippet.snippetId,
                enabled = !busy,
                onRestore = { onRestore(snippet.snippetId) },
                onDelete = { deleteIds = listOf(snippet.snippetId); deleteTitle = "Delete ${snippet.title}?" },
            )
        }
    }
    deleteIds?.let { ids ->
        AlertDialog(
            onDismissRequest = { deleteIds = null },
            title = { Text(deleteTitle) },
            text = { Text("Permanently delete ${ids.size} archived snippet(s)? This cannot be undone. Cloud deletion will sync when connected.") },
            confirmButton = {
                TextButton(enabled = !busy, onClick = { deleteIds = null; onDelete(ids) }) {
                    Text("Delete permanently", color = DevLedgerColors.ErrorRed)
                }
            },
            dismissButton = { TextButton(onClick = { deleteIds = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ArchivePanel(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, DevLedgerColors.Border),
        colors = CardDefaults.cardColors(containerColor = DevLedgerColors.SurfaceCard),
        content = content,
    )
}

@Composable
private fun ArchivedSnippetCard(
    snippet: SnippetEntity, folder: String, isRestoring: Boolean, enabled: Boolean,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    val updated = remember(snippet.updatedAt, Locale.getDefault()) {
        SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(snippet.updatedAt))
    }
    ArchivePanel {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(48.dp).background(DevLedgerColors.AccentPurple.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.Code, null, tint = DevLedgerColors.AccentPurple) }
                Spacer(Modifier.width(12.dp))
                Text(snippet.title, color = DevLedgerColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold, fontSize = 18.sp, modifier = Modifier.weight(1f))
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Tag(snippet.language, DevLedgerColors.AccentPurple)
                snippet.tagsCsv.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct().forEach { tag ->
                    Tag(tag, DevLedgerColors.SuccessGreen)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Folder, null, tint = DevLedgerColors.TextSecondary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(folder, color = DevLedgerColors.TextSecondary)
            }
            // The supplied entity has no archivedAt field: do not mislabel updatedAt as an archive date.
            Text("Last updated $updated", color = DevLedgerColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            Row(Modifier.align(Alignment.End)) {
                TextButton(onClick = onRestore, enabled = enabled) {
                    if (isRestoring) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.Restore, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (isRestoring) "Restoring…" else "Restore")
                }
                TextButton(onClick = onDelete, enabled = enabled,
                    colors = ButtonDefaults.textButtonColors(contentColor = DevLedgerColors.ErrorRed)) {
                    Icon(Icons.Default.DeleteOutline, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Delete")
                }
            }
        }
    }
}

@Composable
private fun Tag(label: String, color: Color) {
    Text(label, color = color, style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.background(color.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp))
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun RecyclePreview() {
    val sample = SnippetEntity(
        snippetId = "preview", userId = "preview", folderId = "network",
        title = "Retrofit API Service", content = "", language = "Kotlin", description = null,
        tagsCsv = "Networking,API", isArchived = true, createdAt = 0L, updatedAt = 1_789_603_200_000L,
    )
    DevLedgerTheme {
        RecycleScreenContent(
            state = RecycleUiState(snippets = listOf(sample), totalCount = 1,
                languages = listOf("Kotlin"), folders = mapOf("network" to "Networking"), isLoading = false),
            onBack = {}, onQueryChange = {}, onLanguageChange = {}, onFolderChange = {},
            onToggleSort = {}, onClearFilters = {}, onRestore = {}, onDelete = {}, onRefresh = {},
        )
    }
}
