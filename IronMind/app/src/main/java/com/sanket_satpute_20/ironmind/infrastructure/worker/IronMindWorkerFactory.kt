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
            ReflectionProcessingWorker::class.java.name -> ReflectionProcessingWorker(appContext, workerParameters, appContainer.autonomousReflectionEngine)
            SyncWorker::class.java.name -> SyncWorker(appContext, workerParameters, appContainer.syncUseCase)
            EventProcessingWorker::class.java.name -> EventProcessingWorker(appContext, workerParameters, appContainer.eventRepository) // Placeholder for now
            PatternUpdateWorker::class.java.name -> PatternUpdateWorker(appContext, workerParameters, appContainer.patternEngine)
            InterventionEvaluationWorker::class.java.name -> InterventionEvaluationWorker(appContext, workerParameters, appContainer.interventionExecutionPipeline)
            ObservationWorker::class.java.name -> ObservationWorker(appContext, workerParameters, appContainer.executeObservationCollectionUseCase, appContainer.authRepository)
            else -> null // Let DefaultWorkerFactory handle other workers
        }
    }
}
