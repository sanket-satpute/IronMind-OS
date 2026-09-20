package com.sanket_satpute_20.ironmind.infrastructure.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.usecase.sync.SyncUseCase

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val syncUseCase: SyncUseCase
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_USER_ID = "userId"
    }

    override suspend fun doWork(): Result {
        return try {
            val userId = inputData.getString(KEY_USER_ID) ?: return Result.failure()

            when (val result = syncUseCase(userId)) {
                is com.sanket_satpute_20.ironmind.domain.common.Result.Success<*> -> Result.success()
                is com.sanket_satpute_20.ironmind.domain.common.Result.Failure<*> -> Result.retry()
            }
        } catch (e: java.io.IOException) {
            Result.retry()
        } catch (e: SecurityException) {
            Result.failure()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
