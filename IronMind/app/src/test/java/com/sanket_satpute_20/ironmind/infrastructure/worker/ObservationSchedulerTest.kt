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

class ObservationSchedulerTest {

    private lateinit var workManager: WorkManager
    private lateinit var scheduler: ObservationScheduler

    @Before
    fun setUp() {
        workManager = mockk(relaxed = true)
        scheduler = ObservationScheduler(workManager)
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
}
