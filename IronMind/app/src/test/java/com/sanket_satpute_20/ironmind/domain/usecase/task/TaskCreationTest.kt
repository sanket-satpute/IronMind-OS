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
    private lateinit var deleteTaskUseCase: DeleteTaskUseCase

    @Before
    fun setup() {
        repository = FakeTaskRepository()
        clock = FakeClock()
        idGenerator = FakeIdGenerator()
        eventRepository = FakeEventRepository()

        createTaskUseCase = CreateTaskUseCase(repository, idGenerator, clock, eventRepository)
        editTaskUseCase = EditTaskUseCase(repository, clock, eventRepository, idGenerator)
        deleteTaskUseCase = DeleteTaskUseCase(repository)
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

    @Test
    fun `create task with dueAt succeeds and sets dueAt`() = runTest {
        idGenerator.nextId = "task-due-1"

        val result = createTaskUseCase(
            userId = "user-1",
            goalId = "goal-1",
            planId = "plan-1",
            title = "Due Task",
            description = "Desc",
            priority = 2,
            dueAt = 1731628800000L
        )

        assertTrue(result is Result.Success)
        val task = (result as Result.Success).data
        assertEquals("task-due-1", task.id)
        assertEquals(1731628800000L, task.dueAt)
    }

    @Test
    fun `edit task updates and clears dueAt correctly`() = runTest {
        val task = (createTaskUseCase(
            userId = "user-1",
            goalId = "goal-1",
            planId = "plan-1",
            title = "Task",
            description = "Desc",
            priority = 1,
            dueAt = 5000L
        ) as Result.Success).data

        // 1. Update dueAt
        clock.advanceTimeBy(1000)
        val editResult = editTaskUseCase(
            taskId = task.id,
            dueAt = 10000L,
            clearDueAt = false
        )
        assertTrue(editResult is Result.Success)
        val editedTask = (editResult as Result.Success).data
        assertEquals(10000L, editedTask.dueAt)

        // 2. Clear dueAt
        clock.advanceTimeBy(1000)
        val clearResult = editTaskUseCase(
            taskId = task.id,
            dueAt = null,
            clearDueAt = true
        )
        assertTrue(clearResult is Result.Success)
        val clearedTask = (clearResult as Result.Success).data
        org.junit.Assert.assertNull(clearedTask.dueAt)

        // 3. Omit dueAt without clearDueAt keeps null
        clock.advanceTimeBy(1000)
        val omitResult = editTaskUseCase(
            taskId = task.id,
            title = "Updated Title",
            dueAt = null,
            clearDueAt = false
        )
        assertTrue(omitResult is Result.Success)
        val omittedTask = (omitResult as Result.Success).data
        assertEquals("Updated Title", omittedTask.title)
        org.junit.Assert.assertNull(omittedTask.dueAt)
    }

    @Test
    fun `delete task succeeds when task exists and ownership matches`() = runTest {
        val task = (createTaskUseCase("user-1", "goal-1", "plan-1", "Task to Delete", "Desc", 1) as Result.Success).data
        assertNotNull(repository.getTask(task.id))

        val deleteResult = deleteTaskUseCase("user-1", task.id)
        assertTrue(deleteResult is Result.Success)
        org.junit.Assert.assertNull((repository.getTask(task.id) as Result.Success).data)
    }

    @Test
    fun `delete task fails with blank userId or taskId`() = runTest {
        val result1 = deleteTaskUseCase("", "task-1")
        assertTrue(result1 is Result.Failure)
        assertEquals("User ID cannot be blank", (result1 as Result.Failure).error.message)

        val result2 = deleteTaskUseCase("user-1", "")
        assertTrue(result2 is Result.Failure)
        assertEquals("Task ID cannot be blank", (result2 as Result.Failure).error.message)
    }

    @Test
    fun `forward dueAt and scheduledAt reschedules emit TASK_POSTPONED with schedule metadata`() = runTest {
        val task = (createTaskUseCase(
            userId = "user-1",
            goalId = "goal-1",
            planId = "plan-1",
            title = "Scheduled Task",
            description = "Desc",
            priority = 2,
            scheduledAt = 10_000L,
            dueAt = 20_000L
        ) as Result.Success).data
        eventRepository.events.clear()

        clock.advanceTimeBy(1_000L)
        val dueRes = editTaskUseCase(taskId = task.id, dueAt = 30_000L)
        assertTrue(dueRes is Result.Success)

        clock.advanceTimeBy(1_000L)
        val schedRes = editTaskUseCase(taskId = task.id, scheduledAt = 15_000L)
        assertTrue(schedRes is Result.Success)

        val events = eventRepository.events.values.sortedBy { it.occurredAt }
        assertEquals(2, events.size)

        assertEquals(EventType.TASK_POSTPONED, events[0].type)
        assertEquals("user-1", events[0].userId)
        assertEquals(task.id, events[0].entityId)
        assertTrue(events[0].metadata!!.contains("reason=forward_reschedule"))
        assertTrue(events[0].metadata!!.contains("previousDueAt=20000"))
        assertTrue(events[0].metadata!!.contains("newDueAt=30000"))

        assertEquals(EventType.TASK_POSTPONED, events[1].type)
        assertTrue(events[1].metadata!!.contains("previousScheduledAt=10000"))
        assertTrue(events[1].metadata!!.contains("newScheduledAt=15000"))
    }

    @Test
    fun `non-postponement and no-op edits do not emit TASK_POSTPONED`() = runTest {
        val task = (createTaskUseCase(
            userId = "user-1",
            goalId = null,
            planId = null,
            title = "Plain Task",
            description = "Desc",
            priority = 1,
            scheduledAt = null,
            dueAt = null
        ) as Result.Success).data
        eventRepository.events.clear()

        // 1. Metadata/title edit
        clock.advanceTimeBy(500L)
        assertTrue(editTaskUseCase(taskId = task.id, title = "Renamed", priority = 3) is Result.Success)

        // 2. Setting initial dueAt from null is scheduling, not postponement
        clock.advanceTimeBy(500L)
        assertTrue(editTaskUseCase(taskId = task.id, dueAt = 50_000L) is Result.Success)

        // 3. Moving dueAt earlier is not postponement
        clock.advanceTimeBy(500L)
        assertTrue(editTaskUseCase(taskId = task.id, dueAt = 40_000L) is Result.Success)

        // 4. Clearing dueAt is not postponement
        clock.advanceTimeBy(500L)
        assertTrue(editTaskUseCase(taskId = task.id, clearDueAt = true) is Result.Success)

        // 5. No-op edit does not write or emit events
        clock.advanceTimeBy(500L)
        assertTrue(editTaskUseCase(taskId = task.id, title = "Renamed") is Result.Success)

        assertTrue(eventRepository.events.isEmpty())
    }

    @Test
    fun `edit task rolls back forward reschedule when event persistence fails`() = runTest {
        val task = (createTaskUseCase(
            userId = "user-1",
            goalId = null,
            planId = null,
            title = "Rollback Schedule",
            description = "Desc",
            priority = 2,
            dueAt = 20_000L
        ) as Result.Success).data
        eventRepository.events.clear()
        eventRepository.shouldFail = true

        clock.advanceTimeBy(1_000L)
        val result = editTaskUseCase(taskId = task.id, dueAt = 90_000L)
        assertTrue(result is Result.Failure)

        val persisted = (repository.getTask(task.id) as Result.Success).data!!
        assertEquals(20_000L, persisted.dueAt)
        assertEquals(task.updatedAt, persisted.updatedAt)
        assertTrue(eventRepository.events.isEmpty())
    }
}
