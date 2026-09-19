package com.sanket_satpute_20.ironmind.domain.ai

/**
 * Structured output from the IronMindAI reasoning layer.
 *
 * Every output:
 * - Has a declared type (§78 of AI_BEHAVIOR_CONTRACT.md)
 * - Carries a confidence score validated to [0.0, 1.0] (§83)
 * - Carries a schemaVersion for auditability (§85)
 * - Is a PROPOSAL only — must not directly mutate domain state (§4, §5)
 *
 * The sealed class hierarchy ensures downstream code can only pattern-match
 * on known, validated output types. Unknown types cannot be invented by providers.
 */
sealed class AIOutput {
    abstract val type: AIOutputType
    abstract val confidence: Float
    abstract val schemaVersion: Int
    abstract val reasoning: String?

    /**
     * AI proposes the user had a detectable intent.
     * Per §78: INTENT category.
     */
    data class IntentOutput(
        val intentDescription: String,
        override val confidence: Float,
        override val reasoning: String? = null,
        override val schemaVersion: Int = 1
    ) : AIOutput() {
        override val type: AIOutputType = AIOutputType.INTENT
    }

    /**
     * AI extracts structured content from a user reflection.
     * Per §78: REFLECTION_EXTRACTION category.
     */
    data class ReflectionExtraction(
        val extractedText: String,
        val candidateEvents: List<String> = emptyList(),
        val candidateBarriers: List<String> = emptyList(),
        override val confidence: Float,
        override val reasoning: String? = null,
        override val schemaVersion: Int = 1
    ) : AIOutput() {
        override val type: AIOutputType = AIOutputType.REFLECTION_EXTRACTION
    }

    /**
     * AI proposes a behavioral pattern candidate.
     * Per §78: PATTERN_CANDIDATE category. Must not include identity labels (§26).
     */
    data class PatternCandidate(
        val patternDescription: String,
        val evidenceReferences: List<String> = emptyList(),
        override val confidence: Float,
        override val reasoning: String? = null,
        override val schemaVersion: Int = 1
    ) : AIOutput() {
        override val type: AIOutputType = AIOutputType.PATTERN_CANDIDATE
    }

    /**
     * AI proposes a memory candidate for user confirmation.
     * Per §78: MEMORY_CANDIDATE category. Must not be auto-written to memory (§52, §53).
     */
    data class MemoryCandidate(
        val candidateContent: String,
        override val confidence: Float,
        override val reasoning: String? = null,
        override val schemaVersion: Int = 1
    ) : AIOutput() {
        override val type: AIOutputType = AIOutputType.MEMORY_CANDIDATE
    }

    /**
     * AI recommends a typed intervention candidate.
     * Per §78, §80: INTERVENTION_RECOMMENDATION category.
     * Per Sprint V2.10: now carries a structured [InterventionType].
     *
     * CRITICAL RULE: Must NOT be executed directly. Must flow through the Decision Engine
     * and user policy before any action is taken (§4, §5, INTERVENTION_RULES.md).
     * This is a CANDIDATE only.
     */
    data class InterventionRecommendation(
        val interventionType: InterventionType = InterventionType.REMIND,
        val recommendation: String,
        val reason: String,
        val supportingContext: String? = null,
        val targetEntityId: String? = null,
        override val confidence: Float,
        override val reasoning: String? = null,
        override val schemaVersion: Int = 1
    ) : AIOutput() {
        override val type: AIOutputType = AIOutputType.INTERVENTION_RECOMMENDATION
    }

    /**
     * AI produces a text summary.
     * Per §78: SUMMARY category.
     */
    data class Summary(
        val summaryText: String,
        override val confidence: Float,
        override val reasoning: String? = null,
        override val schemaVersion: Int = 1
    ) : AIOutput() {
        override val type: AIOutputType = AIOutputType.SUMMARY
    }

    /**
     * AI proposes a practical plan breakdown for a goal.
     * Per Sprint V2.8: PLAN category.
     * This is a recommendation only — must NOT automatically become domain state (§4, §5).
     * The caller (UI/Presenter) must present this for explicit user confirmation before any
     * domain mutation is permitted.
     */
    data class PlanOutput(
        val goalId: String,
        val proposedTasks: List<TaskCandidate>,
        override val confidence: Float,
        override val reasoning: String? = null,
        override val schemaVersion: Int = 1
    ) : AIOutput() {
        override val type: AIOutputType = AIOutputType.PLAN
    }

    /**
     * AI proposes a list of hypothesised barriers that may be contributing to user inaction.
     * Per Sprint V2.9: BARRIER category.
     *
     * CRITICAL CONTRACT RULES:
     * - Barriers are HYPOTHESES only. They must never be asserted as facts.
     * - [proposedBarriers] entries carry isConfirmed=false. Only user action can confirm.
     * - Descriptions must use tentative language ("Could X be part of what is getting in the way?").
     * - No identity labels or psychological certainty permitted.
     * Per AI_BEHAVIOR_CONTRACT.md §4, §5 and IRONMIND_IMPLEMENTATION_ROADMAP.md V2.9.
     */
    data class BarrierOutput(
        val proposedBarriers: List<BarrierCandidate>,
        override val confidence: Float,
        override val reasoning: String? = null,
        override val schemaVersion: Int = 1
    ) : AIOutput() {
        override val type: AIOutputType = AIOutputType.BARRIER
    }

    /**
     * AI requires more information before producing output.
     * Per §78, §29: CLARIFICATION_REQUEST category.
     */
    data class ClarificationRequest(
        val question: String,
        override val confidence: Float = 0.0f,
        override val reasoning: String? = null,
        override val schemaVersion: Int = 1
    ) : AIOutput() {
        override val type: AIOutputType = AIOutputType.CLARIFICATION_REQUEST
    }

    /**
     * AI determines no action is needed or justified.
     * Per §78, §298: NO_ACTION category. AI must be capable of staying silent.
     */
    data class NoAction(
        val reason: String? = null,
        override val confidence: Float = 1.0f,
        override val reasoning: String? = null,
        override val schemaVersion: Int = 1
    ) : AIOutput() {
        override val type: AIOutputType = AIOutputType.NO_ACTION
    }
}

/**
 * Validates that an AIOutput conforms to required constraints from AI_BEHAVIOR_CONTRACT.md.
 *
 * @return true if valid, false if any constraint is violated.
 */
fun AIOutput.isValid(): Boolean {
    // §83: confidence must be in [0.0, 1.0]
    if (confidence < 0.0f || confidence > 1.0f) return false
    // §85: schemaVersion must be positive
    if (schemaVersion < 1) return false
    // §78: type must match the sealed subtype
    if (type != this.type) return false
    return true
}
