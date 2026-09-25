package com.sanket_satpute_20.ironmind.domain.usecase.goal

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.GoalStatus
import com.sanket_satpute_20.ironmind.testutil.fake.FakeClock
import com.sanket_satpute_20.ironmind.testutil.fake.FakeEventRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeGoalRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeIdGenerator
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import com.sanket_satpute_20.ironmind.domain.model.EventType
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GoalCreationTest {

    private lateinit var repository: FakeGoalRepository
    private lateinit var clock: FakeClock
    private lateinit var idGenerator: FakeIdGenerator
    private lateinit var eventRepository: FakeEventRepository

    private lateinit var createGoalUseCase: CreateGoalUseCase
    private lateinit var editGoalUseCase: EditGoalUseCase

    @Before
    fun setup() {
        repository = FakeGoalRepository()
        clock = FakeClock()
        idGenerator = FakeIdGenerator()
        eventRepository = FakeEventRepository()

        createGoalUseCase = CreateGoalUseCase(repository, idGenerator, clock, eventRepository)
        editGoalUseCase = EditGoalUseCase(repository, clock, idGenerator, eventRepository)
    }

    @Test
    fun `create goal succeeds with valid input`() = runTest {
        idGenerator.nextId = "goal-1"

        val result = createGoalUseCase(
            userId = "user-1",
            title = "Get fit",
            description = "Train 3 times a week",
            why = "Health",
            importance = 8,
            targetAt = null
        )

        assertTrue(result is Result.Success)
        val goal = (result as Result.Success).data

        assertEquals("goal-1", goal.id)
        assertEquals("user-1", goal.userId)
        assertEquals("Get fit", goal.title)
        assertEquals(GoalStatus.ACTIVE, goal.status)
        assertEquals(clock.currentTimeMillis(), goal.createdAt)

        val events = eventRepository.events.values.toList()
        assertEquals(1, events.size)
        assertEquals(EventType.GOAL_CREATED, events[0].type)
        assertEquals(goal.id, events[0].entityId)
    }

    @Test
    fun `create goal succeeds even if event persistence fails`() = runTest {
        eventRepository.shouldFail = true

        val result = createGoalUseCase(
            userId = "user-1",
            title = "Get fit",
            description = "Train 3 times a week",
            why = "Health",
            importance = 8,
            targetAt = null
        )

        assertTrue(result is Result.Success)
        val goal = (result as Result.Success).data
        assertNotNull(repository.getGoal(goal.id))
        assertTrue(eventRepository.events.isEmpty())
    }

    @Test
    fun `create goal fails with blank title`() = runTest {
        val result = createGoalUseCase(
            userId = "user-1",
            title = "   ",
            description = "Desc",
            why = "Why",
            importance = 5,
            targetAt = null
        )

        assertTrue(result is Result.Failure)
        assertEquals("Title cannot be blank", (result as Result.Failure).error.message)
    }

    @Test
    fun `create goal fails with invalid importance`() = runTest {
        val result = createGoalUseCase(
            userId = "user-1",
            title = "Valid Title",
            description = "Desc",
            why = "Why",
            importance = 11, // Invalid
            targetAt = null
        )

        assertTrue(result is Result.Failure)
    }

    @Test
    fun `edit goal updates fields and timestamp`() = runTest {
        // Create initial goal
        val createResult = createGoalUseCase(
            userId = "user-1",
            title = "Original Title",
            description = "Original Desc",
            why = "Original Why",
            importance = 5,
            targetAt = null
        )
        val initialGoal = (createResult as Result.Success).data

        // Advance time
        clock.advanceTimeBy(1000)

        val editResult = editGoalUseCase(
            userId = "user-1",
            goalId = initialGoal.id,
            title = "New Title",
            description = null, // Should remain original
            why = "New Why",
            importance = 9,
            targetAt = 12345L
        )

        assertTrue(editResult is Result.Success)
        val editedGoal = (editResult as Result.Success).data

        assertEquals("New Title", editedGoal.title)
        assertEquals("Original Desc", editedGoal.description)
        assertEquals("New Why", editedGoal.why)
        assertEquals(9, editedGoal.importance)
        assertEquals(12345L, editedGoal.targetAt)

        // Assert timestamp updated
        assertTrue(editedGoal.updatedAt > initialGoal.updatedAt)
    }
}
