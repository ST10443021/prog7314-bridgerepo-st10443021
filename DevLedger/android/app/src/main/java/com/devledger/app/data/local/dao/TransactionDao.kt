package com.devledger.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.devledger.app.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
/**
 * Reference scope: Room persistence.
 * (Google Developers, n.d.b). See REFERENCES-CODE.md for full entries.
 */
interface TransactionDao {

    @Query("SELECT * FROM transactions WHERE userId = :userId ORDER BY transactionDate DESC")
    fun observeAllForUser(userId: String): Flow<List<TransactionEntity>>

    @Query("""
        SELECT * FROM transactions
        WHERE userId = :userId AND type = :type
        AND (:query = '' OR category LIKE '%' || :query || '%' OR clientOrFrom LIKE '%' || :query || '%'
             OR description LIKE '%' || :query || '%' OR relatedProject LIKE '%' || :query || '%')
        ORDER BY transactionDate DESC
    """)
    fun observeByTypeForUser(userId: String, type: String, query: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE transactionId = :id LIMIT 1")
    suspend fun getById(id: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE userId = :userId AND syncStatus = 'PENDING' ORDER BY createdAt ASC")
    suspend fun getPendingSyncForUser(userId: String): List<TransactionEntity>

    @Query("SELECT DISTINCT relatedProject FROM transactions WHERE userId = :userId AND relatedProject IS NOT NULL AND relatedProject != '' ORDER BY relatedProject ASC")
    suspend fun getDistinctProjects(userId: String): List<String>

    @Query("SELECT * FROM transactions WHERE isRecurring = 1 AND nextOccurrenceDate IS NOT NULL AND nextOccurrenceDate <= :nowMillis")
    suspend fun getDueRecurring(nowMillis: Long): List<TransactionEntity>

    @Upsert
    suspend fun upsert(transaction: TransactionEntity)

    @Query("UPDATE transactions SET syncStatus = :status WHERE transactionId = :id")
    suspend fun updateSyncStatus(id: String, status: String)

    @Delete
    suspend fun delete(transaction: TransactionEntity)

    @Transaction
    suspend fun mergeRemoteTransactions(userId: String, remoteTransactions: List<TransactionEntity>) {
        for (remote in remoteTransactions) {
            require(remote.userId == userId) { "The downloaded transaction belongs to a different account." }
            val local = getById(remote.transactionId)
            require(local == null || local.userId == userId) { "A transaction ID conflicts with another cached account." }
            
            if (local == null) {
                upsert(remote)
            } else if (local.syncStatus == "SYNCED") {
                upsert(remote)
            }
        }
    }
}
