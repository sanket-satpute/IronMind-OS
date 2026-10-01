package com.sanket_satpute_20.ironmind.infrastructure.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.engine.AutonomousReflectionEngine
import com.sanket_satpute_20.ironmind.domain.repository.ReflectionRepository
import com.sanket_satpute_20.ironmind.domain.repository.UserProfileRepository
import com.sanket_satpute_20.ironmind.domain.usecase.pattern.EvaluatePatternCandidatesUseCase
import java.time.ZoneId

class ReflectionProcessingWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val reflectionRepository: ReflectionRepository,
    private val userProfileRepository: UserProfileRepository,
    private val reflectionEngine: AutonomousReflectionEngine,
    private val evaluatePatternCandidatesUseCase: EvaluatePatternCandidatesUseCase
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_REFLECTION_ID = "reflectionId"
    }

    override suspend fun doWork(): Result {
        return try {
            val reflectionId = inputData.getString(KEY_REFLECTION_ID) ?: return Result.failure()
            
            val reflectionResult = reflectionRepository.getReflection(reflectionId)
            if (reflectionResult is com.sanket_satpute_20.ironmind.domain.common.Result.Failure || (reflectionResult as com.sanket_satpute_20.ironmind.domain.common.Result.Success).data == null) {
                return Result.failure()
            }
            val userId = (reflectionResult as com.sanket_satpute_20.ironmind.domain.common.Result.Success).data!!.userId

            val userProfileResult = userProfileRepository.getProfile(userId)
            if (userProfileResult is com.sanket_satpute_20.ironmind.domain.common.Result.Failure || (userProfileResult as com.sanket_satpute_20.ironmind.domain.common.Result.Success).data == null) {
                return Result.failure()
            }
            val zoneId = ZoneId.of((userProfileResult as com.sanket_satpute_20.ironmind.domain.common.Result.Success).data!!.timezone)
            
            when (val result = reflectionEngine.processReflection(reflectionId)) {
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
        } catch (e: java.io.IOException) {
            Result.retry()
        } catch (e: SecurityException) {
            Result.failure()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
