package com.devledger.app.repository

import android.content.Context
import com.devledger.app.data.local.dao.SnippetDao
import com.devledger.app.data.local.dao.UserDao
import com.devledger.app.data.local.entity.SnippetEntity
import com.devledger.app.data.local.entity.SyncStatus
import com.devledger.app.data.local.entity.UserEntity
import com.devledger.app.data.remote.DevLedgerApiService
import com.devledger.app.data.remote.auth.TokenManager
import com.devledger.app.data.repository.SettingsRepository
import com.devledger.app.data.repository.SnippetRepository
import com.devledger.app.sync.SyncScheduler
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Verifies the offline-first snippet behaviour:
 * local writes are immediately persisted as PENDING and can later be
 * synchronised by WorkManager.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SnippetRepositoryTest {

    private lateinit var dao: SnippetDao
    private lateinit var userDao: UserDao
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var api: DevLedgerApiService
    private lateinit var tokenManager: TokenManager
    private lateinit var context: Context
    private lateinit var currentUser: UserEntity
    private lateinit var repository: SnippetRepository

    @Before
    fun setUp() {
        dao = mockk(relaxed = true)
        userDao = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        api = mockk(relaxed = true)
        tokenManager = mockk(relaxed = true)
        context = mockk(relaxed = true)
        currentUser = mockk(relaxed = true)

        every { currentUser.userId } returns "user-1"
        every { userDao.observeCurrentUser() } returns flowOf(currentUser)
        coEvery { userDao.getCurrentUser() } returns currentUser

        every { settingsRepository.wifiOnlySync } returns flowOf(false)

        mockkObject(SyncScheduler)
        every {
            SyncScheduler.schedulePeriodicSync(any(), any())
        } returns Unit
        every {
            SyncScheduler.syncNow(any(), any())
        } returns Unit

        repository = SnippetRepository(
            dao = dao,
            userDao = userDao,
            settingsRepository = settingsRepository,
            api = api,
            tokenManager = tokenManager,
            context = context,
        )
    }

    @After
    fun tearDown() {
        unmockkObject(SyncScheduler)
    }

    @Test
    fun `createOrUpdate persists a new snippet flagged PENDING for sync`() = runTest {
        val snippet = repository.createOrUpdate(
            userId = "user-1",
            title = "Deploy script",
            content = "#!/bin/bash\necho hello",
            language = "shell",
            description = null,
            tags = listOf("shell", "ci"),
        )

        assertThat(snippet.title).isEqualTo("Deploy script")
        assertThat(snippet.syncStatus).isEqualTo(SyncStatus.PENDING)
        assertThat(snippet.tagsCsv).isEqualTo("shell,ci")

        coVerify(exactly = 1) {
            dao.upsert(any())
        }
    }

    @Test
    fun `createOrUpdate reuses the same id when editing existing snippet`() = runTest {
        val existing = snippet(
            id = "existing-id-123",
            title = "Old title",
        )

        coEvery {
            dao.getById("existing-id-123")
        } returns existing

        val updated = repository.createOrUpdate(
            userId = "user-1",
            id = "existing-id-123",
            title = "Updated title",
            content = "new content",
            language = "kotlin",
            description = "desc",
            tags = emptyList(),
        )

        assertThat(updated.snippetId).isEqualTo("existing-id-123")
        assertThat(updated.title).isEqualTo("Updated title")
        assertThat(updated.syncStatus).isEqualTo(SyncStatus.PENDING)
    }

    @Test
    fun `observeSnippets with blank query returns current users active snippets`() =
        runTest {

            val expected = listOf(
                snippet(
                    id = "1",
                    title = "Example",
                ),
            )

            every {
                dao.observeSnippetsForUser("user-1", "")
            } returns flowOf(expected)

            val result = repository.observeSnippets("")

            result.collect { list ->
                assertThat(list).isEqualTo(expected)
            }
        }

    @Test
    fun `archive marks current users snippet archived`() = runTest {
        val existing = snippet(id = "snippet-1")

        coEvery {
            dao.getById("snippet-1")
        } returns existing

        repository.archive("snippet-1", true)

        coVerify(exactly = 1) {
            dao.setArchived("snippet-1", true, any())
        }
    }

    @Test
    fun `toggleFavourite delegates requested state for current users snippet`() =
        runTest {

            val existing = snippet(id = "snippet-1")

            coEvery {
                dao.getById("snippet-1")
            } returns existing

            repository.toggleFavourite("snippet-1", true)

            coVerify(exactly = 1) {
                dao.setFavourite("snippet-1", true, any())
            }
        }

    private fun snippet(
        id: String,
        title: String = "Snippet",
    ) = SnippetEntity(
        snippetId = id,
        userId = "user-1",
        folderId = null,
        title = title,
        content = "println(\"hello\")",
        language = "kotlin",
        description = null,
        tagsCsv = "",
        createdAt = 1L,
        updatedAt = 1L,
        syncStatus = SyncStatus.PENDING,
    )
}