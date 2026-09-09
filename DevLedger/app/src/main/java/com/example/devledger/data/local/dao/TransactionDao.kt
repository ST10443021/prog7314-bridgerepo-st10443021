package com.devledger.app.data.local.dao

import androidx.room.*
import com.devledger.app.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY transactionDate DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE syncStatus = 'PENDING'")
    suspend fun getPendingSync(): List<TransactionEntity>

    @Upsert
    suspend fun upsert(transaction: TransactionEntity)

    @Query("UPDATE transactions SET syncStatus = :status WHERE transactionId = :id")
    suspend fun updateSyncStatus(id: String, status: String)

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE type = 'INCOME'")
    fun observeTotalIncome(): Flow<Double>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE type = 'EXPENSE'")
    fun observeTotalExpenses(): Flow<Double>

    @Delete
    suspend fun delete(transaction: TransactionEntity)
}
