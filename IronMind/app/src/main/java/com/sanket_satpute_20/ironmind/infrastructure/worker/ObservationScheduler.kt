package com.sanket_satpute_20.ironmind.infrastructure.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class ObservationScheduler(
    private val workManager: WorkManager
) {
    companion object {
        const val UNIQUE_WORK_NAME = "ironmind_observation_collection_work"
        // 1 hour is chosen as a reasonable infrastructure test configuration to prevent aggressive battery drain.
        // This is NOT the final product policy, which may be modified later based on privacy/battery rules.
        const val INTERVAL_HOURS = 1L
    }

    fun scheduleObservationCollection() {
        val constraints = Constraints.Builder()
            // Using only the minimum required constraint. We do not require charging or network,
            // as observation currently relies on local OS APIs and local Room database.
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<ObservationWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        println("IronMindLifecycle ObservationScheduler [SCHEDULED]")
        workManager.enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE, // Update existing schedule if it changes
            request
        )
    }

    fun cancelObservationCollection() {
        println("IronMindLifecycle ObservationScheduler [CANCELLED]")
        workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
    }
}
