package com.sanket_satpute_20.ironmind.infrastructure.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sanket_satpute_20.ironmind.domain.session.UserSessionProvider
import com.sanket_satpute_20.ironmind.domain.usecase.observation.ExecuteObservationCollectionUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.observation.ObservationExecutionError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

class ObservationWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val executeObservationCollectionUseCase: ExecuteObservationCollectionUseCase,
    private val userSessionProvider: UserSessionProvider,
    private val intelligenceOrchestrationScheduler: IntelligenceOrchestrationScheduler? = null
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_SESSION_GENERATION = UserSessionProvider.KEY_SESSION_GENERATION
        const val TAG = "ObservationWorker"
    }

    override suspend fun doWork(): Result {
        println("IronMindLifecycle ObservationWorker [STARTED]")

        val generation = inputData.getLong(KEY_SESSION_GENERATION, -1L)
        if (generation <= 0L) {
            println("IronMindLifecycle [ObservationWorker] [FAILED_CLOSED] Reason=missing_or_invalid_generation generation=$generation")
            return Result.failure()
        }

        return try {
            userSessionProvider.withUserSession(expectedGeneration = generation) { activeUserId ->
                withContext(Dispatchers.IO) {
                    val result = executeObservationCollectionUseCase(activeUserId)
                    when (result) {
                        is com.sanket_satpute_20.ironmind.domain.common.Result.Success -> {
                            val summary = result.data
                            if (summary.totalCollected > 0) {
                                println("IronMindLifecycle [ObservationWorker] [TRIGGER_INTELLIGENCE] userId=$activeUserId count=${summary.totalCollected}")
                                intelligenceOrchestrationScheduler?.scheduleOrchestration(
                                    userId = activeUserId,
                                    generation = generation
                                )
                            } else {
                                println("IronMindLifecycle [ObservationWorker] [SKIP_INTELLIGENCE] reason=no_new_data userId=$activeUserId")
                            }
                            println("IronMindLifecycle ObservationWorker [COMPLETED]")
                            Result.success()
                        }
                        is com.sanket_satpute_20.ironmind.domain.common.Result.Failure -> {
                            when (result.error) {
                                is ObservationExecutionError.AlreadyRunning -> {
                                    println("IronMindLifecycle ObservationWorker [COMPLETED] reason=already_running")
                                    Result.success()
                                }
                                is ObservationExecutionError.CollectionFailed -> {
                                    println("IronMindLifecycle ObservationWorker [FAILED] reason=collection_failed")
                                    Result.failure()
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalStateException) {
            if (e.message?.contains("reconciliation", ignoreCase = true) == true) {
                println("IronMindLifecycle [ObservationWorker] [RETRY_RECONCILIATION] error=${e.message}")
                Result.retry()
            } else {
                println("IronMindLifecycle [ObservationWorker] [REJECTED_STALE_GENERATION] error=${e.message}")
                Result.failure()
            }
        } catch (e: Exception) {
            println("IronMindLifecycle ObservationWorker [RETRY] reason=session_or_storage_error")
            Result.retry()
        }
    }
}
