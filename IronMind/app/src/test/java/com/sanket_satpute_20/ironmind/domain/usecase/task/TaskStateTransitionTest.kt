package com.sanket_satpute_20.ironmind.domain.usecase.task

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.TaskStatus
import com.sanket_satpute_20.ironmind.testutil.fake.FakeClock
import com.sanket_satpute_20.ironmind.testutil.fake.FakeIdGenerator
import com.sanket_satpute_20.ironmind.testutil.fake.FakeTaskRepository
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
    
    private lateinit var createTaskUseCase: CreateTaskUseCase
    private lateinit var updateTaskStatusUseCase: UpdateTaskStatusUseCase

    @Before
    fun setup() {
        repository = FakeTaskRepository()
        clock = FakeClock()
        idGenerator = FakeIdGenerator()
        
        createTaskUseCase = CreateTaskUseCase(repository, idGenerator, clock)
        updateTaskStatusUseCase = UpdateTaskStatusUseCase(repository, clock)
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
}
