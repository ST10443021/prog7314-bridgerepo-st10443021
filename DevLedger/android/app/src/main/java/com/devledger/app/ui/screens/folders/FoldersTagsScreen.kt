package com.devledger.app.ui.screens.folders

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.devledger.app.data.local.entity.FolderEntity
import com.devledger.app.ui.theme.DevLedgerColors

/**
 * Folder and Tag Management screen.
 *
 * Provides:
 * - Folder creation, rename and deletion.
 * - Tag creation.
 * - Live folder and tag search.
 * - Snippet counts per folder and tag.
 */
@Composable
fun FoldersTagsScreen(
    onBack: () -> Unit,
    viewModel: FoldersTagsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showCreateTagDialog by remember { mutableStateOf(false) }

    var folderPendingDelete by remember {
        mutableStateOf<FolderEntity?>(null)
    }

    var folderPendingRename by remember {
        mutableStateOf<FolderEntity?>(null)
    }

    var searchActive by remember {
        mutableStateOf(false)
    }

    var searchQuery by remember {
        mutableStateOf("")
    }

    val trimmedSearchQuery = searchQuery.trim()

    val filteredFolders = remember(
        state.folders,
        trimmedSearchQuery,
    ) {
        if (trimmedSearchQuery.isBlank()) {
            state.folders
        } else {
            state.folders.filter { folder ->
                folder.name.contains(
                    trimmedSearchQuery,
                    ignoreCase = true,
                )
            }
        }
    }

    val filteredTags = remember(
        state.tagCounts,
        trimmedSearchQuery,
    ) {
        if (trimmedSearchQuery.isBlank()) {
            state.tagCounts
        } else {
            state.tagCounts.filter { (tagName, _) ->
                tagName.contains(
                    trimmedSearchQuery,
                    ignoreCase = true,
                )
            }
        }
    }

    Scaffold(
        topBar = {
            FoldersTopBar(
                onBack = onBack,
                searchActive = searchActive,
                searchQuery = searchQuery,
                onSearchQueryChange = {
                    searchQuery = it
                },
                onSearchClick = {
                    searchActive = true
                },
                onCloseSearch = {
                    searchActive = false
                    searchQuery = ""
                },
            )
        },
        containerColor = DevLedgerColors.BgDeep,
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {

            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {

                // ---------------------------------------------------------
                // FOLDERS
                // ---------------------------------------------------------

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(end = 8.dp),
                ) {

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {

                        Text(
                            text = "FOLDERS",
                            color = DevLedgerColors.TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )

                        Text(
                            text = "+ New Folder",
                            color = DevLedgerColors.AccentBlue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable {
                                showCreateFolderDialog = true
                            },
                        )
                    }

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {

                        if (trimmedSearchQuery.isBlank()) {
                            item {
                                FolderItem(
                                    name = "All Snippets",
                                    count = state.folderCounts.values.sum(),
                                    color = DevLedgerColors.AccentBlue,
                                    isExpandable = true,
                                )
                            }
                        }

                        items(
                            items = filteredFolders,
                            key = { folder ->
                                folder.folderId
                            },
                        ) { folder ->

                            FolderItem(
                                name = folder.name,
                                count = state.folderCounts[folder.folderId] ?: 0,
                                color = DevLedgerColors.AccentPurple,
                                onEdit = {
                                    folderPendingRename = folder
                                },
                                onDelete = {
                                    folderPendingDelete = folder
                                },
                            )
                        }

                        if (
                            trimmedSearchQuery.isNotBlank() &&
                            filteredFolders.isEmpty()
                        ) {
                            item {
                                EmptySearchItem(
                                    text = "No matching folders",
                                )
                            }
                        }

                        if (trimmedSearchQuery.isBlank()) {
                            item {
                                FolderItem(
                                    name = "Archived",
                                    count = 0,
                                    color = DevLedgerColors.TextMuted,
                                    icon = Icons.Default.Inventory,
                                )
                            }
                        }
                    }
                }

                // Vertical divider
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .padding(vertical = 8.dp)
                        .background(DevLedgerColors.Border),
                )

                // ---------------------------------------------------------
                // TAGS
                // ---------------------------------------------------------

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(start = 8.dp),
                ) {

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {

                        Text(
                            text = "TAGS",
                            color = DevLedgerColors.TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )

                        Text(
                            text = "+ New Tag",
                            color = DevLedgerColors.AccentBlue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable {
                                showCreateTagDialog = true
                            },
                        )
                    }

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {

                        if (trimmedSearchQuery.isBlank()) {
                            item {
                                TagItem(
                                    name = "All Tags",
                                    count = state.tagCounts.sumOf { it.second },
                                    color = DevLedgerColors.AccentPurple,
                                )
                            }
                        }

                        items(
                            items = filteredTags,
                            key = { (tagName, _) ->
                                tagName.lowercase()
                            },
                        ) { (tagName, count) ->

                            TagItem(
                                name = tagName,
                                count = count,
                                color = DevLedgerColors.AccentPurple,
                            )
                        }

                        if (
                            trimmedSearchQuery.isNotBlank() &&
                            filteredTags.isEmpty()
                        ) {
                            item {
                                EmptySearchItem(
                                    text = "No matching tags",
                                )
                            }
                        }
                    }
                }
            }

            // -------------------------------------------------------------
            // TIPS
            // -------------------------------------------------------------

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {

                TipCard(
                    icon = Icons.Default.Info,
                    title = "Folder Tips",
                    text = "Use folders to group related snippets.",
                    color = DevLedgerColors.AccentBlue,
                    modifier = Modifier.weight(1f),
                )

                TipCard(
                    icon = Icons.Default.Lightbulb,
                    title = "Tag Tips",
                    text = "Use tags to quickly filter and find related snippets.",
                    color = DevLedgerColors.AccentPurple,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    // ---------------------------------------------------------------------
    // DIALOGS
    // ---------------------------------------------------------------------

    if (showCreateFolderDialog) {
        FolderDialog(
            title = "New Folder",
            initialName = "",
            onDismiss = {
                showCreateFolderDialog = false
            },
            onConfirm = { name ->
                viewModel.createFolder(name)
                showCreateFolderDialog = false
            },
        )
    }

    if (showCreateTagDialog) {
        TagDialog(
            existingTags = state.tagCounts.map { it.first },
            onDismiss = {
                showCreateTagDialog = false
            },
            onConfirm = { name ->
                viewModel.createTag(name)
                showCreateTagDialog = false
            },
        )
    }

    folderPendingRename?.let { folder ->

        FolderDialog(
            title = "Rename Folder",
            initialName = folder.name,
            onDismiss = {
                folderPendingRename = null
            },
            onConfirm = { name ->
                viewModel.renameFolder(
                    id = folder.folderId,
                    name = name,
                )

                folderPendingRename = null
            },
        )
    }

    folderPendingDelete?.let { folder ->

        DeleteFolderDialog(
            folder = folder,
            otherFolders = state.folders.filter {
                it.folderId != folder.folderId
            },
            onDismiss = {
                folderPendingDelete = null
            },
            onConfirm = { reassignTo ->
                viewModel.deleteFolder(
                    id = folder.folderId,
                    reassignToFolderId = reassignTo,
                )

                folderPendingDelete = null
            },
        )
    }
}

@Composable
private fun FoldersTopBar(
    onBack: () -> Unit,
    searchActive: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchClick: () -> Unit,
    onCloseSearch: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(
                horizontal = 4.dp,
                vertical = 8.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {

        IconButton(
            onClick = {
                if (searchActive) {
                    onCloseSearch()
                } else {
                    onBack()
                }
            },
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription =
                    if (searchActive) {
                        "Close search"
                    } else {
                        "Back"
                    },
                tint = Color.White,
            )
        }

        if (searchActive) {

            Spacer(
                modifier = Modifier.width(4.dp),
            )

            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                singleLine = true,
                placeholder = {
                    Text(
                        text = "Search folders & tags",
                        color = DevLedgerColors.TextMuted,
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = DevLedgerColors.TextMuted,
                    )
                },
                trailingIcon = {

                    if (searchQuery.isNotEmpty()) {

                        IconButton(
                            onClick = {
                                onSearchQueryChange("")
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear search",
                                tint = Color.White,
                            )
                        }
                    } else {

                        IconButton(
                            onClick = onCloseSearch,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close search",
                                tint = Color.White,
                            )
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = DevLedgerColors.AccentBlue,
                    unfocusedBorderColor = DevLedgerColors.Border,
                    cursorColor = DevLedgerColors.AccentBlue,
                ),
            )
        } else {

            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                tint = DevLedgerColors.AccentBlue,
                modifier = Modifier.size(28.dp),
            )

            Spacer(
                modifier = Modifier.width(12.dp),
            )

            Column {

                Text(
                    text = "Folders & Tags",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )

                Text(
                    text = "Organize your snippets",
                    color = DevLedgerColors.TextMuted,
                    fontSize = 12.sp,
                )
            }

            Spacer(
                modifier = Modifier.weight(1f),
            )

            IconButton(
                onClick = onSearchClick,
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search folders and tags",
                    tint = Color.White,
                )
            }
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
    onDelete: (() -> Unit)? = null,
) {
    var menuExpanded by remember {
        mutableStateOf(false)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DevLedgerColors.SurfaceCard)
            .border(
                width = 1.dp,
                color = DevLedgerColors.Border,
                shape = RoundedCornerShape(12.dp),
            )
            .padding(10.dp),
    ) {

        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {

            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp),
            )

            Spacer(
                modifier = Modifier.width(10.dp),
            )

            Column(
                modifier = Modifier.weight(1f),
            ) {

                Text(
                    text = name,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Text(
                    text = "$count snippets",
                    color = DevLedgerColors.TextMuted,
                    fontSize = 11.sp,
                )
            }

            if (isExpandable) {

                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = DevLedgerColors.TextMuted,
                    modifier = Modifier.size(16.dp),
                )

            } else if (
                onEdit != null ||
                onDelete != null
            ) {

                Box {

                    IconButton(
                        onClick = {
                            menuExpanded = true
                        },
                        modifier = Modifier.size(30.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Folder actions",
                            tint = DevLedgerColors.TextMuted,
                            modifier = Modifier.size(18.dp),
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = {
                            menuExpanded = false
                        },
                        modifier = Modifier.background(
                            DevLedgerColors.SurfaceCard
                        ),
                    ) {

                        if (onEdit != null) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Rename",
                                        color = Color.White,
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = null,
                                        tint = DevLedgerColors.TextMuted,
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onEdit()
                                },
                            )
                        }

                        if (onDelete != null) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Delete",
                                        color = DevLedgerColors.ErrorRed,
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = DevLedgerColors.ErrorRed,
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onDelete()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TagItem(
    name: String,
    count: Int,
    color: Color,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DevLedgerColors.SurfaceCard)
            .border(
                width = 1.dp,
                color = DevLedgerColors.Border,
                shape = RoundedCornerShape(12.dp),
            )
            .padding(10.dp),
    ) {

        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {

            Icon(
                imageVector = Icons.Default.LocalOffer,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(18.dp),
            )

            Spacer(
                modifier = Modifier.width(10.dp),
            )

            Column(
                modifier = Modifier.weight(1f),
            ) {

                Text(
                    text = name,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Text(
                    text = "$count snippets",
                    color = DevLedgerColors.TextMuted,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun EmptySearchItem(
    text: String,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = DevLedgerColors.TextMuted,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun TipCard(
    icon: ImageVector,
    title: String,
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(DevLedgerColors.SurfaceCard)
            .padding(12.dp),
    ) {

        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {

            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp),
            )

            Spacer(
                modifier = Modifier.width(8.dp),
            )

            Text(
                text = title,
                color = color,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(
            modifier = Modifier.height(4.dp),
        )

        Text(
            text = text,
            color = DevLedgerColors.TextMuted,
            fontSize = 11.sp,
            lineHeight = 16.sp,
        )
    }
}

@Composable
private fun FolderDialog(
    title: String,
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember {
        mutableStateOf(initialName)
    }

    val validName = name.trim().isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DevLedgerColors.SurfaceCard,
        title = {
            Text(
                text = title,
                color = Color.White,
            )
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                },
                singleLine = true,
                label = {
                    Text("Folder name")
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = DevLedgerColors.AccentBlue,
                    unfocusedBorderColor = DevLedgerColors.Border,
                    cursorColor = DevLedgerColors.AccentBlue,
                ),
            )
        },
        confirmButton = {
            TextButton(
                enabled = validName,
                onClick = {
                    if (validName) {
                        onConfirm(name.trim())
                    }
                },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
            ) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun TagDialog(
    existingTags: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember {
        mutableStateOf("")
    }

    val trimmedName = name.trim()

    val duplicateExists = existingTags.any { existingName ->
        existingName.equals(
            trimmedName,
            ignoreCase = true,
        )
    }

    val validName =
        trimmedName.isNotBlank() &&
                !duplicateExists

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DevLedgerColors.SurfaceCard,
        title = {
            Text(
                text = "New Tag",
                color = Color.White,
            )
        },
        text = {
            Column {

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                    },
                    singleLine = true,
                    isError = duplicateExists,
                    label = {
                        Text("Tag name")
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = DevLedgerColors.AccentPurple,
                        unfocusedBorderColor = DevLedgerColors.Border,
                        errorBorderColor = DevLedgerColors.ErrorRed,
                        cursorColor = DevLedgerColors.AccentPurple,
                    ),
                )

                if (duplicateExists) {
                    Spacer(
                        modifier = Modifier.height(6.dp),
                    )

                    Text(
                        text = "A tag with this name already exists.",
                        color = DevLedgerColors.ErrorRed,
                        fontSize = 11.sp,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = validName,
                onClick = {
                    if (validName) {
                        onConfirm(trimmedName)
                    }
                },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
            ) {
                Text("Cancel")
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeleteFolderDialog(
    folder: FolderEntity,
    otherFolders: List<FolderEntity>,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit,
) {
    var reassignTo by remember {
        mutableStateOf<String?>(null)
    }

    var expanded by remember {
        mutableStateOf(false)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DevLedgerColors.SurfaceCard,
        title = {
            Text(
                text = "Delete \"${folder.name}\"?",
                color = Color.White,
            )
        },
        text = {
            Column {

                Text(
                    text = "Its snippets need a new home — choose where they should move.",
                    color = DevLedgerColors.TextSecondary,
                    modifier = Modifier.padding(
                        bottom = 12.dp
                    ),
                )

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = {
                        expanded = it
                    },
                ) {

                    OutlinedTextField(
                        value = otherFolders
                            .find {
                                it.folderId == reassignTo
                            }
                            ?.name
                            ?: "No folder",
                        onValueChange = {},
                        readOnly = true,
                        label = {
                            Text("Move snippets to")
                        },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(
                                expanded = expanded
                            )
                        },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = DevLedgerColors.AccentBlue,
                            unfocusedBorderColor = DevLedgerColors.Border,
                        ),
                    )

                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = {
                            expanded = false
                        },
                        modifier = Modifier.background(
                            DevLedgerColors.SurfaceCard
                        ),
                    ) {

                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "No folder",
                                    color = Color.White,
                                )
                            },
                            onClick = {
                                reassignTo = null
                                expanded = false
                            },
                        )

                        otherFolders.forEach { otherFolder ->

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = otherFolder.name,
                                        color = Color.White,
                                    )
                                },
                                onClick = {
                                    reassignTo =
                                        otherFolder.folderId

                                    expanded = false
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(reassignTo)
                },
            ) {
                Text(
                    text = "Delete",
                    color = DevLedgerColors.ErrorRed,
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
            ) {
                Text("Cancel")
            }
        },
    )
}