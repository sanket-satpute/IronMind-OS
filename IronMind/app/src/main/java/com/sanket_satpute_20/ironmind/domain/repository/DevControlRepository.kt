package com.sanket_satpute_20.ironmind.domain.repository

import kotlinx.coroutines.flow.Flow

interface DevControlRepository {
    fun getEventCount(): Flow<Int>
    fun getObservationCount(): Flow<Int>
    fun getMemoryCount(): Flow<Int>
    fun getPatternCount(): Flow<Int>
    fun getDecisionCount(): Flow<Int>
    fun getInterventionCount(): Flow<Int>
    fun getExperimentCount(): Flow<Int>
    fun getOutboxCount(): Flow<Int>
    fun getProtectionRuleCount(): Flow<Int>
}
