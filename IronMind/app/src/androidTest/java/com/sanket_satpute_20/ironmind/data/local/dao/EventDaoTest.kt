package com.sanket_satpute_20.ironmind.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sanket_satpute_20.ironmind.data.local.IronMindDatabase
import com.sanket_satpute_20.ironmind.data.local.entity.EventEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class EventDaoTest {

    private lateinit var database: IronMindDatabase
    private lateinit var dao: IronMindDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            IronMindDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.ironMindDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun `getEventsForUser returns events ordered by occurredAt ASC then id ASC`() = runTest {
        val event1 = EventEntity(
            id = "evt-B", // ID sorting puts this second
            userId = "user-1",
            type = "TEST",
            entityType = "TEST",
            entityId = "ent-1",
            occurredAt = 1000L, // Same timestamp
            recordedAt = 1000L,
            processedAt = null,
            source = "USER",
            previousState = null,
            newState = null,
            metadata = null,
            correlationId = null,
            causationId = null,
            schemaVersion = 1
        )

        val event2 = EventEntity(
            id = "evt-A", // ID sorting puts this first
            userId = "user-1",
            type = "TEST",
            entityType = "TEST",
            entityId = "ent-2",
            occurredAt = 1000L, // Same timestamp
            recordedAt = 1000L,
            processedAt = null,
            source = "USER",
            previousState = null,
            newState = null,
            metadata = null,
            correlationId = null,
            causationId = null,
            schemaVersion = 1
        )

        dao.insertEvent(event1)
        dao.insertEvent(event2)

        val results = dao.getEventsForUser("user-1")

        assertEquals(2, results.size)
        // With occurredAt being equal, it should sort by id ASC
        assertEquals("evt-A", results[0].id)
        assertEquals("evt-B", results[1].id)
    }

    @Test
    fun `getEventsForDateRange returns events ordered by occurredAt DESC then id DESC`() = runTest {
        val event1 = EventEntity(
            id = "evt-A", // ID sorting puts this second for DESC
            userId = "user-1",
            type = "TEST",
            entityType = "TEST",
            entityId = "ent-1",
            occurredAt = 1000L, // Same timestamp
            recordedAt = 1000L,
            processedAt = null,
            source = "USER",
            previousState = null,
            newState = null,
            metadata = null,
            correlationId = null,
            causationId = null,
            schemaVersion = 1
        )

        val event2 = EventEntity(
            id = "evt-B", // ID sorting puts this first for DESC
            userId = "user-1",
            type = "TEST",
            entityType = "TEST",
            entityId = "ent-2",
            occurredAt = 1000L, // Same timestamp
            recordedAt = 1000L,
            processedAt = null,
            source = "USER",
            previousState = null,
            newState = null,
            metadata = null,
            correlationId = null,
            causationId = null,
            schemaVersion = 1
        )

        dao.insertEvent(event1)
        dao.insertEvent(event2)

        val results = dao.getEventsForDateRange("user-1", 0L, 2000L)

        assertEquals(2, results.size)
        // With occurredAt being equal, it should sort by id DESC
        assertEquals("evt-B", results[0].id)
        assertEquals("evt-A", results[1].id)
    }
}
