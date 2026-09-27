package com.sanket_satpute_20.ironmind.domain.usecase.observation

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.observation.LocationObservationAggregation
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

class AggregateLocationObservationsUseCaseTest {

    private lateinit var observationRepository: FakeObservationRepository
    private lateinit var useCase: AggregateLocationObservationsUseCase

    private val userId = "test_user_id"
    private val startTimeMs = 1000L
    private val endTimeMs = 5000L

    @Before
    fun setUp() {
        observationRepository = FakeObservationRepository()
        useCase = AggregateLocationObservationsUseCase(observationRepository)
    }

    private fun createObservation(
        id: String,
        contextValue: String,
        occurredAt: Long,
        uid: String = userId,
        type: ObservationType = ObservationType.LOCATION_CONTEXT_CHANGED
    ): Observation = Observation(
        id = id,
        userId = uid,
        type = type,
        source = ObservationSource.LOCATION,
        occurredAt = occurredAt,
        recordedAt = occurredAt,
        subjectId = null,
        value = "COARSE_LOCATION",
        context = contextValue,
        confidence = null,
        provenance = ObservationProvenance(ObservationSource.LOCATION, "test", occurredAt)
    )

    private fun loc(lat: String, lng: String): String = "lat=$lat,lng=$lng"

    @Test
    fun `1 empty query result`() = runBlocking {
        val result = useCase(userId, startTimeMs, endTimeMs)
        assertTrue(result is Result.Success)
        val data = (result as Result.Success).data
        assertEquals(0, data.observationCount)
        assertEquals(0, data.uniqueCoordinateCount)
        assertEquals(0, data.coordinateChangeCount)
        assertNull(data.firstOccurrenceTimestamp)
        assertNull(data.lastOccurrenceTimestamp)
    }

    @Test
    fun `2 single valid location observation`() = runBlocking {
        observationRepository.insertObservation(createObservation("1", loc("12.34", "56.78"), 2000L))
        val result = useCase(userId, startTimeMs, endTimeMs)
        val data = (result as Result.Success).data
        assertEquals(1, data.observationCount)
        assertEquals(1, data.uniqueCoordinateCount)
        assertEquals(0, data.coordinateChangeCount)
    }

    @Test
    fun `3 multiple identical coordinates`() = runBlocking {
        observationRepository.insertObservation(createObservation("1", loc("12.34", "56.78"), 2000L))
        observationRepository.insertObservation(createObservation("2", loc("12.34", "56.78"), 3000L))
        val result = useCase(userId, startTimeMs, endTimeMs)
        val data = (result as Result.Success).data
        assertEquals(2, data.observationCount)
        assertEquals(1, data.uniqueCoordinateCount)
        assertEquals(0, data.coordinateChangeCount)
    }

    @Test
    fun `4 multiple distinct coordinates`() = runBlocking {
        observationRepository.insertObservation(createObservation("1", loc("12.34", "56.78"), 2000L))
        observationRepository.insertObservation(createObservation("2", loc("23.45", "67.89"), 3000L))
        val result = useCase(userId, startTimeMs, endTimeMs)
        val data = (result as Result.Success).data
        assertEquals(2, data.observationCount)
        assertEquals(2, data.uniqueCoordinateCount)
    }

    @Test
    fun `5 coordinateChangeCount for A to B`() = runBlocking {
        observationRepository.insertObservation(createObservation("1", loc("12.34", "56.78"), 2000L))
        observationRepository.insertObservation(createObservation("2", loc("23.45", "67.89"), 3000L))
        val result = useCase(userId, startTimeMs, endTimeMs)
        val data = (result as Result.Success).data
        assertEquals(1, data.coordinateChangeCount)
    }

    @Test
    fun `6 A to A to A produces zero changes`() = runBlocking {
        observationRepository.insertObservation(createObservation("1", loc("12.34", "56.78"), 2000L))
        observationRepository.insertObservation(createObservation("2", loc("12.34", "56.78"), 3000L))
        observationRepository.insertObservation(createObservation("3", loc("12.34", "56.78"), 4000L))
        val result = useCase(userId, startTimeMs, endTimeMs)
        val data = (result as Result.Success).data
        assertEquals(3, data.observationCount)
        assertEquals(1, data.uniqueCoordinateCount)
        assertEquals(0, data.coordinateChangeCount)
    }

    @Test
    fun `7 A to B to A produces two coordinate changes`() = runBlocking {
        observationRepository.insertObservation(createObservation("1", loc("12.34", "56.78"), 2000L))
        observationRepository.insertObservation(createObservation("2", loc("23.45", "67.89"), 3000L))
        observationRepository.insertObservation(createObservation("3", loc("12.34", "56.78"), 4000L))
        val result = useCase(userId, startTimeMs, endTimeMs)
        val data = (result as Result.Success).data
        assertEquals(2, data.uniqueCoordinateCount)
        assertEquals(2, data.coordinateChangeCount)
    }

    @Test
    fun `8 malformed or blank context`() = runBlocking {
        observationRepository.insertObservation(createObservation("1", "   ", 2000L))
        observationRepository.insertObservation(createObservation("2", "INVALID", 3000L))
        val result = useCase(userId, startTimeMs, endTimeMs)
        val data = (result as Result.Success).data
        assertEquals(2, data.observationCount) // 14. invalid observation still contributes to observationCount
        assertEquals(0, data.uniqueCoordinateCount) // 15. invalid observation does not contribute to uniqueCoordinateCount
        assertEquals(0, data.coordinateChangeCount)
    }

    @Test
    fun `9 malformed latitude`() = runBlocking {
        observationRepository.insertObservation(createObservation("1", loc("ABC", "56.78"), 2000L))
        val result = useCase(userId, startTimeMs, endTimeMs)
        val data = (result as Result.Success).data
        assertEquals(0, data.uniqueCoordinateCount)
    }

    @Test
    fun `10 malformed longitude`() = runBlocking {
        observationRepository.insertObservation(createObservation("1", loc("12.34", "DEF"), 2000L))
        val result = useCase(userId, startTimeMs, endTimeMs)
        val data = (result as Result.Success).data
        assertEquals(0, data.uniqueCoordinateCount)
    }

    @Test
    fun `11 out-of-range latitude`() = runBlocking {
        observationRepository.insertObservation(createObservation("1", loc("91.00", "56.78"), 2000L))
        observationRepository.insertObservation(createObservation("2", loc("-91.00", "56.78"), 3000L))
        val result = useCase(userId, startTimeMs, endTimeMs)
        val data = (result as Result.Success).data
        assertEquals(0, data.uniqueCoordinateCount)
    }

    @Test
    fun `12 out-of-range longitude`() = runBlocking {
        observationRepository.insertObservation(createObservation("1", loc("12.34", "181.00"), 2000L))
        observationRepository.insertObservation(createObservation("2", loc("12.34", "-181.00"), 3000L))
        val result = useCase(userId, startTimeMs, endTimeMs)
        val data = (result as Result.Success).data
        assertEquals(0, data.uniqueCoordinateCount)
    }

    @Test
    fun `13 A to INVALID to B does NOT produce a change`() = runBlocking {
        observationRepository.insertObservation(createObservation("1", loc("12.34", "56.78"), 2000L))
        observationRepository.insertObservation(createObservation("2", "INVALID", 3000L))
        observationRepository.insertObservation(createObservation("3", loc("23.45", "67.89"), 4000L))
        
        val result = useCase(userId, startTimeMs, endTimeMs)
        val data = (result as Result.Success).data
        
        // INVALID breaks adjacency. A to INVALID = 0. INVALID to B = 0.
        assertEquals(3, data.observationCount)
        assertEquals(2, data.uniqueCoordinateCount)
        assertEquals(0, data.coordinateChangeCount)
    }

    @Test
    fun `16 start boundary inclusive`() = runBlocking {
        observationRepository.insertObservation(createObservation("1", loc("12.34", "56.78"), startTimeMs))
        val result = useCase(userId, startTimeMs, endTimeMs)
        val data = (result as Result.Success).data
        assertEquals(1, data.observationCount)
    }

    @Test
    fun `17 end boundary exclusive`() = runBlocking {
        observationRepository.insertObservation(createObservation("1", loc("12.34", "56.78"), endTimeMs))
        val result = useCase(userId, startTimeMs, endTimeMs)
        val data = (result as Result.Success).data
        assertEquals(0, data.observationCount)
    }

    @Test
    fun `18 user isolation`() = runBlocking {
        observationRepository.insertObservation(createObservation("1", loc("12.34", "56.78"), 2000L, uid = userId))
        observationRepository.insertObservation(createObservation("2", loc("23.45", "67.89"), 3000L, uid = "other_user"))
        val result = useCase(userId, startTimeMs, endTimeMs)
        val data = (result as Result.Success).data
        assertEquals(1, data.observationCount)
        assertEquals(1, data.uniqueCoordinateCount)
    }

    @Test
    fun `19 observation type isolation`() = runBlocking {
        observationRepository.insertObservation(createObservation("1", loc("12.34", "56.78"), 2000L, type = ObservationType.LOCATION_CONTEXT_CHANGED))
        observationRepository.insertObservation(createObservation("2", loc("23.45", "67.89"), 3000L, type = ObservationType.ACTIVITY_CONTEXT_CHANGED))
        val result = useCase(userId, startTimeMs, endTimeMs)
        val data = (result as Result.Success).data
        assertEquals(1, data.observationCount)
        assertEquals(1, data.uniqueCoordinateCount)
    }

    @Test
    fun `20 invalid aggregation window`() = runBlocking {
        val result = useCase(userId, 5000L, 1000L)
        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error is IllegalArgumentException)
    }

    @Test
    fun `21 first occurrence timestamp`() = runBlocking {
        observationRepository.insertObservation(createObservation("1", loc("12.34", "56.78"), 2000L))
        observationRepository.insertObservation(createObservation("2", loc("23.45", "67.89"), 3000L))
        val result = useCase(userId, startTimeMs, endTimeMs)
        val data = (result as Result.Success).data
        assertEquals(2000L, data.firstOccurrenceTimestamp)
    }

    @Test
    fun `22 last occurrence timestamp`() = runBlocking {
        observationRepository.insertObservation(createObservation("1", loc("12.34", "56.78"), 2000L))
        observationRepository.insertObservation(createObservation("2", loc("23.45", "67.89"), 3000L))
        val result = useCase(userId, startTimeMs, endTimeMs)
        val data = (result as Result.Success).data
        assertEquals(3000L, data.lastOccurrenceTimestamp)
    }

    @Test
    fun `23 deterministic repeated aggregation`() = runBlocking {
        observationRepository.insertObservation(createObservation("1", loc("12.34", "56.78"), 2000L))
        val result1 = useCase(userId, startTimeMs, endTimeMs)
        val data1 = (result1 as Result.Success).data
        
        val result2 = useCase(userId, startTimeMs, endTimeMs)
        val data2 = (result2 as Result.Success).data
        
        assertEquals(data1, data2)
    }

    @Test
    fun `24 output model contains no coordinate payload`() = runBlocking {
        val properties = com.sanket_satpute_20.ironmind.domain.model.observation.LocationObservationAggregation::class.java.declaredFields
        val hasLat = properties.any { it.name.contains("lat", ignoreCase = true) }
        val hasLng = properties.any { it.name.contains("lng", ignoreCase = true) }
        val hasMap = properties.any { it.type.name.contains("Map", ignoreCase = true) }
        val hasList = properties.any { it.type.name.contains("List", ignoreCase = true) }
        
        assertTrue(!hasLat)
        assertTrue(!hasLng)
        assertTrue(!hasMap)
        assertTrue(!hasList)
    }
}
