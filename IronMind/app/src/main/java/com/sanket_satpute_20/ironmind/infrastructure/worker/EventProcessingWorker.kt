package com.sanket_satpute_20.ironmind.infrastructure.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.ListenableWorker.Result
import androidx.room.withTransaction
import com.sanket_satpute_20.ironmind.data.local.IronMindDatabase
import com.sanket_satpute_20.ironmind.data.local.dao.IronMindDao
import com.sanket_satpute_20.ironmind.data.local.dao.LearnerEventStatusDao
import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.Result as DomainResult
import com.sanket_satpute_20.ironmind.domain.logging.IronLogger
import com.sanket_satpute_20.ironmind.domain.repository.EventRepository
import com.sanket_satpute_20.ironmind.domain.session.UserSessionProvider
import com.sanket_satpute_20.ironmind.domain.session.runWithSession
import com.sanket_satpute_20.ironmind.execution.background.DefaultEventProcessor
import com.sanket_satpute_20.ironmind.execution.background.EventBackoffPolicy
import com.sanket_satpute_20.ironmind.execution.background.EventProcessingOutcome
import com.sanket_satpute_20.ironmind.execution.background.EventProcessor
import kotlinx.coroutines.CancellationException
import java.util.UUID

class EventProcessingWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val learnerEventStatusDao: LearnerEventStatusDao,
    private val eventRepository: EventRepository,
    private val eventProcessor: EventProcessor = DefaultEventProcessor(),
    private val clock: Clock = object : Clock { override fun currentTimeMillis(): Long = System.currentTimeMillis() },
    private val logger: IronLogger? = null,
    private val leaseDurationMs: Long = DEFAULT_LEASE_DURATION_MS,
    private val batchSize: Int = DEFAULT_BATCH_SIZE,
    private val maxDrainIterations: Int = DEFAULT_MAX_DRAIN_ITERATIONS,
    private val database: IronMindDatabase? = null,
    private val ironMindDao: IronMindDao? = null,
    private val userSessionProvider: UserSessionProvider
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_SESSION_GENERATION = UserSessionProvider.KEY_SESSION_GENERATION
        const val DEFAULT_LEASE_DURATION_MS: Long = 30_000L
        const val DEFAULT_BATCH_SIZE: Int = 10
        const val DEFAULT_MAX_DRAIN_ITERATIONS: Int = 5
        const val UNIQUE_WORK_NAME = "EventProcessingWorker"
        const val RECONCILIATION_WORK_NAME = "EventProcessingReconciliation"
    }

    override suspend fun doWork(): Result {
        val generation = inputData.getLong(KEY_SESSION_GENERATION, -1L)
        if (generation <= 0L) {
            println("IronMindLifecycle [EventProcessingWorker] [FAILED_CLOSED] Reason=missing_or_invalid_generation generation=$generation")
            return Result.failure()
        }

        return try {
            userSessionProvider.withUserSession(expectedGeneration = generation) { activeUserId ->
                doWorkForUser(activeUserId)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalStateException) {
            println("IronMindLifecycle [EventProcessingWorker] [REJECTED_STALE_GENERATION] error=${e.message}")
            Result.failure()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private suspend fun doWorkForUser(activeUserId: String): Result {
        logger?.logLifecycle("EventWorker", "STARTED", mapOf("runAttemptCount" to runAttemptCount.toString()))

        var totalProcessed = 0
        var totalClaimed = 0
        var iteration = 0

        try {
            // First, sweep orphaned expired leases with claimCount >= maxAttempts
            val now = clock.currentTimeMillis()
            learnerEventStatusDao.sweepClaimExhaustionForUser(activeUserId, now)

            while (iteration < maxDrainIterations && !isStopped) {
                iteration++
                val currentNow = clock.currentTimeMillis()
                val leaseExpiresAt = currentNow + leaseDurationMs

                // 1. Discover expired processing leases for reclamation
                val expiredIds = learnerEventStatusDao.getExpiredProcessingEventIdsForUser(activeUserId, currentNow, batchSize)

                // 2. Discover pending eligible events to fill remaining capacity
                val remainingCapacity = (batchSize - expiredIds.size).coerceAtLeast(0)
                val pendingIds = if (remainingCapacity > 0) {
                    learnerEventStatusDao.getPendingEligibleEventIdsForUser(activeUserId, currentNow, remainingCapacity)
                } else {
                    emptyList()
                }

                val candidateIds = (expiredIds + pendingIds).distinct()
                if (candidateIds.isEmpty()) {
                    break
                }

                for (eventId in candidateIds) {
                    if (isStopped) {
                        logger?.logLifecycle("EventWorker", "STOPPED", mapOf("totalProcessed" to totalProcessed.toString()))
                        return Result.retry()
                    }

                    val claimToken = UUID.randomUUID().toString()
                    val isExpired = expiredIds.contains(eventId)

                    // Atomic CAS claim
                    val claimResult = if (isExpired) {
                        learnerEventStatusDao.claimExpiredProcessingEventForUser(
                            eventId = eventId,
                            userId = activeUserId,
                            now = currentNow,
                            leaseExpiresAt = leaseExpiresAt,
                            newLeaseToken = claimToken
                        )
                    } else {
                        learnerEventStatusDao.claimPendingEventForUser(
                            eventId = eventId,
                            userId = activeUserId,
                            now = currentNow,
                            leaseExpiresAt = leaseExpiresAt,
                            newLeaseToken = claimToken
                        )
                    }

                    if (claimResult <= 0) {
                        logger?.logLifecycle("EventWorker", "CLAIM_SKIPPED", mapOf("eventId" to eventId))
                        continue
                    }

                    totalClaimed++
                    processClaimedEvent(eventId, claimToken, activeUserId)
                    totalProcessed++
                }
            }

            // Tail-check: If remaining eligible work exists because drain iterations were capped,
            // return Result.retry() so WorkManager schedules a follow-up run without stranding work.
            val tailNow = clock.currentTimeMillis()
            val hasRemainingPending = learnerEventStatusDao.getPendingEligibleEventIdsForUser(activeUserId, tailNow, 1).isNotEmpty()
            val hasRemainingExpired = learnerEventStatusDao.getExpiredProcessingEventIdsForUser(activeUserId, tailNow, 1).isNotEmpty()
            val hasRemainingWork = hasRemainingPending || hasRemainingExpired

            if (hasRemainingWork) {
                logger?.logLifecycle(
                    "EventWorker",
                    "DRAIN_CAPPED",
                    mapOf(
                        "totalProcessed" to totalProcessed.toString(),
                        "iterations" to iteration.toString(),
                        "hasRemainingWork" to "true"
                    )
                )
                return Result.retry()
            }

            logger?.logLifecycle(
                "EventWorker",
                "COMPLETED",
                mapOf(
                    "totalProcessed" to totalProcessed.toString(),
                    "totalClaimed" to totalClaimed.toString(),
                    "iterations" to iteration.toString()
                )
            )
            return Result.success()

        } catch (e: CancellationException) {
            logger?.logLifecycle("EventWorker", "CANCELLED", mapOf("totalProcessed" to totalProcessed.toString()))
            throw e
        } catch (e: Exception) {
            logger?.logLifecycle("EventWorker", "ERROR", mapOf("error" to (e.message ?: "UNKNOWN")))
            return Result.retry()
        }
    }

    private suspend fun processClaimedEvent(eventId: String, claimToken: String, activeUserId: String) {
        val now = clock.currentTimeMillis()
        try {
            val eventResult = eventRepository.getEvent(eventId)
            val event = when (eventResult) {
                is DomainResult.Success -> eventResult.data
                is DomainResult.Failure -> null
            }

            if (event != null && event.userId != activeUserId) {
                throw SessionOwnershipMismatchException("Queued event $eventId does not belong to the active session.")
            }

            if (event == null) {
                if (database != null) {
                    database.withTransaction {
                        val status = learnerEventStatusDao.getEventStatusForUser(eventId, activeUserId)
                        if (status != null && status.leaseToken == claimToken && status.status == "PROCESSING") {
                            learnerEventStatusDao.failPermanentForUser(eventId, activeUserId, claimToken, "SOURCE_EVENT_NOT_FOUND", now)
                        } else {
                            logger?.logLifecycle("EventWorker", "LEASE_LOST_MISSING_EVENT", mapOf("eventId" to eventId))
                        }
                    }
                } else {
                    val rows = learnerEventStatusDao.failPermanentForUser(eventId, activeUserId, claimToken, "SOURCE_EVENT_NOT_FOUND", now)
                    if (rows == 0) {
                        logger?.logLifecycle("EventWorker", "LEASE_LOST_MISSING_EVENT", mapOf("eventId" to eventId))
                    }
                }
                return
            }

            val outcome = eventProcessor.processEvent(event)
            handleOutcome(eventId, claimToken, outcome, activeUserId)

        } catch (e: CancellationException) {
            val cancelNow = clock.currentTimeMillis()
            learnerEventStatusDao.releaseLeaseForUser(eventId, activeUserId, claimToken, cancelNow)
            logger?.logLifecycle("EventWorker", "LEASE_RELEASED_ON_CANCELLATION", mapOf("eventId" to eventId))
            throw e
        } catch (e: SessionOwnershipMismatchException) {
            throw e
        } catch (e: Exception) {
            val failNow = clock.currentTimeMillis()
            handleRetryableFailure(eventId, activeUserId, claimToken, e.message ?: "UNEXPECTED_FAILURE", failNow)
        }
    }

    private suspend fun handleOutcome(eventId: String, claimToken: String, outcome: EventProcessingOutcome, userId: String) {
        val now = clock.currentTimeMillis()
        if (database != null) {
            database.withTransaction {
                // 1. Validate lease token and PROCESSING status under transaction lock
                val status = learnerEventStatusDao.getEventStatusForUser(eventId, userId)
                if (status == null || status.leaseToken != claimToken || status.status != "PROCESSING") {
                    logger?.logLifecycle("EventWorker", "LEASE_LOST_TRANSACTION_ABORTED", mapOf("eventId" to eventId))
                    return@withTransaction
                }
                // 2. Validate account existence
                val dao = ironMindDao ?: database.ironMindDao()
                if (dao.getUserProfile(userId) == null) {
                    learnerEventStatusDao.deleteAllForUser(userId)
                    logger?.logLifecycle("EventWorker", "ACCOUNT_ERASED_EVENT_TERMINATED", mapOf("eventId" to eventId, "userId" to userId))
                    return@withTransaction
                }
                // 3. Complete status atomically
                applyOutcomeStatus(eventId, userId, claimToken, outcome, now)
            }
        } else {
            val status = learnerEventStatusDao.getEventStatusForUser(eventId, userId)
            if (status != null && (status.leaseToken != claimToken || status.status != "PROCESSING")) {
                logger?.logLifecycle("EventWorker", "LEASE_LOST_EXECUTION_SKIPPED", mapOf("eventId" to eventId))
                return
            }
            if (ironMindDao != null && ironMindDao.getUserProfile(userId) == null) {
                learnerEventStatusDao.deleteAllForUser(userId)
                return
            }
            applyOutcomeStatus(eventId, userId, claimToken, outcome, now)
        }
    }

    private suspend fun applyOutcomeStatus(eventId: String, userId: String, claimToken: String, outcome: EventProcessingOutcome, now: Long) {
        when (outcome) {
            is EventProcessingOutcome.Success -> {
                val rows = learnerEventStatusDao.completeEventForUser(eventId, userId, claimToken, now)
                if (rows == 0) {
                    logger?.logLifecycle("EventWorker", "STALE_TOKEN_COMPLETE", mapOf("eventId" to eventId))
                } else {
                    logger?.logLifecycle("EventWorker", "EVENT_SUCCEEDED", mapOf("eventId" to eventId))
                }
            }

            is EventProcessingOutcome.SuccessNoChange -> {
                val rows = learnerEventStatusDao.completeEventForUser(eventId, userId, claimToken, now)
                if (rows == 0) {
                    logger?.logLifecycle("EventWorker", "STALE_TOKEN_COMPLETE", mapOf("eventId" to eventId, "reason" to outcome.reason))
                } else {
                    logger?.logLifecycle("EventWorker", "EVENT_SUCCEEDED_NO_CHANGE", mapOf("eventId" to eventId, "reason" to outcome.reason))
                }
            }

            is EventProcessingOutcome.RetryableFailure -> {
                handleRetryableFailure(eventId, userId, claimToken, outcome.message, now)
            }

            is EventProcessingOutcome.PermanentFailure -> {
                val rows = learnerEventStatusDao.failPermanentForUser(eventId, userId, claimToken, outcome.message, now)
                if (rows == 0) {
                    logger?.logLifecycle("EventWorker", "STALE_TOKEN_PERM_FAIL", mapOf("eventId" to eventId))
                } else {
                    logger?.logLifecycle("EventWorker", "EVENT_FAILED_PERMANENT", mapOf("eventId" to eventId))
                }
            }

            is EventProcessingOutcome.UnsupportedEventType -> {
                val message = "UNSUPPORTED_EVENT_TYPE: ${outcome.eventType.name}"
                val rows = learnerEventStatusDao.failPermanentForUser(eventId, userId, claimToken, message, now)
                if (rows == 0) {
                    logger?.logLifecycle("EventWorker", "STALE_TOKEN_UNSUPPORTED", mapOf("eventId" to eventId))
                } else {
                    logger?.logLifecycle("EventWorker", "EVENT_UNSUPPORTED", mapOf("eventId" to eventId))
                }
            }
        }
    }

    private suspend fun handleRetryableFailure(eventId: String, userId: String, claimToken: String, message: String, now: Long) {
        val currentStatus = learnerEventStatusDao.getEventStatusForUser(eventId, userId)
        val nextFailureCount = (currentStatus?.failureCount ?: 0) + 1
        val maxAttempts = currentStatus?.maxAttempts ?: 5

        if (nextFailureCount >= maxAttempts) {
            val permMessage = "MAX_ATTEMPTS_EXHAUSTED: $message"
            val rows = learnerEventStatusDao.failPermanentForUser(eventId, userId, claimToken, permMessage, now)
            if (rows == 0) {
                logger?.logLifecycle("EventWorker", "STALE_TOKEN_EXHAUSTED", mapOf("eventId" to eventId))
            }
        } else {
            val nextAttemptAt = EventBackoffPolicy.calculateNextAttemptAt(nextFailureCount, now)
            val rows = learnerEventStatusDao.failRetryableForUser(eventId, userId, claimToken, nextAttemptAt, message, now)
            if (rows == 0) {
                logger?.logLifecycle("EventWorker", "STALE_TOKEN_RETRY", mapOf("eventId" to eventId))
            } else {
                logger?.logLifecycle("EventWorker", "EVENT_RETRY_SCHEDULED", mapOf("eventId" to eventId, "nextAttemptAt" to nextAttemptAt.toString()))
            }
        }
    }
}

private class SessionOwnershipMismatchException(message: String) : IllegalStateException(message)
