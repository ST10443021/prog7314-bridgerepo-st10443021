package com.devledger.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.devledger.app.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Query(
        """
        SELECT * FROM transactions
        WHERE userId = :userId
        ORDER BY transactionDate DESC
        """
    )
    fun observeAllForUser(userId: String): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT * FROM transactions
        WHERE userId = :userId
        AND syncStatus = 'PENDING'
        ORDER BY createdAt ASC
        """
    )
    suspend fun getPendingSyncForUser(userId: String): List<TransactionEntity>

    @Upsert
    suspend fun upsert(transaction: TransactionEntity)

    @Query(
        """
        UPDATE transactions
        SET syncStatus = :status
        WHERE transactionId = :id
        """
    )
    suspend fun updateSyncStatus(id: String, status: String)

    @Delete
    suspend fun delete(transaction: TransactionEntity)
}