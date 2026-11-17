package com.sanket_satpute_20.ironmind.domain.common

import com.sanket_satpute_20.ironmind.domain.ai.BarrierCategory
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierHypothesis
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierStatus
import com.sanket_satpute_20.ironmind.domain.model.context.EpistemicAuthority
import com.sanket_satpute_20.ironmind.domain.model.context.SourceFreshness
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternCategory
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternLifecycleState
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternStatus
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Versioned, deterministic serialization and deserialization codec for IronMind intelligence metadata.
 *
 * Hardened in Phase 3.3.1:
 * - Version 2 format with validated prefixes (`v2:barrier_meta:`, `v2:pattern_meta:`)
 * - Value URL-encoding to prevent delimiter clash from commas, pipes, semicolons, equals, newlines, and unicode
 * - Strict backward compatibility: preserves legacy reflection IDs, Phase 3.2 pattern conditions, and Phase 3.3 barriers
 * - Safe fallback handling: unknown future versions or malformed records fail gracefully without crashing
 * - Epistemic authority integrity: prevents automatic authority promotion during deserialization
 */
object IntelligenceMetadataCodec {

    const val BARRIER_V2_PREFIX = "v2:barrier_meta:"
    const val LEGACY_BARRIER_PREFIX = "patterns:"

    const val PATTERN_V2_PREFIX = "v2:pattern_meta:"

    private val UTF8 = StandardCharsets.UTF_8.name()

    // =========================================================================
    // Barrier Metadata Codec
    // =========================================================================

    /**
     * Serializes barrier intelligence metadata into a versioned, delimiter-safe string.
     * If the barrier is not derived from patterns and has no evidence, returns the original
     * reflection ID untouched.
     */
    fun encodeBarrierMetadata(barrier: BarrierHypothesis): String {
        if (barrier.supportingPatternIds.isNotEmpty() || barrier.evidenceCount > 0) {
            val params = linkedMapOf<String, String>()
            params["v"] = "2"
            if (barrier.supportingPatternIds.isNotEmpty()) {
                params["patterns"] = barrier.supportingPatternIds.joinToString(",") { encodeValue(it) }
            }
            if (barrier.title.isNotBlank()) {
                params["title"] = encodeValue(barrier.title)
            }
            params["count"] = barrier.evidenceCount.toString()
            params["days"] = barrier.distinctDays.toString()
            params["conf"] = barrier.confidence.toString()
            params["auth"] = barrier.epistemicAuthority.name
            if (barrier.sourceProvenance.isNotEmpty()) {
                params["sources"] = barrier.sourceProvenance.joinToString(",") { encodeValue(it) }
            }
            params["fresh"] = barrier.sourceFreshness.name
            if (barrier.sourceReflectionId.isNotBlank()) {
                params["ref"] = encodeValue(barrier.sourceReflectionId)
            }
            return BARRIER_V2_PREFIX + params.entries.joinToString("|") { "${it.key}=${it.value}" }
        } else {
            return barrier.sourceReflectionId
        }
    }

    data class DecodedBarrierMetadata(
        val originalRef: String,
        val title: String,
        val supportingPatternIds: List<String>,
        val evidenceCount: Int,
        val distinctDays: Int,
        val confidence: Float,
        val epistemicAuthority: EpistemicAuthority,
        val sourceProvenance: List<String>,
        val sourceFreshness: SourceFreshness,
        val isPatternDerived: Boolean
    )

    /**
     * Safely decodes barrier metadata from a database column value.
     *
     * Handles:
     * 1. Version 2 format (`v2:barrier_meta:...`)
     * 2. Legacy Phase 3.3 format (`patterns:...`)
     * 3. Legacy reflection reference (e.g. UUID or legacy reflection id without prefix)
     * 4. Malformed or unknown future versions (safe fallback)
     */
    fun decodeBarrierMetadata(
        rawSource: String?,
        confirmationState: String?
    ): DecodedBarrierMetadata {
        val defaultAuthority = if (confirmationState == BarrierConfirmationState.CONFIRMED.name) {
            EpistemicAuthority.USER_CONFIRMED_FACT
        } else {
            EpistemicAuthority.AI_HYPOTHESIS
        }

        if (rawSource.isNullOrBlank()) {
            return DecodedBarrierMetadata(
                originalRef = "",
                title = "",
                supportingPatternIds = emptyList(),
                evidenceCount = 0,
                distinctDays = 0,
                confidence = 0.5f,
                epistemicAuthority = defaultAuthority,
                sourceProvenance = emptyList(),
                sourceFreshness = SourceFreshness.CURRENT,
                isPatternDerived = false
            )
        }

        // 1. Versioned barrier metadata check (v2:barrier_meta:, v3:barrier_meta:, etc.)
        val isBarrierMeta = rawSource.startsWith("v") && rawSource.contains(":barrier_meta:")
        if (isBarrierMeta) {
            val prefix = rawSource.substringBefore(":barrier_meta:") + ":barrier_meta:"
            val payload = rawSource.removePrefix(prefix)
            val tokens = payload.split("|")
            val map = tokens.associate { token ->
                val eqIdx = token.indexOf('=')
                if (eqIdx >= 0) token.substring(0, eqIdx) to token.substring(eqIdx + 1) else token to ""
            }

            val version = prefix.removePrefix("v").removeSuffix(":barrier_meta:").toIntOrNull()
                ?: map["v"]?.toIntOrNull() ?: 2
            if (version > 2) {
                // Unknown future version: fallback safely
                return DecodedBarrierMetadata(
                    originalRef = decodeValue(map["ref"].orEmpty()),
                    title = decodeValue(map["title"].orEmpty()),
                    supportingPatternIds = emptyList(),
                    evidenceCount = 0,
                    distinctDays = 0,
                    confidence = 0.5f,
                    epistemicAuthority = defaultAuthority,
                    sourceProvenance = emptyList(),
                    sourceFreshness = SourceFreshness.CURRENT,
                    isPatternDerived = true
                )
            }

            val rawPatterns = map["patterns"].orEmpty()
            val patternIds = if (rawPatterns.isNotBlank()) {
                rawPatterns.split(",").map { decodeValue(it) }.filter { it.isNotBlank() }
            } else emptyList()

            val title = decodeValue(map["title"].orEmpty())
            val count = map["count"]?.toIntOrNull() ?: 0
            val days = map["days"]?.toIntOrNull() ?: 0
            val conf = map["conf"]?.toFloatOrNull() ?: 0.5f
            val auth = try {
                EpistemicAuthority.valueOf(map["auth"].orEmpty())
            } catch (e: Exception) {
                EpistemicAuthority.AI_HYPOTHESIS
            }
            val rawSources = map["sources"].orEmpty()
            val sources = if (rawSources.isNotBlank()) {
                rawSources.split(",").map { decodeValue(it) }.filter { it.isNotBlank() }
            } else emptyList()
            val fresh = try {
                SourceFreshness.valueOf(map["fresh"].orEmpty())
            } catch (e: Exception) {
                SourceFreshness.CURRENT
            }
            val originalRef = decodeValue(map["ref"].orEmpty())

            val finalAuthority = if (confirmationState == BarrierConfirmationState.CONFIRMED.name) {
                EpistemicAuthority.USER_CONFIRMED_FACT
            } else {
                auth
            }

            return DecodedBarrierMetadata(
                originalRef = originalRef,
                title = title,
                supportingPatternIds = patternIds,
                evidenceCount = count,
                distinctDays = days,
                confidence = conf,
                epistemicAuthority = finalAuthority,
                sourceProvenance = sources,
                sourceFreshness = fresh,
                isPatternDerived = true
            )
        }

        // 2. Backward compatibility with Phase 3.3 unversioned "patterns:" format
        if (rawSource.startsWith(LEGACY_BARRIER_PREFIX)) {
            val parts = rawSource.removePrefix(LEGACY_BARRIER_PREFIX).split("|")
            val patternIds = parts.firstOrNull()?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
            var title = ""
            var count = 0
            var days = 0
            var conf = 0.5f
            var auth = EpistemicAuthority.AI_HYPOTHESIS
            var sources = emptyList<String>()
            var freshness = SourceFreshness.CURRENT
            var originalRef = ""
            for (part in parts.drop(1)) {
                when {
                    part.startsWith("title=") -> title = part.substringAfter("title=")
                    part.startsWith("count=") -> count = part.substringAfter("count=").toIntOrNull() ?: 0
                    part.startsWith("days=") -> days = part.substringAfter("days=").toIntOrNull() ?: 0
                    part.startsWith("conf=") -> conf = part.substringAfter("conf=").toFloatOrNull() ?: 0.5f
                    part.startsWith("auth=") -> auth = try {
                        EpistemicAuthority.valueOf(part.substringAfter("auth="))
                    } catch (e: Exception) { auth }
                    part.startsWith("sources=") -> sources = part.substringAfter("sources=").split(",").filter { it.isNotBlank() }
                    part.startsWith("freshness=") -> freshness = try {
                        SourceFreshness.valueOf(part.substringAfter("freshness="))
                    } catch (e: Exception) { freshness }
                    part.startsWith("ref=") -> originalRef = part.substringAfter("ref=")
                }
            }
            val finalAuthority = if (confirmationState == BarrierConfirmationState.CONFIRMED.name) {
                EpistemicAuthority.USER_CONFIRMED_FACT
            } else {
                auth
            }

            return DecodedBarrierMetadata(
                originalRef = originalRef,
                title = title,
                supportingPatternIds = patternIds,
                evidenceCount = count,
                distinctDays = days,
                confidence = conf,
                epistemicAuthority = finalAuthority,
                sourceProvenance = sources,
                sourceFreshness = freshness,
                isPatternDerived = true
            )
        }

        // 3. True legacy reflection reference without metadata prefix
        return DecodedBarrierMetadata(
            originalRef = rawSource,
            title = "",
            supportingPatternIds = emptyList(),
            evidenceCount = 0,
            distinctDays = 0,
            confidence = 0.5f,
            epistemicAuthority = defaultAuthority,
            sourceProvenance = emptyList(),
            sourceFreshness = SourceFreshness.CURRENT,
            isPatternDerived = false
        )
    }

    // =========================================================================
    // Pattern Lifecycle & Conditions Codec
    // =========================================================================

    /**
     * Serializes pattern conditions and lifecycle metadata into a versioned, delimiter-safe string.
     */
    fun encodePatternConditions(
        category: PatternCategory,
        lifecycleState: PatternLifecycleState,
        sourceProvenance: List<String>
    ): String {
        val encodedSources = sourceProvenance.joinToString(",") { encodeValue(it) }
        return "${PATTERN_V2_PREFIX}v=2;category=${category.name};lifecycleState=${lifecycleState.name};sources=$encodedSources"
    }

    data class DecodedPatternConditions(
        val category: PatternCategory?,
        val lifecycleState: PatternLifecycleState,
        val sources: List<String>
    )

    /**
     * Safely decodes pattern conditions and lifecycle metadata.
     *
     * Handles:
     * 1. Version 2 format (`v2:pattern_meta:...`)
     * 2. Phase 3.2 format (`category=...;lifecycleState=...;sources=...`)
     * 3. Plain legacy conditions string (e.g. `"AFTER_WORK"`) or null
     * 4. Malformed or unknown future versions (safe fallback)
     */
    fun decodePatternConditions(
        conditions: String?,
        status: PatternStatus
    ): DecodedPatternConditions {
        val fallbackState = if (status == PatternStatus.ACTIVE) {
            PatternLifecycleState.ESTABLISHED
        } else {
            PatternLifecycleState.INACTIVE
        }

        if (conditions.isNullOrBlank()) {
            return DecodedPatternConditions(
                category = null,
                lifecycleState = fallbackState,
                sources = emptyList()
            )
        }

        // 1. Versioned pattern metadata check (v2:pattern_meta:, v3:pattern_meta:, etc.)
        val isPatternMeta = conditions.startsWith("v") && conditions.contains(":pattern_meta:")
        if (isPatternMeta) {
            val prefix = conditions.substringBefore(":pattern_meta:") + ":pattern_meta:"
            val payload = conditions.removePrefix(prefix)
            val tokens = payload.split(";")
            val map = tokens.associate { token ->
                val eqIdx = token.indexOf('=')
                if (eqIdx >= 0) token.substring(0, eqIdx) to token.substring(eqIdx + 1) else token to ""
            }

            val version = prefix.removePrefix("v").removeSuffix(":pattern_meta:").toIntOrNull()
                ?: map["v"]?.toIntOrNull() ?: 2
            if (version > 2) {
                // Unknown future version: fallback safely
                return DecodedPatternConditions(
                    category = null,
                    lifecycleState = fallbackState,
                    sources = emptyList()
                )
            }

            val cat = map["category"]?.let {
                try { PatternCategory.valueOf(it) } catch (e: Exception) { null }
            }
            val state = map["lifecycleState"]?.let {
                try { PatternLifecycleState.valueOf(it) } catch (e: Exception) { fallbackState }
            } ?: fallbackState

            val rawSources = map["sources"].orEmpty()
            val sources = if (rawSources.isNotBlank()) {
                rawSources.split(",").map { decodeValue(it) }.filter { it.isNotBlank() }
            } else emptyList()

            return DecodedPatternConditions(
                category = cat,
                lifecycleState = state,
                sources = sources
            )
        }

        // 2. Backward compatibility with Phase 3.2 format: "category=...;lifecycleState=...;sources=..."
        val tokens = conditions.split(";")
        val cat = tokens.firstOrNull { it.startsWith("category=") }
            ?.substringAfter("category=")
            ?.let { try { PatternCategory.valueOf(it) } catch (e: Exception) { null } }

        val state = tokens.firstOrNull { it.startsWith("lifecycleState=") }
            ?.substringAfter("lifecycleState=")
            ?.let { try { PatternLifecycleState.valueOf(it) } catch (e: Exception) { fallbackState } }
            ?: fallbackState

        val rawSources = tokens.firstOrNull { it.startsWith("sources=") }
            ?.substringAfter("sources=")
            .orEmpty()
        val sources = if (rawSources.isNotBlank()) {
            rawSources.split(",").map { decodeValue(it) }.filter { it.isNotBlank() }
        } else emptyList()

        return DecodedPatternConditions(
            category = cat,
            lifecycleState = state,
            sources = sources
        )
    }

    private fun encodeValue(value: String): String {
        return try {
            URLEncoder.encode(value, UTF8)
        } catch (e: Exception) {
            value
        }
    }

    private fun decodeValue(value: String): String {
        return try {
            URLDecoder.decode(value, UTF8)
        } catch (e: Exception) {
            value
        }
    }
}
