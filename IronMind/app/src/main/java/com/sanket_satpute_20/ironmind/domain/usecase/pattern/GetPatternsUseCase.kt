package com.sanket_satpute_20.ironmind.domain.usecase.pattern

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.pattern.Pattern
import com.sanket_satpute_20.ironmind.domain.repository.PatternRepository
import kotlinx.coroutines.flow.Flow

class GetPatternsUseCase(
    private val patternRepository: PatternRepository
) {
    fun observe(userId: String): Flow<List<Pattern>> {
        return patternRepository.observePatternsForUser(userId)
    }

    suspend operator fun invoke(userId: String): Result<List<Pattern>, Exception> {
        if (userId.isBlank()) {
            return Result.Failure(IllegalArgumentException("userId must not be blank"))
        }
        return patternRepository.getPatternsForUser(userId)
    }
}
