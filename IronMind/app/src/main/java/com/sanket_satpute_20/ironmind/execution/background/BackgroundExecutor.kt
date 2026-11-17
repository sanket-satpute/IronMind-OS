package com.sanket_satpute_20.ironmind.execution.background

/**
 * Interface for executing tasks in the background.
 * Separates the mechanism of background execution (e.g., WorkManager)
 * from the tasks themselves.
 */
interface BackgroundExecutor {
    fun scheduleReflectionProcessing(reflectionId: String)
    fun scheduleEventProcessing()
    fun scheduleEventReconciliation()
    fun scheduleSync()
    fun scheduleObservationReconciliation()
    fun scheduleInterventionEvaluation(trigger: String = "scheduled")
    fun schedulePeriodicInterventionEvaluation()
    fun scheduleInterventionCandidate(candidate: com.sanket_satpute_20.ironmind.domain.ai.AIOutput.InterventionRecommendation, userId: String)
    fun schedulePatternMaintenance()
}
