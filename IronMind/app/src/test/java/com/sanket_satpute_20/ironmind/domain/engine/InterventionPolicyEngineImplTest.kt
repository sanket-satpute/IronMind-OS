package com.sanket_satpute_20.ironmind.domain.engine

import com.sanket_satpute_20.ironmind.domain.ai.AIOutput
import com.sanket_satpute_20.ironmind.domain.ai.InterventionType
import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecord
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class InterventionPolicyEngineImplTest {

    private lateinit var interventionRepository: FakeInterventionRepository
    private lateinit var clock: FakeClock
    private lateinit var policyEngine: InterventionPolicyEngineImpl

    @Before
    fun setup() {
        interventionRepository = FakeInterventionRepository()
        // Start in the middle of the day so that "now - 1 hour" is still today
        val oneDayAndHalf = (24 + 12) * 60 * 60 * 1000L
        clock = FakeClock(oneDayAndHalf)
        policyEngine = InterventionPolicyEngineImpl(
            interventionRepository = interventionRepository,
            clock = clock,
            cooldownMillis = 15 * 60 * 1000L,
            interruptionBudget = 3
        )
    }

    @Test
    fun `evaluatePolicy allows when no recent interventions`() = runTest {
        val candidate = AIOutput.InterventionRecommendation(
            interventionType = InterventionType.REMIND,
            recommendation = "Do X",
            reason = "Because Y",
            confidence = 0.9f
        )
        val result = policyEngine.evaluatePolicy("u1", candidate)
        assertTrue(result.isAllowed)
    }

    @Test
    fun `evaluatePolicy blocks when budget exceeded`() = runTest {
        val now = clock.currentTimeMillis()
        // Add 3 delivered interventions today
        for (i in 1..3) {
            interventionRepository.save(InterventionRecord(
                id = "id$i",
                userId = "u1",
                type = InterventionType.REDIRECT,
                state = InterventionState.DELIVERED,
                title = "T$i",
                description = "D$i",
                createdAt = now - (1000 * 60 * 60 * i),
                updatedAt = now
            ))
        }

        val candidate = AIOutput.InterventionRecommendation(
            interventionType = InterventionType.REMIND,
            recommendation = "Do X",
            reason = "Because Y",
            confidence = 0.9f
        )
        val result = policyEngine.evaluatePolicy("u1", candidate)
        assertFalse(result.isAllowed)
        assertEquals("Interruption budget exceeded (3/3)", result.suppressionReason)
    }

    @Test
    fun `evaluatePolicy blocks duplicate`() = runTest {
        val now = clock.currentTimeMillis()
        interventionRepository.save(InterventionRecord(
            id = "id1",
            userId = "u1",
            type = InterventionType.REMIND,
            state = InterventionState.OUTCOME,
            title = "Do X",
            description = "Because Y",
            createdAt = now - (1000 * 60 * 60), // 1 hour ago (past cooldown, but same today)
            updatedAt = now
        ))

        val candidate = AIOutput.InterventionRecommendation(
            interventionType = InterventionType.REMIND,
            recommendation = "Do X",
            reason = "Because Y",
            confidence = 0.9f
        )
        val result = policyEngine.evaluatePolicy("u1", candidate)
        assertFalse(result.isAllowed)
        assertTrue(result.isDuplicate)
    }

    @Test
    fun `evaluatePolicy blocks for cooldown`() = runTest {
        val now = clock.currentTimeMillis()
        interventionRepository.save(InterventionRecord(
            id = "id1",
            userId = "u1",
            type = InterventionType.REMIND,
            state = InterventionState.OUTCOME,
            title = "Do Something Else",
            description = "Different reason",
            createdAt = now - (5 * 60 * 1000L), // 5 mins ago (within 15 min cooldown)
            updatedAt = now
        ))

        val candidate = AIOutput.InterventionRecommendation(
            interventionType = InterventionType.REMIND, // Same type
            recommendation = "Do X",
            reason = "Because Y",
            confidence = 0.9f
        )
        val result = policyEngine.evaluatePolicy("u1", candidate)
        assertFalse(result.isAllowed)
        assertTrue(result.isCooldownActive)
    }

    @Test
    fun `evaluatePolicy blocks for active primary intervention`() = runTest {
        val now = clock.currentTimeMillis()
        interventionRepository.save(InterventionRecord(
            id = "id1",
            userId = "u1",
            type = InterventionType.REDIRECT,
            state = InterventionState.TRIGGERED, // Active
            title = "Different",
            description = "Different reason",
            createdAt = now - (30 * 60 * 1000L), // 30 mins ago
            updatedAt = now
        ))

        val candidate = AIOutput.InterventionRecommendation(
            interventionType = InterventionType.REMIND,
            recommendation = "Do X",
            reason = "Because Y",
            confidence = 0.9f
        )
        val result = policyEngine.evaluatePolicy("u1", candidate)
        assertFalse(result.isAllowed)
        assertEquals("Another primary intervention is currently active", result.suppressionReason)
    }
}

class FakeClock(var time: Long = 0L) : Clock {
    override fun currentTimeMillis(): Long = time
}
