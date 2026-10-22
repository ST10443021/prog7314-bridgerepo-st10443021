package com.devledger.app.ui.screens.snippets

import com.devledger.app.util.localization.UiText

import com.devledger.app.R

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

enum class SnippetSortOption(@androidx.annotation.StringRes val labelRes: Int) {
    RECENTLY_UPDATED(R.string.lang_recently_updated),
    TITLE_AZ(R.string.lang_title_a_z),
    LANGUAGE(R.string.lang_language),
}

data class SnippetVaultUiState(
    val query: String = "",
    val allSnippets: List<SnippetEntity> = emptyList(),
    val folders: List<FolderEntity> = emptyList(),
    val selectedFolderId: String? = null, // null = "All Snippets"
    val selectedTag: String? = null,      // null = "All"
    val sortOption: SnippetSortOption = SnippetSortOption.RECENTLY_UPDATED,
    val isRefreshing: Boolean = false,
    val errorMessage: UiText? = null,
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
    private val error = MutableStateFlow<UiText?>(null)

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
                    is IOException -> UiText.Resource(R.string.lang_could_not_reach_the_api_saved_local_snippets_remain_availabl)
                    is HttpException -> when (exception.code()) {
                        401, 403 -> UiText.Resource(R.string.lang_your_session_was_rejected_sign_out_and_sign_in_again)
                        else -> UiText.Resource(R.string.lang_could_not_refresh_the_vault_please_retry)
                    }
                    else -> UiText.Resource(R.string.lang_could_not_refresh_the_vault_please_retry)
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
                error.value = UiText.Resource(R.string.lang_could_not_update_the_snippet_please_retry)
            }
        }
    }
}
