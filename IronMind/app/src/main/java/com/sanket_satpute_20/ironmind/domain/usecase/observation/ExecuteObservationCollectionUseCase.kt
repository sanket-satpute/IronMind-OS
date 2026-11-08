package com.sanket_satpute_20.ironmind.domain.usecase.observation

import com.sanket_satpute_20.ironmind.domain.common.Result
import java.util.concurrent.ConcurrentHashMap

sealed class ObservationExecutionError {
    data class CollectionFailed(val exception: Exception) : ObservationExecutionError()
    object AlreadyRunning : ObservationExecutionError()
}

/**
 * A controlled execution boundary for observation collection.
 * 
 * Serves as the single entry point for all observation collection triggers (e.g., manual, lifecycle, WorkManager).
 * Uses a per-user in-process guard to prevent concurrent execution overlaps for the same user
 * while preserving multi-user isolation.
 * 
 * Does NOT implement background scheduling.
 */
class ExecuteObservationCollectionUseCase(
    private val collectObservationsUseCase: CollectObservationsUseCase
) {
    companion object {
        private val runningUsers = ConcurrentHashMap.newKeySet<String>()
    }

    suspend operator fun invoke(userId: String): Result<ObservationCollectionSummary, ObservationExecutionError> {
        if (!runningUsers.add(userId)) {
            println("IronMindLifecycle ObservationExecution [SKIPPED_ALREADY_RUNNING] userId=$userId")
            return Result.Failure(ObservationExecutionError.AlreadyRunning)
        }

        println("IronMindLifecycle ObservationExecution [STARTED] userId=$userId")
        val startTime = System.currentTimeMillis()

        return try {
            val result = collectObservationsUseCase(userId)
            val duration = System.currentTimeMillis() - startTime
            
            when (result) {
                is Result.Success -> {
                    val summary = result.data
                    val status = if (summary.isPartialSuccess) "PARTIAL_SUCCESS" else "SUCCESS"
                    println("IronMindLifecycle ObservationExecution [COMPLETED] status=$status userId=$userId totalCollected=${summary.totalCollected} durationMs=$duration")
                    Result.Success(summary)
                }
                is Result.Failure -> {
                    println("IronMindLifecycle ObservationExecution [FAILED] userId=$userId error=${result.error.message} durationMs=$duration")
                    Result.Failure(ObservationExecutionError.CollectionFailed(result.error))
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            // Log a safe failure event without arbitrary exception dumps
            println("IronMindLifecycle ObservationExecution [FAILED] userId=$userId error=unexpected_exception durationMs=$duration")
            Result.Failure(ObservationExecutionError.CollectionFailed(e))
        } finally {
            runningUsers.remove(userId)
        }
    }
}
