package com.devledger.app.ui.screens.snippets

import com.devledger.app.util.localization.rememberAppStrings

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devledger.app.R
import com.devledger.app.data.local.entity.SnippetEntity
import com.devledger.app.data.local.entity.UserTagEntity
import com.devledger.app.data.repository.FolderRepository
import com.devledger.app.data.repository.SnippetRepository
import com.devledger.app.data.repository.UserTagRepository
import com.devledger.app.ui.components.DevLedgerCard
import com.devledger.app.ui.theme.DevLedgerColors
import com.devledger.app.util.SyntaxHighlighter
import com.devledger.app.util.TimeAgo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SnippetDetailUiState(val snippet: SnippetEntity? = null, val folderName: String? = null, val isLoading: Boolean = true)

@HiltViewModel
/**
 * Reference scope: UI and state-holder separation.
 * (Google Developers, n.d.a; Google Developers, n.d.f). See REFERENCES-CODE.md for full entries.
 */
class SnippetDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val snippetRepository: SnippetRepository,
    private val folderRepository: FolderRepository,
    private val userTagRepository: UserTagRepository,
) : ViewModel() {

    private val snippetId: String = requireNotNull(savedStateHandle["snippetId"])
    private val _uiState = MutableStateFlow(SnippetDetailUiState())
    val uiState: StateFlow<SnippetDetailUiState> = _uiState.asStateFlow()

    val availableTags: StateFlow<List<UserTagEntity>> = userTagRepository.observeTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init { load() }

    private fun load() {
        viewModelScope.launch {
            val snippet = snippetRepository.getById(snippetId)
            val folderName = snippet?.folderId?.let { folderRepository.getById(it)?.name }
            _uiState.value = SnippetDetailUiState(snippet, folderName, isLoading = false)
        }
    }

    fun addTag(tagName: String) {
        val snippet = _uiState.value.snippet ?: return
        val currentTags = snippet.tagsCsv.split(",").map(String::trim).filter(String::isNotEmpty)
        if (currentTags.any { it.equals(tagName, ignoreCase = true) }) return

        viewModelScope.launch {
            // Ensure tag exists globally
            userTagRepository.create(tagName)

            val newTags = currentTags + tagName
            snippetRepository.createOrUpdate(
                userId = snippet.userId,
                id = snippet.snippetId,
                title = snippet.title,
                content = snippet.content,
                language = snippet.language,
                description = snippet.description,
                tags = newTags,
                folderId = snippet.folderId
            )
            load()
        }
    }

    fun removeTag(tagName: String) {
        val snippet = _uiState.value.snippet ?: return
        val currentTags = snippet.tagsCsv.split(",").map(String::trim).filter(String::isNotEmpty)
        val newTags = currentTags.filter { !it.equals(tagName, ignoreCase = true) }

        viewModelScope.launch {
            snippetRepository.createOrUpdate(
                userId = snippet.userId,
                id = snippet.snippetId,
                title = snippet.title,
                content = snippet.content,
                language = snippet.language,
                description = snippet.description,
                tags = newTags,
                folderId = snippet.folderId
            )
            load()
        }
    }

    fun toggleFavourite() {
        val snippet = _uiState.value.snippet ?: return
        viewModelScope.launch { snippetRepository.toggleFavourite(snippet.snippetId, !snippet.isFavourite); load() }
    }

    fun archive() {
        val snippet = _uiState.value.snippet ?: return
        viewModelScope.launch { snippetRepository.archive(snippet.snippetId, true) }
    }

    fun duplicate(copyTitle: String, onDuplicated: (String) -> Unit) {
        val snippet = _uiState.value.snippet ?: return
        viewModelScope.launch {
            val copy = snippetRepository.createOrUpdate(
                userId = snippet.userId, id = null, title = copyTitle, content = snippet.content,
                language = snippet.language, description = snippet.description,
                tags = snippet.tagsCsv.split(",").map(String::trim).filter(String::isNotEmpty), folderId = snippet.folderId,
            )
            onDuplicated(copy.snippetId)
        }
    }
}

/** Snippet Detail view: complete content and metadata.
 *  Key elements: Title, language, tags, syntax-highlighted code, description and management actions.
 *  Matches Image 2 pixel-accurate design. */
@Composable
/**
 * Reference scope: UI and state-holder separation.
 * (Google Developers, n.d.a; Google Developers, n.d.f). See REFERENCES-CODE.md for full entries.
 */
fun SnippetDetailScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onDuplicated: (String) -> Unit,
    viewModel: SnippetDetailViewModel = hiltViewModel(),
) {
    val strings = rememberAppStrings()
    val state by viewModel.uiState.collectAsState()
    val availableTags by viewModel.availableTags.collectAsState()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showFullscreen by remember { mutableStateOf(false) }
    var showTagDropdown by remember { mutableStateOf(false) }
    var showCreateTagDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            DetailTopBar(
                onBack = onBack,
                onEdit = { state.snippet?.let { onEdit(it.snippetId) } },
                isFavourite = state.snippet?.isFavourite ?: false,
                onToggleFavourite = viewModel::toggleFavourite
            )
        },
        containerColor = DevLedgerColors.BgDeep
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DevLedgerColors.AccentBlue)
            }
        } else {
            val snippet = state.snippet
            if (snippet == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(strings.get(R.string.lang_this_snippet_could_not_be_found), color = DevLedgerColors.TextMuted)
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    Badge(snippet.language, DevLedgerColors.AccentPurple)

                    Text(
                        text = snippet.title,
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )

                    if (!snippet.description.isNullOrBlank()) {
                        Text(
                            text = snippet.description,
                            color = DevLedgerColors.TextMuted,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                        )
                    }

                    // Metadata Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(DevLedgerColors.SurfaceCard)
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MetaItem(Icons.Default.Folder, strings.get(R.string.lang_folder), state.folderName ?: strings.get(R.string.lang_none))
                        MetaItem(Icons.Default.CalendarToday, strings.get(R.string.lang_created), formatDate(snippet.createdAt, strings.locale))
                        MetaItem(Icons.Default.Update, strings.get(R.string.lang_updated), formatDate(snippet.updatedAt, strings.locale))
                        MetaItem(Icons.Default.Person, strings.get(R.string.lang_creator), strings.get(R.string.lang_you))
                    }

                    Spacer(Modifier.height(24.dp))

                    Text(strings.get(R.string.lang_tags), color = DevLedgerColors.TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val currentTags = snippet.tagsCsv.split(",").map(String::trim).filter(String::isNotEmpty)
                        currentTags.forEach { tag ->
                            TagBadge(tag) { viewModel.removeTag(tag) }
                            Spacer(Modifier.width(8.dp))
                        }

                        Box {
                            AddTagButton { showTagDropdown = true }

                            DropdownMenu(
                                expanded = showTagDropdown,
                                onDismissRequest = { showTagDropdown = false },
                                modifier = Modifier.background(DevLedgerColors.SurfaceCard).widthIn(min = 180.dp)
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Add, null, tint = DevLedgerColors.AccentBlue, modifier = Modifier.size(20.dp))
                                            Spacer(Modifier.width(12.dp))
                                            Text(strings.get(R.string.lang_create_new_tag), color = DevLedgerColors.AccentBlue, fontWeight = FontWeight.Bold)
                                        }
                                    },
                                    onClick = {
                                        showCreateTagDialog = true
                                        showTagDropdown = false
                                    }
                                )

                                val selectableSuggestions = availableTags.map { it.name }.filter { it !in currentTags }
                                if (selectableSuggestions.isNotEmpty()) {
                                    HorizontalDivider(color = DevLedgerColors.Border, modifier = Modifier.padding(vertical = 4.dp))
                                    selectableSuggestions.forEach { suggestion ->
                                        DropdownMenuItem(
                                            text = { Text(suggestion, color = Color.White) },
                                            onClick = {
                                                viewModel.addTag(suggestion)
                                                showTagDropdown = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(strings.get(R.string.lang_code), color = DevLedgerColors.TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { clipboard.setText(AnnotatedString(snippet.content)) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.ContentCopy, contentDescription = strings.get(R.string.lang_copy), tint = DevLedgerColors.AccentBlue, modifier = Modifier.size(18.dp))
                            }
                            Text(strings.get(R.string.lang_copy), color = DevLedgerColors.AccentBlue, fontSize = 12.sp)
                            Spacer(Modifier.width(16.dp))
                            IconButton(onClick = { showFullscreen = true }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Fullscreen, contentDescription = strings.get(R.string.lang_fullscreen), tint = DevLedgerColors.TextMuted, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    CodeViewer(snippet.content)

                    Spacer(Modifier.height(24.dp))

                    Text(strings.get(R.string.lang_description_heading), color = DevLedgerColors.TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        snippet.description ?: strings.get(R.string.lang_no_description_provided),
                        color = DevLedgerColors.TextSecondary,
                        fontSize = 14.sp
                    )

                    Spacer(Modifier.height(24.dp))

                    Text(strings.get(R.string.lang_stats), color = DevLedgerColors.TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard(Icons.Default.FormatAlignLeft, strings.get(R.string.lang_lines), snippet.content.lineSequence().count().toString(), Modifier.weight(1f))
                        StatCard(Icons.Default.TextFormat, strings.get(R.string.lang_characters), snippet.content.length.toString(), Modifier.weight(1f))
                        StatCard(Icons.Default.Star, strings.get(R.string.lang_is_favorite), if (snippet.isFavourite) strings.get(R.string.lang_yes) else strings.get(R.string.lang_no), Modifier.weight(1f))
                        StatCard(Icons.Default.History, strings.get(R.string.lang_age), TimeAgo.format(snippet.createdAt, strings), Modifier.weight(1f))
                    }

                    Spacer(Modifier.height(24.dp))

                    Text(strings.get(R.string.lang_action), color = DevLedgerColors.TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ActionBtn(Icons.Default.Share, strings.get(R.string.lang_share), Modifier.weight(1f)) {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "${snippet.title}\n\n${snippet.content}")
                            }
                            context.startActivity(Intent.createChooser(intent, strings.get(R.string.lang_share_snippet)))
                        }
                        ActionBtn(Icons.Default.ContentCopy, strings.get(R.string.lang_duplicate), Modifier.weight(1f)) { viewModel.duplicate(strings.get(R.string.lang_copy_title, snippet.title), onDuplicated) }
                        ActionBtn(Icons.Default.FileDownload, strings.get(R.string.lang_export), Modifier.weight(1f)) {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TITLE, "${snippet.title}.${snippet.language.lowercase()}")
                                putExtra(Intent.EXTRA_TEXT, snippet.content)
                            }
                            context.startActivity(Intent.createChooser(intent, strings.get(R.string.lang_export_snippet)))
                        }
                        ActionBtn(Icons.Default.Delete, strings.get(R.string.lang_delete), Modifier.weight(1f), isDestructive = true) { showDeleteConfirm = true }
                    }

                    Spacer(Modifier.height(48.dp))
                }
            }
        }
    }

    if (showCreateTagDialog) {
        TagCreationDialog(
            onDismiss = { showCreateTagDialog = false },
            onConfirm = { name ->
                viewModel.addTag(name)
                showCreateTagDialog = false
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(strings.get(R.string.lang_delete_snippet), color = Color.White) },
            text = { Text(strings.get(R.string.lang_are_you_sure_you_want_to_move_this_snippet_to_the_recycle_bi), color = DevLedgerColors.TextSecondary) },
            confirmButton = {
                TextButton(onClick = { viewModel.archive(); onBack() }) {
                    Text(strings.get(R.string.lang_delete), color = DevLedgerColors.ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(strings.get(R.string.lang_cancel), color = Color.White)
                }
            },
            containerColor = DevLedgerColors.SurfaceCard
        )
    }

    if (showFullscreen) {
        Dialog(
            onDismissRequest = { showFullscreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(Modifier.fillMaxSize().background(DevLedgerColors.BgDeep)) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(state.snippet?.title ?: strings.get(R.string.lang_code_view), color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
                        IconButton(onClick = { showFullscreen = false }) {
                            Icon(Icons.Default.Close, contentDescription = strings.get(R.string.lang_close), tint = Color.White)
                        }
                    }
                    CodeViewer(state.snippet?.content ?: "")
                }
            }
        }
    }
}

@Composable
private fun DetailTopBar(onBack: () -> Unit, onEdit: () -> Unit, isFavourite: Boolean, onToggleFavourite: () -> Unit) {
    val strings = rememberAppStrings()
    Row(
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.get(R.string.lang_back), tint = Color.White)
        }
        Image(
            painter = painterResource(id = R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(32.dp).clip(CircleShape)
        )
        Spacer(Modifier.width(8.dp))
        Text("DevLedger", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)

        Spacer(Modifier.weight(1f))

        IconButton(onClick = onToggleFavourite) {
            Icon(
                if (isFavourite) Icons.Filled.Star else Icons.Outlined.Star,
                contentDescription = strings.get(R.string.lang_favourite),
                tint = if (isFavourite) DevLedgerColors.AccentBlue else Color.White
            )
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Default.Edit, contentDescription = strings.get(R.string.lang_edit), tint = Color.White)
        }
    }
}

@Composable
private fun MetaItem(icon: ImageVector, label: String, value: String) {
    Column(horizontalAlignment = Alignment.Start) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = DevLedgerColors.TextMuted, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, color = DevLedgerColors.TextMuted, fontSize = 11.sp)
        }
        Text(value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun TagBadge(text: String, onRemove: () -> Unit) {
    val strings = rememberAppStrings()
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(DevLedgerColors.SurfaceCard)
            .border(1.dp, DevLedgerColors.Border, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(DevLedgerColors.AccentPurple))
            Spacer(Modifier.width(8.dp))
            Text(text, color = Color.White, fontSize = 12.sp)
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = strings.get(R.string.lang_remove),
                tint = DevLedgerColors.TextMuted,
                modifier = Modifier.size(14.dp).clickable { onRemove() }
            )
        }
    }
}

@Composable
private fun TagCreationDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val strings = rememberAppStrings()
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DevLedgerColors.SurfaceCard,
        title = { Text(strings.get(R.string.lang_create_new_tag_title), color = Color.White) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(strings.get(R.string.lang_tag_name)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name.trim()) }) {
                Text(strings.get(R.string.lang_add), color = DevLedgerColors.AccentBlue)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.get(R.string.lang_cancel), color = Color.White) } },
    )
}

@Composable
private fun AddTagButton(onClick: () -> Unit) {
    val strings = rememberAppStrings()
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(DevLedgerColors.SurfaceCardAlt)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(strings.get(R.string.lang_add_tag), color = DevLedgerColors.AccentBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CodeViewer(content: String) {
    val highlighted = remember(content) { SyntaxHighlighter.highlight(content) }
    val lines = content.lineSequence().toList()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0F1320))
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.width(32.dp)) {
            lines.forEachIndexed { index, _ ->
                Text(
                    text = (index + 1).toString(),
                    color = Color(0xFF2D3748),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth().padding(end = 8.dp)
                )
            }
        }
        Text(
            text = highlighted,
            color = DevLedgerColors.TextPrimary,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 20.sp
        )
    }
}

@Composable
private fun StatCard(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(DevLedgerColors.SurfaceCard)
            .padding(12.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Icon(icon, contentDescription = null, tint = DevLedgerColors.AccentBlue, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(8.dp))
        Text(value, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(label, color = DevLedgerColors.TextMuted, fontSize = 11.sp)
    }
}

@Composable
private fun ActionBtn(icon: ImageVector, label: String, modifier: Modifier = Modifier, isDestructive: Boolean = false, onClick: () -> Unit) {
    val contentColor = if (isDestructive) DevLedgerColors.ErrorRed else Color.White
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(DevLedgerColors.SurfaceCard)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = DevLedgerColors.TextMuted, fontSize = 11.sp)
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

private fun formatDate(timestamp: Long, locale: Locale): String {
    val sdf = SimpleDateFormat("dd MMM yyyy", locale)
    return sdf.format(Date(timestamp))
}
