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

/**
 * Programming/configuration languages supported by the snippet language filter.
 *
 * Languages already stored on snippets are also added automatically below,
 * so older/custom language values are never lost from the filter.
 */
private val SUPPORTED_SNIPPET_LANGUAGES = listOf(
    "Bash",
    "C",
    "C#",
    "C++",
    "CSS",
    "Dart",
    "Dockerfile",
    "Go",
    "Groovy",
    "HTML",
    "Java",
    "JavaScript",
    "JSON",
    "Kotlin",
    "Markdown",
    "PHP",
    "PowerShell",
    "Python",
    "R",
    "Ruby",
    "Rust",
    "SQL",
    "Swift",
    "Terraform",
    "TypeScript",
    "XML",
    "YAML",
)

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

/**
 * Manages archived snippets using the existing account-scoped Room flows.
 *
 * Restore and permanent deletion use the existing SnippetRepository so
 * changes continue through the normal offline-first sync process.
 */
@HiltViewModel
/**
 * Reference scope: UI and state-holder separation.
 * (Google Developers, n.d.a; Google Developers, n.d.f). See REFERENCES-CODE.md for full entries.
 */
class RecycleViewModel @Inject constructor(
    private val snippets: SnippetRepository,
    private val folders: FolderRepository,
) : ViewModel() {

    private val filters = MutableStateFlow(
        RecycleFilters()
    )

    private val restoreState = MutableStateFlow(
        RestoreState()
    )

    val uiState: StateFlow<RecycleUiState> = combine(
        snippets.observeArchived(),
        folders.observeFolders(),
        filters,
        restoreState,
    ) { archived, folderList, selected, action ->

        val folderNames = folderList.associate {
            it.folderId to it.name
        }

        val query = selected.query.trim()

        /*
         * Show every supported language in the dropdown, not only languages
         * that happen to exist in the recycle bin.
         *
         * We also preserve any existing/custom language stored on an
         * archived snippet in case it is not part of the standard list.
         */
        val archivedLanguages = archived
            .map { it.language.trim() }
            .filter { it.isNotBlank() }

        val allLanguages = (
                SUPPORTED_SNIPPET_LANGUAGES +
                        archivedLanguages
                )
            .distinctBy { it.lowercase() }
            .sortedBy { it.lowercase() }

        val visible = archived.filter { snippet ->

            val matchesLanguage =
                selected.language == null ||
                        snippet.language.equals(
                            selected.language,
                            ignoreCase = true,
                        )

            val matchesFolder =
                selected.folderId == null ||
                        snippet.folderId == selected.folderId

            val matchesQuery =
                query.isEmpty() ||
                        listOf(
                            snippet.title,
                            snippet.language,
                            snippet.tagsCsv,
                            snippet.description.orEmpty(),
                            folderNames[snippet.folderId].orEmpty(),
                        ).any { value ->
                            value.contains(
                                query,
                                ignoreCase = true,
                            )
                        }

            matchesLanguage &&
                    matchesFolder &&
                    matchesQuery
        }

        val sortedSnippets =
            if (selected.newestFirst) {
                visible.sortedByDescending {
                    it.updatedAt
                }
            } else {
                visible.sortedBy {
                    it.updatedAt
                }
            }

        RecycleUiState(
            snippets = sortedSnippets,
            totalCount = archived.size,
            archivedIds = archived.map {
                it.snippetId
            },
            deleting = action.deleting,
            refreshing = action.refreshing,
            languages = allLanguages,
            folders = folderNames,
            filters = selected,
            isLoading = false,
            restoringId = action.id,
            error = action.error,
            message = action.message,
        )
    }
        .catch { error ->

            if (error is CancellationException) {
                throw error
            }

            emit(
                RecycleUiState(
                    languages = SUPPORTED_SNIPPET_LANGUAGES
                        .sortedBy { it.lowercase() },
                    isLoading = false,
                    error = "Could not load the archive. Reopen this screen to retry.",
                )
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(
                5_000
            ),
            initialValue = RecycleUiState(
                languages = SUPPORTED_SNIPPET_LANGUAGES
                    .sortedBy { it.lowercase() }
            ),
        )

    init {
        refresh()
    }

    fun refresh() {
        if (
            restoreState.value.refreshing ||
            restoreState.value.deleting ||
            restoreState.value.id != null
        ) {
            return
        }

        restoreState.update {
            it.copy(
                refreshing = true,
                error = null,
                message = null,
            )
        }

        viewModelScope.launch {
            try {
                snippets.refreshFromApi()

                // Cached folder names remain available while offline.
                folders.refreshFromApi()

                restoreState.update {
                    it.copy(
                        refreshing = false
                    )
                }

            } catch (cancelled: CancellationException) {

                restoreState.update {
                    it.copy(
                        refreshing = false
                    )
                }

                throw cancelled

            } catch (error: Exception) {

                restoreState.update {
                    it.copy(
                        refreshing = false,
                        error =
                            "Cloud refresh failed. Showing saved items; check your connection and tap Refresh.",
                    )
                }
            }
        }
    }

    fun deleteArchived(
        ids: List<String>,
    ) {
        if (
            ids.isEmpty() ||
            restoreState.value.deleting ||
            restoreState.value.id != null ||
            restoreState.value.refreshing
        ) {
            return
        }

        restoreState.value = RestoreState(
            deleting = true
        )

        viewModelScope.launch {
            try {
                val count =
                    snippets.deleteArchived(ids)

                restoreState.value = RestoreState(
                    message =
                        if (count == 0) {
                            "These snippets are no longer in this account's archive."
                        } else {
                            "$count snippet(s) removed from this device. Cloud deletion is queued."
                        }
                )

            } catch (cancelled: CancellationException) {

                restoreState.value =
                    RestoreState()

                throw cancelled

            } catch (error: Exception) {

                restoreState.value = RestoreState(
                    error =
                        "Could not delete the snippets. Please retry."
                )
            }
        }
    }

    fun setQuery(
        value: String,
    ) {
        filters.update {
            it.copy(
                query = value
            )
        }
    }

    fun setLanguage(
        value: String?,
    ) {
        filters.update {
            it.copy(
                language = value
            )
        }
    }

    fun setFolder(
        value: String?,
    ) {
        filters.update {
            it.copy(
                folderId = value
            )
        }
    }

    fun toggleSort() {
        filters.update {
            it.copy(
                newestFirst = !it.newestFirst
            )
        }
    }

    fun clearFilters() {
        filters.value =
            RecycleFilters()
    }

    fun restore(
        id: String,
    ) {
        if (
            restoreState.value.id != null ||
            restoreState.value.deleting ||
            restoreState.value.refreshing
        ) {
            return
        }

        restoreState.value =
            RestoreState(id = id)

        viewModelScope.launch {
            try {
                val snippet =
                    snippets.getById(id)
                        ?: error(
                            "This snippet is no longer available for this account."
                        )

                check(snippet.isArchived) {
                    "This snippet has already been restored."
                }

                snippets.archive(
                    id,
                    archived = false,
                )

                restoreState.value =
                    RestoreState(
                        message =
                            "Restored to your vault. Cloud sync is queued."
                    )

            } catch (cancelled: CancellationException) {

                restoreState.value =
                    RestoreState()

                throw cancelled

            } catch (error: Exception) {

                restoreState.value =
                    RestoreState(
                        error =
                            error.message
                                ?: "Could not restore the snippet. Please retry."
                    )
            }
        }
    }
}