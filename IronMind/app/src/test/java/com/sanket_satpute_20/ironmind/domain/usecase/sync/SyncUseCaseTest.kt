package com.sanket_satpute_20.ironmind.domain.usecase.sync

import com.sanket_satpute_20.ironmind.data.sync.SyncOrchestrator
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.OutboxEntry
import com.sanket_satpute_20.ironmind.domain.model.OutboxOperationType
import com.sanket_satpute_20.ironmind.domain.model.SyncStatus
import com.sanket_satpute_20.ironmind.testutil.fake.FakeOutboxRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeSyncRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SyncUseCaseTest {

    private lateinit var outboxRepository: FakeOutboxRepository
    private lateinit var syncRepository: FakeSyncRepository
    private lateinit var orchestrator: SyncOrchestrator
    private lateinit var syncUseCase: SyncUseCase

    @Before
    fun setup() {
        outboxRepository = FakeOutboxRepository()
        syncRepository = FakeSyncRepository()
        orchestrator = SyncOrchestrator(outboxRepository, syncRepository)
        syncUseCase = SyncUseCase(orchestrator)
    }

    private fun entry(id: String, entityType: String = "Goal", status: SyncStatus = SyncStatus.PENDING, retryCount: Int = 0) =
        OutboxEntry(
            operationId = id,
            entityType = entityType,
            entityId = "entity-$id",
            operationType = OutboxOperationType.UPSERT,
            payload = """{"id":"entity-$id","title":"Test"}""",
            createdAt = 1000L,
            retryCount = retryCount,
            lastAttemptAt = null,
            status = status
        )

    // --- offline creation ---
    @Test
    fun `offline creation - entry inserted into outbox successfully`() = runTest {
        val e = entry("op-1")
        val insertResult = outboxRepository.insertEntry(e)
        assertTrue(insertResult is Result.Success)

        val pendingResult = outboxRepository.getPendingEntries()
        assertTrue(pendingResult is Result.Success)
        assertEquals(1, (pendingResult as Result.Success).data.size)
    }

    // --- reconnect / upload ---
    @Test
    fun `sync with pending entry uploads it and removes from outbox`() = runTest {
        outboxRepository.insertEntry(entry("op-2"))

        val result = syncUseCase("user-1")

        assertTrue(result is Result.Success)
        val state = (result as Result.Success).data
        assertEquals(1, syncRepository.uploadedEntries.size)
        assertEquals("op-2", syncRepository.uploadedEntries[0].operationId)
        // Entry must be removed from outbox after confirmed upload
        assertNull(outboxRepository.getEntry("op-2"))
        assertEquals(0, state.pendingCount)
    }

    // --- idempotent duplicate retry ---
    @Test
    fun `inserting the same operationId twice does not create duplicate`() = runTest {
        val e = entry("op-dup")
        outboxRepository.insertEntry(e)
        outboxRepository.insertEntry(e) // same operationId — should be ignored

        val pending = (outboxRepository.getPendingEntries() as Result.Success).data
        assertEquals(1, pending.size)
    }

    @Test
    fun `retrying a failed entry does not duplicate uploads`() = runTest {
        val e = entry("op-retry", status = SyncStatus.FAILED, retryCount = 1)
        outboxRepository.insertEntry(e)

        val result = syncUseCase("user-1")

        assertTrue(result is Result.Success)
        // Uploaded exactly once despite it being a retry
        assertEquals(1, syncRepository.uploadedEntries.size)
        assertNull(outboxRepository.getEntry("op-retry"))
    }

    // --- conflict ---
    @Test
    fun `conflict is recorded in outbox status without overwriting remote`() = runTest {
        val e = entry("op-conflict")
        outboxRepository.insertEntry(e)
        syncRepository.conflictOperationIds.add("op-conflict")

        val result = syncUseCase("user-1")

        assertTrue(result is Result.Success)
        // Entry must still be in outbox but marked CONFLICT
        val stored = outboxRepository.getEntry("op-conflict")!!
        assertEquals(SyncStatus.CONFLICT, stored.status)
        // Nothing was uploaded
        assertEquals(0, syncRepository.uploadedEntries.size)
    }

    // --- partial failure ---
    @Test
    fun `partial failure - successful entries synced, failed entries marked FAILED`() = runTest {
        outboxRepository.insertEntry(entry("op-ok"))
        outboxRepository.insertEntry(entry("op-fail"))
        syncRepository.failingOperationIds.add("op-fail")

        val result = syncUseCase("user-1")

        assertTrue(result is Result.Success)
        val state = (result as Result.Success).data

        // op-ok uploaded and removed
        assertNull(outboxRepository.getEntry("op-ok"))
        assertEquals(1, syncRepository.uploadedEntries.size)

        // op-fail still in outbox, marked FAILED
        val failedEntry = outboxRepository.getEntry("op-fail")!!
        assertEquals(SyncStatus.FAILED, failedEntry.status)
        assertEquals(1, failedEntry.retryCount)
        assertEquals(1, state.pendingCount)
    }

    // --- empty outbox ---
    @Test
    fun `sync with empty outbox returns idle state without errors`() = runTest {
        val result = syncUseCase("user-1")

        assertTrue(result is Result.Success)
        val state = (result as Result.Success).data
        assertTrue(state.isIdle)
        assertEquals(0, state.pendingCount)
        assertNull(state.lastError)
    }
}
