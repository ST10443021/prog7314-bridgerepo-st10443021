package com.devledger.app.data.local.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.devledger.app.data.local.entity.DeletedEntity
import com.devledger.app.data.local.entity.SyncStatus

// Reference scope: Room persistence. (Google Developers, n.d.b). See REFERENCES-CODE.md for full entries.

/** Durable outbox and retained delete-wins markers, scoped to the signed-in account. */
@Dao
interface DeletedDao {
    @Query("SELECT * FROM deleted_entities WHERE userId = :userId AND syncStatus = 'PENDING' ORDER BY deletedAt ASC")
    suspend fun getForUser(userId: String): List<DeletedEntity>

    @Query("SELECT * FROM deleted_entities WHERE userId = :userId AND syncStatus = 'PENDING' ORDER BY deletedAt ASC")
    fun observePendingForUser(userId: String): Flow<List<DeletedEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DeletedEntity)

    @Query("SELECT * FROM deleted_entities WHERE entityId = :id LIMIT 1")
    suspend fun getById(id: String): DeletedEntity?

    @Query("DELETE FROM deleted_entities WHERE entityId = :id")
    suspend fun delete(id: String)

    @Query("UPDATE deleted_entities SET syncStatus = 'SYNCED' WHERE userId = :userId AND entityType = :type AND entityId = :id AND deletedAt = :deletedAt AND syncStatus = 'PENDING'")
    suspend fun acknowledge(userId: String, type: String, id: String, deletedAt: Long): Int

    @Query("SELECT EXISTS(SELECT 1 FROM deleted_entities WHERE userId = :userId AND entityType = :type AND entityId = :id)")
    suspend fun isDeleted(userId: String, type: String, id: String): Boolean

    @Query("DELETE FROM transactions WHERE userId = :userId AND transactionId = :id")
    suspend fun removeTransaction(userId: String, id: String)

    @Query("DELETE FROM folders WHERE userId = :userId AND folderId = :id")
    suspend fun removeFolder(userId: String, id: String)

    @Query("DELETE FROM user_tags WHERE userId = :userId AND tagId = :id")
    suspend fun removeTag(userId: String, id: String)

    @Query("UPDATE snippets SET folderId = :target, syncStatus = CASE WHEN syncStatus = 'CONFLICT' THEN 'CONFLICT' ELSE 'PENDING' END, updatedAt = MAX(updatedAt + 1, :now) WHERE userId = :userId AND folderId = :id AND syncStatus NOT IN ('DELETE_PENDING', 'DELETED')")
    suspend fun reassignSnippets(userId: String, id: String, target: String?, now: Long)

    @Query("UPDATE folders SET parentFolderId = :target, syncStatus = 'PENDING' WHERE userId = :userId AND parentFolderId = :id")
    suspend fun reparentChildren(userId: String, id: String, target: String?)

    @Query("UPDATE snippets SET folderId = NULL WHERE userId = :userId AND folderId = :id")
    suspend fun detachDeletedFolder(userId: String, id: String)

    @Query("UPDATE folders SET parentFolderId = NULL WHERE userId = :userId AND parentFolderId = :id")
    suspend fun detachDeletedParent(userId: String, id: String)

    @Transaction
    suspend fun queueTransactionDeletion(marker: DeletedEntity) {
        requireMarkerOwner(marker)
        insert(marker)
        removeTransaction(marker.userId, marker.entityId)
    }

    @Transaction
    suspend fun queueTagDeletion(marker: DeletedEntity) {
        requireMarkerOwner(marker)
        insert(marker)
        removeTag(marker.userId, marker.entityId)
    }

    @Transaction
    suspend fun queueFolderDeletion(marker: DeletedEntity, parentFolderId: String?) {
        requireMarkerOwner(marker)
        insert(marker)
        reassignSnippets(marker.userId, marker.entityId, marker.reassignToFolderId, marker.deletedAt)
        reparentChildren(marker.userId, marker.entityId, parentFolderId)
        removeFolder(marker.userId, marker.entityId)
    }

    /** Called before and after snapshots; retain markers even after upload acknowledgement. */
    @Transaction
    suspend fun applyRemoteDeletion(marker: DeletedEntity) {
        require(marker.entityType in setOf("folder", "tag", "transaction"))
        val existing = getById(marker.entityId)
        require(existing == null || (existing.userId == marker.userId && existing.entityType == marker.entityType)) {
            "A deletion ID conflicts with another account or entity type."
        }
        if (!isDeleted(marker.userId, marker.entityType, marker.entityId)) {
            insert(marker.copy(syncStatus = SyncStatus.SYNCED))
        }
        when (marker.entityType) {
            "transaction" -> removeTransaction(marker.userId, marker.entityId)
            "tag" -> removeTag(marker.userId, marker.entityId)
            "folder" -> {
                detachDeletedFolder(marker.userId, marker.entityId)
                detachDeletedParent(marker.userId, marker.entityId)
                removeFolder(marker.userId, marker.entityId)
            }
        }
    }

    suspend fun requireMarkerOwner(marker: DeletedEntity) {
        val existing = getById(marker.entityId)
        require(existing == null || (existing.userId == marker.userId && existing.entityType == marker.entityType)) {
            "A deletion ID conflicts with another account or entity type."
        }
    }
}
