package com.sanket_satpute_20.ironmind.domain.repository

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.observation.Observation
import com.sanket_satpute_20.ironmind.domain.model.observation.ObservationType

interface ObservationRepository {
    suspend fun insertObservation(observation: Observation): Result<Unit, Exception>
    suspend fun getObservations(userId: String, limit: Int = 50, offset: Int = 0): Result<List<Observation>, Exception>
    suspend fun getObservationsByType(userId: String, type: ObservationType, limit: Int = 50, offset: Int = 0): Result<List<Observation>, Exception>
    suspend fun getObservationById(id: String): Result<Observation, Exception>
    suspend fun deleteObservation(id: String): Result<Unit, Exception>
}
