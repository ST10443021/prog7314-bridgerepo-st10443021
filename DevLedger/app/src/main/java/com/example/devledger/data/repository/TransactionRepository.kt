package com.devledger.app.data.repository

import com.devledger.app.data.local.dao.TransactionDao
import com.devledger.app.data.local.entity.SyncStatus
import com.devledger.app.data.local.entity.TransactionEntity
import com.devledger.app.util.IdGenerator
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Offline-first income/expense repository backing the Financial Tracker screens. */
@Singleton
class TransactionRepository @Inject constructor(
    private val dao: TransactionDao,
) {
    fun observeAll(): Flow<List<TransactionEntity>> = dao.observeAll()
    fun observeTotalIncome(): Flow<Double> = dao.observeTotalIncome()
    fun observeTotalExpenses(): Flow<Double> = dao.observeTotalExpenses()

    suspend fun addTransaction(
        userId: String,
        type: String,
        amount: Double,
        currency: String,
        category: String,
        description: String?,
        transactionDate: Long,
    ): TransactionEntity {
        val tx = TransactionEntity(
            transactionId = IdGenerator.newId(),
            userId = userId,
            type = type,
            amount = amount,
            currency = currency,
            category = category,
            description = description,
            transactionDate = transactionDate,
            createdAt = System.currentTimeMillis(),
            syncStatus = SyncStatus.PENDING,
        )
        dao.upsert(tx)
        return tx
    }

    suspend fun getPendingSync() = dao.getPendingSync()
    suspend fun markSynced(id: String) = dao.updateSyncStatus(id, SyncStatus.SYNCED)
}
