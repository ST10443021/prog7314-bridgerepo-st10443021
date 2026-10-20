package com.devledger.app.repository

import android.content.Context
import com.devledger.app.data.local.dao.DeletedDao
import com.devledger.app.data.local.dao.UserDao
import com.devledger.app.data.local.dao.UserTagDao
import com.devledger.app.data.local.entity.UserEntity
import com.devledger.app.data.remote.DevLedgerApiService
import com.devledger.app.data.repository.SettingsRepository
import com.devledger.app.data.repository.UserTagRepository
import com.devledger.app.util.DevLedgerResult
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

class UserTagRepositoryTest {
    private lateinit var dao: UserTagDao
    private lateinit var deletedDao: DeletedDao
    private lateinit var userDao: UserDao
    private lateinit var repository: UserTagRepository

    @Before
    fun setUp() {
        dao = mockk(relaxed = true)
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
        every { SyncScheduler.schedulePeriodicSync(any(), any()) } returns Unit
        every { SyncScheduler.syncNow(any(), any()) } returns Unit
        repository = UserTagRepository(dao, deletedDao, userDao, api, settings, context)
    }

    @After
    fun tearDown() = unmockkObject(SyncScheduler)

    @Test
    fun `create stores a pending user tag`() = runTest {
        val result = repository.create("  Kotlin  ", "#123456")
        assertThat(result).isInstanceOf(DevLedgerResult.Success::class.java)
        val tag = (result as DevLedgerResult.Success).data
        assertThat(tag.name).isEqualTo("Kotlin")
        assertThat(tag.colorHex).isEqualTo("#123456")
        assertThat(tag.syncStatus).isEqualTo("PENDING")
        coVerify(exactly = 1) { dao.upsert(tag) }
    }

    @Test
    fun `delete records a tag tombstone and removes the local row`() = runTest {
        val existing = com.devledger.app.data.local.entity.UserTagEntity("t1", "u1", "Kotlin", "#123456", 1L, "SYNCED")
        coEvery { dao.getById("t1") } returns existing
        val result = repository.delete("t1")
        assertThat(result).isInstanceOf(DevLedgerResult.Success::class.java)
        coVerify(exactly = 1) { deletedDao.queueTagDeletion(match { it.entityId == "t1" && it.entityType == "tag" && it.userId == "u1" }) }
        // Real Room tests verify the atomic row removal and retained tombstone.
    }

    @Test
    fun `observeTags is scoped to the current account`() = runTest {
        val expected = listOf(com.devledger.app.data.local.entity.UserTagEntity("t1", "u1", "Kotlin", null, 1L, "SYNCED"))
        every { dao.observeForUser("u1") } returns flowOf(expected)
        assertThat(repository.observeTags().first()).isEqualTo(expected)
    }
}
