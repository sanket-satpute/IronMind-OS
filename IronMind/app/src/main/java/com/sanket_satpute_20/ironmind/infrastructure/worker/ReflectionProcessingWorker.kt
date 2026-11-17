package com.sanket_satpute_20.ironmind.infrastructure.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sanket_satpute_20.ironmind.domain.engine.AutonomousReflectionEngine
import com.sanket_satpute_20.ironmind.domain.engine.BarrierUnderstandingOrchestrator
import com.sanket_satpute_20.ironmind.domain.repository.ReflectionRepository
import com.sanket_satpute_20.ironmind.domain.repository.UserProfileRepository
import com.sanket_satpute_20.ironmind.domain.session.UserSessionProvider
import com.sanket_satpute_20.ironmind.domain.usecase.pattern.EvaluatePatternCandidatesUseCase
import kotlinx.coroutines.CancellationException
import java.time.ZoneId

class ReflectionProcessingWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val reflectionRepository: ReflectionRepository,
    private val userProfileRepository: UserProfileRepository,
    private val reflectionEngine: AutonomousReflectionEngine,
    private val evaluatePatternCandidatesUseCase: EvaluatePatternCandidatesUseCase,
    private val barrierUnderstandingOrchestrator: BarrierUnderstandingOrchestrator,
    private val userSessionProvider: UserSessionProvider
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_SESSION_GENERATION = UserSessionProvider.KEY_SESSION_GENERATION
        const val KEY_REFLECTION_ID = "reflectionId"
        const val TAG = "ReflectionProcessingWorker"
    }

    override suspend fun doWork(): Result {
        val generation = inputData.getLong(KEY_SESSION_GENERATION, -1L)
        if (generation <= 0L) {
            return Result.failure()
        }
        return try {
            userSessionProvider.withUserSession(expectedGeneration = generation) { activeUserId -> doWorkForUser(activeUserId) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalStateException) {
            Result.failure()
        } catch (e: SecurityException) {
            Result.failure()
        } catch (e: java.io.IOException) {
            Result.retry()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private suspend fun doWorkForUser(activeUserId: String): Result {
        val reflectionId = inputData.getString(KEY_REFLECTION_ID) ?: return Result.failure()

        val reflectionResult = reflectionRepository.getReflection(reflectionId)
        if (reflectionResult is com.sanket_satpute_20.ironmind.domain.common.Result.Failure || (reflectionResult as com.sanket_satpute_20.ironmind.domain.common.Result.Success).data == null) {
            return Result.failure()
        }
        val reflection = (reflectionResult as com.sanket_satpute_20.ironmind.domain.common.Result.Success).data!!
        if (reflection.userId != activeUserId) return Result.retry()
        val userId = activeUserId

        val userProfileResult = userProfileRepository.getProfile(userId)
        if (userProfileResult is com.sanket_satpute_20.ironmind.domain.common.Result.Failure || (userProfileResult as com.sanket_satpute_20.ironmind.domain.common.Result.Success).data == null) {
            return Result.failure()
        }
        val zoneId = ZoneId.of((userProfileResult as com.sanket_satpute_20.ironmind.domain.common.Result.Success).data!!.timezone)

            /*
             * Barrier Understanding is currently integrated into reflection processing for capability verification.
             * This invocation is not yet the final autonomous production trigger policy.
             * Trigger policy is deferred to the next explicit product/architecture decision.
             */
        val barrierResult = barrierUnderstandingOrchestrator.processReflection(reflection)
        if (barrierResult is com.sanket_satpute_20.ironmind.domain.common.Result.Failure) {
            // Failure Isolation: Barrier pipeline failures should not block Pattern pipeline
            android.util.Log.e("ReflectionProcessingWorker", "Barrier pipeline failed: ${barrierResult.error.javaClass.simpleName}")
        }

        return when (val result = reflectionEngine.processReflection(reflectionId)) {
            is com.sanket_satpute_20.ironmind.domain.common.Result.Success -> {
                evaluatePatternCandidatesUseCase(
                    userId = userId,
                    candidates = result.data,
                    zoneId = zoneId
                )
                Result.success()
            }
            is com.sanket_satpute_20.ironmind.domain.common.Result.Failure -> Result.retry()
        }
    }
}
