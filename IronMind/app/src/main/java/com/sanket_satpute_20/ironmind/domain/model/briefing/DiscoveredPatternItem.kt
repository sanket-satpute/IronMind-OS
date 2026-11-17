package com.sanket_satpute_20.ironmind.domain.model.briefing

import com.sanket_satpute_20.ironmind.domain.model.context.EpistemicAuthority
import com.sanket_satpute_20.ironmind.domain.model.context.SourceFreshness

/**
 * Phase 3.5: Represents a relevant behavioural pattern discovered across personal productivity or phone behaviour.
 */
data class DiscoveredPatternItem(
    val patternId: String,
    val patternType: String,
    val description: String,
    val confidence: Float,
    val occurrences: Int,
    val epistemicAuthority: EpistemicAuthority,
    val freshness: SourceFreshness,
    val isUserConfirmed: Boolean
)
