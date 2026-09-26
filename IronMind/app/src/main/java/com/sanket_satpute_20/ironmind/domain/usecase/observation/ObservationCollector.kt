package com.sanket_satpute_20.ironmind.domain.usecase.observation

import com.sanket_satpute_20.ironmind.domain.common.Result

/**
 * An abstraction for any observation collection strategy.
 * Allows orchestration (e.g. CollectObservationsUseCase) to dynamically execute
 * multiple providers without knowing their underlying concrete implementations.
 */
interface ObservationCollector {
    val collectorName: String
    
    /**
     * Executes the collection logic for the given [userId].
     * Should respect its own internal consent and capability rules.
     * Returns the number of observations successfully collected.
     */
    suspend operator fun invoke(userId: String): Result<Int, Exception>
}
