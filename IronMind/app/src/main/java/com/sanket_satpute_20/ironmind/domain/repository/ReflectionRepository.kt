package com.sanket_satpute_20.ironmind.domain.repository

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Reflection

interface ReflectionRepository : Repository {
    suspend fun saveReflection(reflection: Reflection): Result<Unit, Exception>
    suspend fun getReflection(id: String): Result<Reflection?, Exception>
}
