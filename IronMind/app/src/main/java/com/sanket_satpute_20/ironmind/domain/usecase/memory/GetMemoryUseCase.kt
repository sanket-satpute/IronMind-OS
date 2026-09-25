package com.sanket_satpute_20.ironmind.domain.usecase.memory

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Memory
import com.sanket_satpute_20.ironmind.domain.repository.MemoryRepository

class GetMemoryUseCase(
    private val repository: MemoryRepository
) {
    suspend operator fun invoke(id: String): Result<Memory?, Exception> {
        println("IronMindLifecycle Memory [LOAD_START] memoryId=$id")
        val result = repository.getMemoryById(id)
        if (result is Result.Success) {
            println("IronMindLifecycle Memory [LOAD_SUCCESS] memoryId=$id found=${result.data != null}")
        } else {
            println("IronMindLifecycle Memory [LOAD_FAILURE] memoryId=$id error=${(result as Result.Failure).error.message}")
        }
        return result
    }
}
