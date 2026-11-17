package com.sanket_satpute_20.ironmind.domain.usecase.task

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.Plan
import com.sanket_satpute_20.ironmind.domain.model.PlanStatus
import com.sanket_satpute_20.ironmind.domain.model.Task
import com.sanket_satpute_20.ironmind.domain.model.TaskStatus
import com.sanket_satpute_20.ironmind.testutil.fake.FakeClock
import com.sanket_satpute_20.ironmind.testutil.fake.FakePlanRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeTaskRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class GetDueTasksUseCaseTest {

    private lateinit var taskRepository: FakeTaskRepository
    private lateinit var planRepository: FakePlanRepository
    private lateinit var clock: FakeClock
    private lateinit var useCase: GetDueTasksUseCase

    private val zoneId = ZoneId.of("UTC")
    // Use fixed reference date: 2026-11-06 12:00:00 UTC
    private val fixedDate = LocalDate.of(2026, 11, 6)
    private val referenceTime = fixedDate.atTime(12, 0, 0).atZone(zoneId).toInstant().toEpochMilli()
    private val startOfToday = fixedDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
    private val startOfTomorrow = fixedDate.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()

    @Before
    fun setUp() {
        taskRepository = FakeTaskRepository()
        planRepository = FakePlanRepository()
        clock = FakeClock(referenceTime)
        useCase = GetDueTasksUseCase(
            taskRepository = taskRepository,
            planRepository = planRepository,
            clock = clock
        )
    }

    private fun createTask(
        id: String,
        userId: String = "user-1",
        planId: String? = "plan-1",
        title: String = "Task $id",
        status: TaskStatus = TaskStatus.PENDING,
        dueAt: Long? = null
    ): Task = Task(
        id = id,
        userId = userId,
        goalId = "goal-1",
        planId = planId,
        title = title,
        description = "Description for $id",
        status = status,
        priority = 1,
        dueAt = dueAt,
        createdAt = referenceTime,
        updatedAt = referenceTime,
        source = EntitySource.USER
    )

    @Test
    fun `task due today is classified under dueToday`() = runTest {
        val taskDueToday = createTask("t1", dueAt = startOfToday + 3600_000L) // 1:00 AM UTC today
        taskRepository.saveTask(taskDueToday)

        val result = useCase(userId = "user-1", referenceTimeMillis = referenceTime, zoneId = zoneId)
        assertTrue(result is Result.Success)
        val data = (result as Result.Success).data

        assertEquals(1, data.dueToday.size)
        assertEquals("t1", data.dueToday[0].task.id)
        assertFalse(data.dueToday[0].isOverdue)
        assertTrue(data.overdue.isEmpty())
    }

    @Test
    fun `past-due incomplete task is classified under overdue`() = runTest {
        val taskYesterday = createTask("t-old", dueAt = startOfToday - 1000L) // 1 second before today started
        taskRepository.saveTask(taskYesterday)

        val result = useCase(userId = "user-1", referenceTimeMillis = referenceTime, zoneId = zoneId)
        assertTrue(result is Result.Success)
        val data = (result as Result.Success).data

        assertEquals(1, data.overdue.size)
        assertEquals("t-old", data.overdue[0].task.id)
        assertTrue(data.overdue[0].isOverdue)
        assertTrue(data.dueToday.isEmpty())
    }

    @Test
    fun `completed task is excluded from both dueToday and overdue`() = runTest {
        val completedOverdue = createTask("t-comp-old", status = TaskStatus.COMPLETED, dueAt = startOfToday - 5000L)
        val completedToday = createTask("t-comp-today", status = TaskStatus.COMPLETED, dueAt = startOfToday + 5000L)
        taskRepository.saveTask(completedOverdue)
        taskRepository.saveTask(completedToday)

        val result = useCase(userId = "user-1", referenceTimeMillis = referenceTime, zoneId = zoneId)
        assertTrue(result is Result.Success)
        val data = (result as Result.Success).data

        assertTrue("Completed overdue task must be excluded", data.overdue.isEmpty())
        assertTrue("Completed today task must be excluded", data.dueToday.isEmpty())
    }

    @Test
    fun `cancelled task is excluded from both dueToday and overdue`() = runTest {
        val cancelledTask = createTask("t-cancelled", status = TaskStatus.CANCELLED, dueAt = startOfToday - 5000L)
        taskRepository.saveTask(cancelledTask)

        val result = useCase(userId = "user-1", referenceTimeMillis = referenceTime, zoneId = zoneId)
        assertTrue(result is Result.Success)
        val data = (result as Result.Success).data

        assertTrue(data.overdue.isEmpty())
        assertTrue(data.dueToday.isEmpty())
    }

    @Test
    fun `future-due task is excluded from both today and overdue`() = runTest {
        val futureTask = createTask("t-future", dueAt = startOfTomorrow + 1000L)
        taskRepository.saveTask(futureTask)

        val result = useCase(userId = "user-1", referenceTimeMillis = referenceTime, zoneId = zoneId)
        assertTrue(result is Result.Success)
        val data = (result as Result.Success).data

        assertTrue("Future task must not be in dueToday", data.dueToday.isEmpty())
        assertTrue("Future task must not be in overdue", data.overdue.isEmpty())
    }

    @Test
    fun `task with null dueAt is excluded`() = runTest {
        val noDueTask = createTask("t-nodue", dueAt = null)
        taskRepository.saveTask(noDueTask)

        val result = useCase(userId = "user-1", referenceTimeMillis = referenceTime, zoneId = zoneId)
        assertTrue(result is Result.Success)
        val data = (result as Result.Success).data

        assertTrue(data.dueToday.isEmpty())
        assertTrue(data.overdue.isEmpty())
    }

    @Test
    fun `task due later today is classified as dueToday and not overdue`() = runTest {
        // Due at 23:59:59 tonight
        val endOfDayMillis = startOfTomorrow - 1000L
        val taskTonight = createTask("t-tonight", dueAt = endOfDayMillis)
        taskRepository.saveTask(taskTonight)

        val result = useCase(userId = "user-1", referenceTimeMillis = referenceTime, zoneId = zoneId)
        assertTrue(result is Result.Success)
        val data = (result as Result.Success).data

        assertEquals(1, data.dueToday.size)
        assertEquals("t-tonight", data.dueToday[0].task.id)
        assertTrue("Task due later today must not be overdue", data.overdue.isEmpty())
    }

    @Test
    fun `user ownership filtering excludes tasks belonging to other users`() = runTest {
        val otherUserTask = createTask("t-other", userId = "user-2", dueAt = startOfToday + 1000L)
        val currentUserTask = createTask("t-mine", userId = "user-1", dueAt = startOfToday + 1000L)
        taskRepository.saveTask(otherUserTask)
        taskRepository.saveTask(currentUserTask)

        val result = useCase(userId = "user-1", referenceTimeMillis = referenceTime, zoneId = zoneId)
        assertTrue(result is Result.Success)
        val data = (result as Result.Success).data

        assertEquals(1, data.dueToday.size)
        assertEquals("t-mine", data.dueToday[0].task.id)
    }

    @Test
    fun `blank userId returns Failure with IllegalArgumentException`() = runTest {
        val result = useCase(userId = "   ")
        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error is IllegalArgumentException)
    }

    @Test
    fun `planTitle is resolved from planRepository when planId is present`() = runTest {
        val plan = Plan(
            id = "plan-alpha",
            userId = "user-1",
            title = "Alpha Mastery Plan",
            description = "Desc",
            status = PlanStatus.ACTIVE,
            createdAt = referenceTime,
            updatedAt = referenceTime,
            source = EntitySource.USER
        )
        planRepository.savePlan(plan)

        val task = createTask("t-with-plan", planId = "plan-alpha", dueAt = startOfToday + 2000L)
        taskRepository.saveTask(task)

        val result = useCase(userId = "user-1", referenceTimeMillis = referenceTime, zoneId = zoneId)
        assertTrue(result is Result.Success)
        val data = (result as Result.Success).data

        assertEquals(1, data.dueToday.size)
        assertEquals("Alpha Mastery Plan", data.dueToday[0].planTitle)
    }

    @Test
    fun `planTitle is null when task has no planId or plan not found`() = runTest {
        val taskNoPlan = createTask("t-noplan", planId = null, dueAt = startOfToday + 2000L)
        val taskMissingPlan = createTask("t-missingplan", planId = "plan-ghost", dueAt = startOfToday + 3000L)
        taskRepository.saveTask(taskNoPlan)
        taskRepository.saveTask(taskMissingPlan)

        val result = useCase(userId = "user-1", referenceTimeMillis = referenceTime, zoneId = zoneId)
        assertTrue(result is Result.Success)
        val data = (result as Result.Success).data

        assertEquals(2, data.dueToday.size)
        assertNull(data.dueToday.first { it.task.id == "t-noplan" }.planTitle)
        assertNull(data.dueToday.first { it.task.id == "t-missingplan" }.planTitle)
    }

    @Test
    fun `items are sorted by dueAt ascending`() = runTest {
        val t2 = createTask("t2", dueAt = startOfToday + 5000L)
        val t1 = createTask("t1", dueAt = startOfToday + 1000L)
        val t3 = createTask("t3", dueAt = startOfToday + 9000L)
        taskRepository.saveTask(t2)
        taskRepository.saveTask(t1)
        taskRepository.saveTask(t3)

        val result = useCase(userId = "user-1", referenceTimeMillis = referenceTime, zoneId = zoneId)
        assertTrue(result is Result.Success)
        val data = (result as Result.Success).data

        assertEquals(listOf("t1", "t2", "t3"), data.dueToday.map { it.task.id })
    }
}

