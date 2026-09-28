package com.sanket_satpute_20.ironmind.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sanket_satpute_20.ironmind.data.local.IronMindDatabase
import com.sanket_satpute_20.ironmind.data.local.entity.EventEntity
import com.sanket_satpute_20.ironmind.data.local.entity.ObservationEntity
import com.sanket_satpute_20.ironmind.data.local.entity.ReflectionEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DiscoveryDaoTest {

    private lateinit var database: IronMindDatabase
    private lateinit var ironMindDao: IronMindDao
    private lateinit var observationDao: ObservationDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            IronMindDatabase::class.java
        ).allowMainThreadQueries().build()
        ironMindDao = database.ironMindDao()
        observationDao = database.observationDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    // --- EVENT TESTS ---
    @Test
    fun `events date range ASC limit`() = runTest {
        val e1 = event("1", 1000L)
        val e2 = event("2", 1000L) // tie
        val e3 = event("3", 2000L)
        val e4 = event("4", 3000L) // boundary excluded

        ironMindDao.insertEvent(e4)
        ironMindDao.insertEvent(e3)
        ironMindDao.insertEvent(e2)
        ironMindDao.insertEvent(e1)

        val results = ironMindDao.getEventsForDateRangeAsc("user", 1000L, 3000L, 2)
        assertEquals(2, results.size)
        assertEquals("1", results[0].id)
        assertEquals("2", results[1].id)
    }

    @Test
    fun `events date range DESC limit`() = runTest {
        val e1 = event("1", 1000L)
        val e2 = event("2", 1000L) // tie
        val e3 = event("3", 2000L)
        
        ironMindDao.insertEvent(e3)
        ironMindDao.insertEvent(e2)
        ironMindDao.insertEvent(e1)

        val results = ironMindDao.getEventsForDateRangeDesc("user", 1000L, 3000L, 2)
        assertEquals(2, results.size)
        assertEquals("3", results[0].id)
        assertEquals("2", results[1].id) // id DESC
    }

    // --- OBSERVATION TESTS ---
    @Test
    fun `observations type agnostic ASC limit`() = runTest {
        val o1 = obs("1", "TYPE_A", 100L)
        val o2 = obs("2", "TYPE_B", 100L) // tie
        val o3 = obs("3", "TYPE_C", 200L)

        observationDao.insertObservation(o3)
        observationDao.insertObservation(o2)
        observationDao.insertObservation(o1)

        // Null semantics implementation implies calling the type-agnostic DAO method
        val results = observationDao.getObservationsForTimeWindowAsc("user", 100L, 300L, 2)
        assertEquals(2, results.size)
        assertEquals("1", results[0].id)
        assertEquals("2", results[1].id) // ASC by id
    }

    @Test
    fun `observations with types DESC limit`() = runTest {
        val o1 = obs("1", "TYPE_A", 100L)
        val o2 = obs("2", "TYPE_B", 100L) // tie
        val o3 = obs("3", "TYPE_C", 200L) // Excluded by type

        observationDao.insertObservation(o3)
        observationDao.insertObservation(o2)
        observationDao.insertObservation(o1)

        val results = observationDao.getObservationsForTimeWindowWithTypesDesc("user", listOf("TYPE_A", "TYPE_B"), 100L, 300L, 2)
        assertEquals(2, results.size)
        assertEquals("2", results[0].id) // 100L, id=2 DESC
        assertEquals("1", results[1].id) // 100L, id=1 DESC
    }

    // --- REFLECTION TESTS ---
    @Test
    fun `reflections date range DESC limit boundary`() = runTest {
        val r1 = ref("1", 10L) // Excluded (start)
        val r2 = ref("2", 100L)
        val r3 = ref("3", 200L)
        val r4 = ref("4", 300L) // Excluded (end boundary exclusive)

        ironMindDao.insertReflection(r1)
        ironMindDao.insertReflection(r2)
        ironMindDao.insertReflection(r3)
        ironMindDao.insertReflection(r4)

        val results = ironMindDao.getReflectionsForDateRangeDesc("user", 100L, 300L, 5)
        assertEquals(2, results.size)
        assertEquals("3", results[0].id)
        assertEquals("2", results[1].id)
    }

    private fun event(id: String, occurredAt: Long) = EventEntity(
        id = id, userId = "user", type = "TEST", entityType = null, entityId = null,
        occurredAt = occurredAt, recordedAt = 0L, processedAt = null, source = "USER",
        previousState = null, newState = null, metadata = null, correlationId = null, causationId = null, schemaVersion = 1
    )

    private fun obs(id: String, type: String, occurredAt: Long) = ObservationEntity(
        id = id, userId = "user", type = type, source = "USER", occurredAt = occurredAt, recordedAt = 0L,
        subjectId = null, value = "", context = "", confidence = null, provenance = "SYSTEM", schemaVersion = 1
    )

    private fun ref(id: String, createdAt: Long) = ReflectionEntity(
        id = id, userId = "user", targetEntityId = null, targetEntityType = null, content = "", sentiment = null, createdAt = createdAt, schemaVersion = 1
    )
}
