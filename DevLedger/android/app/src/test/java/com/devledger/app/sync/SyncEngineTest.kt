package com.devledger.app.sync

import com.devledger.app.data.local.dao.DeletedDao
import com.devledger.app.data.local.dao.SnippetDao
import com.devledger.app.data.local.entity.*
import com.devledger.app.data.remote.DevLedgerApiService
import com.devledger.app.data.remote.auth.TokenManager
import com.devledger.app.data.remote.dto.SyncBatchResponse
import com.devledger.app.data.repository.*
import com.devledger.app.util.DevLedgerResult
import io.mockk.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Response

// Reference scope: JVM unit testing for Android with JUnit, MockK and Truth. (Google Developers, n.d.s). See REFERENCES-CODE.md for full entries.

class SyncEngineTest {
    private val user = UserEntity("user", "test@example.test", "Test", null)
    private val auth = mockk<AuthRepository>()
    private val token = mockk<TokenManager>()
    private val api = mockk<DevLedgerApiService>()
    private val snippets = mockk<SnippetRepository>(relaxed = true)
    private val transactions = mockk<TransactionRepository>(relaxed = true)
    private val folders = mockk<FolderRepository>(relaxed = true)
    private val tags = mockk<UserTagRepository>(relaxed = true)
    private val deleted = mockk<DeletedDao>(relaxed = true)
    private val snippetDao = mockk<SnippetDao>(relaxed = true)
    private val account = MutableStateFlow<UserEntity?>(user)
    private lateinit var engine: SyncEngine
    private fun snippet() = SnippetEntity("s", user.userId, null, "Code", "local", "kotlin", null, "",
        createdAt = 1, updatedAt = 2)

    @Before fun setUp() {
        every { auth.currentUser } returns account
        every { token.getAccessToken() } returns "token"
        coEvery { snippets.getPendingSyncForUser(any()) } returns emptyList()
        coEvery { snippets.getPendingDeletesForUser(any()) } returns emptyList()
        coEvery { transactions.getPendingSyncForUser(any()) } returns emptyList()
        coEvery { folders.getPendingSyncForUser(any()) } returns emptyList()
        coEvery { tags.getPendingSyncForUser(any()) } returns emptyList()
        coEvery { deleted.getForUser(any()) } returns emptyList()
        coEvery { snippetDao.conflictCount(any()) } returns 0
        coEvery { api.getSyncDeletions() } returns Response.success(emptyList())
        coEvery { folders.refreshFromApi() } returns DevLedgerResult.Success(Unit)
        coEvery { tags.refreshFromApi() } returns DevLedgerResult.Success(Unit)
        engine = SyncEngine(api, snippets, transactions, folders, tags, auth, token, deleted, snippetDao)
    }
    @Test fun `required download failure cannot complete an empty upload queue`() = runTest {
        coEvery { tags.refreshFromApi() } returns DevLedgerResult.Error("Server rejected tags")
        assertEquals("FAILED", engine.run {}.status)
        coVerify(exactly = 0) { transactions.refreshFromApi() }
    }
    @Test fun `network failure is retained as retry rather than completion`() = runTest {
        coEvery { folders.refreshFromApi() } returns DevLedgerResult.Error("No network", isOffline = true)
        assertEquals("RETRY", engine.run {}.status)
    }
    @Test fun `409 holds only the rejected snapshot and acknowledges none of its batch`() = runTest {
        val row = snippet()
        coEvery { snippets.getPendingSyncForUser(user.userId) } returns listOf(row)
        coEvery { folders.getPendingSyncForUser(user.userId) } returns listOf(FolderEntity("f", user.userId, "Folder", null, 1))
        coEvery { api.syncBatch(any()) } returns Response.error(409,
            """{"applied":[],"conflicts":["s"]}""".toResponseBody("application/json".toMediaType()))
        coEvery { snippetDao.flagConflictIfUnchanged(row) } returns true
        assertEquals("CONFLICT", engine.run {}.status)
        coVerify(exactly = 1) { snippetDao.flagConflictIfUnchanged(row) }
        coVerify(exactly = 0) { snippets.markSyncedIfUnchanged(any()) }
        coVerify(exactly = 0) { folders.markSyncedIfUnchanged(any()) }
    }
    @Test fun `edit during conflict response retries without holding a newer local version`() = runTest {
        val row = snippet()
        coEvery { snippets.getPendingSyncForUser(user.userId) } returns listOf(row)
        coEvery { api.syncBatch(any()) } returns Response.error(409,
            """{"applied":[],"conflicts":["s"]}""".toResponseBody("application/json".toMediaType()))
        coEvery { snippetDao.flagConflictIfUnchanged(row) } returns false
        assertEquals("RETRY", engine.run {}.status)
    }
    @Test fun `unrelated conflict ID cannot alter any local row`() = runTest {
        coEvery { snippets.getPendingSyncForUser(user.userId) } returns listOf(snippet())
        coEvery { api.syncBatch(any()) } returns Response.error(409,
            """{"applied":[],"conflicts":["unknown"]}""".toResponseBody("application/json".toMediaType()))
        assertEquals("FAILED", engine.run {}.status)
        coVerify(exactly = 0) { snippetDao.flagConflictIfUnchanged(any()) }
    }
    @Test fun `edit made during upload remains partial until its own acknowledgement`() = runTest {
        val sent = snippet(); val edited = sent.copy(content = "newer", updatedAt = 3)
        coEvery { snippets.getPendingSyncForUser(user.userId) } returnsMany listOf(listOf(sent), listOf(edited))
        coEvery { api.syncBatch(any()) } returns Response.success(SyncBatchResponse(listOf("s"), emptyList()))
        coEvery { snippets.markSyncedIfUnchanged(sent) } returns false
        val result = engine.run {}
        assertEquals("PARTIAL", result.status)
        assertEquals(1, result.uploaded)
    }
    @Test fun `a successful check counts all required downloads and no fake percentages`() = runTest {
        val progress = mutableListOf<SyncProgress>()
        assertEquals("COMPLETED", engine.run { progress += it }.status)
        assertEquals(6, progress.last().processed)
        assertEquals(6, progress.last().total)
        assertTrue(progress.zipWithNext().all { (a, b) -> b.processed >= a.processed })
        coVerify(exactly = 2) { api.getSyncDeletions() }
    }
    @Test fun `persisted conflicts keep an otherwise successful pass out of completed history`() = runTest {
        coEvery { snippetDao.conflictCount(user.userId) } returns 1
        assertEquals("CONFLICT", engine.run {}.status)
    }
    @Test fun `account switch during request cannot acknowledge the previous accounts upload`() = runTest {
        coEvery { snippets.getPendingSyncForUser(user.userId) } returns listOf(snippet())
        coEvery { api.syncBatch(any()) } answers {
            account.value = user.copy(userId = "other")
            Response.success(SyncBatchResponse(listOf("s"), emptyList()))
        }
        assertEquals("FAILED", engine.run {}.status)
        coVerify(exactly = 0) { snippets.markSyncedIfUnchanged(any()) }
    }
    @Test fun `cancellation propagates instead of becoming a completed or failed sync`() = runTest {
        coEvery { api.getSyncDeletions() } throws CancellationException("stopped")
        assertTrue(runCatching { engine.run {} }.exceptionOrNull() is CancellationException)
    }
    @Test fun `HTTP server failure after uploads never reports success`() = runTest {
        val row = snippet()
        coEvery { snippets.getPendingSyncForUser(user.userId) } returns listOf(row)
        coEvery { api.syncBatch(any()) } returns Response.success(SyncBatchResponse(listOf("s"), emptyList()))
        coEvery { snippets.markSyncedIfUnchanged(row) } returns true
        coEvery { api.getSyncDeletions() } returns Response.error(503, "down".toResponseBody())
        val result = engine.run {}
        assertEquals("RETRY", result.status)
        assertEquals(1, result.uploaded)
        coVerify(exactly = 1) { snippets.markSyncedIfUnchanged(row) }
    }
}
