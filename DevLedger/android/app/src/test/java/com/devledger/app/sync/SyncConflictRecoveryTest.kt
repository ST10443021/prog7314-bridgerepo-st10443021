package com.devledger.app.sync

import com.devledger.app.data.local.dao.SnippetDao
import com.devledger.app.data.local.entity.*
import com.devledger.app.data.remote.DevLedgerApiService
import com.devledger.app.data.remote.auth.TokenManager
import com.devledger.app.data.remote.dto.*
import com.devledger.app.data.repository.AuthRepository
import com.devledger.app.data.repository.SyncConflictRepository
import io.mockk.*
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Response

// Reference scope: JVM unit testing for Android with JUnit, MockK and Truth. (Google Developers, n.d.s). See REFERENCES-CODE.md for full entries.

class SyncConflictRecoveryTest {
    private val user = UserEntity("user", "test@example.test", "Test", null)
    private val account = MutableStateFlow<UserEntity?>(user)
    private val local = SnippetEntity("s", user.userId, null, "Code", "local", "kotlin", null, "",
        createdAt = 1, updatedAt = 2, syncStatus = "CONFLICT")
    private val dao = mockk<SnippetDao>(relaxed = true)
    private val api = mockk<DevLedgerApiService>()
    private val auth = mockk<AuthRepository>()
    private val token = mockk<TokenManager>()
    private lateinit var repository: SyncConflictRepository
    @Before fun setUp() {
        every { auth.currentUser } returns account
        every { token.getAccessToken() } returns "token"
        coEvery { dao.getById("s") } returns local
        repository = SyncConflictRepository(dao, api, auth, token)
    }
    private fun server() = SnippetDto("s", title = "Server", content = "server", language = "kotlin", createdAt = 1, updatedAt = 2)

    @Test fun `failed recovery fetch performs no local mutation`() = runTest {
        coEvery { api.getSnippetSnapshot() } throws IOException("offline")
        assertTrue(runCatching { repository.keepBoth("s") }.isFailure)
        coVerify(exactly = 0) { dao.keepBothConflictVersions(any(), any(), any(), any(), any()) }
    }
    @Test fun `account switch during recovery performs no local mutation`() = runTest {
        coEvery { api.getSnippetSnapshot() } answers {
            account.value = user.copy(userId = "other")
            Response.success(SnippetSnapshotDto(listOf(server()), emptyList()))
        }
        assertTrue(runCatching { repository.keepBoth("s") }.isFailure)
        coVerify(exactly = 0) { dao.keepBothConflictVersions(any(), any(), any(), any(), any()) }
    }
    @Test fun `server must confirm a version or deletion before saving a copy`() = runTest {
        coEvery { api.getSnippetSnapshot() } returns Response.success(SnippetSnapshotDto(emptyList(), emptyList()))
        assertTrue(runCatching { repository.keepBoth("s") }.isFailure)
        coVerify(exactly = 0) { dao.keepBothConflictVersions(any(), any(), any(), any(), any()) }
    }
    @Test fun `recovery passes the fresh server content and a distinct ID to atomic persistence`() = runTest {
        coEvery { api.getSnippetSnapshot() } returns Response.success(SnippetSnapshotDto(listOf(server()), emptyList()))
        coEvery { dao.keepBothConflictVersions(any(), any(), any(), any(), any()) } answers { arg(3) }
        val copyId = repository.keepBoth("s")
        assertNotEquals("s", copyId)
        coVerify(exactly = 1) { dao.keepBothConflictVersions(local,
            match { it != null && it.content == "server" && it.userId == user.userId && it.syncStatus == "SYNCED" }, false, copyId, any()) }
    }
}
