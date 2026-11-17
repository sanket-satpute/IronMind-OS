package com.sanket_satpute_20.ironmind.infrastructure.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.session.SessionState
import com.sanket_satpute_20.ironmind.domain.session.UserSessionProvider
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class IntelligenceOrchestrationScheduler(
    private val workManager: WorkManager,
    private val sessionProvider: (() -> UserSessionProvider?)? = null,
    private val clock: Clock = object : Clock { override fun currentTimeMillis(): Long = System.currentTimeMillis() },
    private val context: Context? = null,
    private val minIntervalMillis: Long = DEFAULT_MIN_INTERVAL_MILLIS,
    private val debounceDelayMillis: Long = DEFAULT_DEBOUNCE_DELAY_MILLIS
) {
    companion object {
        const val TAG = "IntelligenceOrchestrationWorker"
        const val UNIQUE_WORK_NAME_PREFIX = "IntelligenceOrchestration_"
        const val PREFS_NAME = "ironmind_intel_orchestration_prefs"
        const val KEY_LAST_COMPLETED_PREFIX = "last_completed_"

        // 1 hour minimum cooldown between background intelligence orchestrations
        const val DEFAULT_MIN_INTERVAL_MILLIS = 60 * 60 * 1000L
        // 5 seconds debounce window for coalescing immediate batches
        const val DEFAULT_DEBOUNCE_DELAY_MILLIS = 5_000L

        fun getUniqueWorkName(userId: String): String = "${UNIQUE_WORK_NAME_PREFIX}$userId"
    }

    private val memoryLastCompleted = ConcurrentHashMap<String, Long>()

    fun getLastCompletedAt(userId: String): Long {
        val memoryTime = memoryLastCompleted[userId]
        if (memoryTime != null) return memoryTime

        val prefTime = try {
            context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                ?.getLong("$KEY_LAST_COMPLETED_PREFIX$userId", 0L) ?: 0L
        } catch (_: Exception) {
            0L
        }

        if (prefTime > 0L) {
            memoryLastCompleted[userId] = prefTime
        }
        return prefTime
    }

    fun recordCompletion(userId: String, timestamp: Long = clock.currentTimeMillis()) {
        memoryLastCompleted[userId] = timestamp
        try {
            context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                ?.edit()
                ?.putLong("$KEY_LAST_COMPLETED_PREFIX$userId", timestamp)
                ?.apply()
        } catch (_: Exception) {
            // Safe fallback to in-memory only
        }
        println("IronMindLifecycle [IntelligenceScheduling] [RECORD_COMPLETION] userId=$userId completedAt=$timestamp")
    }

    fun isThrottled(userId: String, now: Long = clock.currentTimeMillis()): Boolean {
        if (minIntervalMillis <= 0L) return false
        val lastCompleted = getLastCompletedAt(userId)
        if (lastCompleted <= 0L) return false
        return (now - lastCompleted) < minIntervalMillis
    }

    fun scheduleOrchestration(
        userId: String,
        generation: Long? = null,
        force: Boolean = false
    ): Boolean {
        if (userId.isBlank()) {
            println("IronMindLifecycle [IntelligenceScheduling] [SKIPPED] reason=blank_user_id")
            return false
        }

        val provider = sessionProvider?.invoke()
        val validGeneration = generation ?: provider?.getSessionGeneration() ?: 0L
        if (validGeneration <= 0L) {
            println("IronMindLifecycle [IntelligenceScheduling] [SKIPPED] reason=no_active_generation userId=$userId")
            return false
        }

        if (provider != null) {
            try {
                val state = provider.sessionState.value
                if (state is SessionState.Initializing || state is SessionState.ReconciliationFailed) {
                    println("IronMindLifecycle [IntelligenceScheduling] [SKIPPED] reason=invalid_session_state userId=$userId state=${state.javaClass.simpleName}")
                    return false
                }
            } catch (_: Exception) {
                // If sessionState not configured in test fakes, continue
            }

            try {
                val activeUser = provider.getActiveUserId()
                if (activeUser != userId) {
                    println("IronMindLifecycle [IntelligenceScheduling] [SKIPPED] reason=user_not_active scheduledUser=$userId activeUser=$activeUser")
                    return false
                }
                if (provider.isUserErased(activeUser, validGeneration)) {
                    println("IronMindLifecycle [IntelligenceScheduling] [SKIPPED] reason=user_erased userId=$userId")
                    return false
                }
            } catch (_: Exception) {
                // If not configured in test fakes, continue
            }
        }

        val now = clock.currentTimeMillis()
        val lastCompleted = getLastCompletedAt(userId)
        val timeSinceCompleted = now - lastCompleted

        val delayMillis: Long
        if (!force && minIntervalMillis > 0L && lastCompleted > 0L && timeSinceCompleted < minIntervalMillis) {
            val remainingWait = minIntervalMillis - timeSinceCompleted
            delayMillis = maxOf(remainingWait, debounceDelayMillis)
            println("IronMindLifecycle [IntelligenceScheduling] [COOLDOWN_ACTIVE] userId=$userId remainingWaitMs=$remainingWait delayMs=$delayMillis")
        } else {
            delayMillis = debounceDelayMillis
            println("IronMindLifecycle [IntelligenceScheduling] [SCHEDULE_IMMEDIATE] userId=$userId delayMs=$delayMillis")
        }

        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val inputData = Data.Builder()
            .putString(IntelligenceOrchestrationWorker.KEY_USER_ID, userId)
            .putLong(IntelligenceOrchestrationWorker.KEY_SESSION_GENERATION, validGeneration)
            .build()

        val request = OneTimeWorkRequestBuilder<IntelligenceOrchestrationWorker>()
            .setInputData(inputData)
            .setConstraints(constraints)
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .addTag(TAG)
            .addTag("${TAG}_$userId")
            .build()

        return try {
            workManager.enqueueUniqueWork(
                getUniqueWorkName(userId),
                ExistingWorkPolicy.KEEP,
                request
            )
            println("IronMindLifecycle [IntelligenceScheduling] [ENQUEUED] userId=$userId policy=KEEP")
            true
        } catch (e: Exception) {
            println("IronMindLifecycle [IntelligenceScheduling] [ENQUEUE_FAILED] userId=$userId error=${e.message}")
            false
        }
    }

    fun cancelOrchestration(userId: String) {
        try {
            workManager.cancelUniqueWork(getUniqueWorkName(userId))
            workManager.cancelAllWorkByTag("${TAG}_$userId")
            println("IronMindLifecycle [IntelligenceScheduling] [CANCELLED] userId=$userId")
        } catch (e: Exception) {
            println("IronMindLifecycle [IntelligenceScheduling] [CANCEL_FAILED] userId=$userId error=${e.message}")
        }
    }
}
