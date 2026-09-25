package com.sanket_satpute_20.ironmind.data.repository

import com.sanket_satpute_20.ironmind.data.local.dao.IronMindDao
import com.sanket_satpute_20.ironmind.data.local.entity.EventEntity
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.logging.IronLogger
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.Event
import com.sanket_satpute_20.ironmind.domain.model.EventType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeIronLogger : IronLogger {
    val loggedEvents = mutableListOf<Map<String, Any?>>()

    override fun logLifecycle(component: String, event: String, parameters: Map<String, Any?>) {
        loggedEvents.add(
            mapOf(
                "component" to component,
                "action" to event
            ) + parameters
        )
    }
}

class EventRepositoryImplTest {

    private val failingDao = object : IronMindDao {
        override fun insertEvent(event: EventEntity) {
            throw Exception("Database full")
        }

        // Stubs for all other methods
        override fun insertUserProfile(profile: com.sanket_satpute_20.ironmind.data.local.entity.UserProfileEntity) {}
        override fun getUserProfile(id: String) = null
        override fun getLocalUserProfile() = null
        override fun insertGoal(goal: com.sanket_satpute_20.ironmind.data.local.entity.GoalEntity) {}
        override fun getGoal(id: String) = null
        override fun getGoalsForUser(userId: String) = emptyList<com.sanket_satpute_20.ironmind.data.local.entity.GoalEntity>()
        override fun insertPlan(plan: com.sanket_satpute_20.ironmind.data.local.entity.PlanEntity) {}
        override fun getPlan(id: String) = null
        override fun getPlansForGoal(goalId: String) = emptyList<com.sanket_satpute_20.ironmind.data.local.entity.PlanEntity>()
        override fun insertTask(task: com.sanket_satpute_20.ironmind.data.local.entity.TaskEntity) {}
        override fun getTask(id: String) = null
        override fun getTasksForPlan(planId: String) = emptyList<com.sanket_satpute_20.ironmind.data.local.entity.TaskEntity>()
        override fun getTasksForGoal(goalId: String) = emptyList<com.sanket_satpute_20.ironmind.data.local.entity.TaskEntity>()
        override fun insertCommitment(commitment: com.sanket_satpute_20.ironmind.data.local.entity.CommitmentEntity) {}
        override fun getCommitment(id: String) = null
        override fun getCommitmentsForUser(userId: String) = emptyList<com.sanket_satpute_20.ironmind.data.local.entity.CommitmentEntity>()
        override fun getActiveCommitmentsForUser(userId: String, statuses: List<String>) = emptyList<com.sanket_satpute_20.ironmind.data.local.entity.CommitmentEntity>()
        override fun getCommitmentsForGoal(goalId: String) = emptyList<com.sanket_satpute_20.ironmind.data.local.entity.CommitmentEntity>()
        override fun getCommitmentsForDateRange(userId: String, startTime: Long, endTime: Long) = emptyList<com.sanket_satpute_20.ironmind.data.local.entity.CommitmentEntity>()
        override fun getCommitmentsForPlan(planId: String) = emptyList<com.sanket_satpute_20.ironmind.data.local.entity.CommitmentEntity>()
        override fun getCommitmentsForTask(taskId: String) = emptyList<com.sanket_satpute_20.ironmind.data.local.entity.CommitmentEntity>()
        override fun insertOutcome(outcome: com.sanket_satpute_20.ironmind.data.local.entity.OutcomeEntity) {}
        override fun getOutcome(id: String) = null
        override fun getOutcomeForSource(sourceEntityId: String) = null
        override fun insertReflection(reflection: com.sanket_satpute_20.ironmind.data.local.entity.ReflectionEntity) {}
        override fun getReflection(id: String) = null
        override fun getReflectionsForDateRange(userId: String, startTime: Long, endTime: Long) = emptyList<com.sanket_satpute_20.ironmind.data.local.entity.ReflectionEntity>()
        override fun insertProtectionRule(rule: com.sanket_satpute_20.ironmind.data.local.entity.ProtectionRuleEntity) {}
        override fun getProtectionRule(id: String) = null
        override fun getProtectionRulesForUser(userId: String) = emptyList<com.sanket_satpute_20.ironmind.data.local.entity.ProtectionRuleEntity>()
        override fun getProtectionRuleCount(): Flow<Int> = kotlinx.coroutines.flow.emptyFlow()
        override fun insertProtectionSession(session: com.sanket_satpute_20.ironmind.data.local.entity.ProtectionSessionEntity) {}
        override fun getProtectionSession(id: String) = null
        override fun getActiveProtectionSessionsForUser(userId: String) = emptyList<com.sanket_satpute_20.ironmind.data.local.entity.ProtectionSessionEntity>()
        override fun getEventsForEntity(entityId: String) = emptyList<EventEntity>()
        override fun getEventsForUser(userId: String) = emptyList<EventEntity>()
        override fun getEventCount(): Flow<Int> = kotlinx.coroutines.flow.emptyFlow()
        override fun insertMemory(memory: com.sanket_satpute_20.ironmind.data.local.entity.MemoryEntity) {}
        override fun getMemoryById(id: String) = null
        override fun getMemoriesForUser(userId: String) = emptyList<com.sanket_satpute_20.ironmind.data.local.entity.MemoryEntity>()
        override fun getActiveMemoriesForUser(userId: String) = emptyList<com.sanket_satpute_20.ironmind.data.local.entity.MemoryEntity>()
        override fun getMemoryCount(): Flow<Int> = kotlinx.coroutines.flow.emptyFlow()
        override fun searchGoals(userId: String, query: String) = emptyList<com.sanket_satpute_20.ironmind.data.local.entity.GoalEntity>()
        override fun searchCommitments(userId: String, query: String) = emptyList<com.sanket_satpute_20.ironmind.data.local.entity.CommitmentEntity>()
        override fun searchReflections(userId: String, query: String) = emptyList<com.sanket_satpute_20.ironmind.data.local.entity.ReflectionEntity>()
        override fun searchMemories(userId: String, query: String) = emptyList<com.sanket_satpute_20.ironmind.data.local.entity.MemoryEntity>()
        override fun searchEvents(userId: String, query: String) = emptyList<EventEntity>()
        override fun getEvent(id: String) = null
        override fun getEventsForDateRange(userId: String, startTime: Long, endTime: Long) = emptyList<EventEntity>()
        override fun getMemoriesForDateRange(userId: String, startTime: Long, endTime: Long) = emptyList<com.sanket_satpute_20.ironmind.data.local.entity.MemoryEntity>()
        override fun deleteUserProfile(userId: String) {}
        override fun deleteGoalsForUser(userId: String) {}
        override fun deletePlansForUser(userId: String) {}
        override fun deleteTasksForUser(userId: String) {}
        override fun deleteCommitmentsForUser(userId: String) {}
        override fun deleteReflectionsForUser(userId: String) {}
        override fun deleteProtectionRulesForUser(userId: String) {}
        override fun deleteProtectionSessionsForUser(userId: String) {}
        override fun deleteEventsForUser(userId: String) {}
        override fun deleteMemoriesForUser(userId: String) {}
        override fun deleteForgottenMemoriesOlderThan(userId: String, thresholdTime: Long) {}
    }

    @Test
    fun `saveEvent logs PERSIST_FAILURE and avoids logging sensitive payload`() = runTest {
        val logger = FakeIronLogger()
        val repository = EventRepositoryImpl(failingDao, logger)

        val event = Event(
            id = "evt-1",
            userId = "usr-1",
            type = EventType.REFLECTION_CREATED,
            entityType = "REFLECTION",
            entityId = "ref-1",
            occurredAt = 1000L,
            recordedAt = 1000L,
            source = EntitySource.USER,
            metadata = "This is a highly sensitive private reflection note."
        )

        val result = repository.saveEvent(event)

        assertTrue(result is Result.Failure)

        assertEquals(1, logger.loggedEvents.size)
        val logMap = logger.loggedEvents[0]
        assertEquals("Event", logMap["component"])
        assertEquals("PERSIST_FAILURE", logMap["action"])
        assertEquals("REFLECTION_CREATED", logMap["eventType"])
        assertEquals("USER", logMap["source"])

        // Assert sensitive payload is NOT in the log
        assertTrue(!logMap.values.any { it.toString().contains("sensitive private reflection note") })
    }
}
