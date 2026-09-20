package com.sanket_satpute_20.ironmind.data.repository

import com.sanket_satpute_20.ironmind.data.local.dao.*
import com.sanket_satpute_20.ironmind.domain.repository.DevControlRepository
import kotlinx.coroutines.flow.Flow

class DevControlRepositoryImpl(
    private val ironMindDao: IronMindDao,
    private val observationDao: ObservationDao,
    private val patternDao: PatternDao,
    private val decisionRecordDao: DecisionRecordDao,
    private val interventionDao: InterventionDao,
    private val experimentDao: ExperimentDao,
    private val outboxDao: OutboxDao
) : DevControlRepository {
    override fun getEventCount(): Flow<Int> = ironMindDao.getEventCount()
    override fun getObservationCount(): Flow<Int> = observationDao.getObservationCount()
    override fun getMemoryCount(): Flow<Int> = ironMindDao.getMemoryCount()
    override fun getPatternCount(): Flow<Int> = patternDao.getPatternCount()
    override fun getDecisionCount(): Flow<Int> = decisionRecordDao.getDecisionCount()
    override fun getInterventionCount(): Flow<Int> = interventionDao.getInterventionCount()
    override fun getExperimentCount(): Flow<Int> = experimentDao.getExperimentCount()
    override fun getOutboxCount(): Flow<Int> = outboxDao.getOutboxCount()
    override fun getProtectionRuleCount(): Flow<Int> = ironMindDao.getProtectionRuleCount()
}
