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

class AggregateCalendarObservationsUseCaseTest {

    private lateinit var observationRepository: FakeObservationRepository
    private lateinit var useCase: AggregateCalendarObservationsUseCase

    private val userId = "test_user_id"
    private val startTimeMs = 1000L
    private val endTimeMs = 5000L

    @Before
    fun setUp() {
        observationRepository = FakeObservationRepository()
        useCase = AggregateCalendarObservationsUseCase(observationRepository)
    }

    private fun createObservation(
        id: String,
        jsonContext: String,
        occurredAt: Long,
        uid: String = userId,
        type: ObservationType = ObservationType.CALENDAR_CONTEXT_CHANGED
    ): Observation = Observation(
        id = id,
        userId = uid,
        type = type,
        source = ObservationSource.CALENDAR,
        occurredAt = occurredAt,
        recordedAt = occurredAt,
        subjectId = null,
        value = "UPCOMING_EVENTS",
        context = jsonContext,
        confidence = null,
        provenance = ObservationProvenance(ObservationSource.CALENDAR, "test", occurredAt)
    )

    private fun buildJsonEvent(title: String = "Meeting", start: Long = 10000L, end: Long = 13600L): String {
        return """[{"title":"$title","startTimeMillis":$start,"endTimeMillis":$end}]"""
    }
    
    private fun buildJsonEvents(vararg events: Triple<String, Long, Long>): String {
        val arrayStr = events.joinToString(",") { 
            """{"title":"${it.first}","startTimeMillis":${it.second},"endTimeMillis":${it.third}}""" 
        }
        return "[$arrayStr]"
    }

    @Test
    fun `1 empty query result`() = runBlocking {
        val result = useCase(userId, startTimeMs, endTimeMs)
        assertTrue(result is Result.Success)
        val data = (result as Result.Success).data
        assertEquals(0, data.observationCount)
        assertEquals(0, data.uniqueEventCount)
        assertEquals(0L, data.sumOfScheduledDurationMs)
        assertNull(data.earliestScheduledEventStartMs)
        assertNull(data.latestScheduledEventEndMs)
    }

    @Test
    fun `2 one observation with one valid event`() = runBlocking {
        observationRepository.insertObservation(createObservation("1", buildJsonEvent("M1", 10000L, 11000L), 2000L))
        val result = useCase(userId, startTimeMs, endTimeMs)
        val data = (result as Result.Success).data
        assertEquals(1, data.observationCount)
        assertEquals(1, data.uniqueEventCount)
        assertEquals(1000L, data.sumOfScheduledDurationMs)
    }

    @Test
    fun `3 multiple valid events`() = runBlocking {
        val json = buildJsonEvents(Triple("M1", 10000L, 11000L), Triple("M2", 12000L, 13000L))
        observationRepository.insertObservation(createObservation("1", json, 2000L))
        
        val result = useCase(userId, startTimeMs, endTimeMs)
        val data = (result as Result.Success).data
        assertEquals(1, data.observationCount)
        assertEquals(2, data.uniqueEventCount)
        assertEquals(2000L, data.sumOfScheduledDurationMs)
    }

    @Test
    fun `4 identical events repeated across multiple snapshots`() = runBlocking {
        observationRepository.insertObservation(createObservation("1", buildJsonEvent("M1", 10000L, 11000L), 2000L))
        observationRepository.insertObservation(createObservation("2", buildJsonEvent("M1", 10000L, 11000L), 3000L))
        
        val result = useCase(userId, startTimeMs, endTimeMs)
        val data = (result as Result.Success).data
        assertEquals(2, data.observationCount)
        assertEquals(1, data.uniqueEventCount)
        assertEquals(1000L, data.sumOfScheduledDurationMs) // Deduplicated
    }

    @Test
    fun `5 different events across multiple snapshots`() = runBlocking {
        observationRepository.insertObservation(createObservation("1", buildJsonEvent("M1", 10000L, 11000L), 2000L))
        observationRepository.insertObservation(createObservation("2", buildJsonEvent("M2", 12000L, 13000L), 3000L))
        
        val result = useCase(userId, startTimeMs, endTimeMs)
        val data = (result as Result.Success).data
        assertEquals(2, data.observationCount)
        assertEquals(2, data.uniqueEventCount)
        assertEquals(2000L, data.sumOfScheduledDurationMs)
    }

    @Test
    fun `6 malformed JSON`() = runBlocking {
        observationRepository.insertObservation(createObservation("1", buildJsonEvent("M1", 10000L, 11000L), 2000L))
        observationRepository.insertObservation(createObservation("2", "NOT_JSON", 3000L))
        
        val result = useCase(userId, startTimeMs, endTimeMs)
        val data = (result as Result.Success).data
        assertEquals(2, data.observationCount)
        assertEquals(1, data.uniqueEventCount)
    }

    @Test
    fun `7 missing title`() = runBlocking {
        val json = """[{"startTimeMillis":10000,"endTimeMillis":11000}]"""
        observationRepository.insertObservation(createObservation("1", json, 2000L))
        
        val result = useCase(userId, startTimeMs, endTimeMs)
        val data = (result as Result.Success).data
        assertEquals(1, data.observationCount)
        assertEquals(0, data.uniqueEventCount)
    }

    @Test
    fun `8 missing startTimeMillis`() = runBlocking {
        val json = """[{"title":"M1","endTimeMillis":11000}]"""
        observationRepository.insertObservation(createObservation("1", json, 2000L))
        
        val result = useCase(userId, startTimeMs, endTimeMs)
        val data = (result as Result.Success).data
        assertEquals(0, data.uniqueEventCount)
    }

    @Test
    fun `9 missing endTimeMillis`() = runBlocking {
        val json = """[{"title":"M1","startTimeMillis":10000}]"""
        observationRepository.insertObservation(createObservation("1", json, 2000L))
        
        val result = useCase(userId, startTimeMs, endTimeMs)
        val data = (result as Result.Success).data
        assertEquals(0, data.uniqueEventCount)
    }

    @Test
    fun `10 endTimeMillis less than startTimeMillis`() = runBlocking {
        val json = """[{"title":"M1","startTimeMillis":11000,"endTimeMillis":10000}]"""
        observationRepository.insertObservation(createObservation("1", json, 2000L))
        
        val result = useCase(userId, startTimeMs, endTimeMs)
        val data = (result as Result.Success).data
        assertEquals(0, data.uniqueEventCount)
    }

    @Test
    fun `11 zero-duration event`() = runBlocking {
        val json = buildJsonEvent("M1", 10000L, 10000L)
        observationRepository.insertObservation(createObservation("1", json, 2000L))
        
        val result = useCase(userId, startTimeMs, endTimeMs)
        val data = (result as Result.Success).data
        assertEquals(1, data.uniqueEventCount)
        assertEquals(0L, data.sumOfScheduledDurationMs)
    }

    @Test
    fun `12 start boundary inclusive`() = runBlocking {
        observationRepository.insertObservation(createObservation("1", buildJsonEvent("M1", 10000L, 11000L), startTimeMs))
        val result = useCase(userId, startTimeMs, endTimeMs)
        val data = (result as Result.Success).data
        assertEquals(1, data.observationCount)
    }

    @Test
    fun `13 end boundary exclusive`() = runBlocking {
        observationRepository.insertObservation(createObservation("1", buildJsonEvent("M1", 10000L, 11000L), endTimeMs))
        val result = useCase(userId, startTimeMs, endTimeMs)
        val data = (result as Result.Success).data
        assertEquals(0, data.observationCount)
    }

    @Test
    fun `14 user isolation`() = runBlocking {
        observationRepository.insertObservation(createObservation("1", buildJsonEvent("M1", 10000L, 11000L), 2000L, uid = userId))
        observationRepository.insertObservation(createObservation("2", buildJsonEvent("M2", 12000L, 13000L), 3000L, uid = "other_user"))
        val result = useCase(userId, startTimeMs, endTimeMs)
        val data = (result as Result.Success).data
        assertEquals(1, data.observationCount)
        assertEquals(1, data.uniqueEventCount)
    }

    @Test
    fun `15 observation type isolation`() = runBlocking {
        observationRepository.insertObservation(createObservation("1", buildJsonEvent("M1", 10000L, 11000L), 2000L, type = ObservationType.CALENDAR_CONTEXT_CHANGED))
        observationRepository.insertObservation(createObservation("2", buildJsonEvent("M2", 12000L, 13000L), 3000L, type = ObservationType.ACTIVITY_CONTEXT_CHANGED))
        val result = useCase(userId, startTimeMs, endTimeMs)
        val data = (result as Result.Success).data
        assertEquals(1, data.observationCount)
        assertEquals(1, data.uniqueEventCount)
    }

    @Test
    fun `16 invalid aggregation window`() = runBlocking {
        val result = useCase(userId, 5000L, 1000L)
        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error is IllegalArgumentException)
    }

    @Test
    fun `17 earliest and latest scheduled event`() = runBlocking {
        val json = buildJsonEvents(Triple("M1", 10000L, 11000L), Triple("M2", 9000L, 9500L), Triple("M3", 15000L, 18000L))
        observationRepository.insertObservation(createObservation("1", json, 2000L))
        val result = useCase(userId, startTimeMs, endTimeMs)
        val data = (result as Result.Success).data
        assertEquals(9000L, data.earliestScheduledEventStartMs)
        assertEquals(18000L, data.latestScheduledEventEndMs)
    }

    @Test
    fun `18 duration calculation after deduplication`() = runBlocking {
        observationRepository.insertObservation(createObservation("1", buildJsonEvent("M1", 1000L, 2000L), 2000L)) // 1000ms
        observationRepository.insertObservation(createObservation("2", buildJsonEvent("M1", 1000L, 2000L), 3000L)) // Duplicate, ignored
        observationRepository.insertObservation(createObservation("3", buildJsonEvent("M2", 3000L, 5000L), 3500L)) // 2000ms
        val result = useCase(userId, startTimeMs, endTimeMs)
        val data = (result as Result.Success).data
        assertEquals(2, data.uniqueEventCount)
        assertEquals(3000L, data.sumOfScheduledDurationMs)
    }

    @Test
    fun `19 titles absent from aggregation output`() = runBlocking {
        val properties = com.sanket_satpute_20.ironmind.domain.model.observation.CalendarObservationAggregation::class.java.declaredFields
        val hasTitle = properties.any { it.name.contains("title", ignoreCase = true) }
        val hasName = properties.any { it.name.contains("name", ignoreCase = true) }
        val hasEventList = properties.any { it.name.contains("event", ignoreCase = true) && !it.name.contains("count", ignoreCase = true) && !it.name.contains("Ms") }
        
        assertTrue(!hasTitle)
        assertTrue(!hasName)
        assertTrue(!hasEventList)
    }

    @Test
    fun `20 deterministic repeated aggregation`() = runBlocking {
        observationRepository.insertObservation(createObservation("1", buildJsonEvent("M1", 1000L, 2000L), 2000L))
        
        val result1 = useCase(userId, startTimeMs, endTimeMs)
        val data1 = (result1 as Result.Success).data
        
        val result2 = useCase(userId, startTimeMs, endTimeMs)
        val data2 = (result2 as Result.Success).data
        
        assertEquals(data1, data2)
    }
}
