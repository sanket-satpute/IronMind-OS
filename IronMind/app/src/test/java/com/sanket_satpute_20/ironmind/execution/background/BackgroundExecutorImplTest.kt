package com.sanket_satpute_20.ironmind.execution.background

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.sanket_satpute_20.ironmind.infrastructure.worker.ReflectionProcessingWorker
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class BackgroundExecutorImplTest {

    private lateinit var context: Context
    private lateinit var workManager: WorkManager
    private lateinit var backgroundExecutor: BackgroundExecutorImpl

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        workManager = mockk(relaxed = true)
        backgroundExecutor = BackgroundExecutorImpl(context, workManager)
    }

    @Test
    fun `scheduleReflectionProcessing enqueues unique work with correct parameters`() {
        val reflectionId = "test-reflection-123"
        val workRequestSlot = slot<OneTimeWorkRequest>()
        val workNameSlot = slot<String>()
        val workPolicySlot = slot<ExistingWorkPolicy>()

        every {
            workManager.enqueueUniqueWork(
                capture(workNameSlot),
                capture(workPolicySlot),
                capture(workRequestSlot)
            )
        } returns mockk()

        backgroundExecutor.scheduleReflectionProcessing(reflectionId)

        verify(exactly = 1) {
            workManager.enqueueUniqueWork(any(), any(), any<OneTimeWorkRequest>())
        }

        // C. Unique work name is exactly: ProcessReflection_<reflectionId>
        assertEquals("ProcessReflection_test-reflection-123", workNameSlot.captured)

        // D. ExistingWorkPolicy is KEEP
        assertEquals(ExistingWorkPolicy.KEEP, workPolicySlot.captured)

        // A. Worker class is ReflectionProcessingWorker
        val capturedRequest = workRequestSlot.captured
        assertEquals(ReflectionProcessingWorker::class.java.name, capturedRequest.workSpec.workerClassName)

        // B. InputData contains exactly reflectionId
        val inputData = capturedRequest.workSpec.input
        assertEquals("test-reflection-123", inputData.getString(ReflectionProcessingWorker.KEY_REFLECTION_ID))
        assertEquals(1, inputData.keyValueMap.size)
        // Ensure no userId, ZoneId, or extra input keys
        assertEquals(false, inputData.keyValueMap.containsKey("userId"))
        assertEquals(false, inputData.keyValueMap.containsKey("zoneId"))
    }
}
