package com.sanket_satpute_20.ironmind.domain.usecase.progress

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.Goal
import com.sanket_satpute_20.ironmind.domain.model.GoalStatus
import com.sanket_satpute_20.ironmind.domain.model.Plan
import com.sanket_satpute_20.ironmind.domain.model.PlanStatus
import com.sanket_satpute_20.ironmind.domain.model.ProgressInfo
import com.sanket_satpute_20.ironmind.domain.model.Task
import com.sanket_satpute_20.ironmind.domain.model.TaskStatus
import com.sanket_satpute_20.ironmind.domain.usecase.goal.GetGoalProgressUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.plan.GetPlanProgressUseCase
import com.sanket_satpute_20.ironmind.testutil.fake.FakePlanRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeTaskRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GoalAndPlanProgressTest {

    private lateinit var planRepository: FakePlanRepository
    private lateinit var taskRepository: FakeTaskRepository
    private lateinit var getPlanProgressUseCase: GetPlanProgressUseCase
    private lateinit var getGoalProgressUseCase: GetGoalProgressUseCase

    private val testUserId = "user-test-1"
    private val otherUserId = "user-other-99"

    @Before
    fun setUp() {
        planRepository = FakePlanRepository()
        taskRepository = FakeTaskRepository()
        getPlanProgressUseCase = GetPlanProgressUseCase(taskRepository)
        getGoalProgressUseCase = GetGoalProgressUseCase(planRepository, taskRepository)
    }

    private fun createTask(
        id: String,
        planId: String? = null,
        goalId: String? = null,
        status: TaskStatus = TaskStatus.PENDING,
        userId: String = testUserId
    ): Task = Task(
        id = id,
        userId = userId,
        goalId = goalId,
        planId = planId,
        title = "Task $id",
        description = "Description",
        status = status,
        priority = 1,
        createdAt = 1000L,
        updatedAt = 1000L,
        source = EntitySource.USER
    )

    private fun createPlan(
        id: String,
        goalId: String? = null,
        status: PlanStatus = PlanStatus.ACTIVE,
        userId: String = testUserId
    ): Plan = Plan(
        id = id,
        userId = userId,
        goalId = goalId,
        title = "Plan $id",
        description = "Description",
        status = status,
        createdAt = 1000L,
        updatedAt = 1000L,
        source = EntitySource.USER
    )

    private fun createGoal(
        id: String,
        status: GoalStatus = GoalStatus.ACTIVE,
        userId: String = testUserId
    ): Goal = Goal(
        id = id,
        userId = userId,
        title = "Goal $id",
        description = "Description",
        why = "Why",
        importance = 5,
        status = status,
        createdAt = 1000L,
        updatedAt = 1000L
    )

    // 1. Zero-task Goal and Plan
    @Test
    fun `zero-task Plan returns ZERO progress without division by zero`() = runTest {
        val plan = createPlan("plan-empty")
        planRepository.savePlan(plan)

        val result = getPlanProgressUseCase(plan.id, testUserId)
        assertTrue(result is Result.Success)
        val progress = (result as Result.Success).data

        assertEquals(0, progress.totalTasks)
        assertEquals(0, progress.completedTasks)
        assertEquals(0, progress.pendingTasks)
        assertEquals(0, progress.percentage)
        assertEquals(0f, progress.progressFraction, 0.001f)
        assertTrue(progress.isZeroTasks)
    }

    @Test
    fun `zero-task Goal returns ZERO progress without division by zero`() = runTest {
        val goal = createGoal("goal-empty")
        val result = getGoalProgressUseCase(goal.id, testUserId)

        assertTrue(result is Result.Success)
        val progress = (result as Result.Success).data

        assertEquals(0, progress.totalTasks)
        assertEquals(0, progress.completedTasks)
        assertEquals(0, progress.pendingTasks)
        assertEquals(0, progress.percentage)
        assertEquals(0f, progress.progressFraction, 0.001f)
        assertTrue(progress.isZeroTasks)
    }

    // 2. Partially completed Goal and Plan
    @Test
    fun `partially completed Plan calculates percentage and counts accurately`() = runTest {
        val plan = createPlan("plan-partial")
        planRepository.savePlan(plan)

        taskRepository.saveTask(createTask("t1", planId = plan.id, status = TaskStatus.COMPLETED))
        taskRepository.saveTask(createTask("t2", planId = plan.id, status = TaskStatus.PENDING))

        val result = getPlanProgressUseCase(plan.id, testUserId)
        assertTrue(result is Result.Success)
        val progress = (result as Result.Success).data

        assertEquals(2, progress.totalTasks)
        assertEquals(1, progress.completedTasks)
        assertEquals(1, progress.pendingTasks)
        assertEquals(50, progress.percentage)
        assertEquals(0.5f, progress.progressFraction, 0.001f)
        assertFalse(progress.isZeroTasks)
    }

    @Test
    fun `partially completed Goal aggregates tasks across multiple plans and direct tasks`() = runTest {
        val goal = createGoal("goal-partial")
        val plan1 = createPlan("plan-1", goalId = goal.id)
        val plan2 = createPlan("plan-2", goalId = goal.id)
        planRepository.savePlan(plan1)
        planRepository.savePlan(plan2)

        // Plan 1 has 1 completed, 1 in-progress
        taskRepository.saveTask(createTask("t1", planId = plan1.id, status = TaskStatus.COMPLETED))
        taskRepository.saveTask(createTask("t2", planId = plan1.id, status = TaskStatus.IN_PROGRESS))

        // Plan 2 has 1 completed, 1 postponed
        taskRepository.saveTask(createTask("t3", planId = plan2.id, status = TaskStatus.COMPLETED))
        taskRepository.saveTask(createTask("t4", planId = plan2.id, status = TaskStatus.POSTPONED))

        // Goal has 1 direct task: pending
        taskRepository.saveTask(createTask("t5", goalId = goal.id, status = TaskStatus.PENDING))

        val result = getGoalProgressUseCase(goal.id, testUserId)
        assertTrue(result is Result.Success)
        val progress = (result as Result.Success).data

        assertEquals(5, progress.totalTasks)
        assertEquals(2, progress.completedTasks) // t1, t3
        assertEquals(3, progress.pendingTasks)   // t2 (in_progress), t4 (postponed), t5 (pending)
        assertEquals(40, progress.percentage)   // 2 / 5 = 40%
        assertEquals(0.4f, progress.progressFraction, 0.001f)
    }

    // 3. Fully completed Goal and Plan
    @Test
    fun `fully completed Plan calculates 100 percent progress`() = runTest {
        val plan = createPlan("plan-full")
        planRepository.savePlan(plan)

        taskRepository.saveTask(createTask("t1", planId = plan.id, status = TaskStatus.COMPLETED))
        taskRepository.saveTask(createTask("t2", planId = plan.id, status = TaskStatus.COMPLETED))
        taskRepository.saveTask(createTask("t3", planId = plan.id, status = TaskStatus.COMPLETED))

        val result = getPlanProgressUseCase(plan.id, testUserId)
        val progress = (result as Result.Success).data

        assertEquals(3, progress.totalTasks)
        assertEquals(3, progress.completedTasks)
        assertEquals(0, progress.pendingTasks)
        assertEquals(100, progress.percentage)
        assertEquals(1.0f, progress.progressFraction, 0.001f)
    }

    @Test
    fun `fully completed Goal calculates 100 percent progress`() = runTest {
        val goal = createGoal("goal-full")
        val plan = createPlan("p1", goalId = goal.id)
        planRepository.savePlan(plan)

        taskRepository.saveTask(createTask("t1", planId = plan.id, status = TaskStatus.COMPLETED))
        taskRepository.saveTask(createTask("t2", goalId = goal.id, status = TaskStatus.COMPLETED))

        val result = getGoalProgressUseCase(goal.id, testUserId)
        val progress = (result as Result.Success).data

        assertEquals(2, progress.totalTasks)
        assertEquals(2, progress.completedTasks)
        assertEquals(0, progress.pendingTasks)
        assertEquals(100, progress.percentage)
    }

    // 4. Reopened task reducing progress
    @Test
    fun `reopened task reduces completed count and percentage`() = runTest {
        val plan = createPlan("plan-reopen")
        planRepository.savePlan(plan)

        val task1 = createTask("t1", planId = plan.id, status = TaskStatus.COMPLETED)
        val task2 = createTask("t2", planId = plan.id, status = TaskStatus.COMPLETED)
        taskRepository.saveTask(task1)
        taskRepository.saveTask(task2)

        val initialProgress = (getPlanProgressUseCase(plan.id, testUserId) as Result.Success).data
        assertEquals(100, initialProgress.percentage)
        assertEquals(2, initialProgress.completedTasks)
        assertEquals(0, initialProgress.pendingTasks)

        // Reopen task2 -> status becomes PENDING
        taskRepository.saveTask(task2.copy(status = TaskStatus.PENDING))

        val updatedProgress = (getPlanProgressUseCase(plan.id, testUserId) as Result.Success).data
        assertEquals(50, updatedProgress.percentage)
        assertEquals(1, updatedProgress.completedTasks)
        assertEquals(1, updatedProgress.pendingTasks)
    }

    // 5. Deleted task exclusion
    @Test
    fun `deleted task is excluded from progress calculation and updates totals`() = runTest {
        val plan = createPlan("plan-delete")
        planRepository.savePlan(plan)

        val task1 = createTask("t1", planId = plan.id, status = TaskStatus.COMPLETED)
        val task2 = createTask("t2", planId = plan.id, status = TaskStatus.PENDING)
        taskRepository.saveTask(task1)
        taskRepository.saveTask(task2)

        val before = (getPlanProgressUseCase(plan.id, testUserId) as Result.Success).data
        assertEquals(2, before.totalTasks)
        assertEquals(1, before.completedTasks)
        assertEquals(50, before.percentage)

        // Delete pending task2
        taskRepository.deleteTask(testUserId, task2.id)

        val after = (getPlanProgressUseCase(plan.id, testUserId) as Result.Success).data
        assertEquals(1, after.totalTasks)
        assertEquals(1, after.completedTasks)
        assertEquals(0, after.pendingTasks)
        assertEquals(100, after.percentage)
    }

    // 6. User ownership filtering
    @Test
    fun `user ownership filtering excludes tasks belonging to other users`() = runTest {
        val plan = createPlan("plan-ownership")
        planRepository.savePlan(plan)

        // 1 task for active user (COMPLETED)
        taskRepository.saveTask(createTask("t-mine", planId = plan.id, status = TaskStatus.COMPLETED, userId = testUserId))

        // 3 tasks for another user
        taskRepository.saveTask(createTask("t-other-1", planId = plan.id, status = TaskStatus.PENDING, userId = otherUserId))
        taskRepository.saveTask(createTask("t-other-2", planId = plan.id, status = TaskStatus.FAILED, userId = otherUserId))
        taskRepository.saveTask(createTask("t-other-3", planId = plan.id, status = TaskStatus.COMPLETED, userId = otherUserId))

        val result = getPlanProgressUseCase(plan.id, testUserId)
        val progress = (result as Result.Success).data

        assertEquals(1, progress.totalTasks)
        assertEquals(1, progress.completedTasks)
        assertEquals(0, progress.pendingTasks)
        assertEquals(100, progress.percentage)
    }

    // 7. Paused Goal and Plan retain progress
    @Test
    fun `paused Goal and Plan retain progress information`() = runTest {
        val pausedGoal = createGoal("goal-paused", status = GoalStatus.PAUSED)
        val pausedPlan = createPlan("plan-paused", goalId = pausedGoal.id, status = PlanStatus.PAUSED)
        planRepository.savePlan(pausedPlan)

        taskRepository.saveTask(createTask("t1", planId = pausedPlan.id, status = TaskStatus.COMPLETED))
        taskRepository.saveTask(createTask("t2", planId = pausedPlan.id, status = TaskStatus.PENDING))

        val planProgress = (getPlanProgressUseCase(pausedPlan.id, testUserId) as Result.Success).data
        assertEquals(2, planProgress.totalTasks)
        assertEquals(1, planProgress.completedTasks)
        assertEquals(50, planProgress.percentage)

        val goalProgress = (getGoalProgressUseCase(pausedGoal.id, testUserId) as Result.Success).data
        assertEquals(2, goalProgress.totalTasks)
        assertEquals(1, goalProgress.completedTasks)
        assertEquals(50, goalProgress.percentage)
    }

    @Test
    fun `archived and completed Goals retain progress information`() = runTest {
        val archivedGoal = createGoal("goal-archived", status = GoalStatus.ARCHIVED)
        val plan = createPlan("plan-archived", goalId = archivedGoal.id)
        planRepository.savePlan(plan)

        taskRepository.saveTask(createTask("t1", planId = plan.id, status = TaskStatus.COMPLETED))

        val progress = (getGoalProgressUseCase(archivedGoal.id, testUserId) as Result.Success).data
        assertEquals(1, progress.totalTasks)
        assertEquals(1, progress.completedTasks)
        assertEquals(100, progress.percentage)
    }

    // 8. Correct handling of all existing task statuses
    @Test
    fun `all existing TaskStatus values are classified correctly`() {
        val tasks = listOf(
            createTask("c1", status = TaskStatus.COMPLETED),
            createTask("p1", status = TaskStatus.PENDING),
            createTask("ip1", status = TaskStatus.IN_PROGRESS),
            createTask("post1", status = TaskStatus.POSTPONED),
            createTask("f1", status = TaskStatus.FAILED),
            createTask("canc1", status = TaskStatus.CANCELLED)
        )

        val progress = ProgressInfo.fromTasks(tasks, testUserId)

        // 6 total tasks
        assertEquals(6, progress.totalTasks)
        // 1 completed (COMPLETED)
        assertEquals(1, progress.completedTasks)
        // 3 pending (PENDING, IN_PROGRESS, POSTPONED)
        assertEquals(3, progress.pendingTasks)
        // 1/6 = 16%
        assertEquals(16, progress.percentage)
    }

    // 9. Deduplication when task is linked to both planId and goalId
    @Test
    fun `task linked to both planId and goalId is deduplicated and counted once`() = runTest {
        val goal = createGoal("goal-dedup")
        val plan = createPlan("plan-dedup", goalId = goal.id)
        planRepository.savePlan(plan)

        // Task has both planId and goalId set
        val task = createTask("t-both", planId = plan.id, goalId = goal.id, status = TaskStatus.COMPLETED)
        taskRepository.saveTask(task)

        val goalProgress = (getGoalProgressUseCase(goal.id, testUserId) as Result.Success).data
        assertEquals(1, goalProgress.totalTasks)
        assertEquals(1, goalProgress.completedTasks)
        assertEquals(100, goalProgress.percentage)
    }

    // 10. Non-mutation of Goal and Plan status
    @Test
    fun `calculating progress does not silently modify Goal or Plan status`() = runTest {
        val activeGoal = createGoal("goal-immutable", status = GoalStatus.ACTIVE)
        val activePlan = createPlan("plan-immutable", goalId = activeGoal.id, status = PlanStatus.ACTIVE)
        planRepository.savePlan(activePlan)

        // All tasks completed (100% progress)
        taskRepository.saveTask(createTask("t1", planId = activePlan.id, status = TaskStatus.COMPLETED))

        getGoalProgressUseCase(activeGoal.id, testUserId)
        getPlanProgressUseCase(activePlan.id, testUserId)

        // Status must strictly remain ACTIVE (never auto-completed)
        assertEquals(GoalStatus.ACTIVE, activeGoal.status)
        assertEquals(PlanStatus.ACTIVE, activePlan.status)
    }
}

