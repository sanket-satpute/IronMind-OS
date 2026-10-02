package com.sanket_satpute_20.ironmind.infrastructure.worker

import android.content.Context
import androidx.work.Data
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.sanket_satpute_20.ironmind.domain.common.Result as DomainResult
import com.sanket_satpute_20.ironmind.domain.model.Reflection
import com.sanket_satpute_20.ironmind.domain.model.UserProfile
import com.sanket_satpute_20.ironmind.domain.usecase.pattern.EvaluatePatternCandidatesUseCase
import com.sanket_satpute_20.ironmind.domain.engine.BarrierUnderstandingOrchestrator
import com.sanket_satpute_20.ironmind.testutil.fake.FakeAutonomousReflectionEngine
import com.sanket_satpute_20.ironmind.testutil.fake.FakeReflectionRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeUserProfileRepository
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.coVerify
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.ZoneId

class ReflectionProcessingWorkerTest {

    private lateinit var context: Context
    private lateinit var workerParams: WorkerParameters
    private lateinit var reflectionRepository: FakeReflectionRepository
    private lateinit var userProfileRepository: FakeUserProfileRepository
    private lateinit var engine: FakeAutonomousReflectionEngine
    private lateinit var evaluateUseCase: EvaluatePatternCandidatesUseCase
    private lateinit var orchestrator: BarrierUnderstandingOrchestrator
    private lateinit var worker: ReflectionProcessingWorker

    @Before
    fun setup() {
        io.mockk.mockkStatic(android.util.Log::class)
        io.mockk.every { android.util.Log.e(any(), any()) } returns 0
        context = mockk(relaxed = true)
        workerParams = mockk(relaxed = true)
        reflectionRepository = FakeReflectionRepository()
        userProfileRepository = FakeUserProfileRepository()
        engine = FakeAutonomousReflectionEngine()
        evaluateUseCase = mockk(relaxed = true)
        orchestrator = mockk(relaxed = true)
        coEvery { orchestrator.processReflection(any()) } returns DomainResult.Success(Unit)
    }

    private fun createWorker(reflectionId: String?): ReflectionProcessingWorker {
        val dataBuilder = Data.Builder()
        if (reflectionId != null) {
            dataBuilder.putString(ReflectionProcessingWorker.KEY_REFLECTION_ID, reflectionId)
        }
        io.mockk.every { workerParams.inputData } returns dataBuilder.build()
        
        return ReflectionProcessingWorker(
            context,
            workerParams,
            reflectionRepository,
            userProfileRepository,
            engine,
            evaluateUseCase,
            orchestrator
        )
    }

    @Test
    fun `doWork returns failure when reflectionId is missing`() = runBlocking {
        worker = createWorker(null)
        val result = worker.doWork()
        assertEquals(Result.failure(), result)
    }

    @Test
    fun `doWork returns failure when reflection not found`() = runBlocking {
        worker = createWorker("ref-1")
        val result = worker.doWork()
        assertEquals(Result.failure(), result)
    }

    @Test
    fun `doWork returns failure when user profile not found`() = runBlocking {
        reflectionRepository.saveReflection(
            Reflection(
                id = "ref-1", 
                userId = "user-1", 
                content = "Test", 
                createdAt = 0L,
                targetEntityId = null,
                targetEntityType = null,
                sentiment = null
            )
        )
        worker = createWorker("ref-1")
        val result = worker.doWork()
        assertEquals(Result.failure(), result)
    }

    @Test
    fun `doWork calls engine and evaluation on success`() = runBlocking {
        reflectionRepository.saveReflection(
            Reflection(
                id = "ref-1", 
                userId = "user-1", 
                content = "Test", 
                createdAt = 0L,
                targetEntityId = null,
                targetEntityType = null,
                sentiment = null
            )
        )
        userProfileRepository.saveProfile(
            UserProfile(
                id = "user-1", 
                timezone = "UTC", 
                createdAt = 0L,
                updatedAt = 0L,
                displayName = "Test User",
                createdFrom = "Test",
                status = "ACTIVE"
            )
        )

        coEvery { evaluateUseCase(any(), any(), any()) } returns DomainResult.Success(Unit)

        worker = createWorker("ref-1")
        val result = worker.doWork()

        assertEquals(Result.success(), result)
        coVerify(exactly = 1) { evaluateUseCase("user-1", any(), ZoneId.of("UTC")) }
        coVerify(exactly = 1) { orchestrator.processReflection(any()) }
    }

    @Test
    fun `doWork returns retry when engine fails`() = runBlocking {
        reflectionRepository.saveReflection(
            Reflection(
                id = "ref-1", 
                userId = "user-1", 
                content = "Test", 
                createdAt = 0L,
                targetEntityId = null,
                targetEntityType = null,
                sentiment = null
            )
        )
        userProfileRepository.saveProfile(
            UserProfile(
                id = "user-1", 
                timezone = "UTC", 
                createdAt = 0L,
                updatedAt = 0L,
                displayName = "Test User",
                createdFrom = "Test",
                status = "ACTIVE"
            )
        )

        engine.shouldFail = true

        worker = createWorker("ref-1")
        val result = worker.doWork()

        assertEquals(Result.retry(), result)
    }

    @Test
    fun `doWork returns success even if orchestrator fails`() = runBlocking {
        reflectionRepository.saveReflection(
            Reflection(
                id = "ref-1", 
                userId = "user-1", 
                content = "Test", 
                createdAt = 0L,
                targetEntityId = null,
                targetEntityType = null,
                sentiment = null
            )
        )
        userProfileRepository.saveProfile(
            UserProfile(
                id = "user-1", 
                timezone = "UTC", 
                createdAt = 0L,
                updatedAt = 0L,
                displayName = "Test User",
                createdFrom = "Test",
                status = "ACTIVE"
            )
        )

        coEvery { evaluateUseCase(any(), any(), any()) } returns DomainResult.Success(Unit)
        coEvery { orchestrator.processReflection(any()) } returns DomainResult.Failure(Exception("Orchestrator simulated failure"))

        worker = createWorker("ref-1")
        val result = worker.doWork()

        // Pattern pipeline should still succeed
        assertEquals(Result.success(), result)
        coVerify(exactly = 1) { evaluateUseCase("user-1", any(), ZoneId.of("UTC")) }
    }
}
