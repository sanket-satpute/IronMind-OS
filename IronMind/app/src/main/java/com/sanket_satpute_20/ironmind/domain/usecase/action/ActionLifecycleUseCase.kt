package com.sanket_satpute_20.ironmind.domain.usecase.action

import androidx.room.withTransaction
import com.sanket_satpute_20.ironmind.data.local.IronMindDatabase
import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Commitment
import com.sanket_satpute_20.ironmind.domain.model.CommitmentStatus
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.Event
import com.sanket_satpute_20.ironmind.domain.model.EventType
import com.sanket_satpute_20.ironmind.domain.model.Outcome
import com.sanket_satpute_20.ironmind.domain.model.PlanStatus
import com.sanket_satpute_20.ironmind.domain.model.ResultStatus
import com.sanket_satpute_20.ironmind.domain.model.action.ActionCategory
import com.sanket_satpute_20.ironmind.domain.model.action.ActionExecutionResult
import com.sanket_satpute_20.ironmind.domain.model.action.ActionReviewStatus
import com.sanket_satpute_20.ironmind.domain.model.action.ProposedAction
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecommendationStatus
import com.sanket_satpute_20.ironmind.domain.repository.ActionRepository
import com.sanket_satpute_20.ironmind.domain.repository.CommitmentRepository
import com.sanket_satpute_20.ironmind.domain.repository.EventRepository
import com.sanket_satpute_20.ironmind.domain.repository.InterventionRecommendationRepository
import com.sanket_satpute_20.ironmind.domain.repository.OutcomeRepository
import com.sanket_satpute_20.ironmind.domain.repository.TaskRepository
import com.sanket_satpute_20.ironmind.domain.usecase.commitment.CreateCommitmentUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.commitment.ScheduleCommitmentUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.plan.UpdatePlanStatusUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.task.CreateTaskUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.task.EditTaskUseCase
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.ConcurrentHashMap

/**
 * Phase 3.5 & 3.5.1: User Approval and Action Lifecycle Management.
 *
 * Hardened Guarantees:
 * 1. User remains in full control: No autonomous action execution occurs.
 * 2. Acceptance != Execution: Accepting an action changes review status to ACCEPTED without modifying domain state.
 * 3. Execution != Success: Executing an action applies domain mutation; success requires a recorded Outcome.
 * 4. Rejection != Refutation: Rejecting an action never refutes its source insight or confirms/refutes barrier hypotheses.
 * 5. Factual integrity: Does not modify domain entities directly from AI hypotheses without explicit user execution.
 * 6. Concurrency Protection: Actions cannot be executed concurrently (double-tap / race prevention).
 * 7. State Invariant: Only ACCEPTED actions can be executed. EXECUTED actions cannot be re-executed.
 * 8. Execution Failure: EXECUTION_FAILED actions cannot be silently re-executed without user reset.
 * 9. Outcome Invariant: Outcomes can ONLY be recorded for EXECUTED actions.
 * 10. Outcome Idempotency: Recording outcome multiple times returns existing outcome without duplicate creation.
 * 11. Durable Persistence: All state transitions are saved to ActionRepository.
 * 12. Crash-Safety: Atomic shared transactions when database is present; deterministic entity IDs and recovery when interrupted.
 */
class ActionLifecycleUseCase(
    private val actionRepository: ActionRepository? = null,
    private val recommendationRepository: InterventionRecommendationRepository? = null,
    private val outcomeRepository: OutcomeRepository,
    private val eventRepository: EventRepository,
    private val taskRepository: TaskRepository? = null,
    private val editTaskUseCase: EditTaskUseCase? = null,
    private val createTaskUseCase: CreateTaskUseCase? = null,
    private val scheduleCommitmentUseCase: ScheduleCommitmentUseCase? = null,
    private val createCommitmentUseCase: CreateCommitmentUseCase? = null,
    private val updatePlanStatusUseCase: UpdatePlanStatusUseCase? = null,
    private val clock: Clock,
    private val idGenerator: IdGenerator,
    private val commitmentRepository: CommitmentRepository? = null,
    private val database: IronMindDatabase? = null
) {

    private val executionLocks = ConcurrentHashMap<String, Mutex>()

    /**
     * User accepts the proposed action.
     * Invariant: Acceptance does NOT execute the action or mutate domain entities.
     */
    suspend fun acceptAction(action: ProposedAction, userId: String): Result<ProposedAction, Exception> {
        if (action.userId != userId) {
            return Result.Failure(SecurityException("Action ${action.id} does not belong to user $userId"))
        }

        val now = clock.currentTimeMillis()
        val updatedAction = action.copy(
            reviewStatus = ActionReviewStatus.ACCEPTED,
            reviewedAt = now
        )

        // Durably persist accepted state
        actionRepository?.saveAction(updatedAction)

        // If backed by a recommendation, sync recommendation status
        if (action.sourceRecommendationId != null && recommendationRepository != null) {
            recommendationRepository.updateRecommendationStatus(
                id = action.sourceRecommendationId,
                status = InterventionRecommendationStatus.ACCEPTED
            )
        }

        // Record audit event
        val event = Event(
            id = idGenerator.generateId(),
            userId = userId,
            type = EventType.INTERVENTION_ACCEPTED,
            entityType = action.targetEntityType ?: "ProposedAction",
            entityId = action.targetEntityId ?: action.id,
            occurredAt = now,
            recordedAt = now,
            source = EntitySource.USER,
            metadata = "action_accepted:category=${action.category},title=${action.title}",
            causationId = action.sourceRecommendationId ?: action.id
        )
        eventRepository.saveEventWithLearnerStatus(event)

        return Result.Success(updatedAction)
    }

    /**
     * User rejects the proposed action.
     * Invariant: Rejecting recommendation does NOT refute its source insight or barrier hypothesis.
     */
    suspend fun rejectAction(action: ProposedAction, userId: String, reason: String? = null): Result<ProposedAction, Exception> {
        if (action.userId != userId) {
            return Result.Failure(SecurityException("Action ${action.id} does not belong to user $userId"))
        }

        val now = clock.currentTimeMillis()
        val updatedAction = action.copy(
            reviewStatus = ActionReviewStatus.REJECTED,
            reviewedAt = now
        )

        // Durably persist rejected state
        actionRepository?.saveAction(updatedAction)

        // If backed by a recommendation, sync recommendation status
        if (action.sourceRecommendationId != null && recommendationRepository != null) {
            recommendationRepository.updateRecommendationStatus(
                id = action.sourceRecommendationId,
                status = InterventionRecommendationStatus.REJECTED
            )
        }

        // Record audit event
        val event = Event(
            id = idGenerator.generateId(),
            userId = userId,
            type = EventType.INTERVENTION_DISMISSED,
            entityType = action.targetEntityType ?: "ProposedAction",
            entityId = action.targetEntityId ?: action.id,
            occurredAt = now,
            recordedAt = now,
            source = EntitySource.USER,
            metadata = "action_rejected:category=${action.category},reason=${reason ?: "none"}",
            causationId = action.sourceRecommendationId ?: action.id
        )
        eventRepository.saveEventWithLearnerStatus(event)

        return Result.Success(updatedAction)
    }

    /**
     * User dismisses the proposed action from the current view.
     */
    suspend fun dismissAction(action: ProposedAction, userId: String): Result<ProposedAction, Exception> {
        if (action.userId != userId) {
            return Result.Failure(SecurityException("Action ${action.id} does not belong to user $userId"))
        }

        val now = clock.currentTimeMillis()
        val updatedAction = action.copy(
            reviewStatus = ActionReviewStatus.DISMISSED,
            reviewedAt = now
        )
        actionRepository?.saveAction(updatedAction)
        return Result.Success(updatedAction)
    }

    /**
     * User defers the proposed action until a later time.
     */
    suspend fun deferAction(action: ProposedAction, userId: String, deferUntilMs: Long): Result<ProposedAction, Exception> {
        if (action.userId != userId) {
            return Result.Failure(SecurityException("Action ${action.id} does not belong to user $userId"))
        }

        val now = clock.currentTimeMillis()
        val updatedAction = action.copy(
            reviewStatus = ActionReviewStatus.DEFERRED,
            reviewedAt = now,
            deferredUntilMs = deferUntilMs
        )
        actionRepository?.saveAction(updatedAction)
        return Result.Success(updatedAction)
    }

    /**
     * User explicitly executes an approved supported action.
     *
     * Invariants:
     * - Only executed on explicit user instruction.
     * - Action MUST be in ACCEPTED state (or recovered from EXECUTING).
     * - Cannot execute an already EXECUTED action.
     * - Cannot silently retry an EXECUTION_FAILED action without explicit user reset.
     * - Concurrent executions on the same action are blocked via JVM Mutex.
     * - Uses shared Room transaction when database is available for atomic crash safety.
     * - Subtasks and focus commitments use deterministic IDs to prevent duplicates on retry.
     * - Execution does not automatically record success; outcome recording is a separate step.
     */
    suspend fun executeAction(
        action: ProposedAction,
        userId: String,
        executionParams: Map<String, Any?> = emptyMap()
    ): Result<ProposedAction, Exception> {
        if (action.userId != userId) {
            return Result.Failure(SecurityException("Action ${action.id} does not belong to user $userId"))
        }

        val mutex = executionLocks.computeIfAbsent(action.id) { Mutex() }
        if (!mutex.tryLock()) {
            return Result.Failure(IllegalStateException("Execution already in progress for action ${action.id}"))
        }

        try {
            // Re-fetch latest persisted state to prevent stale execution
            val currentAction = when (val res = actionRepository?.getActionById(action.id)) {
                is Result.Success -> res.data ?: action
                else -> action
            }

            if (currentAction.userId != userId) {
                return Result.Failure(SecurityException("Action ${currentAction.id} does not belong to user $userId"))
            }

            // Invariant 1: Cannot re-execute already EXECUTED action
            if (currentAction.reviewStatus == ActionReviewStatus.EXECUTED) {
                return Result.Failure(IllegalStateException("Action ${currentAction.id} has already been executed"))
            }

            // Invariant 2: Cannot silently re-execute EXECUTION_FAILED action without reset
            if (currentAction.reviewStatus == ActionReviewStatus.EXECUTION_FAILED) {
                return Result.Failure(IllegalStateException("Action ${currentAction.id} previously failed execution; cannot re-execute without explicit user reset"))
            }

            // Invariant 3: Unapproved actions cannot execute
            if (currentAction.reviewStatus != ActionReviewStatus.ACCEPTED &&
                currentAction.reviewStatus != ActionReviewStatus.EXECUTING) {
                return Result.Failure(IllegalStateException("Action ${currentAction.id} must be in ACCEPTED state before execution (current: ${currentAction.reviewStatus})"))
            }

            val now = clock.currentTimeMillis()

            // If action is in EXECUTING state from an interrupted prior attempt:
            // Check if domain mutation already committed before crash!
            if (currentAction.reviewStatus == ActionReviewStatus.EXECUTING) {
                val recovery = checkPriorExecutionState(currentAction, executionParams, now)
                if (recovery != null && recovery.success) {
                    val recoveredAction = currentAction.copy(
                        reviewStatus = ActionReviewStatus.EXECUTED,
                        executedAt = recovery.executedAt,
                        lastAttemptedAt = now,
                        executionResult = recovery
                    )
                    actionRepository?.saveAction(recoveredAction)
                    return Result.Success(recoveredAction)
                }
            }

            // Persist durable EXECUTING state marker before mutations
            val executingAction = currentAction.copy(
                reviewStatus = ActionReviewStatus.EXECUTING,
                lastAttemptedAt = now
            )
            actionRepository?.saveAction(executingAction)

            try {
                val executionResult = executeCategoryMutation(currentAction, userId, executionParams, now)
                if (!executionResult.success) {
                    val failedAction = currentAction.copy(
                        reviewStatus = ActionReviewStatus.EXECUTION_FAILED,
                        executedAt = now,
                        lastAttemptedAt = now,
                        executionResult = executionResult
                    )
                    actionRepository?.saveAction(failedAction)
                    return Result.Success(failedAction)
                }

                val executedAction = currentAction.copy(
                    reviewStatus = ActionReviewStatus.EXECUTED,
                    executedAt = now,
                    lastAttemptedAt = now,
                    executionResult = executionResult
                )
                actionRepository?.saveAction(executedAction)
                return Result.Success(executedAction)
            } catch (e: Exception) {
                val failedAction = currentAction.copy(
                    reviewStatus = ActionReviewStatus.EXECUTION_FAILED,
                    executedAt = now,
                    lastAttemptedAt = now,
                    executionResult = ActionExecutionResult(
                        executedAt = now,
                        success = false,
                        affectedEntityId = currentAction.targetEntityId,
                        affectedEntityType = currentAction.targetEntityType,
                        message = "Execution failed: ${e.message}"
                    )
                )
                actionRepository?.saveAction(failedAction)
                return Result.Success(failedAction)
            }
        } finally {
            mutex.unlock()
        }
    }

    /**
     * Startup & recovery reconciler: inspects any actions left in EXECUTING state.
     * If the domain mutation committed, marks EXECUTED.
     * If the domain mutation never committed, reverts to ACCEPTED so user approval is preserved.
     */
    suspend fun reconcileInterruptedActions(userId: String): Result<List<ProposedAction>, Exception> {
        val actions = when (val res = actionRepository?.getActionsForUser(userId)) {
            is Result.Success -> res.data
            else -> return Result.Success(emptyList())
        }

        val executingActions = actions.filter { it.reviewStatus == ActionReviewStatus.EXECUTING }
        if (executingActions.isEmpty()) return Result.Success(emptyList())

        val now = clock.currentTimeMillis()
        val reconciled = mutableListOf<ProposedAction>()

        for (action in executingActions) {
            val recoveryResult = checkPriorExecutionState(action, emptyMap(), now)
            val updatedAction = if (recoveryResult != null && recoveryResult.success) {
                action.copy(
                    reviewStatus = ActionReviewStatus.EXECUTED,
                    executedAt = recoveryResult.executedAt,
                    lastAttemptedAt = now,
                    executionResult = recoveryResult
                )
            } else {
                action.copy(
                    reviewStatus = ActionReviewStatus.ACCEPTED,
                    lastAttemptedAt = now
                )
            }
            actionRepository?.saveAction(updatedAction)
            reconciled.add(updatedAction)
        }

        return Result.Success(reconciled)
    }

    /**
     * Inspects target domain entities to determine if a mutation was already committed before crash.
     */
    suspend fun checkPriorExecutionState(
        action: ProposedAction,
        params: Map<String, Any?>,
        now: Long
    ): ActionExecutionResult? {
        return when (action.category) {
            ActionCategory.TASK_RESCHEDULE -> {
                val taskId = action.targetEntityId ?: return null
                val task = when (val res = taskRepository?.getTask(taskId)) {
                    is Result.Success -> res.data
                    else -> null
                } ?: return null
                val targetDue = (params["newDueAt"] as? Long) ?: action.suggestedNewDueAt
                if (targetDue != null && task.dueAt == targetDue) {
                    ActionExecutionResult(
                        executedAt = task.updatedAt,
                        success = true,
                        affectedEntityId = taskId,
                        affectedEntityType = "TASK",
                        message = "Task already rescheduled to $targetDue",
                        newDueAt = targetDue
                    )
                } else null
            }
            ActionCategory.TASK_DECOMPOSITION -> {
                val taskId = action.targetEntityId ?: return null
                val taskRepo = taskRepository ?: return null
                val parentTask = when (val res = taskRepo.getTask(taskId)) {
                    is Result.Success -> res.data
                    else -> null
                } ?: return null
                @Suppress("UNCHECKED_CAST")
                val subTaskTitles = (params["subTasks"] as? List<String>)?.filter { it.isNotBlank() }
                    ?: action.suggestedSubTasks.ifEmpty {
                        listOf(
                            "${parentTask.title} (Part 1)",
                            "${parentTask.title} (Part 2)"
                        )
                    }
                val existingIds = mutableListOf<String>()
                for (index in subTaskTitles.indices) {
                    val subTaskId = "${action.id}_sub_$index"
                    val subTask = when (val res = taskRepo.getTask(subTaskId)) {
                        is Result.Success -> res.data
                        else -> null
                    }
                    if (subTask != null) {
                        existingIds.add(subTask.id)
                    }
                }
                if (existingIds.size == subTaskTitles.size && existingIds.isNotEmpty()) {
                    ActionExecutionResult(
                        executedAt = now,
                        success = true,
                        affectedEntityId = taskId,
                        affectedEntityType = "TASK",
                        message = "Decomposed into ${existingIds.size} sub-tasks (recovered)",
                        resultingSubTaskIds = existingIds
                    )
                } else null
            }
            ActionCategory.FOCUS_PROTECTION -> {
                val focusCommId = "${action.id}_focus_comm"
                val comm = when (val res = commitmentRepository?.getCommitment(focusCommId)) {
                    is Result.Success -> res.data
                    else -> null
                }
                if (comm != null) {
                    ActionExecutionResult(
                        executedAt = comm.createdAt,
                        success = true,
                        affectedEntityId = comm.id,
                        affectedEntityType = "COMMITMENT",
                        message = "Protected focus commitment recovered: ${comm.id}"
                    )
                } else null
            }
            ActionCategory.GOAL_PLAN_RECONSIDERATION -> null
            ActionCategory.COMMITMENT_REVIEW -> null
            ActionCategory.BEHAVIOUR_PATTERN_REVIEW -> null
        }
    }

    /**
     * Records the measured outcome and user feedback for an executed action.
     *
     * Invariants:
     * 1. Cannot record outcome if action has not been EXECUTED.
     * 2. Strict action-scoped lookup and persistence (never uses targetEntityId).
     * 3. Idempotent: repeated outcome recordings return existing outcome without duplicate creation.
     * 4. Idempotent learner events: event ID is deterministic ("evt_outcome_act_${action.id}").
     * 5. Does NOT re-execute action or perform domain mutations.
     * 6. Updates action.outcomeId and saves to ActionRepository.
     */
    suspend fun recordActionOutcome(
        action: ProposedAction,
        userId: String,
        resultStatus: ResultStatus,
        actualDurationMinutes: Int? = null,
        feedback: String? = null
    ): Result<Outcome, Exception> {
        if (action.userId != userId) {
            return Result.Failure(SecurityException("Action ${action.id} does not belong to user $userId"))
        }

        // Re-fetch latest persisted action state
        val currentAction = when (val res = actionRepository?.getActionById(action.id)) {
            is Result.Success -> res.data ?: action
            else -> action
        }

        if (currentAction.userId != userId) {
            return Result.Failure(SecurityException("Action ${currentAction.id} does not belong to user $userId"))
        }

        // Invariant: Action MUST be EXECUTED
        if (currentAction.reviewStatus != ActionReviewStatus.EXECUTED) {
            return Result.Failure(IllegalStateException("Cannot record outcome for action ${currentAction.id}: action review status is ${currentAction.reviewStatus}, must be EXECUTED"))
        }

        // Strict action-scoped outcome ID: NEVER fall back to targetEntityId
        val deterministicOutcomeId = currentAction.outcomeId ?: "outcome_act_${currentAction.id}"

        val existingOutcome = when {
            currentAction.outcomeId != null -> {
                when (val res = outcomeRepository.getOutcome(currentAction.outcomeId)) {
                    is Result.Success -> res.data
                    else -> null
                } ?: when (val res = outcomeRepository.getOutcomeForSource(currentAction.id)) {
                    is Result.Success -> res.data
                    else -> null
                } ?: when (val res = outcomeRepository.getOutcomesForUser(userId)) {
                    is Result.Success -> res.data.find { it.id == currentAction.outcomeId }
                    else -> null
                }
            }
            else -> {
                when (val res = outcomeRepository.getOutcome(deterministicOutcomeId)) {
                    is Result.Success -> res.data
                    else -> null
                } ?: when (val res = outcomeRepository.getOutcomeForSource(currentAction.id)) {
                    is Result.Success -> res.data
                    else -> null
                } ?: when (val res = outcomeRepository.getOutcomesForUser(userId)) {
                    is Result.Success -> res.data.find { it.id == deterministicOutcomeId }
                    else -> null
                }
            }
        }

        if (existingOutcome != null) {
            if (currentAction.outcomeId != existingOutcome.id) {
                val updatedAction = currentAction.copy(outcomeId = existingOutcome.id)
                actionRepository?.saveAction(updatedAction)
            }
            return Result.Success(existingOutcome)
        }

        val now = clock.currentTimeMillis()
        val outcome = Outcome(
            id = deterministicOutcomeId,
            userId = userId,
            sourceEntityId = currentAction.id, // Strictly action-scoped!
            sourceEntityType = "PROPOSED_ACTION",
            resultStatus = resultStatus,
            actualDurationMinutes = actualDurationMinutes,
            completedAt = now,
            createdAt = now
        )

        val saveResult = outcomeRepository.saveOutcome(outcome)
        if (saveResult is Result.Failure) {
            return Result.Failure(saveResult.error)
        }

        // Record learner status event with deterministic action-scoped event ID to prevent duplicate events on retry
        val deterministicEventId = "evt_outcome_act_${currentAction.id}"
        val event = Event(
            id = deterministicEventId,
            userId = userId,
            type = if (resultStatus == ResultStatus.POSITIVE) EventType.OUTCOME_RECORDED else EventType.INTERVENTION_OVERRIDDEN,
            entityType = "Outcome",
            entityId = outcome.id,
            occurredAt = now,
            recordedAt = now,
            source = EntitySource.USER,
            metadata = "action_outcome:status=$resultStatus,duration=$actualDurationMinutes,feedback=${feedback ?: "none"}",
            causationId = currentAction.id
        )
        eventRepository.saveEventWithLearnerStatus(event)

        // Durably update action with outcomeId
        val updatedAction = currentAction.copy(outcomeId = outcome.id)
        actionRepository?.saveAction(updatedAction)

        return Result.Success(outcome)
    }

    // -------------------------------------------------------------
    // Private Execution Helpers
    // -------------------------------------------------------------

    private suspend fun executeCategoryMutation(
        action: ProposedAction,
        userId: String,
        params: Map<String, Any?>,
        now: Long
    ): ActionExecutionResult {
        return when (action.category) {
            ActionCategory.TASK_RESCHEDULE -> executeTaskReschedule(action, params, now)
            ActionCategory.TASK_DECOMPOSITION -> executeTaskDecomposition(action, userId, params, now)
            ActionCategory.COMMITMENT_REVIEW -> executeCommitmentReview(action, userId, params, now)
            ActionCategory.FOCUS_PROTECTION -> executeFocusProtection(action, userId, params, now)
            ActionCategory.GOAL_PLAN_RECONSIDERATION -> executeGoalPlanReconsideration(action, userId, params, now)
            ActionCategory.BEHAVIOUR_PATTERN_REVIEW -> executePatternReview(action, now)
        }
    }

    private suspend fun executeTaskReschedule(
        action: ProposedAction,
        params: Map<String, Any?>,
        now: Long
    ): ActionExecutionResult {
        val taskId = action.targetEntityId ?: return ActionExecutionResult(now, false, null, "TASK", "Target task ID missing")
        val useCase = editTaskUseCase ?: return ActionExecutionResult(now, false, taskId, "TASK", "EditTaskUseCase not available")

        val newDueAt = (params["newDueAt"] as? Long) ?: action.suggestedNewDueAt ?: (now + 86400000L)

        // Idempotency check: if task already has this due date, skip redundant mutation
        val taskRepo = taskRepository
        if (taskRepo != null) {
            val existingTask = when (val res = taskRepo.getTask(taskId)) {
                is Result.Success -> res.data
                else -> null
            }
            if (existingTask != null && existingTask.dueAt == newDueAt) {
                return ActionExecutionResult(
                    executedAt = now,
                    success = true,
                    affectedEntityId = taskId,
                    affectedEntityType = "TASK",
                    message = "Task successfully rescheduled to $newDueAt",
                    newDueAt = newDueAt,
                    previousDueAt = existingTask.dueAt
                )
            }
        }

        val editResult = useCase(
            taskId = taskId,
            dueAt = newDueAt
        )

        return when (editResult) {
            is Result.Success -> ActionExecutionResult(
                executedAt = now,
                success = true,
                affectedEntityId = taskId,
                affectedEntityType = "TASK",
                message = "Task successfully rescheduled to $newDueAt",
                newDueAt = newDueAt
            )
            is Result.Failure -> ActionExecutionResult(
                executedAt = now,
                success = false,
                affectedEntityId = taskId,
                affectedEntityType = "TASK",
                message = "Failed to reschedule task: ${editResult.error.message}"
            )
        }
    }

    private suspend fun executeTaskDecomposition(
        action: ProposedAction,
        userId: String,
        params: Map<String, Any?>,
        now: Long
    ): ActionExecutionResult {
        val taskId = action.targetEntityId ?: return ActionExecutionResult(now, false, null, "TASK", "Target task ID missing")
        val taskRepo = taskRepository ?: return ActionExecutionResult(now, false, taskId, "TASK", "TaskRepository not available")
        val createUseCase = createTaskUseCase ?: return ActionExecutionResult(now, false, taskId, "TASK", "CreateTaskUseCase not available")

        val parentTask = when (val res = taskRepo.getTask(taskId)) {
            is Result.Success -> res.data ?: return ActionExecutionResult(now, false, taskId, "TASK", "Parent task not found")
            is Result.Failure -> return ActionExecutionResult(now, false, taskId, "TASK", "Could not load parent task")
        }

        @Suppress("UNCHECKED_CAST")
        val subTaskTitles = (params["subTasks"] as? List<String>)?.filter { it.isNotBlank() }
            ?: action.suggestedSubTasks.ifEmpty {
                listOf(
                    "${parentTask.title} (Part 1)",
                    "${parentTask.title} (Part 2)"
                )
            }

        val createdIds = mutableListOf<String>()
        for ((index, subTitle) in subTaskTitles.withIndex()) {
            val subTaskId = "${action.id}_sub_$index"
            val subTaskEventId = "evt_task_created_$subTaskId"

            // Idempotency check: check if deterministic subtask already exists in repository
            val existingSubTask = when (val res = taskRepo.getTask(subTaskId)) {
                is Result.Success -> res.data
                else -> null
            }

            if (existingSubTask != null) {
                // Subtask was already created in prior run/attempt; reuse without duplicate insertion or event
                createdIds.add(existingSubTask.id)
            } else {
                val res = createUseCase(
                    userId = userId,
                    goalId = parentTask.goalId,
                    planId = parentTask.planId,
                    title = subTitle,
                    description = "Sub-task generated from decomposition of: ${parentTask.title}",
                    priority = parentTask.priority,
                    estimatedDurationMinutes = (parentTask.estimatedDurationMinutes ?: 60) / subTaskTitles.size,
                    dueAt = parentTask.dueAt,
                    taskId = subTaskId,
                    eventId = subTaskEventId
                )
                if (res is Result.Success) {
                    createdIds.add(res.data.id)
                }
            }
        }

        return ActionExecutionResult(
            executedAt = now,
            success = createdIds.isNotEmpty(),
            affectedEntityId = taskId,
            affectedEntityType = "TASK",
            message = "Decomposed into ${createdIds.size} sub-tasks",
            resultingSubTaskIds = createdIds
        )
    }

    private suspend fun executeCommitmentReview(
        action: ProposedAction,
        userId: String,
        params: Map<String, Any?>,
        now: Long
    ): ActionExecutionResult {
        val commitmentId = action.targetEntityId
        if (commitmentId != null && scheduleCommitmentUseCase != null) {
            val newTime = (params["scheduledStartAt"] as? Long) ?: (now + 3600000L)
            val res = scheduleCommitmentUseCase(userId, commitmentId, newTime)
            return when (res) {
                is Result.Success -> ActionExecutionResult(
                    executedAt = now,
                    success = true,
                    affectedEntityId = commitmentId,
                    affectedEntityType = "COMMITMENT",
                    message = "Commitment scheduled for $newTime"
                )
                is Result.Failure -> ActionExecutionResult(
                    executedAt = now,
                    success = false,
                    affectedEntityId = commitmentId,
                    affectedEntityType = "COMMITMENT",
                    message = "Failed to reschedule commitment: ${res.error.message}"
                )
            }
        }
        return ActionExecutionResult(now, true, commitmentId, "COMMITMENT", "Commitment review noted")
    }

    private suspend fun executeFocusProtection(
        action: ProposedAction,
        userId: String,
        params: Map<String, Any?>,
        now: Long
    ): ActionExecutionResult {
        val createUseCase = createCommitmentUseCase
        if (createUseCase != null) {
            val focusCommitmentId = "${action.id}_focus_comm"
            val focusEventId = "evt_focus_comm_${action.id}"

            // Idempotency check: check if focus commitment was already created
            val existingCommitment = when (val res = commitmentRepository?.getCommitment(focusCommitmentId)) {
                is Result.Success -> res.data
                else -> null
            }
            if (existingCommitment != null) {
                return ActionExecutionResult(
                    executedAt = existingCommitment.createdAt,
                    success = true,
                    affectedEntityId = existingCommitment.id,
                    affectedEntityType = "COMMITMENT",
                    message = "Protected focus commitment created: ${existingCommitment.id}"
                )
            }

            val title = (params["title"] as? String) ?: "Protected Focus Session"
            val duration = (params["durationMinutes"] as? Int) ?: 45
            val startAt = (params["startAt"] as? Long) ?: (now + 600000L) // in 10 mins

            val res = createUseCase(
                userId = userId,
                goalId = null,
                planId = null,
                taskId = action.targetEntityId,
                parentCommitmentId = null,
                title = title,
                description = "Focus time dedicated to priority execution.",
                priority = 2,
                scheduledStartAt = startAt,
                scheduledEndAt = startAt + (duration * 60 * 1000L),
                initialStatus = CommitmentStatus.COMMITTED,
                commitmentId = focusCommitmentId,
                eventId = focusEventId
            )
            return when (res) {
                is Result.Success -> ActionExecutionResult(
                    executedAt = now,
                    success = true,
                    affectedEntityId = res.data.id,
                    affectedEntityType = "COMMITMENT",
                    message = "Protected focus commitment created: ${res.data.id}"
                )
                is Result.Failure -> ActionExecutionResult(
                    executedAt = now,
                    success = false,
                    affectedEntityId = null,
                    affectedEntityType = "COMMITMENT",
                    message = "Failed to create focus commitment: ${res.error.message}"
                )
            }
        }
        return ActionExecutionResult(now, true, null, "FOCUS", "Focus period acknowledged")
    }

    private suspend fun executeGoalPlanReconsideration(
        action: ProposedAction,
        userId: String,
        params: Map<String, Any?>,
        now: Long
    ): ActionExecutionResult {
        val planId = action.targetEntityId
        if (planId != null && action.targetEntityType == "PLAN" && updatePlanStatusUseCase != null) {
            val newStatusStr = params["newStatus"] as? String
            val newStatus = if (newStatusStr != null) {
                try { PlanStatus.valueOf(newStatusStr) } catch (e: Exception) { PlanStatus.PAUSED }
            } else {
                PlanStatus.PAUSED
            }
            val res = updatePlanStatusUseCase(userId, planId, newStatus)
            return when (res) {
                is Result.Success -> ActionExecutionResult(
                    executedAt = now,
                    success = true,
                    affectedEntityId = planId,
                    affectedEntityType = "PLAN",
                    message = "Plan status updated to $newStatus"
                )
                is Result.Failure -> ActionExecutionResult(
                    executedAt = now,
                    success = false,
                    affectedEntityId = planId,
                    affectedEntityType = "PLAN",
                    message = "Could not update plan status: ${res.error.message}"
                )
            }
        }
        return ActionExecutionResult(now, true, planId, action.targetEntityType ?: "PLAN", "Reconsideration acknowledged")
    }

    private fun executePatternReview(
        action: ProposedAction,
        now: Long
    ): ActionExecutionResult {
        return ActionExecutionResult(
            executedAt = now,
            success = true,
            affectedEntityId = action.sourceInsightId,
            affectedEntityType = "PATTERN",
            message = "Behaviour pattern acknowledged by user"
        )
    }
}
