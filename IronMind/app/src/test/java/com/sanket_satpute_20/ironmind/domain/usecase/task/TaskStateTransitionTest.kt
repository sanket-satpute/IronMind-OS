package com.sanket_satpute_20.ironmind.domain.usecase.task

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.EventType
import com.sanket_satpute_20.ironmind.domain.model.TaskStatus
import com.sanket_satpute_20.ironmind.testutil.fake.FakeClock
import com.sanket_satpute_20.ironmind.testutil.fake.FakeIdGenerator
import com.sanket_satpute_20.ironmind.testutil.fake.FakeTaskRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeEventRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TaskStateTransitionTest {

    private lateinit var repository: FakeTaskRepository
    private lateinit var clock: FakeClock
    private lateinit var idGenerator: FakeIdGenerator
    private lateinit var eventRepository: FakeEventRepository
    
    private lateinit var createTaskUseCase: CreateTaskUseCase
    private lateinit var updateTaskStatusUseCase: UpdateTaskStatusUseCase

    @Before
    fun setup() {
        repository = FakeTaskRepository()
        clock = FakeClock()
        idGenerator = FakeIdGenerator()
        eventRepository = FakeEventRepository()
        
        createTaskUseCase = CreateTaskUseCase(repository, idGenerator, clock, eventRepository)
        updateTaskStatusUseCase = UpdateTaskStatusUseCase(repository, clock, eventRepository, idGenerator)
    }

    @Test
    fun `updating status to COMPLETED sets completedAt`() = runTest {
        val task = (createTaskUseCase("user-1", "goal-1", "plan-1", "Title", "Desc", 1) as Result.Success).data
        assertNull(task.completedAt)
        
        clock.advanceTimeBy(1000)
        
        val updateResult = updateTaskStatusUseCase(task.id, TaskStatus.COMPLETED)
        assertTrue(updateResult is Result.Success)
        val updatedTask = (updateResult as Result.Success).data
        
        assertEquals(TaskStatus.COMPLETED, updatedTask.status)
        assertEquals(clock.currentTimeMillis(), updatedTask.completedAt)
        assertNull(updatedTask.postponedAt)
    }

    @Test
    fun `updating status to POSTPONED sets postponedAt but not completedAt`() = runTest {
        val task = (createTaskUseCase("user-1", "goal-1", "plan-1", "Title", "Desc", 1) as Result.Success).data
        
        clock.advanceTimeBy(1000)
        
        val updateResult = updateTaskStatusUseCase(task.id, TaskStatus.POSTPONED)
        assertTrue(updateResult is Result.Success)
        val updatedTask = (updateResult as Result.Success).data
        
        assertEquals(TaskStatus.POSTPONED, updatedTask.status)
        assertNull(updatedTask.completedAt)
        assertEquals(clock.currentTimeMillis(), updatedTask.postponedAt)
    }

    @Test
    fun `updating from FAILED to IN_PROGRESS clears completedAt`() = runTest {
        val task = (createTaskUseCase("user-1", "goal-1", "plan-1", "Title", "Desc", 1) as Result.Success).data
        
        clock.advanceTimeBy(1000)
        val failedTask = (updateTaskStatusUseCase(task.id, TaskStatus.FAILED) as Result.Success).data
        assertNotNull(failedTask.completedAt) // FAILED marks it finished in this domain model implementation
        
        clock.advanceTimeBy(1000)
        val activeTask = (updateTaskStatusUseCase(task.id, TaskStatus.IN_PROGRESS) as Result.Success).data
        
        assertEquals(TaskStatus.IN_PROGRESS, activeTask.status)
        assertNull(activeTask.completedAt)
    }

    @Test
    fun `valid lifecycle transitions emit exact events with ownership and states`() = runTest {
        val task = (createTaskUseCase("user-1", "goal-1", "plan-1", "Title", "Desc", 2, dueAt = 50_000L, scheduledAt = 40_000L) as Result.Success).data
        eventRepository.events.clear()

        clock.advanceTimeBy(1_000L)
        val started = updateTaskStatusUseCase(task.id, TaskStatus.IN_PROGRESS)
        assertTrue(started is Result.Success)

        clock.advanceTimeBy(1_000L)
        val postponed = updateTaskStatusUseCase(task.id, TaskStatus.POSTPONED)
        assertTrue(postponed is Result.Success)

        clock.advanceTimeBy(1_000L)
        val completed = updateTaskStatusUseCase(task.id, TaskStatus.COMPLETED)
        assertTrue(completed is Result.Success)

        val events = eventRepository.events.values.sortedBy { it.occurredAt }
        assertEquals(3, events.size)

        assertEquals(EventType.TASK_STARTED, events[0].type)
        assertEquals("user-1", events[0].userId)
        assertEquals(task.id, events[0].entityId)
        assertEquals("TASK", events[0].entityType)
        assertEquals("PENDING", events[0].previousState)
        assertEquals("IN_PROGRESS", events[0].newState)

        assertEquals(EventType.TASK_POSTPONED, events[1].type)
        assertEquals("IN_PROGRESS", events[1].previousState)
        assertEquals("POSTPONED", events[1].newState)
        assertTrue(events[1].metadata!!.contains("previousDueAt=50000"))
        assertTrue(events[1].metadata!!.contains("postponedAt=${events[1].occurredAt}"))

        assertEquals(EventType.TASK_COMPLETED, events[2].type)
        assertEquals("POSTPONED", events[2].previousState)
        assertEquals("COMPLETED", events[2].newState)
        assertTrue(events[2].metadata!!.contains("completedAt=${events[2].occurredAt}"))
    }

    @Test
    fun `cancelled transition emits TASK_CANCELLED`() = runTest {
        val task = (createTaskUseCase("user-1", null, null, "Cancel Me", "Desc", 1) as Result.Success).data
        eventRepository.events.clear()

        clock.advanceTimeBy(500L)
        val res = updateTaskStatusUseCase(task.id, TaskStatus.CANCELLED)
        assertTrue(res is Result.Success)

        val events = eventRepository.events.values.toList()
        assertEquals(1, events.size)
        assertEquals(EventType.TASK_CANCELLED, events[0].type)
        assertEquals("PENDING", events[0].previousState)
        assertEquals("CANCELLED", events[0].newState)
    }

    @Test
    fun `no-op status update and repeated completion do not emit duplicate events`() = runTest {
        val task = (createTaskUseCase("user-1", null, null, "Idempotent", "Desc", 1) as Result.Success).data
        eventRepository.events.clear()

        clock.advanceTimeBy(500L)
        assertTrue(updateTaskStatusUseCase(task.id, TaskStatus.PENDING) is Result.Success)
        assertTrue(eventRepository.events.isEmpty())

        clock.advanceTimeBy(500L)
        assertTrue(updateTaskStatusUseCase(task.id, TaskStatus.IN_PROGRESS) is Result.Success)
        assertEquals(1, eventRepository.events.size)

        clock.advanceTimeBy(500L)
        assertTrue(updateTaskStatusUseCase(task.id, TaskStatus.IN_PROGRESS) is Result.Success)
        assertEquals(1, eventRepository.events.size)

        clock.advanceTimeBy(500L)
        assertTrue(updateTaskStatusUseCase(task.id, TaskStatus.COMPLETED) is Result.Success)
        assertEquals(2, eventRepository.events.size)

        clock.advanceTimeBy(500L)
        assertTrue(updateTaskStatusUseCase(task.id, TaskStatus.COMPLETED) is Result.Success)
        assertEquals(2, eventRepository.events.size)
    }

    @Test
    fun `event persistence failure rolls back task state and returns failure`() = runTest {
        val task = (createTaskUseCase("user-1", null, null, "Rollback Task", "Desc", 1) as Result.Success).data
        eventRepository.events.clear()
        eventRepository.shouldFail = true

        clock.advanceTimeBy(1_000L)
        val result = updateTaskStatusUseCase(task.id, TaskStatus.COMPLETED)
        assertTrue(result is Result.Failure)

        val persisted = (repository.getTask(task.id) as Result.Success).data!!
        assertEquals(TaskStatus.PENDING, persisted.status)
        assertNull(persisted.completedAt)
        assertEquals(task.updatedAt, persisted.updatedAt)
        assertTrue(eventRepository.events.isEmpty())
    }
}
