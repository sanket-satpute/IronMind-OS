package com.sanket_satpute_20.ironmind.domain.usecase.memory

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Memory
import com.sanket_satpute_20.ironmind.domain.repository.MemoryRepository

class GetMemoryUseCase(
    private val repository: MemoryRepository
) {
    suspend operator fun invoke(id: String): Result<Memory?, Exception> {
        return repository.getMemoryById(id)
    }
}
