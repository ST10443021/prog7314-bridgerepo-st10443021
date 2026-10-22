package com.devledger.app.ui.screens.snippets


import com.devledger.app.util.localization.rememberAppStrings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.devledger.app.R
import com.devledger.app.data.local.entity.FolderEntity
import com.devledger.app.ui.components.DevLedgerPrimaryButton
import com.devledger.app.ui.components.DevLedgerTextField
import com.devledger.app.ui.theme.DevLedgerColors
import com.devledger.app.util.SyntaxHighlightTransformation

/**
 * Snippet Editor: Creates or edits a snippet.
 * Key elements: Title, folder, language, tags, syntax-highlighted code with line numbers.
 * Matches Image 3 pixel-accurate design.
 */
@Composable
/**
 * Reference scope: UI and state-holder separation.
 * (Google Developers, n.d.a; Google Developers, n.d.f). See REFERENCES-CODE.md for full entries.
 */
fun SnippetEditorScreen(
    onSaved: () -> Unit,
    onBack: () -> Unit,
    viewModel: SnippetEditorViewModel = hiltViewModel(),
) {
    val strings = rememberAppStrings()
    val state by viewModel.uiState.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val availableTags by viewModel.availableTags.collectAsState()
    var showPreview by remember { mutableStateOf(false) }
    var showCreateTagDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.saved) { if (state.saved) onSaved() }

    Scaffold(
        topBar = {
            EditorTopBar(
                onBack = onBack,
                onSave = viewModel::save,
                isSaving = state.isSaving
            )
        },
        containerColor = DevLedgerColors.BgDeep
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            if (state.isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp))

            Text(
                text = if (state.snippetId == null) strings.get(R.string.lang_new_snippet) else strings.get(R.string.lang_edit_snippet),
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (state.snippetId == null) strings.get(R.string.lang_create_a_new_code_snippet_and_save) else strings.get(R.string.lang_update_your_code_snippet_and_save),
                color = DevLedgerColors.TextMuted,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
            )

            state.errorMessage?.let {
                Text(it.resolve(strings), color = DevLedgerColors.ErrorRed, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 12.dp))
            }

            Text(strings.get(R.string.lang_title), color = DevLedgerColors.TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                DevLedgerTextField(
                    value = state.title,
                    onValueChange = viewModel::onTitleChange,
                    label = "",
                    placeholder = strings.get(R.string.lang_snippet_title),
                )
                Text(
                    text = "${state.title.length}/120",
                    color = DevLedgerColors.TextMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp)
                )
            }

            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FolderSelector(folders, state.folderId, viewModel::onFolderChange, Modifier.weight(1f))
                LanguageSelector(state.language, viewModel::onLanguageChange, Modifier.weight(1f))
            }

            Text(strings.get(R.string.lang_tags_label), color = DevLedgerColors.TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
            TagInputArea(
                tags = state.tags,
                suggestions = availableTags.map { it.name },
                onAddTag = viewModel::addTag,
                onRemoveTag = viewModel::removeTag,
                onCreateNew = { showCreateTagDialog = true },
                modifier = Modifier.padding(bottom = 20.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(strings.get(R.string.lang_code_label), color = DevLedgerColors.TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row {
                    Icon(Icons.Default.WbSunny, contentDescription = null, tint = DevLedgerColors.TextMuted, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(12.dp))
                    Icon(Icons.Default.LibraryBooks, contentDescription = null, tint = DevLedgerColors.TextMuted, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(12.dp))
                    Icon(Icons.Default.Fullscreen, contentDescription = null, tint = DevLedgerColors.TextMuted, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            CodeEditor(state.content, viewModel::onContentChange, Modifier.padding(bottom = 20.dp))

            Text(strings.get(R.string.lang_snippet_description_optional), color = DevLedgerColors.TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
            Box(modifier = Modifier.fillMaxWidth()) {
                DevLedgerTextField(
                    value = state.description,
                    onValueChange = viewModel::onDescriptionChange,
                    label = "",
                    placeholder = strings.get(R.string.lang_describe_what_this_snippet_does),
                    singleLine = false,
                    minLines = 3,
                )
                Text(
                    text = "${state.description.length}/500",
                    color = DevLedgerColors.TextMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 12.dp)
                )
            }

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = DevLedgerColors.SuccessGreen, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(strings.get(R.string.lang_all_changes_saved), color = Color.White, fontSize = 12.sp)
                    Text(strings.get(R.string.lang_just_now), color = DevLedgerColors.TextMuted, fontSize = 11.sp)
                }
                Spacer(Modifier.weight(1f))
                OutlinedButton(
                    onClick = { showPreview = true },
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, DevLedgerColors.Border),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DevLedgerColors.AccentBlue)
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(strings.get(R.string.lang_preview), fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(32.dp))
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

    if (showPreview) {
        Dialog(
            onDismissRequest = { showPreview = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(Modifier.fillMaxSize().background(DevLedgerColors.BgDeep)) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(strings.get(R.string.lang_snippet_preview), color = Color.White, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { showPreview = false }) {
                            Icon(Icons.Default.Close, contentDescription = strings.get(R.string.lang_close), tint = Color.White)
                        }
                    }
                    Text(state.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    CodeEditor(state.content, {}, Modifier.padding(bottom = 20.dp))
                }
            }
        }
    }
}

@Composable
private fun EditorTopBar(onBack: () -> Unit, onSave: () -> Unit, isSaving: Boolean) {
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

        Button(
            onClick = onSave,
            enabled = !isSaving,
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DevLedgerColors.AccentBlue),
            modifier = Modifier.height(36.dp).padding(end = 8.dp)
        ) {
            if (isSaving) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(strings.get(R.string.lang_save), fontSize = 12.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderSelector(folders: List<FolderEntity>, selectedFolderId: String?, onSelected: (String?) -> Unit, modifier: Modifier = Modifier) {
    val strings = rememberAppStrings()
    var expanded by remember { mutableStateOf(false) }
    val selectedName = folders.find { it.folderId == selectedFolderId }?.name ?: strings.get(R.string.lang_no_folder)

    Column(modifier = modifier) {
        Text(strings.get(R.string.lang_folder), color = DevLedgerColors.TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = selectedName, onValueChange = {}, readOnly = true,
                leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null, tint = DevLedgerColors.TextMuted, modifier = Modifier.size(18.dp)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = DevLedgerColors.SurfaceCard,
                    unfocusedContainerColor = DevLedgerColors.SurfaceCard,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = DevLedgerColors.AccentBlue,
                    unfocusedBorderColor = DevLedgerColors.Border
                )
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.background(DevLedgerColors.SurfaceCard)) {
                DropdownMenuItem(text = { Text(strings.get(R.string.lang_no_folder), color = Color.White) }, onClick = { onSelected(null); expanded = false })
                folders.forEach { folder ->
                    DropdownMenuItem(text = { Text(folder.name, color = Color.White) }, onClick = { onSelected(folder.folderId); expanded = false })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageSelector(selectedLanguage: String, onSelected: (String) -> Unit, modifier: Modifier = Modifier) {
    val strings = rememberAppStrings()
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Text(strings.get(R.string.lang_language), color = DevLedgerColors.TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = strings.label(selectedLanguage), onValueChange = {}, readOnly = true,
                leadingIcon = { Icon(Icons.Default.Code, contentDescription = null, tint = DevLedgerColors.TextMuted, modifier = Modifier.size(18.dp)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = DevLedgerColors.SurfaceCard,
                    unfocusedContainerColor = DevLedgerColors.SurfaceCard,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = DevLedgerColors.AccentBlue,
                    unfocusedBorderColor = DevLedgerColors.Border
                )
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.background(DevLedgerColors.SurfaceCard)) {
                SnippetLanguages.forEach { lang ->
                    DropdownMenuItem(text = { Text(strings.label(lang), color = Color.White) }, onClick = { onSelected(lang); expanded = false })
                }
            }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagInputArea(
    tags: List<String>,
    suggestions: List<String>,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    onCreateNew: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = rememberAppStrings()
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tags.forEach { tag ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(DevLedgerColors.SurfaceCard)
                        .border(1.dp, DevLedgerColors.Border, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tag, color = Color.White, fontSize = 12.sp)
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = strings.get(R.string.lang_remove),
                            tint = DevLedgerColors.TextMuted,
                            modifier = Modifier.size(14.dp).clickable { onRemoveTag(tag) }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.width(8.dp))

        Box {
            Surface(
                onClick = { expanded = true },
                shape = RoundedCornerShape(8.dp),
                color = DevLedgerColors.SurfaceCardAlt,
                border = BorderStroke(1.dp, DevLedgerColors.AccentBlue.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(strings.get(R.string.lang_tag), color = DevLedgerColors.AccentBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Icon(Icons.Default.ArrowDropDown, null, tint = DevLedgerColors.AccentBlue, modifier = Modifier.size(16.dp))
                }
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .background(DevLedgerColors.SurfaceCard)
                    .widthIn(min = 180.dp)
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
                        onCreateNew()
                        expanded = false
                    }
                )

                val selectableSuggestions = suggestions.filter { it !in tags }
                if (selectableSuggestions.isNotEmpty()) {
                    HorizontalDivider(color = DevLedgerColors.Border, modifier = Modifier.padding(vertical = 4.dp))
                    selectableSuggestions.forEach { suggestion ->
                        DropdownMenuItem(
                            text = { Text(suggestion, color = Color.White) },
                            onClick = {
                                onAddTag(suggestion)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CodeEditor(code: String, onCodeChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val lineCount = remember(code) { maxOf(1, code.count { it == '\n' } + 1) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 300.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0F1320))
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.width(28.dp)) {
            for (i in 1..maxOf(lineCount, 15)) {
                Text(
                    text = i.toString(),
                    color = Color(0xFF2D3748),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth().padding(end = 8.dp)
                )
            }
        }
        Box(modifier = Modifier.fillMaxWidth()) {
            BasicTextField(
                value = code,
                onValueChange = onCodeChange,
                textStyle = TextStyle(
                    color = DevLedgerColors.TextPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 20.sp
                ),
                cursorBrush = SolidColor(DevLedgerColors.AccentBlue),
                visualTransformation = SyntaxHighlightTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
