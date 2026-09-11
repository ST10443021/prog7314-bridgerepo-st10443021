package com.devledger.app.repository

import com.devledger.app.data.local.dao.SnippetDao
import com.devledger.app.data.local.entity.SnippetEntity
import com.devledger.app.data.local.entity.SyncStatus
import com.devledger.app.data.repository.SnippetRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * Verifies the offline-first behaviour described in the Part 1 design doc: writes go
 * straight to Room with a PENDING sync status, ready for [com.devledger.app.sync.SyncWorker]
 * to push later, and reads never touch the network.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SnippetRepositoryTest {

    private lateinit var dao: SnippetDao
    private lateinit var repository: SnippetRepository

    @Before
    fun setUp() {
        dao = mockk(relaxed = true)
        repository = SnippetRepository(dao)
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
        coVerify(exactly = 1) { dao.upsert(any()) }
    }

    @Test
    fun `createOrUpdate reuses the same id when editing an existing snippet`() = runTest {
        val existingId = "existing-id-123"
        val updated = repository.createOrUpdate(
            userId = "user-1",
            id = existingId,
            title = "Updated title",
            content = "new content",
            language = "kotlin",
            description = "desc",
            tags = emptyList(),
        )
        assertThat(updated.snippetId).isEqualTo(existingId)
    }

    @Test
    fun `observeSnippets with a blank query returns the active-snippets stream`() = runTest {
        val expected = listOf(
            SnippetEntity(
                snippetId = "1", userId = "u", folderId = null, title = "A", content = "x",
                language = "kotlin", description = null, tagsCsv = "", createdAt = 0, updatedAt = 0,
            ),
        )
        coEvery { dao.observeActiveSnippets() } returns flowOf(expected)

        val result = repository.observeSnippets("")

        result.collect { list -> assertThat(list).isEqualTo(expected) }
    }

    @Test
    fun `archive marks the snippet archived via the DAO`() = runTest {
        repository.archive("snippet-1", true)
        coVerify(exactly = 1) { dao.setArchived("snippet-1", true) }
    }

    @Test
    fun `toggleFavourite delegates to the DAO with the requested state`() = runTest {
        repository.toggleFavourite("snippet-1", true)
        coVerify(exactly = 1) { dao.setFavourite("snippet-1", true) }
    }
}
