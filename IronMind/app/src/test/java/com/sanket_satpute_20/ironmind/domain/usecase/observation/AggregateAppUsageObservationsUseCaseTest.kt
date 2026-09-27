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
import org.junit.Before
import org.junit.Test

class AggregateAppUsageObservationsUseCaseTest {

    private lateinit var observationRepository: FakeObservationRepository
    private lateinit var useCase: AggregateAppUsageObservationsUseCase

    private val userId = "test_user_id"
    private val startTimeMs = 1000L
    private val endTimeMs = 5000L

    @Before
    fun setUp() {
        observationRepository = FakeObservationRepository()
        useCase = AggregateAppUsageObservationsUseCase(observationRepository)
    }

    private fun createObservation(
        id: String,
        value: String,
        context: String,
        occurredAt: Long,
        uid: String = userId,
        type: ObservationType = ObservationType.APP_USAGE_SESSION
    ): Observation = Observation(
        id = id,
        userId = uid,
        type = type,
        source = ObservationSource.APP_USAGE,
        occurredAt = occurredAt,
        recordedAt = occurredAt + 100,
        subjectId = null,
        value = value,
        context = context,
        confidence = null,
        provenance = ObservationProvenance(ObservationSource.APP_USAGE, "test", occurredAt)
    )

    @Test
    fun `A - Empty result - No observations`() = runBlocking {
        val result = useCase(userId, startTimeMs, endTimeMs)
        
        assertTrue(result is Result.Success)
        val data = (result as Result.Success).data
        assertEquals(0, data.observationCount)
        assertEquals(0L, data.totalDurationMs)
        assertEquals(0, data.uniquePackageCount)
    }

    @Test
    fun `B - Single observation - Verify exact duration and package count`() = runBlocking {
        val obs = createObservation("1", "com.example.app", "duration_ms=1500", 2000L)
        observationRepository.insertObservation(obs)

        val result = useCase(userId, startTimeMs, endTimeMs)
        
        val data = (result as Result.Success).data
        assertEquals(1, data.observationCount)
        assertEquals(1500L, data.totalDurationMs)
        assertEquals(1, data.uniquePackageCount)
    }

    @Test
    fun `C - Multiple observations - Correct summation and unique packages`() = runBlocking {
        observationRepository.insertObservation(createObservation("1", "com.example.appA", "duration_ms=1000", 2000L))
        observationRepository.insertObservation(createObservation("2", "com.example.appA", "duration_ms=2000", 3000L))
        observationRepository.insertObservation(createObservation("3", "com.example.appB", "duration_ms=3000", 4000L))
        
        val result = useCase(userId, startTimeMs, endTimeMs)
        
        val data = (result as Result.Success).data
        assertEquals(3, data.observationCount)
        assertEquals(6000L, data.totalDurationMs)
        assertEquals(2, data.uniquePackageCount) // appA and appB
    }

    @Test
    fun `D - Time-window start boundary - occurredAt == startTimeMs must be included`() = runBlocking {
        val obs = createObservation("1", "com.example.app", "duration_ms=500", startTimeMs) // exactly at start
        observationRepository.insertObservation(obs)

        val result = useCase(userId, startTimeMs, endTimeMs)
        
        val data = (result as Result.Success).data
        assertEquals(1, data.observationCount)
        assertEquals(500L, data.totalDurationMs)
    }

    @Test
    fun `E - Time-window end boundary - occurredAt == endTimeMs must be excluded`() = runBlocking {
        val obs = createObservation("1", "com.example.app", "duration_ms=500", endTimeMs) // exactly at end
        observationRepository.insertObservation(obs)

        val result = useCase(userId, startTimeMs, endTimeMs)
        val data = (result as Result.Success).data
        assertEquals(0, data.observationCount)
    }

    @Test
    fun `F - User isolation - Observations belonging to another user must never be included`() = runBlocking {
        val obs1 = createObservation("1", "com.example.appA", "duration_ms=1000", 2000L, uid = userId)
        val obs2 = createObservation("2", "com.example.appB", "duration_ms=2000", 3000L, uid = "other_user")
        observationRepository.insertObservation(obs1)
        observationRepository.insertObservation(obs2)

        val result = useCase(userId, startTimeMs, endTimeMs)
        
        val data = (result as Result.Success).data
        assertEquals(1, data.observationCount)
        assertEquals(1000L, data.totalDurationMs)
    }

    @Test
    fun `G - Observation type isolation - Non-APP_USAGE_SESSION must never be included`() = runBlocking {
        val obs1 = createObservation("1", "com.example.appA", "duration_ms=1000", 2000L, type = ObservationType.APP_USAGE_SESSION)
        val obs2 = createObservation("2", "com.example.appB", "duration_ms=2000", 3000L, type = ObservationType.NOTIFICATION_RECEIVED)
        observationRepository.insertObservation(obs1)
        observationRepository.insertObservation(obs2)

        val result = useCase(userId, startTimeMs, endTimeMs)
        
        val data = (result as Result.Success).data
        assertEquals(1, data.observationCount)
        assertEquals(1000L, data.totalDurationMs)
    }

    @Test
    fun `H - Malformed duration context - missing duration must not crash aggregation`() = runBlocking {
        observationRepository.insertObservation(createObservation("1", "com.example.appA", "duration_ms=1000", 2000L))
        observationRepository.insertObservation(createObservation("2", "com.example.appB", "malformed_string_without_duration", 3000L))
        observationRepository.insertObservation(createObservation("3", "com.example.appC", "duration_ms=not_a_number", 4000L))
        
        val result = useCase(userId, startTimeMs, endTimeMs)
        
        val data = (result as Result.Success).data
        assertEquals(3, data.observationCount) // Still counts as observations
        assertEquals(3, data.uniquePackageCount)
        assertEquals(1000L, data.totalDurationMs) // Only valid duration is counted
    }

    @Test
    fun `I - Invalid negative duration - must be ignored`() = runBlocking {
        observationRepository.insertObservation(createObservation("1", "com.example.appA", "duration_ms=1000", 2000L))
        observationRepository.insertObservation(createObservation("2", "com.example.appB", "duration_ms=-500", 3000L))
        
        val result = useCase(userId, startTimeMs, endTimeMs)
        
        val data = (result as Result.Success).data
        assertEquals(2, data.observationCount) 
        assertEquals(2, data.uniquePackageCount)
        assertEquals(1000L, data.totalDurationMs) // Negative duration is ignored
    }

    @Test
    fun `J - Invalid time window - startTimeMs greater than or equal to endTimeMs fails`() = runBlocking {
        val result1 = useCase(userId, 5000L, 1000L)
        assertTrue(result1 is Result.Failure)
        assertTrue((result1 as Result.Failure).error is IllegalArgumentException)

        val result2 = useCase(userId, 5000L, 5000L)
        assertTrue(result2 is Result.Failure)
        assertTrue((result2 as Result.Failure).error is IllegalArgumentException)
    }

    @Test
    fun `K - Unique package calculation - repeated package names count once`() = runBlocking {
        observationRepository.insertObservation(createObservation("1", "com.example.app", "duration_ms=1000", 2000L))
        observationRepository.insertObservation(createObservation("2", "com.example.app", "duration_ms=1000", 3000L))
        observationRepository.insertObservation(createObservation("3", "com.example.app", "duration_ms=1000", 4000L))
        
        val result = useCase(userId, startTimeMs, endTimeMs)
        
        val data = (result as Result.Success).data
        assertEquals(3, data.observationCount)
        assertEquals(1, data.uniquePackageCount)
        assertEquals(3000L, data.totalDurationMs) 
    }
}
