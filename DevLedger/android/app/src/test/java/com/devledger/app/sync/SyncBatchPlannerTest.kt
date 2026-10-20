package com.devledger.app.sync

import com.devledger.app.data.local.entity.*
import com.devledger.app.data.remote.dto.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class SyncBatchPlannerTest {
    private val user = "user-a"
    private fun folder(id: String, parent: String? = null) = FolderEntity(id, user, id, parent, 1L, "PENDING")
    private fun plan(folders: List<FolderEntity> = emptyList(), tags: List<UserTagEntity> = emptyList(),
        snippets: List<SnippetEntity> = emptyList(), transactions: List<TransactionEntity> = emptyList(),
        deletions: List<DeletedEntity> = emptyList()) =
        SyncBatchPlanner.plan(user, snippets, transactions, folders, tags, emptyList(), deletions)

    @Test fun `parent dependency precedes child even across batch boundaries`() {
        val folders = (0..100).reversed().map { i -> folder("f$i", if (i == 0) null else "f${i - 1}") }
        val planned = plan(folders = folders)
        assertEquals((0..100).map { "f$it" }, planned.map { it.id })
        assertEquals("f99", planned.chunked(100)[0].last().id)
        assertEquals("f100", planned.chunked(100)[1].first().id)
    }

    @Test fun `folder and snippet reassignment precede folder deletion`() {
        val snippet = SnippetEntity("s", user, "new", "Code", "println(1)", "kotlin", null, "", createdAt = 1, updatedAt = 2)
        val planned = plan(folders = listOf(folder("new")), snippets = listOf(snippet),
            deletions = listOf(DeletedEntity("old", user, "folder", reassignToFolderId = "new")))
        assertEquals(listOf("new", "s", "old"), planned.map { it.id })
        val payload = Json.decodeFromString<DeleteSyncPayload>(planned.last().operation.payload)
        assertEquals("new", payload.reassignToFolderId)
        assertEquals(user, payload.userId)
    }

    @Test fun `create then delete never emits an upsert for the deleted row`() {
        val planned = plan(tags = listOf(UserTagEntity("t", user, "Kotlin", null, 1, "PENDING")),
            deletions = listOf(DeletedEntity("t", user, "tag")))
        assertEquals(1, planned.size)
        assertEquals("delete", planned.single().operation.action)
    }

    @Test fun `folder cycle fails without constructing an uploadable plan`() {
        assertThrows(IllegalStateException::class.java) {
            plan(folders = listOf(folder("a", "b"), folder("b", "a")))
        }
    }

    @Test fun `another accounts queued row is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            plan(folders = listOf(folder("a").copy(userId = "other")))
        }
    }

    @Test fun `acknowledgement must match exact submitted IDs without conflicts`() {
        val batch = plan(folders = listOf(folder("a")))
        SyncBatchPlanner.validateAcknowledgement(batch, SyncBatchResponse(listOf("a"), emptyList()))
        for (response in listOf(
            SyncBatchResponse(emptyList(), emptyList()),
            SyncBatchResponse(listOf("a", "a"), emptyList()),
            SyncBatchResponse(listOf("other"), emptyList()),
            SyncBatchResponse(listOf("a"), listOf("a")),
        )) assertThrows(IllegalStateException::class.java) { SyncBatchPlanner.validateAcknowledgement(batch, response) }
    }

    @Test fun `transaction deletion payload matches server generic deletion contract`() {
        val batch = plan(deletions = listOf(DeletedEntity("tx", user, "transaction", 1234)))
        val payload = Json.decodeFromString<DeleteSyncPayload>(batch.single().operation.payload)
        assertEquals("tx", payload.entityId)
        assertEquals(user, payload.userId)
        assertEquals(1234L, batch.single().operation.clientTimestamp)
    }
}
