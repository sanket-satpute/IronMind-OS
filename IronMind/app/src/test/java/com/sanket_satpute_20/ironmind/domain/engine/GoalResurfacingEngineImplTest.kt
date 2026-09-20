package com.sanket_satpute_20.ironmind.domain.engine

import com.sanket_satpute_20.ironmind.domain.ai.AIOutput
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Goal
import com.sanket_satpute_20.ironmind.domain.model.GoalStatus
import com.sanket_satpute_20.ironmind.domain.model.Task
import com.sanket_satpute_20.ironmind.domain.model.TaskStatus
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecord
import com.sanket_satpute_20.ironmind.domain.repository.GoalRepository
import com.sanket_satpute_20.ironmind.domain.repository.TaskRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeClock
import com.sanket_satpute_20.ironmind.testutil.fake.FakeIronLogger
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class GoalResurfacingEngineImplTest {

    private lateinit var goalRepository: FakeGoalRepository
    private lateinit var taskRepository: FakeTaskRepository
    private lateinit var pipeline: FakeInterventionExecutionPipeline
    private lateinit var logger: FakeIronLogger
    private lateinit var clock: FakeClock
    private lateinit var engine: GoalResurfacingEngineImpl

    private val THRESHOLD = 14 * 24 * 60 * 60 * 1000L

    @Before
    fun setup() {
        goalRepository = FakeGoalRepository()
        taskRepository = FakeTaskRepository()
        pipeline = FakeInterventionExecutionPipeline()
        logger = FakeIronLogger()
        clock = FakeClock(100L * 24 * 60 * 60 * 1000L) // 100 days in

        engine = GoalResurfacingEngineImpl(
            goalRepository = goalRepository,
            taskRepository = taskRepository,
            interventionExecutionPipeline = pipeline,
            clock = clock,
            logger = logger,
            neglectThresholdMs = THRESHOLD
        )
    }

    @Test
    fun `ignores newly created goals`() = runTest {
        val now = clock.currentTimeMillis()
        goalRepository.goals.add(createGoal("g1", createdAt = now - (5 * 24 * 60 * 60 * 1000L), updatedAt = now - (5 * 24 * 60 * 60 * 1000L)))
        
        engine.evaluateNeglectedGoals("u1")

        assertEquals(0, pipeline.proposals.size)
    }

    @Test
    fun `ignores non-active goals`() = runTest {
        val now = clock.currentTimeMillis()
        val oldTime = now - (20 * 24 * 60 * 60 * 1000L)
        goalRepository.goals.add(createGoal("g1", status = GoalStatus.COMPLETED, createdAt = oldTime, updatedAt = oldTime))
        
        engine.evaluateNeglectedGoals("u1")

        assertEquals(0, pipeline.proposals.size)
    }

    @Test
    fun `ignores old goals with recent tasks`() = runTest {
        val now = clock.currentTimeMillis()
        val oldTime = now - (20 * 24 * 60 * 60 * 1000L)
        val recentTime = now - (5 * 24 * 60 * 60 * 1000L)
        
        goalRepository.goals.add(createGoal("g1", createdAt = oldTime, updatedAt = oldTime))
        
        taskRepository.tasks.add(createTask("t1", "g1", updatedAt = recentTime))

        engine.evaluateNeglectedGoals("u1")

        assertEquals(0, pipeline.proposals.size)
    }

    @Test
    fun `proposes intervention for neglected active goal`() = runTest {
        val now = clock.currentTimeMillis()
        val oldTime = now - (20 * 24 * 60 * 60 * 1000L) // 20 days old/inactive
        
        goalRepository.goals.add(createGoal("g1", createdAt = oldTime, updatedAt = oldTime))

        engine.evaluateNeglectedGoals("u1")

        assertEquals(1, pipeline.proposals.size)
        val proposal = pipeline.proposals[0]
        assertEquals("g1", proposal.targetEntityId)
    }

    private fun createGoal(id: String, status: GoalStatus = GoalStatus.ACTIVE, createdAt: Long, updatedAt: Long): Goal {
        return Goal(id = id, userId = "u1", title = "Goal", description = "Desc", why = "Why", importance = 5, status = status, createdAt = createdAt, updatedAt = updatedAt)
    }

    private fun createTask(id: String, goalId: String, updatedAt: Long): Task {
        return Task(id = id, userId = "u1", goalId = goalId, title = "Task", description = "Desc", status = TaskStatus.PENDING, priority = 1, createdAt = updatedAt, updatedAt = updatedAt, source = EntitySource.USER)
    }
}

class FakeGoalRepository : GoalRepository {
    val goals = mutableListOf<Goal>()

    override suspend fun saveGoal(goal: Goal): Result<Unit, Exception> = Result.Success(Unit)
    override suspend fun getGoal(id: String): Result<Goal?, Exception> = Result.Success(goals.find { it.id == id })
    override suspend fun getGoalsForUser(userId: String): Result<List<Goal>, Exception> = Result.Success(goals.filter { it.userId == userId })
    override suspend fun searchGoals(userId: String, query: String): Result<List<Goal>, Exception> = Result.Success(emptyList())
}

class FakeTaskRepository : TaskRepository {
    val tasks = mutableListOf<Task>()

    override suspend fun saveTask(task: Task): Result<Unit, Exception> = Result.Success(Unit)
    override suspend fun getTask(id: String): Result<Task?, Exception> = Result.Success(tasks.find { it.id == id })
    override suspend fun getTasksForPlan(planId: String): Result<List<Task>, Exception> = Result.Success(tasks.filter { it.planId == planId })
    override suspend fun getTasksForGoal(goalId: String): Result<List<Task>, Exception> = Result.Success(tasks.filter { it.goalId == goalId })
}

class FakeInterventionExecutionPipeline : InterventionExecutionPipeline {
    val proposals = mutableListOf<AIOutput.InterventionRecommendation>()

    override suspend fun propose(userId: String, candidate: AIOutput.InterventionRecommendation): Result<InterventionRecord, Exception> {
        proposals.add(candidate)
        return Result.Failure(Exception("Not implemented")) // We only care that it was called
    }
    
    override suspend fun trigger(id: String): Result<InterventionRecord, Exception> = Result.Failure(Exception("Not implemented"))
    override suspend fun deliver(id: String): Result<InterventionRecord, Exception> = Result.Failure(Exception("Not implemented"))
    override suspend fun resolve(id: String, reason: com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionResolutionReason): Result<InterventionRecord, Exception> = Result.Failure(Exception("Not implemented"))
}
