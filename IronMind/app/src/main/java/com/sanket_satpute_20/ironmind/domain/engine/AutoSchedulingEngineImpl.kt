package com.sanket_satpute_20.ironmind.domain.engine

import com.sanket_satpute_20.ironmind.domain.logging.IronLogger
import com.sanket_satpute_20.ironmind.domain.model.AutonomyCapability
import com.sanket_satpute_20.ironmind.domain.model.Commitment
import com.sanket_satpute_20.ironmind.domain.model.CommitmentStatus
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.decision.CandidateAction
import com.sanket_satpute_20.ironmind.domain.model.decision.DecisionResult
import com.sanket_satpute_20.ironmind.domain.model.decision.EvaluationContext
import com.sanket_satpute_20.ironmind.domain.model.scheduling.ScheduleCandidate
import com.sanket_satpute_20.ironmind.domain.model.scheduling.ScheduleResult
import com.sanket_satpute_20.ironmind.domain.repository.CommitmentRepository
import java.util.UUID

class AutoSchedulingEngineImpl(
    private val decisionEngine: DecisionEngine,
    private val commitmentRepository: CommitmentRepository,
    private val logger: IronLogger,
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) : AutoSchedulingEngine {

    override suspend fun schedule(userId: String, candidate: ScheduleCandidate): ScheduleResult {
        val candidateAction = CandidateAction(
            capability = AutonomyCapability.SCHEDULING,
            isSafe = true,
            isReversible = true
        )

        val context = EvaluationContext(
            hasRequiredPermissions = true,
            isUserOverrideActive = false,
            isCooldownActive = false,
            isDuplicate = false
        )

        val decision = decisionEngine.evaluate(userId, candidateAction, context)

        when (decision) {
            DecisionResult.EXECUTE -> {
                // Revalidate current state: Check for overlapping commitments
                val overlapsResult = commitmentRepository.getCommitmentsForDateRange(
                    userId = userId,
                    startTime = candidate.proposedStartTime,
                    endTime = candidate.proposedEndTime
                )

                if (overlapsResult.isFailure) {
                    logger.logLifecycle("Scheduling", "REJECTED_ERROR", mapOf("reason" to "could_not_check_overlaps"))
                    return ScheduleResult.Conflict("Failed to revalidate current state.")
                }

                val overlaps = overlapsResult.getOrNull() ?: emptyList()
                val activeOverlaps = overlaps.filter { 
                    it.status == CommitmentStatus.COMMITTED || it.status == CommitmentStatus.STARTED 
                }

                if (activeOverlaps.isNotEmpty()) {
                    logger.logLifecycle("Scheduling", "REJECTED_CONFLICT", mapOf("reason" to "overlapping_commitments", "overlapCount" to activeOverlaps.size.toString()))
                    return ScheduleResult.Conflict("Time slot is no longer available.")
                }

                // Passed all checks, create the commitment
                val commitment = Commitment(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    goalId = candidate.goalId,
                    planId = candidate.planId,
                    taskId = candidate.taskId,
                    parentCommitmentId = candidate.parentCommitmentId,
                    title = candidate.title,
                    description = candidate.description,
                    committedAt = timeProvider(),
                    scheduledStartAt = candidate.proposedStartTime,
                    scheduledEndAt = candidate.proposedEndTime,
                    status = CommitmentStatus.COMMITTED,
                    priority = candidate.priority,
                    source = EntitySource.AI,
                    createdAt = timeProvider(),
                    updatedAt = timeProvider()
                )

                val saveResult = commitmentRepository.saveCommitment(commitment)
                return if (saveResult.isSuccess) {
                    logger.logLifecycle("Scheduling", "AUTO_SCHEDULED", mapOf("commitmentId" to commitment.id))
                    ScheduleResult.Scheduled(commitment)
                } else {
                    logger.logLifecycle("Scheduling", "REJECTED_ERROR", mapOf("reason" to "failed_to_save"))
                    ScheduleResult.Conflict("Failed to save commitment.")
                }
            }
            DecisionResult.ASK_USER -> {
                logger.logLifecycle("Scheduling", "REQUIRES_PERMISSION", mapOf("title" to candidate.title))
                return ScheduleResult.RequiresPermission
            }
            DecisionResult.SUGGEST, DecisionResult.STAY_SILENT -> {
                logger.logLifecycle("Scheduling", "NOT_ALLOWED", mapOf("decision" to decision.name))
                return ScheduleResult.NotAllowed
            }
        }
    }
}
