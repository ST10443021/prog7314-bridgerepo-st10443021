package com.devledger.app.data.repository

import android.content.Context
import android.util.Log
import com.devledger.app.data.local.dao.TransactionDao
import com.devledger.app.data.local.entity.SyncStatus
import com.devledger.app.data.local.entity.TransactionEntity
import com.devledger.app.sync.SyncScheduler
import com.devledger.app.util.IdGenerator
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Offline-first income/expense repository.
 *
 * Transactions are written to Room immediately, marked PENDING, and queued for
 * WorkManager synchronisation with the DevLedger API.
 */
@Singleton
class TransactionRepository @Inject constructor(
    private val dao: TransactionDao,
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val context: Context,
) {

    fun observeAllForUser(userId: String): Flow<List<TransactionEntity>> =
        dao.observeAllForUser(userId)

    suspend fun addTransaction(
        userId: String,
        type: String,
        amount: Double,
        currency: String,
        category: String,
        description: String?,
        transactionDate: Long,
    ): TransactionEntity {
        require(userId.isNotBlank()) {
            "Sign in before saving a transaction."
        }

        require(type in setOf("INCOME", "EXPENSE")) {
            "Transaction type must be INCOME or EXPENSE."
        }

        require(amount.isFinite() && amount > 0.0) {
            "Transaction amount must be greater than zero."
        }

        require(category.isNotBlank()) {
            "Transaction category is required."
        }

        val now = System.currentTimeMillis()

        val tx = TransactionEntity(
            transactionId = IdGenerator.newId(),
            userId = userId,
            type = type,
            amount = amount,
            currency = currency.trim().uppercase(Locale.ROOT),
            category = category.trim(),
            description = description?.trim()?.takeIf { it.isNotEmpty() },
            transactionDate = transactionDate,
            createdAt = now,
            syncStatus = SyncStatus.PENDING,
        )

        dao.upsert(tx)

        // The Room save remains valid even if WorkManager cannot be scheduled.
        queueSync()

        return tx
    }

    suspend fun getPendingSyncForUser(userId: String): List<TransactionEntity> =
        dao.getPendingSyncForUser(userId)

    suspend fun markSynced(id: String) =
        dao.updateSyncStatus(id, SyncStatus.SYNCED)

    private suspend fun queueSync() {
        try {
            val wifiOnly = settingsRepository.wifiOnlySync.first()

            SyncScheduler.schedulePeriodicSync(
                context = context,
                wifiOnly = wifiOnly,
            )

            SyncScheduler.syncNow(
                context = context,
                wifiOnly = wifiOnly,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e(
                "TransactionRepository",
                "Could not schedule transaction sync. Transaction remains PENDING.",
                error,
            )
        }
    }
}