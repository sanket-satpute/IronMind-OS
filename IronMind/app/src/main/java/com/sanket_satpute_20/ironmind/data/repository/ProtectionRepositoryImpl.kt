package com.sanket_satpute_20.ironmind.data.repository

import com.sanket_satpute_20.ironmind.data.local.dao.IronMindDao
import com.sanket_satpute_20.ironmind.data.local.entity.toDomain
import com.sanket_satpute_20.ironmind.data.local.entity.toEntity
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.ProtectionRule
import com.sanket_satpute_20.ironmind.domain.model.ProtectionSession
import com.sanket_satpute_20.ironmind.domain.repository.ProtectionRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProtectionRepositoryImpl(
    private val dao: IronMindDao,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : ProtectionRepository {

    override suspend fun saveProtectionRule(rule: ProtectionRule): Result<Unit, Exception> = withContext(dispatcher) {
        try {
            dao.insertProtectionRule(rule.toEntity())
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getProtectionRule(id: String): Result<ProtectionRule, Exception> = withContext(dispatcher) {
        try {
            val entity = dao.getProtectionRule(id)
            if (entity != null) {
                Result.Success(entity.toDomain())
            } else {
                Result.Failure(Exception("ProtectionRule not found"))
            }
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getProtectionRulesForUser(userId: String): Result<List<ProtectionRule>, Exception> = withContext(dispatcher) {
        try {
            val entities = dao.getProtectionRulesForUser(userId)
            Result.Success(entities.map { it.toDomain() })
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun saveProtectionSession(session: ProtectionSession): Result<Unit, Exception> = withContext(dispatcher) {
        try {
            dao.insertProtectionSession(session.toEntity())
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getProtectionSession(id: String): Result<ProtectionSession, Exception> = withContext(dispatcher) {
        try {
            val entity = dao.getProtectionSession(id)
            if (entity != null) {
                Result.Success(entity.toDomain())
            } else {
                Result.Failure(Exception("ProtectionSession not found"))
            }
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getActiveProtectionSessionsForUser(userId: String): Result<List<ProtectionSession>, Exception> = withContext(dispatcher) {
        try {
            val entities = dao.getActiveProtectionSessionsForUser(userId)
            Result.Success(entities.map { it.toDomain() })
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
}
