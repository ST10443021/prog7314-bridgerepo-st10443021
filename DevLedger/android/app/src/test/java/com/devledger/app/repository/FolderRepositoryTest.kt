package com.devledger.app.repository

import android.content.Context
import com.devledger.app.data.local.dao.DeletedDao
import com.devledger.app.data.local.dao.FolderDao
import com.devledger.app.data.local.dao.SnippetDao
import com.devledger.app.data.local.dao.UserDao
import com.devledger.app.data.local.entity.FolderEntity
import com.devledger.app.data.local.entity.UserEntity
import com.devledger.app.data.repository.FolderRepository
import com.devledger.app.data.repository.SettingsRepository
import com.devledger.app.util.DevLedgerResult
import com.devledger.app.data.remote.DevLedgerApiService
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

// Reference scope: JVM unit testing for Android with JUnit, MockK and Truth. (Google Developers, n.d.s). See REFERENCES-CODE.md for full entries.

class FolderRepositoryTest {
    private lateinit var dao: FolderDao
    private lateinit var snippetDao: SnippetDao
    private lateinit var deletedDao: DeletedDao
    private lateinit var userDao: UserDao
    private lateinit var repository: FolderRepository

    @Before
    fun setUp() {
        dao = mockk(relaxed = true)
        snippetDao = mockk(relaxed = true)
        deletedDao = mockk(relaxed = true)
        userDao = mockk(relaxed = true)
        val settings = mockk<SettingsRepository>(relaxed = true)
        val api = mockk<DevLedgerApiService>(relaxed = true)
        val context = mockk<Context>(relaxed = true)
        every { settings.wifiOnlySync } returns flowOf(false)
        val user = UserEntity("u1", "u@test.com", "Test", "GOOGLE")
        coEvery { userDao.getCurrentUser() } returns user
        every { userDao.observeCurrentUser() } returns flowOf(user)
        mockkObject(SyncScheduler)
        coEvery { SyncScheduler.queueAfterLocalChange(any(), any()) } returns Unit
        every { SyncScheduler.schedulePeriodicSync(any(), any()) } returns Unit
        every { SyncScheduler.syncNow(any(), any()) } returns Unit
        repository = FolderRepository(dao, snippetDao, deletedDao, userDao, api, settings, context)
    }

    @After
    fun tearDown() = unmockkObject(SyncScheduler)

    @Test
    fun `create stores a pending folder`() = runTest {
        coEvery { dao.getById("parent") } returns FolderEntity("parent", "u1", "Parent", null, 1L, "SYNCED")
        val result = repository.create("  Projects  ", "parent")
        assertThat(result).isInstanceOf(DevLedgerResult.Success::class.java)
        val folder = (result as DevLedgerResult.Success).data
        assertThat(folder.name).isEqualTo("Projects")
        assertThat(folder.parentFolderId).isEqualTo("parent")
        assertThat(folder.syncStatus).isEqualTo("PENDING")
        coVerify(exactly = 1) { dao.upsert(folder) }
    }

    @Test
    fun `rename preserves folder identity and marks it pending`() = runTest {
        val existing = FolderEntity("f1", "u1", "Old", null, 1L, "SYNCED")
        coEvery { dao.getById("f1") } returns existing
        val result = repository.rename("f1", " New ")
        assertThat(result).isInstanceOf(DevLedgerResult.Success::class.java)
        coVerify { dao.upsert(existing.copy(name = "New", syncStatus = "PENDING")) }
    }

    @Test
    fun `delete queues an atomic folder deletion with reassignment target`() = runTest {
        val existing = FolderEntity("f1", "u1", "Old", null, 1L, "SYNCED")
        coEvery { dao.getById("f1") } returns existing
        coEvery { dao.getById("f2") } returns FolderEntity("f2", "u1", "Target", null, 1L, "SYNCED")
        val result = repository.delete("f1", "f2")
        assertThat(result).isInstanceOf(DevLedgerResult.Success::class.java)
        coVerify(exactly = 1) { deletedDao.queueFolderDeletion(match {
            it.entityId == "f1" && it.entityType == "folder" && it.userId == "u1" && it.reassignToFolderId == "f2"
        }, null) }
    }

    @Test
    fun `observeFolders returns only the current account flow`() = runTest {
        val expected = listOf(FolderEntity("f1", "u1", "Work", null, 1L, "SYNCED"))
        every { dao.observeForUser("u1") } returns flowOf(expected)
        assertThat(repository.observeFolders().first()).isEqualTo(expected)
    }
}
