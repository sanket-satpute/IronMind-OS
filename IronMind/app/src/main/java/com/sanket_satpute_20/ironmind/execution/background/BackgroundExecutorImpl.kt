package com.sanket_satpute_20.ironmind.execution.background

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.sanket_satpute_20.ironmind.infrastructure.worker.EventProcessingWorker
import com.sanket_satpute_20.ironmind.infrastructure.worker.PatternUpdateWorker
import com.sanket_satpute_20.ironmind.infrastructure.worker.SyncWorker

class BackgroundExecutorImpl(
    private val context: Context,
    private val workManager: WorkManager
) : BackgroundExecutor {

    // Sprint V4.12: Hardened Background Scheduling
    // Provides safe, constraint-aware execution for offline resilience and battery preservation.

    fun scheduleSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniqueWork(
            "SyncWorker",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun schedulePatternUpdate() {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val request = OneTimeWorkRequestBuilder<PatternUpdateWorker>()
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniqueWork(
            "PatternUpdateWorker",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun scheduleEventProcessing() {
        // Event processing is critical but shouldn't drain a dying battery if avoidable
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val request = OneTimeWorkRequestBuilder<EventProcessingWorker>()
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniqueWork(
            "EventProcessingWorker",
            ExistingWorkPolicy.KEEP,
            request
        )
    }
}
