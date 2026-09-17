package com.devledger.app.ui.screens.sync

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.devledger.app.data.local.dao.SyncLogDao
import com.devledger.app.data.local.entity.FolderEntity
import com.devledger.app.data.local.entity.SnippetEntity
import com.devledger.app.data.local.entity.SyncLogEntity
import com.devledger.app.data.local.entity.TransactionEntity
import com.devledger.app.data.local.entity.UserTagEntity
import com.devledger.app.data.repository.*
import com.devledger.app.sync.SyncScheduler
import com.devledger.app.sync.SyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class SyncRunState { IDLE, QUEUED, RUNNING, SUCCEEDED, FAILED, CANCELLED }

data class QueueItem(val id: String, val label: String, val entityType: String, val direction: String) // direction: "UPLOAD" | "DOWNLOAD"

data class SyncUiState(
    val totalSnippets: Int = 0,
    val totalFolders: Int = 0,
    val totalTags: Int = 0,
    val totalTransactions: Int = 0,
    val uploadQueue: List<QueueItem> = emptyList(),
    val downloadInProgress: Boolean = false,
    val recentLog: List<SyncLogEntity> = emptyList(),
    val wifiOnlySync: Boolean = false,
    val chargingOnlySync: Boolean = false,
    val autoSyncEnabled: Boolean = true,
    val syncFrequencyMinutes: Int = 15,
    val runState: SyncRunState = SyncRunState.IDLE,
    val progress: Float = 0f,
    val processedItems: Int = 0,
    val totalItems: Int = 0,
    val currentEntity: String? = null,
    val lastCompletedAt: Long? = null,
    val error: String? = null,
) {
    val completedCount: Int get() = recentLog.count { it.status == "COMPLETED" }
    val errorCount: Int get() = recentLog.count { it.status == "FAILED" }
    val isSyncing: Boolean get() = runState == SyncRunState.QUEUED || runState == SyncRunState.RUNNING
    val isUpToDate: Boolean get() = uploadQueue.isEmpty() && runState != SyncRunState.FAILED
}

/** Backs the Sync Status screen: live upload queue from RoomDB PENDING rows, WorkManager
 *  progress for the run in flight, a persisted completed/error log (Google Developers,
 *  n.d.b; Google Developers, n.d.c), and the sync preferences that drive [SyncScheduler]. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
/**
 * Reference scope: UI and state-holder separation.
 * (Google Developers, n.d.a; Google Developers, n.d.f). See REFERENCES-CODE.md for full entries.
 */
class SyncViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository,
    private val snippetRepository: SnippetRepository,
    private val transactionRepository: TransactionRepository,
    private val folderRepository: FolderRepository,
    private val userTagRepository: UserTagRepository,
    private val syncLogDao: SyncLogDao,
) : ViewModel() {

    private val workManager = WorkManager.getInstance(context)
    private val _uiState = MutableStateFlow(SyncUiState())
    val uiState = _uiState.asStateFlow()

    private val userId: Flow<String?> = authRepository.currentUser.map { it?.userId }

    private val snippets = userId.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else snippetRepository.observeSnippets("") }
    private val transactions = userId.flatMapLatest { id -> if (id == null) flowOf<List<TransactionEntity>>(emptyList()) else transactionRepository.observeAllForUser(id) }
    private val folders = userId.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else folderRepository.observeFolders() }
    private val tags = userId.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else userTagRepository.observeTags() }
    private val syncLog = userId.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else syncLogDao.observeForUser(id) }

    init {
        observeLocalData()
        observeImmediateSyncWork()
        observeSettings()
    }

    private fun observeLocalData() {
        viewModelScope.launch {
            combine(
                snippets, transactions, folders, tags, syncLog, 
                _uiState.map { it.downloadInProgress }.distinctUntilChanged()
            ) { args: Array<Any> ->
                @Suppress("UNCHECKED_CAST")
                DataSnapshot(
                    snippets = args[0] as List<SnippetEntity>,
                    transactions = args[1] as List<TransactionEntity>,
                    folders = args[2] as List<FolderEntity>,
                    tags = args[3] as List<UserTagEntity>,
                    log = args[4] as List<SyncLogEntity>,
                    downloading = args[5] as Boolean
                )
            }.collect { data ->
                val uploadQueue = mutableListOf<QueueItem>()
                
                uploadQueue.addAll(data.snippets.filter { it.syncStatus == "PENDING" }
                    .map { QueueItem(it.snippetId, "Snippet: \"${it.title}\"", "Snippet", "UPLOAD") })
                
                uploadQueue.addAll(data.transactions.filter { it.syncStatus == "PENDING" }
                    .map { QueueItem(it.transactionId, "${it.type}: ${it.category}", "Transaction", "UPLOAD") })
                
                uploadQueue.addAll(data.folders.filter { it.syncStatus == "PENDING" }
                    .map { QueueItem(it.folderId, "Folder: ${it.name}", "Folder", "UPLOAD") })
                
                uploadQueue.addAll(data.tags.filter { it.syncStatus == "PENDING" }
                    .map { QueueItem(it.tagId, "Tag: ${it.name}", "Tag", "UPLOAD") })

                val downloadQueue = if (data.downloading) {
                    listOf(QueueItem("dl-1", "Remote Updates", "Sync", "DOWNLOAD"))
                } else emptyList()

                _uiState.update {
                    it.copy(
                        totalSnippets = data.snippets.count { it.syncStatus == "SYNCED" },
                        totalFolders = data.folders.count { it.syncStatus == "SYNCED" },
                        totalTags = data.tags.count { it.syncStatus == "SYNCED" },
                        totalTransactions = data.transactions.count { it.syncStatus == "SYNCED" },
                        uploadQueue = uploadQueue + downloadQueue,
                        recentLog = data.log,
                    )
                }
            }
        }
    }

    private data class DataSnapshot(
        val snippets: List<SnippetEntity>,
        val transactions: List<TransactionEntity>,
        val folders: List<FolderEntity>,
        val tags: List<UserTagEntity>,
        val log: List<SyncLogEntity>,
        val downloading: Boolean
    )

    private fun observeSettings() {
        viewModelScope.launch {
            combine(
                settingsRepository.wifiOnlySync, settingsRepository.chargingOnlySync,
                settingsRepository.autoSyncEnabled, settingsRepository.syncFrequencyMinutes,
            ) { wifiOnly, chargingOnly, autoSync, frequency -> arrayOf(wifiOnly, chargingOnly, autoSync, frequency) }
                .collect { (wifiOnly, chargingOnly, autoSync, frequency) ->
                    _uiState.update {
                        it.copy(
                            wifiOnlySync = wifiOnly as Boolean, chargingOnlySync = chargingOnly as Boolean,
                            autoSyncEnabled = autoSync as Boolean, syncFrequencyMinutes = frequency as Int,
                        )
                    }
                }
        }
    }

    private fun observeImmediateSyncWork() {
        viewModelScope.launch {
            workManager.getWorkInfosForUniqueWorkLiveData(IMMEDIATE_WORK_NAME).asFlow().collect { workInfos ->
                val workInfo = workInfos.lastOrNull()
                if (workInfo == null) _uiState.update { it.copy(runState = SyncRunState.IDLE) } else applyWorkInfo(workInfo)
            }
        }
    }

    private fun applyWorkInfo(workInfo: WorkInfo) {
        val state = when (workInfo.state) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> SyncRunState.QUEUED
            WorkInfo.State.RUNNING -> SyncRunState.RUNNING
            WorkInfo.State.SUCCEEDED -> SyncRunState.SUCCEEDED
            WorkInfo.State.FAILED -> SyncRunState.FAILED
            WorkInfo.State.CANCELLED -> SyncRunState.CANCELLED
        }
        val processed = workInfo.progress.getInt(SyncWorker.KEY_PROCESSED_ITEMS, 0)
        val total = workInfo.progress.getInt(SyncWorker.KEY_TOTAL_ITEMS, 0)
        val progress = when { state == SyncRunState.SUCCEEDED -> 1f; total > 0 -> (processed.toFloat() / total.toFloat()).coerceIn(0f, 1f); else -> 0f }
        val completedAt = workInfo.outputData.getLong(SyncWorker.KEY_COMPLETED_AT, 0L).takeIf { it > 0L }
        val error = workInfo.outputData.getString(SyncWorker.KEY_SYNC_ERROR)
        val entity = workInfo.progress.getString(SyncWorker.KEY_CURRENT_ENTITY)

        _uiState.update {
            it.copy(
                runState = state, progress = progress, processedItems = processed, totalItems = total,
                currentEntity = entity,
                lastCompletedAt = completedAt ?: it.lastCompletedAt, error = error,
                downloadInProgress = entity == "Downloading"
            )
        }
    }

    fun syncNow() {
        if (_uiState.value.isSyncing) return
        _uiState.update { it.copy(runState = SyncRunState.QUEUED, progress = 0f, processedItems = 0, totalItems = 0, currentEntity = null, error = null) }
        SyncScheduler.syncNow(context, _uiState.value.wifiOnlySync, _uiState.value.chargingOnlySync)
    }

    fun retryFailed() = syncNow()

    fun onAutoSyncToggle(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoSyncEnabled(enabled)
            if (enabled) SyncScheduler.schedulePeriodicSync(context, _uiState.value.wifiOnlySync, _uiState.value.syncFrequencyMinutes, _uiState.value.chargingOnlySync)
            else SyncScheduler.cancelPeriodicSync(context)
        }
    }

    fun onWifiOnlyToggle(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setWifiOnlySync(enabled)
            rescheduleIfAutoSyncOn()
        }
    }

    fun onChargingOnlyToggle(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setChargingOnlySync(enabled)
            rescheduleIfAutoSyncOn()
        }
    }

    fun onFrequencyChange(minutes: Int) {
        viewModelScope.launch {
            settingsRepository.setSyncFrequencyMinutes(minutes)
            rescheduleIfAutoSyncOn()
        }
    }

    private suspend fun rescheduleIfAutoSyncOn() {
        val state = _uiState.value
        if (state.autoSyncEnabled) SyncScheduler.schedulePeriodicSync(context, state.wifiOnlySync, state.syncFrequencyMinutes, state.chargingOnlySync)
    }

    private companion object { const val IMMEDIATE_WORK_NAME = "${SyncWorker.WORK_NAME}_immediate" }
}
