package com.devledger.app.ui.screens.sync

import com.devledger.app.util.localization.UiText

import com.devledger.app.data.local.entity.*

// Reference scope: Background work with WorkManager. (Google Developers, n.d.c). See REFERENCES-CODE.md for full entries.

enum class SyncRunState { IDLE, QUEUED, WAITING, RUNNING, SUCCEEDED, FAILED, RETRYING, PARTIAL, CONFLICT, CANCELLED }
data class QueueItem(val id: String, val label: String, val entityType: String, val direction: String)
data class SyncUiState(
    val accountId: String? = null,
    val isLoaded: Boolean = false,
    val totalSnippets: Int = 0, val totalFolders: Int = 0, val totalTags: Int = 0, val totalTransactions: Int = 0,
    val uploadQueue: List<QueueItem> = emptyList(),
    val conflicts: List<SnippetEntity> = emptyList(),
    val resolvingId: String? = null,
    val actionError: UiText? = null,
    val downloadInProgress: Boolean = false,
    val recentLog: List<SyncLogEntity> = emptyList(),
    val wifiOnlySync: Boolean = false, val chargingOnlySync: Boolean = false,
    val autoSyncEnabled: Boolean = true, val syncFrequencyMinutes: Int = 15,
    val runState: SyncRunState = SyncRunState.IDLE,
    val progress: Float = 0f, val processedItems: Int = 0, val totalItems: Int = 0,
    val currentEntity: String? = null, val lastCompletedAt: Long? = null, val error: String? = null,
) {
    val completedCount get() = recentLog.count { it.status == "COMPLETED" }
    val errorCount get() = recentLog.count { it.status != "COMPLETED" }
    val isSyncing get() = runState == SyncRunState.QUEUED || runState == SyncRunState.RUNNING
    val isUpToDate get() = isLoaded && accountId != null && uploadQueue.isEmpty() && conflicts.isEmpty() &&
        runState == SyncRunState.SUCCEEDED && lastCompletedAt != null && error == null && resolvingId == null
}

/** Queue contents come from persisted rows, including archived snippets and all
 * deletion outboxes. Retained acknowledged markers are never pending uploads. */
object SyncStatusModel {
    fun queue(snippets: List<SnippetEntity>, transactions: List<TransactionEntity>,
        folders: List<FolderEntity>, tags: List<UserTagEntity>, deletions: List<DeletedEntity>): List<QueueItem> = buildList {
        snippets.filter { it.syncStatus == SyncStatus.PENDING || it.syncStatus == SyncStatus.DELETE_PENDING }.forEach {
            val deleting = it.syncStatus == SyncStatus.DELETE_PENDING
            add(QueueItem("snippet:${it.snippetId}", if (deleting) it.snippetId else it.title, "snippet", if (deleting) "DELETE" else "UPLOAD"))
        }
        transactions.filter { it.syncStatus == SyncStatus.PENDING }.forEach {
            add(QueueItem("transaction:${it.transactionId}", it.category, "transaction", "UPLOAD"))
        }
        folders.filter { it.syncStatus == SyncStatus.PENDING }.forEach {
            add(QueueItem("folder:${it.folderId}", it.name, "folder", "UPLOAD"))
        }
        tags.filter { it.syncStatus == SyncStatus.PENDING }.forEach {
            add(QueueItem("tag:${it.tagId}", it.name, "tag", "UPLOAD"))
        }
        deletions.filter { it.syncStatus == SyncStatus.PENDING }.forEach {
            if (none { item -> item.id == "${it.entityType}:${it.entityId}" })
                add(QueueItem("${it.entityType}:${it.entityId}", it.entityId, it.entityType, "DELETE"))
        }
    }
    fun stateForLog(status: String?) = when (status) {
        "COMPLETED" -> SyncRunState.SUCCEEDED
        "RETRY" -> SyncRunState.RETRYING
        "PARTIAL" -> SyncRunState.PARTIAL
        "CONFLICT" -> SyncRunState.CONFLICT
        "CANCELLED" -> SyncRunState.CANCELLED
        "FAILED" -> SyncRunState.FAILED
        else -> SyncRunState.IDLE
    }
}

/** Only live progress with this account's owner ID may drive progress bars.
 * Finished work is intentionally ignored here; durable account logs own history. */
data class SyncWorkSnapshot(val owner: String?, val state: String, val manual: Boolean,
    val updatedAt: Long = 0, val processed: Int = 0, val total: Int = 0, val phase: String? = null)
data class SyncLiveStatus(val state: SyncRunState, val processed: Int = 0, val total: Int = 0, val phase: String? = null)

fun liveSyncStatus(userId: String?, pending: Boolean, conflicts: Boolean, latestStatus: String?,
    work: List<SyncWorkSnapshot>): SyncLiveStatus {
    if (userId == null) return SyncLiveStatus(SyncRunState.IDLE)
    val running = work.filter { it.state == "RUNNING" && it.owner == userId }.maxByOrNull { it.updatedAt }
    if (running != null) return SyncLiveStatus(SyncRunState.RUNNING, running.processed, running.total, running.phase)
    val queued = work.any { it.manual && it.state in listOf("ENQUEUED", "BLOCKED") }
    val starting = work.any { it.state == "RUNNING" && it.owner == null }
    if (queued || starting) return SyncLiveStatus(if (latestStatus in listOf("RETRY", "PARTIAL")) SyncRunState.RETRYING else SyncRunState.QUEUED)
    if (conflicts) return SyncLiveStatus(SyncRunState.CONFLICT)
    if (pending && work.any { !it.manual && it.state in listOf("ENQUEUED", "BLOCKED") })
        return SyncLiveStatus(if (latestStatus in listOf("RETRY", "PARTIAL")) SyncRunState.RETRYING else SyncRunState.WAITING)
    return SyncLiveStatus(SyncStatusModel.stateForLog(latestStatus))
}
