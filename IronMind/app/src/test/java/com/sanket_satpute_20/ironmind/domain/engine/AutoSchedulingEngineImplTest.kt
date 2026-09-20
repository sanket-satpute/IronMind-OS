package com.sanket_satpute_20.ironmind.domain.engine

import com.sanket_satpute_20.ironmind.domain.model.AutonomyCapability
import com.sanket_satpute_20.ironmind.domain.model.Commitment
import com.sanket_satpute_20.ironmind.domain.model.CommitmentStatus
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.decision.CandidateAction
import com.sanket_satpute_20.ironmind.domain.model.decision.DecisionResult
import com.sanket_satpute_20.ironmind.domain.model.decision.EvaluationContext
import com.sanket_satpute_20.ironmind.domain.model.scheduling.ScheduleCandidate
import com.sanket_satpute_20.ironmind.domain.model.scheduling.ScheduleResult
import com.sanket_satpute_20.ironmind.testutil.fake.FakeCommitmentRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeIronLogger
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AutoSchedulingEngineImplTest {

    private lateinit var commitmentRepository: FakeCommitmentRepository
    private lateinit var logger: FakeIronLogger
    private lateinit var decisionEngine: FakeDecisionEngine
    private lateinit var engine: AutoSchedulingEngineImpl

    private var currentTimeMs: Long = 1000000L

    @Before
    fun setup() {
        commitmentRepository = FakeCommitmentRepository()
        logger = FakeIronLogger()
        decisionEngine = FakeDecisionEngine()

        engine = AutoSchedulingEngineImpl(
            decisionEngine = decisionEngine,
            commitmentRepository = commitmentRepository,
            logger = logger,
            timeProvider = { currentTimeMs }
        )
    }

    @Test
    fun `schedule creates commitment when authorized and no conflict`() = runTest {
        decisionEngine.nextResult = DecisionResult.EXECUTE

        val candidate = ScheduleCandidate(
            title = "Test",
            description = "Desc",
            proposedStartTime = 1000L,
            proposedEndTime = 2000L
        )

        val result = engine.schedule("u1", candidate)

        assertTrue(result is ScheduleResult.Scheduled)
        val commitment = (result as ScheduleResult.Scheduled).commitment
        assertEquals("Test", commitment.title)
        assertEquals(1000L, commitment.scheduledStartAt)

        val saved = commitmentRepository.getCommitment(commitment.id).getOrNull()
        assertEquals(commitment, saved)
    }

    @Test
    fun `schedule rejects when authorized but there is a time conflict`() = runTest {
        decisionEngine.nextResult = DecisionResult.EXECUTE

        // Existing commitment overlapping
        commitmentRepository.saveCommitment(
            Commitment(
                id = "c1",
                userId = "u1",
                title = "Existing",
                description = "",
                committedAt = 0L,
                scheduledStartAt = 1500L,
                scheduledEndAt = 2500L,
                status = CommitmentStatus.COMMITTED,
                priority = 0,
                source = EntitySource.USER,
                createdAt = 0L,
                updatedAt = 0L
            )
        )

        val candidate = ScheduleCandidate(
            title = "Test",
            description = "Desc",
            proposedStartTime = 1000L,
            proposedEndTime = 2000L
        )

        val result = engine.schedule("u1", candidate)

        assertTrue(result is ScheduleResult.Conflict)
    }

    @Test
    fun `schedule returns RequiresPermission when DecisionEngine says ASK_USER`() = runTest {
        decisionEngine.nextResult = DecisionResult.ASK_USER

        val candidate = ScheduleCandidate(
            title = "Test",
            description = "Desc",
            proposedStartTime = 1000L,
            proposedEndTime = 2000L
        )

        val result = engine.schedule("u1", candidate)

        assertTrue(result is ScheduleResult.RequiresPermission)
    }

    @Test
    fun `schedule returns NotAllowed when DecisionEngine says STAY_SILENT`() = runTest {
        decisionEngine.nextResult = DecisionResult.STAY_SILENT

        val candidate = ScheduleCandidate(
            title = "Test",
            description = "Desc",
            proposedStartTime = 1000L,
            proposedEndTime = 2000L
        )

        val result = engine.schedule("u1", candidate)

        assertTrue(result is ScheduleResult.NotAllowed)
    }
}

class FakeDecisionEngine : DecisionEngine {
    var nextResult: DecisionResult = DecisionResult.EXECUTE

    override suspend fun evaluate(
        userId: String,
        candidate: CandidateAction,
        context: EvaluationContext
    ): DecisionResult {
        return nextResult
    }
}
