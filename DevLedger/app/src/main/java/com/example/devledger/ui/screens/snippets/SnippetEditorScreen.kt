package com.devledger.app.ui.screens.snippets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.devledger.app.R
import com.devledger.app.ui.components.DevLedgerPrimaryButton
import com.devledger.app.ui.components.DevLedgerTextField
import com.devledger.app.ui.components.DevLedgerTopBar
import com.devledger.app.ui.theme.DevLedgerColors

/** Create/edit a snippet — Screen 4 ("Snippet Editor & Tester") in the Part 1 design
 *  doc. Title and code are required; everything else is optional. */
@Composable
fun SnippetEditorScreen(
    onSaved: () -> Unit,
    onBack: () -> Unit,
    viewModel: SnippetEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.saved) {
        if (state.saved) onSaved()
    }

    Column(modifier = Modifier.fillMaxSize().background(DevLedgerColors.BgDeep)) {
        DevLedgerTopBar(
            title = stringResource(if (state.snippetId == null) R.string.editor_title_new else R.string.editor_title_edit),
            onBack = onBack,
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            DevLedgerTextField(
                value = state.title,
                onValueChange = viewModel::onTitleChange,
                label = stringResource(R.string.editor_field_title),
                modifier = Modifier.padding(bottom = 12.dp),
            )
            DevLedgerTextField(
                value = state.language,
                onValueChange = viewModel::onLanguageChange,
                label = stringResource(R.string.editor_field_language),
                modifier = Modifier.padding(bottom = 12.dp),
            )
            DevLedgerTextField(
                value = state.tags,
                onValueChange = viewModel::onTagsChange,
                label = stringResource(R.string.editor_field_tags) + " (comma-separated)",
                modifier = Modifier.padding(bottom = 12.dp),
            )
            DevLedgerTextField(
                value = state.content,
                onValueChange = viewModel::onContentChange,
                label = stringResource(R.string.editor_field_code),
                singleLine = false,
                minLines = 8,
                isError = state.errorMessage != null,
                supportingText = state.errorMessage,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            DevLedgerTextField(
                value = state.description,
                onValueChange = viewModel::onDescriptionChange,
                label = stringResource(R.string.editor_field_description),
                singleLine = false,
                minLines = 2,
                modifier = Modifier.padding(bottom = 20.dp),
            )
            DevLedgerPrimaryButton(
                text = stringResource(R.string.editor_save),
                onClick = viewModel::save,
                loading = state.isSaving,
            )
        }
    }
}
