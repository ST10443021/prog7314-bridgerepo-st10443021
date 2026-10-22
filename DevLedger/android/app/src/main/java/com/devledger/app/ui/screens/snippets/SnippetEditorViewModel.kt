package com.devledger.app.ui.screens.snippets

import com.devledger.app.util.localization.UiText

import com.devledger.app.R

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devledger.app.data.local.entity.FolderEntity
import com.devledger.app.data.local.entity.UserTagEntity
import com.devledger.app.data.repository.AuthRepository
import com.devledger.app.data.repository.FolderRepository
import com.devledger.app.data.repository.SnippetRepository
import com.devledger.app.data.repository.UserTagRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SnippetEditorUiState(
    val snippetId: String? = null,
    val title: String = "",
    val content: String = "",
    val language: String = "Kotlin",
    val description: String = "",
    val tags: List<String> = emptyList(),
    val folderId: String? = null,
    val isSaving: Boolean = false,
    val errorMessage: UiText? = null,
    val saved: Boolean = false,
    val isLoading: Boolean = false,
)

val SnippetLanguages = listOf("Kotlin", "Java", "Swift", "JavaScript", "TypeScript", "Python", "C#", "SQL", "JSON", "YAML", "Bash", "Other")

/** Loads and saves snippets belonging to the signed-in user. Saves to Room first;
 *  pending records require cloud synchronisation. */
@HiltViewModel
/**
 * Reference scope: UI and state-holder separation.
 * (Google Developers, n.d.a; Google Developers, n.d.f). See REFERENCES-CODE.md for full entries.
 */
class SnippetEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val snippetRepository: SnippetRepository,
    private val folderRepository: FolderRepository,
    private val userTagRepository: UserTagRepository,
) : ViewModel() {

    private val initialId = savedStateHandle.get<String>("snippetId")
    private val _uiState = MutableStateFlow(SnippetEditorUiState(snippetId = initialId, isLoading = initialId != null))
    val uiState: StateFlow<SnippetEditorUiState> = _uiState.asStateFlow()

    val folders: StateFlow<List<FolderEntity>> = folderRepository.observeFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val availableTags: StateFlow<List<UserTagEntity>> = userTagRepository.observeTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var loadFailed = false

    init {
        if (initialId != null) {
            viewModelScope.launch {
                try {
                    check(authRepository.isLoggedIn()) { "Sign in before opening a snippet." }
                    val user = authRepository.currentUser.first() ?: error("Your account could not be loaded. Sign in again.")
                    val snippet = snippetRepository.getById(initialId) ?: error("This snippet could not be found.")
                    check(snippet.userId == user.userId) { "This snippet does not belong to your account." }

                    Log.i(TAG, "Loaded snippet $initialId for user ${user.userId}")
                    _uiState.value = _uiState.value.copy(
                        title = snippet.title,
                        content = snippet.content,
                        language = snippet.language,
                        description = snippet.description ?: "",
                        tags = snippet.tagsCsv.split(",").map(String::trim).filter(String::isNotEmpty),
                        folderId = snippet.folderId,
                        isLoading = false,
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.e(TAG, "Failed to load snippet $initialId", error)
                    loadFailed = true
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = UiText.Resource(R.string.lang_could_not_load_this_snippet_go_back_and_retry))
                }
            }
        }
    }

    private fun edit(change: (SnippetEditorUiState) -> SnippetEditorUiState) {
        val state = _uiState.value
        if (state.isLoading || state.isSaving || state.saved || loadFailed) return
        _uiState.value = change(state).copy(errorMessage = null)
    }

    fun onTitleChange(v: String) = edit { it.copy(title = v) }
    fun onContentChange(v: String) = edit { it.copy(content = v) }
    fun onLanguageChange(v: String) = edit { it.copy(language = v) }
    fun onDescriptionChange(v: String) = edit { it.copy(description = v) }
    fun onFolderChange(folderId: String?) = edit { it.copy(folderId = folderId) }

    fun addTag(tag: String) = edit {
        val trimmed = tag.trim()
        if (trimmed.isEmpty() || it.tags.any { existing -> existing.equals(trimmed, ignoreCase = true) }) it
        else {
            // Asynchronously ensure the tag is saved to the global repository
            viewModelScope.launch {
                userTagRepository.create(trimmed)
            }
            it.copy(tags = it.tags + trimmed)
        }
    }
    fun removeTag(tag: String) = edit { it.copy(tags = it.tags - tag) }

    fun save() {
        val state = _uiState.value
        if (state.isLoading || state.isSaving || state.saved || loadFailed) return

        val validationError = when {
            state.title.isBlank() -> UiText.Resource(R.string.lang_enter_a_snippet_title)
            state.content.isBlank() -> UiText.Resource(R.string.lang_enter_some_code_or_configuration_content)
            state.language.isBlank() -> UiText.Resource(R.string.lang_select_a_language)
            else -> null
        }
        if (validationError != null) { _uiState.value = state.copy(errorMessage = validationError); return }

        _uiState.value = state.copy(isSaving = true, errorMessage = null)

        viewModelScope.launch {
            try {
                check(authRepository.isLoggedIn()) { "Sign in before saving a snippet." }
                val user = authRepository.currentUser.first() ?: error("Your account could not be loaded. Sign in again.")

                Log.i(TAG, "Saving snippet '${state.title}' (id=${state.snippetId ?: "new"}) for user ${user.userId}")

                val snippet = snippetRepository.createOrUpdate(
                    userId = user.userId, id = state.snippetId, title = state.title, content = state.content,
                    language = state.language, description = state.description.ifBlank { null },
                    tags = state.tags, folderId = state.folderId,
                )
                _uiState.value = _uiState.value.copy(snippetId = snippet.snippetId, isSaving = false, saved = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e(TAG, "Failed to save snippet", error)
                _uiState.value = _uiState.value.copy(isSaving = false, errorMessage = UiText.Resource(R.string.lang_could_not_save_the_snippet_please_retry))
            }
        }
    }

    private companion object { const val TAG = "SnippetEditorViewModel" }
}
