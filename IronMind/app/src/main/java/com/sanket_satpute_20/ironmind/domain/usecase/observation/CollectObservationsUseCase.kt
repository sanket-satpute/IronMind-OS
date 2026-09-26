package com.sanket_satpute_20.ironmind.domain.usecase.observation

import com.sanket_satpute_20.ironmind.domain.common.Result

data class ObservationCollectionSummary(
    val totalCollected: Int,
    val successfulProviders: List<String>,
    val failedProviders: List<String>
) {
    val isPartialSuccess: Boolean
        get() = successfulProviders.isNotEmpty() && failedProviders.isNotEmpty()
        
    val isCompleteFailure: Boolean
        get() = successfulProviders.isEmpty() && failedProviders.isNotEmpty()
}

/**
 * Orchestrates the collection of observations across multiple providers.
 * Enforces failure isolation: if one provider fails, others still run.
 * Respects consent and capability rules by delegating to the individual collectors.
 */
class CollectObservationsUseCase(
    private val collectors: List<ObservationCollector>
) {
    suspend operator fun invoke(userId: String): Result<ObservationCollectionSummary, Exception> {
        println("IronMindLifecycle Observation [COLLECTION_STARTED] userId=$userId totalProviders=${collectors.size}")
        
        var totalCollected = 0
        val successfulProviders = mutableListOf<String>()
        val failedProviders = mutableListOf<String>()
        
        for (collector in collectors) {
            try {
                val result = collector.invoke(userId)
                when (result) {
                    is Result.Success -> {
                        totalCollected += result.data
                        successfulProviders.add(collector.collectorName)
                        println("IronMindLifecycle Observation [PROVIDER_COMPLETED] provider=${collector.collectorName} count=${result.data}")
                    }
                    is Result.Failure -> {
                        failedProviders.add(collector.collectorName)
                        println("IronMindLifecycle Observation [PROVIDER_FAILED] provider=${collector.collectorName} error=${result.error.message}")
                    }
                }
            } catch (e: Exception) {
                // Catch unexpected exceptions thrown outside the Result wrapper
                failedProviders.add(collector.collectorName)
                println("IronMindLifecycle Observation [PROVIDER_FAILED] provider=${collector.collectorName} error=${e.message}")
            }
        }
        
        val summary = ObservationCollectionSummary(
            totalCollected = totalCollected,
            successfulProviders = successfulProviders,
            failedProviders = failedProviders
        )
        
        println("IronMindLifecycle Observation [COLLECTION_COMPLETED] userId=$userId totalCollected=$totalCollected successful=${successfulProviders.size} failed=${failedProviders.size}")
        
        // Even if all failed, it is a valid orchestrated run. But if you want to distinguish full failure via Result:
        if (summary.isCompleteFailure) {
            return Result.Failure(Exception("All observation providers failed"))
        }
        
        return Result.Success(summary)
    }
}
