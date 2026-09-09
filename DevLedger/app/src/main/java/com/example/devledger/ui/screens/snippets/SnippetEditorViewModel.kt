package com.devledger.app.ui.screens.snippets

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devledger.app.data.repository.AuthRepository
import com.devledger.app.data.repository.SnippetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SnippetEditorUiState(
    val snippetId: String? = null,
    val title: String = "",
    val content: String = "",
    val language: String = "Kotlin",
    val description: String = "",
    val tags: String = "",
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val saved: Boolean = false,
)

/** Create/update flow for a single snippet — Screen "Snippet Editor Screen" in the
 *  design doc. Writes land in Room immediately (offline-first) and are queued for the
 *  next [com.devledger.app.sync.SyncWorker] run. */
@HiltViewModel
class SnippetEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val snippetRepository: SnippetRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SnippetEditorUiState(snippetId = savedStateHandle.get<String>("snippetId")),
    )
    val uiState: StateFlow<SnippetEditorUiState> = _uiState.asStateFlow()

    init {
        val id = savedStateHandle.get<String>("snippetId")
        if (id != null) {
            viewModelScope.launch {
                snippetRepository.getById(id)?.let { snippet ->
                    _uiState.value = _uiState.value.copy(
                        title = snippet.title,
                        content = snippet.content,
                        language = snippet.language,
                        description = snippet.description ?: "",
                        tags = snippet.tagsCsv,
                    )
                }
            }
        }
    }

    fun onTitleChange(v: String) { _uiState.value = _uiState.value.copy(title = v, errorMessage = null) }
    fun onContentChange(v: String) { _uiState.value = _uiState.value.copy(content = v, errorMessage = null) }
    fun onLanguageChange(v: String) { _uiState.value = _uiState.value.copy(language = v) }
    fun onDescriptionChange(v: String) { _uiState.value = _uiState.value.copy(description = v) }
    fun onTagsChange(v: String) { _uiState.value = _uiState.value.copy(tags = v) }

    fun save() {
        val state = _uiState.value
        if (state.title.isBlank() || state.content.isBlank()) {
            _uiState.value = state.copy(errorMessage = "Title and code are required.")
            return
        }
        _uiState.value = state.copy(isSaving = true)
        viewModelScope.launch {
            val userId = "local-user" // resolved from AuthRepository.currentUser in a full build; simplified for the prototype
            snippetRepository.createOrUpdate(
                userId = userId,
                id = state.snippetId,
                title = state.title,
                content = state.content,
                language = state.language,
                description = state.description.ifBlank { null },
                tags = state.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() },
            )
            _uiState.value = _uiState.value.copy(isSaving = false, saved = true)
        }
    }
}
