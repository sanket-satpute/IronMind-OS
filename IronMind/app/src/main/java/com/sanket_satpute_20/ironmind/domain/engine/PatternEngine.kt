package com.sanket_satpute_20.ironmind.domain.engine

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.context.UnifiedContext
import com.sanket_satpute_20.ironmind.domain.model.pattern.DiscoveredPattern
import java.time.ZoneId

interface PatternEngine {
    /**
     * Re-evaluates active patterns for the given user, decaying confidence
     * if evidence has become stale, and expiring patterns if they drop below a threshold.
     */
    suspend fun decayStalePatterns(userId: String): Result<Unit, Exception>

    /**
     * Discovers deterministic, evidence-backed behaviour patterns across productivity,
     * digital behaviour, focus activity, and non-causal cross-domain correlations using
     * the assembled [UnifiedContext].
     * Part of Phase 3.2.
     */
    suspend fun discoverPatterns(
        unifiedContext: UnifiedContext,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Result<List<DiscoveredPattern>, Exception> =
        Result.Failure(UnsupportedOperationException("discoverPatterns not implemented"))
}
