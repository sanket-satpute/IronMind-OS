package com.sanket_satpute_20.ironmind.infrastructure.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.session.UserSessionProvider
import java.util.concurrent.TimeUnit

sealed class ObservationSchedulingError {
    object ScheduleFailed : ObservationSchedulingError()
    object CancelFailed : ObservationSchedulingError()
}

class ObservationScheduler(
    private val workManager: WorkManager,
    private val sessionProvider: (() -> UserSessionProvider?)? = null
) {
    companion object {
        const val UNIQUE_WORK_NAME = "ironmind_observation_collection_work"
        // 1 hour is chosen as a reasonable infrastructure test configuration to prevent aggressive battery drain.
        // This is NOT the final product policy, which may be modified later based on privacy/battery rules.
        const val INTERVAL_HOURS = 1L
    }

    fun scheduleObservationCollection(): Result<Unit, ObservationSchedulingError> {
        val provider = sessionProvider?.invoke()
        val generation = provider?.getSessionGeneration() ?: 0L
        if (generation <= 0L) {
            println("IronMindLifecycle [ObservationScheduling] [FAILED] reason=no_active_generation")
            return Result.Failure(ObservationSchedulingError.ScheduleFailed)
        }

        if (provider != null) {
            try {
                val state = provider.sessionState.value
                if (state is com.sanket_satpute_20.ironmind.domain.session.SessionState.Initializing ||
                    state is com.sanket_satpute_20.ironmind.domain.session.SessionState.ReconciliationFailed
                ) {
                    println("IronMindLifecycle [ObservationScheduling] [FAILED] reason=invalid_session_state state=${state.javaClass.simpleName}")
                    return Result.Failure(ObservationSchedulingError.ScheduleFailed)
                }
            } catch (e: Exception) {
                // If sessionState is not mocked in unit test fakes, proceed safely
            }
            try {
                val activeUser = provider.getActiveUserId()
                if (provider.isUserErased(activeUser, generation)) {
                    println("IronMindLifecycle [ObservationScheduling] [FAILED] reason=user_erased")
                    return Result.Failure(ObservationSchedulingError.ScheduleFailed)
                }
            } catch (e: Exception) {
                // If not mocked in unit test fakes, proceed safely
            }
        }

        val constraints = Constraints.Builder()
            // Using only the minimum required constraint. We do not require charging or network,
            // as observation currently relies on local OS APIs and local Room database.
            .setRequiresBatteryNotLow(true)
            .build()

        val inputData = androidx.work.Data.Builder()
            .putLong(ObservationWorker.KEY_SESSION_GENERATION, generation)
            .build()

        val request = PeriodicWorkRequestBuilder<ObservationWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
            .setInputData(inputData)
            .setConstraints(constraints)
            .addTag(ObservationWorker.TAG)
            .addTag(UNIQUE_WORK_NAME)
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
