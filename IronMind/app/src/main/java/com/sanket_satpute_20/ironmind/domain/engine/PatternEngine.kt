package com.sanket_satpute_20.ironmind.domain.engine

import com.sanket_satpute_20.ironmind.domain.common.Result

interface PatternEngine {
    /**
     * Re-evaluates active patterns for the given user, decaying confidence
     * if evidence has become stale, and expiring patterns if they drop below a threshold.
     */
    suspend fun decayStalePatterns(userId: String): Result<Unit, Exception>
    
    // Future expansion: detectPatterns / analyzeEvents will likely be implemented in V2.4 (AI Abstraction)
}
