package com.devledger.app.ui.screens.sync

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.lifecycle.asFlow
import com.devledger.app.data.local.entity.TransactionEntity
import com.devledger.app.data.repository.AuthRepository
import com.devledger.app.data.repository.SettingsRepository
import com.devledger.app.data.repository.SnippetRepository
import com.devledger.app.data.repository.TransactionRepository
import com.devledger.app.sync.SyncScheduler
import com.devledger.app.sync.SyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SyncRunState {
    IDLE,
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
}

data class SyncUiState(
    val totalSnippets: Int = 0,
    val totalFolders: Int = 0,
    val totalTags: Int = 0,
    val totalTransactions: Int = 0,
    val pendingSnippets: Int = 0,
    val pendingTransactions: Int = 0,
    val wifiOnlySync: Boolean = false,
    val runState: SyncRunState = SyncRunState.IDLE,
    val progress: Float = 0f,
    val processedItems: Int = 0,
    val totalItems: Int = 0,
    val currentEntity: String? = null,
    val lastCompletedAt: Long? = null,
    val error: String? = null,
) {
    val totalPending: Int
        get() = pendingSnippets + pendingTransactions

    val isSyncing: Boolean
        get() = runState == SyncRunState.QUEUED ||
                runState == SyncRunState.RUNNING
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SyncViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    authRepository: AuthRepository,
    snippetRepository: SnippetRepository,
    transactionRepository: TransactionRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val workManager = WorkManager.getInstance(context)

    private val _uiState = MutableStateFlow(SyncUiState())
    val uiState = _uiState.asStateFlow()

    private val transactions =
        authRepository.currentUser.flatMapLatest { user ->
            if (user == null) {
                flowOf(emptyList<TransactionEntity>())
            } else {
                transactionRepository.observeAllForUser(user.userId)
            }
        }

    init {
        observeLocalData(
            authRepository = authRepository,
            snippetRepository = snippetRepository,
            settingsRepository = settingsRepository,
        )

        observeImmediateSyncWork()
    }

    private fun observeLocalData(
        authRepository: AuthRepository,
        snippetRepository: SnippetRepository,
        settingsRepository: SettingsRepository,
    ) {
        viewModelScope.launch {
            combine(
                authRepository.currentUser,
                snippetRepository.observeSnippets(""),
                transactions,
                settingsRepository.wifiOnlySync,
            ) { _, snippets, transactions, wifiOnly ->
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

                LocalSyncStatistics(
                    totalSnippets = snippets.size,
                    totalFolders = folderCount,
                    totalTags = tagCount,
                    totalTransactions = transactions.size,
                    pendingSnippets = snippets.count {
                        it.syncStatus.equals(
                            "PENDING",
                            ignoreCase = true,
                        )
                    },
                    pendingTransactions = transactions.count {
                        it.syncStatus.equals(
                            "PENDING",
                            ignoreCase = true,
                        )
                    },
                    wifiOnlySync = wifiOnly,
                )
            }.collect { statistics ->
                _uiState.update {
                    it.copy(
                        totalSnippets = statistics.totalSnippets,
                        totalFolders = statistics.totalFolders,
                        totalTags = statistics.totalTags,
                        totalTransactions =
                            statistics.totalTransactions,
                        pendingSnippets =
                            statistics.pendingSnippets,
                        pendingTransactions =
                            statistics.pendingTransactions,
                        wifiOnlySync =
                            statistics.wifiOnlySync,
                    )
                }
            }
        }
    }

    private fun observeImmediateSyncWork() {
        viewModelScope.launch {
            workManager
                .getWorkInfosForUniqueWorkLiveData(
                    IMMEDIATE_WORK_NAME
                )
                .asFlow()
                .collect { workInfos ->
                    val workInfo = workInfos.lastOrNull()

                    if (workInfo == null) {
                        _uiState.update {
                            it.copy(
                                runState = SyncRunState.IDLE
                            )
                        }
                    } else {
                        applyWorkInfo(workInfo)
                    }
                }
        }
    }

    private fun applyWorkInfo(
        workInfo: WorkInfo,
    ) {
        val state = when (workInfo.state) {
            WorkInfo.State.ENQUEUED,
            WorkInfo.State.BLOCKED,
                -> SyncRunState.QUEUED

            WorkInfo.State.RUNNING ->
                SyncRunState.RUNNING

            WorkInfo.State.SUCCEEDED ->
                SyncRunState.SUCCEEDED

            WorkInfo.State.FAILED ->
                SyncRunState.FAILED

            WorkInfo.State.CANCELLED ->
                SyncRunState.CANCELLED
        }

        val processed = workInfo.progress.getInt(
            SyncWorker.KEY_PROCESSED_ITEMS,
            0,
        )

        val total = workInfo.progress.getInt(
            SyncWorker.KEY_TOTAL_ITEMS,
            0,
        )

        val progress = when {
            state == SyncRunState.SUCCEEDED -> 1f
            total > 0 -> {
                (processed.toFloat() / total.toFloat())
                    .coerceIn(0f, 1f)
            }
            else -> 0f
        }

        val completedAt = workInfo.outputData.getLong(
            SyncWorker.KEY_COMPLETED_AT,
            0L,
        ).takeIf { it > 0L }

        val error = workInfo.outputData.getString(
            SyncWorker.KEY_SYNC_ERROR
        )

        _uiState.update {
            it.copy(
                runState = state,
                progress = progress,
                processedItems = processed,
                totalItems = total,
                currentEntity = workInfo.progress.getString(
                    SyncWorker.KEY_CURRENT_ENTITY
                ),
                lastCompletedAt =
                    completedAt ?: it.lastCompletedAt,
                error = error,
            )
        }
    }

    fun syncNow() {
        if (_uiState.value.isSyncing) return

        _uiState.update {
            it.copy(
                runState = SyncRunState.QUEUED,
                progress = 0f,
                processedItems = 0,
                totalItems = 0,
                currentEntity = null,
                error = null,
            )
        }

        SyncScheduler.syncNow(
            context = context,
            wifiOnly = _uiState.value.wifiOnlySync,
        )
    }

    fun retrySync() {
        syncNow()
    }

    private data class LocalSyncStatistics(
        val totalSnippets: Int,
        val totalFolders: Int,
        val totalTags: Int,
        val totalTransactions: Int,
        val pendingSnippets: Int,
        val pendingTransactions: Int,
        val wifiOnlySync: Boolean,
    )

    companion object {
        private const val IMMEDIATE_WORK_NAME =
            "${SyncWorker.WORK_NAME}_immediate"
    }
}