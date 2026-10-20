package com.devledger.app.sync

import com.devledger.app.data.local.entity.*
import com.devledger.app.ui.screens.sync.*
import org.junit.Assert.*
import org.junit.Test

class SyncStatusModelTest {
    @Test fun `empty queue alone is not a successful sync`() {
        assertFalse(SyncUiState(accountId = "user", isLoaded = true).isUpToDate)
        val verified = SyncUiState(accountId = "user", isLoaded = true, lastCompletedAt = 1, runState = SyncRunState.SUCCEEDED)
        assertTrue(verified.isUpToDate)
        for (status in SyncRunState.entries.filter { it != SyncRunState.SUCCEEDED })
            assertFalse(verified.copy(runState = status).isUpToDate)
        assertFalse(verified.copy(error = "download failed").isUpToDate)
        assertFalse(verified.copy(accountId = null).isUpToDate)
    }
    @Test fun `archived edits and all deletion outboxes are represented without acknowledged markers`() {
        val archived = SnippetEntity("s", "user", null, "Archived", "content", "kotlin", null, "", isArchived = true,
            createdAt = 1, updatedAt = 1)
        val queue = SyncStatusModel.queue(listOf(archived, archived.copy(snippetId = "d", syncStatus = "DELETE_PENDING")),
            emptyList(), emptyList(), emptyList(), listOf(DeletedEntity("tx", "user", "transaction"),
                DeletedEntity("tag", "user", "tag"), DeletedEntity("folder", "user", "folder"),
                DeletedEntity("synced", "user", "folder", syncStatus = "SYNCED")))
        assertEquals(5, queue.size)
        assertEquals(4, queue.count { it.direction == "DELETE" })
        assertFalse(SyncUiState(accountId = "user", isLoaded = true, lastCompletedAt = 1,
            runState = SyncRunState.SUCCEEDED, uploadQueue = queue).isUpToDate)
    }
    @Test fun `background work drives live status and other accounts progress is ignored`() {
        val mine = SyncWorkSnapshot("user", "RUNNING", false, 10, 2, 6, "Downloading")
        val other = SyncWorkSnapshot("other", "RUNNING", true, 20, 5, 6, "Uploading")
        val live = liveSyncStatus("user", false, false, "COMPLETED", listOf(other, mine))
        assertEquals(SyncRunState.RUNNING, live.state)
        assertEquals(2, live.processed)
        assertEquals("Downloading", live.phase)
        assertEquals(0, liveSyncStatus("user", false, false, "COMPLETED", listOf(other)).processed)
    }
    @Test fun `finished manual work never hides a newer persisted failure or background attempt`() {
        val oldSuccess = SyncWorkSnapshot("user", "SUCCEEDED", true)
        assertEquals(SyncRunState.FAILED, liveSyncStatus("user", false, false, "FAILED", listOf(oldSuccess)).state)
        val periodic = SyncWorkSnapshot("user", "RUNNING", false, 10)
        assertEquals(SyncRunState.RUNNING, liveSyncStatus("user", false, false, "FAILED", listOf(oldSuccess, periodic)).state)
    }
    @Test fun `scheduled periodic work is waiting and does not disable manual sync`() {
        val waiting = liveSyncStatus("user", true, false, "COMPLETED", listOf(SyncWorkSnapshot(null, "ENQUEUED", false)))
        assertEquals(SyncRunState.WAITING, waiting.state)
        assertFalse(SyncUiState(runState = waiting.state).isSyncing)
    }
    @Test fun `retry conflict cancellation and account switch never look complete`() {
        for (status in listOf("RETRY", "PARTIAL", "CONFLICT", "CANCELLED", "FAILED"))
            assertNotEquals(SyncRunState.SUCCEEDED, liveSyncStatus("user", false, false, status, emptyList()).state)
        assertEquals(SyncRunState.CONFLICT, liveSyncStatus("user", false, true, "COMPLETED", emptyList()).state)
        assertEquals(SyncRunState.IDLE, liveSyncStatus(null, false, false, "COMPLETED", emptyList()).state)
    }
}
