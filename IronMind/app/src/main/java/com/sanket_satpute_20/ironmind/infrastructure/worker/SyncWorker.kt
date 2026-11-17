package com.sanket_satpute_20.ironmind.infrastructure.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.session.UserSessionProvider
import com.sanket_satpute_20.ironmind.domain.session.runWithSession
import com.sanket_satpute_20.ironmind.domain.usecase.sync.SyncUseCase
import kotlinx.coroutines.CancellationException

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val syncUseCase: SyncUseCase,
    private val userSessionProvider: UserSessionProvider? = null
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_USER_ID = "userId"
        const val KEY_SESSION_GENERATION = UserSessionProvider.KEY_SESSION_GENERATION
        const val TAG = "SyncWorker"
    }

    override suspend fun doWork(): Result {
        return try {
            val session = userSessionProvider?.sessionState?.value
            if (session != null && !session.isAuthenticated) {
                println("IronMindLifecycle [SyncWorker] [DEFERRED] Reason=unauthenticated_session state=$session")
                return Result.retry()
            }

            val generation = inputData.getLong(KEY_SESSION_GENERATION, -1L)
            if (generation <= 0L) {
                println("IronMindLifecycle [SyncWorker] [FAILED_CLOSED] Reason=missing_or_invalid_generation generation=$generation")
                return Result.failure()
            }

            val explicitUserId = inputData.getString(KEY_USER_ID)?.takeIf { it.isNotBlank() }
            val useCaseResult = userSessionProvider.runWithSession(expectedGeneration = generation) { activeUserId ->
                if (explicitUserId != null && explicitUserId != activeUserId) {
                    println("IronMindLifecycle [SyncWorker] [SKIPPED_STALE_USER] explicitUserId=$explicitUserId activeUserId=$activeUserId")
                    return@runWithSession Result.success()
                }
                if (activeUserId == com.sanket_satpute_20.ironmind.domain.session.SessionState.DEFAULT_LEGACY_USER_ID || activeUserId == "legacy_user_v0") {
                    println("IronMindLifecycle [SyncWorker] [DEFERRED] Reason=fallback_legacy_user activeUserId=$activeUserId")
                    return@runWithSession Result.retry()
                }
                when (syncUseCase(activeUserId)) {
                    is com.sanket_satpute_20.ironmind.domain.common.Result.Success<*> -> Result.success()
                    is com.sanket_satpute_20.ironmind.domain.common.Result.Failure<*> -> Result.retry()
                }
            }

            return useCaseResult ?: Result.retry()
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalStateException) {
            println("IronMindLifecycle [SyncWorker] [REJECTED_STALE_GENERATION] error=${e.message}")
            Result.failure()
        } catch (e: java.io.IOException) {
            Result.retry()
        } catch (e: SecurityException) {
            Result.failure()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
