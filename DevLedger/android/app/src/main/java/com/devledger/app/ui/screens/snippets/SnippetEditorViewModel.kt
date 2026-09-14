package com.devledger.app.ui.screens.snippets

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devledger.app.data.repository.AuthRepository
import com.devledger.app.data.repository.SnippetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
    val isLoading: Boolean = false,
    val folderId: String? = null,
)

/**
 * Loads and saves snippets belonging to the signed-in user.
 * Saves to Room first; pending records require cloud synchronisation.
 */
@HiltViewModel
class SnippetEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val snippetRepository: SnippetRepository,
) : ViewModel() {

    private val initialId = savedStateHandle.get<String>("snippetId")

    private val _uiState = MutableStateFlow(
        SnippetEditorUiState(
            snippetId = initialId,
            isLoading = initialId != null,
        ),
    )
    val uiState: StateFlow<SnippetEditorUiState> = _uiState.asStateFlow()

    private var loadFailed = false

    init {
        if (initialId != null) {
            viewModelScope.launch {
                try {
                    check(authRepository.isLoggedIn()) {
                        "Sign in before opening a snippet."
                    }

                    val user = authRepository.currentUser.first()
                        ?: error("Your account could not be loaded. Sign in again.")

                    val snippet = snippetRepository.getById(initialId)
                        ?: error("This snippet could not be found.")

                    check(snippet.userId == user.userId) {
                        "This snippet does not belong to your account."
                    }

                    _uiState.value = _uiState.value.copy(
                        title = snippet.title,
                        content = snippet.content,
                        language = snippet.language,
                        description = snippet.description ?: "",
                        tags = snippet.tagsCsv,
                        folderId = snippet.folderId,
                        isLoading = false,
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    loadFailed = true
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = error.message
                            ?: "Could not load this snippet. Go back and retry.",
                    )
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
    fun onTagsChange(v: String) = edit { it.copy(tags = v) }

    fun save() {
        val state = _uiState.value
        if (state.isLoading || state.isSaving || state.saved || loadFailed) return

        val validationError = when {
            state.title.isBlank() -> "Enter a snippet title."
            state.content.isBlank() -> "Enter some code or configuration content."
            state.language.isBlank() -> "Select a language."
            else -> null
        }

        if (validationError != null) {
            _uiState.value = state.copy(errorMessage = validationError)
            return
        }

        _uiState.value = state.copy(isSaving = true, errorMessage = null)

        viewModelScope.launch {
            try {
                check(authRepository.isLoggedIn()) {
                    "Sign in before saving a snippet."
                }

                val user = authRepository.currentUser.first()
                    ?: error("Your account could not be loaded. Sign in again.")

                val snippet = snippetRepository.createOrUpdate(
                    userId = user.userId,
                    id = state.snippetId,
                    title = state.title,
                    content = state.content,
                    language = state.language,
                    description = state.description.ifBlank { null },
                    tags = state.tags.split(","),
                    folderId = state.folderId,
                )

                _uiState.value = _uiState.value.copy(
                    snippetId = snippet.snippetId,
                    isSaving = false,
                    saved = true,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    errorMessage = error.message
                        ?: "Could not save the snippet. Please retry.",
                )
            }
        }
    }
}