package com.devledger.app.repository

import com.devledger.app.data.local.dao.TransactionDao
import com.devledger.app.data.local.entity.SyncStatus
import com.devledger.app.data.repository.TransactionRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionRepositoryTest {

    private lateinit var dao: TransactionDao
    private lateinit var repository: TransactionRepository

    @Before
    fun setUp() {
        dao = mockk(relaxed = true)
        repository = TransactionRepository(dao)
    }

    @Test
    fun `addTransaction persists an expense with a PENDING sync status`() = runTest {
        val tx = repository.addTransaction(
            userId = "user-1",
            type = "EXPENSE",
            amount = 450.0,
            currency = "ZAR",
            category = "Hosting",
            description = "Azure App Service",
            transactionDate = 1_700_000_000_000L,
        )

        assertThat(tx.type).isEqualTo("EXPENSE")
        assertThat(tx.amount).isEqualTo(450.0)
        assertThat(tx.syncStatus).isEqualTo(SyncStatus.PENDING)
        coVerify(exactly = 1) { dao.upsert(any()) }
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
            transactionDate = 1_700_000_000_000L,
        )
        assertThat(tx.type).isEqualTo("INCOME")
        assertThat(tx.amount).isGreaterThan(0.0)
    }

    @Test
    fun `markSynced updates the DAO sync status to SYNCED`() = runTest {
        repository.markSynced("tx-1")
        coVerify(exactly = 1) { dao.updateSyncStatus("tx-1", SyncStatus.SYNCED) }
    }
}
