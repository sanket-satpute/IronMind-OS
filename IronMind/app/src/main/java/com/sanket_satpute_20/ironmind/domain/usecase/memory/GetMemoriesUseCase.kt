package com.sanket_satpute_20.ironmind.domain.usecase.memory

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Memory
import com.sanket_satpute_20.ironmind.domain.repository.MemoryRepository
import kotlinx.coroutines.flow.Flow

class GetMemoriesUseCase(
    private val memoryRepository: MemoryRepository
) {
    fun observe(userId: String): Flow<List<Memory>> {
        return memoryRepository.observeMemoriesForUser(userId)
    }

    suspend operator fun invoke(userId: String): Result<List<Memory>, Exception> {
        if (userId.isBlank()) {
            return Result.Failure(IllegalArgumentException("userId must not be blank"))
        }
        return memoryRepository.getMemoriesForUser(userId)
    }
}
