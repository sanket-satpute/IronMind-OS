package com.sanket_satpute_20.ironmind.testutil.fake

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Reflection
import com.sanket_satpute_20.ironmind.domain.repository.ReflectionRepository

class FakeReflectionRepository : ReflectionRepository {
    private val reflections = mutableMapOf<String, Reflection>()
    var shouldFail = false

    override suspend fun saveReflection(reflection: Reflection): Result<Unit, Exception> {
        if (shouldFail) return Result.Failure(Exception("Fake failure"))
        reflections[reflection.id] = reflection
        return Result.Success(Unit)
    }

    override suspend fun getReflection(id: String): Result<Reflection?, Exception> {
        val reflection = reflections.values.find { it.id == id }
        return Result.Success(reflection)
    }

    override suspend fun getReflectionsForDateRange(userId: String, startTime: Long, endTime: Long): Result<List<Reflection>, Exception> {
        val list = reflections.values.filter { it.userId == userId && it.createdAt in startTime..endTime }
        return Result.Success(list)
    }
}
