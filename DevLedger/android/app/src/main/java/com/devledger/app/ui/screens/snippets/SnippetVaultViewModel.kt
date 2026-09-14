package com.devledger.app.ui.screens.snippets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devledger.app.data.local.entity.SnippetEntity
import com.devledger.app.data.repository.SnippetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import retrofit2.HttpException

data class SnippetVaultUiState(
    val query: String = "",
    val snippets: List<SnippetEntity> = emptyList(),
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SnippetVaultViewModel @Inject constructor(
    private val repository: SnippetRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val refreshing = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

    private val localResults = query.flatMapLatest { search ->
        repository.observeSnippets(search).map { snippets ->
            SnippetVaultUiState(query = search, snippets = snippets)
        }
    }

    val uiState: StateFlow<SnippetVaultUiState> = combine(
        localResults,
        refreshing,
        error,
    ) { local, isRefreshing, errorMessage ->
        local.copy(
            isRefreshing = isRefreshing,
            errorMessage = errorMessage,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        SnippetVaultUiState(),
    )

    init {
        refresh()
    }

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun refresh() {
        if (refreshing.value) return

        refreshing.value = true
        error.value = null

        viewModelScope.launch {
            try {
                repository.refreshFromApi()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (exception: Exception) {
                error.value = when (exception) {
                    is IOException ->
                        "Could not reach the API. Saved local snippets remain available."

                    is HttpException -> when (exception.code()) {
                        401, 403 ->
                            "Your session was rejected. Sign out and sign in again."
                        else ->
                            "Could not refresh the vault (${exception.code()}). Please retry."
                    }

                    else ->
                        "Could not refresh the vault. Please retry."
                }
            } finally {
                refreshing.value = false
            }
        }
    }

    fun toggleFavourite(snippet: SnippetEntity) {
        runLocalAction {
            repository.toggleFavourite(
                snippet.snippetId,
                !snippet.isFavourite,
            )
        }
    }

    fun archive(snippet: SnippetEntity) {
        runLocalAction {
            repository.archive(snippet.snippetId, true)
        }
    }

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