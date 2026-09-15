package com.devledger.app.data.repository

import com.devledger.app.data.local.dao.FolderDao
import com.devledger.app.data.local.dao.UserDao
import com.devledger.app.data.local.entity.FolderEntity
import com.devledger.app.data.remote.DevLedgerApiService
import com.devledger.app.data.remote.dto.CreateFolderRequest
import com.devledger.app.data.remote.dto.DeleteFolderRequest
import com.devledger.app.util.DevLedgerResult
import com.devledger.app.util.IdGenerator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Folders are simple, infrequently-changed metadata, so — unlike Snippets/Transactions —
 * this repository is refresh-then-cache rather than part of the offline PENDING sync
 * queue: create/rename/delete call the API directly and update Room from the response,
 * so a failure surfaces immediately rather than silently queuing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class FolderRepository @Inject constructor(
    private val dao: FolderDao,
    private val userDao: UserDao,
    private val api: DevLedgerApiService,
) {
    fun observeFolders(): Flow<List<FolderEntity>> =
        userDao.observeCurrentUser().flatMapLatest { user ->
            if (user == null) flowOf(emptyList()) else dao.observeForUser(user.userId)
        }

    suspend fun refreshFromApi(): DevLedgerResult<Unit> = DevLedgerResult.safeCall {
        val user = userDao.getCurrentUser() ?: error("Sign in before loading folders.")
        val response = api.getFolders()
        if (!response.isSuccessful) error("Could not load folders: HTTP ${response.code()}")
        val remote = response.body() ?: error("The API returned no folder list.")
        remote.forEach { dto ->
            dao.upsert(FolderEntity(
                folderId = dto.folderId,
                userId = user.userId,
                name = dto.name,
                parentFolderId = dto.parentFolderId,
                createdAt = dto.createdAt,
            ))
        }
    }

    suspend fun create(name: String, parentFolderId: String? = null): DevLedgerResult<FolderEntity> = DevLedgerResult.safeCall {
        val user = userDao.getCurrentUser() ?: error("Sign in before creating a folder.")
        val response = api.createFolder(CreateFolderRequest(name, parentFolderId))
        if (!response.isSuccessful) error("Could not create the folder: HTTP ${response.code()}")
        val dto = response.body() ?: error("Empty folder response.")
        val entity = FolderEntity(dto.folderId, user.userId, dto.name, dto.parentFolderId, dto.createdAt)
        dao.upsert(entity)
        entity
    }

    suspend fun rename(id: String, name: String): DevLedgerResult<Unit> = DevLedgerResult.safeCall {
        val user = userDao.getCurrentUser() ?: error("Sign in before renaming a folder.")
        val response = api.renameFolder(id, CreateFolderRequest(name, null))
        if (!response.isSuccessful) error("Could not rename the folder: HTTP ${response.code()}")
        val dto = response.body() ?: error("Empty folder response.")
        dao.upsert(FolderEntity(dto.folderId, user.userId, dto.name, dto.parentFolderId, dto.createdAt))
    }

    /** [reassignToFolderId] is required by the API too — this repository can't skip
     *  that decision, matching the "reassignment, never silent delete" rule. */
    suspend fun delete(id: String, reassignToFolderId: String?): DevLedgerResult<Unit> = DevLedgerResult.safeCall {
        val response = api.deleteFolder(id, DeleteFolderRequest(reassignToFolderId))
        if (!response.isSuccessful) error("Could not delete the folder: HTTP ${response.code()}")
        dao.delete(id)
    }

    suspend fun getById(id: String): FolderEntity? = dao.getById(id)
}