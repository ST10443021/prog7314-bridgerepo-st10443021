package com.devledger.app.ui.screens.snippets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devledger.app.data.local.entity.SnippetEntity
import com.devledger.app.data.repository.SnippetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class SnippetVaultUiState(
    val query: String = "",
    val snippets: List<SnippetEntity> = emptyList(),
)

/** Backs the Snippet Vault list screen: live search over Room plus favourite/archive
 *  actions, matching "Screen 3: Snippet & Config Vault (List)" from the design doc. */
@HiltViewModel
class SnippetVaultViewModel @Inject constructor(
    private val repository: SnippetRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")

    val uiState: StateFlow<SnippetVaultUiState> = query
        .flatMapLatest { q -> repository.observeSnippets(q).map { SnippetVaultUiState(q, it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SnippetVaultUiState())

    fun onQueryChange(value: String) { query.value = value }

    fun toggleFavourite(snippet: SnippetEntity) = viewModelScope.launch {
        repository.toggleFavourite(snippet.snippetId, !snippet.isFavourite)
    }

    fun archive(snippet: SnippetEntity) = viewModelScope.launch {
        repository.archive(snippet.snippetId, true)
    }
}
