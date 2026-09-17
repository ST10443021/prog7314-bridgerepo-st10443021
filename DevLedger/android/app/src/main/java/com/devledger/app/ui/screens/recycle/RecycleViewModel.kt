package com.devledger.app.ui.screens.recycle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devledger.app.data.local.entity.SnippetEntity
import com.devledger.app.data.repository.FolderRepository
import com.devledger.app.data.repository.SnippetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class RecycleFilters(
    val query: String = "",
    val language: String? = null,
    val folderId: String? = null,
    val newestFirst: Boolean = true,
)

data class RecycleUiState(
    val snippets: List<SnippetEntity> = emptyList(),
    val totalCount: Int = 0,
    val archivedIds: List<String> = emptyList(),
    val deleting: Boolean = false,
    val refreshing: Boolean = false,
    val languages: List<String> = emptyList(),
    val folders: Map<String, String> = emptyMap(),
    val filters: RecycleFilters = RecycleFilters(),
    val isLoading: Boolean = true,
    val restoringId: String? = null,
    val error: String? = null,
    val message: String? = null,
)

private data class RestoreState(
    val deleting: Boolean = false,
    val refreshing: Boolean = false,
    val id: String? = null,
    val error: String? = null,
    val message: String? = null,
)

/** Uses the existing account-scoped Room flows; restore queues the existing sync worker. */
@HiltViewModel
class RecycleViewModel @Inject constructor(
    private val snippets: SnippetRepository,
    private val folders: FolderRepository,
) : ViewModel() {
    private val filters = MutableStateFlow(RecycleFilters())
    private val restoreState = MutableStateFlow(RestoreState())

    val uiState: StateFlow<RecycleUiState> = combine(
        snippets.observeArchived(), folders.observeFolders(), filters, restoreState,
    ) { archived, folderList, selected, action ->
        val names = folderList.associate { it.folderId to it.name }
        val query = selected.query.trim()
        val visible = archived.filter { snippet ->
            (selected.language == null || snippet.language == selected.language) &&
                    (selected.folderId == null || snippet.folderId == selected.folderId) &&
                    (query.isEmpty() || listOf(
                        snippet.title, snippet.language, snippet.tagsCsv,
                        snippet.description.orEmpty(), names[snippet.folderId].orEmpty(),
                    ).any { it.contains(query, ignoreCase = true) })
        }
        RecycleUiState(
            snippets = if (selected.newestFirst) visible.sortedByDescending { it.updatedAt }
            else visible.sortedBy { it.updatedAt },
            totalCount = archived.size,
            archivedIds = archived.map { it.snippetId },
            deleting = action.deleting,
            refreshing = action.refreshing,
            languages = archived.map { it.language }.distinct().sorted(),
            folders = names,
            filters = selected,
            isLoading = false,
            restoringId = action.id,
            error = action.error,
            message = action.message,
        )
    }.catch { error ->
        if (error is CancellationException) throw error
        emit(RecycleUiState(isLoading = false, error = "Could not load the archive. Reopen this screen to retry."))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecycleUiState())

    init { refresh() }

    fun refresh() {
        if (restoreState.value.refreshing || restoreState.value.deleting || restoreState.value.id != null) return
        restoreState.update { it.copy(refreshing = true, error = null, message = null) }
        viewModelScope.launch {
            try {
                snippets.refreshFromApi()
                // Folder names are optional; cached names remain useful offline.
                folders.refreshFromApi()
                restoreState.update { it.copy(refreshing = false) }
            } catch (cancelled: CancellationException) {
                restoreState.update { it.copy(refreshing = false) }
                throw cancelled
            } catch (error: Exception) {
                restoreState.update { it.copy(refreshing = false,
                    error = "Cloud refresh failed. Showing saved items; check your connection and tap Refresh.") }
            }
        }
    }

    fun deleteArchived(ids: List<String>) {
        if (ids.isEmpty() || restoreState.value.deleting || restoreState.value.id != null || restoreState.value.refreshing) return
        restoreState.value = RestoreState(deleting = true)
        viewModelScope.launch {
            try {
                val count = snippets.deleteArchived(ids)
                restoreState.value = RestoreState(message =
                    if (count == 0) "These snippets are no longer in this account's archive."
                    else "$count snippet(s) removed from this device. Cloud deletion is queued.")
            } catch (cancelled: CancellationException) {
                restoreState.value = RestoreState()
                throw cancelled
            } catch (error: Exception) {
                restoreState.value = RestoreState(error = "Could not delete the snippets. Please retry.")
            }
        }
    }

    fun setQuery(value: String) = filters.update { it.copy(query = value) }
    fun setLanguage(value: String?) = filters.update { it.copy(language = value) }
    fun setFolder(value: String?) = filters.update { it.copy(folderId = value) }
    fun toggleSort() = filters.update { it.copy(newestFirst = !it.newestFirst) }
    fun clearFilters() { filters.value = RecycleFilters() }

    fun restore(id: String) {
        if (restoreState.value.id != null || restoreState.value.deleting || restoreState.value.refreshing) return
        restoreState.value = RestoreState(id = id)
        viewModelScope.launch {
            try {
                val snippet = snippets.getById(id)
                    ?: error("This snippet is no longer available for this account.")
                check(snippet.isArchived) { "This snippet has already been restored." }
                snippets.archive(id, archived = false)
                restoreState.value = RestoreState(message = "Restored to your vault. Cloud sync is queued.")
            } catch (cancelled: CancellationException) {
                restoreState.value = RestoreState()
                throw cancelled
            } catch (error: Exception) {
                restoreState.value = RestoreState(error = error.message ?: "Could not restore the snippet. Please retry.")
            }
        }
    }
}
