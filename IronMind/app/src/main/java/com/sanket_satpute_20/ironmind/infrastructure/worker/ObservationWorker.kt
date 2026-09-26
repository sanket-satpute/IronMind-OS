package com.sanket_satpute_20.ironmind.infrastructure.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.repository.AuthRepository
import com.sanket_satpute_20.ironmind.domain.usecase.observation.ExecuteObservationCollectionUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.observation.ObservationExecutionError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ObservationWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val executeObservationCollectionUseCase: ExecuteObservationCollectionUseCase,
    private val authRepository: AuthRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        println("IronMindLifecycle ObservationWorker [STARTED]")

        return withContext(Dispatchers.IO) {
            val user = authRepository.getCurrentUser()
            if (user == null) {
                println("IronMindLifecycle ObservationWorker [FAILED] reason=missing_user")
                return@withContext Result.failure()
            }

            val result = executeObservationCollectionUseCase(user.id)
            when (result) {
                is com.sanket_satpute_20.ironmind.domain.common.Result.Success -> {
                    println("IronMindLifecycle ObservationWorker [COMPLETED]")
                    Result.success()
                }
                is com.sanket_satpute_20.ironmind.domain.common.Result.Failure -> {
                    when (result.error) {
                        is ObservationExecutionError.AlreadyRunning -> {
                            println("IronMindLifecycle ObservationWorker [COMPLETED] reason=already_running")
                            Result.success() // Already running means another execution is handling it, no need to retry
                        }
                        is ObservationExecutionError.CollectionFailed -> {
                            println("IronMindLifecycle ObservationWorker [FAILED] reason=collection_failed")
                            Result.failure() // Do not blindly retry since provider failures are usually permission/setting issues
                        }
                    }
                }
            }
        }
    }
}
