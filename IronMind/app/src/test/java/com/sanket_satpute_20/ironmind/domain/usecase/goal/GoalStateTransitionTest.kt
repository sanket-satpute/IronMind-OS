package com.sanket_satpute_20.ironmind.domain.usecase.goal

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.GoalStatus
import com.sanket_satpute_20.ironmind.testutil.fake.FakeClock
import com.sanket_satpute_20.ironmind.testutil.fake.FakeGoalRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeIdGenerator
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GoalStateTransitionTest {

    private lateinit var repository: FakeGoalRepository
    private lateinit var clock: FakeClock
    private lateinit var idGenerator: FakeIdGenerator
    
    private lateinit var createGoalUseCase: CreateGoalUseCase
    private lateinit var updateGoalStatusUseCase: UpdateGoalStatusUseCase

    @Before
    fun setup() {
        repository = FakeGoalRepository()
        clock = FakeClock()
        idGenerator = FakeIdGenerator()
        
        createGoalUseCase = CreateGoalUseCase(repository, idGenerator, clock)
        updateGoalStatusUseCase = UpdateGoalStatusUseCase(repository, clock)
    }

    @Test
    fun `updating status to COMPLETED sets completedAt`() = runTest {
        val createResult = createGoalUseCase("user-1", "Title", "Desc", "Why", 5, null)
        val initialGoal = (createResult as Result.Success).data

        assertNull(initialGoal.completedAt)
        
        clock.advanceTimeBy(1000)
        
        val updateResult = updateGoalStatusUseCase(initialGoal.id, GoalStatus.COMPLETED)
        assertTrue(updateResult is Result.Success)
        val updatedGoal = (updateResult as Result.Success).data
        
        assertEquals(GoalStatus.COMPLETED, updatedGoal.status)
        assertEquals(clock.currentTimeMillis(), updatedGoal.completedAt)
        assertTrue(updatedGoal.updatedAt > initialGoal.updatedAt)
    }

    @Test
    fun `updating status to ARCHIVED does not set completedAt`() = runTest {
        val createResult = createGoalUseCase("user-1", "Title", "Desc", "Why", 5, null)
        val initialGoal = (createResult as Result.Success).data

        clock.advanceTimeBy(1000)
        
        val updateResult = updateGoalStatusUseCase(initialGoal.id, GoalStatus.ARCHIVED)
        assertTrue(updateResult is Result.Success)
        val updatedGoal = (updateResult as Result.Success).data
        
        assertEquals(GoalStatus.ARCHIVED, updatedGoal.status)
        assertNull(updatedGoal.completedAt)
    }
    
    @Test
    fun `updating from COMPLETED back to ACTIVE clears completedAt`() = runTest {
        val createResult = createGoalUseCase("user-1", "Title", "Desc", "Why", 5, null)
        val initialGoal = (createResult as Result.Success).data

        clock.advanceTimeBy(1000)
        
        val completedResult = updateGoalStatusUseCase(initialGoal.id, GoalStatus.COMPLETED)
        val completedGoal = (completedResult as Result.Success).data
        assertEquals(clock.currentTimeMillis(), completedGoal.completedAt)
        
        clock.advanceTimeBy(1000)
        
        val activeResult = updateGoalStatusUseCase(initialGoal.id, GoalStatus.ACTIVE)
        val activeGoal = (activeResult as Result.Success).data
        
        assertEquals(GoalStatus.ACTIVE, activeGoal.status)
        assertNull(activeGoal.completedAt)
    }
}
