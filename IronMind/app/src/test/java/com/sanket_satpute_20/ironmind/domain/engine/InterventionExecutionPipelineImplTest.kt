package com.sanket_satpute_20.ironmind.domain.engine

import com.sanket_satpute_20.ironmind.domain.ai.AIOutput
import com.sanket_satpute_20.ironmind.domain.ai.InterventionType
import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.decision.DecisionResult
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecord
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionResolutionReason
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionState
import com.sanket_satpute_20.ironmind.domain.repository.InterventionRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeIronLogger
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class InterventionExecutionPipelineImplTest {

    private lateinit var decisionEngine: FakeDecisionEngine
    private lateinit var interventionRepository: FakeInterventionRepository
    private lateinit var pipeline: InterventionExecutionPipelineImpl
    private lateinit var logger: FakeIronLogger

    @Before
    fun setup() {
        decisionEngine = FakeDecisionEngine()
        interventionRepository = FakeInterventionRepository()
        logger = FakeIronLogger()

        pipeline = InterventionExecutionPipelineImpl(
            decisionEngine = decisionEngine,
            interventionRepository = interventionRepository,
            idGenerator = object : IdGenerator {
                override fun generateId() = "int-123"
            },
            clock = object : Clock {
                override fun currentTimeMillis() = 1000L
            },
            logger = logger
        )
    }

    @Test
    fun `propose with STAY_SILENT immediately suppresses intervention`() = runTest {
        val candidate = AIOutput.InterventionRecommendation(
            interventionType = InterventionType.STAY_SILENT,
            recommendation = "Nothing",
            reason = "Stay silent",
            confidence = 0.9f
        )

        val result = pipeline.propose("u1", candidate)

        assertTrue(result is Result.Success)
        val record = (result as Result.Success).data
        assertEquals(InterventionState.SUPPRESSED, record.state)
        
        val saved = interventionRepository.getById("int-123") as Result.Success
        assertEquals(InterventionState.SUPPRESSED, saved.data?.state)
    }

    @Test
    fun `propose evaluates and becomes APPROVED if execute`() = runTest {
        decisionEngine.nextResult = DecisionResult.EXECUTE
        val candidate = AIOutput.InterventionRecommendation(
            interventionType = InterventionType.REMIND,
            recommendation = "Remind",
            reason = "Description",
            confidence = 0.9f
        )

        val result = pipeline.propose("u1", candidate)

        assertTrue(result is Result.Success)
        val record = (result as Result.Success).data
        assertEquals(InterventionState.APPROVED, record.state)
    }

    @Test
    fun `propose evaluates and becomes SUPPRESSED if stay silent or ask user not allowed`() = runTest {
        decisionEngine.nextResult = DecisionResult.STAY_SILENT
        val candidate = AIOutput.InterventionRecommendation(
            interventionType = InterventionType.REMIND,
            recommendation = "Remind",
            reason = "Description",
            confidence = 0.9f
        )

        val result = pipeline.propose("u1", candidate)

        assertTrue(result is Result.Success)
        val record = (result as Result.Success).data
        assertEquals(InterventionState.SUPPRESSED, record.state)
    }

    @Test
    fun `trigger transitions from APPROVED to TRIGGERED`() = runTest {
        val record = InterventionRecord(
            id = "int-123",
            userId = "u1",
            type = InterventionType.REMIND,
            state = InterventionState.APPROVED,
            title = "Test",
            description = "Desc",
            createdAt = 1000L,
            updatedAt = 1000L
        )
        interventionRepository.save(record)

        val result = pipeline.trigger("int-123")

        assertTrue(result is Result.Success)
        assertEquals(InterventionState.TRIGGERED, (result as Result.Success).data.state)
    }

    @Test
    fun `deliver transitions from TRIGGERED to DELIVERED`() = runTest {
        val record = InterventionRecord(
            id = "int-123",
            userId = "u1",
            type = InterventionType.REMIND,
            state = InterventionState.TRIGGERED,
            title = "Test",
            description = "Desc",
            createdAt = 1000L,
            updatedAt = 1000L
        )
        interventionRepository.save(record)

        val result = pipeline.deliver("int-123")

        assertTrue(result is Result.Success)
        assertEquals(InterventionState.DELIVERED, (result as Result.Success).data.state)
    }

    @Test
    fun `resolve sets FAILED for technical failure`() = runTest {
        val record = InterventionRecord(
            id = "int-123",
            userId = "u1",
            type = InterventionType.REMIND,
            state = InterventionState.DELIVERED,
            title = "Test",
            description = "Desc",
            createdAt = 1000L,
            updatedAt = 1000L
        )
        interventionRepository.save(record)

        val result = pipeline.resolve("int-123", InterventionResolutionReason.TECHNICAL_FAILURE)

        assertTrue(result is Result.Success)
        val updated = (result as Result.Success).data
        assertEquals(InterventionState.FAILED, updated.state)
        assertEquals(InterventionResolutionReason.TECHNICAL_FAILURE, updated.resolutionReason)
    }

    @Test
    fun `resolve sets OUTCOME for OVERRIDDEN`() = runTest {
        val record = InterventionRecord(
            id = "int-123",
            userId = "u1",
            type = InterventionType.REMIND,
            state = InterventionState.DELIVERED,
            title = "Test",
            description = "Desc",
            createdAt = 1000L,
            updatedAt = 1000L
        )
        interventionRepository.save(record)

        val result = pipeline.resolve("int-123", InterventionResolutionReason.OVERRIDDEN)

        assertTrue(result is Result.Success)
        val updated = (result as Result.Success).data
        assertEquals(InterventionState.OUTCOME, updated.state)
        assertEquals(InterventionResolutionReason.OVERRIDDEN, updated.resolutionReason)
    }
}

class FakeInterventionRepository : InterventionRepository {
    private val store = mutableMapOf<String, InterventionRecord>()

    override suspend fun save(record: InterventionRecord): Result<InterventionRecord, Exception> {
        store[record.id] = record
        return Result.Success(record)
    }

    override suspend fun getById(id: String): Result<InterventionRecord?, Exception> {
        return Result.Success(store[id])
    }
}
