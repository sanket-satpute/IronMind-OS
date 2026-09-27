package com.sanket_satpute_20.ironmind.domain.usecase.observation

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.observation.NotificationObservationAggregation
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

class AggregateNotificationObservationsUseCaseTest {

    private lateinit var observationRepository: FakeObservationRepository
    private lateinit var useCase: AggregateNotificationObservationsUseCase

    private val userId = "test_user_id"
    private val startTimeMs = 1000L
    private val endTimeMs = 5000L

    @Before
    fun setUp() {
        observationRepository = FakeObservationRepository()
        useCase = AggregateNotificationObservationsUseCase(observationRepository)
    }

    private fun createObservation(
        id: String,
        packageName: String,
        occurredAt: Long,
        uid: String = userId,
        type: ObservationType = ObservationType.NOTIFICATION_RECEIVED
    ): Observation = Observation(
        id = id,
        userId = uid,
        type = type,
        source = ObservationSource.NOTIFICATION,
        occurredAt = occurredAt,
        recordedAt = occurredAt,
        subjectId = packageName,
        value = packageName,
        context = "{\"packageName\":\"$packageName\"}",
        confidence = 1.0f,
        provenance = ObservationProvenance(ObservationSource.NOTIFICATION, packageName, occurredAt)
    )

    // CORE

    @Test
    fun `1 Empty window`() = runBlocking {
        val result = useCase(userId, startTimeMs, endTimeMs)
        assertTrue(result is Result.Success)
        val data = (result as Result.Success).data
        assertEquals(0, data.observationCount)
        assertEquals(0, data.uniquePackageCount)
        assertNull(data.firstOccurrenceTimestamp)
        assertNull(data.lastOccurrenceTimestamp)
    }

    @Test
    fun `2 Single notification`() = runBlocking {
        observationRepository.insertObservation(createObservation("1", "com.example.app", 2000L))
        val result = useCase(userId, startTimeMs, endTimeMs)
        val data = (result as Result.Success).data
        assertEquals(1, data.observationCount)
        assertEquals(1, data.uniquePackageCount)
    }

    @Test
    fun `3 Multiple notifications`() = runBlocking {
        observationRepository.insertObservation(createObservation("1", "com.example.app1", 2000L))
        observationRepository.insertObservation(createObservation("2", "com.example.app2", 3000L))
        val result = useCase(userId, startTimeMs, endTimeMs)
        val data = (result as Result.Success).data
        assertEquals(2, data.observationCount)
        assertEquals(2, data.uniquePackageCount)
    }

    @Test
    fun `4 Multiple notifications from same package`() = runBlocking {
        observationRepository.insertObservation(createObservation("1", "com.example.app", 2000L))
        observationRepository.insertObservation(createObservation("2", "com.example.app", 3000L))
        val result = useCase(userId, startTimeMs, endTimeMs)
        val data = (result as Result.Success).data
        assertEquals(2, data.observationCount)
        assertEquals(1, data.uniquePackageCount)
    }

    @Test
    fun `5 Multiple distinct packages`() = runBlocking {
        observationRepository.insertObservation(createObservation("1", "com.app.a", 2000L))
        observationRepository.insertObservation(createObservation("2", "com.app.b", 3000L))
        observationRepository.insertObservation(createObservation("3", "com.app.c", 4000L))
        val result = useCase(userId, startTimeMs, endTimeMs)
        val data = (result as Result.Success).data
        assertEquals(3, data.observationCount)
        assertEquals(3, data.uniquePackageCount)
    }

    @Test
    fun `6 Correct first occurrence`() = runBlocking {
        observationRepository.insertObservation(createObservation("1", "com.example.app", 3000L))
        observationRepository.insertObservation(createObservation("2", "com.example.app", 2000L))
        val result = useCase(userId, startTimeMs, endTimeMs)
        val data = (result as Result.Success).data
        assertEquals(2000L, data.firstOccurrenceTimestamp)
    }

    @Test
    fun `7 Correct last occurrence`() = runBlocking {
        observationRepository.insertObservation(createObservation("1", "com.example.app", 2000L))
        observationRepository.insertObservation(createObservation("2", "com.example.app", 4000L))
        val result = useCase(userId, startTimeMs, endTimeMs)
        val data = (result as Result.Success).data
        assertEquals(4000L, data.lastOccurrenceTimestamp)
    }

    // TIME

    @Test
    fun `8 Start boundary is inclusive`() = runBlocking {
        observationRepository.insertObservation(createObservation("1", "com.example.app", startTimeMs))
        val result = useCase(userId, startTimeMs, endTimeMs)
        val data = (result as Result.Success).data
        assertEquals(1, data.observationCount)
    }

    @Test
    fun `9 End boundary is exclusive`() = runBlocking {
        observationRepository.insertObservation(createObservation("1", "com.example.app", endTimeMs))
        val result = useCase(userId, startTimeMs, endTimeMs)
        val data = (result as Result.Success).data
        assertEquals(0, data.observationCount)
    }

    @Test
    fun `10 Observation before window excluded`() = runBlocking {
        observationRepository.insertObservation(createObservation("1", "com.example.app", startTimeMs - 1))
        val result = useCase(userId, startTimeMs, endTimeMs)
        val data = (result as Result.Success).data
        assertEquals(0, data.observationCount)
    }

    @Test
    fun `11 Observation after window excluded`() = runBlocking {
        observationRepository.insertObservation(createObservation("1", "com.example.app", endTimeMs + 1))
        val result = useCase(userId, startTimeMs, endTimeMs)
        val data = (result as Result.Success).data
        assertEquals(0, data.observationCount)
    }

    @Test
    fun `12 Invalid time window`() = runBlocking {
        val result = useCase(userId, 5000L, 1000L)
        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error is IllegalArgumentException)
    }

    // ISOLATION

    @Test
    fun `13 User isolation`() = runBlocking {
        observationRepository.insertObservation(createObservation("1", "com.example.app", 2000L, uid = userId))
        observationRepository.insertObservation(createObservation("2", "com.example.app", 3000L, uid = "other_user"))
        val result = useCase(userId, startTimeMs, endTimeMs)
        val data = (result as Result.Success).data
        assertEquals(1, data.observationCount)
    }

    @Test
    fun `14 Observation type isolation`() = runBlocking {
        observationRepository.insertObservation(createObservation("1", "com.example.app", 2000L, type = ObservationType.NOTIFICATION_RECEIVED))
        observationRepository.insertObservation(createObservation("2", "com.example.app", 3000L, type = ObservationType.ACTIVITY_CONTEXT_CHANGED))
        val result = useCase(userId, startTimeMs, endTimeMs)
        val data = (result as Result.Success).data
        assertEquals(1, data.observationCount)
    }

    @Test
    fun `15 Non-notification observations excluded`() = runBlocking {
        observationRepository.insertObservation(createObservation("1", "com.example.app", 3000L, type = ObservationType.APP_USAGE_SESSION))
        val result = useCase(userId, startTimeMs, endTimeMs)
        val data = (result as Result.Success).data
        assertEquals(0, data.observationCount)
    }

    // PACKAGE HANDLING

    @Test
    fun `16 Blank package counted as observation but not unique package`() = runBlocking {
        observationRepository.insertObservation(createObservation("1", "   ", 2000L))
        val result = useCase(userId, startTimeMs, endTimeMs)
        val data = (result as Result.Success).data
        assertEquals(1, data.observationCount)
        assertEquals(0, data.uniquePackageCount)
    }

    @Test
    fun `17 Malformed package handling`() = runBlocking {
        observationRepository.insertObservation(createObservation("1", "", 2000L))
        val result = useCase(userId, startTimeMs, endTimeMs)
        val data = (result as Result.Success).data
        assertEquals(1, data.observationCount)
        assertEquals(0, data.uniquePackageCount)
    }

    @Test
    fun `18 Repeated package counts once for uniquePackageCount`() = runBlocking {
        observationRepository.insertObservation(createObservation("1", "com.app", 2000L))
        observationRepository.insertObservation(createObservation("2", "com.app", 3000L))
        observationRepository.insertObservation(createObservation("3", "com.app", 4000L))
        val result = useCase(userId, startTimeMs, endTimeMs)
        val data = (result as Result.Success).data
        assertEquals(3, data.observationCount)
        assertEquals(1, data.uniquePackageCount)
    }

    // DETERMINISM

    @Test
    fun `19 Unordered repository results still produce correct first or last timestamps`() = runBlocking {
        observationRepository.insertObservation(createObservation("1", "com.app", 4000L))
        observationRepository.insertObservation(createObservation("2", "com.app", 2000L))
        observationRepository.insertObservation(createObservation("3", "com.app", 3000L))
        val result = useCase(userId, startTimeMs, endTimeMs)
        val data = (result as Result.Success).data
        assertEquals(2000L, data.firstOccurrenceTimestamp)
        assertEquals(4000L, data.lastOccurrenceTimestamp)
    }

    @Test
    fun `20 Repeated aggregation produces identical output`() = runBlocking {
        observationRepository.insertObservation(createObservation("1", "com.app", 2000L))
        val result1 = useCase(userId, startTimeMs, endTimeMs)
        val data1 = (result1 as Result.Success).data
        
        val result2 = useCase(userId, startTimeMs, endTimeMs)
        val data2 = (result2 as Result.Success).data
        
        assertEquals(data1, data2)
    }

    // PRIVACY

    @Test
    fun `21 Output model has no package collection`() = runBlocking {
        val properties = NotificationObservationAggregation::class.java.declaredFields
        val hasList = properties.any { it.type.name.contains("List", ignoreCase = true) }
        val hasSet = properties.any { it.type.name.contains("Set", ignoreCase = true) }
        assertTrue(!hasList)
        assertTrue(!hasSet)
    }

    @Test
    fun `22 Output model has no raw context`() = runBlocking {
        val properties = NotificationObservationAggregation::class.java.declaredFields
        val hasContext = properties.any { it.name.contains("context", ignoreCase = true) }
        assertTrue(!hasContext)
    }

    @Test
    fun `23 Output model has no notification content or title or body`() = runBlocking {
        val properties = NotificationObservationAggregation::class.java.declaredFields
        val hasTitle = properties.any { it.name.contains("title", ignoreCase = true) }
        val hasBody = properties.any { it.name.contains("body", ignoreCase = true) }
        val hasContent = properties.any { it.name.contains("content", ignoreCase = true) }
        val hasPackage = properties.any { it.name.contains("packageName", ignoreCase = true) }
        assertTrue(!hasTitle)
        assertTrue(!hasBody)
        assertTrue(!hasContent)
        assertTrue(!hasPackage)
    }

    @Test
    fun `24 Reflection based structural privacy test`() = runBlocking {
        val properties = NotificationObservationAggregation::class.java.declaredFields
        val hasString = properties.count { it.type == String::class.java }
        // Only userId is String
        assertEquals(1, hasString)
    }
}
