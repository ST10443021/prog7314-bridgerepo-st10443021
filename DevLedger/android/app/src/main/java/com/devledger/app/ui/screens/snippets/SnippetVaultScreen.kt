package com.devledger.app.ui.screens.snippets


import com.devledger.app.util.localization.rememberAppStrings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.devledger.app.R
import com.devledger.app.data.local.entity.SnippetEntity
import com.devledger.app.ui.components.DevLedgerCard
import com.devledger.app.ui.theme.DevLedgerColors
import com.devledger.app.util.SyntaxHighlighter
import com.devledger.app.util.TimeAgo

private val FolderPalette = listOf(
    DevLedgerColors.AccentBlue, DevLedgerColors.AccentPurple, DevLedgerColors.SuccessGreen,
    DevLedgerColors.WarningAmber, DevLedgerColors.AccentCyan, DevLedgerColors.ErrorRed,
)

/**
 * Snippet Vault screen: Provides central access to stored code snippets.
 * Key elements: Search, folder/tag filters, sorting, favourites and snippet previews.
 * Behaviour: Users search, filter and open snippets. Content is loaded from RoomDB for offline access.
 * Matches Image 1 pixel-accurate design.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
/**
 * Reference scope: UI and state-holder separation.
 * (Google Developers, n.d.a; Google Developers, n.d.f). See REFERENCES-CODE.md for full entries.
 */
fun SnippetVaultScreen(
    onOpenSnippet: (String) -> Unit,
    onAddSnippet: () -> Unit,
    onOpenFoldersAndTags: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenSync: () -> Unit,
    viewModel: SnippetVaultViewModel = hiltViewModel(),
) {
    val strings = rememberAppStrings()
    val state by viewModel.uiState.collectAsState()
    var sortMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            VaultTopBar(onOpenDrawer, onOpenNotifications)
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddSnippet,
                containerColor = DevLedgerColors.AccentBlue,
                contentColor = Color.White,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.padding(bottom = 16.dp, end = 8.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = strings.get(R.string.lang_new_snippet))
            }
        },
        containerColor = DevLedgerColors.BgDeep
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp)
            ) {
                item {
                    Text(
                        text = strings.get(R.string.lang_snippet_vault),
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = strings.get(R.string.lang_store_organize_and_reuse_your_code_snippets),
                        color = DevLedgerColors.TextMuted,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
                    )
                }

                item {
                    SearchBar(
                        query = state.query,
                        onQueryChange = viewModel::onQueryChange,
                        onOpenFilter = onOpenFoldersAndTags
                    )
                    Spacer(Modifier.height(24.dp))
                }

                // FOLDERS Section
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(strings.get(R.string.lang_folders), color = DevLedgerColors.TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(
                            strings.get(R.string.lang_new_folder_action),
                            color = DevLedgerColors.AccentBlue,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.clickable { onOpenFoldersAndTags() }
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        item {
                            FolderCard(
                                name = strings.get(R.string.lang_all_snippets),
                                count = state.allSnippets.size,
                                color = DevLedgerColors.AccentBlue,
                                selected = state.selectedFolderId == null,
                                onClick = { viewModel.onFolderSelected(null) }
                            )
                        }
                        itemsIndexed(state.folders) { index, folder ->
                            FolderCard(
                                name = folder.name,
                                count = state.allSnippets.count { it.folderId == folder.folderId },
                                color = FolderPalette[index % FolderPalette.size],
                                selected = state.selectedFolderId == folder.folderId,
                                onClick = { viewModel.onFolderSelected(folder.folderId) }
                            )
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }

                // TAGS Section
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(strings.get(R.string.lang_tags), color = DevLedgerColors.TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(
                            strings.get(R.string.lang_view_all),
                            color = DevLedgerColors.AccentBlue,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.clickable { onOpenFoldersAndTags() }
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            TagChip(
                                label = strings.get(R.string.lang_all),
                                count = null,
                                selected = state.selectedTag == null,
                                onClick = { viewModel.onTagSelected(null) }
                            )
                        }
                        items(state.availableTags) { (tag, count) ->
                            TagChip(
                                label = tag,
                                count = count,
                                selected = state.selectedTag == tag,
                                onClick = { viewModel.onTagSelected(tag) }
                            )
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }

                // SNIPPETS List Header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            strings.get(R.string.lang_snippets_heading, state.visibleSnippets.size),
                            color = DevLedgerColors.TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(DevLedgerColors.SurfaceCard)
                                        .clickable { sortMenuExpanded = true }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(strings.get(state.sortOption.labelRes), color = DevLedgerColors.TextPrimary, fontSize = 12.sp)
                                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = DevLedgerColors.TextMuted, modifier = Modifier.size(16.dp))
                                }
                                DropdownMenu(
                                    expanded = sortMenuExpanded,
                                    onDismissRequest = { sortMenuExpanded = false },
                                    modifier = Modifier.background(DevLedgerColors.SurfaceCard)
                                ) {
                                    SnippetSortOption.entries.forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(strings.get(option.labelRes), color = Color.White) },
                                            onClick = {
                                                viewModel.onSortOptionChange(option)
                                                sortMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                // Error Message
                state.errorMessage?.let { message ->
                    item {
                        Text(
                            text = message.resolve(strings),
                            color = DevLedgerColors.ErrorRed,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                    }
                }

                // Empty State
                if (state.visibleSnippets.isEmpty() && !state.isRefreshing) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(stringResource(R.string.vault_empty), color = DevLedgerColors.TextMuted, fontSize = 14.sp)
                        }
                    }
                }

                // SNIPPETS List
                items(state.visibleSnippets, key = { it.snippetId }) { snippet ->
                    SnippetPreviewCard(
                        snippet = snippet,
                        onClick = { onOpenSnippet(snippet.snippetId) },
                        onToggleFavourite = { viewModel.toggleFavourite(snippet) },
                        onArchive = { viewModel.archive(snippet) }
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun VaultTopBar(onOpenDrawer: () -> Unit, onOpenNotifications: () -> Unit) {
    val strings = rememberAppStrings()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onOpenDrawer) {
            Icon(Icons.Filled.Menu, contentDescription = strings.get(R.string.lang_menu), tint = Color.White)
        }

        Spacer(Modifier.width(8.dp))

        Image(
            painter = painterResource(id = R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(32.dp).clip(CircleShape)
        )

        Spacer(Modifier.width(8.dp))

        Text(
            text = "DevLedger",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.weight(1f))

        IconButton(onClick = onOpenNotifications) {
            Box {
                Icon(Icons.Filled.Notifications, contentDescription = "Notifications", tint = Color.White)
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = (-2).dp, y = 2.dp)
                        .clip(CircleShape)
                        .background(DevLedgerColors.AccentBlue)
                        .border(1.5.dp, DevLedgerColors.BgDeep, CircleShape)
                )
            }
        }
    }
}

@Composable
private fun SearchBar(query: String, onQueryChange: (String) -> Unit, onOpenFilter: () -> Unit) {
    val strings = rememberAppStrings()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DevLedgerColors.SurfaceCard)
            .padding(horizontal = 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = DevLedgerColors.TextMuted, modifier = Modifier.size(20.dp))
        TextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text(strings.get(R.string.lang_search_snippets), color = DevLedgerColors.TextMuted, fontSize = 14.sp) },
            modifier = Modifier.weight(1f),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            singleLine = true
        )
        IconButton(onClick = onOpenFilter) {
            Icon(Icons.Filled.FilterList, contentDescription = strings.get(R.string.lang_filter), tint = DevLedgerColors.TextMuted, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun FolderCard(name: String, count: Int, color: Color, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(100.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) color.copy(alpha = 0.15f) else DevLedgerColors.SurfaceCard)
            .border(1.dp, if (selected) color else DevLedgerColors.Border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Icon(Icons.Filled.Folder, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(12.dp))
        Text(name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(count.toString(), color = DevLedgerColors.TextMuted, fontSize = 12.sp)
    }
}

@Composable
private fun TagChip(label: String, count: Int?, selected: Boolean, onClick: () -> Unit) {
    val strings = rememberAppStrings()
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) DevLedgerColors.AccentBlue else DevLedgerColors.SurfaceCard)
            .border(1.dp, if (selected) DevLedgerColors.AccentBlue else DevLedgerColors.Border, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (count != null) {
            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(DevLedgerColors.AccentPurple))
            Spacer(Modifier.width(8.dp))
        }
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        if (count != null) {
            Spacer(Modifier.width(6.dp))
            Text(count.toString(), color = if (selected) Color.White.copy(alpha = 0.7f) else DevLedgerColors.TextMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SnippetPreviewCard(
    snippet: SnippetEntity,
    onClick: () -> Unit,
    onToggleFavourite: () -> Unit,
    onArchive: () -> Unit
) {
    val strings = rememberAppStrings()
    val tags = remember(snippet.tagsCsv) {
        snippet.tagsCsv.split(",").map(String::trim).filter(String::isNotEmpty)
    }

    DevLedgerCard(modifier = Modifier.clickable(onClick = onClick)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    snippet.title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Badge(snippet.language, DevLedgerColors.AccentPurple)
                if (tags.isNotEmpty()) {
                    Spacer(Modifier.width(6.dp))
                    Badge(tags.first(), DevLedgerColors.SuccessGreen)
                }
                IconButton(onClick = onToggleFavourite, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = if (snippet.isFavourite) Icons.Filled.Star else Icons.Outlined.Star,
                        contentDescription = strings.get(R.string.lang_favourite),
                        tint = if (snippet.isFavourite) DevLedgerColors.AccentBlue else DevLedgerColors.TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = { /* Menu */ }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.MoreVert, contentDescription = null, tint = DevLedgerColors.TextMuted, modifier = Modifier.size(20.dp))
                }
            }

            if (!snippet.description.isNullOrBlank()) {
                Text(
                    snippet.description,
                    color = DevLedgerColors.TextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(Modifier.height(12.dp))

            // Code block with syntax highlighting look
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0F1320))
                    .padding(12.dp)
            ) {
                Text(
                    text = SyntaxHighlighter.highlight(snippet.content),
                    color = DevLedgerColors.TextPrimary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(TimeAgo.format(snippet.updatedAt, strings), color = DevLedgerColors.TextMuted, fontSize = 11.sp)
                IconButton(onClick = onArchive, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Filled.Archive, contentDescription = strings.get(R.string.lang_archive), tint = DevLedgerColors.TextMuted, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.1f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}
