package com.sanket_satpute_20.ironmind.domain.usecase.correction

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.EventType
import com.sanket_satpute_20.ironmind.domain.usecase.commitment.CreateCommitmentUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.commitment.EditCommitmentUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.goal.CreateGoalUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.goal.EditGoalUseCase
import com.sanket_satpute_20.ironmind.testutil.fake.FakeClock
import com.sanket_satpute_20.ironmind.testutil.fake.FakeCommitmentRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeEventRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeGoalRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeIdGenerator
import com.sanket_satpute_20.ironmind.testutil.fake.FakeReminderScheduler
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Sprint V1.7 — User Corrections.
 *
 * Verifies:
 * - Goal corrections update the entity and emit GOAL_UPDATED event with source=USER.
 * - Commitment corrections update the entity and emit COMMITMENT_UPDATED event with source=USER.
 * - Invalid corrections are rejected without emitting any event.
 * - User correction is treated as high-value evidence (source=USER, not AI/SYSTEM).
 */
class UserCorrectionTest {

    private lateinit var goalRepository: FakeGoalRepository
    private lateinit var commitmentRepository: FakeCommitmentRepository
    private lateinit var eventRepository: FakeEventRepository
    private lateinit var clock: FakeClock
    private lateinit var idGenerator: FakeIdGenerator
    private lateinit var reminderScheduler: FakeReminderScheduler

    private lateinit var createGoalUseCase: CreateGoalUseCase
    private lateinit var editGoalUseCase: EditGoalUseCase
    private lateinit var createCommitmentUseCase: CreateCommitmentUseCase
    private lateinit var editCommitmentUseCase: EditCommitmentUseCase

    @Before
    fun setup() {
        goalRepository = FakeGoalRepository()
        commitmentRepository = FakeCommitmentRepository()
        eventRepository = FakeEventRepository()
        clock = FakeClock()
        idGenerator = FakeIdGenerator()
        reminderScheduler = FakeReminderScheduler()

        createGoalUseCase = CreateGoalUseCase(goalRepository, idGenerator, clock)
        editGoalUseCase = EditGoalUseCase(goalRepository, clock, idGenerator, eventRepository)
        createCommitmentUseCase = CreateCommitmentUseCase(commitmentRepository, reminderScheduler, idGenerator, clock, eventRepository)
        editCommitmentUseCase = EditCommitmentUseCase(commitmentRepository, reminderScheduler, clock, idGenerator, eventRepository)
    }

    // =========================================================
    // GOAL CORRECTIONS
    // =========================================================

    @Test
    fun `editGoal emits GOAL_UPDATED correction event with source USER`() = runTest {
        val goal = (createGoalUseCase(
            userId = "user-1",
            title = "Original Title",
            description = "Desc",
            why = "Why",
            importance = 5,
            targetAt = null
        ) as Result.Success).data

        clock.advanceTimeBy(1000)

        val result = editGoalUseCase(
            userId = "user-1",
            goalId = goal.id,
            title = "Corrected Title",
            description = null,
            why = null,
            importance = null,
            targetAt = null
        )

        assertTrue(result is Result.Success)
        // A GOAL_UPDATED correction event must exist for this goal with source=USER
        val correctionEvents = eventRepository.events.values.filter {
            it.entityId == goal.id && it.type == EventType.GOAL_UPDATED
        }
        assertEquals(1, correctionEvents.size)
        assertEquals(EntitySource.USER, correctionEvents.first().source)
        assertEquals("GOAL", correctionEvents.first().entityType)
    }

    @Test
    fun `editGoal updates fields and corrected information takes precedence`() = runTest {
        val goal = (createGoalUseCase(
            userId = "user-1",
            title = "Original",
            description = "Desc",
            why = "Why",
            importance = 3,
            targetAt = null
        ) as Result.Success).data

        val result = editGoalUseCase(
            userId = "user-1",
            goalId = goal.id,
            title = "Corrected",
            description = "New Desc",
            why = null,     // not provided — should remain original
            importance = 7,
            targetAt = 99999L
        )

        assertTrue(result is Result.Success)
        val updated = (result as Result.Success).data
        assertEquals("Corrected", updated.title)
        assertEquals("New Desc", updated.description)
        assertEquals("Why", updated.why)   // unchanged
        assertEquals(7, updated.importance)
        assertEquals(99999L, updated.targetAt)
    }

    @Test
    fun `editGoal with blank title is rejected and no correction event emitted`() = runTest {
        val goal = (createGoalUseCase(
            userId = "user-1",
            title = "Valid Title",
            description = "Desc",
            why = "Why",
            importance = 5,
            targetAt = null
        ) as Result.Success).data

        val eventCountBefore = eventRepository.events.size

        val result = editGoalUseCase(
            userId = "user-1",
            goalId = goal.id,
            title = "   ", // invalid blank
            description = null,
            why = null,
            importance = null,
            targetAt = null
        )

        assertTrue(result is Result.Failure)
        // No additional events must have been emitted
        assertEquals(eventCountBefore, eventRepository.events.size)
    }

    @Test
    fun `editGoal for non-existent goal is rejected and no event emitted`() = runTest {
        val eventCountBefore = eventRepository.events.size

        val result = editGoalUseCase(
            userId = "user-1",
            goalId = "non-existent-id",
            title = "Something",
            description = null,
            why = null,
            importance = null,
            targetAt = null
        )

        assertTrue(result is Result.Failure)
        assertEquals(eventCountBefore, eventRepository.events.size)
    }

    // =========================================================
    // COMMITMENT CORRECTIONS
    // =========================================================

    @Test
    fun `editCommitment emits COMMITMENT_UPDATED correction event with source USER`() = runTest {
        val commitment = (createCommitmentUseCase(
            userId = "user-1",
            goalId = null,
            planId = null,
            taskId = null,
            parentCommitmentId = null,
            title = "Original Commitment",
            description = "Desc",
            priority = 1
        ) as Result.Success).data

        clock.advanceTimeBy(1000)

        val result = editCommitmentUseCase(
            userId = "user-1",
            commitmentId = commitment.id,
            title = "Corrected Commitment",
            description = null,
            priority = null,
            scheduledStartAt = null,
            scheduledEndAt = null
        )

        assertTrue(result is Result.Success)
        // A COMMITMENT_UPDATED correction event must exist for this commitment with source=USER
        val correctionEvents = eventRepository.events.values.filter {
            it.entityId == commitment.id && it.type == EventType.COMMITMENT_UPDATED
        }
        assertEquals(1, correctionEvents.size)
        assertEquals(EntitySource.USER, correctionEvents.first().source)
        assertEquals("COMMITMENT", correctionEvents.first().entityType)
    }

    @Test
    fun `editCommitment updates fields correctly`() = runTest {
        val commitment = (createCommitmentUseCase(
            userId = "user-1",
            goalId = null,
            planId = null,
            taskId = null,
            parentCommitmentId = null,
            title = "Original",
            description = "Old Desc",
            priority = 1
        ) as Result.Success).data

        clock.advanceTimeBy(500)

        val result = editCommitmentUseCase(
            userId = "user-1",
            commitmentId = commitment.id,
            title = "Updated",
            description = "New Desc",
            priority = 3,
            scheduledStartAt = 10000L,
            scheduledEndAt = 20000L
        )

        assertTrue(result is Result.Success)
        val updated = (result as Result.Success).data
        assertEquals("Updated", updated.title)
        assertEquals("New Desc", updated.description)
        assertEquals(3, updated.priority)
        assertEquals(10000L, updated.scheduledStartAt)
        assertEquals(20000L, updated.scheduledEndAt)
        assertTrue(updated.updatedAt > commitment.updatedAt)
    }

    @Test
    fun `editCommitment with blank title is rejected and no correction event emitted`() = runTest {
        val commitment = (createCommitmentUseCase(
            userId = "user-1",
            goalId = null,
            planId = null,
            taskId = null,
            parentCommitmentId = null,
            title = "Valid",
            description = "Desc",
            priority = 1
        ) as Result.Success).data

        val eventCountBefore = eventRepository.events.size

        val result = editCommitmentUseCase(
            userId = "user-1",
            commitmentId = commitment.id,
            title = "",  // invalid blank
            description = null,
            priority = null,
            scheduledStartAt = null,
            scheduledEndAt = null
        )

        assertTrue(result is Result.Failure)
        assertEquals(eventCountBefore, eventRepository.events.size)
    }
}
