package com.sanket_satpute_20.ironmind.testutil.fake

import com.sanket_satpute_20.ironmind.execution.background.BackgroundExecutor

class FakeBackgroundExecutor : BackgroundExecutor {
    var scheduleReflectionProcessingCalled = false
    var scheduledReflectionId: String? = null
    var shouldThrowException = false
    var scheduleEventProcessingCalled = false
    var scheduleEventReconciliationCalled = false
    var scheduleSyncCalled = false
    var scheduleObservationReconciliationCalled = false
    var scheduleInterventionEvaluationCalled = false
    var schedulePeriodicInterventionEvaluationCalled = false
    var scheduleInterventionCandidateCalled = false
    var lastCandidate: com.sanket_satpute_20.ironmind.domain.ai.AIOutput.InterventionRecommendation? = null
    var schedulePatternMaintenanceCalled = false
    var lastInterventionTrigger: String? = null

    override fun scheduleReflectionProcessing(reflectionId: String) {
        if (shouldThrowException) {
            throw Exception("Failed to schedule")
        }
        scheduleReflectionProcessingCalled = true
        scheduledReflectionId = reflectionId
    }

    override fun scheduleEventProcessing() {
        if (shouldThrowException) {
            throw Exception("Failed to schedule")
        }
        scheduleEventProcessingCalled = true
    }

    override fun scheduleEventReconciliation() {
        if (shouldThrowException) {
            throw Exception("Failed to schedule")
        }
        scheduleEventReconciliationCalled = true
    }

    override fun scheduleSync() {
        if (shouldThrowException) {
            throw Exception("Failed to schedule sync")
        }
        scheduleSyncCalled = true
    }

    override fun scheduleObservationReconciliation() {
        if (shouldThrowException) {
            throw Exception("Failed to schedule observation reconciliation")
        }
        scheduleObservationReconciliationCalled = true
    }

    override fun scheduleInterventionEvaluation(trigger: String) {
        if (shouldThrowException) {
            throw Exception("Failed to schedule intervention evaluation")
        }
        scheduleInterventionEvaluationCalled = true
        lastInterventionTrigger = trigger
    }

    override fun schedulePeriodicInterventionEvaluation() {
        if (shouldThrowException) {
            throw Exception("Failed to schedule periodic intervention evaluation")
        }
        schedulePeriodicInterventionEvaluationCalled = true
    }

    override fun scheduleInterventionCandidate(
        candidate: com.sanket_satpute_20.ironmind.domain.ai.AIOutput.InterventionRecommendation,
        userId: String
    ) {
        if (shouldThrowException) {
            throw Exception("Failed to schedule intervention candidate")
        }
        scheduleInterventionCandidateCalled = true
        lastCandidate = candidate
    }

    override fun schedulePatternMaintenance() {
        if (shouldThrowException) {
            throw Exception("Failed to schedule pattern maintenance")
        }
        schedulePatternMaintenanceCalled = true
    }
}
