package com.sanket_satpute_20.ironmind.domain.adapter

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.observation.Observation

/**
 * Interface that external sources (like Android Services, Receivers) implement
 * to feed observations into the system.
 */
interface ObservationAdapter {
    suspend fun emit(observation: Observation): Result<Unit, Exception>
}
