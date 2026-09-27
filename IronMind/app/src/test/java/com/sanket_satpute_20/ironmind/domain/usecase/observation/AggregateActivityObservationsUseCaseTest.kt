package com.sanket_satpute_20.ironmind.domain.usecase.observation

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.observation.Observation
import com.sanket_satpute_20.ironmind.domain.model.observation.ObservationProvenance
import com.sanket_satpute_20.ironmind.domain.model.observation.ObservationSource
import com.sanket_satpute_20.ironmind.domain.model.observation.ObservationType
import com.sanket_satpute_20.ironmind.testutil.fake.FakeObservationRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class AggregateActivityObservationsUseCaseTest {

    private lateinit var observationRepository: FakeObservationRepository
    private lateinit var useCase: AggregateActivityObservationsUseCase

    private val userId = "test_user_id"
    private val startTimeMs = 1000L
    private val endTimeMs = 5000L

    @Before
    fun setUp() {
        observationRepository = FakeObservationRepository()
        useCase = AggregateActivityObservationsUseCase(observationRepository)
    }

    private fun createObservation(
        id: String,
        value: String,
        occurredAt: Long,
        uid: String = userId,
        type: ObservationType = ObservationType.ACTIVITY_CONTEXT_CHANGED
    ): Observation = Observation(
        id = id,
        userId = uid,
        type = type,
        source = ObservationSource.ACTIVITY,
        occurredAt = occurredAt,
        recordedAt = occurredAt + 100,
        subjectId = null,
        value = value,
        context = "Confidence: 100.0%",
        confidence = 1.0f,
        provenance = ObservationProvenance(ObservationSource.ACTIVITY, "test", occurredAt)
    )

    @Test
    fun `1 empty time window - returns empty aggregation`() = runBlocking {
        val result = useCase(userId, startTimeMs, endTimeMs)
        
        assertTrue(result is Result.Success)
        val data = (result as Result.Success).data
        assertEquals(0, data.observationCount)
        assertEquals(0, data.distinctActivityStateCount)
        assertTrue(data.perStateObservationCounts.isEmpty())
        assertNull(data.firstOccurrenceTimestamp)
        assertNull(data.lastOccurrenceTimestamp)
    }

    @Test
    fun `2 single activity observation`() = runBlocking {
        observationRepository.insertObservation(createObservation("1", "WALKING", 2000L))

        val result = useCase(userId, startTimeMs, endTimeMs)
        
        val data = (result as Result.Success).data
        assertEquals(1, data.observationCount)
        assertEquals(1, data.distinctActivityStateCount)
        assertEquals(1, data.perStateObservationCounts["WALKING"])
        assertEquals(2000L, data.firstOccurrenceTimestamp)
        assertEquals(2000L, data.lastOccurrenceTimestamp)
    }

    @Test
    fun `3 multiple observations`() = runBlocking {
        observationRepository.insertObservation(createObservation("1", "WALKING", 2000L))
        observationRepository.insertObservation(createObservation("2", "WALKING", 3000L))
        
        val result = useCase(userId, startTimeMs, endTimeMs)
        
        val data = (result as Result.Success).data
        assertEquals(2, data.observationCount)
        assertEquals(1, data.distinctActivityStateCount)
        assertEquals(2, data.perStateObservationCounts["WALKING"])
        assertEquals(2000L, data.firstOccurrenceTimestamp)
        assertEquals(3000L, data.lastOccurrenceTimestamp)
    }

    @Test
    fun `4 multiple activity states`() = runBlocking {
        observationRepository.insertObservation(createObservation("1", "WALKING", 2000L))
        observationRepository.insertObservation(createObservation("2", "IN_VEHICLE", 3000L))
        observationRepository.insertObservation(createObservation("3", "STILL", 4000L))
        
        val result = useCase(userId, startTimeMs, endTimeMs)
        
        val data = (result as Result.Success).data
        assertEquals(3, data.observationCount)
        assertEquals(3, data.distinctActivityStateCount)
        assertEquals(1, data.perStateObservationCounts["WALKING"])
        assertEquals(1, data.perStateObservationCounts["IN_VEHICLE"])
        assertEquals(1, data.perStateObservationCounts["STILL"])
        assertEquals(2000L, data.firstOccurrenceTimestamp)
        assertEquals(4000L, data.lastOccurrenceTimestamp)
    }

    @Test
    fun `5 start boundary is included`() = runBlocking {
        val obs = createObservation("1", "STILL", startTimeMs) 
        observationRepository.insertObservation(obs)

        val result = useCase(userId, startTimeMs, endTimeMs)
        
        val data = (result as Result.Success).data
        assertEquals(1, data.observationCount)
        assertEquals(startTimeMs, data.firstOccurrenceTimestamp)
    }

    @Test
    fun `6 end boundary is excluded`() = runBlocking {
        val obs = createObservation("1", "STILL", endTimeMs)
        observationRepository.insertObservation(obs)

        val result = useCase(userId, startTimeMs, endTimeMs)
        val data = (result as Result.Success).data
        assertEquals(0, data.observationCount)
    }

    @Test
    fun `7 user isolation - Observations belonging to another user must never be included`() = runBlocking {
        observationRepository.insertObservation(createObservation("1", "WALKING", 2000L, uid = userId))
        observationRepository.insertObservation(createObservation("2", "IN_VEHICLE", 3000L, uid = "other_user"))

        val result = useCase(userId, startTimeMs, endTimeMs)
        
        val data = (result as Result.Success).data
        assertEquals(1, data.observationCount)
        assertEquals("WALKING", data.perStateObservationCounts.keys.first())
    }

    @Test
    fun `8 observation type isolation - Non-ACTIVITY must never be included`() = runBlocking {
        observationRepository.insertObservation(createObservation("1", "WALKING", 2000L, type = ObservationType.ACTIVITY_CONTEXT_CHANGED))
        observationRepository.insertObservation(createObservation("2", "com.app", 3000L, type = ObservationType.APP_USAGE_SESSION))

        val result = useCase(userId, startTimeMs, endTimeMs)
        
        val data = (result as Result.Success).data
        assertEquals(1, data.observationCount)
        assertEquals("WALKING", data.perStateObservationCounts.keys.first())
    }

    @Test
    fun `9 invalid time window - startTimeMs greater than or equal to endTimeMs fails`() = runBlocking {
        val result1 = useCase(userId, 5000L, 1000L)
        assertTrue(result1 is Result.Failure)
        assertTrue((result1 as Result.Failure).error is IllegalArgumentException)

        val result2 = useCase(userId, 5000L, 5000L)
        assertTrue(result2 is Result.Failure)
        assertTrue((result2 as Result.Failure).error is IllegalArgumentException)
    }

    @Test
    fun `10 malformed data - blank value is not added to distinct counts but observation counts`() = runBlocking {
        observationRepository.insertObservation(createObservation("1", "WALKING", 2000L))
        observationRepository.insertObservation(createObservation("2", "WALKING", 2500L))
        observationRepository.insertObservation(createObservation("3", "  ", 3000L))
        observationRepository.insertObservation(createObservation("4", "STILL", 3500L))
        
        val result = useCase(userId, startTimeMs, endTimeMs)
        
        val data = (result as Result.Success).data
        assertEquals(4, data.observationCount)
        assertEquals(2, data.distinctActivityStateCount)
        assertEquals(2, data.perStateObservationCounts["WALKING"])
        assertEquals(1, data.perStateObservationCounts["STILL"])
        assertTrue(!data.perStateObservationCounts.containsKey("UNKNOWN"))
    }

    @Test
    fun `11 deterministic repeated aggregation`() = runBlocking {
        observationRepository.insertObservation(createObservation("1", "WALKING", 2000L))
        
        val result1 = useCase(userId, startTimeMs, endTimeMs)
        val data1 = (result1 as Result.Success).data
        
        val result2 = useCase(userId, startTimeMs, endTimeMs)
        val data2 = (result2 as Result.Success).data
        
        assertEquals(data1, data2)
    }
}
