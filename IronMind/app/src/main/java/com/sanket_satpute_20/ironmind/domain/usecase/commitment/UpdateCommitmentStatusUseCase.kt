package com.sanket_satpute_20.ironmind.domain.usecase.commitment

import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Commitment
import com.sanket_satpute_20.ironmind.domain.model.CommitmentStatus
import com.sanket_satpute_20.ironmind.domain.model.Outcome
import com.sanket_satpute_20.ironmind.domain.model.ResultStatus
import com.sanket_satpute_20.ironmind.domain.provider.ReminderScheduler
import com.sanket_satpute_20.ironmind.domain.repository.CommitmentRepository
import com.sanket_satpute_20.ironmind.domain.repository.OutcomeRepository
import com.sanket_satpute_20.ironmind.domain.repository.EventRepository
import com.sanket_satpute_20.ironmind.domain.model.Event
import com.sanket_satpute_20.ironmind.domain.model.EventType
import com.sanket_satpute_20.ironmind.domain.model.EntitySource

class UpdateCommitmentStatusUseCase(
    private val repository: CommitmentRepository,
    private val outcomeRepository: OutcomeRepository,
    private val reminderScheduler: ReminderScheduler,
    private val clock: Clock,
    private val idGenerator: IdGenerator,
    private val eventRepository: EventRepository
) {
    suspend operator fun invoke(
        commitmentId: String,
        newStatus: CommitmentStatus,
        resultStatus: ResultStatus? = null,
        actualDurationMinutes: Int? = null
    ): Result<Commitment, Exception> {
        val existingResult = repository.getCommitment(commitmentId)
        if (existingResult is Result.Failure) return Result.Failure(existingResult.error)

        val commitment = (existingResult as Result.Success).data 
            ?: return Result.Failure(IllegalArgumentException("Commitment not found"))

        val currentStatus = commitment.status

        if (currentStatus == newStatus) {
            return Result.Success(commitment)
        }

        // Validate transition
        val isValid = when (currentStatus) {
            CommitmentStatus.PLANNED -> newStatus == CommitmentStatus.COMMITTED
            CommitmentStatus.COMMITTED -> newStatus in listOf(
                CommitmentStatus.STARTED, CommitmentStatus.POSTPONED, CommitmentStatus.MISSED
            )
            CommitmentStatus.STARTED -> newStatus in listOf(
                CommitmentStatus.COMPLETED, CommitmentStatus.POSTPONED, CommitmentStatus.MISSED
            )
            CommitmentStatus.POSTPONED -> newStatus == CommitmentStatus.COMMITTED
            CommitmentStatus.MISSED -> newStatus in listOf(
                CommitmentStatus.RECOVERED, CommitmentStatus.ABANDONED
            )
            CommitmentStatus.COMPLETED,
            CommitmentStatus.RECOVERED,
            CommitmentStatus.ABANDONED -> false // Terminal states in this specific graph
        }

        if (!isValid) {
            return Result.Failure(
                IllegalStateException("Invalid state transition from $currentStatus to $newStatus")
            )
        }

        val now = clock.currentTimeMillis()
        
        var committedAt = commitment.committedAt
        var startedAt = commitment.startedAt
        var completedAt = commitment.completedAt
        var postponedAt = commitment.postponedAt
        var missedAt = commitment.missedAt
        var recoveredAt = commitment.recoveredAt
        
        when (newStatus) {
            CommitmentStatus.COMMITTED -> {
                committedAt = now
                if (currentStatus == CommitmentStatus.POSTPONED) {
                    startedAt = null
                    postponedAt = null
                }
            }
            CommitmentStatus.STARTED -> {
                startedAt = now
            }
            CommitmentStatus.COMPLETED -> {
                completedAt = now
            }
            CommitmentStatus.POSTPONED -> {
                postponedAt = now
            }
            CommitmentStatus.MISSED -> {
                missedAt = now
            }
            CommitmentStatus.RECOVERED -> {
                recoveredAt = now
            }
            CommitmentStatus.ABANDONED -> {
                // No specific timestamp for abandoned, rely on updatedAt and status
            }
            CommitmentStatus.PLANNED -> {
                // Not possible to transition into PLANNED based on valid graph
            }
        }

        val updatedCommitment = commitment.copy(
            status = newStatus,
            committedAt = committedAt,
            startedAt = startedAt,
            completedAt = completedAt,
            postponedAt = postponedAt,
            missedAt = missedAt,
            recoveredAt = recoveredAt,
            updatedAt = now
        )

        return when (val saveResult = repository.saveCommitment(updatedCommitment)) {
            is Result.Failure -> Result.Failure(saveResult.error)
            is Result.Success -> {
                // If a terminal/meaningful state transition is provided with a result, record the outcome
                if (resultStatus != null) {
                    val outcome = Outcome(
                        id = idGenerator.generateId(),
                        userId = commitment.userId,
                        sourceEntityId = commitment.id,
                        sourceEntityType = "COMMITMENT",
                        resultStatus = resultStatus,
                        actualDurationMinutes = actualDurationMinutes,
                        completedAt = now,
                        createdAt = now
                    )
                    outcomeRepository.saveOutcome(outcome)
                }

                // Cancel reminder if transitioning to a state where it is no longer needed
                if (newStatus in listOf(
                        CommitmentStatus.COMPLETED, 
                        CommitmentStatus.MISSED, 
                        CommitmentStatus.POSTPONED, 
                        CommitmentStatus.ABANDONED,
                        CommitmentStatus.STARTED
                    )) {
                    reminderScheduler.cancelReminder(commitment.id)
                }
                
                val eventType = when (newStatus) {
                    CommitmentStatus.COMMITTED -> EventType.COMMITMENT_COMMITTED
                    CommitmentStatus.STARTED -> EventType.COMMITMENT_STARTED
                    CommitmentStatus.COMPLETED -> EventType.COMMITMENT_COMPLETED
                    CommitmentStatus.POSTPONED -> EventType.COMMITMENT_POSTPONED
                    CommitmentStatus.MISSED -> EventType.COMMITMENT_MISSED
                    CommitmentStatus.RECOVERED -> EventType.COMMITMENT_RECOVERED
                    CommitmentStatus.ABANDONED -> EventType.COMMITMENT_ABANDONED
                    else -> EventType.COMMITMENT_POSTPONED // Fallback, shouldn't happen based on graph
                }

                val transitionEvent = Event(
                    id = idGenerator.generateId(),
                    userId = commitment.userId,
                    type = eventType,
                    entityType = "COMMITMENT",
                    entityId = commitment.id,
                    occurredAt = now,
                    recordedAt = now,
                    source = EntitySource.USER,
                    previousState = currentStatus.name,
                    newState = newStatus.name
                )
                eventRepository.saveEvent(transitionEvent)
                println("IronMindLifecycle [Commitment] [${newStatus.name}] commitmentId=${commitment.id}")

                if (resultStatus != null) {
                    val outcomeEvent = Event(
                        id = idGenerator.generateId(),
                        userId = commitment.userId,
                        type = EventType.OUTCOME_RECORDED,
                        entityType = "COMMITMENT",
                        entityId = commitment.id,
                        occurredAt = now,
                        recordedAt = now,
                        source = EntitySource.USER,
                        metadata = "resultStatus=${resultStatus.name}"
                    )
                    eventRepository.saveEvent(outcomeEvent)
                    println("IronMindLifecycle [Outcome] [RECORDED] commitmentId=${commitment.id}")
                }

                Result.Success(updatedCommitment)
            }
        }
    }
}
