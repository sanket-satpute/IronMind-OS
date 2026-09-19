package com.sanket_satpute_20.ironmind.domain.usecase.commitment

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.CommitmentStatus
import com.sanket_satpute_20.ironmind.testutil.fake.FakeClock
import com.sanket_satpute_20.ironmind.testutil.fake.FakeCommitmentRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeOutcomeRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeIdGenerator
import com.sanket_satpute_20.ironmind.testutil.fake.FakeReminderScheduler
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CommitmentStateMachineTest {

    private lateinit var repository: FakeCommitmentRepository
    private lateinit var outcomeRepository: FakeOutcomeRepository
    private lateinit var clock: FakeClock
    private lateinit var idGenerator: FakeIdGenerator
    private lateinit var reminderScheduler: FakeReminderScheduler
    
    private lateinit var createCommitmentUseCase: CreateCommitmentUseCase
    private lateinit var updateStatusUseCase: UpdateCommitmentStatusUseCase

    @Before
    fun setup() {
        repository = FakeCommitmentRepository()
        outcomeRepository = FakeOutcomeRepository()
        clock = FakeClock()
        idGenerator = FakeIdGenerator()
        reminderScheduler = FakeReminderScheduler()
        
        createCommitmentUseCase = CreateCommitmentUseCase(repository, reminderScheduler, idGenerator, clock)
        updateStatusUseCase = UpdateCommitmentStatusUseCase(repository, outcomeRepository, reminderScheduler, clock, idGenerator)
    }

    @Test
    fun `valid transition PLANNED to COMMITTED to STARTED to COMPLETED`() = runTest {
        val planned = (createCommitmentUseCase(
            "u1", null, null, null, null, "T", "D", 1, initialStatus = CommitmentStatus.PLANNED
        ) as Result.Success).data
        
        clock.advanceTimeBy(1000)
        val committed = (updateStatusUseCase(planned.id, CommitmentStatus.COMMITTED) as Result.Success).data
        assertEquals(CommitmentStatus.COMMITTED, committed.status)
        assertEquals(clock.currentTimeMillis(), committed.committedAt)
        
        clock.advanceTimeBy(1000)
        val started = (updateStatusUseCase(committed.id, CommitmentStatus.STARTED) as Result.Success).data
        assertEquals(CommitmentStatus.STARTED, started.status)
        assertEquals(clock.currentTimeMillis(), started.startedAt)
        
        clock.advanceTimeBy(1000)
        val completed = (updateStatusUseCase(started.id, CommitmentStatus.COMPLETED) as Result.Success).data
        assertEquals(CommitmentStatus.COMPLETED, completed.status)
        assertEquals(clock.currentTimeMillis(), completed.completedAt)
    }

    @Test
    fun `invalid transition PLANNED to STARTED is rejected`() = runTest {
        val planned = (createCommitmentUseCase(
            "u1", null, null, null, null, "T", "D", 1, initialStatus = CommitmentStatus.PLANNED
        ) as Result.Success).data
        
        val result = updateStatusUseCase(planned.id, CommitmentStatus.STARTED)
        assertTrue(result is Result.Failure)
    }

    @Test
    fun `valid transition POSTPONED to COMMITTED clears startedAt`() = runTest {
        val committed = (createCommitmentUseCase(
            "u1", null, null, null, null, "T", "D", 1, initialStatus = CommitmentStatus.COMMITTED
        ) as Result.Success).data
        
        clock.advanceTimeBy(1000)
        val started = (updateStatusUseCase(committed.id, CommitmentStatus.STARTED) as Result.Success).data
        assertNotNull(started.startedAt)
        
        clock.advanceTimeBy(1000)
        val postponed = (updateStatusUseCase(started.id, CommitmentStatus.POSTPONED) as Result.Success).data
        assertEquals(CommitmentStatus.POSTPONED, postponed.status)
        
        clock.advanceTimeBy(1000)
        val recommitted = (updateStatusUseCase(postponed.id, CommitmentStatus.COMMITTED) as Result.Success).data
        assertEquals(CommitmentStatus.COMMITTED, recommitted.status)
        assertNull(recommitted.startedAt)
        assertNull(recommitted.postponedAt)
        assertEquals(clock.currentTimeMillis(), recommitted.committedAt)
    }

    @Test
    fun `invalid transition COMPLETED to STARTED is rejected`() = runTest {
        val committed = (createCommitmentUseCase(
            "u1", null, null, null, null, "T", "D", 1, initialStatus = CommitmentStatus.COMMITTED
        ) as Result.Success).data
        
        val started = (updateStatusUseCase(committed.id, CommitmentStatus.STARTED) as Result.Success).data
        val completed = (updateStatusUseCase(started.id, CommitmentStatus.COMPLETED) as Result.Success).data
        
        val result = updateStatusUseCase(completed.id, CommitmentStatus.STARTED)
        assertTrue(result is Result.Failure)
    }
}
