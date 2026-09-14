package com.devledger.app.ui.screens.snippets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.devledger.app.R
import com.devledger.app.data.local.entity.SnippetEntity
import com.devledger.app.ui.components.DevLedgerCard
import com.devledger.app.ui.components.DevLedgerTextField
import com.devledger.app.ui.theme.DevLedgerColors
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton

/** Snippet Vault list: search, favourite toggle and swipe-to-archive — Screen 3 in the
 *  Part 1 design doc. Backed live by Room so it works fully offline. */
@Composable
fun SnippetVaultScreen(
    onOpenSnippet: (String) -> Unit,
    viewModel: SnippetVaultViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(DevLedgerColors.BgDeep).padding(16.dp)) {
        Text(stringResource(R.string.vault_title), color = DevLedgerColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.vault_subtitle), color = DevLedgerColors.TextMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 16.dp))

        TextButton(
            onClick = viewModel::refresh,
            enabled = !state.isRefreshing,
        ) {
            Text(if (state.isRefreshing) "Refreshing..." else "Refresh")
        }

        if (state.isRefreshing) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
            )
        }

        state.errorMessage?.let { message ->
            Text(
                text = message,
                color = DevLedgerColors.ErrorRed,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }

        DevLedgerTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            label = stringResource(R.string.vault_search_hint),
            modifier = Modifier.padding(bottom = 16.dp),
        )

        if (state.snippets.isEmpty() && !state.isRefreshing) {
            Box(modifier = Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.vault_empty), color = DevLedgerColors.TextMuted, fontSize = 13.sp)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.snippets, key = { it.snippetId }) { snippet ->
                    SnippetRow(
                        snippet = snippet,
                        onClick = { onOpenSnippet(snippet.snippetId) },
                        onToggleFavourite = { viewModel.toggleFavourite(snippet) },
                        onArchive = { viewModel.archive(snippet) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SnippetRow(
    snippet: SnippetEntity,
    onClick: () -> Unit,
    onToggleFavourite: () -> Unit,
    onArchive: () -> Unit,
) {
    DevLedgerCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(snippet.title, color = DevLedgerColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(snippet.language, color = DevLedgerColors.AccentBlue, fontSize = 11.sp)
                if (snippet.tagsCsv.isNotBlank()) {
                    Text(snippet.tagsCsv.replace(",", "  ·  "), color = DevLedgerColors.TextMuted, fontSize = 11.sp)
                }
            }
            IconButton(onClick = onToggleFavourite) {
                Icon(
                    imageVector = if (snippet.isFavourite) Icons.Filled.Star else Icons.Outlined.Star,
                    contentDescription = "Favourite",
                    tint = if (snippet.isFavourite) DevLedgerColors.WarningAmber else DevLedgerColors.TextMuted,
                )
            }
            IconButton(onClick = onArchive) {
                Icon(Icons.Filled.Archive, contentDescription = "Archive", tint = DevLedgerColors.TextMuted)
            }
        }
    }
}
