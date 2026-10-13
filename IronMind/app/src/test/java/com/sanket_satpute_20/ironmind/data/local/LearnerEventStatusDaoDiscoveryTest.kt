package com.sanket_satpute_20.ironmind.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sanket_satpute_20.ironmind.data.local.dao.LearnerEventStatusDao
import com.sanket_satpute_20.ironmind.data.local.entity.LearnerEventStatusEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(Jdk25CompatibleRobolectricTestRunner::class)
@Config(sdk = [34])
class LearnerEventStatusDaoDiscoveryTest {

    private lateinit var db: IronMindDatabase
    private lateinit var dao: LearnerEventStatusDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, IronMindDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.learnerEventStatusDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun createStatus(
        id: String,
        status: String = "PENDING",
        claimCount: Int = 0,
        failureCount: Int = 0,
        maxAttempts: Int = 5,
        nextAttemptAt: Long = 1000L,
        leaseExpiresAt: Long? = null,
        leaseToken: String? = null
    ): LearnerEventStatusEntity {
        return LearnerEventStatusEntity(
            eventId = id,
            userId = "user-test",
            status = status,
            claimCount = claimCount,
            failureCount = failureCount,
            maxAttempts = maxAttempts,
            leaseToken = leaseToken,
            leaseExpiresAt = leaseExpiresAt,
            lastAttemptAt = null,
            nextAttemptAt = nextAttemptAt,
            errorMessage = null,
            hadMalformedMetadata = false,
            createdAt = 1000L,
            updatedAt = 1000L
        )
    }

    @Test
    fun `getPendingEligibleEventIds returns eligible pending records ordered by nextAttemptAt ASC`() = runBlocking {
        dao.insert(createStatus("event-3", nextAttemptAt = 3000L))
        dao.insert(createStatus("event-1", nextAttemptAt = 1000L))
        dao.insert(createStatus("event-2", nextAttemptAt = 2000L))

        // All 3 have nextAttemptAt <= 5000L
        val discovered = dao.getPendingEligibleEventIds(now = 5000L, limit = 10)
        assertEquals(listOf("event-1", "event-2", "event-3"), discovered)
    }

    @Test
    fun `getPendingEligibleEventIds respects nextAttemptAt boundary and excludes future retries`() = runBlocking {
        val now = 2000L
        dao.insert(createStatus("event-past", nextAttemptAt = 1999L))
        dao.insert(createStatus("event-exact", nextAttemptAt = 2000L))
        dao.insert(createStatus("event-future", nextAttemptAt = 2001L))

        val discovered = dao.getPendingEligibleEventIds(now = now, limit = 10)
        assertEquals(listOf("event-past", "event-exact"), discovered)
    }

    @Test
    fun `getPendingEligibleEventIds includes both PENDING and FAILED_RETRYABLE when eligible`() = runBlocking {
        val now = 5000L
        dao.insert(createStatus("event-pending", status = "PENDING", claimCount = 0, nextAttemptAt = 1000L))
        dao.insert(createStatus("event-processing", status = "PROCESSING", claimCount = 1, nextAttemptAt = 1000L))
        dao.insert(createStatus("event-succeeded", status = "SUCCEEDED", claimCount = 1, nextAttemptAt = 1000L))
        dao.insert(createStatus("event-retryable", status = "FAILED_RETRYABLE", claimCount = 1, nextAttemptAt = 2000L))
        dao.insert(createStatus("event-exhausted", status = "PENDING", claimCount = 5, maxAttempts = 5, nextAttemptAt = 1000L))

        val discovered = dao.getPendingEligibleEventIds(now = now, limit = 10)
        assertEquals(listOf("event-pending", "event-retryable"), discovered)
    }

    @Test
    fun `getPendingEligibleEventIds excludes FAILED_RETRYABLE when nextAttemptAt is in future`() = runBlocking {
        val now = 2000L
        dao.insert(createStatus("event-due-retry", status = "FAILED_RETRYABLE", claimCount = 1, nextAttemptAt = 1500L))
        dao.insert(createStatus("event-future-retry", status = "FAILED_RETRYABLE", claimCount = 1, nextAttemptAt = 3000L))
        dao.insert(createStatus("event-exhausted-retry", status = "FAILED_RETRYABLE", claimCount = 5, maxAttempts = 5, nextAttemptAt = 1000L))

        val discovered = dao.getPendingEligibleEventIds(now = now, limit = 10)
        assertEquals(listOf("event-due-retry"), discovered)
    }

    @Test
    fun `claimPendingEvent claims FAILED_RETRYABLE record without double-incrementing failureCount`() = runBlocking {
        val now = 5000L
        dao.insert(createStatus("event-retry-claim", status = "FAILED_RETRYABLE", claimCount = 1, failureCount = 1, nextAttemptAt = 4000L))

        val rows = dao.claimPendingEvent(
            eventId = "event-retry-claim",
            now = now,
            leaseExpiresAt = now + 30_000L,
            newLeaseToken = "token-test"
        )
        assertEquals(1, rows)

        val updated = dao.getEventStatus("event-retry-claim")
        assertEquals("PROCESSING", updated?.status)
        assertEquals(2, updated?.claimCount)
        assertEquals(1, updated?.failureCount) // Preserved, not incremented during claim!
        assertEquals("token-test", updated?.leaseToken)
    }

    @Test
    fun `claimPendingEvent rejects claim on FAILED_RETRYABLE when claimCount reached maxAttempts`() = runBlocking {
        val now = 5000L
        dao.insert(createStatus("event-retry-exhausted", status = "FAILED_RETRYABLE", claimCount = 5, maxAttempts = 5, nextAttemptAt = 4000L))

        val rows = dao.claimPendingEvent(
            eventId = "event-retry-exhausted",
            now = now,
            leaseExpiresAt = now + 30_000L,
            newLeaseToken = "token-test"
        )
        assertEquals(0, rows)
    }

    @Test
    fun `getPendingEligibleEventIds respects batch limits and handles zero limit safely`() = runBlocking {
        dao.insert(createStatus("event-1", nextAttemptAt = 1000L))
        dao.insert(createStatus("event-2", nextAttemptAt = 2000L))
        dao.insert(createStatus("event-3", nextAttemptAt = 3000L))

        val batch2 = dao.getPendingEligibleEventIds(now = 5000L, limit = 2)
        assertEquals(listOf("event-1", "event-2"), batch2)

        val batch0 = dao.getPendingEligibleEventIds(now = 5000L, limit = 0)
        assertTrue(batch0.isEmpty())
    }

    @Test
    fun `getExpiredProcessingEventIds returns expired processing leases ordered by leaseExpiresAt ASC`() = runBlocking {
        val now = 10000L
        dao.insert(createStatus("proc-3", status = "PROCESSING", leaseExpiresAt = 8000L, claimCount = 1))
        dao.insert(createStatus("proc-1", status = "PROCESSING", leaseExpiresAt = 6000L, claimCount = 1))
        dao.insert(createStatus("proc-2", status = "PROCESSING", leaseExpiresAt = 7000L, claimCount = 1))

        val discovered = dao.getExpiredProcessingEventIds(now = now, limit = 10)
        assertEquals(listOf("proc-1", "proc-2", "proc-3"), discovered)
    }

    @Test
    fun `getExpiredProcessingEventIds excludes unexpired leases, claim-exhausted records, and non-processing statuses`() = runBlocking {
        val now = 5000L
        dao.insert(createStatus("proc-expired", status = "PROCESSING", leaseExpiresAt = 4000L, claimCount = 1, maxAttempts = 5))
        dao.insert(createStatus("proc-active", status = "PROCESSING", leaseExpiresAt = 6000L, claimCount = 1, maxAttempts = 5))
        dao.insert(createStatus("proc-exact", status = "PROCESSING", leaseExpiresAt = 5000L, claimCount = 1, maxAttempts = 5)) // not strictly less than now
        dao.insert(createStatus("proc-exhausted", status = "PROCESSING", leaseExpiresAt = 3000L, claimCount = 5, maxAttempts = 5))
        dao.insert(createStatus("pending", status = "PENDING", leaseExpiresAt = 3000L, claimCount = 1, maxAttempts = 5))

        val discovered = dao.getExpiredProcessingEventIds(now = now, limit = 10)
        assertEquals(listOf("proc-expired"), discovered)
    }

    @Test
    fun `getExpiredProcessingEventIds respects batch limits and handles zero limit safely`() = runBlocking {
        val now = 10000L
        dao.insert(createStatus("proc-1", status = "PROCESSING", leaseExpiresAt = 1000L, claimCount = 1))
        dao.insert(createStatus("proc-2", status = "PROCESSING", leaseExpiresAt = 2000L, claimCount = 1))
        dao.insert(createStatus("proc-3", status = "PROCESSING", leaseExpiresAt = 3000L, claimCount = 1))

        val batch2 = dao.getExpiredProcessingEventIds(now = now, limit = 2)
        assertEquals(listOf("proc-1", "proc-2"), batch2)

        val batch0 = dao.getExpiredProcessingEventIds(now = now, limit = 0)
        assertTrue(batch0.isEmpty())
    }
}
