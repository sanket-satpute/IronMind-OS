package com.sanket_satpute_20.ironmind.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.OutboxEntry
import com.sanket_satpute_20.ironmind.domain.model.OutboxOperationType
import com.sanket_satpute_20.ironmind.domain.model.SyncStatus
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FirestoreSyncRepositoryTest {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var syncRepository: FirestoreSyncRepository

    @Before
    fun setUp() {
        firestore = mockk(relaxed = true)
        syncRepository = FirestoreSyncRepository(firestore)
    }

    @Test
    fun `collectionPathFor maps all supported domain entities to expected subcollections`() {
        assertEquals("goals", syncRepository.collectionPathFor("Goal"))
        assertEquals("plans", syncRepository.collectionPathFor("Plan"))
        assertEquals("tasks", syncRepository.collectionPathFor("Task"))
        assertEquals("commitments", syncRepository.collectionPathFor("Commitment"))
        assertEquals("reflections", syncRepository.collectionPathFor("Reflection"))
        assertEquals("memories", syncRepository.collectionPathFor("Memory"))
    }

    @Test
    fun `collectionPathFor returns null for unsupported or unknown entity types`() {
        assertNull(syncRepository.collectionPathFor("UnknownEntity"))
        assertNull(syncRepository.collectionPathFor(""))
        assertNull(syncRepository.collectionPathFor("goal")) // case sensitivity check
        assertNull(syncRepository.collectionPathFor("User"))
    }

    @Test
    fun `upload fails immediately for unsupported entityType without invoking firestore`() = runTest {
        val unknownEntry = OutboxEntry(
            operationId = "op-unknown",
            entityType = "InvalidType",
            entityId = "entity-1",
            operationType = OutboxOperationType.UPSERT,
            payload = """{"userId":"user-1"}""",
            createdAt = 1000L,
            retryCount = 0,
            lastAttemptAt = null,
            status = SyncStatus.PENDING
        )

        val result = syncRepository.upload("user-1", unknownEntry)
        assertTrue("Upload must fail for unknown entityType", result is Result.Failure)
        val ex = (result as Result.Failure).error
        assertTrue(ex.message?.contains("Unknown entityType: InvalidType") == true)
    }

    @Test
    fun `parsePayload maps JSONObject NULL to Kotlin null so Firestore merge clears remote field`() {
        val payload = """{"id":"task-1","userId":"user-1","completedAt":null,"postponedAt":null,"title":"Test"}"""
        val parsed = syncRepository.parsePayload(payload)

        assertTrue("completedAt must be present in map keys", parsed.containsKey("completedAt"))
        assertNull("completedAt value must be null", parsed["completedAt"])
        assertTrue("postponedAt must be present in map keys", parsed.containsKey("postponedAt"))
        assertNull("postponedAt value must be null", parsed["postponedAt"])
        assertEquals("task-1", parsed["id"])
        assertEquals("user-1", parsed["userId"])
        assertEquals("Test", parsed["title"])
    }

    @Test
    fun `parsePayload preserves non-null numeric timestamps and strings`() {
        val payload = """{"id":"task-2","userId":"user-1","completedAt":1000,"scheduledAt":2000,"dueAt":5000}"""
        val parsed = syncRepository.parsePayload(payload)

        assertEquals("task-2", parsed["id"])
        assertEquals("user-1", parsed["userId"])
        assertEquals(1000, parsed["completedAt"])
        assertEquals(2000, parsed["scheduledAt"])
        assertEquals(5000, parsed["dueAt"])
    }

    @Test
    fun `parsePayload does not contain keys that were omitted from JSON`() {
        val payload = """{"id":"task-3","userId":"user-1"}"""
        val parsed = syncRepository.parsePayload(payload)

        assertEquals("task-3", parsed["id"])
        assertTrue(!parsed.containsKey("completedAt"))
        assertTrue(!parsed.containsKey("postponedAt"))
        assertTrue(!parsed.containsKey("scheduledAt"))
        assertTrue(!parsed.containsKey("dueAt"))
    }

    @Test
    fun `upload returns Conflict when remote document exists but lacks version and updatedAt`() = runTest {
        val docSnapshot = io.mockk.mockk<com.google.firebase.firestore.DocumentSnapshot>(relaxed = true)
        io.mockk.every { docSnapshot.exists() } returns true
        io.mockk.every { docSnapshot.getLong("version") } returns null
        io.mockk.every { docSnapshot.getLong("updatedAt") } returns null
        io.mockk.every { docSnapshot.data } returns mapOf("id" to "g-corrupt", "title" to "Corrupt Remote")

        val tx = io.mockk.mockk<com.google.firebase.firestore.Transaction>(relaxed = true)
        io.mockk.every { tx.get(any<com.google.firebase.firestore.DocumentReference>()) } returns docSnapshot

        io.mockk.every { firestore.runTransaction<com.sanket_satpute_20.ironmind.domain.repository.SyncUploadResult>(any()) } answers {
            val block = firstArg<com.google.firebase.firestore.Transaction.Function<com.sanket_satpute_20.ironmind.domain.repository.SyncUploadResult>>()
            com.google.android.gms.tasks.Tasks.forResult(block.apply(tx))
        }

        val entry = OutboxEntry(
            operationId = "op-corrupt",
            entityType = "Goal",
            entityId = "g-corrupt",
            operationType = OutboxOperationType.UPSERT,
            payload = """{"id":"g-corrupt","userId":"user-1","title":"Local"}""",
            createdAt = 1000L,
            retryCount = 0,
            lastAttemptAt = null,
            status = SyncStatus.PENDING,
            baseVersion = 0L
        )

        val result = syncRepository.upload("user-1", entry)
        assertTrue(result is Result.Success)
        val uploadResult = (result as Result.Success).data
        assertTrue("Must be Conflict", uploadResult is com.sanket_satpute_20.ironmind.domain.repository.SyncUploadResult.Conflict)
        val conflict = uploadResult as com.sanket_satpute_20.ironmind.domain.repository.SyncUploadResult.Conflict
        assertTrue(conflict.conflictReason.contains("lacks version and updatedAt"))
    }

    @Test
    fun `upload returns Conflict when remote document has negative version`() = runTest {
        val docSnapshot = io.mockk.mockk<com.google.firebase.firestore.DocumentSnapshot>(relaxed = true)
        io.mockk.every { docSnapshot.exists() } returns true
        io.mockk.every { docSnapshot.getLong("version") } returns -5L
        io.mockk.every { docSnapshot.data } returns mapOf("id" to "g-neg", "version" to -5L)

        val tx = io.mockk.mockk<com.google.firebase.firestore.Transaction>(relaxed = true)
        io.mockk.every { tx.get(any<com.google.firebase.firestore.DocumentReference>()) } returns docSnapshot

        io.mockk.every { firestore.runTransaction<com.sanket_satpute_20.ironmind.domain.repository.SyncUploadResult>(any()) } answers {
            val block = firstArg<com.google.firebase.firestore.Transaction.Function<com.sanket_satpute_20.ironmind.domain.repository.SyncUploadResult>>()
            com.google.android.gms.tasks.Tasks.forResult(block.apply(tx))
        }

        val entry = OutboxEntry(
            operationId = "op-neg",
            entityType = "Goal",
            entityId = "g-neg",
            operationType = OutboxOperationType.UPSERT,
            payload = """{"id":"g-neg","userId":"user-1","title":"Local"}""",
            createdAt = 1000L,
            retryCount = 0,
            lastAttemptAt = null,
            status = SyncStatus.PENDING,
            baseVersion = 0L
        )

        val result = syncRepository.upload("user-1", entry)
        assertTrue(result is Result.Success)
        val uploadResult = (result as Result.Success).data
        assertTrue("Must be Conflict", uploadResult is com.sanket_satpute_20.ironmind.domain.repository.SyncUploadResult.Conflict)
        val conflict = uploadResult as com.sanket_satpute_20.ironmind.domain.repository.SyncUploadResult.Conflict
        assertTrue(conflict.conflictReason.contains("invalid negative version"))
    }
}

