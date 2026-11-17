package com.sanket_satpute_20.ironmind.domain.engine

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.context.ContextSnapshot
import com.sanket_satpute_20.ironmind.domain.model.context.UnifiedContext

interface ContextEngine {
    /**
     * Generates a deterministic snapshot of the current context for the given user.
     * This snapshot includes current time, active commitments, recent actions,
     * recent reflections, active protection session, and goal relevance.
     */
    suspend fun getCurrentContext(userId: String): Result<ContextSnapshot, Exception>

    /**
     * Generates a comprehensive, unified personal productivity and digital behaviour context
     * with strict provenance, epistemological authority distinctions, and non-causal cross-domain correlations.
     */
    suspend fun getUnifiedContext(
        userId: String,
        windowStartTimeMs: Long? = null,
        windowEndTimeMs: Long? = null
    ): Result<UnifiedContext, Exception> = Result.Failure(UnsupportedOperationException("getUnifiedContext not implemented"))
}
