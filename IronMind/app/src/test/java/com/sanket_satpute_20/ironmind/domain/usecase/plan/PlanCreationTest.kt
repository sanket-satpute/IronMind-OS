package com.sanket_satpute_20.ironmind.domain.usecase.plan

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.PlanStatus
import com.sanket_satpute_20.ironmind.testutil.fake.FakeClock
import com.sanket_satpute_20.ironmind.testutil.fake.FakeIdGenerator
import com.sanket_satpute_20.ironmind.testutil.fake.FakePlanRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PlanCreationTest {

    private lateinit var repository: FakePlanRepository
    private lateinit var clock: FakeClock
    private lateinit var idGenerator: FakeIdGenerator
    
    private lateinit var createPlanUseCase: CreatePlanUseCase

    @Before
    fun setup() {
        repository = FakePlanRepository()
        clock = FakeClock()
        idGenerator = FakeIdGenerator()
        
        createPlanUseCase = CreatePlanUseCase(repository, idGenerator, clock)
    }

    @Test
    fun `create plan succeeds with valid input`() = runTest {
        idGenerator.nextId = "plan-1"
        
        val result = createPlanUseCase(
            userId = "user-1",
            goalId = "goal-1",
            title = "My Plan",
            description = "Plan desc"
        )
        
        assertTrue(result is Result.Success)
        val plan = (result as Result.Success).data
        
        assertEquals("plan-1", plan.id)
        assertEquals("user-1", plan.userId)
        assertEquals("goal-1", plan.goalId)
        assertEquals("My Plan", plan.title)
        assertEquals(PlanStatus.ACTIVE, plan.status)
        assertEquals(EntitySource.USER, plan.source)
        assertEquals(clock.currentTimeMillis(), plan.createdAt)
        assertEquals(clock.currentTimeMillis(), plan.startedAt)
    }

    @Test
    fun `create plan fails with blank title`() = runTest {
        val result = createPlanUseCase(
            userId = "user-1",
            goalId = "goal-1",
            title = "   ",
            description = "Plan desc"
        )
        
        assertTrue(result is Result.Failure)
        assertEquals("Title cannot be blank", (result as Result.Failure).error.message)
    }
}
