package com.sanket_satpute_20.ironmind.infrastructure.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.sanket_satpute_20.ironmind.domain.common.Result
import java.util.concurrent.TimeUnit

sealed class ObservationSchedulingError {
    object ScheduleFailed : ObservationSchedulingError()
    object CancelFailed : ObservationSchedulingError()
}

class ObservationScheduler(
    private val workManager: WorkManager
) {
    companion object {
        const val UNIQUE_WORK_NAME = "ironmind_observation_collection_work"
        // 1 hour is chosen as a reasonable infrastructure test configuration to prevent aggressive battery drain.
        // This is NOT the final product policy, which may be modified later based on privacy/battery rules.
        const val INTERVAL_HOURS = 1L
    }

    fun scheduleObservationCollection(): Result<Unit, ObservationSchedulingError> {
        val constraints = Constraints.Builder()
            // Using only the minimum required constraint. We do not require charging or network,
            // as observation currently relies on local OS APIs and local Room database.
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<ObservationWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        return try {
            workManager.enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE, // Update existing schedule if it changes
                request
            )
            println("IronMindLifecycle [ObservationScheduling] [SCHEDULED]")
            Result.Success(Unit)
        } catch (e: Exception) {
            println("IronMindLifecycle [ObservationScheduling] [FAILED] operation=schedule")
            Result.Failure(ObservationSchedulingError.ScheduleFailed)
        }
    }

    fun cancelObservationCollection(): Result<Unit, ObservationSchedulingError> {
        return try {
            workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
            println("IronMindLifecycle [ObservationScheduling] [CANCELLED]")
            Result.Success(Unit)
        } catch (e: Exception) {
            println("IronMindLifecycle [ObservationScheduling] [FAILED] operation=cancel")
            Result.Failure(ObservationSchedulingError.CancelFailed)
        }
    }
}
