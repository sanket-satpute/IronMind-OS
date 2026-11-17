package com.sanket_satpute_20.ironmind.domain.usecase.insight

import com.sanket_satpute_20.ironmind.domain.model.insight.PersonalInsight
import com.sanket_satpute_20.ironmind.domain.repository.InsightRepository
import kotlinx.coroutines.flow.Flow

class ObservePersonalInsightsUseCase(
    private val insightRepository: InsightRepository
) {
    operator fun invoke(userId: String): Flow<List<PersonalInsight>> {
        return insightRepository.observeInsightsForUser(userId)
    }
}
