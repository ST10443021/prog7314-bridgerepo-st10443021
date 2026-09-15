package com.devledger.app.ui.screens.snippets

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
import com.devledger.app.data.repository.FolderRepository
import com.devledger.app.data.repository.SnippetRepository
import com.devledger.app.ui.components.DevLedgerCard
import com.devledger.app.ui.theme.DevLedgerColors
import com.devledger.app.util.SyntaxHighlighter
import com.devledger.app.util.TimeAgo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SnippetDetailUiState(val snippet: SnippetEntity? = null, val folderName: String? = null, val isLoading: Boolean = true)

@HiltViewModel
class SnippetDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val snippetRepository: SnippetRepository,
    private val folderRepository: FolderRepository,
) : ViewModel() {

    private val snippetId: String = requireNotNull(savedStateHandle["snippetId"])
    private val _uiState = MutableStateFlow(SnippetDetailUiState())
    val uiState: StateFlow<SnippetDetailUiState> = _uiState.asStateFlow()

    init { load() }

    private fun load() {
        viewModelScope.launch {
            val snippet = snippetRepository.getById(snippetId)
            val folderName = snippet?.folderId?.let { folderRepository.getById(it)?.name }
            _uiState.value = SnippetDetailUiState(snippet, folderName, isLoading = false)
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

    fun duplicate(onDuplicated: (String) -> Unit) {
        val snippet = _uiState.value.snippet ?: return
        viewModelScope.launch {
            val copy = snippetRepository.createOrUpdate(
                userId = snippet.userId, id = null, title = "${snippet.title} (Copy)", content = snippet.content,
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
fun SnippetDetailScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onDuplicated: (String) -> Unit,
    viewModel: SnippetDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

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
                    Text("This snippet could not be found.", color = DevLedgerColors.TextMuted)
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
                        MetaItem(Icons.Default.Folder, "Folder", state.folderName ?: "None")
                        MetaItem(Icons.Default.CalendarToday, "Created", formatDate(snippet.createdAt))
                        MetaItem(Icons.Default.Update, "Updated", formatDate(snippet.updatedAt))
                        MetaItem(Icons.Default.Person, "Creator", "You")
                    }

                    Spacer(Modifier.height(24.dp))

                    Text("TAGS", color = DevLedgerColors.TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        snippet.tagsCsv.split(",").map(String::trim).filter(String::isNotEmpty).forEach { tag ->
                            TagBadge(tag)
                            Spacer(Modifier.width(8.dp))
                        }
                        AddTagButton { /* Read-only here */ }
                    }

                    Spacer(Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("CODE", color = DevLedgerColors.TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { clipboard.setText(AnnotatedString(snippet.content)) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = DevLedgerColors.AccentBlue, modifier = Modifier.size(18.dp))
                            }
                            Text("Copy", color = DevLedgerColors.AccentBlue, fontSize = 12.sp)
                            Spacer(Modifier.width(16.dp))
                            Icon(Icons.Default.Fullscreen, contentDescription = "Fullscreen", tint = DevLedgerColors.TextMuted, modifier = Modifier.size(18.dp))
                        }
                    }
                    
                    Spacer(Modifier.height(8.dp))
                    
                    CodeViewer(snippet.content)

                    Spacer(Modifier.height(24.dp))

                    Text("DESCRIPTION", color = DevLedgerColors.TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        snippet.description ?: "No description provided.",
                        color = DevLedgerColors.TextSecondary,
                        fontSize = 14.sp
                    )

                    Spacer(Modifier.height(24.dp))

                    Text("STATS", color = DevLedgerColors.TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard(Icons.Default.Visibility, "Views", "156", Modifier.weight(1f))
                        StatCard(Icons.Default.Code, "Uses", "32", Modifier.weight(1f))
                        StatCard(Icons.Default.StarBorder, "Favorites", "12", Modifier.weight(1f))
                        StatCard(Icons.Default.History, "Last Used", "Just now", Modifier.weight(1f))
                    }

                    Spacer(Modifier.height(24.dp))

                    Text("ACTION", color = DevLedgerColors.TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ActionBtn(Icons.Default.Share, "Share", Modifier.weight(1f)) {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "${snippet.title}\n\n${snippet.content}")
                            }
                            context.startActivity(Intent.createChooser(intent, "Share snippet"))
                        }
                        ActionBtn(Icons.Default.ContentCopy, "Duplicate", Modifier.weight(1f)) { viewModel.duplicate(onDuplicated) }
                        ActionBtn(Icons.Default.FileDownload, "Export", Modifier.weight(1f)) { /* Export */ }
                        ActionBtn(Icons.Default.Delete, "Delete", Modifier.weight(1f), isDestructive = true) { viewModel.archive(); onBack() }
                    }
                    
                    Spacer(Modifier.height(48.dp))
                }
            }
        }
    }
}

@Composable
private fun DetailTopBar(onBack: () -> Unit, onEdit: () -> Unit, isFavourite: Boolean, onToggleFavourite: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
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
                contentDescription = "Favourite",
                tint = if (isFavourite) DevLedgerColors.AccentBlue else Color.White
            )
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color.White)
        }
        IconButton(onClick = { /* Menu */ }) {
            Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.White)
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
private fun TagBadge(text: String) {
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
        }
    }
}

@Composable
private fun AddTagButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(DevLedgerColors.SurfaceCardAlt)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text("+ Add Tag", color = DevLedgerColors.AccentBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}