package com.sanket_satpute_20.ironmind.domain.usecase.history

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Commitment
import com.sanket_satpute_20.ironmind.domain.model.CommitmentStatus
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.Event
import com.sanket_satpute_20.ironmind.domain.model.EventType
import com.sanket_satpute_20.ironmind.domain.model.Goal
import com.sanket_satpute_20.ironmind.domain.model.GoalStatus
import com.sanket_satpute_20.ironmind.domain.model.Reflection
import com.sanket_satpute_20.ironmind.domain.model.TimelineItem
import com.sanket_satpute_20.ironmind.testutil.fake.FakeCommitmentRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeEventRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeGoalRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeReflectionRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GetTimelineUseCaseTest {

    private lateinit var eventRepository: FakeEventRepository
    private lateinit var commitmentRepository: FakeCommitmentRepository
    private lateinit var reflectionRepository: FakeReflectionRepository
    private lateinit var goalRepository: FakeGoalRepository
    private lateinit var getTimelineUseCase: GetTimelineUseCase

    @Before
    fun setup() {
        eventRepository = FakeEventRepository()
        commitmentRepository = FakeCommitmentRepository()
        reflectionRepository = FakeReflectionRepository()
        goalRepository = FakeGoalRepository()

        getTimelineUseCase = GetTimelineUseCase(
            eventRepository,
            commitmentRepository,
            reflectionRepository,
            goalRepository
        )
    }

    @Test
    fun `invoke returns mapped timeline items sorted by timestamp`() = runTest {
        val userId = "user-1"
        
        // Create Commitment
        val commitment = Commitment(
            id = "c1",
            userId = userId,
            title = "Read 10 pages",
            description = "Book",
            status = CommitmentStatus.COMMITTED,
            priority = 1,
            source = EntitySource.USER,
            committedAt = 1000L,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        commitmentRepository.saveCommitment(commitment)
        
        val event1 = Event(
            id = "e1",
            userId = userId,
            type = EventType.COMMITMENT_CREATED,
            entityId = "c1",
            occurredAt = 1000L,
            recordedAt = 1000L,
            source = EntitySource.USER
        )
        eventRepository.saveEvent(event1)
        
        // Create Reflection
        val reflection = Reflection(
            id = "r1",
            userId = userId,
            targetEntityId = null,
            targetEntityType = null,
            content = "Felt great today",
            sentiment = "POSITIVE",
            createdAt = 2000L
        )
        reflectionRepository.saveReflection(reflection)
        
        val event2 = Event(
            id = "e2",
            userId = userId,
            type = EventType.REFLECTION_CREATED,
            entityId = "r1",
            occurredAt = 2000L,
            recordedAt = 2000L,
            source = EntitySource.USER
        )
        eventRepository.saveEvent(event2)

        // Create Goal
        val goal = Goal(
            id = "g1",
            userId = userId,
            title = "Read a book",
            description = "To get smarter",
            why = "Because",
            importance = 5,
            status = GoalStatus.ACTIVE,
            createdAt = 3000L,
            updatedAt = 3000L
        )
        goalRepository.saveGoal(goal)
        
        val event3 = Event(
            id = "e3",
            userId = userId,
            type = EventType.GOAL_CREATED,
            entityId = "g1",
            occurredAt = 3000L,
            recordedAt = 3000L,
            source = EntitySource.USER
        )
        eventRepository.saveEvent(event3)

        // Add irrelevant event
        val event4 = Event(
            id = "e4",
            userId = userId,
            type = EventType.SYNC_STARTED,
            occurredAt = 4000L,
            recordedAt = 4000L,
            source = EntitySource.AI
        )
        eventRepository.saveEvent(event4)
        
        val result = getTimelineUseCase(userId)
        assertTrue(result is Result.Success)
        
        val items = (result as Result.Success).data
        assertEquals(3, items.size) // Sync started should be ignored
        
        // Verify sorting (descending)
        assertTrue(items[0] is TimelineItem.GoalEvent)
        assertEquals("Read a book", (items[0] as TimelineItem.GoalEvent).goalTitle)
        
        assertTrue(items[1] is TimelineItem.ReflectionRecorded)
        assertEquals("Felt great today", (items[1] as TimelineItem.ReflectionRecorded).content)
        
        assertTrue(items[2] is TimelineItem.CommitmentEvent)
        assertEquals("Read 10 pages", (items[2] as TimelineItem.CommitmentEvent).commitmentTitle)
    }
}
