package com.sanket_satpute_20.ironmind.domain.usecase.search

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Commitment
import com.sanket_satpute_20.ironmind.domain.model.Goal
import com.sanket_satpute_20.ironmind.domain.model.GoalStatus
import com.sanket_satpute_20.ironmind.domain.model.Reflection
import com.sanket_satpute_20.ironmind.domain.model.Memory
import com.sanket_satpute_20.ironmind.domain.model.Event
import com.sanket_satpute_20.ironmind.domain.model.EventType
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.CommitmentStatus
import com.sanket_satpute_20.ironmind.testutil.fake.FakeCommitmentRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeEventRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeGoalRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeMemoryRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeReflectionRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LocalSearchUseCaseTest {

    private lateinit var goalRepository: FakeGoalRepository
    private lateinit var commitmentRepository: FakeCommitmentRepository
    private lateinit var reflectionRepository: FakeReflectionRepository
    private lateinit var memoryRepository: FakeMemoryRepository
    private lateinit var eventRepository: FakeEventRepository
    private lateinit var useCase: LocalSearchUseCase

    @Before
    fun setup() {
        goalRepository = FakeGoalRepository()
        commitmentRepository = FakeCommitmentRepository()
        reflectionRepository = FakeReflectionRepository()
        memoryRepository = FakeMemoryRepository()
        eventRepository = FakeEventRepository()

        useCase = LocalSearchUseCase(
            goalRepository,
            commitmentRepository,
            reflectionRepository,
            memoryRepository,
            eventRepository
        )
    }

    @Test
    fun `search with empty query returns empty result`() = runTest {
        val result = useCase("user-1", "")
        assertTrue(result is Result.Success)
        val data = (result as Result.Success).data
        assertTrue(data.goals.isEmpty())
        assertTrue(data.commitments.isEmpty())
        assertTrue(data.reflections.isEmpty())
        assertTrue(data.memories.isEmpty())
        assertTrue(data.events.isEmpty())
    }

    @Test
    fun `search with matching query returns matching entities`() = runTest {
        val goal = Goal(
            id = "goal-1",
            userId = "user-1",
            ambitionId = null,
            title = "Fitness plan",
            description = "Get strong",
            why = "Health",
            importance = 10,
            status = GoalStatus.ACTIVE,
            targetAt = 0L,
            startedAt = 0L,
            completedAt = null,
            createdAt = 0L,
            updatedAt = 0L
        )
        goalRepository.saveGoal(goal)

        val commitment = Commitment(
            id = "comm-1",
            userId = "user-1",
            goalId = "goal-1",
            planId = null,
            taskId = null,
            parentCommitmentId = null,
            title = "Run 5k",
            description = "Morning run",
            committedAt = 0L,
            scheduledStartAt = null,
            scheduledEndAt = null,
            status = CommitmentStatus.COMMITTED,
            priority = 1,
            source = EntitySource.USER,
            createdAt = 0L,
            updatedAt = 0L,
            startedAt = null,
            completedAt = null,
            postponedAt = null,
            missedAt = null,
            recoveredAt = null
        )
        commitmentRepository.saveCommitment(commitment)

        val reflection = Reflection(
            id = "ref-1",
            userId = "user-1",
            targetEntityId = null,
            targetEntityType = null,
            content = "Running was hard today",
            sentiment = null,
            createdAt = 0L,
            schemaVersion = 1
        )
        reflectionRepository.saveReflection(reflection)

        val result = useCase("user-1", "Run")
        assertTrue(result is Result.Success)
        val data = (result as Result.Success).data
        
        // "Run" doesn't match goal
        assertTrue(data.goals.isEmpty())
        // "Run" matches commitment title
        assertEquals(1, data.commitments.size)
        assertEquals("comm-1", data.commitments[0].id)
        // "Run" matches reflection content
        assertEquals(1, data.reflections.size)
        assertEquals("ref-1", data.reflections[0].id)
    }

    @Test
    fun `search limits to correct user`() = runTest {
        val goal1 = Goal(
            id = "goal-1",
            userId = "user-1",
            ambitionId = null,
            title = "Run marathon",
            description = "",
            why = "",
            importance = 10,
            status = GoalStatus.ACTIVE,
            targetAt = 0L,
            startedAt = 0L,
            completedAt = null,
            createdAt = 0L,
            updatedAt = 0L
        )
        val goal2 = Goal(
            id = "goal-2",
            userId = "user-2", // different user
            ambitionId = null,
            title = "Run marathon",
            description = "",
            why = "",
            importance = 10,
            status = GoalStatus.ACTIVE,
            targetAt = 0L,
            startedAt = 0L,
            completedAt = null,
            createdAt = 0L,
            updatedAt = 0L
        )
        goalRepository.saveGoal(goal1)
        goalRepository.saveGoal(goal2)

        val result = useCase("user-1", "Run")
        assertTrue(result is Result.Success)
        val data = (result as Result.Success).data
        
        assertEquals(1, data.goals.size)
        assertEquals("user-1", data.goals[0].userId)
    }
}
