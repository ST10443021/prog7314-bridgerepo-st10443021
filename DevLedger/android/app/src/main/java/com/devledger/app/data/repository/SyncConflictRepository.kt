package com.devledger.app.data.repository

import com.devledger.app.data.local.dao.SnippetDao
import com.devledger.app.data.local.entity.SnippetEntity
import com.devledger.app.data.local.entity.SyncStatus
import com.devledger.app.data.remote.DevLedgerApiService
import com.devledger.app.data.remote.auth.TokenManager
import com.devledger.app.util.IdGenerator
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import retrofit2.HttpException

// Reference scope: Repository pattern and offline-first data flow. (Google Developers, n.d.a; Google Developers, n.d.b). See REFERENCES-CODE.md for full entries.

/** Recovery never overwrites the remote original with an offline draft. */
@Singleton
class SyncConflictRepository @Inject constructor(
    private val dao: SnippetDao,
    private val api: DevLedgerApiService,
    private val authRepository: AuthRepository,
    private val tokenManager: TokenManager,
) {
    suspend fun keepBoth(id: String): String {
        val token = tokenManager.getAccessToken()?.takeIf { it.isNotBlank() }
            ?: error("Sign in before resolving a conflict.")
        val userId = authRepository.currentUser.first()?.userId ?: error("Sign in first.")
        val local = dao.getById(id) ?: error("The local snippet no longer exists.")
        check(local.userId == userId && local.syncStatus == SyncStatus.CONFLICT) {
            "This conflict does not belong to the current account or has already changed."
        }
        val response = api.getSnippetSnapshot()
        if (!response.isSuccessful) throw HttpException(response)
        val snapshot = response.body() ?: error("No server snapshot received. Local draft retained.")
        check(tokenManager.getAccessToken() == token && authRepository.currentUser.first()?.userId == userId) {
            "The account changed. Local draft retained."
        }
        val matches = snapshot.snippets.filter { it.snippetId == id }
        check(matches.size <= 1) { "The server returned duplicate versions. Local draft retained." }
        val remote = matches.singleOrNull()
        val deleted = id in snapshot.deletedIds
        check((remote != null) != deleted) { "The server did not confirm a unique current version. Try again." }
        val server = remote?.let {
            SnippetEntity(it.snippetId, userId, it.folderId, it.title, it.content, it.language,
                it.description, it.tags.joinToString(","), it.isFavourite, it.isArchived,
                it.createdAt, it.updatedAt, SyncStatus.SYNCED)
        }
        return dao.keepBothConflictVersions(local, server, deleted, IdGenerator.newId(), System.currentTimeMillis())
    }
}
