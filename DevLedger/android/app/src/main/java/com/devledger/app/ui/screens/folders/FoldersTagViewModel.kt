package com.devledger.app.ui.screens.folders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.devledger.app.data.local.entity.FolderEntity
import com.devledger.app.data.repository.FolderRepository
import com.devledger.app.data.repository.SnippetRepository
import com.devledger.app.data.repository.UserTagRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * UI state for the Folders & Tags screen.
 *
 * Folder counts and tag counts are calculated from the user's snippets.
 * User-defined tags are also observed independently so that a newly-created
 * tag can be displayed even when it has not yet been assigned to a snippet.
 */
data class FoldersTagsUiState(
    val folders: List<FolderEntity> = emptyList(),
    val folderCounts: Map<String, Int> = emptyMap(),
    val tagCounts: List<Pair<String, Int>> = emptyList(),
    /** Lower-cased tag name to the id of its catalogue row (only tags created in the app have one). */
    val tagIds: Map<String, String> = emptyMap(),
    val totalSnippets: Int = 0,
    val archivedCount: Int = 0,
)

@HiltViewModel
/**
 * Reference scope: UI and state-holder separation.
 * (Google Developers, n.d.a; Google Developers, n.d.f). See REFERENCES-CODE.md for full entries.
 */
class FoldersTagsViewModel @Inject constructor(
    private val folderRepository: FolderRepository,
    private val snippetRepository: SnippetRepository,
    private val userTagRepository: UserTagRepository,
) : ViewModel() {

    /**
     * Combines:
     * - folders stored locally,
     * - snippets stored locally,
     * - user-defined tags stored locally.
     *
     * Room remains the UI source of truth, which keeps this screen usable
     * while offline.
     */
    val uiState: StateFlow<FoldersTagsUiState> = combine(
        folderRepository.observeFolders(),
        snippetRepository.observeSnippets(""),
        userTagRepository.observeTags(),
        snippetRepository.observeArchived(),
    ) { folders, snippets, userTags, archived ->

        /*
         * Existing snippets currently store their assigned tags as CSV.
         * We use those values to calculate how many snippets use each tag.
         */
        val snippetTagNames = snippets.flatMap { snippet ->
            snippet.tagsCsv
                .split(",")
                .map { tag -> tag.trim() }
                .filter { tag -> tag.isNotBlank() }
        }

        /*
         * Count tags case-insensitively.
         *
         * Example:
         * "Android" and "android" are treated as the same tag.
         */
        val snippetTagCounts = snippetTagNames
            .groupingBy { tag ->
                tag.lowercase()
            }
            .eachCount()

        /*
         * Combine:
         * - standalone user-created tags, and
         * - tags already assigned to snippets.
         *
         * This is important because a newly-created tag may initially have
         * zero snippets associated with it.
         */
        val allTagNames = (
                userTags
                    .map { tag -> tag.name.trim() }
                    .filter { name -> name.isNotBlank() } +
                        snippetTagNames
                )
            .distinctBy { name ->
                name.lowercase()
            }

        val tagCounts = allTagNames
            .map { tagName ->

                val snippetCount =
                    snippetTagCounts[tagName.lowercase()] ?: 0

                tagName to snippetCount
            }
            .sortedWith(
                compareByDescending<Pair<String, Int>> {
                    it.second
                }.thenBy {
                    it.first.lowercase()
                }
            )

        /*
         * Calculate the number of snippets contained in each folder.
         */
        val folderCounts = snippets
            .filter { snippet ->
                snippet.folderId != null
            }
            .groupingBy { snippet ->
                snippet.folderId!!
            }
            .eachCount()

        FoldersTagsUiState(
            folders = folders.sortedBy { folder ->
                folder.name.lowercase()
            },
            folderCounts = folderCounts,
            tagCounts = tagCounts,
            tagIds = userTags.associate { tag -> tag.name.trim().lowercase() to tag.tagId },
            totalSnippets = snippets.size,
            archivedCount = archived.size,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(
            stopTimeoutMillis = 5_000
        ),
        initialValue = FoldersTagsUiState(),
    )

    /**
     * Creates a folder through the existing offline-first repository.
     */
    fun createFolder(name: String) {
        val trimmedName = name.trim()

        if (trimmedName.isBlank()) {
            return
        }

        viewModelScope.launch {
            folderRepository.create(trimmedName)
        }
    }

    /**
     * Renames an existing folder.
     */
    fun renameFolder(
        id: String,
        name: String,
    ) {
        val trimmedName = name.trim()

        if (trimmedName.isBlank()) {
            return
        }

        viewModelScope.launch {
            folderRepository.rename(id, trimmedName)
        }
    }

    /**
     * Deletes a folder.
     *
     * reassignToFolderId determines where its existing snippets are moved.
     */
    fun deleteFolder(
        id: String,
        reassignToFolderId: String?,
    ) {
        viewModelScope.launch {
            folderRepository.delete(id, reassignToFolderId)
        }
    }

    /**
     * Renames a tag everywhere: on every snippet that uses it and in the tag catalogue.
     * Both writes are local-first and queue the normal background synchronisation.
     */
    fun renameTag(oldName: String, newName: String) {
        val clean = newName.trim()
        if (clean.isBlank() || ',' in clean) return
        viewModelScope.launch {
            try {
                snippetRepository.renameTag(oldName, clean)
                uiState.value.tagIds[oldName.trim().lowercase()]?.let { userTagRepository.rename(it, clean) }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e("FoldersTagsVM", "Could not rename tag", error)
            }
        }
    }

    /** Deletes a tag from the catalogue and removes it from every snippet that carries it. */
    fun deleteTag(name: String) {
        viewModelScope.launch {
            try {
                snippetRepository.removeTag(name)
                uiState.value.tagIds[name.trim().lowercase()]?.let { userTagRepository.delete(it) }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e("FoldersTagsVM", "Could not delete tag", error)
            }
        }
    }

    /**
     * Creates a new user-defined tag.
     *
     * UserTagRepository saves the tag into Room first and queues the normal
     * DevLedger background synchronisation process.
     */
    fun createTag(name: String) {
        val trimmedName = name.trim()

        if (trimmedName.isBlank()) {
            return
        }

        /*
         * Prevent tags such as:
         *
         * Android
         * android
         * ANDROID
         *
         * from being created as separate entries.
         */
        val duplicateExists = uiState.value.tagCounts.any {
                (existingTagName, _) ->

            existingTagName.equals(
                trimmedName,
                ignoreCase = true,
            )
        }

        if (duplicateExists) {
            return
        }

        viewModelScope.launch {
            userTagRepository.create(
                name = trimmedName,
            )
        }
    }
}