package com.devledger.app.ui.screens.profile

import com.devledger.app.util.localization.UiText

import com.devledger.app.R

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devledger.app.data.local.entity.UserEntity
import com.devledger.app.data.repository.AuthRepository
import com.devledger.app.data.repository.SnippetRepository
import com.devledger.app.data.repository.TransactionRepository
import com.devledger.app.util.CurrencyConverter
import com.devledger.app.util.DevLedgerResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val user: UserEntity? = null,
    val editableFullName: String = "",
    val totalSnippets: Int = 0,
    val totalFolders: Int = 0,
    val totalTags: Int = 0,
    val totalIncome: Double = 0.0,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isEditing: Boolean = false,
    val error: UiText? = null,
    val message: UiText? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    snippetRepository: SnippetRepository,
    transactionRepository: TransactionRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState = _uiState.asStateFlow()

    private val transactions =
        authRepository.currentUser.flatMapLatest { user ->
            if (user == null) {
                flowOf(emptyList())
            } else {
                transactionRepository.observeAllForUser(user.userId)
            }
        }

    init {
        viewModelScope.launch {
            combine(
                authRepository.currentUser,
                snippetRepository.observeSnippets(""),
                transactions,
            ) { user, snippets, transactions ->
                val currency = user?.baseCurrency ?: "ZAR"

                val totalIncome = transactions
                    .filter {
                        it.type.equals(
                            "INCOME",
                            ignoreCase = true,
                        )
                    }
                    .sumOf {
                        CurrencyConverter.convert(
                            amount = it.amount,
                            from = it.currency,
                            to = currency,
                        )
                    }

                val folderCount = snippets
                    .mapNotNull { it.folderId }
                    .distinct()
                    .size

                val tagCount = snippets
                    .flatMap { snippet ->
                        snippet.tagsCsv
                            .split(",")
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                    }
                    .distinctBy { it.lowercase() }
                    .size

                ProfileStatistics(
                    user = user,
                    totalSnippets = snippets.size,
                    totalFolders = folderCount,
                    totalTags = tagCount,
                    totalIncome = totalIncome,
                )
            }.collect { statistics ->
                _uiState.update { current ->
                    current.copy(
                        user = statistics.user,
                        editableFullName = if (current.isEditing) {
                            current.editableFullName
                        } else {
                            statistics.user?.fullName.orEmpty()
                        },
                        totalSnippets = statistics.totalSnippets,
                        totalFolders = statistics.totalFolders,
                        totalTags = statistics.totalTags,
                        totalIncome = statistics.totalIncome,
                    )
                }
            }
        }

        refreshProfile()
    }

    fun refreshProfile() {
        if (_uiState.value.isSaving) return

        _uiState.update {
            it.copy(
                isLoading = true,
                error = null,
                message = null,
            )
        }

        viewModelScope.launch {
            when (val result = authRepository.refreshProfile()) {
                is DevLedgerResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = null,
                        )
                    }
                }

                is DevLedgerResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = UiText.Resource(R.string.lang_profile_load_failed),
                        )
                    }
                }
            }
        }
    }

    fun startEditing() {
        val user = _uiState.value.user ?: return

        _uiState.update {
            it.copy(
                editableFullName = user.fullName,
                isEditing = true,
                error = null,
                message = null,
            )
        }
    }

    fun updateFullName(value: String) {
        if (!_uiState.value.isEditing) return

        _uiState.update {
            it.copy(
                editableFullName = value.take(100),
                error = null,
                message = null,
            )
        }
    }

    fun cancelEditing() {
        _uiState.update {
            it.copy(
                editableFullName = it.user?.fullName.orEmpty(),
                isEditing = false,
                isSaving = false,
                error = null,
            )
        }
    }

    fun saveProfile() {
        val current = _uiState.value
        val cleanedName = current.editableFullName.trim()

        if (cleanedName.isBlank()) {
            _uiState.update {
                it.copy(error = UiText.Resource(R.string.lang_full_name_is_required))
            }
            return
        }

        if (
            current.isSaving ||
            cleanedName == current.user?.fullName
        ) {
            if (cleanedName == current.user?.fullName) {
                cancelEditing()
            }
            return
        }

        _uiState.update {
            it.copy(
                isSaving = true,
                error = null,
                message = null,
            )
        }

        viewModelScope.launch {
            when (
                val result = authRepository.updateProfile(
                    fullName = cleanedName,
                )
            ) {
                is DevLedgerResult.Success -> {
                    _uiState.update {
                        it.copy(
                            user = result.data,
                            editableFullName = result.data.fullName,
                            isSaving = false,
                            isEditing = false,
                            message = UiText.Resource(R.string.lang_profile_updated_successfully),
                        )
                    }
                }

                is DevLedgerResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            error = UiText.Resource(R.string.lang_your_profile_could_not_be_updated_check_your_connection_and_),
                        )
                    }
                }
            }
        }
    }
}

private data class ProfileStatistics(
    val user: UserEntity?,
    val totalSnippets: Int,
    val totalFolders: Int,
    val totalTags: Int,
    val totalIncome: Double,
)
