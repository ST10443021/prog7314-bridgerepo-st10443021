package com.devledger.app.data.repository

import android.content.Context
import android.util.Log
import com.devledger.app.data.local.dao.DeletedDao
import com.devledger.app.data.local.dao.TransactionDao
import com.devledger.app.data.local.dao.UserDao
import com.devledger.app.data.local.entity.DeletedEntity
import com.devledger.app.data.local.entity.SyncStatus
import com.devledger.app.data.local.entity.TransactionEntity
import com.devledger.app.data.remote.DevLedgerApiService
import com.devledger.app.data.remote.auth.TokenManager
import com.devledger.app.sync.SyncScheduler
import com.devledger.app.util.IdGenerator
import dagger.hilt.android.qualifiers.ApplicationContext
import retrofit2.HttpException
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/** Offline-first income/expense repository. Writes land in Room immediately, marked
 *  PENDING, and are queued for WorkManager synchronisation with the DevLedger API. */
@Singleton
class TransactionRepository @Inject constructor(
    private val dao: TransactionDao,
    private val deletedDao: DeletedDao,
    private val userDao: UserDao,
    private val settingsRepository: SettingsRepository,
    private val api: DevLedgerApiService,
    private val tokenManager: TokenManager,
    @ApplicationContext private val context: Context,
) {
    fun observeAllForUser(userId: String): Flow<List<TransactionEntity>> = dao.observeAllForUser(userId)

    fun observeByType(userId: String, type: String, query: String): Flow<List<TransactionEntity>> =
        dao.observeByTypeForUser(userId, type, query.trim())

    suspend fun refreshFromApi() {
        val token = tokenManager.getAccessToken()?.takeIf { it.isNotBlank() } ?: return
        val user = userDao.getCurrentUser() ?: return
        
        val response = api.getTransactions()
        if (!response.isSuccessful) throw HttpException(response)
        
        val remoteTransactions = response.body() ?: return
        
        if (tokenManager.getAccessToken() != token) return
        
        val entities = remoteTransactions.map { remote ->
            TransactionEntity(
                transactionId = remote.transactionId,
                userId = user.userId,
                type = remote.type,
                amount = remote.amount,
                currency = remote.currency,
                category = remote.category,
                description = remote.description,
                clientOrFrom = remote.clientOrFrom,
                relatedProject = remote.relatedProject,
                paymentMethod = remote.paymentMethod,
                referenceNumber = remote.referenceNumber,
                isRecurring = remote.isRecurring,
                recurringFrequency = remote.recurringFrequency,
                nextOccurrenceDate = remote.nextOccurrenceDate,
                attachmentFileName = remote.attachmentFileName,
                attachmentBase64 = null,
                transactionDate = remote.transactionDate,
                createdAt = System.currentTimeMillis(),
                syncStatus = SyncStatus.SYNCED
            )
        }
        dao.mergeRemoteTransactions(user.userId, entities)
    }

    suspend fun getById(id: String): TransactionEntity? = dao.getById(id)

    suspend fun getDistinctProjects(userId: String): List<String> = dao.getDistinctProjects(userId)

    suspend fun addTransaction(
        userId: String,
        type: String,
        amount: Double,
        currency: String,
        category: String,
        description: String?,
        clientOrFrom: String,
        relatedProject: String?,
        paymentMethod: String?,
        referenceNumber: String?,
        isRecurring: Boolean,
        recurringFrequency: String?,
        nextOccurrenceDate: Long?,
        attachmentFileName: String?,
        attachmentBase64: String?,
        transactionDate: Long,
    ): TransactionEntity {
        require(userId.isNotBlank()) { "Sign in before saving a transaction." }
        require(type in setOf("INCOME", "EXPENSE")) { "Transaction type must be INCOME or EXPENSE." }
        require(amount.isFinite() && amount > 0.0) { "Transaction amount must be greater than zero." }
        require(category.isNotBlank()) { "Transaction category is required." }
        require(clientOrFrom.isNotBlank()) { "Client/From is required." }
        require(!isRecurring || !recurringFrequency.isNullOrBlank()) { "Choose a frequency for a recurring transaction." }

        val now = System.currentTimeMillis()
        val tx = TransactionEntity(
            transactionId = IdGenerator.newId(),
            userId = userId,
            type = type,
            amount = amount,
            currency = currency.trim().uppercase(Locale.ROOT),
            category = category.trim(),
            description = description?.trim()?.takeIf { it.isNotEmpty() },
            clientOrFrom = clientOrFrom.trim(),
            relatedProject = relatedProject?.trim()?.takeIf { it.isNotEmpty() },
            paymentMethod = paymentMethod,
            referenceNumber = referenceNumber?.trim()?.takeIf { it.isNotEmpty() },
            isRecurring = isRecurring,
            recurringFrequency = if (isRecurring) recurringFrequency else null,
            nextOccurrenceDate = if (isRecurring) nextOccurrenceDate else null,
            attachmentFileName = attachmentFileName,
            attachmentBase64 = attachmentBase64,
            transactionDate = transactionDate,
            createdAt = now,
            syncStatus = SyncStatus.PENDING,
        )
        dao.upsert(tx)
        queueSync()
        return tx
    }

    suspend fun delete(transaction: TransactionEntity) {
        val user = userDao.getCurrentUser() ?: return
        deletedDao.insert(DeletedEntity(
            entityId = transaction.transactionId,
            userId = user.userId,
            entityType = "transaction"
        ))
        dao.delete(transaction)
        queueSync()
    }

    /** Called by [com.devledger.app.sync.RecurringTransactionWorker]: creates the next
     *  occurrence of a due recurring transaction and advances its own nextOccurrenceDate,
     *  so it fires again on the following cycle rather than repeating immediately. */
    suspend fun rollForwardRecurring(original: TransactionEntity): TransactionEntity {
        val nextDate = advanceDate(original.nextOccurrenceDate ?: original.transactionDate, original.recurringFrequency)
        val newTransaction = original.copy(
            transactionId = IdGenerator.newId(),
            transactionDate = original.nextOccurrenceDate ?: System.currentTimeMillis(),
            createdAt = System.currentTimeMillis(),
            syncStatus = SyncStatus.PENDING,
        )
        dao.upsert(newTransaction)
        dao.upsert(original.copy(nextOccurrenceDate = nextDate, syncStatus = SyncStatus.PENDING))
        queueSync()
        return newTransaction
    }

    suspend fun getDueRecurring(): List<TransactionEntity> = dao.getDueRecurring(System.currentTimeMillis())

    suspend fun getPendingSyncForUser(userId: String): List<TransactionEntity> = dao.getPendingSyncForUser(userId)
    suspend fun markSynced(id: String) = dao.updateSyncStatus(id, SyncStatus.SYNCED)

    private fun advanceDate(fromMillis: Long, frequency: String?): Long {
        val calendar = Calendar.getInstance().apply { timeInMillis = fromMillis }
        when (frequency) {
            "WEEKLY" -> calendar.add(Calendar.WEEK_OF_YEAR, 1)
            "MONTHLY" -> calendar.add(Calendar.MONTH, 1)
            "YEARLY" -> calendar.add(Calendar.YEAR, 1)
            else -> calendar.add(Calendar.MONTH, 1)
        }
        return calendar.timeInMillis
    }

    private suspend fun queueSync() {
        try {
            val wifiOnly = settingsRepository.wifiOnlySync.first()
            SyncScheduler.schedulePeriodicSync(context, wifiOnly)
            SyncScheduler.syncNow(context, wifiOnly)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e("TransactionRepository", "Could not schedule transaction sync. Transaction remains PENDING.", error)
        }
    }
}
