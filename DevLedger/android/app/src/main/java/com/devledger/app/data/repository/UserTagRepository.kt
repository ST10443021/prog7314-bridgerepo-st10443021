package com.devledger.app.data.repository

import android.content.Context
import android.util.Log
import com.devledger.app.data.local.dao.DeletedDao
import com.devledger.app.data.local.dao.UserDao
import com.devledger.app.data.local.dao.UserTagDao
import com.devledger.app.data.local.entity.DeletedEntity
import com.devledger.app.data.local.entity.SyncStatus
import com.devledger.app.data.local.entity.UserTagEntity
import com.devledger.app.data.remote.DevLedgerApiService
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
 * Repository for user-defined snippet tags. Fully offline-first.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class UserTagRepository @Inject constructor(
    private val dao: UserTagDao,
    private val deletedDao: DeletedDao,
    private val userDao: UserDao,
    private val api: DevLedgerApiService,
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val context: Context,
) {
    fun observeTags(): Flow<List<UserTagEntity>> =
        userDao.observeCurrentUser().flatMapLatest { user ->
            if (user == null) flowOf(emptyList()) else dao.observeForUser(user.userId)
        }

    suspend fun refreshFromApi(): DevLedgerResult<Unit> = DevLedgerResult.safeCall {
        val user = userDao.getCurrentUser() ?: error("Sign in before loading tags.")
        val response = api.getTags()
        if (!response.isSuccessful) throw HttpException(response)
        val remote = response.body() ?: error("The API returned no tag list.")
        
        remote.forEach { dto ->
            dao.upsert(UserTagEntity(
                tagId = dto.tagId,
                userId = user.userId,
                name = dto.name,
                colorHex = dto.colorHex,
                createdAt = dto.createdAt,
                syncStatus = SyncStatus.SYNCED
            ))
        }
    }

    suspend fun create(name: String, colorHex: String? = null): DevLedgerResult<UserTagEntity> = DevLedgerResult.safeCall {
        val user = userDao.getCurrentUser() ?: error("Sign in before creating a tag.")
        val entity = UserTagEntity(
            tagId = IdGenerator.newId(),
            userId = user.userId,
            name = name.trim(),
            colorHex = colorHex,
            createdAt = System.currentTimeMillis(),
            syncStatus = SyncStatus.PENDING
        )
        dao.upsert(entity)
        queueSync()
        entity
    }

    suspend fun delete(tagId: String): DevLedgerResult<Unit> = DevLedgerResult.safeCall {
        val user = userDao.getCurrentUser() ?: error("Sign in before deleting.")
        val existing = dao.getById(tagId) ?: return@safeCall
        
        deletedDao.insert(DeletedEntity(
            entityId = existing.tagId,
            userId = user.userId,
            entityType = "tag"
        ))
        dao.delete(tagId)
        queueSync()
    }

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
            Log.e("UserTagRepository", "Could not schedule tag sync.", error)
        }
    }
}
