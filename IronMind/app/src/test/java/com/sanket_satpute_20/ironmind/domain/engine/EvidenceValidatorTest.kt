package com.sanket_satpute_20.ironmind.domain.engine

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.Event
import com.sanket_satpute_20.ironmind.domain.model.EventType
import com.sanket_satpute_20.ironmind.domain.model.Reflection
import com.sanket_satpute_20.ironmind.domain.model.observation.Observation
import com.sanket_satpute_20.ironmind.domain.model.observation.ObservationProvenance
import com.sanket_satpute_20.ironmind.domain.model.observation.ObservationSource
import com.sanket_satpute_20.ironmind.domain.model.observation.ObservationType
import com.sanket_satpute_20.ironmind.domain.model.pattern.EvidenceReference
import com.sanket_satpute_20.ironmind.domain.model.pattern.EvidenceResolutionResult
import com.sanket_satpute_20.ironmind.domain.model.pattern.EvidenceSourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EvidenceValidatorTest {

    private val validator = EvidenceValidator()
    private val userId = "user123"

    private fun createObservation(
        userId: String = this.userId,
        confidence: Float? = null,
        id: String = "obs1"
    ): Observation = Observation(
        id = id,
        userId = userId,
        type = ObservationType.APP_USAGE_SESSION,
        source = ObservationSource.APP_USAGE,
        occurredAt = 1000L,
        recordedAt = 2000L,
        subjectId = null,
        value = "val",
        context = "ctx",
        confidence = confidence,
        provenance = ObservationProvenance(ObservationSource.APP_USAGE, null, 1000L)
    )

    private fun createEvent(
        userId: String = this.userId,
        id: String = "evt1"
    ): Event = Event(
        id = id,
        userId = userId,
        type = EventType.TASK_COMPLETED,
        occurredAt = 1000L,
        recordedAt = 2000L,
        source = EntitySource.USER
    )

    private fun createReflection(
        userId: String = this.userId,
        content: String = "valid reflection",
        id: String = "ref1"
    ): Reflection = Reflection(
        id = id,
        userId = userId,
        targetEntityId = null,
        targetEntityType = null,
        content = content,
        sentiment = null,
        createdAt = 1000L
    )

    @Test
    fun testEmptyInput_returnsEmptySuccess() {
        val result = validator.validate(userId, emptyList())
        assertTrue(result is Result.Success)
        assertEquals(0, (result as Result.Success).data.size)
    }

    @Test
    fun testValidObservation_accepted() {
        val obs = createObservation()
        val result = validator.validate(userId, listOf(
            EvidenceResolutionResult.Resolved(obs.id, EvidenceSourceType.OBSERVATION, obs)
        ))
        
        assertTrue(result is Result.Success)
        val data = (result as Result.Success).data
        assertEquals(1, data.size)
        assertEquals(EvidenceReference(obs.id, EvidenceSourceType.OBSERVATION), data[0].reference)
    }

    @Test
    fun testObservationConfidenceRules() {
        val obsNull = createObservation(id = "1", confidence = null)
        val obsZero = createObservation(id = "2", confidence = 0.0f)
        val obsOne = createObservation(id = "3", confidence = 1.0f)
        val obsNegative = createObservation(id = "4", confidence = -0.1f)
        val obsOver = createObservation(id = "5", confidence = 1.1f)

        val result = validator.validate(userId, listOf(
            EvidenceResolutionResult.Resolved(obsNull.id, EvidenceSourceType.OBSERVATION, obsNull),
            EvidenceResolutionResult.Resolved(obsZero.id, EvidenceSourceType.OBSERVATION, obsZero),
            EvidenceResolutionResult.Resolved(obsOne.id, EvidenceSourceType.OBSERVATION, obsOne),
            EvidenceResolutionResult.Resolved(obsNegative.id, EvidenceSourceType.OBSERVATION, obsNegative),
            EvidenceResolutionResult.Resolved(obsOver.id, EvidenceSourceType.OBSERVATION, obsOver)
        ))
        
        assertTrue(result is Result.Success)
        val data = (result as Result.Success).data
        assertEquals(3, data.size)
        assertTrue(data.any { it.reference.sourceId == "1" })
        assertTrue(data.any { it.reference.sourceId == "2" })
        assertTrue(data.any { it.reference.sourceId == "3" })
        // 4 and 5 are excluded
    }

    @Test
    fun testValidEvent_accepted() {
        val evt = createEvent()
        val result = validator.validate(userId, listOf(
            EvidenceResolutionResult.Resolved(evt.id, EvidenceSourceType.EVENT, evt)
        ))
        
        assertTrue(result is Result.Success)
        assertEquals(1, (result as Result.Success).data.size)
    }

    @Test
    fun testReflectionContentRules() {
        val refValid = createReflection(id = "1", content = "valid")
        val refBlank = createReflection(id = "2", content = "")
        val refWhitespace = createReflection(id = "3", content = "   ")

        val result = validator.validate(userId, listOf(
            EvidenceResolutionResult.Resolved(refValid.id, EvidenceSourceType.REFLECTION, refValid),
            EvidenceResolutionResult.Resolved(refBlank.id, EvidenceSourceType.REFLECTION, refBlank),
            EvidenceResolutionResult.Resolved(refWhitespace.id, EvidenceSourceType.REFLECTION, refWhitespace)
        ))
        
        assertTrue(result is Result.Success)
        val data = (result as Result.Success).data
        assertEquals(1, data.size)
        assertEquals("1", data[0].reference.sourceId)
    }

    @Test
    fun testOwnershipMismatch_excluded() {
        val obsOther = createObservation(userId = "otherUser", id = "obs1")
        val result = validator.validate(userId, listOf(
            EvidenceResolutionResult.Resolved(obsOther.id, EvidenceSourceType.OBSERVATION, obsOther)
        ))
        
        assertTrue(result is Result.Success)
        assertEquals(0, (result as Result.Success).data.size)
    }

    @Test
    fun testDiscardedResolutionStates_excluded() {
        val result = validator.validate(userId, listOf(
            EvidenceResolutionResult.Missing("1", EvidenceSourceType.OBSERVATION),
            EvidenceResolutionResult.Unsupported("2", EvidenceSourceType.OBSERVATION),
            EvidenceResolutionResult.Ambiguous("3", EvidenceSourceType.LEGACY_AMBIGUOUS),
            EvidenceResolutionResult.OwnershipMismatch("4", EvidenceSourceType.OBSERVATION)
        ))
        
        assertTrue(result is Result.Success)
        assertEquals(0, (result as Result.Success).data.size)
    }

    @Test
    fun testSystemicError_failsEntireValidation() {
        val obs = createObservation()
        val result = validator.validate(userId, listOf(
            EvidenceResolutionResult.Resolved(obs.id, EvidenceSourceType.OBSERVATION, obs),
            EvidenceResolutionResult.Error("2", EvidenceSourceType.OBSERVATION, RuntimeException("DB down"))
        ))
        
        assertTrue(result is Result.Failure)
        val error = (result as Result.Failure).error
        assertEquals("DB down", error.message)
    }

    @Test
    fun testDeduplication() {
        val obs = createObservation(id = "dup")
        
        val result = validator.validate(userId, listOf(
            EvidenceResolutionResult.Resolved(obs.id, EvidenceSourceType.OBSERVATION, obs),
            EvidenceResolutionResult.Resolved(obs.id, EvidenceSourceType.OBSERVATION, obs),
            EvidenceResolutionResult.Missing(obs.id, EvidenceSourceType.OBSERVATION) // Invalid mixed in
        ))
        
        assertTrue(result is Result.Success)
        val data = (result as Result.Success).data
        assertEquals(1, data.size)
        assertEquals("dup", data[0].reference.sourceId)
    }

    @Test
    fun testUnsupportedResolvedEntityType_excluded() {
        // e.g. String instead of Observation
        val result = validator.validate(userId, listOf(
            EvidenceResolutionResult.Resolved("1", EvidenceSourceType.OBSERVATION, "Not a domain entity")
        ))
        
        assertTrue(result is Result.Success)
        assertEquals(0, (result as Result.Success).data.size)
    }
    
    @Test
    fun testDeterminismAndIdempotency() {
        val obs = createObservation()
        val input = listOf(EvidenceResolutionResult.Resolved(obs.id, EvidenceSourceType.OBSERVATION, obs))
        
        val result1 = validator.validate(userId, input)
        val result2 = validator.validate(userId, input)
        
        assertEquals(result1, result2)
    }
}
