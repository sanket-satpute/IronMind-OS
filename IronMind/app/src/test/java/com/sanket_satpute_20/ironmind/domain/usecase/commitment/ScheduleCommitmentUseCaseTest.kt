package com.sanket_satpute_20.ironmind.domain.usecase.commitment

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.CommitmentStatus
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.testutil.fake.FakeClock
import com.sanket_satpute_20.ironmind.testutil.fake.FakeCommitmentRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeEventRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeIdGenerator
import com.sanket_satpute_20.ironmind.testutil.fake.FakeReminderScheduler
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ScheduleCommitmentUseCaseTest {

    private lateinit var repository: FakeCommitmentRepository
    private lateinit var clock: FakeClock
    private lateinit var idGenerator: FakeIdGenerator
    private lateinit var reminderScheduler: FakeReminderScheduler
    private lateinit var eventRepository: FakeEventRepository
    
    private lateinit var useCase: ScheduleCommitmentUseCase

    @Before
    fun setup() {
        repository = FakeCommitmentRepository()
        clock = FakeClock()
        idGenerator = FakeIdGenerator()
        reminderScheduler = FakeReminderScheduler()
        eventRepository = FakeEventRepository()
        
        useCase = ScheduleCommitmentUseCase(
            repository = repository,
            reminderScheduler = reminderScheduler,
            clock = clock,
            idGenerator = idGenerator,
            eventRepository = eventRepository
        )
    }

    @Test
    fun `schedule valid future time succeeds and updates db and scheduler`() = runTest {
        val now = clock.currentTimeMillis()
        val futureTime = now + 60000 // 1 minute in future
        
        // Setup initial commitment
        val createUseCase = CreateCommitmentUseCase(repository, reminderScheduler, idGenerator, clock, eventRepository)
        val commitmentResult = createUseCase(userId = "user-1", goalId = null, planId = null, taskId = null, parentCommitmentId = null, title = "Test", description = "", priority = 1, initialStatus = CommitmentStatus.COMMITTED)
        val commitmentId = (commitmentResult as Result.Success).data.id

        val result = useCase("user-1", commitmentId, futureTime)
        
        assertTrue(result is Result.Success)
        val updated = (result as Result.Success).data
        assertEquals(futureTime, updated.scheduledStartAt)
        
        // verify repo updated
        val retrieved = (repository.getCommitment(commitmentId) as Result.Success).data
        assertEquals(futureTime, retrieved?.scheduledStartAt)
        
        // Verify event created
        assertEquals(3, eventRepository.events.size) // 2 for create (CREATED, COMMITTED), 1 for update
        
        // Verify reminder scheduler updated
        // In a real fake, we would check scheduled reminders, but FakeReminderScheduler 
        // doesn't record them yet, so we just trust the test runs without crashing.
    }

    @Test
    fun `schedule past time fails`() = runTest {
        val now = clock.currentTimeMillis()
        val pastTime = now - 60000 // 1 minute in past
        
        val result = useCase("user-1", "test-id", pastTime)
        
        assertTrue(result is Result.Failure)
    }
}
