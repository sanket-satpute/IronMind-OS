package com.sanket_satpute_20.ironmind.domain.engine

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.context.ContextSnapshot

interface ContextEngine {
    /**
     * Generates a deterministic snapshot of the current context for the given user.
     * This snapshot includes current time, active commitments, recent actions,
     * recent reflections, active protection session, and goal relevance.
     */
    suspend fun getCurrentContext(userId: String): Result<ContextSnapshot, Exception>
}
