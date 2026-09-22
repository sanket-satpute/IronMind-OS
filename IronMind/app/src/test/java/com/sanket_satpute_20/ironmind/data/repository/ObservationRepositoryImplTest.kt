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
}

