package com.sanket_satpute_20.ironmind.infrastructure.worker

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.sanket_satpute_20.ironmind.di.AppContainer

class IronMindWorkerFactory(
    private val appContainer: AppContainer
) : WorkerFactory() {

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? {
        return when (workerClassName) {
            ReflectionProcessingWorker::class.java.name -> ReflectionProcessingWorker(
                appContext,
                workerParameters,
                appContainer.reflectionRepository,
                appContainer.userProfileRepository,
                appContainer.autonomousReflectionEngine,
                appContainer.evaluatePatternCandidatesUseCase,
                appContainer.barrierUnderstandingOrchestrator,
                appContainer.userSessionProvider
            )
            SyncWorker::class.java.name -> SyncWorker(appContext, workerParameters, appContainer.syncUseCase, appContainer.userSessionProvider)
            EventProcessingWorker::class.java.name -> EventProcessingWorker(
                appContext = appContext,
                workerParams = workerParameters,
                learnerEventStatusDao = appContainer.learnerEventStatusDao,
                eventRepository = appContainer.eventRepository,
                eventProcessor = appContainer.eventProcessor,
                clock = appContainer.clock,
                logger = appContainer.logger,
                database = appContainer.database,
                ironMindDao = appContainer.database.ironMindDao(),
                userSessionProvider = appContainer.userSessionProvider
            )
            PatternUpdateWorker::class.java.name -> PatternUpdateWorker(
                appContext = appContext,
                workerParams = workerParameters,
                patternEngine = appContainer.patternEngine,
                userSessionProvider = appContainer.userSessionProvider,
                evolvePersonalModelUseCase = appContainer.evolvePersonalModelUseCase,
                autonomySettingsRepository = appContainer.autonomySettingsRepository,
                learnInterventionResponsePatternUseCase = appContainer.learnInterventionResponsePatternUseCase,
                learnInterventionTimingPatternUseCase = appContainer.learnInterventionTimingPatternUseCase,
                evaluatePatternCandidatesUseCase = appContainer.evaluatePatternCandidatesUseCase,
                userProfileRepository = appContainer.userProfileRepository,
                clock = appContainer.clock,
                logger = appContainer.logger
            )
            InterventionEvaluationWorker::class.java.name -> InterventionEvaluationWorker(
                appContext = appContext,
                workerParams = workerParameters,
                interventionExecutionPipeline = appContainer.interventionExecutionPipeline,
                userSessionProvider = appContainer.userSessionProvider,
                autonomySettingsRepository = appContainer.autonomySettingsRepository,
                goalResurfacingEngine = appContainer.goalResurfacingEngine,
                logger = appContainer.logger
            )
            ObservationWorker::class.java.name -> ObservationWorker(
                appContext = appContext,
                workerParams = workerParameters,
                executeObservationCollectionUseCase = appContainer.executeObservationCollectionUseCase,
                userSessionProvider = appContainer.userSessionProvider,
                intelligenceOrchestrationScheduler = appContainer.intelligenceOrchestrationScheduler
            )
            IntelligenceOrchestrationWorker::class.java.name -> IntelligenceOrchestrationWorker(
                appContext = appContext,
                workerParams = workerParameters,
                personalIntelligenceOrchestrator = appContainer.personalIntelligenceOrchestrator,
                userSessionProvider = appContainer.userSessionProvider,
                userProfileRepository = appContainer.userProfileRepository,
                clock = appContainer.clock,
                logger = appContainer.logger,
                scheduler = appContainer.intelligenceOrchestrationScheduler
            )
            else -> null // Let DefaultWorkerFactory handle other workers
        }
    }
}
