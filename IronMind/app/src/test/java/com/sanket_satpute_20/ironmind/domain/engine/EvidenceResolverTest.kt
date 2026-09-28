package com.sanket_satpute_20.ironmind.domain.engine

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Event
import com.sanket_satpute_20.ironmind.domain.model.EventType
import com.sanket_satpute_20.ironmind.domain.model.Reflection
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.observation.Observation
import com.sanket_satpute_20.ironmind.domain.model.observation.ObservationProvenance
import com.sanket_satpute_20.ironmind.domain.model.observation.ObservationSource
import com.sanket_satpute_20.ironmind.domain.model.observation.ObservationType
import com.sanket_satpute_20.ironmind.domain.model.pattern.EvidenceReference
import com.sanket_satpute_20.ironmind.domain.model.pattern.EvidenceResolutionResult
import com.sanket_satpute_20.ironmind.domain.model.pattern.EvidenceSourceType
import com.sanket_satpute_20.ironmind.domain.repository.EventRepository
import com.sanket_satpute_20.ironmind.domain.repository.ObservationRepository
import com.sanket_satpute_20.ironmind.domain.repository.ReflectionRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EvidenceResolverTest {

    private lateinit var observationRepository: ObservationRepository
    private lateinit var eventRepository: EventRepository
    private lateinit var reflectionRepository: ReflectionRepository
    private lateinit var evidenceResolver: EvidenceResolver

    @Before
    fun setup() {
        observationRepository = mockk()
        eventRepository = mockk()
        reflectionRepository = mockk()
        evidenceResolver = EvidenceResolver(observationRepository, eventRepository, reflectionRepository)
    }

    @Test
    fun `resolve observation returns Resolved when successful`() = runBlocking {
        val obsId = "obs-1"
        val mockObservation = Observation(
            id = obsId, userId = "user1", type = ObservationType.APP_USAGE_SESSION,
            source = ObservationSource.SYSTEM, occurredAt = 1000L, recordedAt = 1000L,
            subjectId = null, value = "value", context = "context", confidence = 1.0f,
            provenance = ObservationProvenance(ObservationSource.SYSTEM, null, 1000L)
        )
        coEvery { observationRepository.getObservationById(obsId) } returns Result.Success(mockObservation)

        val reference = EvidenceReference(obsId, EvidenceSourceType.OBSERVATION)
        val result = evidenceResolver.resolve(reference)

        assertTrue(result is EvidenceResolutionResult.Resolved)
        val resolved = result as EvidenceResolutionResult.Resolved
        assertEquals(obsId, resolved.sourceId)
        assertEquals(EvidenceSourceType.OBSERVATION, resolved.sourceType)
        assertEquals(mockObservation, resolved.entity)
    }

    @Test
    fun `resolve observation returns Missing when not found`() = runBlocking {
        val obsId = "obs-2"
        coEvery { observationRepository.getObservationById(obsId) } returns Result.Failure(Exception("Not found"))

        val reference = EvidenceReference(obsId, EvidenceSourceType.OBSERVATION)
        val result = evidenceResolver.resolve(reference)

        assertTrue(result is EvidenceResolutionResult.Missing)
    }

    @Test
    fun `resolve event returns Resolved when successful`() = runBlocking {
        val eventId = "evt-1"
        val mockEvent = Event(
            id = eventId, userId = "user1", type = EventType.TASK_COMPLETED,
            occurredAt = 1000L, recordedAt = 1000L, source = EntitySource.USER
        )
        coEvery { eventRepository.getEvent(eventId) } returns Result.Success(mockEvent)

        val reference = EvidenceReference(eventId, EvidenceSourceType.EVENT)
        val result = evidenceResolver.resolve(reference)

        assertTrue(result is EvidenceResolutionResult.Resolved)
        val resolved = result as EvidenceResolutionResult.Resolved
        assertEquals(mockEvent, resolved.entity)
    }

    @Test
    fun `resolve event returns Missing when null`() = runBlocking {
        val eventId = "evt-2"
        coEvery { eventRepository.getEvent(eventId) } returns Result.Success(null)

        val reference = EvidenceReference(eventId, EvidenceSourceType.EVENT)
        val result = evidenceResolver.resolve(reference)

        assertTrue(result is EvidenceResolutionResult.Missing)
    }

    @Test
    fun `resolve reflection returns Resolved when successful`() = runBlocking {
        val refId = "ref-1"
        val mockReflection = Reflection(
            id = refId, userId = "user1", targetEntityId = null, targetEntityType = null,
            content = "test", sentiment = null, createdAt = 1000L
        )
        coEvery { reflectionRepository.getReflection(refId) } returns Result.Success(mockReflection)

        val reference = EvidenceReference(refId, EvidenceSourceType.REFLECTION)
        val result = evidenceResolver.resolve(reference)

        assertTrue(result is EvidenceResolutionResult.Resolved)
        val resolved = result as EvidenceResolutionResult.Resolved
        assertEquals(mockReflection, resolved.entity)
    }
}
