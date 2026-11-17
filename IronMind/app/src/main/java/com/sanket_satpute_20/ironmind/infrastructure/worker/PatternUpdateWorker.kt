package com.sanket_satpute_20.ironmind.infrastructure.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.Result as DomainResult
import com.sanket_satpute_20.ironmind.domain.engine.PatternEngine
import com.sanket_satpute_20.ironmind.domain.logging.IronLogger
import com.sanket_satpute_20.ironmind.domain.model.AutonomyCapability
import com.sanket_satpute_20.ironmind.domain.model.AutonomyLevel
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternCandidate
import com.sanket_satpute_20.ironmind.domain.repository.AutonomySettingsRepository
import com.sanket_satpute_20.ironmind.domain.repository.UserProfileRepository
import com.sanket_satpute_20.ironmind.domain.session.UserSessionProvider
import com.sanket_satpute_20.ironmind.domain.session.runWithSession
import com.sanket_satpute_20.ironmind.domain.usecase.model.EvolvePersonalModelUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.pattern.EvaluatePatternCandidatesUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.pattern.LearnInterventionResponsePatternUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.pattern.LearnInterventionTimingPatternUseCase
import kotlinx.coroutines.CancellationException
import java.time.ZoneId

class PatternUpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val patternEngine: PatternEngine,
    private val userSessionProvider: UserSessionProvider? = null,
    private val evolvePersonalModelUseCase: EvolvePersonalModelUseCase? = null,
    private val autonomySettingsRepository: AutonomySettingsRepository? = null,
    private val learnInterventionResponsePatternUseCase: LearnInterventionResponsePatternUseCase? = null,
    private val learnInterventionTimingPatternUseCase: LearnInterventionTimingPatternUseCase? = null,
    private val evaluatePatternCandidatesUseCase: EvaluatePatternCandidatesUseCase? = null,
    private val userProfileRepository: UserProfileRepository? = null,
    private val clock: Clock? = null,
    private val logger: IronLogger? = null
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_USER_ID = "userId"
        const val KEY_SESSION_GENERATION = UserSessionProvider.KEY_SESSION_GENERATION
        const val TAG = "PatternUpdateWorker"
        const val UNIQUE_WORK_NAME = "PatternUpdateWorker"
    }

    override suspend fun doWork(): Result {
        return try {
            val generation = inputData.getLong(KEY_SESSION_GENERATION, -1L)
            if (generation <= 0L || userSessionProvider == null) {
                logger?.logLifecycle("PatternWorker", "FAILED_CLOSED", mapOf("reason" to "missing_or_invalid_generation"))
                return Result.failure()
            }

            val explicitUserId = inputData.getString(KEY_USER_ID)
            val sessionResult = userSessionProvider.runWithSession(expectedGeneration = generation) { activeUserId ->
                if (explicitUserId != null && explicitUserId != activeUserId) {
                    logger?.logLifecycle("PatternWorker", "OWNERSHIP_MISMATCH", mapOf("explicit" to explicitUserId, "active" to activeUserId))
                    return@runWithSession Result.success()
                }

                // 1. Decay stale patterns (skipping USER_CONFIRMED)
                val decayResult = patternEngine.decayStalePatterns(activeUserId)
                if (decayResult is DomainResult.Failure) {
                    logger?.logLifecycle("PatternWorker", "DECAY_FAILED", mapOf("error" to (decayResult.error.message ?: "UNKNOWN")))
                    return@runWithSession Result.retry()
                }

                // 2. Background Learning and Model Evolution
                val hasLearningUseCases = evolvePersonalModelUseCase != null ||
                    learnInterventionResponsePatternUseCase != null ||
                    learnInterventionTimingPatternUseCase != null

                if (hasLearningUseCases) {
                    var isAllowed = true
                    if (autonomySettingsRepository != null) {
                        val settingsResult = autonomySettingsRepository.getSettings(activeUserId)
                        if (settingsResult is DomainResult.Success) {
                            val settings = settingsResult.data
                            if (settings.isGlobalPauseActive) {
                                logger?.logLifecycle("PatternWorker", "EVOLUTION_SUPPRESSED_GLOBAL_PAUSE", mapOf("userId" to activeUserId))
                                isAllowed = false
                            } else {
                                val bgLearning = settings.getLevel(AutonomyCapability.BACKGROUND_LEARNING)
                                val memProcessing = settings.getLevel(AutonomyCapability.MEMORY_PATTERN_PROCESSING)
                                if (bgLearning == AutonomyLevel.OFF || memProcessing == AutonomyLevel.OFF) {
                                    logger?.logLifecycle("PatternWorker", "EVOLUTION_SUPPRESSED_AUTONOMY_OFF", mapOf("userId" to activeUserId))
                                    isAllowed = false
                                }
                            }
                        } else if (settingsResult is DomainResult.Failure) {
                            return@runWithSession Result.retry()
                        }
                    }

                    if (isAllowed) {
                        if (evolvePersonalModelUseCase != null) {
                            val evolveResult = evolvePersonalModelUseCase(activeUserId)
                            if (evolveResult is DomainResult.Failure) {
                                logger?.logLifecycle("PatternWorker", "EVOLUTION_FAILED", mapOf("error" to (evolveResult.error.message ?: "UNKNOWN")))
                                return@runWithSession Result.retry()
                            }
                            logger?.logLifecycle("PatternWorker", "EVOLUTION_SUCCEEDED", mapOf("userId" to activeUserId))
                        }

                        // 3. Closed-Loop Intervention Learning
                        val now = clock?.currentTimeMillis() ?: System.currentTimeMillis()
                        val sinceTimestamp = now - (30L * 24 * 60 * 60 * 1000L) // last 30 days
                        val learnedCandidates = mutableListOf<PatternCandidate>()

                        if (learnInterventionResponsePatternUseCase != null) {
                            val responseResult = learnInterventionResponsePatternUseCase(activeUserId, sinceTimestamp)
                            if (responseResult is DomainResult.Success && responseResult.data != null) {
                                learnedCandidates.add(responseResult.data!!)
                                logger?.logLifecycle("PatternWorker", "INTERVENTION_RESPONSE_PATTERN_LEARNED", mapOf("userId" to activeUserId))
                            } else if (responseResult is DomainResult.Failure) {
                                logger?.logLifecycle("PatternWorker", "INTERVENTION_RESPONSE_LEARNING_FAILED", mapOf("error" to (responseResult.error.message ?: "UNKNOWN")))
                            }
                        }

                        if (learnInterventionTimingPatternUseCase != null) {
                            val timingResult = learnInterventionTimingPatternUseCase(activeUserId, sinceTimestamp)
                            if (timingResult is DomainResult.Success && timingResult.data != null) {
                                learnedCandidates.add(timingResult.data!!)
                                logger?.logLifecycle("PatternWorker", "INTERVENTION_TIMING_PATTERN_LEARNED", mapOf("userId" to activeUserId))
                            } else if (timingResult is DomainResult.Failure) {
                                logger?.logLifecycle("PatternWorker", "INTERVENTION_TIMING_LEARNING_FAILED", mapOf("error" to (timingResult.error.message ?: "UNKNOWN")))
                            }
                        }

                        if (learnedCandidates.isNotEmpty() && evaluatePatternCandidatesUseCase != null) {
                            val profileResult = userProfileRepository?.getProfile(activeUserId)
                            val zoneId = if (profileResult is DomainResult.Success && profileResult.data?.timezone != null) {
                                try {
                                    ZoneId.of(profileResult.data!!.timezone)
                                } catch (_: Exception) {
                                    ZoneId.systemDefault()
                                }
                            } else {
                                ZoneId.systemDefault()
                            }

                            val evalResult = evaluatePatternCandidatesUseCase(
                                userId = activeUserId,
                                candidates = learnedCandidates,
                                zoneId = zoneId
                            )
                            if (evalResult is DomainResult.Failure) {
                                logger?.logLifecycle("PatternWorker", "EVALUATE_LEARNED_CANDIDATES_FAILED", mapOf("error" to (evalResult.error.message ?: "UNKNOWN")))
                            } else {
                                logger?.logLifecycle("PatternWorker", "LEARNED_CANDIDATES_EVALUATED", mapOf("count" to learnedCandidates.size.toString()))
                            }
                        }
                    }
                }

                Result.success()
            }

            sessionResult ?: Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalStateException) {
            logger?.logLifecycle("PatternWorker", "REJECTED_STALE_GENERATION", mapOf("error" to (e.message ?: "STALE_GENERATION")))
            Result.failure()
        } catch (e: SecurityException) {
            Result.failure()
        } catch (e: java.io.IOException) {
            Result.retry()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
