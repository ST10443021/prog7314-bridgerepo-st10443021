package com.devledger.app.data.repository

import android.content.Context
import android.util.Log
import com.devledger.app.data.local.dao.DeletedDao
import com.devledger.app.data.local.dao.FolderDao
import com.devledger.app.data.local.dao.SnippetDao
import com.devledger.app.data.local.dao.UserDao
import com.devledger.app.data.local.entity.DeletedEntity
import com.devledger.app.data.local.entity.FolderEntity
import com.devledger.app.data.local.entity.SyncStatus
import com.devledger.app.data.remote.DevLedgerApiService
import com.devledger.app.data.remote.dto.CreateFolderRequest
import com.devledger.app.sync.SyncScheduler
import com.devledger.app.util.DevLedgerResult
import com.devledger.app.util.IdGenerator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline-first folder repository. Writes land in Room immediately, marked
 * PENDING, and are queued for WorkManager synchronisation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class FolderRepository @Inject constructor(
    private val dao: FolderDao,
    private val snippetDao: SnippetDao,
    private val deletedDao: DeletedDao,
    private val userDao: UserDao,
    private val api: DevLedgerApiService,
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val context: Context,
) {
    fun observeFolders(): Flow<List<FolderEntity>> =
        userDao.observeCurrentUser().flatMapLatest { user ->
            if (user == null) flowOf(emptyList()) else dao.observeForUser(user.userId)
        }

    suspend fun refreshFromApi(): DevLedgerResult<Unit> = DevLedgerResult.safeCall {
        val user = userDao.getCurrentUser() ?: error("Sign in before loading folders.")
        val response = api.getFolders()
        if (!response.isSuccessful) throw HttpException(response)
        val remote = response.body() ?: error("The API returned no folder list.")
        
        val entities = remote.map { dto ->
            FolderEntity(
                folderId = dto.folderId,
                userId = user.userId,
                name = dto.name,
                parentFolderId = dto.parentFolderId,
                createdAt = dto.createdAt,
                syncStatus = SyncStatus.SYNCED
            )
        }
        dao.mergeRemoteFolders(user.userId, entities)
    }

    suspend fun create(name: String, parentFolderId: String? = null): DevLedgerResult<FolderEntity> = DevLedgerResult.safeCall {
        val user = userDao.getCurrentUser() ?: error("Sign in before creating a folder.")
        val entity = FolderEntity(
            folderId = IdGenerator.newId(),
            userId = user.userId,
            name = name.trim(),
            parentFolderId = parentFolderId,
            createdAt = System.currentTimeMillis(),
            syncStatus = SyncStatus.PENDING
        )
        dao.upsert(entity)
        queueSync()
        entity
    }

    suspend fun rename(id: String, name: String): DevLedgerResult<Unit> = DevLedgerResult.safeCall {
        val existing = dao.getById(id) ?: error("Folder not found.")
        val updated = existing.copy(
            name = name.trim(),
            syncStatus = SyncStatus.PENDING
        )
        dao.upsert(updated)
        queueSync()
    }

    suspend fun delete(id: String, reassignToFolderId: String?): DevLedgerResult<Unit> = DevLedgerResult.safeCall {
        val user = userDao.getCurrentUser() ?: error("Sign in before deleting.")
        val existing = dao.getById(id) ?: return@safeCall
        
        // Reassign snippets locally first so the change is synced
        snippetDao.reassignFolder(id, reassignToFolderId)
        
        deletedDao.insert(DeletedEntity(
            entityId = existing.folderId,
            userId = user.userId,
            entityType = "folder"
        ))
        dao.delete(id)
        queueSync()
    }

    suspend fun getById(id: String): FolderEntity? = dao.getById(id)

    suspend fun getPendingSyncForUser(userId: String) = dao.getPendingSyncForUser(userId)
    suspend fun markSynced(id: String) = dao.updateSyncStatus(id, SyncStatus.SYNCED)

    private suspend fun queueSync() {
        try {
            val wifiOnly = settingsRepository.wifiOnlySync.first()
            SyncScheduler.schedulePeriodicSync(context, wifiOnly)
            SyncScheduler.syncNow(context, wifiOnly)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e("FolderRepository", "Could not schedule folder sync.", error)
        }
    }
}
