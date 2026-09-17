package com.devledger.app.ui.screens.snippets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devledger.app.data.local.entity.FolderEntity
import com.devledger.app.data.local.entity.SnippetEntity
import com.devledger.app.data.repository.FolderRepository
import com.devledger.app.data.repository.SnippetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import retrofit2.HttpException

enum class SnippetSortOption(val label: String) {
    RECENTLY_UPDATED("Recently Updated"),
    TITLE_AZ("Title A-Z"),
    LANGUAGE("Language"),
}

data class SnippetVaultUiState(
    val query: String = "",
    val allSnippets: List<SnippetEntity> = emptyList(),
    val folders: List<FolderEntity> = emptyList(),
    val selectedFolderId: String? = null, // null = "All Snippets"
    val selectedTag: String? = null,      // null = "All"
    val sortOption: SnippetSortOption = SnippetSortOption.RECENTLY_UPDATED,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
) {
    /** Distinct tags across the unfiltered set, each with its count — the tag chip row
     *  in the Vault mockup ("Kotlin 24", "Compose 18", ...). */
    val availableTags: List<Pair<String, Int>>
        get() = allSnippets
            .flatMap { it.tagsCsv.split(",").map(String::trim).filter(String::isNotEmpty) }
            .groupingBy { it }.eachCount()
            .toList().sortedByDescending { it.second }

    val visibleSnippets: List<SnippetEntity>
        get() {
            var result = allSnippets
            if (selectedFolderId != null) result = result.filter { it.folderId == selectedFolderId }
            if (selectedTag != null) result = result.filter { s ->
                s.tagsCsv.split(",").map(String::trim).any { it.equals(selectedTag, ignoreCase = true) }
            }
            return when (sortOption) {
                SnippetSortOption.RECENTLY_UPDATED -> result.sortedByDescending { it.updatedAt }
                SnippetSortOption.TITLE_AZ -> result.sortedBy { it.title.lowercase() }
                SnippetSortOption.LANGUAGE -> result.sortedBy { it.language.lowercase() }
            }
        }
}

/** Backs the Snippet Vault screen — search, folder/tag filters and sort, all applied
 *  client-side over the RoomDB-backed list so the vault works fully offline. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
/**
 * Reference scope: UI and state-holder separation.
 * (Google Developers, n.d.a; Google Developers, n.d.f). See REFERENCES-CODE.md for full entries.
 */
class SnippetVaultViewModel @Inject constructor(
    private val repository: SnippetRepository,
    private val folderRepository: FolderRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val selectedFolderId = MutableStateFlow<String?>(null)
    private val selectedTag = MutableStateFlow<String?>(null)
    private val sortOption = MutableStateFlow(SnippetSortOption.RECENTLY_UPDATED)
    private val refreshing = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

    private val localSnippets = query.flatMapLatest { repository.observeSnippets(it) }
    private val folders = folderRepository.observeFolders()

    private val dataState = combine(query, localSnippets, folders) { q, snippets, folderList -> Triple(q, snippets, folderList) }
    private val filterState = combine(selectedFolderId, selectedTag, sortOption) { f, t, s -> Triple(f, t, s) }
    private val statusState = combine(refreshing, error) { r, e -> r to e }

    val uiState: StateFlow<SnippetVaultUiState> = combine(dataState, filterState, statusState) { data, filters, status ->
        SnippetVaultUiState(
            query = data.first,
            allSnippets = data.second,
            folders = data.third,
            selectedFolderId = filters.first,
            selectedTag = filters.second,
            sortOption = filters.third,
            isRefreshing = status.first,
            errorMessage = status.second,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SnippetVaultUiState())

    init { refresh() }

    fun onQueryChange(value: String) { query.value = value }
    fun onFolderSelected(folderId: String?) { selectedFolderId.value = folderId }
    fun onTagSelected(tag: String?) { selectedTag.value = tag }
    fun onSortOptionChange(option: SnippetSortOption) { sortOption.value = option }

    fun refresh() {
        if (refreshing.value) return
        refreshing.value = true
        error.value = null
        viewModelScope.launch {
            try {
                repository.refreshFromApi()
                folderRepository.refreshFromApi()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (exception: Exception) {
                error.value = when (exception) {
                    is IOException -> "Could not reach the API. Saved local snippets remain available."
                    is HttpException -> when (exception.code()) {
                        401, 403 -> "Your session was rejected. Sign out and sign in again."
                        else -> "Could not refresh the vault (${exception.code()}). Please retry."
                    }
                    else -> "Could not refresh the vault. Please retry."
                }
            } finally {
                refreshing.value = false
            }
        }
    }

    fun toggleFavourite(snippet: SnippetEntity) = runLocalAction { repository.toggleFavourite(snippet.snippetId, !snippet.isFavourite) }
    fun archive(snippet: SnippetEntity) = runLocalAction { repository.archive(snippet.snippetId, true) }

    private fun runLocalAction(action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                error.value = null
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (exception: Exception) {
                error.value = "Could not update the snippet. Please retry."
            }
        }
    }
}