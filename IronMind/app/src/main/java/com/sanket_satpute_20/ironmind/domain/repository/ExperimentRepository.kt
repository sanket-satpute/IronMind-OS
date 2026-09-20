package com.sanket_satpute_20.ironmind.domain.repository

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.experiment.ExperimentRecord

interface ExperimentRepository {
    suspend fun save(record: ExperimentRecord): Result<ExperimentRecord, Exception>
    suspend fun getById(id: String): Result<ExperimentRecord?, Exception>
    suspend fun getActiveExperiments(userId: String): Result<List<ExperimentRecord>, Exception>
    suspend fun getAllForUser(userId: String): Result<List<ExperimentRecord>, Exception>
}
