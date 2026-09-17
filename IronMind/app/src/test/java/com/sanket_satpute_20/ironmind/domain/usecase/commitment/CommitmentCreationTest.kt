package com.sanket_satpute_20.ironmind.domain.usecase.commitment

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.CommitmentStatus
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.testutil.fake.FakeClock
import com.sanket_satpute_20.ironmind.testutil.fake.FakeCommitmentRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeIdGenerator
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CommitmentCreationTest {

    private lateinit var repository: FakeCommitmentRepository
    private lateinit var clock: FakeClock
    private lateinit var idGenerator: FakeIdGenerator
    
    private lateinit var createCommitmentUseCase: CreateCommitmentUseCase
    private lateinit var editCommitmentUseCase: EditCommitmentUseCase

    @Before
    fun setup() {
        repository = FakeCommitmentRepository()
        clock = FakeClock()
        idGenerator = FakeIdGenerator()
        
        createCommitmentUseCase = CreateCommitmentUseCase(repository, idGenerator, clock)
        editCommitmentUseCase = EditCommitmentUseCase(repository, clock)
    }

    @Test
    fun `create commitment succeeds with valid input`() = runTest {
        idGenerator.nextId = "commitment-1"
        
        val result = createCommitmentUseCase(
            userId = "user-1",
            goalId = "goal-1",
            planId = null,
            taskId = null,
            parentCommitmentId = null,
            title = "My Commitment",
            description = "Desc",
            priority = 1,
            initialStatus = CommitmentStatus.PLANNED
        )
        
        assertTrue(result is Result.Success)
        val commitment = (result as Result.Success).data
        
        assertEquals("commitment-1", commitment.id)
        assertEquals("My Commitment", commitment.title)
        assertEquals(CommitmentStatus.PLANNED, commitment.status)
        assertEquals(EntitySource.USER, commitment.source)
        assertEquals(clock.currentTimeMillis(), commitment.createdAt)
        assertEquals(0L, commitment.committedAt)
    }

    @Test
    fun `create commitment defaults to COMMITTED properly`() = runTest {
        val result = createCommitmentUseCase(
            userId = "user-1",
            goalId = null,
            planId = null,
            taskId = null,
            parentCommitmentId = null,
            title = "Task",
            description = "Desc",
            priority = 1,
            initialStatus = CommitmentStatus.COMMITTED
        )
        
        assertTrue(result is Result.Success)
        val commitment = (result as Result.Success).data
        
        assertEquals(CommitmentStatus.COMMITTED, commitment.status)
        assertEquals(clock.currentTimeMillis(), commitment.committedAt)
    }

    @Test
    fun `edit commitment updates correctly`() = runTest {
        val commitment = (createCommitmentUseCase(
            userId = "user-1", goalId = null, planId = null, taskId = null, parentCommitmentId = null,
            title = "Title", description = "Desc", priority = 1
        ) as Result.Success).data
        
        clock.advanceTimeBy(1000)
        
        val editResult = editCommitmentUseCase(
            commitmentId = commitment.id,
            title = "New Title",
            description = null,
            priority = 2,
            scheduledStartAt = 5000L,
            scheduledEndAt = 6000L
        )
        
        assertTrue(editResult is Result.Success)
        val edited = (editResult as Result.Success).data
        
        assertEquals("New Title", edited.title)
        assertEquals("Desc", edited.description)
        assertEquals(2, edited.priority)
        assertEquals(5000L, edited.scheduledStartAt)
        assertTrue(edited.updatedAt > commitment.updatedAt)
    }
}
