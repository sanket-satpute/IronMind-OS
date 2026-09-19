package com.sanket_satpute_20.ironmind.domain.usecase.reflection

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Reflection
import com.sanket_satpute_20.ironmind.domain.repository.ReflectionRepository

class GetReflectionUseCase(
    private val repository: ReflectionRepository
) {
    suspend operator fun invoke(id: String): Result<Reflection?, Exception> {
        return repository.getReflection(id)
    }
}
