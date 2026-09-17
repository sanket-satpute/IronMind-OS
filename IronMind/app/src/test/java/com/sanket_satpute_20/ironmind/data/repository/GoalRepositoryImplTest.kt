package com.sanket_satpute_20.ironmind.data.repository

import com.sanket_satpute_20.ironmind.data.local.dao.IronMindDao
import com.sanket_satpute_20.ironmind.data.local.entity.*
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Goal
import com.sanket_satpute_20.ironmind.domain.model.GoalStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GoalRepositoryImplTest {

    private lateinit var dao: StubDao
    private lateinit var repository: GoalRepositoryImpl

    class StubDao : IronMindDao {
        var insertedGoal: GoalEntity? = null
        var returnedGoal: GoalEntity? = null
        
        override fun insertUserProfile(profile: UserProfileEntity) {}
        override fun getUserProfile(id: String): UserProfileEntity? = null
        override fun getLocalUserProfile(): UserProfileEntity? = null
        
        override fun insertGoal(goal: GoalEntity) {
            insertedGoal = goal
        }
        override fun getGoal(id: String): GoalEntity? = returnedGoal
        override fun getGoalsForUser(userId: String): List<GoalEntity> = emptyList()
        
        override fun insertPlan(plan: PlanEntity) {}
        override fun getPlan(id: String): PlanEntity? = null
        override fun getPlansForGoal(goalId: String): List<PlanEntity> = emptyList()
        
        override fun insertTask(task: TaskEntity) {}
        override fun getTask(id: String): TaskEntity? = null
        override fun getTasksForPlan(planId: String): List<TaskEntity> = emptyList()
        override fun getTasksForGoal(goalId: String): List<TaskEntity> = emptyList()
        override fun insertCommitment(commitment: CommitmentEntity) {}
        override fun getCommitment(id: String): CommitmentEntity? = null
        override fun getCommitmentsForUser(userId: String): List<CommitmentEntity> = emptyList()
        override fun getCommitmentsForGoal(goalId: String): List<CommitmentEntity> = emptyList()
        override fun getCommitmentsForPlan(planId: String): List<CommitmentEntity> = emptyList()
        override fun getCommitmentsForTask(taskId: String): List<CommitmentEntity> = emptyList()
        override fun insertOutcome(outcome: OutcomeEntity) {}
        override fun getOutcome(id: String): OutcomeEntity? = null
        override fun insertReflection(reflection: ReflectionEntity) {}
        override fun getReflection(id: String): ReflectionEntity? = null
    }

    @Before
    fun setup() {
        dao = StubDao()
        repository = GoalRepositoryImpl(dao)
    }

    @Test
    fun `saveGoal correctly maps domain model to entity`() = runTest {
        val domainGoal = Goal(
            id = "goal-1",
            userId = "user-1",
            ambitionId = "ambition-1",
            title = "Title",
            description = "Desc",
            why = "Why",
            importance = 8,
            status = GoalStatus.ACTIVE,
            targetAt = 1000L,
            startedAt = 2000L,
            completedAt = null,
            createdAt = 3000L,
            updatedAt = 4000L
        )

        val result = repository.saveGoal(domainGoal)
        
        assertTrue(result is Result.Success)
        
        val expectedEntity = GoalEntity(
            id = "goal-1",
            userId = "user-1",
            ambitionId = "ambition-1",
            title = "Title",
            description = "Desc",
            why = "Why",
            importance = 8,
            status = "ACTIVE",
            targetAt = 1000L,
            startedAt = 2000L,
            completedAt = null,
            createdAt = 3000L,
            updatedAt = 4000L,
            schemaVersion = 1
        )
        
        assertEquals(expectedEntity, dao.insertedGoal)
    }

    @Test
    fun `getGoal correctly maps entity to domain model`() = runTest {
        val entity = GoalEntity(
            id = "goal-1",
            userId = "user-1",
            ambitionId = "ambition-1",
            title = "Title",
            description = "Desc",
            why = "Why",
            importance = 8,
            status = "ACTIVE",
            targetAt = 1000L,
            startedAt = 2000L,
            completedAt = null,
            createdAt = 3000L,
            updatedAt = 4000L,
            schemaVersion = 1
        )

        dao.returnedGoal = entity

        val result = repository.getGoal("goal-1")
        
        assertTrue(result is Result.Success)
        val domainGoal = (result as Result.Success).data!!
        
        assertEquals("goal-1", domainGoal.id)
        assertEquals(GoalStatus.ACTIVE, domainGoal.status)
        assertEquals(8, domainGoal.importance)
    }
}
