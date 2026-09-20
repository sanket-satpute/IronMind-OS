package com.sanket_satpute_20.ironmind.domain.engine

import com.sanket_satpute_20.ironmind.domain.common.Result

interface DataLifecycleEngine {
    /**
     * Cleans up expired patterns and forgotten memories that are older than the retention threshold.
     */
    suspend fun performRoutineCleanup(userId: String): Result<Unit, Exception>
}
