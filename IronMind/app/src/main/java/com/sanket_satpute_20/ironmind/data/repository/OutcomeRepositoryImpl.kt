package com.sanket_satpute_20.ironmind.data.repository

import com.sanket_satpute_20.ironmind.data.local.dao.IronMindDao
import com.sanket_satpute_20.ironmind.data.local.entity.toDomain
import com.sanket_satpute_20.ironmind.data.local.entity.toEntity
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Outcome
import com.sanket_satpute_20.ironmind.domain.repository.OutcomeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OutcomeRepositoryImpl(
    private val dao: IronMindDao
) : OutcomeRepository {

    override suspend fun saveOutcome(outcome: Outcome): Result<Unit, Exception> {
        return try {
            withContext(Dispatchers.IO) {
                dao.insertOutcome(outcome.toEntity())
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getOutcomeForSource(sourceEntityId: String): Result<Outcome?, Exception> {
        return try {
            val entity = withContext(Dispatchers.IO) {
                dao.getOutcomeForSource(sourceEntityId)
            }
            Result.Success(entity?.toDomain())
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
}
