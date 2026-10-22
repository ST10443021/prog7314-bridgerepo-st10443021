package com.devledger.app.repository

import com.devledger.app.util.localization.UiText

import com.devledger.app.R

import android.content.Context
import com.devledger.app.data.local.dao.SnippetDao
import com.devledger.app.data.local.dao.UserDao
import com.devledger.app.data.local.entity.SnippetEntity
import com.devledger.app.data.local.entity.SyncStatus
import com.devledger.app.data.local.entity.UserEntity
import com.devledger.app.data.remote.DevLedgerApiService
import com.devledger.app.data.remote.auth.TokenManager
import com.devledger.app.data.repository.NotificationRepository
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for the offline-first snippet repository.
 *
 * These tests deliberately mock Room/network boundaries so they exercise repository
 * behaviour without changing or requiring a live API, database, or WorkManager run.
 */
class SnippetRepositoryTest {

    private lateinit var dao: SnippetDao
    private lateinit var userDao: UserDao
    private lateinit var notificationRepository: NotificationRepository
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
        notificationRepository = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        api = mockk(relaxed = true)
        tokenManager = mockk(relaxed = true)
        context = mockk(relaxed = true)
        currentUser = UserEntity(
            userId = "user-1",
            email = "test@devledger.app",
            fullName = "Test User",
            ssoProvider = "GOOGLE",
        )

        every { userDao.observeCurrentUser() } returns flowOf(currentUser)
        coEvery { userDao.getCurrentUser() } returns currentUser
        every { settingsRepository.wifiOnlySync } returns flowOf(false)

        mockkObject(SyncScheduler)
        every { SyncScheduler.schedulePeriodicSync(any(), any()) } returns Unit
        every { SyncScheduler.syncNow(any(), any()) } returns Unit

        repository = SnippetRepository(
            dao = dao,
            deletedDao = mockk(relaxed = true),
            userDao = userDao,
            notificationRepository = notificationRepository,
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
    fun `createOrUpdate persists a new snippet as PENDING`() = runTest {
        val snippet = repository.createOrUpdate(
            userId = "user-1",
            title = "Deploy script",
            content = "#!/bin/bash\necho hello",
            language = "shell",
            description = null,
            tags = listOf("shell", "ci", "shell"),
        )

        assertThat(snippet.title).isEqualTo("Deploy script")
        assertThat(snippet.syncStatus).isEqualTo(SyncStatus.PENDING)
        assertThat(snippet.tagsCsv).isEqualTo("shell,ci")

        coVerify(exactly = 1) {
            dao.saveUnlessDeleted(any())
        }
    }

    @Test
    fun `createOrUpdate reuses the same id and marks an existing snippet PENDING`() = runTest {
        val existing = snippet(id = "existing-id", title = "Old title", updatedAt = 10L)
        coEvery { dao.getById("existing-id") } returns existing

        val updated = repository.createOrUpdate(
            userId = "user-1",
            id = "existing-id",
            title = "Updated title",
            content = "new content",
            language = "kotlin",
            description = "desc",
            tags = emptyList(),
        )

        assertThat(updated.snippetId).isEqualTo("existing-id")
        assertThat(updated.title).isEqualTo("Updated title")
        assertThat(updated.syncStatus).isEqualTo(SyncStatus.PENDING)
        assertThat(updated.updatedAt).isGreaterThan(existing.updatedAt)
        coVerify(exactly = 1) { dao.saveUnlessDeleted(updated) }
        coVerify(exactly = 0) { notificationRepository.notify(UiText.Resource(R.string.lang_snippet_created), any(), "SNIPPET") }
    }

    @Test
    fun `createOrUpdate rejects a blank title`() = runTest {
        val error = runCatching {
            repository.createOrUpdate(
                userId = "user-1",
                title = "   ",
                content = "code",
                language = "kotlin",
                description = null,
                tags = emptyList(),
            )
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(error?.message).isEqualTo("Enter a snippet title.")
        coVerify(exactly = 0) { dao.saveUnlessDeleted(any()) }
    }

    @Test
    fun `observeSnippets is scoped to the current user and trims the query`() = runTest {
        val expected = listOf(snippet(id = "1", title = "Example"))
        every { dao.observeSnippetsForUser("user-1", "kotlin") } returns flowOf(expected)

        val result = repository.observeSnippets("  kotlin  ").first()

        assertThat(result).isEqualTo(expected)
        coVerify(exactly = 0) { dao.observeSnippetsForUser(any(), "  kotlin  ") }
    }

    @Test
    fun `archive delegates to the DAO for an existing snippet`() = runTest {
        coEvery { dao.getById("snippet-1") } returns snippet(id = "snippet-1")

        repository.archive("snippet-1", true)

        coVerify(exactly = 1) { dao.setArchived("snippet-1", true, any()) }
    }

    @Test
    fun `toggleFavourite delegates requested state for an existing snippet`() = runTest {
        coEvery { dao.getById("snippet-1") } returns snippet(id = "snippet-1")

        repository.toggleFavourite("snippet-1", true)

        coVerify(exactly = 1) { dao.setFavourite("snippet-1", true, any()) }
    }

    @Test
    fun `deleteArchived queues archived snippets and notifies`() = runTest {
        coEvery { dao.queueArchivedDeletes("user-1", listOf("a", "b")) } returns 2

        val deleted = repository.deleteArchived(listOf("a", "b"))

        assertThat(deleted).isEqualTo(2)
        coVerify(exactly = 1) { dao.queueArchivedDeletes("user-1", listOf("a", "b")) }
        coVerify(exactly = 1) { notificationRepository.notify(UiText.Resource(R.string.lang_snippet_deleted), UiText.Quantity(R.plurals.lang_snippets_queued_delete, 2), "SNIPPET") }
    }

    @Test
    fun `deleteArchived does not notify when nothing can be deleted`() = runTest {
        coEvery { dao.queueArchivedDeletes("user-1", listOf("missing")) } returns 0

        val deleted = repository.deleteArchived(listOf("missing"))

        assertThat(deleted).isEqualTo(0)
        coVerify(exactly = 0) { notificationRepository.notify(any(), any(), any()) }
    }

    private fun snippet(
        id: String,
        title: String = "Snippet",
        updatedAt: Long = 1L,
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
        updatedAt = updatedAt,
        syncStatus = SyncStatus.PENDING,
    )
}
