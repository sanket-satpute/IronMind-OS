package com.sanket_satpute_20.ironmind.data.repository

import com.sanket_satpute_20.ironmind.data.local.entity.InterventionRecommendationEntity
import com.sanket_satpute_20.ironmind.domain.ai.InterventionObjective
import com.sanket_satpute_20.ironmind.domain.ai.InterventionType
import com.sanket_satpute_20.ironmind.domain.model.intervention.CausalHypothesisReference
import com.sanket_satpute_20.ironmind.domain.model.intervention.CausalHypothesisType
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecommendation
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecommendationStatus
import org.junit.Assert.*
import org.junit.Test

class InterventionRecommendationMapperTest {

    private fun createEntity(
        id: String = "rec-1",
        userId: String = "user-1",
        interventionType: String = "REMIND",
        objective: String? = "INITIATE_ACTION",
        targetEntityId: String? = "goal-1",
        targetEntityType: String? = "GOAL",
        rationale: String = "Action needed",
        suggestedAction: String = "Work on goal",
        status: String = "PENDING",
        createdAt: Long = 1000L,
        expiresAt: Long? = 2000L,
        causalHypothesisId: String? = null,
        causalHypothesisType: String? = null
    ) = InterventionRecommendationEntity(
        id = id,
        userId = userId,
        interventionType = interventionType,
        objective = objective,
        targetEntityId = targetEntityId,
        targetEntityType = targetEntityType,
        rationale = rationale,
        suggestedAction = suggestedAction,
        status = status,
        createdAt = createdAt,
        expiresAt = expiresAt,
        causalHypothesisId = causalHypothesisId,
        causalHypothesisType = causalHypothesisType
    )

    private fun createDomain(
        id: String = "rec-1",
        userId: String = "user-1",
        interventionType: InterventionType = InterventionType.REMIND,
        objective: InterventionObjective = InterventionObjective.INITIATE_ACTION,
        targetEntityId: String? = "goal-1",
        targetEntityType: String? = "GOAL",
        rationale: String = "Action needed",
        suggestedAction: String = "Work on goal",
        status: InterventionRecommendationStatus = InterventionRecommendationStatus.PENDING,
        createdAt: Long = 1000L,
        expiresAt: Long? = 2000L,
        causalHypothesis: CausalHypothesisReference? = null
    ) = InterventionRecommendation(
        id = id,
        userId = userId,
        interventionType = interventionType,
        objective = objective,
        targetEntityId = targetEntityId,
        targetEntityType = targetEntityType,
        rationale = rationale,
        suggestedAction = suggestedAction,
        status = status,
        createdAt = createdAt,
        expiresAt = expiresAt,
        causalHypothesis = causalHypothesis
    )

    @Test
    fun `toDomain maps valid PATTERN causal hypothesis correctly`() {
        val entity = createEntity(
            causalHypothesisId = "pat-100",
            causalHypothesisType = "PATTERN"
        )
        val domain = entity.toDomain()
        assertNotNull(domain)
        assertEquals(
            CausalHypothesisReference(id = "pat-100", type = CausalHypothesisType.PATTERN),
            domain?.causalHypothesis
        )
    }

    @Test
    fun `toDomain maps valid BARRIER causal hypothesis correctly`() {
        val entity = createEntity(
            causalHypothesisId = "bar-200",
            causalHypothesisType = "BARRIER"
        )
        val domain = entity.toDomain()
        assertNotNull(domain)
        assertEquals(
            CausalHypothesisReference(id = "bar-200", type = CausalHypothesisType.BARRIER),
            domain?.causalHypothesis
        )
    }

    @Test
    fun `toDomain maps null causal hypothesis columns to null`() {
        val entity = createEntity(
            causalHypothesisId = null,
            causalHypothesisType = null
        )
        val domain = entity.toDomain()
        assertNotNull(domain)
        assertNull(domain?.causalHypothesis)
    }

    @Test
    fun `toDomain fails closed to null for unknown causal hypothesis type string while preserving recommendation`() {
        val entity = createEntity(
            causalHypothesisId = "some-id",
            causalHypothesisType = "INVALID_TYPE"
        )
        val domain = entity.toDomain()
        assertNotNull("Recommendation itself must be preserved", domain)
        assertNull("Invalid type string must fail closed to null causalHypothesis", domain?.causalHypothesis)
    }

    @Test
    fun `toDomain fails closed to null for blank id or type while preserving recommendation`() {
        val entityBlankId = createEntity(
            causalHypothesisId = "   ",
            causalHypothesisType = "PATTERN"
        )
        val domainBlankId = entityBlankId.toDomain()
        assertNotNull(domainBlankId)
        assertNull(domainBlankId?.causalHypothesis)

        val entityBlankType = createEntity(
            causalHypothesisId = "pat-1",
            causalHypothesisType = "   "
        )
        val domainBlankType = entityBlankType.toDomain()
        assertNotNull(domainBlankType)
        assertNull(domainBlankType?.causalHypothesis)
    }

    @Test
    fun `toDomain fails closed to null for inconsistent nulls while preserving recommendation`() {
        val entityMissingType = createEntity(
            causalHypothesisId = "pat-1",
            causalHypothesisType = null
        )
        val domainMissingType = entityMissingType.toDomain()
        assertNotNull(domainMissingType)
        assertNull(domainMissingType?.causalHypothesis)

        val entityMissingId = createEntity(
            causalHypothesisId = null,
            causalHypothesisType = "PATTERN"
        )
        val domainMissingId = entityMissingId.toDomain()
        assertNotNull(domainMissingId)
        assertNull(domainMissingId?.causalHypothesis)
    }

    @Test
    fun `toDomain returns null when objective is null or invalid`() {
        val entityNullObj = createEntity(objective = null)
        assertNull(entityNullObj.toDomain())

        val entityInvalidObj = createEntity(objective = "NON_EXISTENT_OBJECTIVE")
        assertNull(entityInvalidObj.toDomain())
    }

    @Test
    fun `toEntity maps domain model with causal hypothesis to entity columns`() {
        val domain = createDomain(
            causalHypothesis = CausalHypothesisReference(id = "pat-focus", type = CausalHypothesisType.PATTERN)
        )
        val entity = domain.toEntity()
        assertEquals("pat-focus", entity.causalHypothesisId)
        assertEquals("PATTERN", entity.causalHypothesisType)
    }

    @Test
    fun `toEntity maps domain model without causal hypothesis to null entity columns`() {
        val domain = createDomain(causalHypothesis = null)
        val entity = domain.toEntity()
        assertNull(entity.causalHypothesisId)
        assertNull(entity.causalHypothesisType)
    }
}
