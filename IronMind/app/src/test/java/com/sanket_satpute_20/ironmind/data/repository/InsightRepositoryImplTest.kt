package com.sanket_satpute_20.ironmind.data.repository

import com.sanket_satpute_20.ironmind.data.local.dao.IronMindDao
import com.sanket_satpute_20.ironmind.data.local.entity.MemoryEntity
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.MemoryConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.context.EpistemicAuthority
import com.sanket_satpute_20.ironmind.domain.model.context.SourceFreshness
import com.sanket_satpute_20.ironmind.domain.model.insight.ExplainabilityBlock
import com.sanket_satpute_20.ironmind.domain.model.insight.InsightCategory
import com.sanket_satpute_20.ironmind.domain.model.insight.InsightJsonCodec
import com.sanket_satpute_20.ironmind.domain.model.insight.InsightReviewStatus
import com.sanket_satpute_20.ironmind.domain.model.insight.PersonalInsight
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class InsightRepositoryImplTest {

    private lateinit var dao: IronMindDao
    private lateinit var repository: InsightRepositoryImpl

    private val userId = "user-insight-test"
    private val now = 1700000000000L

    @Before
    fun setUp() {
        dao = mockk(relaxed = true)
        repository = InsightRepositoryImpl(dao)
    }

    @Test
    fun testJsonCodec_roundtripSerialization() {
        val original = PersonalInsight(
            id = "ins-101",
            userId = userId,
            category = InsightCategory.PRODUCTIVITY,
            title = "Execution Tempo",
            explanation = "High completion tempo observed",
            explainabilityBlock = ExplainabilityBlock(
                whatWasObserved = "Observed 5 completed commitments",
                observationPeriod = "Nov 01 - Nov 07",
                supportingEvidenceDescription = "5 commitments",
                whyItMatters = "Execution reliability",
                uncertaintyOrLimitations = "Limited to recorded events",
                suggestedNextSteps = "Continue current cadence"
            ),
            supportingPatternIds = listOf("pat-1", "pat-2"),
            supportingBarrierIds = listOf("bar-1"),
            supportingEvidenceIds = listOf("ev-1", "ev-2"),
            linkedRecommendationId = "rec-42",
            observationWindowStartMs = now - 604800000L,
            observationWindowEndMs = now,
            firstObservedAt = now - 604800000L,
            lastObservedAt = now,
            confidence = 0.88f,
            epistemicAuthority = EpistemicAuthority.DERIVED_CALCULATION,
            sourceProvenance = listOf("Room:commitment", "Room:task"),
            sourceFreshness = SourceFreshness.CURRENT,
            reviewStatus = InsightReviewStatus.UNREVIEWED,
            userFeedback = null,
            reviewedAt = null,
            createdAt = now,
            updatedAt = now
        )

        val json = InsightJsonCodec.encodeToJson(original)
        assertTrue(json.isNotBlank())

        val decoded = InsightJsonCodec.decodeFromJson(
            id = original.id,
            userId = original.userId,
            jsonString = json
        )

        assertNotNull(decoded)
        assertEquals(original.id, decoded!!.id)
        assertEquals(original.userId, decoded.userId)
        assertEquals(original.category, decoded.category)
        assertEquals(original.title, decoded.title)
        assertEquals(original.explanation, decoded.explanation)
        assertEquals(original.explainabilityBlock.whatWasObserved, decoded.explainabilityBlock.whatWasObserved)
        assertEquals(original.explainabilityBlock.suggestedNextSteps, decoded.explainabilityBlock.suggestedNextSteps)
        assertEquals(original.supportingPatternIds, decoded.supportingPatternIds)
        assertEquals(original.supportingBarrierIds, decoded.supportingBarrierIds)
        assertEquals(original.supportingEvidenceIds, decoded.supportingEvidenceIds)
        assertEquals(original.linkedRecommendationId, decoded.linkedRecommendationId)
        assertEquals(original.epistemicAuthority, decoded.epistemicAuthority)
        assertEquals(original.sourceFreshness, decoded.sourceFreshness)
        assertEquals(original.confidence, decoded.confidence, 0.01f)
    }

    @Test
    fun testSaveAndGetInsight_savesToMemoryEntity() = runBlocking {
        val insight = PersonalInsight(
            id = "ins-save-1",
            userId = userId,
            category = InsightCategory.FOCUS,
            title = "Focus Session Adherence",
            explanation = "Completed 3 focus sessions",
            explainabilityBlock = ExplainabilityBlock("3 sessions", "7d", "Sessions", "Deep work", "None", "Continue"),
            observationWindowStartMs = now - 604800000L,
            observationWindowEndMs = now,
            firstObservedAt = now - 604800000L,
            lastObservedAt = now,
            confidence = 0.9f,
            epistemicAuthority = EpistemicAuthority.DERIVED_CALCULATION,
            createdAt = now,
            updatedAt = now
        )

        val slot = slot<MemoryEntity>()
        every { dao.insertMemory(capture(slot)) } returns Unit

        val saveResult = repository.saveInsight(insight)
        assertTrue(saveResult is Result.Success)

        verify(exactly = 1) { dao.insertMemory(any()) }
        assertEquals("ins-save-1", slot.captured.id)
        assertEquals(userId, slot.captured.userId)
        assertEquals(InsightRepositoryImpl.MEMORY_TYPE_PERSONAL_INSIGHT, slot.captured.type)
        assertTrue(slot.captured.content.contains("Focus Session Adherence"))
    }

    @Test
    fun testUpdateReviewStatus_updatesAndSaves() = runBlocking {
        val insight = PersonalInsight(
            id = "ins-update-1",
            userId = userId,
            category = InsightCategory.PRODUCTIVITY,
            title = "Title",
            explanation = "Exp",
            explainabilityBlock = ExplainabilityBlock("Obs", "Per", "Ev", "Why", "Unc", "Next"),
            observationWindowStartMs = now - 1000,
            observationWindowEndMs = now,
            firstObservedAt = now - 1000,
            lastObservedAt = now,
            confidence = 0.8f,
            epistemicAuthority = EpistemicAuthority.DERIVED_CALCULATION,
            createdAt = now,
            updatedAt = now
        )

        val memoryEntity = MemoryEntity(
            id = insight.id,
            userId = userId,
            type = InsightRepositoryImpl.MEMORY_TYPE_PERSONAL_INSIGHT,
            content = InsightJsonCodec.encodeToJson(insight),
            source = "AI_INFERRED",
            confidence = 0.8f,
            evidenceCount = 1,
            firstObservedAt = now - 1000,
            lastObservedAt = now,
            confirmationState = "PENDING",
            status = "ACTIVE",
            expiresAt = null,
            createdAt = now,
            updatedAt = now,
            schemaVersion = 1
        )

        every { dao.getMemoryById(insight.id) } returns memoryEntity
        val savedSlot = slot<MemoryEntity>()
        every { dao.insertMemory(capture(savedSlot)) } returns Unit

        val updateRes = repository.updateReviewStatus(
            id = insight.id,
            userId = userId,
            status = InsightReviewStatus.USEFUL,
            feedback = "Very helpful insight",
            reviewedAt = now + 5000L
        )

        assertTrue(updateRes is Result.Success)
        verify(exactly = 1) { dao.insertMemory(any()) }
        assertTrue(savedSlot.captured.content.contains("Very helpful insight"))
        assertTrue(savedSlot.captured.content.contains("USEFUL"))
        // Proves that marking USEFUL did NOT alter confirmationState!
        assertEquals(MemoryConfirmationState.UNCONFIRMED.name, savedSlot.captured.confirmationState)
    }

    @Test
    fun testUpdateFactualConfirmation_updatesConfirmationStateAndAuthority() = runBlocking {
        val insight = PersonalInsight(
            id = "ins-confirm-1",
            userId = userId,
            category = InsightCategory.PRODUCTIVITY,
            title = "Title",
            explanation = "Exp",
            explainabilityBlock = ExplainabilityBlock("Obs", "Per", "Ev", "Why", "Unc", "Next"),
            observationWindowStartMs = now - 1000,
            observationWindowEndMs = now,
            firstObservedAt = now - 1000,
            lastObservedAt = now,
            confidence = 0.8f,
            epistemicAuthority = EpistemicAuthority.AI_HYPOTHESIS,
            confirmationState = MemoryConfirmationState.UNCONFIRMED,
            createdAt = now,
            updatedAt = now
        )

        val memoryEntity = MemoryEntity(
            id = insight.id,
            userId = userId,
            type = InsightRepositoryImpl.MEMORY_TYPE_PERSONAL_INSIGHT,
            content = InsightJsonCodec.encodeToJson(insight),
            source = "AI",
            confidence = 0.8f,
            evidenceCount = 1,
            firstObservedAt = now - 1000,
            lastObservedAt = now,
            confirmationState = MemoryConfirmationState.UNCONFIRMED.name,
            status = "ACTIVE",
            expiresAt = null,
            createdAt = now,
            updatedAt = now,
            schemaVersion = 1
        )

        every { dao.getMemoryById(insight.id) } returns memoryEntity
        val savedSlot = slot<MemoryEntity>()
        every { dao.insertMemory(capture(savedSlot)) } returns Unit

        val confirmRes = repository.updateFactualConfirmation(
            id = insight.id,
            userId = userId,
            confirmationState = MemoryConfirmationState.USER_CONFIRMED,
            correction = null,
            timestamp = now + 5000L
        )

        assertTrue(confirmRes is Result.Success)
        assertEquals(MemoryConfirmationState.USER_CONFIRMED.name, savedSlot.captured.confirmationState)
        val decoded = InsightJsonCodec.decodeFromJson(insight.id, userId, savedSlot.captured.content)
        assertNotNull(decoded)
        assertEquals(EpistemicAuthority.USER_CONFIRMED_FACT, decoded!!.epistemicAuthority)
        assertEquals(MemoryConfirmationState.USER_CONFIRMED, decoded.confirmationState)
    }
}

