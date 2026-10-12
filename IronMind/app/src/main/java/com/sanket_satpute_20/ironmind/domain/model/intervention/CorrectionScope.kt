package com.sanket_satpute_20.ironmind.domain.model.intervention

/**
 * Explicit domain-level correction scope for user feedback on intervention recommendations.
 *
 * Epistemic boundaries:
 * - INTERVENTION_CORRECTION: Applies only to this recommendation instance.
 * - INFERENCE_CORRECTION: Rejects or corrects the AI inference/rationale.
 * - PREFERENCE: Declares a soft user preference for future recommendation scheduling.
 * - CONSTRAINT: Declares a hard constraint or boundary on future interventions.
 * - EXPLICIT_GENERAL_RULE: Declares an explicit, general rule/invariant for the personal model.
 */
enum class CorrectionScope {
    INTERVENTION_CORRECTION,
    INFERENCE_CORRECTION,
    PREFERENCE,
    CONSTRAINT,
    EXPLICIT_GENERAL_RULE
}
