package com.devledger.app.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.devledger.app.data.local.DevLedgerDatabase
import com.devledger.app.data.local.entity.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Reference scope: JVM unit testing for Android with JUnit, MockK and Truth. (Google Developers, n.d.s). See REFERENCES-CODE.md for full entries.

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SyncConflictPersistenceTest {
    private lateinit var db: DevLedgerDatabase
    private val user = "user"
    private fun row() = SnippetEntity("s", user, null, "Code", "local", "kotlin", "notes", "kotlin",
        createdAt = 1, updatedAt = 2)
    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), DevLedgerDatabase::class.java)
            .allowMainThreadQueries().build()
    }
    @After fun tearDown() { db.close() }

    @Test fun `conflict draft persists outside upload queue and survives server refresh`() = runBlocking {
        val local = row(); val dao = db.snippetDao()
        dao.upsert(local)
        assertTrue(dao.flagConflictIfUnchanged(local))
        dao.mergeRemoteSnippets(user, listOf(local.copy(content = "server", updatedAt = 3, syncStatus = "SYNCED")))
        assertEquals("local", dao.getById("s")!!.content)
        assertEquals(1, dao.conflictCount(user))
        assertTrue(dao.getPendingSyncForUser(user).isEmpty())
        assertEquals("CONFLICT", dao.observeForSync(user).first().single().syncStatus)
    }
    @Test fun `conflict flag does not capture an edit made during upload`() = runBlocking {
        val local = row(); val dao = db.snippetDao()
        dao.upsert(local.copy(content = "newer", updatedAt = 3))
        assertFalse(dao.flagConflictIfUnchanged(local))
        assertEquals("PENDING", dao.getById("s")!!.syncStatus)
    }
    @Test fun `keep both preserves the draft and replaces original with server even at equal timestamp`() = runBlocking {
        val local = row().copy(syncStatus = "CONFLICT"); val dao = db.snippetDao()
        dao.upsert(local)
        val server = local.copy(content = "server", syncStatus = "SYNCED")
        dao.keepBothConflictVersions(local, server, false, "copy", 10)
        assertEquals(server, dao.getById("s"))
        val copy = dao.getById("copy")!!
        assertEquals(local.content, copy.content)
        assertEquals(local.description, copy.description)
        assertEquals(local.tagsCsv, copy.tagsCsv)
        assertEquals("PENDING", copy.syncStatus)
        assertEquals(10L, copy.createdAt)
        assertEquals(0, dao.conflictCount(user))
    }
    @Test fun `server deletion preserves unresolved conflict until explicit copy recovery`() = runBlocking {
        val local = row().copy(syncStatus = "CONFLICT"); val dao = db.snippetDao()
        dao.upsert(local)
        dao.mergeRemoteSnippets(user, emptyList(), listOf("s"))
        assertEquals(local, dao.getById("s"))
        dao.keepBothConflictVersions(local, null, true, "copy", 10)
        assertEquals("DELETED", dao.getById("s")!!.syncStatus)
        assertEquals("local", dao.getById("copy")!!.content)
    }
    @Test fun `inflight recovery cannot discard a newer local edit`() = runBlocking {
        val local = row().copy(syncStatus = "CONFLICT"); val dao = db.snippetDao()
        val newer = local.copy(content = "newer", updatedAt = 3)
        dao.upsert(newer)
        assertTrue(runCatching { dao.keepBothConflictVersions(local, local.copy(content = "server"), false, "copy", 10) }.isFailure)
        assertEquals(newer, dao.getById("s"))
        assertNull(dao.getById("copy"))
    }
    @Test fun `another accounts server row cannot resolve this draft`() = runBlocking {
        val local = row().copy(syncStatus = "CONFLICT"); val dao = db.snippetDao()
        dao.upsert(local)
        assertTrue(runCatching { dao.keepBothConflictVersions(local, local.copy(userId = "other"), false, "copy", 10) }.isFailure)
        assertEquals(local, dao.getById("s"))
        assertNull(dao.getById("copy"))
    }
    @Test fun `sync observation includes archived edits and snippet deletions`() = runBlocking {
        val dao = db.snippetDao()
        dao.upsert(row().copy(isArchived = true))
        dao.upsert(row().copy(snippetId = "deleting", isArchived = true, syncStatus = "DELETE_PENDING"))
        dao.upsert(row().copy(snippetId = "erased", syncStatus = "DELETED"))
        assertEquals(setOf("s", "deleting"), dao.observeForSync(user).first().map { it.snippetId }.toSet())
    }
    @Test fun `pending generic deletion stream removes acknowledged markers only`() = runBlocking {
        val dao = db.deletedDao()
        dao.insert(DeletedEntity("mine", user, "transaction", 1))
        dao.insert(DeletedEntity("other", "other", "tag", 1))
        assertEquals("mine", dao.observePendingForUser(user).first().single().entityId)
        dao.acknowledge(user, "transaction", "mine", 1)
        assertTrue(dao.observePendingForUser(user).first().isEmpty())
        assertNotNull(dao.getById("mine"))
    }
    @Test fun `editing and toggling flags keep a conflict out of normal uploads`() = runBlocking {
        val local = row().copy(syncStatus = "CONFLICT"); val dao = db.snippetDao()
        dao.upsert(local)
        dao.saveUnlessDeleted(local.copy(content = "revised draft", updatedAt = 3, syncStatus = "PENDING"))
        dao.setFavourite("s", true, 4)
        dao.setArchived("s", true, 5)
        assertEquals("CONFLICT", dao.getById("s")!!.syncStatus)
        assertEquals("revised draft", dao.getById("s")!!.content)
        assertTrue(dao.getPendingSyncForUser(user).isEmpty())
    }
    @Test fun `folder reassignment cannot silently release a conflicted draft`() = runBlocking {
        val local = row().copy(folderId = "old", syncStatus = "CONFLICT")
        db.snippetDao().upsert(local)
        db.deletedDao().queueFolderDeletion(DeletedEntity("old", user, "folder", 10, reassignToFolderId = "new"), null)
        val held = db.snippetDao().getById("s")!!
        assertEquals("new", held.folderId)
        assertEquals("CONFLICT", held.syncStatus)
        assertTrue(db.snippetDao().getPendingSyncForUser(user).isEmpty())
    }

}
