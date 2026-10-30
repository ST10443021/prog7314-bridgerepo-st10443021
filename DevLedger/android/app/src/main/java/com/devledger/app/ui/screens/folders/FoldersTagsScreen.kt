package com.devledger.app.ui.screens.folders

import com.devledger.app.util.localization.rememberAppStrings

import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devledger.app.R
import com.devledger.app.data.local.entity.FolderEntity
import com.devledger.app.data.repository.FolderRepository
import com.devledger.app.data.repository.SnippetRepository
import com.devledger.app.ui.theme.DevLedgerColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Folder and Tag Management screen: organizational structure for the Snippet Vault.
 * Matches Image 4 pixel-accurate split design.
 */
@Composable
/**
 * Reference scope: UI and state-holder separation.
 * (Google Developers, n.d.a; Google Developers, n.d.f). See REFERENCES-CODE.md for full entries.
 */
fun FoldersTagsScreen(onBack: () -> Unit, viewModel: FoldersTagsViewModel = hiltViewModel()) {
    val strings = rememberAppStrings()
    val state by viewModel.uiState.collectAsState()
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var folderPendingDelete by remember { mutableStateOf<FolderEntity?>(null) }
    var folderPendingRename by remember { mutableStateOf<FolderEntity?>(null) }
    var showCreateTagDialog by remember { mutableStateOf(false) }
    var tagPendingRename by remember { mutableStateOf<String?>(null) }
    var tagPendingDelete by remember { mutableStateOf<String?>(null) }
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val query = searchQuery.trim()
    val visibleFolders = state.folders.filter { query.isEmpty() || it.name.contains(query, ignoreCase = true) }
    val visibleTags = state.tagCounts.filter { (name, _) -> query.isEmpty() || name.contains(query, ignoreCase = true) }

    Scaffold(
        topBar = {
            Column {
                FoldersTopBar(
                    onBack = onBack,
                    searchOpen = searchOpen,
                    onToggleSearch = {
                        searchOpen = !searchOpen
                        if (!searchOpen) searchQuery = ""
                    },
                )
                if (searchOpen) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        placeholder = { Text(strings.get(R.string.folders_search_hint), color = DevLedgerColors.TextMuted) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = DevLedgerColors.TextMuted) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = strings.get(R.string.folders_clear_search), tint = DevLedgerColors.TextMuted)
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                        ),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    )
                }
            }
        },
        containerColor = DevLedgerColors.BgDeep
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Row(modifier = Modifier.weight(1f)) {
                // FOLDERS Column
                Column(modifier = Modifier.weight(1f).fillMaxHeight().padding(end = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(strings.get(R.string.lang_folders), color = DevLedgerColors.TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(
                            strings.get(R.string.lang_new_folder_action),
                            color = DevLedgerColors.AccentBlue,
                            fontSize = 11.sp,
                            modifier = Modifier.clickable { showCreateFolderDialog = true }
                        )
                    }

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                        item {
                            FolderItem(strings.get(R.string.lang_all_snippets), state.totalSnippets, DevLedgerColors.AccentBlue, isExpandable = true)
                        }
                        items(visibleFolders, key = { it.folderId }) { folder ->
                            FolderItem(
                                name = folder.name,
                                count = state.folderCounts[folder.folderId] ?: 0,
                                color = DevLedgerColors.AccentPurple,
                                onEdit = { folderPendingRename = folder },
                                onDelete = { folderPendingDelete = folder }
                            )
                        }
                        item {
                            FolderItem(strings.get(R.string.lang_archived), state.archivedCount, DevLedgerColors.TextMuted, icon = Icons.Default.Inventory)
                        }
                    }
                }

                // Vertical Divider
                Box(modifier = Modifier.width(1.dp).fillMaxHeight().padding(vertical = 8.dp).background(DevLedgerColors.Border))

                // TAGS Column
                Column(modifier = Modifier.weight(1f).fillMaxHeight().padding(start = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(strings.get(R.string.lang_tags), color = DevLedgerColors.TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { showCreateTagDialog = true }) {
                            Text(
                                text = strings.get(R.string.lang_new_tag),
                                color = DevLedgerColors.AccentBlue,
                                fontSize = 11.sp
                            )
                        }
                    }

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                        item {
                            TagItem(strings.get(R.string.lang_all_tags), state.tagCounts.sumOf { it.second }, DevLedgerColors.AccentPurple)
                        }
                        items(visibleTags, key = { it.first }) { (tag, count) ->
                            TagItem(
                                name = tag,
                                count = count,
                                color = DevLedgerColors.AccentPurple,
                                onRename = { tagPendingRename = tag },
                                onDelete = { tagPendingDelete = tag },
                            )
                        }
                    }
                }
            }

            // Bottom Tips Row
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                TipCard(Icons.Default.Info, strings.get(R.string.lang_folder_tips), strings.get(R.string.lang_use_folders_to_group_related_snippets_drag_and_drop_to_reord), DevLedgerColors.AccentBlue, Modifier.weight(1f))
                TipCard(Icons.Default.Lightbulb, strings.get(R.string.lang_tag_tips), strings.get(R.string.lang_use_tags_to_quickly_filter_and_find_related_snippets), DevLedgerColors.AccentPurple, Modifier.weight(1f))
            }
        }
    }

    if (showCreateTagDialog) {
        var tagName by remember { mutableStateOf("") }
        val trimmedName = tagName.trim()

        val duplicateExists = state.tagCounts.any { (existingName, _) ->
            existingName.equals(trimmedName, ignoreCase = true)
        }

        val validationError = when {
            ',' in trimmedName -> strings.get(R.string.lang_use_one_tag_name_without_commas)
            duplicateExists -> strings.get(R.string.lang_this_tag_already_exists)
            else -> null
        }

        AlertDialog(
            onDismissRequest = { showCreateTagDialog = false },
            containerColor = DevLedgerColors.SurfaceCard,
            title = {
                Text(strings.get(R.string.lang_new_tag_title), color = Color.White)
            },
            text = {
                OutlinedTextField(
                    value = tagName,
                    onValueChange = { tagName = it },
                    label = { Text(strings.get(R.string.lang_tag_name)) },
                    singleLine = true,
                    isError = validationError != null,
                    supportingText = {
                        validationError?.let { Text(it) }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
            },
            confirmButton = {
                TextButton(
                    enabled = trimmedName.isNotBlank() &&
                            validationError == null,
                    onClick = {
                        viewModel.createTag(trimmedName)
                        showCreateTagDialog = false
                    }
                ) {
                    Text(strings.get(R.string.lang_save))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCreateTagDialog = false }
                ) {
                    Text(strings.get(R.string.lang_cancel))
                }
            }
        )
    }

    // Tag rename / delete dialogs (opened from the three-dot menu on each tag row)
    tagPendingRename?.let { oldName ->
        var newName by remember(oldName) { mutableStateOf(oldName) }
        val trimmed = newName.trim()
        val clash = !trimmed.equals(oldName, ignoreCase = true) &&
            state.tagCounts.any { (existing, _) -> existing.equals(trimmed, ignoreCase = true) }
        val renameError = when {
            ',' in trimmed -> strings.get(R.string.lang_use_one_tag_name_without_commas)
            clash -> strings.get(R.string.lang_this_tag_already_exists)
            else -> null
        }
        AlertDialog(
            onDismissRequest = { tagPendingRename = null },
            containerColor = DevLedgerColors.SurfaceCard,
            title = { Text(strings.get(R.string.tags_rename_title), color = Color.White) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(strings.get(R.string.lang_tag_name)) },
                    singleLine = true,
                    isError = renameError != null,
                    supportingText = { renameError?.let { Text(it) } },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = trimmed.isNotBlank() && renameError == null,
                    onClick = {
                        viewModel.renameTag(oldName, trimmed)
                        tagPendingRename = null
                    },
                ) { Text(strings.get(R.string.lang_save)) }
            },
            dismissButton = {
                TextButton(onClick = { tagPendingRename = null }) { Text(strings.get(R.string.lang_cancel)) }
            },
        )
    }
    tagPendingDelete?.let { name ->
        AlertDialog(
            onDismissRequest = { tagPendingDelete = null },
            containerColor = DevLedgerColors.SurfaceCard,
            title = { Text(strings.get(R.string.tags_delete_title, name), color = Color.White) },
            text = { Text(strings.get(R.string.tags_delete_body), color = DevLedgerColors.TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTag(name)
                    tagPendingDelete = null
                }) { Text(strings.get(R.string.lang_delete), color = DevLedgerColors.ErrorRed) }
            },
            dismissButton = {
                TextButton(onClick = { tagPendingDelete = null }) { Text(strings.get(R.string.lang_cancel)) }
            },
        )
    }

    // Dialogs
    if (showCreateFolderDialog) {
        FolderDialog(strings.get(R.string.lang_new_folder), "", { showCreateFolderDialog = false }) { name ->
            viewModel.createFolder(name); showCreateFolderDialog = false
        }
    }
    folderPendingRename?.let { folder ->
        FolderDialog(strings.get(R.string.lang_rename_folder), folder.name, { folderPendingRename = null }) { name ->
            viewModel.renameFolder(folder.folderId, name); folderPendingRename = null
        }
    }
    folderPendingDelete?.let { folder ->
        DeleteFolderDialog(
            folder = folder,
            otherFolders = state.folders.filter { it.folderId != folder.folderId },
            onDismiss = { folderPendingDelete = null },
            onConfirm = { reassignTo -> viewModel.deleteFolder(folder.folderId, reassignTo); folderPendingDelete = null },
        )
    }
}

@Composable
private fun FoldersTopBar(onBack: () -> Unit, searchOpen: Boolean, onToggleSearch: () -> Unit) {
    val strings = rememberAppStrings()
    Row(
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.get(R.string.lang_back), tint = Color.White)
        }
        Icon(
            imageVector = Icons.Default.Folder,
            contentDescription = null,
            tint = DevLedgerColors.AccentBlue,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(strings.get(R.string.lang_folders_tags), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(strings.get(R.string.lang_organize_your_snippets), color = DevLedgerColors.TextMuted, fontSize = 12.sp)
        }

        Spacer(Modifier.weight(1f))

        IconButton(onClick = onToggleSearch) {
            Icon(
                if (searchOpen) Icons.Default.Close else Icons.Default.Search,
                contentDescription = strings.get(R.string.lang_search),
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun FolderItem(
    name: String,
    count: Int,
    color: Color,
    icon: ImageVector = Icons.Default.Folder,
    isExpandable: Boolean = false,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    val strings = rememberAppStrings()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DevLedgerColors.SurfaceCard)
            .border(1.dp, DevLedgerColors.Border, RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(strings.plural(R.plurals.lang_snippet_count, count), color = DevLedgerColors.TextMuted, fontSize = 11.sp)
            }
            if (isExpandable) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = DevLedgerColors.TextMuted, modifier = Modifier.size(16.dp))
            } else if (onEdit != null || onDelete != null) {
                RowMenu(onRename = onEdit, onDelete = onDelete)
            }
        }
    }
}

@Composable
private fun TagItem(
    name: String,
    count: Int,
    color: Color,
    onRename: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    val strings = rememberAppStrings()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DevLedgerColors.SurfaceCard)
            .border(1.dp, DevLedgerColors.Border, RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.LocalOffer, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(strings.plural(R.plurals.lang_snippet_count, count), color = DevLedgerColors.TextMuted, fontSize = 11.sp)
            }
            if (onRename != null || onDelete != null) {
                RowMenu(onRename = onRename, onDelete = onDelete)
            }
        }
    }
}

/**
 * Three-dot overflow menu shared by folder and tag rows. Previously the icon was drawn but had no
 * click handler, so rename and delete could never be reached (audit issue 1).
 * Reference scope: Compose menus. (Google Developers, n.d.f). See REFERENCES-CODE.md.
 */
@Composable
private fun RowMenu(onRename: (() -> Unit)?, onDelete: (() -> Unit)?) {
    val strings = rememberAppStrings()
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = strings.get(R.string.folders_more_options),
                tint = DevLedgerColors.TextMuted,
                modifier = Modifier.size(18.dp),
            )
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.background(DevLedgerColors.SurfaceCard),
        ) {
            if (onRename != null) {
                DropdownMenuItem(
                    text = { Text(strings.get(R.string.folders_menu_rename), color = Color.White) },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = DevLedgerColors.TextMuted) },
                    onClick = { open = false; onRename() },
                )
            }
            if (onDelete != null) {
                DropdownMenuItem(
                    text = { Text(strings.get(R.string.lang_delete), color = DevLedgerColors.ErrorRed) },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = DevLedgerColors.ErrorRed) },
                    onClick = { open = false; onDelete() },
                )
            }
        }
    }
}

@Composable
private fun TipCard(icon: ImageVector, title: String, text: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(DevLedgerColors.SurfaceCard)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        Text(text, color = DevLedgerColors.TextMuted, fontSize = 11.sp, lineHeight = 16.sp)
    }
}

@Composable
private fun FolderDialog(title: String, initialName: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val strings = rememberAppStrings()
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DevLedgerColors.SurfaceCard,
        title = { Text(title, color = Color.White) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(strings.get(R.string.lang_folder_name_label)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
        },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onConfirm(name.trim()) }) { Text(strings.get(R.string.lang_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.get(R.string.lang_cancel)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeleteFolderDialog(
    folder: FolderEntity,
    otherFolders: List<FolderEntity>,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit
) {
    val strings = rememberAppStrings()
    var reassignTo by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DevLedgerColors.SurfaceCard,
        title = { Text(strings.get(R.string.lang_delete_named, folder.name), color = Color.White) },
        text = {
            Column {
                Text(
                    strings.get(R.string.lang_its_snippets_need_a_new_home_choose_where_they_should_move),
                    color = DevLedgerColors.TextSecondary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = otherFolders.find { it.folderId == reassignTo }?.name ?: strings.get(R.string.lang_no_folder),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(strings.get(R.string.lang_move_snippets_to)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.background(DevLedgerColors.SurfaceCard)
                    ) {
                        DropdownMenuItem(
                            text = { Text(strings.get(R.string.lang_no_folder), color = Color.White) },
                            onClick = {
                                reassignTo = null
                                expanded = false
                            }
                        )
                        otherFolders.forEach { f ->
                            DropdownMenuItem(
                                text = { Text(f.name, color = Color.White) },
                                onClick = {
                                    reassignTo = f.folderId
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(reassignTo) }) {
                Text(strings.get(R.string.lang_delete), color = DevLedgerColors.ErrorRed)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.get(R.string.lang_cancel))
            }
        }
    )
}
