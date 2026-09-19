package com.sanket_satpute_20.ironmind.domain.repository

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Reflection

interface ReflectionRepository : Repository {
    suspend fun saveReflection(reflection: Reflection): Result<Unit, Exception>
    suspend fun getReflection(id: String): Result<Reflection?, Exception>
    suspend fun getReflectionsForDateRange(userId: String, startTime: Long, endTime: Long): Result<List<Reflection>, Exception>
    suspend fun searchReflections(userId: String, query: String): Result<List<Reflection>, Exception>
}
