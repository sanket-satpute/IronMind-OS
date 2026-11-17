package com.sanket_satpute_20.ironmind.domain.model.pattern

/**
 * Logical states representing the lifecycle progression of a discovered pattern.
 * Part of Phase 3.2 Behaviour Pattern Discovery Engine.
 *
 * State progression:
 * - CANDIDATE: Initial single observation exists (N=1). Conservative confidence, not established.
 * - EMERGING: Repeated observations accumulating (N=2 or single calendar day).
 * - ESTABLISHED: Meets conservative evidence sufficiency (N >= 3 across >= 2 distinct calendar days).
 * - WEAKENING: Recent observation window shows declining occurrence or weakened consistency.
 * - INACTIVE: Pattern was not observed within the evaluation window or exceeded inactivity thresholds.
 */
enum class PatternLifecycleState {
    CANDIDATE,
    EMERGING,
    ESTABLISHED,
    WEAKENING,
    INACTIVE;

    fun toLegacyPatternStatus(): PatternStatus = when (this) {
        CANDIDATE, EMERGING, ESTABLISHED, WEAKENING -> PatternStatus.ACTIVE
        INACTIVE -> PatternStatus.INACTIVE
    }
}
