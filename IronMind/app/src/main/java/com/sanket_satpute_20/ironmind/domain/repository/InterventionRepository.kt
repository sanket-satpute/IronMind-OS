package com.sanket_satpute_20.ironmind.domain.repository

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecord

interface InterventionRepository {
    suspend fun save(record: InterventionRecord): Result<InterventionRecord, Exception>
    suspend fun getById(id: String): Result<InterventionRecord?, Exception>
    suspend fun getRecentInterventions(userId: String, since: Long): Result<List<InterventionRecord>, Exception>
    suspend fun getActiveInterventions(userId: String): Result<List<InterventionRecord>, Exception>
}
