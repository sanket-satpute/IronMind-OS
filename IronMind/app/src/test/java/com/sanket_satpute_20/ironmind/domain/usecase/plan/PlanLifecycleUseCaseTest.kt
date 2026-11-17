package com.sanket_satpute_20.ironmind.domain.usecase.plan

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.EventType
import com.sanket_satpute_20.ironmind.domain.model.Plan
import com.sanket_satpute_20.ironmind.domain.model.PlanStatus
import com.sanket_satpute_20.ironmind.testutil.fake.FakeClock
import com.sanket_satpute_20.ironmind.testutil.fake.FakeEventRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeIdGenerator
import com.sanket_satpute_20.ironmind.testutil.fake.FakePlanRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PlanLifecycleUseCaseTest {

    private lateinit var planRepository: FakePlanRepository
    private lateinit var clock: FakeClock
    private lateinit var idGenerator: FakeIdGenerator
    private lateinit var eventRepository: FakeEventRepository

    private lateinit var editPlanUseCase: EditPlanUseCase
    private lateinit var updatePlanStatusUseCase: UpdatePlanStatusUseCase

    @Before
    fun setup() {
        planRepository = FakePlanRepository()
        clock = FakeClock(1000L)
        idGenerator = FakeIdGenerator()
        eventRepository = FakeEventRepository()

        editPlanUseCase = EditPlanUseCase(planRepository, clock, idGenerator, eventRepository)
        updatePlanStatusUseCase = UpdatePlanStatusUseCase(planRepository, clock, idGenerator, eventRepository)
    }

    private suspend fun createAndSavePlan(
        id: String = "plan-1",
        userId: String = "user-1",
        goalId: String? = "goal-1",
        ambitionId: String? = "ambition-1",
        title: String = "Initial Plan Title",
        description: String = "Initial Plan Description",
        status: PlanStatus = PlanStatus.ACTIVE,
        createdAt: Long = 1000L,
        updatedAt: Long = 1000L,
        startedAt: Long? = 1000L,
        completedAt: Long? = null,
        source: EntitySource = EntitySource.USER
    ): Plan {
        val plan = Plan(
            id = id,
            userId = userId,
            goalId = goalId,
            ambitionId = ambitionId,
            title = title,
            description = description,
            status = status,
            createdAt = createdAt,
            updatedAt = updatedAt,
            startedAt = startedAt,
            completedAt = completedAt,
            source = source
        )
        planRepository.savePlan(plan)
        return plan
    }

    // --- EditPlanUseCase Tests ---

    @Test
    fun `edit plan title and description succeeds while preserving relationships and timestamps`() = runTest {
        createAndSavePlan()
        clock.setTime(2000L)

        val result = editPlanUseCase(
            userId = "user-1",
            planId = "plan-1",
            title = "Updated Title",
            description = "Updated Description"
        )

        assertTrue("Edit should succeed", result is Result.Success)
        val updated = (result as Result.Success).data
        assertEquals("plan-1", updated.id)
        assertEquals("user-1", updated.userId)
        assertEquals("goal-1", updated.goalId)
        assertEquals("ambition-1", updated.ambitionId)
        assertEquals("Updated Title", updated.title)
        assertEquals("Updated Description", updated.description)
        assertEquals(PlanStatus.ACTIVE, updated.status)
        assertEquals(1000L, updated.createdAt)
        assertEquals(2000L, updated.updatedAt)
        assertEquals(1000L, updated.startedAt)
        assertNull(updated.completedAt)
        assertEquals(EntitySource.USER, updated.source)

        // Verify event
        val events = eventRepository.events.values.toList()
        assertEquals(1, events.size)
        assertEquals(EventType.PLAN_UPDATED, events[0].type)
        assertEquals("plan-1", events[0].entityId)
    }

    @Test
    fun `edit plan fails with blank title`() = runTest {
        createAndSavePlan()

        val result = editPlanUseCase(
            userId = "user-1",
            planId = "plan-1",
            title = "   "
        )

        assertTrue(result is Result.Failure)
        assertEquals("Title cannot be blank", (result as Result.Failure).error.message)
    }

    @Test
    fun `edit plan fails when plan belongs to a different user`() = runTest {
        createAndSavePlan(userId = "user-1")

        val result = editPlanUseCase(
            userId = "user-2",
            planId = "plan-1",
            title = "Malicious update"
        )

        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error is IllegalStateException)
    }

    @Test
    fun `edit plan status from ACTIVE to PAUSED emits PLAN_PAUSED event`() = runTest {
        createAndSavePlan(status = PlanStatus.ACTIVE)
        clock.setTime(3000L)

        val result = editPlanUseCase(
            userId = "user-1",
            planId = "plan-1",
            status = PlanStatus.PAUSED
        )

        assertTrue(result is Result.Success)
        val updated = (result as Result.Success).data
        assertEquals(PlanStatus.PAUSED, updated.status)
        assertEquals(3000L, updated.updatedAt)

        val events = eventRepository.events.values.toList()
        assertEquals(1, events.size)
        assertEquals(EventType.PLAN_PAUSED, events[0].type)
    }

    @Test
    fun `edit plan status from PAUSED to ACTIVE emits PLAN_ACTIVATED event`() = runTest {
        createAndSavePlan(status = PlanStatus.PAUSED)
        clock.setTime(3500L)

        val result = editPlanUseCase(
            userId = "user-1",
            planId = "plan-1",
            status = PlanStatus.ACTIVE
        )

        assertTrue(result is Result.Success)
        val updated = (result as Result.Success).data
        assertEquals(PlanStatus.ACTIVE, updated.status)
        assertEquals(3500L, updated.updatedAt)

        val events = eventRepository.events.values.toList()
        assertEquals(1, events.size)
        assertEquals(EventType.PLAN_ACTIVATED, events[0].type)
    }

    @Test
    fun `edit plan status from ACTIVE to COMPLETED sets completedAt and emits PLAN_COMPLETED event`() = runTest {
        createAndSavePlan(status = PlanStatus.ACTIVE)
        clock.setTime(4000L)

        val result = editPlanUseCase(
            userId = "user-1",
            planId = "plan-1",
            status = PlanStatus.COMPLETED
        )

        assertTrue(result is Result.Success)
        val updated = (result as Result.Success).data
        assertEquals(PlanStatus.COMPLETED, updated.status)
        assertEquals(4000L, updated.completedAt)

        val events = eventRepository.events.values.toList()
        assertEquals(1, events.size)
        assertEquals(EventType.PLAN_COMPLETED, events[0].type)
    }

    @Test
    fun `edit plan status from COMPLETED to ACTIVE reactivates plan and clears completedAt`() = runTest {
        createAndSavePlan(status = PlanStatus.COMPLETED, completedAt = 3000L)
        clock.setTime(5000L)

        val result = editPlanUseCase(
            userId = "user-1",
            planId = "plan-1",
            status = PlanStatus.ACTIVE
        )

        assertTrue(result is Result.Success)
        val updated = (result as Result.Success).data
        assertEquals(PlanStatus.ACTIVE, updated.status)
        assertNull("completedAt must be cleared when reactivated", updated.completedAt)

        val events = eventRepository.events.values.toList()
        assertEquals(1, events.size)
        assertEquals(EventType.PLAN_ACTIVATED, events[0].type)
    }

    @Test
    fun `edit plan rejects invalid transition from COMPLETED to PAUSED`() = runTest {
        createAndSavePlan(status = PlanStatus.COMPLETED)

        val result = editPlanUseCase(
            userId = "user-1",
            planId = "plan-1",
            status = PlanStatus.PAUSED
        )

        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error is IllegalStateException)
    }

    @Test
    fun `edit plan rejects transitions from terminal ABANDONED state`() = runTest {
        createAndSavePlan(status = PlanStatus.ABANDONED)

        val result = editPlanUseCase(
            userId = "user-1",
            planId = "plan-1",
            status = PlanStatus.ACTIVE
        )

        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error is IllegalStateException)
    }

    // --- UpdatePlanStatusUseCase Tests ---

    @Test
    fun `updatePlanStatus from ACTIVE to PAUSED persists correctly`() = runTest {
        createAndSavePlan(status = PlanStatus.ACTIVE)
        clock.setTime(2200L)

        val result = updatePlanStatusUseCase(
            userId = "user-1",
            planId = "plan-1",
            newStatus = PlanStatus.PAUSED
        )

        assertTrue(result is Result.Success)
        val updated = (result as Result.Success).data
        assertEquals(PlanStatus.PAUSED, updated.status)
        assertEquals(2200L, updated.updatedAt)

        val persisted = (planRepository.getPlan("plan-1") as Result.Success).data
        assertNotNull(persisted)
        assertEquals(PlanStatus.PAUSED, persisted!!.status)
    }

    @Test
    fun `updatePlanStatus from PAUSED to COMPLETED sets completedAt`() = runTest {
        createAndSavePlan(status = PlanStatus.PAUSED)
        clock.setTime(4500L)

        val result = updatePlanStatusUseCase(
            userId = "user-1",
            planId = "plan-1",
            newStatus = PlanStatus.COMPLETED
        )

        assertTrue(result is Result.Success)
        val updated = (result as Result.Success).data
        assertEquals(PlanStatus.COMPLETED, updated.status)
        assertEquals(4500L, updated.completedAt)

        val events = eventRepository.events.values.toList()
        assertEquals(EventType.PLAN_COMPLETED, events[0].type)
    }

    @Test
    fun `updatePlanStatus is no-op success when new status is identical to current status`() = runTest {
        createAndSavePlan(status = PlanStatus.ACTIVE)

        val result = updatePlanStatusUseCase(
            userId = "user-1",
            planId = "plan-1",
            newStatus = PlanStatus.ACTIVE
        )

        assertTrue(result is Result.Success)
        assertTrue(eventRepository.events.isEmpty())
    }

    @Test
    fun `updatePlanStatus rejects invalid transition from DRAFT to PAUSED`() = runTest {
        createAndSavePlan(status = PlanStatus.DRAFT)

        val result = updatePlanStatusUseCase(
            userId = "user-1",
            planId = "plan-1",
            newStatus = PlanStatus.PAUSED
        )

        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error is IllegalStateException)
    }

    @Test
    fun `use cases succeed even if eventRepository throws`() = runTest {
        createAndSavePlan(status = PlanStatus.ACTIVE)
        eventRepository.shouldFail = true

        val result = updatePlanStatusUseCase(
            userId = "user-1",
            planId = "plan-1",
            newStatus = PlanStatus.PAUSED
        )

        assertTrue("Status transition must succeed even if event logging fails", result is Result.Success)
        val updated = (result as Result.Success).data
        assertEquals(PlanStatus.PAUSED, updated.status)
    }
}

