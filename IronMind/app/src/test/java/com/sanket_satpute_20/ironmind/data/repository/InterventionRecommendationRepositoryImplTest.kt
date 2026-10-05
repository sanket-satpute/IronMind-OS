package com.sanket_satpute_20.ironmind.data.repository

import com.sanket_satpute_20.ironmind.data.local.dao.InterventionRecommendationDao
import com.sanket_satpute_20.ironmind.data.local.entity.InterventionRecommendationEntity
import com.sanket_satpute_20.ironmind.domain.ai.InterventionType
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecommendation
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecommendationStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeInterventionRecommendationDao : InterventionRecommendationDao {
    private val records = mutableMapOf<String, InterventionRecommendationEntity>()
    
    override fun insert(entity: InterventionRecommendationEntity) {
        records[entity.id] = entity
    }

    override fun getById(id: String): InterventionRecommendationEntity? {
        return records[id]
    }

    override fun getPendingRecommendations(userId: String, currentTime: Long): Flow<List<InterventionRecommendationEntity>> {
        val pending = records.values.filter {
            it.userId == userId && 
            it.status == "PENDING" && 
            (it.expiresAt == null || it.expiresAt!! > currentTime)
        }
        return flowOf(pending)
    }

    override fun updateStatus(id: String, status: String) {
        val rec = records[id]
        if (rec != null) {
            records[id] = rec.copy(status = status)
        }
    }

    override fun getEquivalentRecommendations(
        userId: String,
        objective: String,
        targetEntityType: String?,
        targetEntityId: String?
    ): List<InterventionRecommendationEntity> {
        return records.values.filter { 
            it.userId == userId && 
            it.objective == objective && 
            it.targetEntityType == targetEntityType && 
            it.targetEntityId == targetEntityId 
        }.sortedByDescending { it.createdAt }
    }

    override fun getRecommendationsForUser(
        userId: String,
        startTime: Long,
        endTime: Long,
        limit: Int
    ): List<InterventionRecommendationEntity> {
        return records.values.filter { 
            it.userId == userId && 
            it.createdAt >= startTime && 
            it.createdAt < endTime
        }.sortedWith(compareByDescending<InterventionRecommendationEntity> { it.createdAt }.thenBy { it.id })
         .take(limit)
    }
}

class InterventionRecommendationRepositoryImplTest {

    private val dao = FakeInterventionRecommendationDao()
    private val repository = InterventionRecommendationRepositoryImpl(dao)

    @Test
    fun `saveRecommendation and getRecommendation performs correct domain-entity round trip`() = runTest {
        val domain = InterventionRecommendation(
            id = "rec-1",
            userId = "user-1",
            interventionType = InterventionType.BREAK_DOWN,
            objective = com.sanket_satpute_20.ironmind.domain.ai.InterventionObjective.INITIATE_ACTION,
            targetEntityId = "goal-1",
            targetEntityType = "GOAL",
            rationale = "Because.",
            suggestedAction = "Do this.",
            status = InterventionRecommendationStatus.PENDING,
            createdAt = 1000L,
            expiresAt = 2000L
        )

        val saveResult = repository.saveRecommendation(domain)
        assertTrue(saveResult is Result.Success)

        val getResult = repository.getRecommendation("rec-1")
        assertTrue(getResult is Result.Success)
        assertEquals(domain, (getResult as Result.Success).data)
    }

    @Test
    fun `getRecommendationsForUser respects time window and limit`() = runTest {
        val r1 = InterventionRecommendationEntity("rec-1", "u1", "BREAK_DOWN", "INITIATE_ACTION", null, null, "1", "1", "PENDING", 1000L, null)
        val r2 = InterventionRecommendationEntity("rec-2", "u1", "BREAK_DOWN", "INITIATE_ACTION", null, null, "2", "2", "PENDING", 2000L, null)
        val r3 = InterventionRecommendationEntity("rec-3", "u1", "BREAK_DOWN", "INITIATE_ACTION", null, null, "3", "3", "PENDING", 3000L, null)
        val r4 = InterventionRecommendationEntity("rec-4", "u2", "BREAK_DOWN", "INITIATE_ACTION", null, null, "4", "4", "PENDING", 2000L, null)

        dao.insert(r1)
        dao.insert(r2)
        dao.insert(r3)
        dao.insert(r4)

        val result = repository.getRecommendationsForUser("u1", 1500L, 3500L, 10)
        assertTrue(result is Result.Success)
        val list = (result as Result.Success).data
        assertEquals(2, list.size)
        // Ordered by createdAt DESC
        assertEquals("rec-3", list[0].id)
        assertEquals("rec-2", list[1].id)

        // Limit test
        val limitResult = repository.getRecommendationsForUser("u1", 500L, 3500L, 2)
        assertTrue(limitResult is Result.Success)
        assertEquals(2, (limitResult as Result.Success).data.size)
    }
}
