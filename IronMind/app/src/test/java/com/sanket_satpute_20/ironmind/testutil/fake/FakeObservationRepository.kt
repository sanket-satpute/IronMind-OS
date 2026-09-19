package com.sanket_satpute_20.ironmind.testutil.fake

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.observation.Observation
import com.sanket_satpute_20.ironmind.domain.model.observation.ObservationType
import com.sanket_satpute_20.ironmind.domain.repository.ObservationRepository

class FakeObservationRepository : ObservationRepository {
    private val observations = mutableMapOf<String, Observation>()

    override suspend fun insertObservation(observation: Observation): Result<Unit, Exception> {
        observations[observation.id] = observation
        return Result.Success(Unit)
    }

    override suspend fun getObservations(
        userId: String,
        limit: Int,
        offset: Int
    ): Result<List<Observation>, Exception> {
        val result = observations.values
            .filter { it.userId == userId }
            .sortedByDescending { it.occurredAt }
            .drop(offset)
            .take(limit)
        return Result.Success(result)
    }

    override suspend fun getObservationsByType(
        userId: String,
        type: ObservationType,
        limit: Int,
        offset: Int
    ): Result<List<Observation>, Exception> {
        val result = observations.values
            .filter { it.userId == userId && it.type == type }
            .sortedByDescending { it.occurredAt }
            .drop(offset)
            .take(limit)
        return Result.Success(result)
    }

    override suspend fun getObservationById(id: String): Result<Observation, Exception> {
        val observation = observations[id]
        return if (observation != null) {
            Result.Success(observation)
        } else {
            Result.Failure(Exception("Observation not found"))
        }
    }

    override suspend fun deleteObservation(id: String): Result<Unit, Exception> {
        observations.remove(id)
        return Result.Success(Unit)
    }
}
