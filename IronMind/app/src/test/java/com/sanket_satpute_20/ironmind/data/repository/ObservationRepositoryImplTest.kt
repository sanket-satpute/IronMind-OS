package com.sanket_satpute_20.ironmind.data.repository

import com.sanket_satpute_20.ironmind.data.local.dao.ObservationDao
import com.sanket_satpute_20.ironmind.data.local.entity.ObservationEntity
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.observation.Observation
import com.sanket_satpute_20.ironmind.domain.model.observation.ObservationProvenance
import com.sanket_satpute_20.ironmind.domain.model.observation.ObservationSource
import com.sanket_satpute_20.ironmind.domain.model.observation.ObservationType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

class ObservationRepositoryImplTest {

    private lateinit var fakeDao: FakeObservationDao
    private lateinit var repository: ObservationRepositoryImpl

    class FakeObservationDao : ObservationDao {
        val entities = mutableMapOf<String, ObservationEntity>()

        override fun insertObservation(observation: ObservationEntity) {
            entities[observation.id] = observation
        }

        override fun getObservations(userId: String, limit: Int, offset: Int): List<ObservationEntity> {
            return entities.values
                .filter { it.userId == userId }
                .sortedByDescending { it.occurredAt }
                .drop(offset)
                .take(limit)
        }

        override fun getObservationsByType(userId: String, type: String, limit: Int, offset: Int): List<ObservationEntity> {
            return entities.values
                .filter { it.userId == userId && it.type == type }
                .sortedByDescending { it.occurredAt }
                .drop(offset)
                .take(limit)
        }

        override fun getObservationById(id: String): ObservationEntity? {
            return entities[id]
        }

        override fun getObservation(userId: String, id: String): ObservationEntity? {
            val obs = entities[id]
            return if (obs?.userId == userId) obs else null
        }

        override fun deleteObservation(id: String) {
            entities.remove(id)
        }

        override fun getRecentObservationsByType(type: String, limit: Int): List<ObservationEntity> {
            return entities.values
                .filter { it.type == type }
                .sortedByDescending { it.occurredAt }
                .take(limit)
        }

        override fun getObservationCount(): Flow<Int> {
            return flowOf(entities.size)
        }

        override fun getLatestObservation(userId: String, type: String): ObservationEntity? {
            return entities.values
                .filter { it.userId == userId && it.type == type }
                .maxByOrNull { it.occurredAt }
        }

        override fun deleteObservationsForUser(userId: String) {
            entities.values.removeIf { it.userId == userId }
        }

        override fun getObservationsForTimeWindow(userId: String, type: String, startTimeMs: Long, endTimeMs: Long): List<ObservationEntity> {
            return entities.values
                .filter { it.userId == userId && it.type == type && it.occurredAt >= startTimeMs && it.occurredAt < endTimeMs }
                .sortedBy { it.occurredAt }
        }
    
    override fun getObservationsForTimeWindowAsc(userId: String, startTimeMs: Long, endTimeMs: Long, limit: Int): List<com.sanket_satpute_20.ironmind.data.local.entity.ObservationEntity> {
        return entities.values
            .filter { it.userId == userId && it.occurredAt >= startTimeMs && it.occurredAt < endTimeMs }
            .sortedWith(compareBy({ it.occurredAt }, { it.id }))
            .take(limit)
    }
    
    override fun getObservationsForTimeWindowDesc(userId: String, startTimeMs: Long, endTimeMs: Long, limit: Int): List<com.sanket_satpute_20.ironmind.data.local.entity.ObservationEntity> {
        return entities.values
            .filter { it.userId == userId && it.occurredAt >= startTimeMs && it.occurredAt < endTimeMs }
            .sortedWith(compareByDescending<com.sanket_satpute_20.ironmind.data.local.entity.ObservationEntity>{ it.occurredAt }.thenByDescending { it.id })
            .take(limit)
    }
    
    override fun getObservationsForTimeWindowWithTypesAsc(userId: String, types: List<String>, startTimeMs: Long, endTimeMs: Long, limit: Int): List<com.sanket_satpute_20.ironmind.data.local.entity.ObservationEntity> {
        return entities.values
            .filter { it.userId == userId && it.type in types && it.occurredAt >= startTimeMs && it.occurredAt < endTimeMs }
            .sortedWith(compareBy({ it.occurredAt }, { it.id }))
            .take(limit)
    }
    
    override fun getObservationsForTimeWindowWithTypesDesc(userId: String, types: List<String>, startTimeMs: Long, endTimeMs: Long, limit: Int): List<com.sanket_satpute_20.ironmind.data.local.entity.ObservationEntity> {
        return entities.values
            .filter { it.userId == userId && it.type in types && it.occurredAt >= startTimeMs && it.occurredAt < endTimeMs }
            .sortedWith(compareByDescending<com.sanket_satpute_20.ironmind.data.local.entity.ObservationEntity>{ it.occurredAt }.thenByDescending { it.id })
            .take(limit)
    }
}

    @Before
    fun setup() {
        fakeDao = FakeObservationDao()
        repository = ObservationRepositoryImpl(fakeDao)
    }

    private fun createObservation(
        id: String = UUID.randomUUID().toString(),
        userId: String = "user-1",
        type: ObservationType = ObservationType.APP_OPENED,
        occurredAt: Long = 1000L
    ) = Observation(
        id = id,
        userId = userId,
        type = type,
        source = ObservationSource.ANDROID,
        occurredAt = occurredAt,
        recordedAt = System.currentTimeMillis(),
        subjectId = null,
        value = "com.example.app",
        context = "Home screen",
        confidence = 1.0f,
        provenance = ObservationProvenance(
            source = ObservationSource.ANDROID,
            sourceReference = "UsageStatsManager",
            capturedAt = System.currentTimeMillis()
        ),
        schemaVersion = 1
    )

    @Test
    fun `insert and retrieve observation by id`() = runTest {
        val observation = createObservation(id = "obs-1")
        val insertResult = repository.insertObservation(observation)
        assertTrue(insertResult is Result.Success)

        val retrieveResult = repository.getObservationById("obs-1")
        assertTrue(retrieveResult is Result.Success)
        val retrieved = (retrieveResult as Result.Success).data
        assertEquals("obs-1", retrieved.id)
        assertEquals(ObservationType.APP_OPENED, retrieved.type)
        assertEquals(ObservationSource.ANDROID, retrieved.source)
        assertEquals("com.example.app", retrieved.value)
    }

    @Test
    fun `get observations returns list sorted by occurredAt descending`() = runTest {
        repository.insertObservation(createObservation(id = "obs-1", occurredAt = 1000L))
        repository.insertObservation(createObservation(id = "obs-2", occurredAt = 3000L))
        repository.insertObservation(createObservation(id = "obs-3", occurredAt = 2000L))

        val result = repository.getObservations("user-1", limit = 10, offset = 0)
        assertTrue(result is Result.Success)
        val list = (result as Result.Success).data
        assertEquals(3, list.size)
        // Order should be obs-2 (3000), obs-3 (2000), obs-1 (1000)
        assertEquals("obs-2", list[0].id)
        assertEquals("obs-3", list[1].id)
        assertEquals("obs-1", list[2].id)
    }

    @Test
    fun `get observations by type filters correctly`() = runTest {
        repository.insertObservation(createObservation(id = "obs-1", type = ObservationType.APP_OPENED))
        repository.insertObservation(createObservation(id = "obs-2", type = ObservationType.TASK_COMPLETED))
        repository.insertObservation(createObservation(id = "obs-3", type = ObservationType.APP_OPENED))

        val result = repository.getObservationsByType("user-1", ObservationType.APP_OPENED, limit = 10, offset = 0)
        assertTrue(result is Result.Success)
        val list = (result as Result.Success).data
        assertEquals(2, list.size)
        assertTrue(list.all { it.type == ObservationType.APP_OPENED })
    }

    @Test
    fun `delete observation removes it from database`() = runTest {
        repository.insertObservation(createObservation(id = "obs-1"))
        val deleteResult = repository.deleteObservation("obs-1")
        assertTrue(deleteResult is Result.Success)

        val retrieveResult = repository.getObservationById("obs-1")
        assertTrue(retrieveResult is Result.Failure)
    }

    @Test
    fun `getObservationsForTimeWindow with null types returns all types`() = runTest {
        repository.insertObservation(createObservation(id = "1", type = ObservationType.APP_OPENED, occurredAt = 1000L))
        repository.insertObservation(createObservation(id = "2", type = ObservationType.TASK_COMPLETED, occurredAt = 1500L))
        
        val result = repository.getObservationsForTimeWindow("user-1", 1000L, 2000L, null, 10, true)
        assertTrue(result is Result.Success)
        val data = (result as Result.Success).data
        assertEquals(2, data.size)
        assertEquals("1", data[0].id)
        assertEquals("2", data[1].id)
    }

    @Test
    fun `getObservationsForTimeWindow with empty list returns exactly zero matches`() = runTest {
        repository.insertObservation(createObservation(id = "1", type = ObservationType.APP_OPENED, occurredAt = 1000L))
        repository.insertObservation(createObservation(id = "2", type = ObservationType.TASK_COMPLETED, occurredAt = 1500L))
        
        val result = repository.getObservationsForTimeWindow("user-1", 1000L, 2000L, emptyList(), 10, true)
        assertTrue(result is Result.Success)
        val data = (result as Result.Success).data
        assertEquals(0, data.size)
    }

    @Test
    fun `getObservationsForTimeWindow with one type returns only that type`() = runTest {
        repository.insertObservation(createObservation(id = "1", type = ObservationType.APP_OPENED, occurredAt = 1000L))
        repository.insertObservation(createObservation(id = "2", type = ObservationType.TASK_COMPLETED, occurredAt = 1500L))
        
        val result = repository.getObservationsForTimeWindow("user-1", 1000L, 2000L, listOf(ObservationType.TASK_COMPLETED), 10, true)
        assertTrue(result is Result.Success)
        val data = (result as Result.Success).data
        assertEquals(1, data.size)
        assertEquals("2", data[0].id)
    }

    @Test
    fun `getObservationsForTimeWindow with multiple types returns only those types`() = runTest {
        repository.insertObservation(createObservation(id = "1", type = ObservationType.APP_OPENED, occurredAt = 1000L))
        repository.insertObservation(createObservation(id = "2", type = ObservationType.TASK_COMPLETED, occurredAt = 1500L))
        repository.insertObservation(createObservation(id = "3", type = ObservationType.ACTIVITY_CONTEXT_CHANGED, occurredAt = 1600L))
        
        val result = repository.getObservationsForTimeWindow("user-1", 1000L, 2000L, listOf(ObservationType.APP_OPENED, ObservationType.ACTIVITY_CONTEXT_CHANGED), 10, true)
        assertTrue(result is Result.Success)
        val data = (result as Result.Success).data
        assertEquals(2, data.size)
        assertEquals("1", data[0].id)
        assertEquals("3", data[1].id)
    }

    @Test
    fun `getObservationsForTimeWindow enforces start inclusive and end exclusive boundary`() = runTest {
        repository.insertObservation(createObservation(id = "1", type = ObservationType.APP_OPENED, occurredAt = 999L))
        repository.insertObservation(createObservation(id = "2", type = ObservationType.APP_OPENED, occurredAt = 1000L)) // In
        repository.insertObservation(createObservation(id = "3", type = ObservationType.APP_OPENED, occurredAt = 1999L)) // In
        repository.insertObservation(createObservation(id = "4", type = ObservationType.APP_OPENED, occurredAt = 2000L)) // Out
        
        val result = repository.getObservationsForTimeWindow("user-1", 1000L, 2000L, null, 10, true)
        assertTrue(result is Result.Success)
        val data = (result as Result.Success).data
        assertEquals(2, data.size)
        assertEquals("2", data[0].id)
        assertEquals("3", data[1].id)
    }

    @Test
    fun `getObservationsForTimeWindow preserves user isolation`() = runTest {
        repository.insertObservation(createObservation(id = "1", userId = "user-1", type = ObservationType.APP_OPENED, occurredAt = 1500L))
        repository.insertObservation(createObservation(id = "2", userId = "user-2", type = ObservationType.APP_OPENED, occurredAt = 1500L))
        
        val result = repository.getObservationsForTimeWindow("user-1", 1000L, 2000L, null, 10, true)
        assertTrue(result is Result.Success)
        val data = (result as Result.Success).data
        assertEquals(1, data.size)
        assertEquals("1", data[0].id)
    }
}

