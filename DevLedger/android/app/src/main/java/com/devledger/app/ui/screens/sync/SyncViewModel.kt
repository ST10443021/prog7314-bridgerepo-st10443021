package com.devledger.app.ui.screens.sync

import com.devledger.app.util.localization.UiText

import com.devledger.app.R

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.devledger.app.data.local.dao.*
import com.devledger.app.data.local.entity.*
import com.devledger.app.data.repository.AuthRepository
import com.devledger.app.data.repository.SettingsRepository
import com.devledger.app.data.repository.SyncConflictRepository
import com.devledger.app.sync.SyncScheduler
import com.devledger.app.sync.SyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// Reference scope: Background work with WorkManager. (Google Developers, n.d.c). See REFERENCES-CODE.md for full entries.

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SyncViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository,
    private val snippetDao: SnippetDao,
    private val transactionDao: TransactionDao,
    private val folderDao: FolderDao,
    private val userTagDao: UserTagDao,
    private val deletedDao: DeletedDao,
    private val syncLogDao: SyncLogDao,
    private val conflictRepository: SyncConflictRepository,
) : ViewModel() {
    private val manager = WorkManager.getInstance(context)
    private val _uiState = MutableStateFlow(SyncUiState())
    val uiState = _uiState.asStateFlow()

    private data class Snapshot(val userId: String? = null,
        val snippets: List<SnippetEntity> = emptyList(), val transactions: List<TransactionEntity> = emptyList(),
        val folders: List<FolderEntity> = emptyList(), val tags: List<UserTagEntity> = emptyList(),
        val deletions: List<DeletedEntity> = emptyList(), val log: List<SyncLogEntity> = emptyList())

    // One account owns the whole combined snapshot; switching accounts cancels all
    // old subscriptions together rather than mixing independently switched flows.
    private val snapshots = authRepository.currentUser.map { it?.userId }.distinctUntilChanged().flatMapLatest { user ->
        if (user == null) flowOf(Snapshot()) else combine(snippetDao.observeForSync(user),
            transactionDao.observeAllForUser(user), folderDao.observeForUser(user), userTagDao.observeForUser(user),
            deletedDao.observePendingForUser(user), syncLogDao.observeForUser(user)) { args: Array<Any> ->
            @Suppress("UNCHECKED_CAST")
            Snapshot(user, args[0] as List<SnippetEntity>, args[1] as List<TransactionEntity>,
                args[2] as List<FolderEntity>, args[3] as List<UserTagEntity>, args[4] as List<DeletedEntity>,
                args[5] as List<SyncLogEntity>)
        }.onStart { emit(Snapshot(user)) }
    }

    init {
        viewModelScope.launch {
            combine(snapshots,
                manager.getWorkInfosForUniqueWorkLiveData("${SyncWorker.WORK_NAME}_immediate").asFlow(),
                manager.getWorkInfosForUniqueWorkLiveData(SyncWorker.WORK_NAME).asFlow()
            ) { data, immediate, periodic -> Triple(data, immediate, periodic) }.collect { (data, immediate, periodic) ->
                val queue = SyncStatusModel.queue(data.snippets, data.transactions, data.folders, data.tags, data.deletions)
                val conflicts = data.snippets.filter { it.syncStatus == SyncStatus.CONFLICT }
                val latest = data.log.maxByOrNull { it.timestamp }
                fun mapWork(info: WorkInfo, manual: Boolean) = SyncWorkSnapshot(
                    info.progress.getString(SyncWorker.KEY_USER_ID), info.state.name, manual,
                    info.progress.getLong(SyncWorker.KEY_UPDATED_AT, 0),
                    info.progress.getInt(SyncWorker.KEY_PROCESSED_ITEMS, 0),
                    info.progress.getInt(SyncWorker.KEY_TOTAL_ITEMS, 0),
                    info.progress.getString(SyncWorker.KEY_CURRENT_ENTITY))
                val live = liveSyncStatus(data.userId, queue.isNotEmpty(), conflicts.isNotEmpty(), latest?.status,
                    immediate.map { mapWork(it, true) } + periodic.map { mapWork(it, false) })
                val runState = live.state
                val processed = live.processed
                val total = live.total
                val phase = live.phase
                _uiState.update { previous -> previous.copy(
                    accountId = data.userId, isLoaded = data.userId != null,
                    totalSnippets = data.snippets.count { it.syncStatus == SyncStatus.SYNCED },
                    totalTransactions = data.transactions.count { it.syncStatus == SyncStatus.SYNCED },
                    totalFolders = data.folders.count { it.syncStatus == SyncStatus.SYNCED },
                    totalTags = data.tags.count { it.syncStatus == SyncStatus.SYNCED },
                    uploadQueue = queue, conflicts = conflicts, recentLog = data.log,
                    resolvingId = previous.resolvingId.takeIf { previous.accountId == data.userId },
                    actionError = previous.actionError.takeIf { previous.accountId == data.userId },
                    runState = runState, processedItems = processed, totalItems = total, currentEntity = phase,
                    downloadInProgress = runState == SyncRunState.RUNNING && phase == "Downloading",
                    progress = if (total > 0) (processed.toFloat() / total).coerceIn(0f, 1f) else 0f,
                    lastCompletedAt = data.log.filter { it.status == "COMPLETED" }.maxOfOrNull { it.timestamp },
                    error = if (latest?.status == "COMPLETED") null else latest?.errorMessage,
                ) }
            }
        }
        viewModelScope.launch {
            combine(settingsRepository.wifiOnlySync, settingsRepository.chargingOnlySync,
                settingsRepository.autoSyncEnabled, settingsRepository.syncFrequencyMinutes
            ) { wifi, charging, auto, frequency -> arrayOf(wifi, charging, auto, frequency) }.collect { prefs ->
                _uiState.update { it.copy(wifiOnlySync = prefs[0] as Boolean, chargingOnlySync = prefs[1] as Boolean,
                    autoSyncEnabled = prefs[2] as Boolean, syncFrequencyMinutes = prefs[3] as Int) }
            }
        }
    }

    fun keepBothVersions(id: String) {
        val before = _uiState.value
        if (before.resolvingId != null || before.runState == SyncRunState.RUNNING || before.accountId == null) return
        _uiState.update { it.copy(resolvingId = id, actionError = null) }
        viewModelScope.launch {
            try {
                check(authRepository.currentUser.first()?.userId == before.accountId) { "Account changed." }
                conflictRepository.keepBoth(id)
                if (authRepository.currentUser.first()?.userId == before.accountId)
                    SyncScheduler.syncNow(context, before.wifiOnlySync, before.chargingOnlySync)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (_uiState.value.accountId == before.accountId)
                    _uiState.update { it.copy(actionError = UiText.Resource(R.string.lang_recovery_failed_local_draft_retained)) }
            } finally {
                if (_uiState.value.accountId == before.accountId) _uiState.update { it.copy(resolvingId = null) }
            }
        }
    }
    fun syncNow() {
        val state = _uiState.value
        if (state.isSyncing || state.accountId == null || state.resolvingId != null) return
        _uiState.update { it.copy(runState = SyncRunState.QUEUED, actionError = null) }
        SyncScheduler.syncNow(context, state.wifiOnlySync, state.chargingOnlySync)
    }
    fun retryFailed() = syncNow()
    // These only persist the preference. DevLedgerApplication observes the saved values and
    // (re)schedules or cancels the periodic job, so every screen shares one code path.
    fun onAutoSyncToggle(enabled: Boolean) { viewModelScope.launch { settingsRepository.setAutoSyncEnabled(enabled) } }
    fun onWifiOnlyToggle(enabled: Boolean) { viewModelScope.launch {
        settingsRepository.setWifiOnlySync(enabled)
        settingsRepository.pushWifiOnlyToApi(enabled)
    } }
    fun onChargingOnlyToggle(enabled: Boolean) { viewModelScope.launch { settingsRepository.setChargingOnlySync(enabled) } }
    fun onFrequencyChange(minutes: Int) { viewModelScope.launch { settingsRepository.setSyncFrequencyMinutes(minutes) } }
}
