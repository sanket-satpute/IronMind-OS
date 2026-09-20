package com.sanket_satpute_20.ironmind.infrastructure.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.engine.AutonomousReflectionEngine

class ReflectionProcessingWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val reflectionEngine: AutonomousReflectionEngine
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_REFLECTION_ID = "reflectionId"
    }

    override suspend fun doWork(): Result {
        val reflectionId = inputData.getString(KEY_REFLECTION_ID)
            ?: return Result.failure()

        return when (val result = reflectionEngine.processReflection(reflectionId)) {
            is com.sanket_satpute_20.ironmind.domain.common.Result.Success<*> -> {
                Result.success()
            }
            is com.sanket_satpute_20.ironmind.domain.common.Result.Failure<*> -> {
                // Retry safely on failure
                Result.retry()
            }
        }
    }
}
