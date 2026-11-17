package com.sanket_satpute_20.ironmind.infrastructure.worker

import android.content.Context
import androidx.work.Data
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.sanket_satpute_20.ironmind.domain.engine.InterventionExecutionPipeline
import com.sanket_satpute_20.ironmind.domain.engine.PatternEngine
import com.sanket_satpute_20.ironmind.domain.session.SessionState
import com.sanket_satpute_20.ironmind.domain.session.UserSessionProvider
import com.sanket_satpute_20.ironmind.domain.usecase.sync.SyncUseCase
import com.sanket_satpute_20.ironmind.testutil.FakeUserSessionProvider
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class SessionScopedMaintenanceWorkerTest {
    private val context = mockk<Context>(relaxed = true)

    private fun params(userId: String?, generation: Long = 1L): WorkerParameters = mockk(relaxed = true) {
        val builder = Data.Builder()
        if (generation > 0L) {
            builder.putLong(UserSessionProvider.KEY_SESSION_GENERATION, generation)
        }
        if (userId != null) {
            builder.putString("userId", userId)
        }
        every { inputData } returns builder.build()
    }

    private fun cancelingProvider(): UserSessionProvider {
        val delegate = FakeUserSessionProvider(SessionState.Authenticated("user-2", isAnonymous = true))
        return object : UserSessionProvider by delegate {
            override suspend fun <T> withUserSession(action: suspend (userId: String) -> T): T {
                throw CancellationException("worker stopped")
            }
            override suspend fun <T> withUserSession(expectedGeneration: Long, action: suspend (userId: String) -> T): T {
                throw CancellationException("worker stopped")
            }
        }
    }

    @Test
    fun `stale explicit UID is skipped by sync and pattern workers`() = runBlocking {
        val provider = FakeUserSessionProvider(SessionState.Authenticated("user-2", isAnonymous = true))
        val syncUseCase = mockk<SyncUseCase>(relaxed = true)
        val patternEngine = mockk<PatternEngine>(relaxed = true)

        val syncResult = SyncWorker(context, params("user-1"), syncUseCase, provider).doWork()
        val patternResult = PatternUpdateWorker(context, params("user-1"), patternEngine, provider).doWork()

        assertEquals(Result.success(), syncResult)
        assertEquals(Result.success(), patternResult)
        coVerify(exactly = 0) { syncUseCase(any()) }
        coVerify(exactly = 0) { patternEngine.decayStalePatterns(any()) }
    }

    @Test
    fun `session cancellation is rethrown by maintenance workers`() = runBlocking {
        val provider = cancelingProvider()
        val sync = SyncWorker(context, params(null), mockk(relaxed = true), provider)
        val pattern = PatternUpdateWorker(context, params(null), mockk(relaxed = true), provider)
        val intervention = InterventionEvaluationWorker(
            context,
            params(null),
            mockk<InterventionExecutionPipeline>(relaxed = true),
            provider
        )

        for (worker in listOf(sync, pattern, intervention)) {
            try {
                worker.doWork()
                fail("Cancellation must escape ${worker::class.simpleName}")
            } catch (_: CancellationException) {
                // Expected: WorkManager owns cancellation handling.
            }
        }
    }
}
