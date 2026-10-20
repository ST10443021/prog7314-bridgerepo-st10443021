package com.devledger.app.repository

import android.content.Context
import com.devledger.app.data.local.dao.TransactionDao
import com.devledger.app.data.local.dao.UserDao
import com.devledger.app.data.local.entity.SyncStatus
import com.devledger.app.data.local.entity.UserEntity
import com.devledger.app.data.repository.NotificationRepository
import com.devledger.app.data.repository.SettingsRepository
import com.devledger.app.data.repository.TransactionRepository
import com.devledger.app.data.remote.DevLedgerApiService
import com.devledger.app.data.remote.auth.TokenManager
import com.devledger.app.sync.SyncScheduler
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

/** Unit tests for validation and offline-first persistence in TransactionRepository. */
class TransactionRepositoryTest {

    private lateinit var dao: TransactionDao
    private lateinit var deletedDao: com.devledger.app.data.local.dao.DeletedDao
    private lateinit var userDao: UserDao
    private lateinit var notificationRepository: NotificationRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var api: DevLedgerApiService
    private lateinit var tokenManager: TokenManager
    private lateinit var context: Context
    private lateinit var repository: TransactionRepository

    @Before
    fun setUp() {
        dao = mockk(relaxed = true)
        deletedDao = mockk(relaxed = true)
        userDao = mockk(relaxed = true)
        notificationRepository = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        api = mockk(relaxed = true)
        tokenManager = mockk(relaxed = true)
        context = mockk(relaxed = true)

        every { settingsRepository.wifiOnlySync } returns flowOf(false)
        coEvery { userDao.getCurrentUser() } returns UserEntity(
            userId = "user-1",
            email = "test@devledger.app",
            fullName = "Test User",
            ssoProvider = "GOOGLE",
        )

        mockkObject(SyncScheduler)
        every { SyncScheduler.schedulePeriodicSync(any(), any()) } returns Unit
        every { SyncScheduler.syncNow(any(), any()) } returns Unit

        repository = TransactionRepository(
            dao = dao,
            deletedDao = deletedDao,
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
    fun `addTransaction persists an expense as PENDING and normalises currency`() = runTest {
        val tx = repository.addTransaction(
            userId = "user-1",
            type = "EXPENSE",
            amount = 450.0,
            currency = "zar",
            category = " Hosting ",
            description = "Azure App Service",
            clientOrFrom = "Test client",
            relatedProject = null,
            paymentMethod = null,
            referenceNumber = null,
            isRecurring = false,
            recurringFrequency = null,
            nextOccurrenceDate = null,
            attachmentFileName = null,
            attachmentBase64 = null,
            transactionDate = 1_700_000_000_000L,
        )

        assertThat(tx.type).isEqualTo("EXPENSE")
        assertThat(tx.amount).isEqualTo(450.0)
        assertThat(tx.currency).isEqualTo("ZAR")
        assertThat(tx.category).isEqualTo("Hosting")
        assertThat(tx.syncStatus).isEqualTo(SyncStatus.PENDING)
        coVerify(exactly = 1) { dao.upsert(tx) }
        coVerify(exactly = 1) { notificationRepository.notify("Expense Added", any(), "TRANSACTION") }
    }

    @Test
    fun `addTransaction supports income transactions with a positive amount`() = runTest {
        val tx = repository.addTransaction(
            userId = "user-1",
            type = "INCOME",
            amount = 2500.0,
            currency = "ZAR",
            category = "Freelance",
            description = null,
            clientOrFrom = "Test client",
            relatedProject = null,
            paymentMethod = null,
            referenceNumber = null,
            isRecurring = false,
            recurringFrequency = null,
            nextOccurrenceDate = null,
            attachmentFileName = null,
            attachmentBase64 = null,
            transactionDate = 1_700_000_000_000L,
        )

        assertThat(tx.type).isEqualTo("INCOME")
        assertThat(tx.amount).isGreaterThan(0.0)
        assertThat(tx.syncStatus).isEqualTo(SyncStatus.PENDING)
    }

    @Test
    fun `addTransaction rejects zero or negative amounts`() = runTest {
        val error = runCatching {
            repository.addTransaction(
                userId = "user-1",
                type = "EXPENSE",
                amount = 0.0,
                currency = "ZAR",
                category = "Hosting",
                description = null,
                clientOrFrom = "DevLedger",
                relatedProject = null,
                paymentMethod = null,
                referenceNumber = null,
                isRecurring = false,
                recurringFrequency = null,
                nextOccurrenceDate = null,
                attachmentFileName = null,
                attachmentBase64 = null,
                transactionDate = 1L,
            )
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(error?.message).isEqualTo("Transaction amount must be greater than zero.")
        coVerify(exactly = 0) { dao.upsert(any()) }
    }

    @Test
    fun `addTransaction rejects recurring transaction without a frequency`() = runTest {
        val error = runCatching {
            repository.addTransaction(
                userId = "user-1",
                type = "EXPENSE",
                amount = 100.0,
                currency = "ZAR",
                category = "Hosting",
                description = null,
                clientOrFrom = "DevLedger",
                relatedProject = null,
                paymentMethod = null,
                referenceNumber = null,
                isRecurring = true,
                recurringFrequency = null,
                nextOccurrenceDate = null,
                attachmentFileName = null,
                attachmentBase64 = null,
                transactionDate = 1L,
            )
        }.exceptionOrNull()

        assertThat(error?.message).isEqualTo("Choose a frequency for a recurring transaction.")
        coVerify(exactly = 0) { dao.upsert(any()) }
    }

    @Test
    fun `markSynced updates the DAO sync status to SYNCED`() = runTest {
        repository.markSynced("tx-1")

        coVerify(exactly = 1) { dao.updateSyncStatus("tx-1", SyncStatus.SYNCED) }
    }
}
