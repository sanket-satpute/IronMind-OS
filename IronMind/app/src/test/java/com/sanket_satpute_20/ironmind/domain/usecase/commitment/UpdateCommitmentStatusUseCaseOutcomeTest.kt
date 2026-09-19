package com.sanket_satpute_20.ironmind.domain.usecase.commitment

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Commitment
import com.sanket_satpute_20.ironmind.domain.model.CommitmentStatus
import com.sanket_satpute_20.ironmind.domain.model.ResultStatus
import com.sanket_satpute_20.ironmind.testutil.fake.FakeClock
import com.sanket_satpute_20.ironmind.testutil.fake.FakeCommitmentRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeIdGenerator
import com.sanket_satpute_20.ironmind.testutil.fake.FakeOutcomeRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UpdateCommitmentStatusUseCaseOutcomeTest {

    private lateinit var repository: FakeCommitmentRepository
    private lateinit var outcomeRepository: FakeOutcomeRepository
    private lateinit var clock: FakeClock
    private lateinit var idGenerator: FakeIdGenerator
    private lateinit var updateStatusUseCase: UpdateCommitmentStatusUseCase
    
    private lateinit var testCommitment: Commitment

    @Before
    fun setup() = runTest {
        repository = FakeCommitmentRepository()
        outcomeRepository = FakeOutcomeRepository()
        clock = FakeClock()
        idGenerator = FakeIdGenerator()
        updateStatusUseCase = UpdateCommitmentStatusUseCase(repository, outcomeRepository, clock, idGenerator)
        
        testCommitment = Commitment(
            id = "c1",
            userId = "u1",
            title = "Test",
            description = "",
            priority = 1,
            source = com.sanket_satpute_20.ironmind.domain.model.EntitySource.USER,
            status = CommitmentStatus.STARTED,
            createdAt = clock.currentTimeMillis(),
            updatedAt = clock.currentTimeMillis(),
            committedAt = clock.currentTimeMillis(),
            startedAt = clock.currentTimeMillis()
        )
        repository.saveCommitment(testCommitment)
    }

    @Test
    fun `completed commitment + positive outcome distinct recording`() = runTest {
        val result = updateStatusUseCase("c1", CommitmentStatus.COMPLETED, ResultStatus.POSITIVE, 30)
        
        assertTrue(result is Result.Success)
        
        val commitment = (repository.getCommitment("c1") as Result.Success).data!!
        assertEquals(CommitmentStatus.COMPLETED, commitment.status)
        
        val outcome = (outcomeRepository.getOutcomeForSource("c1") as Result.Success).data
        assertNotNull(outcome)
        assertEquals(ResultStatus.POSITIVE, outcome?.resultStatus)
        assertEquals(30, outcome?.actualDurationMinutes)
    }

    @Test
    fun `completed commitment + negative outcome distinct recording`() = runTest {
        val result = updateStatusUseCase("c1", CommitmentStatus.COMPLETED, ResultStatus.NEGATIVE, 10)
        
        assertTrue(result is Result.Success)
        
        val outcome = (outcomeRepository.getOutcomeForSource("c1") as Result.Success).data
        assertNotNull(outcome)
        assertEquals(ResultStatus.NEGATIVE, outcome?.resultStatus)
    }
    
    @Test
    fun `completed commitment + neutral outcome distinct recording`() = runTest {
        val result = updateStatusUseCase("c1", CommitmentStatus.COMPLETED, ResultStatus.NEUTRAL)
        
        assertTrue(result is Result.Success)
        
        val outcome = (outcomeRepository.getOutcomeForSource("c1") as Result.Success).data
        assertNotNull(outcome)
        assertEquals(ResultStatus.NEUTRAL, outcome?.resultStatus)
    }

    @Test
    fun `incomplete commitment + partial outcome distinct recording`() = runTest {
        // e.g. missed but with partial progress
        val result = updateStatusUseCase("c1", CommitmentStatus.MISSED, ResultStatus.PARTIAL, 15)
        
        assertTrue(result is Result.Success)
        
        val commitment = (repository.getCommitment("c1") as Result.Success).data!!
        assertEquals(CommitmentStatus.MISSED, commitment.status)
        
        val outcome = (outcomeRepository.getOutcomeForSource("c1") as Result.Success).data
        assertNotNull(outcome)
        assertEquals(ResultStatus.PARTIAL, outcome?.resultStatus)
        assertEquals(15, outcome?.actualDurationMinutes)
    }
}
