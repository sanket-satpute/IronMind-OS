package com.sanket_satpute_20.ironmind.domain.model.intervention

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Commitment
import com.sanket_satpute_20.ironmind.domain.model.CommitmentStatus
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.Goal
import com.sanket_satpute_20.ironmind.domain.model.GoalStatus
import com.sanket_satpute_20.ironmind.domain.repository.CommitmentRepository
import com.sanket_satpute_20.ironmind.domain.repository.GoalRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import io.mockk.coEvery
import io.mockk.mockk

class TargetCompletionResolverTest {

    private lateinit var goalRepository: GoalRepository
    private lateinit var commitmentRepository: CommitmentRepository
    private lateinit var resolver: TargetCompletionResolver

    private val userId = "user-123"

    @Before
    fun setUp() {
        goalRepository = mockk()
        commitmentRepository = mockk()
        resolver = TargetCompletionResolver(goalRepository, commitmentRepository)
    }

    private fun mockGoal(id: String, uId: String, status: GoalStatus): Goal {
        return Goal(
            id = id,
            userId = uId,
            ambitionId = null,
            title = "Test Goal",
            description = "Test Desc",
            why = "Test Why",
            importance = 1,
            status = status,
            targetAt = 1000L,
            startedAt = null,
            completedAt = null,
            createdAt = 1000L,
            updatedAt = 1000L
        )
    }

    private fun mockCommitment(id: String, uId: String, status: CommitmentStatus): Commitment {
        return Commitment(
            id = id,
            userId = uId,
            goalId = null,
            planId = null,
            taskId = null,
            parentCommitmentId = null,
            title = "Test Comm",
            description = "Desc",
            committedAt = 1000L,
            scheduledStartAt = null,
            scheduledEndAt = null,
            status = status,
            priority = 1,
            source = EntitySource.USER,
            createdAt = 1000L,
            updatedAt = 1000L,
            startedAt = null,
            completedAt = null,
            postponedAt = null,
            missedAt = null,
            recoveredAt = null
        )
    }

    @Test
    fun `Goal completed returns COMPLETED`() = runBlocking {
        val goal = mockGoal("g1", userId, GoalStatus.COMPLETED)
        coEvery { goalRepository.getGoal("g1") } returns Result.Success(goal)

        val result = resolver.resolve(userId, "GOAL", "g1")
        assertEquals(TargetCompletionResult.COMPLETED, result)
    }

    @Test
    fun `Goal active returns NOT_COMPLETED`() = runBlocking {
        val goal = mockGoal("g1", userId, GoalStatus.ACTIVE)
        coEvery { goalRepository.getGoal("g1") } returns Result.Success(goal)

        val result = resolver.resolve(userId, "GOAL", "g1")
        assertEquals(TargetCompletionResult.NOT_COMPLETED, result)
    }

    @Test
    fun `Goal archived returns NOT_COMPLETED`() = runBlocking {
        // Domain does not equate ARCHIVED to COMPLETED unless explicitly stated
        val goal = mockGoal("g1", userId, GoalStatus.ARCHIVED)
        coEvery { goalRepository.getGoal("g1") } returns Result.Success(goal)

        val result = resolver.resolve(userId, "GOAL", "g1")
        assertEquals(TargetCompletionResult.NOT_COMPLETED, result)
    }

    @Test
    fun `Commitment completed returns COMPLETED`() = runBlocking {
        val comm = mockCommitment("c1", userId, CommitmentStatus.COMPLETED)
        coEvery { commitmentRepository.getCommitment("c1") } returns Result.Success(comm)

        val result = resolver.resolve(userId, "COMMITMENT", "c1")
        assertEquals(TargetCompletionResult.COMPLETED, result)
    }

    @Test
    fun `Commitment abandoned returns NOT_COMPLETED`() = runBlocking {
        val comm = mockCommitment("c1", userId, CommitmentStatus.ABANDONED)
        coEvery { commitmentRepository.getCommitment("c1") } returns Result.Success(comm)

        val result = resolver.resolve(userId, "COMMITMENT", "c1")
        assertEquals(TargetCompletionResult.NOT_COMPLETED, result)
    }

    @Test
    fun `Reflection target returns COMPLETION_UNDEFINED`() = runBlocking {
        val result = resolver.resolve(userId, "REFLECTION", "r1")
        assertEquals(TargetCompletionResult.COMPLETION_UNDEFINED, result)
    }

    @Test
    fun `Target not found returns UNKNOWN`() = runBlocking {
        coEvery { goalRepository.getGoal("g_missing") } returns Result.Success(null)
        
        val result = resolver.resolve(userId, "GOAL", "g_missing")
        assertEquals(TargetCompletionResult.UNKNOWN, result)
    }

    @Test
    fun `Target wrong user returns UNKNOWN`() = runBlocking {
        val goal = mockGoal("g1", "different-user", GoalStatus.COMPLETED)
        coEvery { goalRepository.getGoal("g1") } returns Result.Success(goal)

        val result = resolver.resolve(userId, "GOAL", "g1")
        assertEquals(TargetCompletionResult.UNKNOWN, result)
    }

    @Test
    fun `Unknown target type returns UNKNOWN`() = runBlocking {
        val result = resolver.resolve(userId, "UNKNOWN_TYPE", "id1")
        assertEquals(TargetCompletionResult.UNKNOWN, result)
    }

    @Test
    fun `Repository failure returns UNKNOWN`() = runBlocking {
        coEvery { goalRepository.getGoal("err") } returns Result.Failure(Exception("DB error"))
        
        val result = resolver.resolve(userId, "GOAL", "err")
        assertEquals(TargetCompletionResult.UNKNOWN, result)
    }
}
