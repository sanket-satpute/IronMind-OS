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
}
