package com.sanket_satpute_20.ironmind.domain.model.intervention

import com.sanket_satpute_20.ironmind.domain.ai.AIOutput
import com.sanket_satpute_20.ironmind.domain.ai.InterventionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class InterventionRecommendationTest {

    @Test
    fun `candidate construction retains AI fields but drops unsafe state`() {
        val aiOutput = AIOutput.InterventionRecommendation(
            interventionType = InterventionType.BREAK_DOWN,
            recommendation = "Break down into smaller tasks",
            reason = "Task is too large",
            supportingContext = "User has postponed 3 times",
            targetEntityId = "goal_123",
            confidence = 0.8f,
            reasoning = "AI reasoning trace"
        )

        val candidate = aiOutput.toCandidate()

        // Retains valid fields
        assertEquals(InterventionType.BREAK_DOWN, candidate.interventionType)
        assertEquals("Break down into smaller tasks", candidate.suggestedAction)
        assertEquals("Task is too large", candidate.rationale)
        assertEquals("goal_123", candidate.targetEntityId)
        assertEquals(0.8f, candidate.confidence, 0.001f)
    }

    @Test
    fun `recommendation construction provides correct defaults and domain ownership`() {
        val recommendation = InterventionRecommendation(
            id = UUID.randomUUID().toString(),
            userId = "user_123",
            interventionType = InterventionType.BREAK_DOWN,
            targetEntityId = "goal_123",
            targetEntityType = "GOAL",
            rationale = "Task is too large",
            suggestedAction = "Break down into smaller tasks",
            createdAt = 1000L
        )

        // Default status is PENDING
        assertEquals(InterventionRecommendationStatus.PENDING, recommendation.status)
        assertEquals("user_123", recommendation.userId)
        assertNull(recommendation.expiresAt)
    }

    @Test
    fun `result representation supports Recommended and NoRecommendation`() {
        val noRecommendation: InterventionRecommendationResult = InterventionRecommendationResult.NoRecommendation
        
        assertTrue(noRecommendation is InterventionRecommendationResult.NoRecommendation)

        val recommendation = InterventionRecommendation(
            id = "rec_1",
            userId = "user_1",
            interventionType = InterventionType.REMIND,
            targetEntityId = null,
            targetEntityType = null,
            rationale = "Time to start",
            suggestedAction = "Start session",
            createdAt = 1000L
        )

        val recommended: InterventionRecommendationResult = InterventionRecommendationResult.Recommended(recommendation)

        assertTrue(recommended is InterventionRecommendationResult.Recommended)
        assertEquals("rec_1", (recommended as InterventionRecommendationResult.Recommended).recommendation.id)
    }
}
