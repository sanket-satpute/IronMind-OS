package com.sanket_satpute_20.ironmind.domain.usecase.commitment

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.CommitmentStatus
import com.sanket_satpute_20.ironmind.testutil.fake.FakeClock
import com.sanket_satpute_20.ironmind.testutil.fake.FakeCommitmentRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeIdGenerator
import com.sanket_satpute_20.ironmind.testutil.fake.FakeOutcomeRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GetActiveCommitmentsUseCaseTest {

    private lateinit var repository: FakeCommitmentRepository
    private lateinit var getActiveCommitmentsUseCase: GetActiveCommitmentsUseCase
    private lateinit var createCommitmentUseCase: CreateCommitmentUseCase

    @Before
    fun setup() {
        repository = FakeCommitmentRepository()
        getActiveCommitmentsUseCase = GetActiveCommitmentsUseCase(repository)
        createCommitmentUseCase = CreateCommitmentUseCase(
            repository = repository,
            idGenerator = FakeIdGenerator(),
            clock = FakeClock()
        )
    }

    @Test
    fun `invoke returns only COMMITTED and STARTED commitments`() = runTest {
        val clock = FakeClock()
        val idGenerator = FakeIdGenerator()
        val outcomeRepository = FakeOutcomeRepository()
        val updateCommitmentStatusUseCase = UpdateCommitmentStatusUseCase(repository, outcomeRepository, clock, idGenerator)
        val userId = "user-1"
        
        // C1: COMMITTED
        createCommitmentUseCase(userId, null, null, null, null, "C1", "Desc", 1, initialStatus = CommitmentStatus.COMMITTED)
        
        // C2: STARTED
        val c2Result = createCommitmentUseCase(userId, null, null, null, null, "C2", "Desc", 1, initialStatus = CommitmentStatus.COMMITTED)
        updateCommitmentStatusUseCase((c2Result as Result.Success).data.id, CommitmentStatus.STARTED)
        
        // C3: PLANNED
        createCommitmentUseCase(userId, null, null, null, null, "C3", "Desc", 1, initialStatus = CommitmentStatus.PLANNED)
        
        // C4: COMPLETED
        val c4Result = createCommitmentUseCase(userId, null, null, null, null, "C4", "Desc", 1, initialStatus = CommitmentStatus.COMMITTED)
        updateCommitmentStatusUseCase((c4Result as Result.Success).data.id, CommitmentStatus.STARTED)
        updateCommitmentStatusUseCase((c4Result as Result.Success).data.id, CommitmentStatus.COMPLETED)

        val result = getActiveCommitmentsUseCase(userId)
        assertTrue(result is Result.Success)
        
        val commitments = (result as Result.Success).data
        assertEquals(2, commitments.size)
        assertTrue(commitments.all { it.status == CommitmentStatus.COMMITTED || it.status == CommitmentStatus.STARTED })
    }
}
