package com.sanket_satpute_20.ironmind.data.repository

import com.sanket_satpute_20.ironmind.data.local.dao.IronMindDao
import com.sanket_satpute_20.ironmind.data.local.entity.*
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.Plan
import com.sanket_satpute_20.ironmind.domain.model.PlanStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PlanRepositoryImplTest {

    private lateinit var dao: StubDao
    private lateinit var repository: PlanRepositoryImpl

    class StubDao : IronMindDao {
        var insertedPlan: PlanEntity? = null
        var returnedPlan: PlanEntity? = null
        
        override fun insertUserProfile(profile: UserProfileEntity) {}
        override fun getUserProfile(id: String): UserProfileEntity? = null
        override fun getLocalUserProfile(): UserProfileEntity? = null
        override fun insertGoal(goal: GoalEntity) {}
        override fun getGoal(id: String): GoalEntity? = null
        override fun getGoalsForUser(userId: String): List<GoalEntity> = emptyList()
        
        override fun insertPlan(plan: PlanEntity) {
            insertedPlan = plan
        }
        override fun getPlan(id: String): PlanEntity? = returnedPlan
        override fun getPlansForGoal(goalId: String): List<PlanEntity> = emptyList()
        
        override fun insertTask(task: TaskEntity) {}
        override fun getTask(id: String): TaskEntity? = null
        override fun getTasksForPlan(planId: String): List<TaskEntity> = emptyList()
        override fun getTasksForGoal(goalId: String): List<TaskEntity> = emptyList()
        override fun insertCommitment(commitment: CommitmentEntity) {}
        override fun getCommitment(id: String): CommitmentEntity? = null
        override fun getCommitmentsForUser(userId: String): List<CommitmentEntity> = emptyList()
        override fun getActiveCommitmentsForUser(userId: String, statuses: List<String>): List<CommitmentEntity> = emptyList()
        override fun getCommitmentsForGoal(goalId: String): List<CommitmentEntity> = emptyList()
        override fun getCommitmentsForDateRange(userId: String, startTime: Long, endTime: Long): List<CommitmentEntity> = emptyList()
        override fun getCommitmentsForPlan(planId: String): List<CommitmentEntity> = emptyList()
        override fun getCommitmentsForTask(taskId: String): List<CommitmentEntity> = emptyList()
        override fun insertOutcome(outcome: OutcomeEntity) {}
        override fun getOutcome(id: String): OutcomeEntity? = null
        override fun getOutcomeForSource(sourceEntityId: String): OutcomeEntity? = null
        override fun insertReflection(reflection: ReflectionEntity) {}
        override fun getReflection(id: String): ReflectionEntity? = null
        override fun insertProtectionRule(rule: ProtectionRuleEntity) {}
        override fun getProtectionRule(id: String): ProtectionRuleEntity? = null
        override fun getProtectionRulesForUser(userId: String): List<ProtectionRuleEntity> = emptyList()
        override fun insertProtectionSession(session: ProtectionSessionEntity) {}
        override fun getProtectionSession(id: String): ProtectionSessionEntity? = null
        override fun getActiveProtectionSessionsForUser(userId: String): List<ProtectionSessionEntity> = emptyList()
        override fun insertEvent(event: EventEntity) {}
        override fun getEventsForEntity(entityId: String): List<EventEntity> = emptyList()
        override fun getEventsForUser(userId: String): List<EventEntity> = emptyList()
    }

    @Before
    fun setup() {
        dao = StubDao()
        repository = PlanRepositoryImpl(dao)
    }

    @Test
    fun `savePlan correctly maps domain model to entity`() = runTest {
        val domainPlan = Plan(
            id = "plan-1",
            userId = "user-1",
            goalId = "goal-1",
            ambitionId = null,
            title = "Title",
            description = "Desc",
            status = PlanStatus.ACTIVE,
            createdAt = 1000L,
            updatedAt = 2000L,
            startedAt = 1000L,
            completedAt = null,
            source = EntitySource.USER
        )

        val result = repository.savePlan(domainPlan)
        assertTrue(result is Result.Success)
        
        val expectedEntity = PlanEntity(
            id = "plan-1",
            userId = "user-1",
            goalId = "goal-1",
            ambitionId = null,
            title = "Title",
            description = "Desc",
            status = "ACTIVE",
            createdAt = 1000L,
            updatedAt = 2000L,
            startedAt = 1000L,
            completedAt = null,
            source = "USER",
            schemaVersion = 1
        )
        
        assertEquals(expectedEntity, dao.insertedPlan)
    }

    @Test
    fun `getPlan correctly maps entity to domain model`() = runTest {
        val entity = PlanEntity(
            id = "plan-1",
            userId = "user-1",
            goalId = "goal-1",
            ambitionId = null,
            title = "Title",
            description = "Desc",
            status = "ACTIVE",
            createdAt = 1000L,
            updatedAt = 2000L,
            startedAt = 1000L,
            completedAt = null,
            source = "USER",
            schemaVersion = 1
        )

        dao.returnedPlan = entity

        val result = repository.getPlan("plan-1")
        assertTrue(result is Result.Success)
        val domainPlan = (result as Result.Success).data!!
        
        assertEquals("plan-1", domainPlan.id)
        assertEquals(PlanStatus.ACTIVE, domainPlan.status)
        assertEquals(EntitySource.USER, domainPlan.source)
    }
}
