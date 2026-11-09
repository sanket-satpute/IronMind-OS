package com.sanket_satpute_20.ironmind.infrastructure.worker

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.session.SessionState
import com.sanket_satpute_20.ironmind.domain.session.UserSessionProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class IntelligenceOrchestrationSchedulerTest {

    private lateinit var workManager: WorkManager
    private lateinit var sessionProvider: UserSessionProvider
    private lateinit var scheduler: IntelligenceOrchestrationScheduler

    private var currentTime = 100_000L
    private val clock = object : Clock {
        override fun currentTimeMillis(): Long = currentTime
    }

    private val userId = "user-intel-1"

    @Before
    fun setUp() {
        workManager = mockk(relaxed = true)
        sessionProvider = mockk {
            every { getSessionGeneration() } returns 1L
            every { getActiveUserId() } returns userId
            every { sessionState } returns kotlinx.coroutines.flow.MutableStateFlow(SessionState.Authenticated(userId, false))
            every { isUserErased(userId, 1L) } returns false
        }
        scheduler = IntelligenceOrchestrationScheduler(
            workManager = workManager,
            sessionProvider = { sessionProvider },
            clock = clock,
            minIntervalMillis = 60 * 60 * 1000L, // 1 hour
            debounceDelayMillis = 5_000L // 5 seconds
        )
    }

    @Test
    fun `scheduleOrchestration creates unique work with KEEP policy`() {
        val requestSlot = slot<OneTimeWorkRequest>()
        every {
            workManager.enqueueUniqueWork(
                IntelligenceOrchestrationScheduler.getUniqueWorkName(userId),
                ExistingWorkPolicy.KEEP,
                capture(requestSlot)
            )
        } returns mockk()

        val scheduled = scheduler.scheduleOrchestration(userId)
        assertTrue(scheduled)

        verify(exactly = 1) {
            workManager.enqueueUniqueWork(
                IntelligenceOrchestrationScheduler.getUniqueWorkName(userId),
                ExistingWorkPolicy.KEEP,
                any<OneTimeWorkRequest>()
            )
        }

        val captured = requestSlot.captured
        val capturedUserId = captured.workSpec.input.getString(IntelligenceOrchestrationWorker.KEY_USER_ID)
        val capturedGen = captured.workSpec.input.getLong(IntelligenceOrchestrationWorker.KEY_SESSION_GENERATION, -1L)

        assertEquals(userId, capturedUserId)
        assertEquals(1L, capturedGen)
        assertTrue(captured.tags.contains(IntelligenceOrchestrationScheduler.TAG))
        assertTrue(captured.tags.contains("${IntelligenceOrchestrationScheduler.TAG}_$userId"))
    }

    @Test
    fun `Duplicate scheduling calls are coalesced via KEEP policy`() {
        every {
            workManager.enqueueUniqueWork(
                IntelligenceOrchestrationScheduler.getUniqueWorkName(userId),
                ExistingWorkPolicy.KEEP,
                any<OneTimeWorkRequest>()
            )
        } returns mockk()

        // Call schedule 3 times in rapid succession
        val result1 = scheduler.scheduleOrchestration(userId)
        val result2 = scheduler.scheduleOrchestration(userId)
        val result3 = scheduler.scheduleOrchestration(userId)

        assertTrue(result1)
        assertTrue(result2)
        assertTrue(result3)

        // All calls use ExistingWorkPolicy.KEEP to guarantee coalescing
        verify(exactly = 3) {
            workManager.enqueueUniqueWork(
                IntelligenceOrchestrationScheduler.getUniqueWorkName(userId),
                ExistingWorkPolicy.KEEP,
                any<OneTimeWorkRequest>()
            )
        }
    }

    @Test
    fun `Scheduling is delayed by remaining cooldown when within cooldown window`() {
        // Record that orchestration completed at currentTime
        scheduler.recordCompletion(userId, currentTime)

        // Advance clock by 15 minutes (less than 1 hour min interval)
        currentTime += 15 * 60 * 1000L

        val requestSlot = slot<OneTimeWorkRequest>()
        every {
            workManager.enqueueUniqueWork(
                IntelligenceOrchestrationScheduler.getUniqueWorkName(userId),
                ExistingWorkPolicy.KEEP,
                capture(requestSlot)
            )
        } returns mockk()

        val scheduled = scheduler.scheduleOrchestration(userId, force = false)
        assertTrue(scheduled)

        val captured = requestSlot.captured
        // Remaining wait = 60m - 15m = 45m = 2,700,000ms
        assertEquals(45 * 60 * 1000L, captured.workSpec.initialDelay)
    }

    @Test
    fun `Forced scheduling bypasses cooldown and uses debounce delay`() {
        scheduler.recordCompletion(userId, currentTime)
        currentTime += 10 * 60 * 1000L // 10 minutes later

        val requestSlot = slot<OneTimeWorkRequest>()
        every {
            workManager.enqueueUniqueWork(
                IntelligenceOrchestrationScheduler.getUniqueWorkName(userId),
                ExistingWorkPolicy.KEEP,
                capture(requestSlot)
            )
        } returns mockk()

        val scheduled = scheduler.scheduleOrchestration(userId, force = true)
        assertTrue(scheduled)

        val captured = requestSlot.captured
        assertEquals(5_000L, captured.workSpec.initialDelay) // immediate debounce delay
    }

    @Test
    fun `Missing or non-positive session generation prevents scheduling`() {
        every { sessionProvider.getSessionGeneration() } returns 0L

        val scheduled = scheduler.scheduleOrchestration(userId)
        assertFalse(scheduled)

        verify(exactly = 0) {
            workManager.enqueueUniqueWork(any(), any(), any<OneTimeWorkRequest>())
        }
    }

    @Test
    fun `Inactive user prevents scheduling`() {
        every { sessionProvider.getActiveUserId() } returns "other-user"

        val scheduled = scheduler.scheduleOrchestration(userId)
        assertFalse(scheduled)

        verify(exactly = 0) {
            workManager.enqueueUniqueWork(any(), any(), any<OneTimeWorkRequest>())
        }
    }

    @Test
    fun `User erasure prevents scheduling`() {
        every { sessionProvider.isUserErased(userId, 1L) } returns true

        val scheduled = scheduler.scheduleOrchestration(userId)
        assertFalse(scheduled)

        verify(exactly = 0) {
            workManager.enqueueUniqueWork(any(), any(), any<OneTimeWorkRequest>())
        }
    }

    @Test
    fun `cancelOrchestration cancels unique work and tags`() {
        scheduler.cancelOrchestration(userId)

        verify(exactly = 1) {
            workManager.cancelUniqueWork(IntelligenceOrchestrationScheduler.getUniqueWorkName(userId))
        }
        verify(exactly = 1) {
            workManager.cancelAllWorkByTag("${IntelligenceOrchestrationScheduler.TAG}_$userId")
        }
    }

    @Test
    fun `Work request enforces battery not low constraint and exponential backoff`() {
        val requestSlot = slot<OneTimeWorkRequest>()
        every {
            workManager.enqueueUniqueWork(any(), any(), capture(requestSlot))
        } returns mockk()

        scheduler.scheduleOrchestration(userId)

        val captured = requestSlot.captured
        assertTrue(captured.workSpec.constraints.requiresBatteryNotLow())
        assertEquals(androidx.work.BackoffPolicy.EXPONENTIAL, captured.workSpec.backoffPolicy)
    }
}
