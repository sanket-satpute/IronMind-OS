package com.sanket_satpute_20.ironmind.data.local.dao

import android.content.Context
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sanket_satpute_20.ironmind.data.local.IronMindDatabase
import com.sanket_satpute_20.ironmind.data.local.entity.AppSessionStateEntity
import com.sanket_satpute_20.ironmind.data.local.Jdk25CompatibleRobolectricTestRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(Jdk25CompatibleRobolectricTestRunner::class)
@Config(sdk = [34])
class AppSessionStateAndLeaseDaoTest {

    @get:Rule
    val globalTimeout: Timeout = Timeout.seconds(60)

    private lateinit var context: Context
    private lateinit var db: IronMindDatabase
    private lateinit var sessionDao: AppSessionStateDao
    private lateinit var leaseDao: SyncWorkerLeaseDao

    @Before
    fun setUp() {
        ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
            override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
            override fun postToMainThread(runnable: Runnable) = runnable.run()
            override fun isMainThread(): Boolean = true
        })

        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, IronMindDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        sessionDao = db.appSessionStateDao()
        leaseDao = db.syncWorkerLeaseDao()
    }

    @After
    fun tearDown() {
        try {
            db.close()
        } finally {
            ArchTaskExecutor.getInstance().setDelegate(null)
        }
    }

    @Test
    fun test01_initialAcquisition_failsWhenAppSessionStateHasNoRow() = runBlocking(Dispatchers.Default) {
        val now = 1_000_000L
        val token = UUID.randomUUID().toString()

        // app_session_state is empty
        val rows = leaseDao.acquireOrTakeoverLease(
            leaseKey = "sync_execution_lock",
            callerOwnerToken = token,
            callerGeneration = 1L,
            leaseDurationMs = 300_000L,
            now = now
        )

        assertTrue("Acquisition must fail closed (<= 0 rows) when app_session_state has no row: found $rows", rows <= 0L)
        val lease = leaseDao.getLease("sync_execution_lock")
        assertNull("No lease row should exist", lease)
    }

    @Test
    fun test02_initialAcquisition_succeedsWithMatchingAuthoritativeGeneration() = runBlocking(Dispatchers.Default) {
        val now = 1_000_000L
        val token = UUID.randomUUID().toString()

        // Seed authoritative session state (gen = 1L)
        sessionDao.setSessionState(
            AppSessionStateEntity(id = 1, activeUserId = "user-1", currentGeneration = 1L, updatedAt = now)
        )

        val rows = leaseDao.acquireOrTakeoverLease(
            leaseKey = "sync_execution_lock",
            callerOwnerToken = token,
            callerGeneration = 1L,
            leaseDurationMs = 300_000L,
            now = now
        )

        assertTrue("Acquisition must succeed with positive rowid: found $rows", rows > 0L)
        val lease = leaseDao.getLease("sync_execution_lock")
        assertNotNull(lease)
        assertEquals(token, lease?.ownerToken)
        assertEquals(1L, lease?.sessionGeneration)
        assertEquals("ACTIVE", lease?.status)
        assertEquals(now + 300_000L, lease?.expiresAt)
    }

    @Test
    fun test03_initialAcquisition_rejectsStaleOrFabricatedGeneration() = runBlocking(Dispatchers.Default) {
        val now = 1_000_000L
        val token = UUID.randomUUID().toString()

        // Authoritative generation is 5L
        sessionDao.setSessionState(
            AppSessionStateEntity(id = 1, activeUserId = "user-1", currentGeneration = 5L, updatedAt = now)
        )

        // Caller supplies stale gen 4L
        val staleRows = leaseDao.acquireOrTakeoverLease(
            leaseKey = "sync_execution_lock",
            callerOwnerToken = token,
            callerGeneration = 4L,
            leaseDurationMs = 300_000L,
            now = now
        )
        assertTrue("Stale generation must be rejected (<= 0 rows): found $staleRows", staleRows <= 0L)

        // Caller supplies fabricated gen 99L
        val fabricatedRows = leaseDao.acquireOrTakeoverLease(
            leaseKey = "sync_execution_lock",
            callerOwnerToken = token,
            callerGeneration = 99L,
            leaseDurationMs = 300_000L,
            now = now
        )
        assertTrue("Fabricated generation must be rejected (<= 0 rows): found $fabricatedRows", fabricatedRows <= 0L)
    }

    @Test
    fun test04_concurrentAcquisition_blocksSecondWorkerWhileLeaseIsActive() = runBlocking(Dispatchers.Default) {
        val now = 1_000_000L
        val token1 = "worker-1-token"
        val token2 = "worker-2-token"

        sessionDao.setSessionState(
            AppSessionStateEntity(id = 1, activeUserId = "user-1", currentGeneration = 1L, updatedAt = now)
        )

        // Worker 1 acquires lock
        val rows1 = leaseDao.acquireOrTakeoverLease("sync_execution_lock", token1, 1L, 300_000L, now)
        assertTrue("Worker 1 must acquire lock: found $rows1", rows1 > 0L)

        // Worker 2 attempts acquisition while Worker 1 lease is still active (expiresAt = now + 300_000)
        val rows2 = leaseDao.acquireOrTakeoverLease("sync_execution_lock", token2, 1L, 300_000L, now + 10_000L)
        assertTrue("Worker 2 must be locked out (<= 0 rows affected): found $rows2", rows2 <= 0L)

        val lease = leaseDao.getLease("sync_execution_lock")
        assertEquals("Owner token must remain Worker 1", token1, lease?.ownerToken)
    }

    @Test
    fun test05_expiredLeaseTakeover_allowsNewWorkerToClaimExpiredLease() = runBlocking(Dispatchers.Default) {
        val t0 = 1_000_000L
        val token1 = "worker-1-token"
        val token2 = "worker-2-token"

        sessionDao.setSessionState(
            AppSessionStateEntity(id = 1, activeUserId = "user-1", currentGeneration = 1L, updatedAt = t0)
        )

        // Worker 1 acquires lock with 5-minute duration (expires at t0 + 300_000)
        leaseDao.acquireOrTakeoverLease("sync_execution_lock", token1, 1L, 300_000L, t0)

        // Time advances 6 minutes (now = t0 + 360_000, lease has expired)
        val t1 = t0 + 360_000L
        val rows2 = leaseDao.acquireOrTakeoverLease("sync_execution_lock", token2, 1L, 300_000L, t1)
        assertTrue("New worker must successfully take over expired lease: found $rows2", rows2 > 0L)

        val lease = leaseDao.getLease("sync_execution_lock")
        assertEquals("Owner must now be Worker 2", token2, lease?.ownerToken)
        assertEquals(t1 + 300_000L, lease?.expiresAt)
    }

    @Test
    fun test06_renewLease_succeedsForActiveOwnerAndRejectsExpiredOrStaleOwner() = runBlocking(Dispatchers.Default) {
        val t0 = 1_000_000L
        val token1 = "worker-1-token"

        sessionDao.setSessionState(
            AppSessionStateEntity(id = 1, activeUserId = "user-1", currentGeneration = 1L, updatedAt = t0)
        )
        leaseDao.acquireOrTakeoverLease("sync_execution_lock", token1, 1L, 300_000L, t0)

        // Valid renewal at t0 + 100_000 (expiresAt = t0 + 300_000 > now)
        val renewRows = leaseDao.renewLease("sync_execution_lock", token1, 1L, 300_000L, t0 + 100_000L)
        assertEquals("Valid renewal must succeed", 1, renewRows)

        val updatedLease = leaseDao.getLease("sync_execution_lock")
        assertEquals(t0 + 100_000L + 300_000L, updatedLease?.expiresAt)

        // Attempt renewal after expiration (t0 + 500_000 > expiresAt)
        val expiredRenewRows = leaseDao.renewLease("sync_execution_lock", token1, 1L, 300_000L, t0 + 500_000L)
        assertEquals("Expired lease renewal must be rejected (0 rows affected)", 0, expiredRenewRows)

        // Attempt renewal by wrong owner token
        val wrongOwnerRows = leaseDao.renewLease("sync_execution_lock", "imposter-token", 1L, 300_000L, t0 + 150_000L)
        assertEquals("Wrong owner token renewal must be rejected", 0, wrongOwnerRows)
    }

    @Test
    fun test07_commitPageProgress_updatesCursorAndExtendsLease() = runBlocking(Dispatchers.Default) {
        val t0 = 1_000_000L
        val token1 = "worker-1-token"

        sessionDao.setSessionState(
            AppSessionStateEntity(id = 1, activeUserId = "user-1", currentGeneration = 1L, updatedAt = t0)
        )
        leaseDao.acquireOrTakeoverLease("sync_execution_lock", token1, 1L, 300_000L, t0)

        val rows = leaseDao.commitPageProgress(
            leaseKey = "sync_execution_lock",
            ownerToken = token1,
            expectedGeneration = 1L,
            nextCursor = "cursor-doc-50",
            leaseDurationMs = 300_000L,
            now = t0 + 50_000L
        )
        assertEquals(1, rows)

        val lease = leaseDao.getLease("sync_execution_lock")
        assertEquals("cursor-doc-50", lease?.cursor)
        assertEquals("IN_PROGRESS", lease?.status)
        assertEquals(t0 + 50_000L + 300_000L, lease?.expiresAt)
    }

    @Test
    fun test08_releaseLease_releasesOnlyWithMatchingOwnerAndGeneration() = runBlocking(Dispatchers.Default) {
        val t0 = 1_000_000L
        val token1 = "worker-1-token"

        sessionDao.setSessionState(
            AppSessionStateEntity(id = 1, activeUserId = "user-1", currentGeneration = 1L, updatedAt = t0)
        )
        leaseDao.acquireOrTakeoverLease("sync_execution_lock", token1, 1L, 300_000L, t0)

        // Stale owner attempts release
        val staleRows = leaseDao.releaseLease("sync_execution_lock", "other-token", 1L, t0 + 10_000L)
        assertEquals("Stale owner release must fail (0 rows affected)", 0, staleRows)

        // Correct owner releases
        val validRows = leaseDao.releaseLease("sync_execution_lock", token1, 1L, t0 + 10_000L)
        assertEquals(1, validRows)

        val lease = leaseDao.getLease("sync_execution_lock")
        assertEquals("IDLE", lease?.status)
        assertEquals(0L, lease?.expiresAt)
    }

    @Test
    fun test09_sessionInvalidation_revokesLeasesAndAllowsNewSessionTakeover() = runBlocking(Dispatchers.Default) {
        val t0 = 1_000_000L
        val token1 = "worker-1-token"
        val token2 = "worker-2-token"

        // Session Generation 1
        sessionDao.setSessionState(
            AppSessionStateEntity(id = 1, activeUserId = "user-1", currentGeneration = 1L, updatedAt = t0)
        )
        leaseDao.acquireOrTakeoverLease("sync_execution_lock", token1, 1L, 300_000L, t0)

        // User signs out / switches accounts: generation advances to 2L
        sessionDao.advanceSessionGeneration("user-2", t0 + 10_000L)
        leaseDao.revokeLeasesForOlderGenerations(2L, t0 + 10_000L)

        val revokedLease = leaseDao.getLease("sync_execution_lock")
        assertEquals("REVOKED", revokedLease?.status)
        assertEquals(0L, revokedLease?.expiresAt)

        // Stale Worker 1 attempts renewal: rejected because generation shifted
        val staleRenew = leaseDao.renewLease("sync_execution_lock", token1, 1L, 300_000L, t0 + 15_000L)
        assertEquals("Stale session worker renewal must be rejected", 0, staleRenew)

        // New Session Worker 2 acquires lock for generation 2L
        val newAcquire = leaseDao.acquireOrTakeoverLease("sync_execution_lock", token2, 2L, 300_000L, t0 + 20_000L)
        assertTrue("New authoritative session worker must acquire lock: found $newAcquire", newAcquire > 0L)

        val finalLease = leaseDao.getLease("sync_execution_lock")
        assertEquals(token2, finalLease?.ownerToken)
        assertEquals(2L, finalLease?.sessionGeneration)
        assertEquals("ACTIVE", finalLease?.status)
    }
}

