package com.sanket_satpute_20.ironmind.infrastructure.worker

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker.Result
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sanket_satpute_20.ironmind.domain.common.Result as DomainResult
import com.sanket_satpute_20.ironmind.domain.engine.PatternEngine
import com.sanket_satpute_20.ironmind.domain.model.AutonomyCapability
import com.sanket_satpute_20.ironmind.domain.model.AutonomyLevel
import com.sanket_satpute_20.ironmind.domain.model.AutonomySettings
import com.sanket_satpute_20.ironmind.domain.repository.AutonomySettingsRepository
import com.sanket_satpute_20.ironmind.domain.session.SessionState
import com.sanket_satpute_20.ironmind.domain.session.UserSessionProvider
import com.sanket_satpute_20.ironmind.domain.usecase.model.EvolvePersonalModelUseCase
import com.sanket_satpute_20.ironmind.execution.background.BackgroundExecutorImpl
import com.sanket_satpute_20.ironmind.testutil.FakeUserSessionProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PatternUpdateWorkerTest {

    private lateinit var context: Context
    private lateinit var workerParams: WorkerParameters
    private lateinit var sessionProvider: FakeUserSessionProvider
    private lateinit var patternEngine: PatternEngine
    private lateinit var evolveUseCase: EvolvePersonalModelUseCase
    private lateinit var autonomyRepo: AutonomySettingsRepository

    private val userId = "user-pat-789"

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        workerParams = mockk(relaxed = true)
        sessionProvider = FakeUserSessionProvider(SessionState.Authenticated(userId, false))
        patternEngine = mockk(relaxed = true)
        evolveUseCase = mockk(relaxed = true)
        autonomyRepo = mockk(relaxed = true)

        coEvery { patternEngine.decayStalePatterns(userId) } returns DomainResult.Success(Unit)
        coEvery { evolveUseCase(userId) } returns DomainResult.Success(Unit)
        coEvery { autonomyRepo.getSettings(userId) } returns DomainResult.Success(
            AutonomySettings(
                userId = userId,
                levels = mapOf(
                    AutonomyCapability.BACKGROUND_LEARNING to AutonomyLevel.FULL_AUTO,
                    AutonomyCapability.MEMORY_PATTERN_PROCESSING to AutonomyLevel.FULL_AUTO
                ),
                isGlobalPauseActive = false
            )
        )
    }

    private fun createWorker(
        inputData: Data,
        customEvolveUseCase: EvolvePersonalModelUseCase? = evolveUseCase,
        customAutonomyRepo: AutonomySettingsRepository? = autonomyRepo
    ): PatternUpdateWorker {
        every { workerParams.inputData } returns inputData
        return PatternUpdateWorker(
            appContext = context,
            workerParams = workerParams,
            patternEngine = patternEngine,
            userSessionProvider = sessionProvider,
            evolvePersonalModelUseCase = customEvolveUseCase,
            autonomySettingsRepository = customAutonomyRepo
        )
    }

    @Test
    fun `valid execution decays stale patterns and succeeds`() = runBlocking {
        val inputData = Data.Builder()
            .putLong(UserSessionProvider.KEY_SESSION_GENERATION, sessionProvider.getSessionGeneration())
            .putString(PatternUpdateWorker.KEY_USER_ID, userId)
            .build()

        val worker = createWorker(inputData)
        val result = worker.doWork()

        assertEquals(Result.success(), result)
        coVerify(exactly = 1) { patternEngine.decayStalePatterns(userId) }
    }

    @Test
    fun `missing session generation fails closed`() = runBlocking {
        val inputData = Data.Builder().build()

        val worker = createWorker(inputData)
        val result = worker.doWork()

        assertEquals(Result.failure(), result)
        coVerify(exactly = 0) { patternEngine.decayStalePatterns(any()) }
    }

    @Test
    fun `stale session generation fails closed`() = runBlocking {
        val inputData = Data.Builder()
            .putLong(UserSessionProvider.KEY_SESSION_GENERATION, 888L) // Stale generation
            .build()

        val worker = createWorker(inputData)
        val result = worker.doWork()

        assertEquals(Result.failure(), result)
        coVerify(exactly = 0) { patternEngine.decayStalePatterns(any()) }
    }

    @Test
    fun `ownership mismatch cleanly skips decay`() = runBlocking {
        val inputData = Data.Builder()
            .putLong(UserSessionProvider.KEY_SESSION_GENERATION, sessionProvider.getSessionGeneration())
            .putString(PatternUpdateWorker.KEY_USER_ID, "attacker-user-id")
            .build()

        val worker = createWorker(inputData)
        val result = worker.doWork()

        assertEquals(Result.success(), result)
        coVerify(exactly = 0) { patternEngine.decayStalePatterns(any()) }
    }

    @Test
    fun `pattern engine decay failure returns retry`() = runBlocking {
        val inputData = Data.Builder()
            .putLong(UserSessionProvider.KEY_SESSION_GENERATION, sessionProvider.getSessionGeneration())
            .build()

        coEvery { patternEngine.decayStalePatterns(userId) } returns DomainResult.Failure(RuntimeException("SQLite busy"))

        val worker = createWorker(inputData)
        val result = worker.doWork()

        assertEquals(Result.retry(), result)
    }

    @Test
    fun `BackgroundExecutorImpl schedulePatternMaintenance enqueues unique periodic work with UPDATE policy`() {
        val mockWm = mockk<WorkManager>(relaxed = true)
        val requestSlot = slot<PeriodicWorkRequest>()

        every {
            mockWm.enqueueUniquePeriodicWork(
                PatternUpdateWorker.UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                capture(requestSlot)
            )
        } returns mockk()

        val executor = BackgroundExecutorImpl(
            context = context,
            workManager = mockWm,
            sessionProvider = { sessionProvider }
        )

        executor.schedulePatternMaintenance()

        val captured = requestSlot.captured
        val generation = captured.workSpec.input.getLong(UserSessionProvider.KEY_SESSION_GENERATION, -1L)

        assertEquals(sessionProvider.getSessionGeneration(), generation)
        assertTrue(captured.tags.contains(PatternUpdateWorker.TAG))
    }

    @Test
    fun `valid execution invokes evolvePersonalModelUseCase under session read barrier`() = runBlocking {
        val inputData = Data.Builder()
            .putLong(UserSessionProvider.KEY_SESSION_GENERATION, sessionProvider.getSessionGeneration())
            .putString(PatternUpdateWorker.KEY_USER_ID, userId)
            .build()

        val worker = createWorker(inputData)
        val result = worker.doWork()

        assertEquals(Result.success(), result)
        coVerify(exactly = 1) { evolveUseCase(userId) }
    }

    @Test
    fun `global pause active suppresses evolvePersonalModelUseCase`() = runBlocking {
        coEvery { autonomyRepo.getSettings(userId) } returns DomainResult.Success(
            AutonomySettings(
                userId = userId,
                levels = mapOf(
                    AutonomyCapability.BACKGROUND_LEARNING to AutonomyLevel.FULL_AUTO,
                    AutonomyCapability.MEMORY_PATTERN_PROCESSING to AutonomyLevel.FULL_AUTO
                ),
                isGlobalPauseActive = true
            )
        )

        val inputData = Data.Builder()
            .putLong(UserSessionProvider.KEY_SESSION_GENERATION, sessionProvider.getSessionGeneration())
            .putString(PatternUpdateWorker.KEY_USER_ID, userId)
            .build()

        val worker = createWorker(inputData)
        val result = worker.doWork()

        assertEquals(Result.success(), result)
        coVerify(exactly = 0) { evolveUseCase(any()) }
    }

    @Test
    fun `autonomy capability OFF suppresses evolvePersonalModelUseCase`() = runBlocking {
        coEvery { autonomyRepo.getSettings(userId) } returns DomainResult.Success(
            AutonomySettings(
                userId = userId,
                levels = mapOf(
                    AutonomyCapability.BACKGROUND_LEARNING to AutonomyLevel.OFF,
                    AutonomyCapability.MEMORY_PATTERN_PROCESSING to AutonomyLevel.FULL_AUTO
                ),
                isGlobalPauseActive = false
            )
        )

        val inputData = Data.Builder()
            .putLong(UserSessionProvider.KEY_SESSION_GENERATION, sessionProvider.getSessionGeneration())
            .putString(PatternUpdateWorker.KEY_USER_ID, userId)
            .build()

        val worker = createWorker(inputData)
        val result = worker.doWork()

        assertEquals(Result.success(), result)
        coVerify(exactly = 0) { evolveUseCase(any()) }
    }

    @Test
    fun `evolution failure returns retry`() = runBlocking {
        coEvery { evolveUseCase(userId) } returns DomainResult.Failure(RuntimeException("AI gateway timeout"))

        val inputData = Data.Builder()
            .putLong(UserSessionProvider.KEY_SESSION_GENERATION, sessionProvider.getSessionGeneration())
            .putString(PatternUpdateWorker.KEY_USER_ID, userId)
            .build()

        val worker = createWorker(inputData)
        val result = worker.doWork()

        assertEquals(Result.retry(), result)
    }

    @Test
    fun `stale generation does not invoke evolvePersonalModelUseCase`() = runBlocking {
        val inputData = Data.Builder()
            .putLong(UserSessionProvider.KEY_SESSION_GENERATION, 999L)
            .build()

        val worker = createWorker(inputData)
        val result = worker.doWork()

        assertEquals(Result.failure(), result)
        coVerify(exactly = 0) { evolveUseCase(any()) }
    }
}

