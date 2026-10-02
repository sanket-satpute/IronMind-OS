package com.sanket_satpute_20.ironmind.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sanket_satpute_20.ironmind.data.local.IronMindDatabase
import com.sanket_satpute_20.ironmind.data.local.entity.InterventionRecommendationEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class InterventionRecommendationDaoTest {

    private lateinit var database: IronMindDatabase
    private lateinit var dao: InterventionRecommendationDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            IronMindDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.interventionRecommendationDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun `insert and getById works correctly`() = runTest {
        val entity = InterventionRecommendationEntity(
            id = "rec-1",
            userId = "user-1",
            interventionType = "BREAK_DOWN",
            targetEntityId = "goal-1",
            targetEntityType = "GOAL",
            rationale = "Because.",
            suggestedAction = "Do this.",
            status = "PENDING",
            createdAt = 1000L,
            expiresAt = null
        )
        dao.insert(entity)
        val retrieved = dao.getById("rec-1")
        assertEquals(entity, retrieved)
    }

    @Test
    fun `getPendingRecommendations returns only PENDING and valid by expiration`() = runTest {
        val baseEntity = InterventionRecommendationEntity(
            id = "rec-1",
            userId = "user-1",
            interventionType = "BREAK_DOWN",
            targetEntityId = "goal-1",
            targetEntityType = "GOAL",
            rationale = "Because.",
            suggestedAction = "Do this.",
            status = "PENDING",
            createdAt = 1000L,
            expiresAt = null
        )
        
        // PENDING, no expiration -> eligible
        dao.insert(baseEntity)
        
        // ACCEPTED -> not eligible
        dao.insert(baseEntity.copy(id = "rec-2", status = "ACCEPTED"))
        
        // PENDING, expired -> not eligible (expiresAt = 2000, currentTime = 3000)
        dao.insert(baseEntity.copy(id = "rec-3", expiresAt = 2000L))
        
        // PENDING, future expiresAt -> eligible (expiresAt = 4000, currentTime = 3000)
        dao.insert(baseEntity.copy(id = "rec-4", expiresAt = 4000L))
        
        // Wrong user -> not eligible
        dao.insert(baseEntity.copy(id = "rec-5", userId = "user-2"))

        val pending = dao.getPendingRecommendations("user-1", 3000L).first()
        
        assertEquals(2, pending.size)
        assertTrue(pending.any { it.id == "rec-1" })
        assertTrue(pending.any { it.id == "rec-4" })
    }
    
    @Test
    fun `updateStatus changes only the status field`() = runTest {
        val entity = InterventionRecommendationEntity(
            id = "rec-1",
            userId = "user-1",
            interventionType = "BREAK_DOWN",
            targetEntityId = "goal-1",
            targetEntityType = "GOAL",
            rationale = "Because.",
            suggestedAction = "Do this.",
            status = "PENDING",
            createdAt = 1000L,
            expiresAt = null
        )
        dao.insert(entity)
        
        dao.updateStatus("rec-1", "ACCEPTED")
        
        val retrieved = dao.getById("rec-1")
        assertEquals("ACCEPTED", retrieved?.status)
        assertEquals("user-1", retrieved?.userId)
    }
}
