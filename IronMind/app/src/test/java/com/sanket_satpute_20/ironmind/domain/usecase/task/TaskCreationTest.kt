package com.sanket_satpute_20.ironmind.domain.usecase.task

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.TaskStatus
import com.sanket_satpute_20.ironmind.testutil.fake.FakeClock
import com.sanket_satpute_20.ironmind.testutil.fake.FakeIdGenerator
import com.sanket_satpute_20.ironmind.testutil.fake.FakeTaskRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeEventRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import com.sanket_satpute_20.ironmind.domain.model.EventType
import org.junit.Before
import org.junit.Test

class TaskCreationTest {

    private lateinit var repository: FakeTaskRepository
    private lateinit var clock: FakeClock
    private lateinit var idGenerator: FakeIdGenerator
    private lateinit var eventRepository: FakeEventRepository

    private lateinit var createTaskUseCase: CreateTaskUseCase
    private lateinit var editTaskUseCase: EditTaskUseCase

    @Before
    fun setup() {
        repository = FakeTaskRepository()
        clock = FakeClock()
        idGenerator = FakeIdGenerator()
        eventRepository = FakeEventRepository()

        createTaskUseCase = CreateTaskUseCase(repository, idGenerator, clock, eventRepository)
        editTaskUseCase = EditTaskUseCase(repository, clock)
    }

    @Test
    fun `create task succeeds with valid input`() = runTest {
        idGenerator.nextId = "task-1"

        val result = createTaskUseCase(
            userId = "user-1",
            goalId = "goal-1",
            planId = "plan-1",
            title = "My Task",
            description = "Task desc",
            priority = 1
        )

        assertTrue(result is Result.Success)
        val task = (result as Result.Success).data

        assertEquals("task-1", task.id)
        assertEquals("user-1", task.userId)
        assertEquals("goal-1", task.goalId)
        assertEquals("plan-1", task.planId)
        assertEquals("My Task", task.title)
        assertEquals(TaskStatus.PENDING, task.status)
        assertEquals(1, task.priority)
        assertEquals(EntitySource.USER, task.source)
        assertEquals(clock.currentTimeMillis(), task.createdAt)

        val events = eventRepository.events.values.toList()
        assertEquals(1, events.size)
        assertEquals(EventType.TASK_CREATED, events[0].type)
        assertEquals(task.id, events[0].entityId)
    }

    @Test
    fun `create task succeeds even if event persistence fails`() = runTest {
        eventRepository.shouldFail = true

        val result = createTaskUseCase(
            userId = "user-1",
            goalId = "goal-1",
            planId = "plan-1",
            title = "My Task",
            description = "Task desc",
            priority = 1
        )

        assertTrue(result is Result.Success)
        val task = (result as Result.Success).data
        assertNotNull(repository.getTask(task.id))
        assertTrue(eventRepository.events.isEmpty())
    }

    @Test
    fun `create task fails with blank title`() = runTest {
        val result = createTaskUseCase(
            userId = "user-1",
            goalId = null,
            planId = null,
            title = "",
            description = "Task desc",
            priority = 1
        )

        assertTrue(result is Result.Failure)
        assertEquals("Title cannot be blank", (result as Result.Failure).error.message)
    }

    @Test
    fun `edit task updates correct fields`() = runTest {
        val task = (createTaskUseCase("user-1", "goal-1", "plan-1", "Task", "Desc", 1) as Result.Success).data

        clock.advanceTimeBy(1000)

        val editResult = editTaskUseCase(
            taskId = task.id,
            title = "New Title",
            description = null,
            priority = 2,
            estimatedDurationMinutes = 60,
            scheduledAt = null,
            dueAt = null
        )

        assertTrue(editResult is Result.Success)
        val editedTask = (editResult as Result.Success).data

        assertEquals("New Title", editedTask.title)
        assertEquals("Desc", editedTask.description)
        assertEquals(2, editedTask.priority)
        assertEquals(60, editedTask.estimatedDurationMinutes)
        assertTrue(editedTask.updatedAt > task.updatedAt)
    }
}
