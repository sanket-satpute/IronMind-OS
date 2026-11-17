package com.sanket_satpute_20.ironmind.domain.model.context

/**
 * Explicit epistemic authority hierarchy governing context elements.
 * Prevents AI hypotheses from ever being conflated with or overriding confirmed user facts.
 */
enum class EpistemicAuthority {
    /**
     * Authored or explicitly confirmed by the user (e.g. user-authored Goals, Plans,
     * user reflections, user-confirmed Barriers or Memories).
     * Authoritative ground truth.
     */
    USER_CONFIRMED_FACT,

    /**
     * Deterministically captured by local system sensors or OS telemetry (e.g. AppUsageEvents
     * from UsageStatsManager, Room event logs, ProtectionSession records).
     * Objective measurement subject to instrumentation availability.
     */
    SYSTEM_RECORDED_OBSERVATION,

    /**
     * Statistically or mathematically computed from underlying facts and observations
     * (e.g. Goal/Plan progress percentages, overdue status determinations, aggregate duration).
     * Deterministic calculation.
     */
    DERIVED_CALCULATION,

    /**
     * Inferred, hypothesized, or proposed by AI or correlation heuristics (e.g. cross-domain
     * correlation hypotheses, proposed unconfirmed barriers).
     * Non-authoritative, tentative, requires user validation.
     */
    AI_HYPOTHESIS
}
