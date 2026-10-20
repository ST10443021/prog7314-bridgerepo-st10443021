package com.devledger.app.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.devledger.app.data.local.DevLedgerDatabase
import com.devledger.app.data.local.entity.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SyncRoomPersistenceTest {
    private lateinit var db: DevLedgerDatabase
    private val user = "a"
    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), DevLedgerDatabase::class.java)
            .allowMainThreadQueries().build()
    }
    @After fun tearDown() { db.close() }
    private fun transaction() = TransactionEntity("tx", user, "EXPENSE", 50.0, "ZAR", "Hosting", null,
        "Client", null, null, null, transactionDate = 1, createdAt = 1)

    @Test fun `transaction delete survives acknowledgement and stale download`() = runBlocking {
        val tx = transaction()
        db.transactionDao().upsert(tx)
        val marker = DeletedEntity(tx.transactionId, user, "transaction", 10)
        db.deletedDao().queueTransactionDeletion(marker)
        assertNull(db.transactionDao().getById(tx.transactionId))
        assertEquals(1, db.deletedDao().getForUser(user).size)
        assertEquals(1, db.deletedDao().acknowledge(user, "transaction", tx.transactionId, marker.deletedAt))
        assertTrue(db.deletedDao().getForUser(user).isEmpty())
        assertTrue(db.deletedDao().isDeleted(user, "transaction", tx.transactionId))
        db.transactionDao().mergeRemoteTransactions(user, listOf(tx.copy(syncStatus = "SYNCED")))
        assertNull(db.transactionDao().getById(tx.transactionId))
    }

    @Test fun `older delete acknowledgement does not clear a newer queued version`() = runBlocking {
        db.deletedDao().insert(DeletedEntity("tx", user, "transaction", 20))
        assertEquals(0, db.deletedDao().acknowledge(user, "transaction", "tx", 10))
        assertEquals(20L, db.deletedDao().getForUser(user).single().deletedAt)
    }

    @Test fun `folder delete atomically retains target and reassigns local snippets`() = runBlocking {
        db.folderDao().upsert(FolderEntity("old", user, "Old", null, 1, "SYNCED"))
        db.folderDao().upsert(FolderEntity("target", user, "Target", null, 1, "SYNCED"))
        db.folderDao().upsert(FolderEntity("child", user, "Child", "old", 1, "SYNCED"))
        db.snippetDao().upsert(SnippetEntity("s", user, "old", "Code", "println(1)", "kotlin", null, "", createdAt = 1, updatedAt = 1, syncStatus = "SYNCED"))
        db.deletedDao().queueFolderDeletion(DeletedEntity("old", user, "folder", reassignToFolderId = "target"), null)
        assertNull(db.folderDao().getById("old"))
        assertNull(db.folderDao().getById("child")!!.parentFolderId)
        assertEquals("target", db.snippetDao().getById("s")!!.folderId)
        assertEquals("PENDING", db.snippetDao().getById("s")!!.syncStatus)
        assertEquals("target", db.deletedDao().getForUser(user).single().reassignToFolderId)
    }

    @Test fun `tag download preserves pending edit and retained deletion`() = runBlocking {
        val pending = UserTagEntity("t", user, "New name", null, 1, "PENDING")
        db.userTagDao().upsert(pending)
        db.userTagDao().mergeRemoteTags(user, listOf(pending.copy(name = "Old name", syncStatus = "SYNCED")))
        assertEquals("New name", db.userTagDao().getById("t")!!.name)
        db.deletedDao().queueTagDeletion(DeletedEntity("t", user, "tag", 10))
        db.userTagDao().mergeRemoteTags(user, listOf(pending.copy(syncStatus = "SYNCED")))
        assertNull(db.userTagDao().getById("t"))
    }

    @Test fun `folder rename during upload remains pending`() = runBlocking {
        val uploaded = FolderEntity("f", user, "Old", null, 1, "PENDING")
        db.folderDao().upsert(uploaded.copy(name = "New"))
        assertFalse(db.folderDao().markSyncedIfUnchanged(uploaded))
        assertEquals("PENDING", db.folderDao().getById("f")!!.syncStatus)
    }

    @Test fun `delete wins over another devices pending transaction edit`() = runBlocking {
        db.transactionDao().upsert(transaction())
        db.deletedDao().applyRemoteDeletion(DeletedEntity("tx", user, "transaction", syncStatus = "SYNCED"))
        assertNull(db.transactionDao().getById("tx"))
        assertTrue(db.deletedDao().isDeleted(user, "transaction", "tx"))
        assertTrue(db.deletedDao().getForUser(user).isEmpty())
    }

    @Test fun `tag acknowledgement does not touch another account row`() = runBlocking {
        val tag = UserTagEntity("t", "other", "Other", null, 1, "PENDING")
        db.userTagDao().upsert(tag)
        assertFalse(db.userTagDao().markSyncedIfUnchanged(tag.copy(userId = user)))
        assertEquals("PENDING", db.userTagDao().getById("t")!!.syncStatus)
    }
}
