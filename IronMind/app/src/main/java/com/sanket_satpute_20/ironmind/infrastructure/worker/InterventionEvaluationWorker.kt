package com.sanket_satpute_20.ironmind.infrastructure.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sanket_satpute_20.ironmind.domain.ai.AIOutput
import com.sanket_satpute_20.ironmind.domain.ai.InterventionType
import com.sanket_satpute_20.ironmind.domain.common.Result as DomainResult
import com.sanket_satpute_20.ironmind.domain.engine.GoalResurfacingEngine
import com.sanket_satpute_20.ironmind.domain.engine.InterventionExecutionPipeline
import com.sanket_satpute_20.ironmind.domain.logging.IronLogger
import com.sanket_satpute_20.ironmind.domain.model.AutonomyCapability
import com.sanket_satpute_20.ironmind.domain.model.AutonomyLevel
import com.sanket_satpute_20.ironmind.domain.repository.AutonomySettingsRepository
import com.sanket_satpute_20.ironmind.domain.session.UserSessionProvider
import com.sanket_satpute_20.ironmind.domain.session.runWithSession
import kotlinx.coroutines.CancellationException

class InterventionEvaluationWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val interventionExecutionPipeline: InterventionExecutionPipeline,
    private val userSessionProvider: UserSessionProvider? = null,
    private val autonomySettingsRepository: AutonomySettingsRepository? = null,
    private val goalResurfacingEngine: GoalResurfacingEngine? = null,
    private val logger: IronLogger? = null
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_SESSION_GENERATION = UserSessionProvider.KEY_SESSION_GENERATION
        const val KEY_USER_ID = "userId"
        const val KEY_TRIGGER = "trigger"
        const val TRIGGER_SCHEDULED = "scheduled"
        const val TAG = "InterventionEvaluationWorker"
        const val UNIQUE_WORK_NAME = "InterventionEvaluationWorker"
        const val UNIQUE_WORK_NAME_SCHEDULED = "InterventionEvaluationWorker_Scheduled"
        const val UNIQUE_WORK_NAME_PERIODIC = "InterventionEvaluationWorker_Periodic"
        const val UNIQUE_WORK_NAME_EVENT = "InterventionEvaluationWorker_Event"
        const val UNIQUE_WORK_NAME_CANDIDATE_PREFIX = "InterventionEvaluationCandidate_"

        // Explicit candidate parameters if enqueued with candidate
        const val KEY_RECOMMENDATION = "recommendation"
        const val KEY_REASON = "reason"
        const val KEY_INTERVENTION_TYPE = "interventionType"
        const val KEY_TARGET_ENTITY_ID = "targetEntityId"
        const val KEY_CONFIDENCE = "confidence"
    }

    override suspend fun doWork(): Result {
        val generation = inputData.getLong(KEY_SESSION_GENERATION, -1L)
        if (generation <= 0L || userSessionProvider == null) {
            logger?.logLifecycle("InterventionWorker", "FAILED_CLOSED", mapOf("reason" to "missing_or_invalid_generation"))
            return Result.failure()
        }

        return try {
            val explicitUserId = inputData.getString(KEY_USER_ID)

            val sessionResult = userSessionProvider.runWithSession(expectedGeneration = generation) { activeUserId ->
                // Ownership check
                if (explicitUserId != null && explicitUserId != activeUserId) {
                    logger?.logLifecycle(
                        "InterventionWorker",
                        "OWNERSHIP_MISMATCH",
                        mapOf("explicit" to explicitUserId, "active" to activeUserId)
                    )
                    return@runWithSession Result.failure()
                }

                val targetUserId = activeUserId

                // Respect global pause and autonomy settings
                if (autonomySettingsRepository != null) {
                    val settingsResult = autonomySettingsRepository.getSettings(targetUserId)
                    if (settingsResult is DomainResult.Success) {
                        val settings = settingsResult.data
                        if (settings.isGlobalPauseActive) {
                            logger?.logLifecycle("InterventionWorker", "SUPPRESSED_GLOBAL_PAUSE", mapOf("userId" to targetUserId))
                            return@runWithSession Result.success()
                        }
                        val level = settings.getLevel(AutonomyCapability.INTERVENTION_GENERATION)
                        if (level == AutonomyLevel.OFF) {
                            logger?.logLifecycle("InterventionWorker", "SUPPRESSED_AUTONOMY_OFF", mapOf("userId" to targetUserId))
                            return@runWithSession Result.success()
                        }
                    } else if (settingsResult is DomainResult.Failure) {
                        return@runWithSession Result.retry()
                    }
                }

                // Check for explicit candidate recommendation in inputData
                val recommendationText = inputData.getString(KEY_RECOMMENDATION)
                val reasonText = inputData.getString(KEY_REASON)
                val typeString = inputData.getString(KEY_INTERVENTION_TYPE)
                val targetEntityId = inputData.getString(KEY_TARGET_ENTITY_ID)

                if (!recommendationText.isNullOrBlank() && !reasonText.isNullOrBlank() && !typeString.isNullOrBlank()) {
                    val interventionType = try {
                        InterventionType.valueOf(typeString)
                    } catch (_: Exception) {
                        InterventionType.ASK
                    }
                    val confidence = inputData.getFloat(KEY_CONFIDENCE, 0.8f)

                    val candidate = AIOutput.InterventionRecommendation(
                        interventionType = interventionType,
                        recommendation = recommendationText,
                        reason = reasonText,
                        targetEntityId = targetEntityId,
                        confidence = confidence
                    )

                    val proposeResult = interventionExecutionPipeline.propose(targetUserId, candidate)
                    when (proposeResult) {
                        is DomainResult.Success -> {
                            val record = proposeResult.data
                            logger?.logLifecycle(
                                "InterventionWorker",
                                "CANDIDATE_PROPOSED",
                                mapOf("id" to record.id, "state" to record.state.name)
                            )
                            Result.success()
                        }
                        is DomainResult.Failure -> {
                            logger?.logLifecycle(
                                "InterventionWorker",
                                "PROPOSE_FAILED",
                                mapOf("error" to (proposeResult.error.message ?: "UNKNOWN"))
                            )
                            Result.retry()
                        }
                    }
                } else if (goalResurfacingEngine != null) {
                    // Evaluate neglected goals via GoalResurfacingEngine
                    try {
                        goalResurfacingEngine.evaluateNeglectedGoals(targetUserId)
                        logger?.logLifecycle("InterventionWorker", "NEGLECTED_GOALS_EVALUATED", mapOf("userId" to targetUserId))
                        Result.success()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        logger?.logLifecycle("InterventionWorker", "EVALUATE_GOALS_FAILED", mapOf("error" to (e.message ?: "UNKNOWN")))
                        Result.retry()
                    }
                } else {
                    Result.success()
                }
            }

            sessionResult ?: Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalStateException) {
            logger?.logLifecycle("InterventionWorker", "REJECTED_STALE_GENERATION", mapOf("error" to (e.message ?: "")))
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
