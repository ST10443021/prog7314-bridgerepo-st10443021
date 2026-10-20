package com.devledger.app.sync

import com.devledger.app.data.local.entity.*
import com.devledger.app.data.remote.dto.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

sealed interface SyncUploadSource {
    data class Snippet(val row: SnippetEntity) : SyncUploadSource
    data class Transaction(val row: TransactionEntity) : SyncUploadSource
    data class Folder(val row: FolderEntity) : SyncUploadSource
    data class Tag(val row: UserTagEntity) : SyncUploadSource
    data class SnippetDelete(val row: SnippetEntity) : SyncUploadSource
    data class Delete(val row: DeletedEntity) : SyncUploadSource
}

data class PlannedSyncUpload(val id: String, val operation: SyncOperation, val source: SyncUploadSource)

/** Dependency order is established BEFORE chunking into the API's 100-operation limit. */
object SyncBatchPlanner {
    private val json = Json { encodeDefaults = true }

    fun plan(
        userId: String,
        snippets: List<SnippetEntity>,
        transactions: List<TransactionEntity>,
        folders: List<FolderEntity>,
        tags: List<UserTagEntity>,
        snippetDeletes: List<SnippetEntity>,
        deletions: List<DeletedEntity>,
    ): List<PlannedSyncUpload> {
        require(snippets.all { it.userId == userId } && transactions.all { it.userId == userId } &&
            folders.all { it.userId == userId } && tags.all { it.userId == userId } &&
            snippetDeletes.all { it.userId == userId } && deletions.all { it.userId == userId }) {
            "Pending operations contain another account's data."
        }
        require(deletions.all { it.entityType in setOf("snippet", "transaction", "folder", "tag") }) {
            "An unsupported entity is queued for deletion."
        }
        val removed = deletions.map { it.entityId }.toSet() + snippetDeletes.map { it.snippetId }
        val uploads = mutableListOf<PlannedSyncUpload>()
        val pendingFolders = folders.filterNot { it.folderId in removed }.associateBy { it.folderId }
        val visiting = mutableSetOf<String>()
        val visited = mutableSetOf<String>()
        fun addFolder(folder: FolderEntity) {
            if (folder.folderId in visited) return
            check(visiting.add(folder.folderId)) { "Pending folders contain a parent cycle." }
            folder.parentFolderId?.let { pendingFolders[it] }?.let { addFolder(it) }
            visiting.remove(folder.folderId)
            visited.add(folder.folderId)
            val payload = FolderSyncPayload(folder.folderId, folder.userId, folder.name,
                folder.parentFolderId?.takeUnless { it in removed }, folder.createdAt)
            uploads += PlannedSyncUpload(folder.folderId,
                SyncOperation("folder", "upsert", json.encodeToString(payload), folder.createdAt), SyncUploadSource.Folder(folder))
        }
        pendingFolders.values.forEach { addFolder(it) }
        tags.filterNot { it.tagId in removed }.forEach { tag ->
            uploads += PlannedSyncUpload(tag.tagId, SyncOperation("tag", "upsert", json.encodeToString(
                UserTagSyncPayload(tag.tagId, userId, tag.name, tag.colorHex, tag.createdAt)), tag.createdAt), SyncUploadSource.Tag(tag))
        }
        snippets.filterNot { it.snippetId in removed }.forEach { snippet ->
            uploads += PlannedSyncUpload(snippet.snippetId, SyncOperation("snippet", "upsert", json.encodeToString(
                SnippetSyncPayload(snippet.snippetId, userId, snippet.folderId?.takeUnless { it in removed },
                    snippet.title, snippet.content, snippet.language, snippet.description,
                    snippet.tagsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                    snippet.isFavourite, snippet.isArchived, snippet.createdAt, snippet.updatedAt)), snippet.updatedAt), SyncUploadSource.Snippet(snippet))
        }
        transactions.filterNot { it.transactionId in removed }.forEach { tx ->
            uploads += PlannedSyncUpload(tx.transactionId, SyncOperation("transaction", "upsert", json.encodeToString(
                TransactionSyncPayload(tx.transactionId, userId, tx.type, tx.amount, tx.currency, tx.category,
                    tx.description, tx.clientOrFrom, tx.relatedProject, tx.paymentMethod, tx.referenceNumber,
                    tx.isRecurring, tx.recurringFrequency, tx.nextOccurrenceDate, tx.attachmentFileName,
                    tx.attachmentBase64, tx.transactionDate, tx.createdAt)), tx.createdAt), SyncUploadSource.Transaction(tx))
        }
        snippetDeletes.forEach { snippet ->
            uploads += PlannedSyncUpload(snippet.snippetId, SyncOperation("snippet", "delete",
                json.encodeToString(SnippetDeletePayload(snippet.snippetId, userId)), snippet.updatedAt), SyncUploadSource.SnippetDelete(snippet))
        }
        // Reassignments and new destination folders must reach the server before deletion.
        deletions.filterNot { it.entityType == "snippet" && it.entityId in snippetDeletes.map { s -> s.snippetId } }
            .sortedBy { if (it.entityType == "folder") 1 else 0 }.forEach { marker ->
                val payload = if (marker.entityType == "snippet")
                    json.encodeToString(SnippetDeletePayload(marker.entityId, userId)) else
                    json.encodeToString(DeleteSyncPayload(marker.entityId, userId,
                        marker.reassignToFolderId?.takeUnless { it in removed }))
                uploads += PlannedSyncUpload(marker.entityId,
                    SyncOperation(marker.entityType, "delete", payload, marker.deletedAt), SyncUploadSource.Delete(marker))
            }
        check(uploads.map { it.id }.distinct().size == uploads.size) {
            "A sync pass contains duplicate entity IDs; no ambiguous acknowledgement is allowed."
        }
        return uploads
    }

    /** Never clear queued work for a missing, duplicated, or unrelated acknowledgement. */
    fun validateAcknowledgement(batch: List<PlannedSyncUpload>, response: SyncBatchResponse) {
        check(response.conflicts.isEmpty()) { "The server reported sync conflicts. Local changes were retained." }
        check(response.applied.size == response.applied.distinct().size &&
            response.applied.toSet() == batch.map { it.id }.toSet()) {
            "The API acknowledgement does not match the uploaded batch. Local changes were retained."
        }
    }
}
