package com.sanket_satpute_20.ironmind.infrastructure.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.Result as DomainResult
import com.sanket_satpute_20.ironmind.domain.engine.PersonalIntelligenceOrchestrator
import com.sanket_satpute_20.ironmind.domain.logging.IronLogger
import com.sanket_satpute_20.ironmind.domain.repository.UserProfileRepository
import com.sanket_satpute_20.ironmind.domain.session.UserSessionProvider
import com.sanket_satpute_20.ironmind.domain.session.runWithSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap

/**
 * Phase 3.6.7.2: Reliable background worker for offline-first Personal Intelligence orchestration.
 *
 * Guarantees:
 * 1. User & Session Safety: Fails closed if session generation is stale or user does not match.
 * 2. Concurrency Safety: Prevents overlapping executions for the same user via in-process guards and WorkManager.
 * 3. Local-Only Execution: Calls deterministic local intelligence orchestrator with no remote/cloud dependency.
 * 4. Bounded Retry: Caps retries at [MAX_RETRIES] with exponential backoff.
 * 5. Cooldown & Idempotency: Records completion timestamp and deduplicates against Room entities.
 */
class IntelligenceOrchestrationWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val personalIntelligenceOrchestrator: PersonalIntelligenceOrchestrator,
    private val userSessionProvider: UserSessionProvider,
    private val userProfileRepository: UserProfileRepository? = null,
    private val clock: Clock = object : Clock { override fun currentTimeMillis(): Long = System.currentTimeMillis() },
    private val logger: IronLogger? = null,
    private val scheduler: IntelligenceOrchestrationScheduler? = null
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val TAG = "IntelligenceOrchestrationWorker"
        const val KEY_USER_ID = "KEY_USER_ID"
        const val KEY_SESSION_GENERATION = UserSessionProvider.KEY_SESSION_GENERATION
        const val MAX_RETRIES = 3

        private val runningUsers = ConcurrentHashMap.newKeySet<String>()
    }

    override suspend fun doWork(): Result {
        val generation = inputData.getLong(KEY_SESSION_GENERATION, -1L)
        val targetUserId = inputData.getString(KEY_USER_ID)

        if (generation <= 0L || targetUserId.isNullOrBlank()) {
            logger?.logLifecycle(TAG, "FAILED_CLOSED", mapOf("reason" to "missing_or_invalid_inputs", "gen" to generation.toString()))
            println("IronMindLifecycle [IntelligenceOrchestrationWorker] [FAILED_CLOSED] reason=missing_or_invalid_inputs generation=$generation userId=$targetUserId")
            return Result.failure()
        }

        // Concurrency Guard: prevent concurrent orchestration for the same user in this process
        if (!runningUsers.add(targetUserId)) {
            logger?.logLifecycle(TAG, "SKIPPED_ALREADY_RUNNING", mapOf("userId" to targetUserId))
            println("IronMindLifecycle [IntelligenceOrchestrationWorker] [SKIPPED_ALREADY_RUNNING] userId=$targetUserId")
            return Result.success()
        }

        try {
            println("IronMindLifecycle [IntelligenceOrchestrationWorker] [STARTED] userId=$targetUserId attempt=$runAttemptCount")

            val sessionResult = userSessionProvider.runWithSession(expectedGeneration = generation) { activeUserId ->
                // Ownership guard: active user must match target user
                if (activeUserId != targetUserId) {
                    logger?.logLifecycle(TAG, "OWNERSHIP_MISMATCH", mapOf("target" to targetUserId, "active" to activeUserId))
                    println("IronMindLifecycle [IntelligenceOrchestrationWorker] [OWNERSHIP_MISMATCH] target=$targetUserId active=$activeUserId")
                    return@runWithSession Result.success()
                }

                // User erasure guard
                if (userSessionProvider.isUserErased(activeUserId, generation)) {
                    logger?.logLifecycle(TAG, "USER_ERASED", mapOf("userId" to activeUserId))
                    println("IronMindLifecycle [IntelligenceOrchestrationWorker] [USER_ERASED] userId=$activeUserId")
                    return@runWithSession Result.failure()
                }

                val now = clock.currentTimeMillis()

                // Resolve timezone from user profile or system default
                val zoneId = try {
                    val profileResult = userProfileRepository?.getProfile(activeUserId)
                    if (profileResult is DomainResult.Success && profileResult.data?.timezone != null) {
                        ZoneId.of(profileResult.data!!.timezone)
                    } else {
                        ZoneId.systemDefault()
                    }
                } catch (_: Exception) {
                    ZoneId.systemDefault()
                }

                val windowStartTimeMs = now - (7L * 24 * 60 * 60 * 1000L) // 7 days historical lookback
                val windowEndTimeMs = now

                val orchestrateResult = withContext(Dispatchers.IO) {
                    personalIntelligenceOrchestrator.orchestrate(
                        userId = activeUserId,
                        windowStartTimeMs = windowStartTimeMs,
                        windowEndTimeMs = windowEndTimeMs,
                        zoneId = zoneId
                    )
                }

                // Post-execution session re-validation
                val currentActive = userSessionProvider.getActiveUserId()
                val currentGen = userSessionProvider.getSessionGeneration()
                if (currentActive != targetUserId || currentGen != generation) {
                    logger?.logLifecycle(TAG, "SESSION_CHANGED_DURING_EXECUTION", mapOf("target" to targetUserId, "current" to currentActive))
                    println("IronMindLifecycle [IntelligenceOrchestrationWorker] [SESSION_CHANGED] target=$targetUserId current=$currentActive")
                    return@runWithSession Result.failure()
                }

                when (orchestrateResult) {
                    is DomainResult.Success -> {
                        scheduler?.recordCompletion(activeUserId, now)
                        val insightsCount = orchestrateResult.data.newlyDiscoveredInsights.size
                        val activeInsightsCount = orchestrateResult.data.allActiveInsights.size
                        val activePatternsCount = orchestrateResult.data.pipelineResult.activePatterns.size
                        logger?.logLifecycle(TAG, "COMPLETED", mapOf(
                            "userId" to activeUserId,
                            "newInsights" to insightsCount.toString(),
                            "activeInsights" to activeInsightsCount.toString(),
                            "activePatterns" to activePatternsCount.toString()
                        ))
                        println("IronMindLifecycle [IntelligenceOrchestrationWorker] [COMPLETED] userId=$activeUserId newInsights=$insightsCount activePatterns=$activePatternsCount")
                        Result.success()
                    }
                    is DomainResult.Failure -> {
                        val errMsg = orchestrateResult.error.message ?: "UNKNOWN"
                        logger?.logLifecycle(TAG, "FAILED", mapOf("userId" to activeUserId, "error" to errMsg))
                        println("IronMindLifecycle [IntelligenceOrchestrationWorker] [FAILED] userId=$activeUserId error=$errMsg attempt=$runAttemptCount")
                        if (runAttemptCount < MAX_RETRIES) {
                            Result.retry()
                        } else {
                            Result.failure()
                        }
                    }
                }
            }

            return sessionResult ?: Result.failure()
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalStateException) {
            logger?.logLifecycle(TAG, "REJECTED_STALE_GENERATION", mapOf("error" to (e.message ?: "STALE_GENERATION")))
            println("IronMindLifecycle [IntelligenceOrchestrationWorker] [REJECTED_STALE_GENERATION] error=${e.message}")
            return Result.failure()
        } catch (e: Exception) {
            println("IronMindLifecycle [IntelligenceOrchestrationWorker] [RETRY_ON_EXCEPTION] error=${e.message} attempt=$runAttemptCount")
            return if (runAttemptCount < MAX_RETRIES) {
                Result.retry()
            } else {
                Result.failure()
            }
        } finally {
            runningUsers.remove(targetUserId)
        }
    }
}
