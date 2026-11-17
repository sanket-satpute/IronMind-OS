package com.sanket_satpute_20.ironmind.domain.usecase.insight

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.insight.PersonalInsight
import com.sanket_satpute_20.ironmind.domain.repository.InsightRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GetPersonalInsightsUseCase(
    private val insightRepository: InsightRepository
) {
    suspend operator fun invoke(userId: String): Result<List<PersonalInsight>, Exception> = withContext(Dispatchers.IO) {
        insightRepository.getInsightsForUser(userId)
    }
}
