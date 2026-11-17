package com.sanket_satpute_20.ironmind.infrastructure.worker

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream

import com.sanket_satpute_20.ironmind.domain.session.UserSessionProvider

class ObservationSchedulerTest {

    private lateinit var workManager: WorkManager
    private lateinit var scheduler: ObservationScheduler
    private lateinit var sessionProvider: UserSessionProvider

    @Before
    fun setUp() {
        workManager = mockk(relaxed = true)
        sessionProvider = mockk {
            every { getSessionGeneration() } returns 1L
        }
        scheduler = ObservationScheduler(workManager, sessionProvider = { sessionProvider })
    }

    @Test
    fun `Scheduler creates unique WorkManager work`() {
        every { workManager.enqueueUniquePeriodicWork(any(), any(), any<PeriodicWorkRequest>()) } returns mockk()

        val result = scheduler.scheduleObservationCollection()
        assertTrue(result is com.sanket_satpute_20.ironmind.domain.common.Result.Success)
        
        verify(exactly = 1) { 
            workManager.enqueueUniquePeriodicWork(
                ObservationScheduler.UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                any<PeriodicWorkRequest>()
            )
        }
    }

    @Test
    fun `Repeated scheduling does not create duplicate independent work`() {
        every { workManager.enqueueUniquePeriodicWork(any(), any(), any<PeriodicWorkRequest>()) } returns mockk()
        
        scheduler.scheduleObservationCollection()
        scheduler.scheduleObservationCollection()
        scheduler.scheduleObservationCollection()
        
        // Will enqueue 3 times, but ExistingPeriodicWorkPolicy.UPDATE guarantees uniqueness
        verify(exactly = 3) { 
            workManager.enqueueUniquePeriodicWork(
                ObservationScheduler.UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                any<PeriodicWorkRequest>()
            )
        }
    }

    @Test
    fun `Cancellation targets the correct unique work`() {
        every { workManager.cancelUniqueWork(any()) } returns mockk()

        val result = scheduler.cancelObservationCollection()
        assertTrue(result is com.sanket_satpute_20.ironmind.domain.common.Result.Success)
        
        verify(exactly = 1) { 
            workManager.cancelUniqueWork(ObservationScheduler.UNIQUE_WORK_NAME)
        }
    }

    @Test
    fun `Scheduler lifecycle logging is safe`() {
        val originalOut = System.out
        val outContent = ByteArrayOutputStream()
        System.setOut(PrintStream(outContent))

        try {
            scheduler.scheduleObservationCollection()
            scheduler.cancelObservationCollection()
            
            val logs = outContent.toString()
            assertTrue(logs.contains("IronMindLifecycle [ObservationScheduling] [SCHEDULED]"))
            assertTrue(logs.contains("IronMindLifecycle [ObservationScheduling] [CANCELLED]"))
            assertTrue(!logs.contains("raw"))
            assertTrue(!logs.contains("payload"))
        } finally {
            System.setOut(originalOut)
        }
    }

    @Test
    fun `Scheduler exceptions return explicit failures`() {
        every { workManager.enqueueUniquePeriodicWork(any(), any(), any<PeriodicWorkRequest>()) } throws RuntimeException("DB error")
        every { workManager.cancelUniqueWork(any()) } throws RuntimeException("DB error")

        val scheduleResult = scheduler.scheduleObservationCollection()
        val cancelResult = scheduler.cancelObservationCollection()

        assertTrue(scheduleResult is com.sanket_satpute_20.ironmind.domain.common.Result.Failure)
        assertTrue(cancelResult is com.sanket_satpute_20.ironmind.domain.common.Result.Failure)
    }

    @Test
    fun `scheduleObservationCollection fails closed when generation is 0L`() {
        val zeroGenProvider: UserSessionProvider = mockk {
            every { getSessionGeneration() } returns 0L
        }
        val zeroScheduler = ObservationScheduler(workManager, sessionProvider = { zeroGenProvider })
        val result = zeroScheduler.scheduleObservationCollection()
        assertTrue(result is com.sanket_satpute_20.ironmind.domain.common.Result.Failure)
        verify(exactly = 0) { workManager.enqueueUniquePeriodicWork(any(), any(), any()) }
    }

    @Test
    fun `scheduleObservationCollection fails closed when session is Initializing`() {
        val initProvider: UserSessionProvider = mockk {
            every { getSessionGeneration() } returns 1L
            every { sessionState } returns kotlinx.coroutines.flow.MutableStateFlow(com.sanket_satpute_20.ironmind.domain.session.SessionState.Initializing)
        }
        val initScheduler = ObservationScheduler(workManager, sessionProvider = { initProvider })
        val result = initScheduler.scheduleObservationCollection()
        assertTrue(result is com.sanket_satpute_20.ironmind.domain.common.Result.Failure)
        verify(exactly = 0) { workManager.enqueueUniquePeriodicWork(any(), any(), any()) }
    }

    @Test
    fun `scheduleObservationCollection fails closed when session is ReconciliationFailed`() {
        val failProvider: UserSessionProvider = mockk {
            every { getSessionGeneration() } returns 1L
            every { sessionState } returns kotlinx.coroutines.flow.MutableStateFlow(com.sanket_satpute_20.ironmind.domain.session.SessionState.ReconciliationFailed(Exception("Failed"), "user1"))
        }
        val failScheduler = ObservationScheduler(workManager, sessionProvider = { failProvider })
        val result = failScheduler.scheduleObservationCollection()
        assertTrue(result is com.sanket_satpute_20.ironmind.domain.common.Result.Failure)
        verify(exactly = 0) { workManager.enqueueUniquePeriodicWork(any(), any(), any()) }
    }

    @Test
    fun `scheduleObservationCollection fails closed when user is marked erased`() {
        val erasedProvider: UserSessionProvider = mockk {
            every { getSessionGeneration() } returns 1L
            every { sessionState } returns kotlinx.coroutines.flow.MutableStateFlow(com.sanket_satpute_20.ironmind.domain.session.SessionState.Authenticated("user1", isAnonymous = false))
            every { getActiveUserId() } returns "user1"
            every { isUserErased("user1", 1L) } returns true
        }
        val erasedScheduler = ObservationScheduler(workManager, sessionProvider = { erasedProvider })
        val result = erasedScheduler.scheduleObservationCollection()
        assertTrue(result is com.sanket_satpute_20.ironmind.domain.common.Result.Failure)
        verify(exactly = 0) { workManager.enqueueUniquePeriodicWork(any(), any(), any()) }
    }
}
